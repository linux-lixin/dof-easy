package com.aiyi.game.dnfserver.service.extension;
import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.conf.CommonAttr;
import com.aiyi.game.dnfserver.utils.MD5;
import com.aiyi.game.dnfserver.utils.cache.*;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
@Service
public class AccountOperationsService {
 private final PasswordChallenges challenges=new PasswordChallenges();
 public void issuePasswordChallenge(String id,String value){challenges.issue(id,value);}
 @Resource private DataSource dataSource;
 @Resource private ExtensionQueryService security;
 public static class Ban {public int days;public String reason;}
 public static class Password {public String account,currentPassword,newPassword,validationIndex,valicode;}
 private void auditTable(Connection c)throws SQLException{try(Statement s=c.createStatement()){s.execute("CREATE TABLE IF NOT EXISTS dnf_service.ACCOUNT_OPERATION_AUDIT(id BIGINT AUTO_INCREMENT PRIMARY KEY,actor BIGINT NOT NULL,target BIGINT NOT NULL,operation VARCHAR(32) NOT NULL,detail VARCHAR(255) NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8");}}
 private void audit(Connection c,long actor,long target,String operation,String detail)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO dnf_service.ACCOUNT_OPERATION_AUDIT(actor,target,operation,detail) VALUES(?,?,?,?)")){p.setLong(1,actor);p.setLong(2,target);p.setString(3,operation);p.setString(4,detail);p.executeUpdate();}}
 private void transactional(Connection c,boolean punishment)throws SQLException {
  List<String[]> tables=new ArrayList<>();tables.add(new String[]{"d_taiwan","accounts"});tables.add(new String[]{"dnf_service","ACCOUNT_OPERATION_AUDIT"});
  if(punishment)tables.add(new String[]{"d_taiwan","member_punish_info"});
  // Pin table metadata for this transaction before checking the engines.
  // An empty locking read does not fetch player data or let concurrent DDL swap engines.
  for(String[] table:tables)try(PreparedStatement p=c.prepareStatement("SELECT * FROM `"+table[0]+"`.`"+table[1]+"` WHERE 1=0 FOR UPDATE");ResultSet ignored=p.executeQuery()){}
  try(PreparedStatement p=c.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) {
   for(String[] table:tables){p.setString(1,table[0]);p.setString(2,table[1]);try(ResultSet r=p.executeQuery()){if(!r.next()||!"InnoDB".equalsIgnoreCase(r.getString(1))||r.next())throw new ActivityConfigService.Failure(409,"账号操作表不支持已验证的事务协议，停止写入；未修改表引擎");}}
  }
 }
 public void requireLoginAllowed(long uid){
  try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement("SELECT 1 FROM d_taiwan.member_punish_info WHERE m_id=? AND punish_type=1 AND punish_value=101 AND apply_flag=2 AND start_time<=NOW() AND end_time>NOW() LIMIT 1")){p.setLong(1,uid);try(ResultSet r=p.executeQuery()){if(r.next())throw new ActivityConfigService.Failure(403,"账号当前被限制登录");}}
  catch(SQLException e){throw new ActivityConfigService.Failure(503,"账号状态检查暂不可用");}
 }
 public Object history(long uid,int page){
  security.requireAdmin();if(uid<1||page<1||page>10000)throw new ActivityConfigService.Failure(400,"账号编号或页码无效");
  List<Map<String,Object>> rows=new ArrayList<>();
  try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement("SELECT id,actor,target,operation,detail,created_at FROM dnf_service.ACCOUNT_OPERATION_AUDIT WHERE target=? ORDER BY id DESC LIMIT 50 OFFSET ?")){
   p.setLong(1,uid);p.setInt(2,(page-1)*50);try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object>x=new LinkedHashMap<>();for(String key:Arrays.asList("id","actor","target","operation","detail","created_at"))x.put(key,r.getString(key));rows.add(x);}}
  }catch(SQLException e){if(e.getErrorCode()!=1146)throw new ActivityConfigService.Failure(409,"账号审计表版本不兼容");}
  return rows;
 }
 public Object status(long uid){
  security.requireAdmin();if(uid<1)throw new ActivityConfigService.Failure(400,"账号编号无效");
  List<Map<String,Object>> rows=new ArrayList<>();
  try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement("SELECT start_time,end_time,admin_id FROM d_taiwan.member_punish_info WHERE m_id=? AND punish_type=1 AND punish_value=101 AND apply_flag=2 AND end_time>NOW() ORDER BY end_time DESC LIMIT 50")){p.setLong(1,uid);try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object>x=new LinkedHashMap<>();x.put("start",r.getString(1));x.put("end",r.getString(2));x.put("managedByThisBackend","dof-easy".equals(r.getString(3)));rows.add(x);}}return rows;}
  catch(SQLException e){throw new ActivityConfigService.Failure(409,"封禁表版本不兼容");}
 }
 public void ban(long uid,Ban request,boolean remove){
  security.requireAdmin();long actor=ThreadUtil.getUserId();
  if(uid<1 || uid==actor || request==null || request.reason==null || request.reason.trim().isEmpty() || request.reason.length()>100 || request.reason.chars().anyMatch(x->x<32) || !remove&&(request.days<1||request.days>3650))throw new ActivityConfigService.Failure(400,"请填写有效账号、期限和 1–100 字原因，不能操作自己的账号");
  try(Connection c=dataSource.getConnection()){
   auditTable(c);c.setAutoCommit(false);transactional(c,true);
   try{
    try(PreparedStatement p=c.prepareStatement("SELECT admin FROM d_taiwan.accounts WHERE UID=? FOR UPDATE")){p.setLong(1,uid);try(ResultSet r=p.executeQuery()){if(!r.next()||r.getBoolean(1))throw new ActivityConfigService.Failure(409,"账号不存在或属于管理员，拒绝修改");}}
    try(PreparedStatement p=c.prepareStatement("DELETE FROM d_taiwan.member_punish_info WHERE m_id=? AND admin_id='dof-easy' AND punish_type=1 AND punish_value=101")){p.setLong(1,uid);p.executeUpdate();}
    if(!remove)try(PreparedStatement p=c.prepareStatement("INSERT INTO d_taiwan.member_punish_info(m_id,punish_type,occ_time,punish_value,apply_flag,start_time,end_time,admin_id,reason) VALUES(?,1,NOW(),101,2,NOW(),TIMESTAMPADD(DAY,?,NOW()),'dof-easy',?)")){p.setLong(1,uid);p.setInt(2,request.days);p.setString(3,request.reason);p.executeUpdate();}
    audit(c,actor,uid,remove?"unban":"ban",request.reason);c.commit();
   }catch(Exception e){c.rollback();throw e;}
  }catch(SQLException e){throw new ActivityConfigService.Failure(503,"封禁结果尚未确认，请先查询实际处罚记录；未自动覆盖其他来源处罚");}
  UserTokenCacheUtil.clear(uid);
 }
 public void password(Password request){
  if(request==null||request.account==null||request.account.trim().isEmpty()||request.account.length()>64||request.account.chars().anyMatch(x->x<32)||request.currentPassword==null||request.currentPassword.length()>128||request.newPassword==null||request.newPassword.length()<8||request.newPassword.length()>64||request.newPassword.equals(request.currentPassword)||request.validationIndex==null||request.validationIndex.length()>100||request.valicode==null)throw new ActivityConfigService.Failure(400,"账号或密码格式无效，新密码须为 8–64 位且不同于旧密码");
  if(!challenges.consume(request.validationIndex,request.valicode))throw new ActivityConfigService.Failure(400,"验证码不正确或已过期");
  try(Connection c=dataSource.getConnection()){
   auditTable(c);c.setAutoCommit(false);transactional(c,false);long uid;
   try{
    try(PreparedStatement p=c.prepareStatement("SELECT UID,admin FROM d_taiwan.accounts WHERE accountname=? AND password=? FOR UPDATE")){p.setString(1,request.account);p.setString(2,MD5.getMd5(request.currentPassword));try(ResultSet r=p.executeQuery()){if(!r.next())throw new ActivityConfigService.Failure(400,"账号或旧密码不正确");if(r.getBoolean(2))throw new ActivityConfigService.Failure(403,"管理员密码请通过实例管理流程修改");uid=r.getLong(1);}}
    try(PreparedStatement p=c.prepareStatement("UPDATE d_taiwan.accounts SET password=? WHERE UID=? AND password=?")){p.setString(1,MD5.getMd5(request.newPassword));p.setLong(2,uid);p.setString(3,MD5.getMd5(request.currentPassword));if(p.executeUpdate()!=1)throw new ActivityConfigService.Failure(409,"账号密码版本改变，停止覆盖");}
    audit(c,uid,uid,"password_change","玩家验证旧密码及一次性验证码后改密；不记录密码或摘要");c.commit();
   }catch(Exception e){c.rollback();throw e;}
   UserTokenCacheUtil.clear(uid);
  }catch(SQLException e){throw new ActivityConfigService.Failure(503,"改密结果尚未确认，请先使用新密码验证登录；需要重试时重新获取验证码");}
 }
}
