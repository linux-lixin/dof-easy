package com.aiyi.game.dnfserver.service.extension;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
public class AccountOperationsServiceTest {
 static Object defaultValue(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
 @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},handler);}
 static class Database {
  boolean committed,rolledBack,failAudit;String passwordHash;List<String> auditStrings=new ArrayList<>();int connections;
  Connection connection(){return proxy(Connection.class,(p,m,a)->{
   if(m.getName().equals("commit")){committed=true;return null;}
   if(m.getName().equals("rollback")){rolledBack=true;return null;}
   if(m.getName().equals("createStatement"))return proxy(Statement.class,(q,n,b)->defaultValue(n.getReturnType()));
   if(m.getName().equals("prepareStatement")){
    String sql=(String)a[0];Map<Integer,Object> params=new HashMap<>();
    return proxy(PreparedStatement.class,(q,n,b)->{
     if(n.getName().startsWith("set")&&b!=null&&b.length==2){params.put((Integer)b[0],b[1]);return null;}
     if(n.getName().equals("executeQuery")){AtomicBoolean first=new AtomicBoolean(true);return proxy(ResultSet.class,(r,k,c)->{
      if(k.getName().equals("next"))return first.getAndSet(false);
      if(k.getName().equals("getLong"))return 22L;
      return defaultValue(k.getReturnType());});}
     if(n.getName().equals("executeUpdate")){
      if(sql.startsWith("UPDATE d_taiwan.accounts"))passwordHash=(String)params.get(1);
      if(sql.startsWith("INSERT INTO dnf_service.ACCOUNT_OPERATION_AUDIT")){
       if(failAudit)throw new SQLException("synthetic audit failure");
       for(Object v:params.values())if(v instanceof String)auditStrings.add((String)v);
      }return 1;
     }return defaultValue(n.getReturnType());});
   }return defaultValue(m.getReturnType());});}
  DataSource source(){return proxy(DataSource.class,(p,m,a)->{if(m.getName().equals("getConnection")){connections++;return connection();}return defaultValue(m.getReturnType());});}
 }
 AccountOperationsService.Password request(){AccountOperationsService.Password r=new AccountOperationsService.Password();r.account="synthetic-player";r.currentPassword="old-test-only";r.newPassword="new-test-only";r.validationIndex="synthetic-index-123";r.valicode="AB123";return r;}
 @Test public void passwordChangeCommitsAuditWithoutSecretsAndRejectsReplay(){
  Database db=new Database();AccountOperationsService s=new AccountOperationsService();ReflectionTestUtils.setField(s,"dataSource",db.source());
  AccountOperationsService.Password r=request();s.issuePasswordChallenge(r.validationIndex,r.valicode);s.password(r);
  assertTrue(db.committed);assertFalse(db.rolledBack);assertEquals(com.aiyi.game.dnfserver.utils.MD5.getMd5(r.newPassword),db.passwordHash);
  for(String value:db.auditStrings){assertFalse(value.contains(r.newPassword));assertFalse(value.contains(r.currentPassword));assertFalse(value.contains(db.passwordHash));}
  try{s.password(r);fail("replayed challenge accepted");}catch(ActivityConfigService.Failure expected){assertEquals(400,expected.status);}
  assertEquals(1,db.connections);
 }
 @Test public void auditFailureRollsBackPasswordChange(){
  Database db=new Database();db.failAudit=true;AccountOperationsService s=new AccountOperationsService();ReflectionTestUtils.setField(s,"dataSource",db.source());
  AccountOperationsService.Password r=request();s.issuePasswordChallenge(r.validationIndex,r.valicode);
  try{s.password(r);fail("audit failure accepted");}catch(ActivityConfigService.Failure expected){assertEquals(503,expected.status);}
  assertFalse(db.committed);assertTrue(db.rolledBack);
 }
}
