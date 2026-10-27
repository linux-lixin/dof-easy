package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual journal/native SQL on the guarded synthetic batch fixture. */
public class ManagedPostalBatchControlContractTest {
    private ManagedPostalBatchContractTest fixture;private ManagedPostalBatchService batch;private ManagedPostalService postal;
    private DofAdminService access;private DriverManagerDataSource ds;private final ObjectMapper json=new ObjectMapper();
    @Before public void setup()throws Exception{
        fixture=new ManagedPostalBatchContractTest();fixture.setup();batch=(ManagedPostalBatchService)ReflectionTestUtils.getField(fixture,"batch");postal=(ManagedPostalService)ReflectionTestUtils.getField(fixture,"postal");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"access");ds=(DriverManagerDataSource)ReflectionTestUtils.getField(fixture,"ds");
    }
    @After public void cleanup()throws Exception{if(fixture!=null){if(access!=null&&ds!=null)ReflectionTestUtils.setField(access,"dataSource",ds);fixture.cleanup();}}
    private void sql(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(text);}}
    private String scalar(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(text)){assertTrue(r.next());return r.getString(1);}}
    private Map<?,?> map(Object v){return (Map<?,?>)v;}
    private void reject(int code,Runnable action){try{action.run();fail("Unsafe task control accepted");}catch(DofFailure e){assertEquals(e.getMessage(),code,e.status);}}
    private ManagedPostalBatchService.Edit root()throws Exception{return ReflectionTestUtils.invokeMethod(fixture,"edit","[123,124]");}
    private ManagedPostalBatchService.Link link(ManagedPostalBatchService.Edit e){return ReflectionTestUtils.invokeMethod(fixture,"link",e);}
    private ManagedPostalBatchService.Control control(String source,String operation){ManagedPostalBatchService.Control c=new ManagedPostalBatchService.Control();c.requestId=UUID.randomUUID().toString();c.revision=(String)map(batch.read(source)).get("controlRevision");c.reason="synthetic controlled task transition";c.operation=operation;return c;}
    private void cancel(ManagedPostalBatchService.Edit root){ManagedPostalBatchService.Control c=control(root.requestId,"cancel-pending");batch.controlPreview(root.requestId,c);assertEquals("PERSISTED",map(batch.controlSave(root.requestId,c)).get("state"));}
    private ManagedPostalBatchService.Edit retry(ManagedPostalBatchService.Edit source,String ids)throws Exception{
        ManagedPostalBatchService.Edit e=new ManagedPostalBatchService.Edit();e.requestId=UUID.randomUUID().toString();e.mail=source.mail;e.reason="synthetic linked takeover";e.retryOf=source.requestId;e.recipients=json.readTree(ids);return e;
    }
    private void previewRetry(ManagedPostalBatchService.Edit e){e.revision=(String)map(batch.validate(e)).get("revision");batch.preview(e);}
    @Test public void cancelBeforeStartIsPermanentAndAllMetadataCommitsTogether()throws Exception{
        ManagedPostalBatchService.Edit e=root();batch.preview(e);ManagedPostalBatchService.Control c=control(e.requestId,"cancel-pending");batch.controlPreview(e.requestId,c);assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
        assertEquals("PERSISTED",map(batch.controlSave(e.requestId,c)).get("state"));assertEquals("PERSISTED",map(batch.controlSave(e.requestId,c)).get("state"));assertEquals("COMPLETED_WITH_FAILURES",map(batch.start(e)).get("phase"));assertEquals(2,map(map(batch.read(e.requestId)).get("progress")).get("notApplied"));
        ManagedPostalService.Edit child=ReflectionTestUtils.invokeMethod(fixture,"child",e,123L);assertEquals("NOT_APPLIED",map(postal.save(child)).get("state"));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));assertEquals(1,((List<?>)batch.controlHistory(e.requestId)).size());c.reason="changed content";reject(409,()->batch.controlSave(e.requestId,c));
    }
    @Test public void partialSuccessIsPreservedAndOldStepsCannotContinue()throws Exception{
        ManagedPostalBatchService.Edit e=root();batch.preview(e);batch.start(e);batch.step(e.requestId,link(e));cancel(e);batch.step(e.requestId,link(e));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));Map<?,?> progress=map(map(batch.read(e.requestId)).get("progress"));assertEquals(1,progress.get("sent"));assertEquals(1,progress.get("notApplied"));
    }
    @Test public void stalePreviewCannotCancelTargetsThatHaveProgressed()throws Exception{
        ManagedPostalBatchService.Edit e=root();batch.preview(e);batch.start(e);ManagedPostalBatchService.Control c=control(e.requestId,"cancel-pending");batch.controlPreview(e.requestId,c);batch.step(e.requestId,link(e));reject(409,()->batch.controlSave(e.requestId,c));assertEquals(1,map(map(batch.read(e.requestId)).get("progress")).get("ready"));assertEquals("PREVIEWED",map(((List<?>)batch.controlHistory(e.requestId)).get(0)).get("state"));
    }
    @Test public void issuerProgressAfterDurableControlIntentInvalidatesTheEntireCancellation()throws Exception{
        ManagedPostalBatchService.Edit e=root();batch.preview(e);batch.start(e);ManagedPostalBatchService.Control control=control(e.requestId,"cancel-pending");batch.controlPreview(e.requestId,control);
        ManagedPostalService.Edit child=ReflectionTestUtils.invokeMethod(fixture,"child",e,123L);AtomicBoolean once=new AtomicBoolean(true);DataSource interleaved=mock(DataSource.class);
        when(interleaved.getConnection()).thenAnswer(call->{Connection conn=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,m,args)->{
            try{Object result=m.invoke(conn,args);
                if(m.getName().equals("setAutoCommit")&&Boolean.FALSE.equals(args[0])&&once.compareAndSet(true,false)){
                    // Model the legal lock reacquisition gap using the actual issuer
                    // on another connection, not a fabricated journal outcome.
                    try(Statement s=conn.createStatement()){s.execute("UNLOCK TABLES");}
                    ReflectionTestUtils.setField(access,"dataSource",ds);assertEquals("PERSISTED",map(postal.save(child)).get("state"));
                }return result;
            }catch(InvocationTargetException ex){throw ex.getCause();}
        });});ReflectionTestUtils.setField(access,"dataSource",interleaved);
        assertEquals("NOT_APPLIED",map(batch.controlSave(e.requestId,control)).get("state"));ReflectionTestUtils.setField(access,"dataSource",ds);Map<?,?> progress=map(map(batch.read(e.requestId)).get("progress"));assertEquals(1,progress.get("sent"));assertEquals(1,progress.get("ready"));assertEquals(0,progress.get("notApplied"));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void interruptionDuringCancellationRollsBackEveryChildAndParent()throws Exception{
        ManagedPostalBatchService.Edit e=root();batch.preview(e);ManagedPostalBatchService.Control c=control(e.requestId,"cancel-pending");batch.controlPreview(e.requestId,c);AtomicInteger updates=new AtomicInteger();DataSource broken=mock(DataSource.class);
        when(broken.getConnection()).thenAnswer(call->{Connection conn=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,m,args)->{
            if(m.getName().equals("prepareStatement")&&args[0].toString().contains("state='NOT_APPLIED'")&&updates.incrementAndGet()==2)throw new SQLException("synthetic second cancellation interruption");try{return m.invoke(conn,args);}catch(InvocationTargetException ex){throw ex.getCause();}
        });});ReflectionTestUtils.setField(access,"dataSource",broken);reject(503,()->batch.controlSave(e.requestId,c));ReflectionTestUtils.setField(access,"dataSource",ds);
        assertEquals("PREVIEWED",map(batch.read(e.requestId)).get("state"));assertEquals(2,map(map(batch.read(e.requestId)).get("progress")).get("ready"));assertEquals("NOT_APPLIED",map(batch.controlReconcile(e.requestId,c.requestId)).get("state"));assertEquals("NOT_APPLIED",map(batch.controlSave(e.requestId,c)).get("state"));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void lostCommitResponseRecoversCompletedControlWithoutReapplying()throws Exception{
        ManagedPostalBatchService.Edit e=root();batch.preview(e);ManagedPostalBatchService.Control c=control(e.requestId,"cancel-pending");batch.controlPreview(e.requestId,c);ReflectionTestUtils.invokeMethod(fixture,"fault",null,true);reject(503,()->batch.controlSave(e.requestId,c));ReflectionTestUtils.setField(access,"dataSource",ds);
        assertEquals("PERSISTED",map(batch.controlReconcile(e.requestId,c.requestId)).get("state"));assertEquals("PERSISTED",map(batch.controlSave(e.requestId,c)).get("state"));assertEquals(2,map(map(batch.read(e.requestId)).get("progress")).get("notApplied"));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    private void secondAdmin()throws Exception{
        sql("UPDATE d_taiwan.accounts SET admin=0 WHERE UID=1");sql("INSERT INTO d_taiwan.accounts VALUES(2,'second_admin',7)");AccountVODao accounts=mock(AccountVODao.class);AccountVO admin=new AccountVO();admin.setUid(2L);admin.setAdmin(true);when(accounts.get(2L)).thenReturn(admin);ReflectionTestUtils.setField(access,"accountVODao",accounts);ThreadUtil.setUserId(2L);
    }
    @Test public void replacementAdminUsesLinkedTasksAndCannotReuseAnySuccessfulRetryAncestor()throws Exception{
        ManagedPostalBatchService.Edit a=root();batch.preview(a);secondAdmin();cancel(a);assertEquals("1",scalar("SELECT actor FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+a.requestId+"'"));
        ManagedPostalBatchService.Edit b=retry(a,"[123]");previewRetry(b);ManagedPostalBatchService.Edit duplicate=retry(a,"[123]");reject(409,()->batch.validate(duplicate));cancel(b);
        ManagedPostalBatchService.Edit c=retry(b,"[123]");previewRetry(c);batch.start(c);batch.step(c.requestId,link(c));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));sql("DELETE FROM taiwan_cain_2nd.postal");reject(409,()->batch.validate(duplicate));ManagedPostalBatchService.Edit sibling=retry(b,"[123]");reject(409,()->batch.validate(sibling));
        ManagedPostalBatchService.Edit remaining=retry(a,"[124]");previewRetry(remaining);batch.start(remaining);batch.step(remaining.requestId,link(remaining));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));assertEquals(2L,map(batch.read(c.requestId)).get("actor"));
    }
    @Test public void cancellingReadyTargetsPreservesUnknownAndOtherAdminCanFinalizeReceipts()throws Exception{
        sql("ALTER TABLE taiwan_cain_2nd.postal ENGINE=MyISAM");ManagedPostalBatchService.Edit e=root();batch.preview(e);batch.start(e);ReflectionTestUtils.invokeMethod(fixture,"fault","UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json=",false);batch.step(e.requestId,link(e));ReflectionTestUtils.setField(access,"dataSource",ds);batch.reconcile(e.requestId,link(e));secondAdmin();cancel(e);
        assertEquals(1,map(map(batch.read(e.requestId)).get("progress")).get("pending"));assertEquals(1,map(map(batch.read(e.requestId)).get("progress")).get("notApplied"));reject(409,()->batch.controlPreview(e.requestId,control(e.requestId,"finalize")));
        String child=ManagedPostalBatchService.childId(e.requestId,123);Map<?,?> original=((List<?>)postal.history(123)).stream().map(this::map).filter(r->child.equals(r.get("requestId"))).findFirst().get();ManagedPostalReceiptService receipts=new ManagedPostalReceiptService();ReflectionTestUtils.setField(receipts,"journal",ReflectionTestUtils.getField(postal,"journal"));ReflectionTestUtils.setField(receipts,"postal",postal);ManagedPostalReceiptService.Edit ack=new ManagedPostalReceiptService.Edit();ack.requestId=UUID.randomUUID().toString();ack.revision=(String)original.get("receiptRevision");ack.reason="synthetic preserve unknown";receipts.preview(123,child,ack);receipts.save(123,child,ack);
        ManagedPostalBatchService.Control done=control(e.requestId,"finalize");batch.controlPreview(e.requestId,done);batch.controlSave(e.requestId,done);assertEquals("COMPLETED_WITH_UNCERTAINTY",map(batch.read(e.requestId)).get("phase"));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
}
