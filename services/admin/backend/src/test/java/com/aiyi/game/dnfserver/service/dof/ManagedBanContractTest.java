package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Native-schema synthetic penalties in an identity-guarded disposable database. */
public class ManagedBanContractTest {
    private DataSource ds;private DofAdminService access;private ManagedBanService service;private String uuid;private boolean created;
    @Before public void setup()throws Exception {
        DofMyisamContractTest fixture=new DofMyisamContractTest();fixture.setup();ds=(DataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"service");uuid=scalar("SELECT @@server_uuid");
        assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='d_taiwan'"));sql("CREATE DATABASE d_taiwan");created=true;
        sql("CREATE TABLE d_taiwan.accounts(UID INT PRIMARY KEY,admin INT,accountname VARCHAR(255) NOT NULL) ENGINE=InnoDB");sql("INSERT INTO d_taiwan.accounts VALUES(1,1,'fixture-admin'),(22,NULL,'player-22'),(23,0,'player-23'),(24,1,'other-admin')");
        sql("CREATE TABLE d_taiwan.member_punish_info(m_id INT NOT NULL DEFAULT 0,punish_type INT NOT NULL DEFAULT 0,occ_time DATETIME NOT NULL,punish_value INT NOT NULL DEFAULT 0,apply_flag TINYINT NOT NULL DEFAULT 0,start_time DATETIME NOT NULL,end_time DATETIME NOT NULL,admin_id VARCHAR(25) NOT NULL,reason VARCHAR(100) NOT NULL,PRIMARY KEY(m_id,punish_type)) ENGINE=InnoDB DEFAULT CHARSET=utf8");
        sql("DELETE FROM dnf_service.DOF_OPERATION_JOURNAL");ManagedOperationJournal journal=new ManagedOperationJournal();ReflectionTestUtils.setField(journal,"access",access);service=new ManagedBanService();ReflectionTestUtils.setField(service,"journal",journal);
    }
    @After public void clean()throws Exception {try{if(created){assertEquals(uuid,scalar("SELECT @@server_uuid"));assertEquals("disposable-native-ui-contract",scalar("SELECT purpose FROM dnf_service.DOF_CONTRACT_TEST_MARKER"));sql("DROP DATABASE d_taiwan");}}finally{ThreadUtil.setUserId(1L);}}
    private String scalar(String q)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(q)){assertTrue(r.next());return r.getString(1);}}
    private void sql(String q)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(q);}}
    private Map<?,?> map(Object v){return (Map<?,?>)v;}
    private ManagedBanService.Edit edit(long uid,String action){ManagedBanService.Edit e=new ManagedBanService.Edit();e.requestId=UUID.randomUUID().toString();e.revision=(String)map(service.status(uid)).get("revision");e.reason="synthetic operator reason";e.operation=action;e.days=action.equals("ban")?2:0;return e;}
    private ManagedBanService.Edit restore(long uid,String id){ManagedBanService.Edit e=edit(uid,"restore");e.targetRequestId=id;return e;}
    private void reject(int code,Runnable f){try{f.run();fail("Expected refusal");}catch(DofFailure e){assertEquals(e.getMessage(),code,e.status);}}
    private void fault(String prefix)throws Exception{DataSource faulty=mock(DataSource.class);AtomicBoolean armed=new AtomicBoolean(true);when(faulty.getConnection()).thenAnswer(call->{Connection c=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
        if(m.getName().equals("prepareStatement")&&a[0].toString().startsWith(prefix)&&armed.compareAndSet(true,false))throw new SQLException("synthetic interruption");
        try{return m.invoke(c,a);}catch(InvocationTargetException e){throw e.getCause();}
    });});ReflectionTestUtils.setField(access,"dataSource",faulty);}
    @Test public void previewRequiresConfirmationAndReplayDoesNotExtendExpiry()throws Exception {
        ManagedBanService.Edit e=edit(22,"ban");reject(404,()->service.save(22,e,"ban"));assertEquals("PREVIEWED",map(service.preview(22,e)).get("state"));assertEquals("0",scalar("SELECT COUNT(*) FROM d_taiwan.member_punish_info"));
        String encoded=scalar("SELECT after_json FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+e.requestId+"'");
        String fixedEnd=new com.fasterxml.jackson.databind.ObjectMapper().readTree(encoded).get("penalty").get("end_time").asText();Thread.sleep(1100);
        assertEquals("PERSISTED",map(service.save(22,e,"ban")).get("state"));assertEquals(fixedEnd,scalar("SELECT DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') FROM d_taiwan.member_punish_info WHERE m_id=22"));
        assertEquals("PERSISTED",map(service.save(22,e,"ban")).get("state"));e.days=3;reject(409,()->service.save(22,e,"ban"));
    }
    @Test public void unbanAndRestorePreserveOtherPunishmentTypesAndForeignIssuers()throws Exception {
        sql("INSERT INTO d_taiwan.member_punish_info VALUES(22,2,NOW(),777,2,NOW(),TIMESTAMPADD(DAY,9,NOW()),'external','other type')");
        ManagedBanService.Edit b=edit(22,"ban");service.preview(22,b);service.save(22,b,"ban");ManagedBanService.Edit u=edit(22,"unban");service.preview(22,u);service.save(22,u,"unban");assertEquals("0",scalar("SELECT apply_flag FROM d_taiwan.member_punish_info WHERE m_id=22 AND punish_type=1"));
        sql("UPDATE d_taiwan.member_punish_info SET reason='later reason' WHERE m_id=22 AND punish_type=1");ManagedBanService.Edit r=restore(22,u.requestId);service.preview(22,r);service.save(22,r,"restore");assertEquals("2",scalar("SELECT apply_flag FROM d_taiwan.member_punish_info WHERE m_id=22 AND punish_type=1"));assertEquals("later reason",scalar("SELECT reason FROM d_taiwan.member_punish_info WHERE m_id=22 AND punish_type=1"));
        assertEquals("external",scalar("SELECT admin_id FROM d_taiwan.member_punish_info WHERE m_id=22 AND punish_type=2"));
        sql("INSERT INTO d_taiwan.member_punish_info VALUES(23,1,NOW(),101,2,NOW(),TIMESTAMPADD(DAY,9,NOW()),'DOF-EASY','external issuer case')");reject(409,()->service.preview(23,edit(23,"ban")));ManagedBanService.Edit no=edit(23,"unban");service.preview(23,no);assertEquals("NO_CHANGE",map(service.save(23,no,"unban")).get("state"));assertEquals("2",scalar("SELECT apply_flag FROM d_taiwan.member_punish_info WHERE m_id=23"));
    }
    @Test public void initialBanCanBeUndoneOnlyWhileExactInsertedPenaltyRemains()throws Exception {
        ManagedBanService.Edit b=edit(22,"ban");service.preview(22,b);service.save(22,b,"ban");sql("UPDATE d_taiwan.member_punish_info SET reason='third party edit' WHERE m_id=22");reject(409,()->service.preview(22,restore(22,b.requestId)));
        sql("UPDATE d_taiwan.member_punish_info SET reason='synthetic operator reason' WHERE m_id=22");ManagedBanService.Edit r=restore(22,b.requestId);service.preview(22,r);service.save(22,r,"restore");assertEquals("0",scalar("SELECT COUNT(*) FROM d_taiwan.member_punish_info WHERE m_id=22"));
    }
    @Test public void beforeAndAfterWriteInterruptionsAreReconciledAndFreshIdsBlocked()throws Exception {
        ManagedBanService.Edit b=edit(22,"ban");service.preview(22,b);fault("INSERT INTO `d_taiwan`.`member_punish_info`");reject(503,()->service.save(22,b,"ban"));reject(409,()->service.preview(22,edit(22,"ban")));assertEquals("NOT_APPLIED",map(service.reconcile(22,b.requestId)).get("state"));
        ManagedBanService.Edit next=edit(22,"ban");service.preview(22,next);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");reject(503,()->service.save(22,next,"ban"));assertEquals("PERSISTED",map(service.reconcile(22,next.requestId)).get("state"));assertEquals("1",scalar("SELECT COUNT(*) FROM d_taiwan.member_punish_info"));
    }
    @Test public void explicitConflictAcknowledgmentNeverOverwritesForeignPenalty()throws Exception {
        ManagedBanService.Edit b=edit(22,"ban");service.preview(22,b);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");reject(503,()->service.save(22,b,"ban"));sql("UPDATE d_taiwan.member_punish_info SET admin_id='another issuer' WHERE m_id=22");assertEquals("CONFLICT",map(service.reconcile(22,b.requestId)).get("state"));
        ManagedBanService.Edit keep=edit(22,"accept-current");keep.targetRequestId=b.requestId;service.preview(22,keep);assertEquals("NO_CHANGE",map(service.save(22,keep,"accept-current")).get("state"));assertEquals("another issuer",scalar("SELECT admin_id FROM d_taiwan.member_punish_info WHERE m_id=22"));
    }
    @Test public void protectedAccountsUnknownSchemaAndInvalidBodiesAreRejected()throws Exception {
        reject(409,()->service.preview(1,edit(1,"ban")));reject(409,()->service.preview(24,edit(24,"ban")));ManagedBanService.Edit e=edit(22,"ban");e.requestId=null;reject(400,()->service.preview(22,e));
        ManagedBanService.Edit days=edit(22,"unban");days.days=1;reject(400,()->service.preview(22,days));sql("ALTER TABLE d_taiwan.member_punish_info ADD unknown_setting INT DEFAULT 7");reject(409,()->service.preview(22,edit(22,"ban")));
        ThreadUtil.setUserId(99L);reject(403,()->service.status(22));assertEquals("0",scalar("SELECT COUNT(*) FROM d_taiwan.member_punish_info"));
    }
    @Test public void unresolvedOlderOperationCannotBeHiddenByNewerPreviews()throws Exception {
        ManagedBanService.Edit old=edit(22,"ban");service.preview(22,old);
        sql("UPDATE dnf_service.DOF_OPERATION_JOURNAL SET created_at='2026-01-01 00:00:00' WHERE request_id='"+old.requestId+"'");
        for(int i=0;i<51;i++)service.preview(22,edit(22,"ban"));fault("INSERT INTO `d_taiwan`.`member_punish_info`");reject(503,()->service.save(22,old,"ban"));
        List<?> rows=(List<?>)service.history(22);assertEquals(50,rows.size());assertEquals(old.requestId,map(rows.get(0)).get("requestId"));assertEquals("PREPARED",map(rows.get(0)).get("state"));
    }
    @Test public void accountIdentityChangeInvalidatesOutstandingPreview()throws Exception {
        ManagedBanService.Edit e=edit(22,"ban");service.preview(22,e);sql("UPDATE d_taiwan.accounts SET accountname='replacement-account' WHERE UID=22");reject(409,()->service.save(22,e,"ban"));assertEquals("0",scalar("SELECT COUNT(*) FROM d_taiwan.member_punish_info"));
    }
    @Test public void myisamUsesDurableBeforeAndAfterChecksWithoutTransactionClaims()throws Exception {
        sql("ALTER TABLE d_taiwan.member_punish_info ENGINE=MyISAM");ManagedBanService.Edit e=edit(22,"ban");service.preview(22,e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");reject(503,()->service.save(22,e,"ban"));assertEquals("PERSISTED",map(service.reconcile(22,e.requestId)).get("state"));
    }
    @Test public void concurrentPreviewsHaveOneWinnerAndOneVersionConflict()throws Exception {
        ManagedBanService.Edit a=edit(22,"ban"),b=edit(22,"ban");service.preview(22,a);service.preview(22,b);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try{List<Future<Integer>> out=new ArrayList<>();for(ManagedBanService.Edit e:Arrays.asList(a,b))out.add(pool.submit(()->{ThreadUtil.setUserId(1L);gate.await();try{service.save(22,e,"ban");return 200;}catch(DofFailure failure){return failure.status;}}));gate.countDown();List<Integer> codes=Arrays.asList(out.get(0).get(20,TimeUnit.SECONDS),out.get(1).get(20,TimeUnit.SECONDS));assertTrue(codes.contains(200));assertTrue(codes.contains(409));}finally{pool.shutdownNow();}
        assertEquals("1",scalar("SELECT COUNT(*) FROM d_taiwan.member_punish_info"));
    }
}
