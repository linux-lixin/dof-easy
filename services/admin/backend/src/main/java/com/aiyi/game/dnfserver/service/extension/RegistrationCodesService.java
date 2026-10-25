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
 private static final SecureRandom RANDOM=new SecureRandom();
 public static class Policy { public boolean enabled; public long revision; }
 public static class Create { public String requestId; public int days; }
 private static ActivityConfigService.Failure fail(int status,String message){return new ActivityConfigService.Failure(status,message);}
 private void initialize(Connection c)throws SQLException {
  try(Statement s=c.createStatement()) {
   s.execute("CREATE TABLE IF NOT EXISTS dnf_service.REGISTRATION_POLICY(id INT PRIMARY KEY,enabled TINYINT NOT NULL,revision BIGINT NOT NULL) ENGINE=InnoDB");
   s.execute("INSERT IGNORE INTO dnf_service.REGISTRATION_POLICY VALUES(1,0,0)");
   s.execute("CREATE TABLE IF NOT EXISTS dnf_service.REGISTRATION_CODES(id BIGINT AUTO_INCREMENT PRIMARY KEY,request_id VARCHAR(64) NOT NULL UNIQUE,code_hash CHAR(64) NOT NULL UNIQUE,state VARCHAR(16) NOT NULL,expires_at BIGINT NOT NULL,created_at BIGINT NOT NULL,actor BIGINT NOT NULL,used_uid BIGINT NULL,used_at BIGINT NULL) ENGINE=InnoDB");
   s.execute("CREATE TABLE IF NOT EXISTS dnf_service.REGISTRATION_AUDIT(id BIGINT AUTO_INCREMENT PRIMARY KEY,actor BIGINT NOT NULL,code_id BIGINT NULL,action VARCHAR(32) NOT NULL,created_at BIGINT NOT NULL) ENGINE=InnoDB");
  }
 }
 static String hash(String code) {
  try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(code.getBytes(java.nio.charset.StandardCharsets.US_ASCII));StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02x",b&255));return s.toString();}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 private void audit(Connection c,long actor,Long id,String action)throws SQLException {
  try(PreparedStatement p=c.prepareStatement("INSERT INTO dnf_service.REGISTRATION_AUDIT(actor,code_id,action,created_at) VALUES(?,?,?,?)")){
   p.setLong(1,actor);if(id==null)p.setNull(2,Types.BIGINT);else p.setLong(2,id);p.setString(3,action);p.setLong(4,System.currentTimeMillis());p.executeUpdate();
  }
 }
 private Map<String,Object> policy(Connection c,boolean lock)throws SQLException {
  try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT enabled,revision FROM dnf_service.REGISTRATION_POLICY WHERE id=1"+(lock?" FOR UPDATE":""))){
   if(!r.next())throw fail(409,"注册策略缺失，拒绝注册或修改");Map<String,Object> out=new LinkedHashMap<>();out.put("enabled",r.getBoolean(1));out.put("revision",r.getLong(2));return out;
  }
 }
 private void transactionalTables(Connection c)throws SQLException {transactionalTables(c,true);}
 private void transactionalTables(Connection c,boolean includeNative)throws SQLException {
  String[][] tables={{"d_taiwan","accounts"},{"d_taiwan","member_info"},{"d_taiwan","member_white_account"},{"taiwan_login","member_login"},{"taiwan_billing","cash_cera"},{"taiwan_billing","cash_cera_point"},{"taiwan_cain_2nd","member_avatar_coin"},{"dnf_service","REGISTRATION_POLICY"},{"dnf_service","REGISTRATION_CODES"},{"dnf_service","REGISTRATION_AUDIT"}};
  try(PreparedStatement p=c.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")){
   for(String[] t:tables){if(!includeNative&&!"dnf_service".equals(t[0]))continue;p.setString(1,t[0]);p.setString(2,t[1]);try(ResultSet r=p.executeQuery()){if(!r.next()||!"InnoDB".equalsIgnoreCase(r.getString(1)))throw fail(409,"注册表不支持完整事务，激活码注册保持关闭；不自动转换表引擎");}}
  }
 }
 public Object status(){security.requireAdmin();try(Connection c=dataSource.getConnection()){initialize(c);Map<String,Object> result=policy(c,false);result.put("protocol","own-launcher-optional-activationCode");return result;}catch(SQLException e){throw fail(409,"注册策略表不兼容");}}
 public Object update(Policy request){
  security.requireAdmin();if(request==null)throw fail(400,"缺少策略");
  try(Connection c=dataSource.getConnection()) {initialize(c);transactionalTables(c,request.enabled);c.setAutoCommit(false);
   try {Map<String,Object> before=policy(c,true);if(((Number)before.get("revision")).longValue()!=request.revision)throw fail(409,"策略已修改，请刷新");
    try(PreparedStatement p=c.prepareStatement("UPDATE dnf_service.REGISTRATION_POLICY SET enabled=?,revision=revision+1 WHERE id=1")){p.setBoolean(1,request.enabled);p.executeUpdate();}
    audit(c,ThreadUtil.getUserId(),null,request.enabled?"enable":"disable");Object result=policy(c,false);c.commit();return result;
   }catch(Exception e){c.rollback();throw e;}
  }catch(SQLException e){throw fail(409,"注册策略修改失败，已回滚");}
 }
 public Object create(Create request){
  security.requireAdmin();if(request==null||request.requestId==null||!request.requestId.matches("[A-Za-z0-9-]{16,64}")||request.days<1||request.days>365)throw fail(400,"请求编号或有效天数无效（1–365 天）");
  byte[] random=new byte[32];RANDOM.nextBytes(random);String code=Base64.getUrlEncoder().withoutPadding().encodeToString(random);long now=System.currentTimeMillis(),expires=now+request.days*86400000L;
  try(Connection c=dataSource.getConnection()){initialize(c);transactionalTables(c);c.setAutoCommit(false);
   try {long id;try(PreparedStatement p=c.prepareStatement("INSERT INTO dnf_service.REGISTRATION_CODES(request_id,code_hash,state,expires_at,created_at,actor) VALUES(?,?,'ready',?,?,?)",Statement.RETURN_GENERATED_KEYS)){
     p.setString(1,request.requestId);p.setString(2,hash(code));p.setLong(3,expires);p.setLong(4,now);p.setLong(5,ThreadUtil.getUserId());p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();id=r.getLong(1);}
    }audit(c,ThreadUtil.getUserId(),id,"create");c.commit();Map<String,Object> out=new LinkedHashMap<>();out.put("id",id);out.put("code",code);out.put("expiresAt",expires);return out;
   }catch(Exception e){c.rollback();throw e;}
  }catch(SQLException e){throw fail(409,"请求编号已使用或表不兼容；未重复生成，明文只在创建成功时显示一次");}
 }
 public void revoke(long id){
  security.requireAdmin();if(id<1)throw fail(400,"编号无效");
  try(Connection c=dataSource.getConnection()){initialize(c);transactionalTables(c);c.setAutoCommit(false);
   try {try(PreparedStatement p=c.prepareStatement("UPDATE dnf_service.REGISTRATION_CODES SET state='revoked' WHERE id=? AND state='ready'")){p.setLong(1,id);if(p.executeUpdate()!=1)throw fail(409,"激活码已使用、撤销或不存在");}audit(c,ThreadUtil.getUserId(),id,"revoke");c.commit();}catch(Exception e){c.rollback();throw e;}
  }catch(SQLException e){throw fail(409,"撤销失败，已回滚");}
 }
 public Object list(int page,boolean history){
  security.requireAdmin();if(page<1||page>10000)throw fail(400,"页码无效");List<Map<String,Object>> out=new ArrayList<>();
  String[] columns=history?new String[]{"id","actor","code_id","action","created_at"}:new String[]{"id","state","expires_at","created_at","actor","used_uid","used_at"};
  try(Connection c=dataSource.getConnection()){initialize(c);try(PreparedStatement p=c.prepareStatement("SELECT "+String.join(",",columns)+" FROM dnf_service."+(history?"REGISTRATION_AUDIT":"REGISTRATION_CODES")+" ORDER BY id DESC LIMIT 50 OFFSET ?")){
    p.setInt(1,(page-1)*50);try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(String k:columns)row.put(k,r.getObject(k));if(!history&&"ready".equals(row.get("state"))&&((Number)row.get("expires_at")).longValue()<=System.currentTimeMillis())row.put("state","expired");out.add(row);}}
   }return out;
  }catch(SQLException e){throw fail(409,"注册记录不可用");}
 }
 /** Called inside the SAME Spring transaction as all seven native account inserts. */
 public Long claim(String code,String account){
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw fail(503,"注册事务未启动");
  Connection c=DataSourceUtils.getConnection(dataSource);
  try {Map<String,Object> config;
   try{config=policy(c,true);}catch(SQLException e){if(e.getErrorCode()==1146)return null;throw e;}
   if(!Boolean.TRUE.equals(config.get("enabled")))return null;
   transactionalTables(c);
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
  catch(SQLException e){throw fail(503,"注册审计失败，事务回滚");}finally{DataSourceUtils.releaseConnection(c,dataSource);}
 }
}
