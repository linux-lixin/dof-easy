package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.fasterxml.jackson.databind.*;
import org.junit.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Composes the existing guarded disposable fixture; never creates a real game character. */
public class ManagedPostalBatchContractTest {
    private ManagedPostalContractTest fixture;private DriverManagerDataSource ds;private DofAdminService access;
    private ManagedPostalService postal;private ManagedOperationJournal journal;private ManagedPostalBatchService batch;private final ObjectMapper json=new ObjectMapper();
    @Before public void setup()throws Exception{
        fixture=new ManagedPostalContractTest();fixture.setup();ds=(DriverManagerDataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"access");
        postal=(ManagedPostalService)ReflectionTestUtils.getField(fixture,"service");journal=(ManagedOperationJournal)ReflectionTestUtils.getField(postal,"journal");rebuildService();
        sql("INSERT INTO taiwan_cain.charac_info VALUES(124,22,'second test role',0,'2020-01-02 00:00:00',X'00FF')");
    }
    private void rebuildService(){batch=new ManagedPostalBatchService();ReflectionTestUtils.setField(batch,"journal",journal);ReflectionTestUtils.setField(batch,"postal",postal);}
    @After public void cleanup()throws Exception{if(fixture!=null){if(access!=null&&ds!=null)ReflectionTestUtils.setField(access,"dataSource",ds);fixture.cleanup();}}
    private void sql(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(text);}}
    private String scalar(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(text)){assertTrue(r.next());return r.getString(1);}}
    private Map<?,?> map(Object v){return (Map<?,?>)v;}
    private List<?> rows(Object v){return (List<?>)map(v).get("targets");}
    private ManagedPostalBatchService.Edit edit(String recipients)throws Exception{
        ManagedPostalBatchService.Edit e=new ManagedPostalBatchService.Edit();e.requestId=UUID.randomUUID().toString();e.recipients=json.readTree(recipients);e.mail=json.readTree("{\"gold\":1,\"message\":\"synthetic batch body\"}");e.reason="synthetic fixed batch contract";e.revision=(String)map(batch.validate(e)).get("revision");return e;
    }
    private ManagedPostalBatchService.Link link(ManagedPostalBatchService.Edit e){ManagedPostalBatchService.Link l=new ManagedPostalBatchService.Link();l.requestId=e.requestId;l.revision=e.revision;for(Object row:rows(batch.read(e.requestId)))if(map(row).get("state").equals("PREVIEWED")){l.childRequestId=(String)map(row).get("requestId");break;}return l;}
    private void reject(int code,Runnable action){try{action.run();fail("Unsafe batch accepted");}catch(DofFailure e){assertEquals(e.getMessage(),code,e.status);}}
    private ManagedPostalService.Edit child(ManagedPostalBatchService.Edit root,long id)throws Exception{
        ManagedPostalService.Edit e=new ManagedPostalService.Edit();e.receiveCharacNo=json.valueToTree(id);e.requestId=ManagedPostalBatchService.childId(root.requestId,id);e.operation="send";e.reason=root.reason;e.mail=root.mail;e.batchRequestId=root.requestId;
        try(Connection c=ds.getConnection()){e.revision=journal.require(c,"postal."+id,e.requestId).beforeHash;}return e;
    }
    private void fault(String prefix,boolean loseCommit)throws Exception{
        DataSource broken=mock(DataSource.class);AtomicBoolean armed=new AtomicBoolean(true);
        when(broken.getConnection()).thenAnswer(call->{Connection c=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,m,args)->{
            if(m.getName().equals("prepareStatement")&&prefix!=null&&args[0].toString().startsWith(prefix)&&armed.compareAndSet(true,false))throw new SQLException("synthetic batch interruption");
            try{Object out=m.invoke(c,args);if(m.getName().equals("commit")&&loseCommit&&armed.compareAndSet(true,false))throw new SQLException("synthetic lost child commit reply");return out;}catch(InvocationTargetException e){throw e.getCause();}
        });});ReflectionTestUtils.setField(access,"dataSource",broken);
    }
    @Test public void atomicPreviewReservesChildrenAndCannotIssueUntilParentStarts()throws Exception{
        ManagedPostalBatchService.Edit e=edit("[124,123]");assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL"));Map<?,?> preview=map(batch.preview(e));assertEquals("PREVIEWED",preview.get("phase"));assertEquals("3",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL"));
        ManagedPostalService.Edit child=child(e,123);reject(409,()->postal.save(child));child.batchRequestId=null;reject(409,()->postal.save(child));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
        assertEquals("READY_FOR_NEXT",map(batch.start(e)).get("phase"));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
        ManagedPostalBatchService.Link first=link(e);batch.step(e.requestId,first);batch.step(e.requestId,first);assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
        ManagedPostalBatchService.Link missing=link(e);missing.childRequestId=null;reject(400,()->batch.step(e.requestId,missing));ManagedPostalBatchService.Link foreign=link(e);foreign.childRequestId=UUID.randomUUID().toString();reject(409,()->batch.step(e.requestId,foreign));
        Map<?,?> done=map(batch.step(e.requestId,link(e)));assertEquals("COMPLETED",done.get("phase"));assertEquals(true,map(done.get("progress")).get("allSent"));assertEquals("2",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
        batch.start(e);batch.step(e.requestId,link(e));batch.preview(e);assertEquals("2",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));assertEquals(1,((List<?>)batch.history()).size());
    }
    @Test public void restartUsesOriginalChildReceiptsEvenAfterNativeConsumption()throws Exception{
        ManagedPostalBatchService.Edit e=edit("[123,124]");batch.preview(e);batch.start(e);batch.step(e.requestId,link(e));sql("DELETE FROM taiwan_cain_2nd.postal WHERE receive_charac_no=123");rebuildService();
        Map<?,?> restored=map(batch.read(e.requestId));assertEquals(1,map(restored.get("progress")).get("sent"));assertEquals("COMPLETED",map(batch.step(e.requestId,link(e))).get("phase"));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal WHERE receive_charac_no=123"));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal WHERE receive_charac_no=124"));
    }
    @Test public void allChildrenCommittedBeforeParentFinalizationRecoverWithoutResending()throws Exception{
        ManagedPostalBatchService.Edit e=edit("[123,124]");batch.preview(e);batch.start(e);postal.save(child(e,123));postal.save(child(e,124));rebuildService();assertEquals("ALL_RESULTS_RECORDED",map(batch.read(e.requestId)).get("phase"));assertEquals("COMPLETED",map(batch.step(e.requestId,link(e))).get("phase"));assertEquals("2",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void lostChildCommitReplyIsReconciledFromReceiptAndDoesNotReissue()throws Exception{
        ManagedPostalBatchService.Edit e=edit("[123,124]");batch.preview(e);batch.start(e);fault(null,true);Map<?,?> result=map(batch.step(e.requestId,link(e)));ReflectionTestUtils.setField(access,"dataSource",ds);assertEquals(1,map(result.get("progress")).get("sent"));batch.step(e.requestId,link(e));assertEquals("2",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void preflightFailureIsRecordedAndOnlyConfirmedUnsentTargetsCanBeRetried()throws Exception{
        ManagedPostalBatchService.Edit e=edit("[123,124]");batch.preview(e);batch.start(e);batch.step(e.requestId,link(e));sql("UPDATE taiwan_cain.charac_info SET charac_name='changed synthetic' WHERE charac_no=124");Map<?,?> result=map(batch.step(e.requestId,link(e)));assertEquals("COMPLETED_WITH_FAILURES",result.get("phase"));assertEquals(1,map(result.get("progress")).get("notApplied"));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
        ManagedPostalBatchService.Edit retry=new ManagedPostalBatchService.Edit();retry.requestId=UUID.randomUUID().toString();retry.recipients=json.readTree("[123]");retry.mail=e.mail;retry.reason="synthetic selective retry";retry.retryOf=e.requestId;reject(409,()->batch.validate(retry));
        retry.recipients=json.readTree("[124]");reject(409,()->batch.validate(retry));sql("UPDATE taiwan_cain.charac_info SET charac_name='second test role' WHERE charac_no=124");retry.revision=(String)map(batch.validate(retry)).get("revision");batch.preview(retry);batch.start(retry);assertEquals("COMPLETED",map(batch.step(retry.requestId,link(retry))).get("phase"));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal WHERE receive_charac_no=123"));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal WHERE receive_charac_no=124"));
    }
    @Test public void myisamUnknownBlocksProgressAndCannotBeTurnedIntoARewardRetry()throws Exception{
        sql("ALTER TABLE taiwan_cain_2nd.postal ENGINE=MyISAM");ManagedPostalBatchService.Edit e=edit("[123,124]");batch.preview(e);batch.start(e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json=",false);Map<?,?> result=map(batch.step(e.requestId,link(e)));ReflectionTestUtils.setField(access,"dataSource",ds);assertEquals("NEEDS_RECONCILIATION",result.get("phase"));batch.reconcile(e.requestId,link(e));reject(409,()->batch.step(e.requestId,link(e)));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
        ManagedPostalBatchService.Edit retry=new ManagedPostalBatchService.Edit();retry.recipients=json.readTree("[123]");retry.mail=e.mail;retry.reason="synthetic forbidden uncertain retry";retry.retryOf=e.requestId;reject(409,()->batch.validate(retry));
        ManagedPostalService.Edit ack=new ManagedPostalService.Edit();ack.receiveCharacNo=json.valueToTree(123);ack.requestId=UUID.randomUUID().toString();ack.operation="accept-current";ack.targetRequestId=ManagedPostalBatchService.childId(e.requestId,123);ack.reason="synthetic manual review keeps unknown outcome";ack.revision=(String)map(postal.status(123)).get("revision");postal.preview(123,ack);postal.save(ack);
        assertEquals("COMPLETED_WITH_UNCERTAINTY",map(batch.step(e.requestId,link(e))).get("phase"));reject(409,()->batch.validate(retry));assertEquals("2",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void parentPreviewFailureRollsBackEveryChildReservation()throws Exception{
        ManagedPostalBatchService.Edit e=edit("[123,124]");DataSource broken=mock(DataSource.class);AtomicInteger inserts=new AtomicInteger();
        when(broken.getConnection()).thenAnswer(call->{Connection c=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,m,args)->{
            if(m.getName().equals("prepareStatement")&&args[0].toString().startsWith("INSERT INTO `dnf_service`.`DOF_OPERATION_JOURNAL`")&&inserts.incrementAndGet()==3)throw new SQLException("synthetic last child preview refusal");
            try{return m.invoke(c,args);}catch(InvocationTargetException ex){throw ex.getCause();}
        });});ReflectionTestUtils.setField(access,"dataSource",broken);reject(503,()->batch.preview(e));ReflectionTestUtils.setField(access,"dataSource",ds);assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL"));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void changedVersionOrRequestContentCannotStartOrRetargetFrozenBatch()throws Exception{
        ManagedPostalBatchService.Edit e=edit("[123,124]");batch.preview(e);ManagedPostalService.Edit other=child(e,123);other.batchRequestId=null;other.requestId=UUID.randomUUID().toString();postal.preview(123,other);postal.save(other);reject(409,()->batch.start(e));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));e.reason="different payload";reject(409,()->batch.preview(e));
        for(String ids:Arrays.asList("[]","[123,123]","[1.5]","[0]")){e.recipients=json.readTree(ids);reject(400,()->batch.validate(e));}
        ReflectionTestUtils.setField(access,"databaseUuid",UUID.randomUUID().toString());reject(409,()->batch.history());
    }
    @Test public void concurrentStepsNeverIssueDuplicateMailToAnyTarget()throws Exception{
        ManagedPostalBatchService.Edit e=edit("[123,124]");batch.preview(e);batch.start(e);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{Callable<Object> call=()->{ThreadUtil.setUserId(1L);start.await();try{return batch.step(e.requestId,link(e));}catch(DofFailure expected){assertEquals(409,expected.status);return null;}};Future<Object>a=pool.submit(call),b=pool.submit(call);start.countDown();a.get(30,TimeUnit.SECONDS);b.get(30,TimeUnit.SECONDS);batch.reconcile(e.requestId,link(e));batch.step(e.requestId,link(e));batch.step(e.requestId,link(e));assertEquals("COMPLETED",map(batch.read(e.requestId)).get("phase"));assertEquals("2:2",scalar("SELECT CONCAT(COUNT(*),':',COUNT(DISTINCT receive_charac_no)) FROM taiwan_cain_2nd.postal"));}finally{pool.shutdownNow();}
    }
    @Test public void missingParentCannotBeForgedAndOldStandaloneFingerprintStillReplays()throws Exception{
        ManagedPostalBatchService.Edit e=edit("[123]");ManagedPostalService.Edit standalone=new ManagedPostalService.Edit();standalone.receiveCharacNo=json.valueToTree(123);standalone.mail=e.mail;standalone.reason=e.reason;standalone.operation="send";standalone.requestId=UUID.randomUUID().toString();standalone.revision=(String)map(postal.status(123)).get("revision");postal.preview(123,standalone);postal.save(standalone);assertEquals("PERSISTED",map(postal.save(standalone)).get("state"));
        standalone.requestId=UUID.randomUUID().toString();standalone.batchRequestId=e.requestId;standalone.revision=(String)map(postal.status(123)).get("revision");reject(409,()->postal.preview(123,standalone));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void oneConnectionPoolAndRealAuthenticationReadsCanFinishAndReadTask()throws Exception{
        ManagedPostalBatchService.Edit e=edit("[123,124]");Object originalAccounts=ReflectionTestUtils.getField(access,"accountVODao");
        com.zaxxer.hikari.HikariConfig cfg=new com.zaxxer.hikari.HikariConfig();cfg.setDataSource(ds);cfg.setMaximumPoolSize(1);cfg.setMinimumIdle(0);cfg.setConnectionTimeout(1000);
        try(com.zaxxer.hikari.HikariDataSource pool=new com.zaxxer.hikari.HikariDataSource(cfg)){
            com.aiyi.game.dnfserver.dao.AccountVODao accounts=mock(com.aiyi.game.dnfserver.dao.AccountVODao.class);
            when(accounts.get(1L)).thenAnswer(call->{
                try(Connection c=pool.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT admin FROM d_taiwan.accounts WHERE UID=1")){
                    assertTrue(r.next());com.aiyi.game.dnfserver.entity.AccountVO actor=new com.aiyi.game.dnfserver.entity.AccountVO();actor.setUid(1L);actor.setAdmin(r.getInt(1)!=0);return actor;
                }
            });ReflectionTestUtils.setField(access,"accountVODao",accounts);ReflectionTestUtils.setField(access,"dataSource",pool);
            batch.preview(e);batch.start(e);batch.step(e.requestId,link(e));batch.step(e.requestId,link(e));assertEquals("COMPLETED",map(batch.read(e.requestId)).get("phase"));assertEquals(1,((List<?>)batch.history()).size());
        }finally{ReflectionTestUtils.setField(access,"dataSource",ds);ReflectionTestUtils.setField(access,"accountVODao",originalAccounts);}
        assertEquals("2",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
}
