package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.service.extension.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.mockito.Mockito.*;
import java.util.*;
import static org.junit.Assert.*;

/** Real SQL on disposable fixtures; no native tool format or gameplay claim. */
public class NativeEquipmentBatchContractTest {
    private NativeEquipmentDatasetContractTest fixture;private DataSource ds;private DofAdminService access;private ActivityConfigService activity;private NativeEquipmentBatchService batches;
    @Before public void setup()throws Exception{fixture=new NativeEquipmentDatasetContractTest();fixture.setup();ds=(DataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"access");activity=(ActivityConfigService)ReflectionTestUtils.getField(fixture,"service");batches=new NativeEquipmentBatchService();ReflectionTestUtils.setField(batches,"activity",activity);ReflectionTestUtils.setField(batches,"journal",ReflectionTestUtils.getField(activity,"journal"));sql("DELETE FROM dof_native.inheritance WHERE code>4");}
    @After public void cleanup()throws Exception{if(fixture!=null)fixture.cleanup();}
    private void sql(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(text);}}
    private String scalar(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(text)){assertTrue(r.next());return r.getString(1);}}
    private Map<String,Object> map(Object x){return (Map<String,Object>)x;}
    private String version(){return map(batches.state("inheritance")).get("revision").toString();}
    private NativeEquipmentBatchService.Edit edit(){NativeEquipmentBatchService.Edit e=new NativeEquipmentBatchService.Edit();e.requestId=UUID.randomUUID().toString();e.revision=version();e.reason="disposable batch test";return e;}
    private Map<String,Object> detail(String id){return map(batches.detail("inheritance",id,1,100));}
    private List<Map<String,Object>> items(Map<String,Object> task){return (List<Map<String,Object>>)task.get("items");}
    private void reject(int status,Checked action)throws Exception{try{action.run();fail("expected refusal");}catch(ActivityConfigService.Failure e){assertEquals(e.getMessage(),status,e.status);}catch(DofFailure e){assertEquals(e.getMessage(),status,e.status);}}
    private interface Checked{void run()throws Exception;}
    private Map<String,Object> step(Map<String,Object> task){return map(batches.step("inheritance",task.get("requestId").toString(),task.get("revision").toString()));}
    private void cancel(Map<String,Object> task){NativeEquipmentBatchService.Control e=new NativeEquipmentBatchService.Control();e.requestId=UUID.randomUUID().toString();e.revision=task.get("revision").toString();e.reason="stop unsubmitted only";e.operation="cancel";String id=task.get("requestId").toString();batches.control("inheritance",id,e,false);assertTrue(map(batches.controlDetail("inheritance",id.toUpperCase(Locale.ROOT),e.requestId)).containsKey("request"));assertEquals(1L,map(batches.controlHistory("inheritance",id,1,20)).get("total"));batches.control("inheritance",id,e,true);batches.control("inheritance",id,e,true);assertFalse(map(batches.controlDetail("inheritance",id,e.requestId)).containsKey("request"));}
    @Test public void clearPersistsEverySnapshotAndRestoresOriginalRowsWithIndependentTask()throws Exception{
        NativeEquipmentBatchService.Edit e=edit();Map<String,Object> preview=map(batches.previewClear("inheritance",e));assertEquals("5",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));assertEquals(preview.get("requestId"),map(batches.previewClear("inheritance",e)).get("requestId"));
        Map<String,Object> done=step(preview);assertEquals("PERSISTED",done.get("state"));assertEquals("0",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));assertEquals("5",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE namespace='activity.batch.item.inheritance' AND state='PERSISTED'"));
        reject(409,()->batches.step("inheritance",e.requestId,preview.get("revision").toString()));
        NativeEquipmentBatchService.Edit restore=edit();restore.sourceTaskId=e.requestId;Map<String,Object> restored=step(map(batches.previewRestore("inheritance",restore)));assertEquals("PERSISTED",restored.get("state"));assertEquals("5",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));assertEquals("opaque/01",scalar("SELECT Return_quantity FROM dof_native.inheritance WHERE code=0"));
    }
    @Test public void importUsesBoundWholeFileAndPreservesUnmentionedRules()throws Exception{
        ByteArrayOutputStream file=new ByteArrayOutputStream();activity.exportDataset("inheritance",file);sql("UPDATE dof_native.inheritance SET differ_lv=6 WHERE code=0");sql("INSERT INTO dof_native.inheritance VALUES(9,'3037','2',7)");NativeEquipmentBatchService.Edit e=edit();Map<String,Object> preview=map(batches.previewImport("inheritance",e,new ByteArrayInputStream(file.toByteArray())));assertEquals("6",scalar("SELECT differ_lv FROM dof_native.inheritance WHERE code=0"));Map<String,Object> done=step(preview);assertEquals("PERSISTED",done.get("state"));assertEquals("5",scalar("SELECT differ_lv FROM dof_native.inheritance WHERE code=0"));assertEquals("7",scalar("SELECT differ_lv FROM dof_native.inheritance WHERE code=9"));assertEquals("6",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));
        NativeEquipmentBatchService.Edit other=edit();reject(409,()->batches.previewImport("inheritance",other,new ByteArrayInputStream(new String(file.toByteArray(),StandardCharsets.UTF_8).replace("opaque/01","opaque/02").getBytes(StandardCharsets.UTF_8))));
    }
    @Test public void staleTableOrLateInvalidRuleRollsBackAllPreviewRows()throws Exception{
        NativeEquipmentBatchService.Edit old=edit();sql("UPDATE dof_native.inheritance SET differ_lv=6 WHERE code=1");reject(409,()->batches.previewClear("inheritance",old));assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL"));
        sql("INSERT INTO dof_native.inheritance VALUES(2147483648,'3037','1',5)");NativeEquipmentBatchService.Edit invalid=edit();reject(409,()->batches.previewClear("inheritance",invalid));assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL"));assertEquals("6",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));
    }
    @Test public void reservationBlocksOrdinaryAndOtherBatchUntilExplicitCancellation()throws Exception{
        NativeEquipmentBatchService.Edit e=edit();Map<String,Object> task=map(batches.previewClear("inheritance",e));NativeEquipmentBatchService.Edit other=edit();reject(409,()->batches.previewClear("inheritance",other));
        Map<String,Object> page=activity.page("inheritance",1,20);Map<String,Object> row=map(((List<?>)page.get("rows")).get(0));ActivityConfigService.Edit single=new ActivityConfigService.Edit();single.requestId=UUID.randomUUID().toString();single.operation="upsert";single.revision=row.get("revision").toString();single.values=map(row.get("values"));reject(409,()->activity.preview("inheritance",single));
        cancel(task);assertEquals("NOT_APPLIED",detail(e.requestId).get("state"));assertEquals("5",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));activity.preview("inheritance",single);assertEquals("PREVIEWED",map(batches.previewClear("inheritance",other)).get("state"));
    }
    @Test public void preparedChildIsNeverExecutedAgainAndRequiresOriginalReconciliation()throws Exception{
        NativeEquipmentBatchService.Edit e=edit();Map<String,Object> task=map(batches.previewClear("inheritance",e));String child=items(task).get(0).get("requestId").toString();sql("UPDATE dnf_service.DOF_OPERATION_JOURNAL SET state='PREPARED' WHERE request_id IN ('"+e.requestId+"','"+child+"')");sql("DELETE FROM dof_native.inheritance WHERE code=0");
        Map<String,Object> read=detail(e.requestId);Map<String,Object> stopped=step(read);assertEquals("4",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));assertEquals("PREPARED",items(stopped).get(0).get("state"));
        Map<String,Object> reconciled=map(batches.reconcile("inheritance",e.requestId,child,stopped.get("revision").toString()));assertEquals("PERSISTED",items(reconciled).get(0).get("state"));assertEquals("PERSISTED",step(reconciled).get("state"));assertEquals("0",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));
    }
    @Test public void conflictingPreparedChildNeedsAuditedAcknowledgmentAndCannotBeRestoredAsSuccess()throws Exception{
        NativeEquipmentBatchService.Edit e=edit();Map<String,Object> task=map(batches.previewClear("inheritance",e));String child=items(task).get(0).get("requestId").toString();sql("UPDATE dnf_service.DOF_OPERATION_JOURNAL SET state='PREPARED' WHERE request_id IN ('"+e.requestId+"','"+child+"')");sql("UPDATE dof_native.inheritance SET differ_lv=8 WHERE code=0");task=map(batches.reconcile("inheritance",e.requestId,child,detail(e.requestId).get("revision").toString()));assertEquals("CONFLICT",items(task).get(0).get("state"));cancel(task);
        NativeEquipmentBatchService.Control ack=new NativeEquipmentBatchService.Control();ack.requestId=UUID.randomUUID().toString();ack.revision=detail(e.requestId).get("revision").toString();ack.reason="retain current; original unknown";ack.operation="accept-current";ack.childRequestId=child;ack.currentRevision=map(batches.item("inheritance",e.requestId,child)).get("currentRevision").toString();batches.control("inheritance",e.requestId,ack,false);batches.control("inheritance",e.requestId,ack,true);assertEquals("8",scalar("SELECT differ_lv FROM dof_native.inheritance WHERE code=0"));
        NativeEquipmentBatchService.Edit restore=edit();restore.sourceTaskId=e.requestId;Map<String,Object> done=step(map(batches.previewRestore("inheritance",restore)));assertEquals(0L,map(done.get("summary")).get("total"));assertEquals("5",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));
    }
    @Test public void moreThanOneThousandPreviewedRowsUsePagedIndependentReceiptsAndPartialRecovery()throws Exception{
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO dof_native.inheritance VALUES(?,'3037','1',5)")){for(int i=5;i<1005;i++){p.setInt(1,i);p.addBatch();}p.executeBatch();}
        NativeEquipmentBatchService.Edit e=edit();Map<String,Object> task=map(batches.previewClear("inheritance",e));assertEquals(1005L,map(task.get("summary")).get("total"));Map<String,Object> last=map(batches.detail("inheritance",e.requestId,11,100));assertEquals(5,items(last).size());assertEquals("1000",items(last).get(0).get("targetKey"));
        task=step(task);assertEquals("985",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));cancel(task);assertEquals("985",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));NativeEquipmentBatchService.Edit restore=edit();restore.sourceTaskId=e.requestId;Map<String,Object> restored=step(map(batches.previewRestore("inheritance",restore)));assertEquals("PERSISTED",restored.get("state"));assertEquals("1005",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));
    }
    @Test public void missingChildOrChangedPlanFailsClosedAndRoleRevocationStopsSteps()throws Exception{
        NativeEquipmentBatchService.Edit e=edit();Map<String,Object> task=map(batches.previewClear("inheritance",e));String child=items(task).get(0).get("requestId").toString();sql("DELETE FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+child+"'");reject(409,()->batches.detail("inheritance",e.requestId,1,20));sql("UPDATE d_taiwan.accounts SET admin=0 WHERE UID=1");reject(403,()->batches.step("inheritance",e.requestId,task.get("revision").toString()));assertEquals("5",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));
    }
    private DataSource faults(String mode)throws Exception{
        AtomicBoolean once=new AtomicBoolean();AtomicInteger inserts=new AtomicInteger();DataSource proxy=mock(DataSource.class);
        when(proxy.getConnection()).thenAnswer(call->{Connection real=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
            try{Object result=m.invoke(real,a);
                if(m.getName().equals("commit")&&mode.equals("commit")&&once.compareAndSet(false,true))throw new SQLException("synthetic lost committed preview response");
                if(m.getName().equals("prepareStatement")&&a[0] instanceof String){String text=(String)a[0];PreparedStatement statement=(PreparedStatement)result;
                    if(text.startsWith("INSERT INTO "+ManagedOperationJournal.TABLE)||text.startsWith("UPDATE "+ManagedOperationJournal.TABLE+" SET state=?"))return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(q,sm,sa)->{
                        if(sm.getName().equals("executeUpdate")){if(mode.equals("insert")&&text.startsWith("INSERT")&&inserts.incrementAndGet()==3)throw new SQLException("synthetic interrupted preview insert");if(mode.equals("finish")&&text.startsWith("UPDATE")&&once.compareAndSet(false,true))throw new SQLException("synthetic lost native completion receipt");}
                        try{return sm.invoke(statement,sa);}catch(InvocationTargetException e){throw e.getCause();}
                    });
                }return result;
            }catch(InvocationTargetException e){throw e.getCause();}
        });});return proxy;
    }
    @Test public void interruptedPreviewRollsBackEveryChildAndCommittedReplyLossReplaysOriginalParent()throws Exception{
        NativeEquipmentBatchService.Edit e=edit();ReflectionTestUtils.setField(access,"dataSource",faults("insert"));try{reject(503,()->batches.previewClear("inheritance",e));}finally{ReflectionTestUtils.setField(access,"dataSource",ds);}assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL"));assertEquals("5",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));
        ReflectionTestUtils.setField(access,"dataSource",faults("commit"));try{reject(503,()->batches.previewClear("inheritance",e));}finally{ReflectionTestUtils.setField(access,"dataSource",ds);}assertEquals("6",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL"));assertEquals("PREVIEWED",map(batches.previewClear("inheritance",e)).get("state"));assertEquals("6",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL"));
    }
    @Test public void actualFailureAfterNativeDeleteSurvivesNewServiceAndNeverRepeatsPreparedChild()throws Exception{
        NativeEquipmentBatchService.Edit e=edit();Map<String,Object> task=map(batches.previewClear("inheritance",e));ReflectionTestUtils.setField(access,"dataSource",faults("finish"));try{reject(503,()->step(task));}finally{ReflectionTestUtils.setField(access,"dataSource",ds);}assertEquals("4",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));
        NativeEquipmentBatchService restarted=new NativeEquipmentBatchService();ReflectionTestUtils.setField(restarted,"activity",activity);ReflectionTestUtils.setField(restarted,"journal",ReflectionTestUtils.getField(activity,"journal"));batches=restarted;Map<String,Object> read=detail(e.requestId);String child=items(read).get(0).get("requestId").toString();assertEquals("PREPARED",items(read).get(0).get("state"));step(read);assertEquals("4",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));read=map(batches.reconcile("inheritance",e.requestId,child,read.get("revision").toString()));assertEquals("PERSISTED",step(read).get("state"));assertEquals("0",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));
    }
    @Test public void simultaneousSegmentsCannotApplySameNativeRowTwice()throws Exception{
        NativeEquipmentBatchService.Edit e=edit();Map<String,Object> task=map(batches.previewClear("inheritance",e));ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{List<Future<String>> futures=new ArrayList<>();for(int i=0;i<2;i++)futures.add(pool.submit(()->{com.aiyi.core.util.thread.ThreadUtil.setUserId(1L);start.await();try{return step(task).get("state").toString();}catch(ActivityConfigService.Failure failure){return Integer.toString(failure.status);}}));start.countDown();List<String> results=new ArrayList<>();for(Future<String> future:futures)results.add(future.get(30,TimeUnit.SECONDS));assertEquals(1,Collections.frequency(results,"PERSISTED"));assertEquals(1,Collections.frequency(results,"409"));assertEquals("0",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));assertEquals("5",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE namespace='activity.batch.item.inheritance' AND state='PERSISTED'"));}finally{pool.shutdownNow();}
    }
    @Test public void crossBorderAndRecoveryClearRestoreKeepEveryOriginalField()throws Exception{
        for(String key:Arrays.asList("cross-border","recovery")){
            NativeEquipmentBatchService.Edit e=new NativeEquipmentBatchService.Edit();e.requestId=UUID.randomUUID().toString();e.revision=map(batches.state(key)).get("revision").toString();e.reason="known other native schema";Map<String,Object> task=map(batches.previewClear(key,e));task=map(batches.step(key,e.requestId,task.get("revision").toString()));assertEquals("PERSISTED",task.get("state"));
            NativeEquipmentBatchService.Edit restore=new NativeEquipmentBatchService.Edit();restore.requestId=UUID.randomUUID().toString();restore.revision=map(batches.state(key)).get("revision").toString();restore.reason="restore exact original native row";restore.sourceTaskId=e.requestId;task=map(batches.previewRestore(key,restore));assertEquals("PERSISTED",map(batches.step(key,restore.requestId,task.get("revision").toString())).get("state"));
        }assertEquals("9:old-cost:opaque:1",scalar("SELECT CONCAT(announcement,':',restitution,':',Return_quantity,':',codenum) FROM dof_native.cross_border"));assertEquals("0:500",scalar("SELECT CONCAT(extra_consumption_id,':',extra_consumption_num) FROM dof_native.item_recovery"));
    }

    @Test public void completeThousandRowClearAndRestorationSurviveAllSegmentsWithoutTruncation()throws Exception{
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO dof_native.inheritance VALUES(?,'3037','1',5)")){for(int i=5;i<1005;i++){p.setInt(1,i);p.addBatch();}p.executeBatch();}String original=version();NativeEquipmentBatchService.Edit e=edit();Map<String,Object> task=map(batches.previewClear("inheritance",e));int segments=0;while(!task.get("state").equals("PERSISTED")){assertTrue(++segments<=51);task=step(task);}assertEquals(51,segments);assertEquals("0",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));assertEquals(0L,((Number)map(task.get("moduleSnapshotAtFinish")).get("rowCount")).longValue());
        NativeEquipmentBatchService.Edit restore=edit();restore.sourceTaskId=e.requestId;task=map(batches.previewRestore("inheritance",restore));segments=0;while(!task.get("state").equals("PERSISTED")){assertTrue(++segments<=51);task=step(task);}assertEquals(51,segments);assertEquals(original,version());assertEquals("1005",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));
    }

}
