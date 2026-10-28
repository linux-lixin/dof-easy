package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.User;
import com.aiyi.game.dnfserver.utils.MD5;
import com.aiyi.game.dnfserver.utils.cache.UserTokenCacheUtil;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ManagedPasswordContractTest {
    private DataSource ds;private DofAdminService access;private ManagedPasswordService service;private String uuid;private boolean created;private Path key;
    @Before public void setup()throws Exception {
        DofMyisamContractTest fixture=new DofMyisamContractTest();fixture.setup();ds=(DataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"service");uuid=scalar("SELECT @@server_uuid");assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='d_taiwan'"));sql("CREATE DATABASE d_taiwan");created=true;
        sql("CREATE TABLE d_taiwan.accounts(UID INT PRIMARY KEY,accountname VARCHAR(255) NOT NULL,password VARCHAR(255) NOT NULL,admin INT,untouched VARBINARY(8)) ENGINE=InnoDB");sql("INSERT INTO d_taiwan.accounts VALUES(1,'admin',MD5('old-test-only'),1,X'00FF'),(22,'player',MD5('old-test-only'),NULL,X'00FF')");sql("DELETE FROM dnf_service.DOF_OPERATION_JOURNAL");
        ManagedOperationJournal journal=new ManagedOperationJournal();ReflectionTestUtils.setField(journal,"access",access);service=new ManagedPasswordService();ReflectionTestUtils.setField(service,"journal",journal);
        key=Files.createTempFile("dof-synthetic-password-key-",".bin",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));byte[] bytes=new byte[64];new java.security.SecureRandom().nextBytes(bytes);Files.write(key,bytes);ReflectionTestUtils.setField(service,"keyFile",key.toString());
    }
    @After public void clean()throws Exception {try{if(created){assertEquals(uuid,scalar("SELECT @@server_uuid"));assertEquals("disposable-native-ui-contract",scalar("SELECT purpose FROM dnf_service.DOF_CONTRACT_TEST_MARKER"));sql("DROP DATABASE d_taiwan");}}finally{if(key!=null)Files.deleteIfExists(key);UserTokenCacheUtil.clear(22);ThreadUtil.setUserId(1L);}}
    private String scalar(String q)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(q)){assertTrue(r.next());return r.getString(1);}}
    private void sql(String q)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(q);}}
    private Map<?,?> map(Object v){return (Map<?,?>)v;}
    private ManagedPasswordService.Edit edit(long uid){ManagedPasswordService.Edit e=new ManagedPasswordService.Edit();e.requestId=UUID.randomUUID().toString();e.operation="reset";e.revision=(String)map(service.status(uid)).get("revision");e.reason="synthetic admin reset";e.newPassword="new-test-only";return e;}
    private void reject(int code,Runnable f){try{f.run();fail("Expected refusal");}catch(DofFailure e){assertEquals(e.getMessage(),code,e.status);}}
    private void fault(String prefix)throws Exception {DataSource broken=mock(DataSource.class);AtomicBoolean armed=new AtomicBoolean(true);when(broken.getConnection()).thenAnswer(call->{Connection c=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
        if(m.getName().equals("prepareStatement")&&a[0].toString().startsWith(prefix)&&armed.compareAndSet(true,false))throw new SQLException("synthetic interruption");try{return m.invoke(c,a);}catch(InvocationTargetException e){throw e.getCause();}
    });});ReflectionTestUtils.setField(access,"dataSource",broken);}
    private void cache(){User user=new User();user.setId(22);UserTokenCacheUtil.putUserCache("synthetic-player-token",user);assertNotNull(UserTokenCacheUtil.getUser("synthetic-player-token"));}
    @Test public void resetRequiresPreviewAndNeverStoresPasswordOrNativeDigestInJournal()throws Exception {
        ManagedPasswordService.Edit e=edit(22);reject(404,()->service.save(22,e));service.preview(22,e);assertEquals(MD5.getMd5("old-test-only"),scalar("SELECT password FROM d_taiwan.accounts WHERE UID=22"));cache();
        assertEquals("PERSISTED",map(service.save(22,e)).get("state"));assertNull(UserTokenCacheUtil.getUser("synthetic-player-token"));assertEquals(MD5.getMd5(e.newPassword),scalar("SELECT password FROM d_taiwan.accounts WHERE UID=22"));
        String evidence=scalar("SELECT CONCAT(before_json,after_json,request_hash) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+e.requestId+"'");for(String secret:Arrays.asList("old-test-only",e.newPassword,MD5.getMd5("old-test-only"),MD5.getMd5(e.newPassword)))assertFalse(evidence.contains(secret));
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(e).contains(e.newPassword));assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(service.history(22)).contains("passwordTag"));assertEquals("00FF",scalar("SELECT HEX(untouched) FROM d_taiwan.accounts WHERE UID=22"));
        sql("UPDATE d_taiwan.accounts SET password=MD5('later-player-password') WHERE UID=22");assertEquals("PERSISTED",map(service.save(22,e)).get("state"));assertEquals(MD5.getMd5("later-player-password"),scalar("SELECT password FROM d_taiwan.accounts WHERE UID=22"));e.newPassword="other-test-only";reject(409,()->service.save(22,e));
    }
    @Test public void beforeAndAfterWriteFailuresHaveExplicitResultsAndInvalidateOldSessions()throws Exception {
        ManagedPasswordService.Edit e=edit(22);service.preview(22,e);fault("UPDATE `d_taiwan`.`accounts` SET password=?");cache();reject(503,()->service.save(22,e));assertNull(UserTokenCacheUtil.getUser("synthetic-player-token"));assertEquals("NOT_APPLIED",map(service.reconcile(22,e.requestId)).get("state"));
        ManagedPasswordService.Edit next=edit(22);service.preview(22,next);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");reject(503,()->service.save(22,next));reject(409,()->service.preview(22,edit(22)));assertEquals("PERSISTED",map(service.reconcile(22,next.requestId)).get("state"));
    }
    @Test public void thirdPartyChangeCanBeAcknowledgedWithoutRewritingPassword()throws Exception {
        ManagedPasswordService.Edit e=edit(22);service.preview(22,e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");reject(503,()->service.save(22,e));sql("UPDATE d_taiwan.accounts SET password=MD5('independent-new-password') WHERE UID=22");assertEquals("CONFLICT",map(service.reconcile(22,e.requestId)).get("state"));
        ManagedPasswordService.Edit keep=edit(22);keep.operation="accept-current";keep.targetRequestId=e.requestId;keep.newPassword=null;service.preview(22,keep);assertEquals("NO_CHANGE",map(service.save(22,keep)).get("state"));assertEquals(MD5.getMd5("independent-new-password"),scalar("SELECT password FROM d_taiwan.accounts WHERE UID=22"));
    }
    @Test public void secretRotationCannotMisclassifyAnUnresolvedPasswordReset()throws Exception {
        ManagedPasswordService.Edit e=edit(22);service.preview(22,e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");reject(503,()->service.save(22,e));byte[] original=Files.readAllBytes(key);Files.write(key,new byte[64]);reject(409,()->service.reconcile(22,e.requestId));reject(409,()->service.preview(22,edit(22)));Files.write(key,original);assertEquals("PERSISTED",map(service.reconcile(22,e.requestId)).get("state"));
    }
    @Test public void protectedAccountsIdentityChangesAndOldPasswordRestoreAreRejected()throws Exception {
        reject(409,()->service.preview(1,edit(1)));ManagedPasswordService.Edit e=edit(22);service.preview(22,e);sql("UPDATE d_taiwan.accounts SET accountname='replacement' WHERE UID=22");reject(409,()->service.save(22,e));
        ManagedPasswordService.Edit restore=edit(22);restore.operation="restore";reject(400,()->service.preview(22,restore));ThreadUtil.setUserId(99L);reject(403,()->service.status(22));
    }
    @Test public void myisamAndMissingKeyFailSafely()throws Exception {
        sql("ALTER TABLE d_taiwan.accounts ENGINE=MyISAM");ManagedPasswordService.Edit e=edit(22);service.preview(22,e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");reject(503,()->service.save(22,e));assertEquals("PERSISTED",map(service.reconcile(22,e.requestId)).get("state"));Files.delete(key);reject(503,()->service.status(22));
    }
}
