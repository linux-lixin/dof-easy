package com.aiyi.game.dnfserver.service.extension;

import com.aiyi.core.util.thread.ThreadUtil;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.*;
import java.security.*;
import java.util.*;

/** Optional gate for our registration protocol. Never consumes QK or GM authorization codes. */
@Service
public class RegistrationCodesService {
 @Resource private DataSource dataSource;
 @Resource private ExtensionQueryService security;
 @Resource private com.aiyi.game.dnfserver.service.dof.ManagedRegistrationAdmin managedAdmin;
 @Resource private com.aiyi.game.dnfserver.service.dof.ManagedOperationJournal journal;
 @org.springframework.beans.factory.annotation.Value("${DOF_ADMIN_ENABLED:false}") private boolean managed;
 private static final SecureRandom RANDOM=new SecureRandom();
 public static class Policy extends com.aiyi.game.dnfserver.service.dof.ManagedRegistrationAdmin.Edit {}
 public static class Create extends com.aiyi.game.dnfserver.service.dof.ManagedRegistrationAdmin.Edit {}
 private static ActivityConfigService.Failure fail(int status,String message){return new ActivityConfigService.Failure(status,message);}
 public static String hash(String code) {
  try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(code.getBytes(java.nio.charset.StandardCharsets.US_ASCII));StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02x",b&255));return s.toString();}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 private void audit(Connection c,long actor,Long id,String action)throws SQLException {
  try(PreparedStatement p=c.prepareStatement("INSERT INTO dnf_service.REGISTRATION_AUDIT(actor,code_id,action,created_at) VALUES(?,?,?,?)")){
   p.setLong(1,actor);if(id==null)p.setNull(2,Types.BIGINT);else p.setLong(2,id);p.setString(3,action);p.setLong(4,System.currentTimeMillis());p.executeUpdate();
  }
 }
 private Map<String,Object> policy(Connection c,boolean lock)throws SQLException {
  // Keep the unique-key record lock acquired by the upsert; a full locking scan
  // would upgrade it to a next-key lock and can deadlock behind another upsert.
  try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT id,enabled,revision FROM dnf_service.REGISTRATION_POLICY WHERE id=1"+(lock?" FOR UPDATE":""))){
   if(!r.next()||r.getInt(1)!=1||r.getObject(2)==null||!Arrays.asList(0,1).contains(r.getInt(2))||r.getObject(3)==null||r.getLong(3)<0)throw fail(409,"注册策略缺失或值不兼容，拒绝注册或修改");Map<String,Object> out=new LinkedHashMap<>();out.put("enabled",r.getInt(2)==1);out.put("revision",r.getLong(3));if(r.next())throw fail(409,"注册策略主键不兼容");return out;
  }
 }
 public Object status(){return managedAdmin.status();}
 public Object update(Policy request){return managedAdmin.save(request,"policy");}
 public Object create(Create request){return managedAdmin.save(request,"create");}
 public void revoke(long id,com.aiyi.game.dnfserver.service.dof.ManagedRegistrationAdmin.Edit request){if(request==null||request.codeId==null||request.codeId!=id)throw fail(400,"撤销目标与预览不同");managedAdmin.save(request,"revoke");}
 public Object list(int page,boolean history){
  journal.admin();if(page<1||page>10000)throw fail(400,"页码无效");List<Map<String,Object>> out=new ArrayList<>();
  String[] columns=history?new String[]{"id","actor","code_id","action","created_at"}:new String[]{"id","state","expires_at","created_at","actor","used_uid","used_at"};
  try(Connection c=journal.connection()){com.aiyi.game.dnfserver.service.dof.RegistrationSchema.check(c);try(PreparedStatement p=c.prepareStatement("SELECT "+String.join(",",columns)+" FROM dnf_service."+(history?"REGISTRATION_AUDIT":"REGISTRATION_CODES")+" ORDER BY id DESC LIMIT 50 OFFSET ?")){
   p.setInt(1,(page-1)*50);try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(String k:columns)row.put(k,r.getObject(k));if(!history&&"ready".equals(row.get("state"))&&((Number)row.get("expires_at")).longValue()<=System.currentTimeMillis())row.put("state","expired");out.add(row);}}
  }return out;}catch(SQLException e){throw fail(409,"注册记录不可用；未自动创建或覆盖表");}
 }
 /** Called inside the SAME Spring transaction as all seven native account inserts. */
 public Long claim(String code,String account){
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw fail(503,"注册事务未启动");
  Connection c=DataSourceUtils.getConnection(dataSource);
  try {Map<String,Object> config;
   NativeAccountRegistration.verifyTransactional(c,true);
   if(managed){
    for(String t:Arrays.asList("REGISTRATION_POLICY","REGISTRATION_CODES","REGISTRATION_AUDIT"))try(Statement st=c.createStatement();ResultSet ignored=st.executeQuery("SELECT * FROM dnf_service.`"+t+"` WHERE 1=0 FOR UPDATE")){}
    com.aiyi.game.dnfserver.service.dof.RegistrationSchema.check(c);
    try(Statement st=c.createStatement()){st.executeUpdate("INSERT INTO dnf_service.REGISTRATION_POLICY(id,enabled,revision) VALUES(1,0,0) ON DUPLICATE KEY UPDATE id=id");}
   }
   try{config=policy(c,true);}catch(SQLException e){if(e.getErrorCode()==1146)return null;throw e;}
   if(!Boolean.TRUE.equals(config.get("enabled")))return null;
   for(String t:Arrays.asList("REGISTRATION_POLICY","REGISTRATION_CODES","REGISTRATION_AUDIT"))try(Statement st=c.createStatement();ResultSet ignored=st.executeQuery("SELECT * FROM dnf_service.`"+t+"` WHERE 1=0 FOR UPDATE")){}
   com.aiyi.game.dnfserver.service.dof.RegistrationSchema.check(c);
   // Current (locking) read: an earlier duplicate check may have used an older RR snapshot.
   try(PreparedStatement p=c.prepareStatement("SELECT UID FROM d_taiwan.accounts WHERE accountname=? FOR UPDATE")){p.setString(1,account);try(ResultSet r=p.executeQuery()){if(r.next())throw fail(400,"该用户名已存在，请更换其他用户名");}}
   if(code==null||!code.matches("[A-Za-z0-9_-]{43}"))throw fail(400,"请提供有效的注册激活码");
   long id;try(PreparedStatement p=c.prepareStatement("SELECT id FROM dnf_service.REGISTRATION_CODES WHERE code_hash=? AND state='ready' AND expires_at>? FOR UPDATE")){p.setString(1,hash(code));p.setLong(2,System.currentTimeMillis());try(ResultSet r=p.executeQuery()){if(!r.next())throw fail(400,"注册激活码无效、过期或已使用");id=r.getLong(1);}}
   try(PreparedStatement p=c.prepareStatement("UPDATE dnf_service.REGISTRATION_CODES SET state='used',used_at=? WHERE id=? AND state='ready'")){p.setLong(1,System.currentTimeMillis());p.setLong(2,id);if(p.executeUpdate()!=1)throw fail(409,"激活码已使用");}return id;
  }catch(SQLException e){throw fail(503,"激活码校验暂不可用，拒绝注册");}finally{DataSourceUtils.releaseConnection(c,dataSource);}
 }
 public void finish(Long id,long uid){
  if(id==null)return;
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw fail(503,"注册事务未启动");
  Connection c=DataSourceUtils.getConnection(dataSource);
  try {try(PreparedStatement p=c.prepareStatement("UPDATE dnf_service.REGISTRATION_CODES SET used_uid=? WHERE id=? AND state='used' AND used_uid IS NULL")){p.setLong(1,uid);p.setLong(2,id);if(p.executeUpdate()!=1)throw fail(409,"激活码归属冲突");}audit(c,uid,id,"register");}
  catch(SQLException e){throw fail(503,"注册审计结果待核对，请保留操作编号");}finally{DataSourceUtils.releaseConnection(c,dataSource);}
 }
}
