package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Real MySQL contracts with disposable players/PVF, not original-tool parity evidence. */
public class ManagedCdkInventoryContractTest {
    private ManagedCdkContractTest fixture;
    private ManagedCdkInventoryService service;
    private DriverManagerDataSource ds;
    private DofAdminService access;
    private final ObjectMapper json=new ObjectMapper();
    @Before public void setup()throws Exception {
        fixture=new ManagedCdkContractTest();fixture.setup();
        ds=(DriverManagerDataSource)ReflectionTestUtils.getField(fixture,"ds");
        access=(DofAdminService)ReflectionTestUtils.getField(fixture,"access");
        service=new ManagedCdkInventoryService();
        ReflectionTestUtils.setField(service,"journal",ReflectionTestUtils.getField(fixture,"journal"));
        ReflectionTestUtils.setField(service,"postal",ReflectionTestUtils.getField(fixture,"postal"));
        ThreadUtil.setUserId(1L);
    }
    @After public void cleanup()throws Exception {if(fixture!=null)fixture.cleanup();}
    private Map<?,?> map(Object x){return (Map<?,?>)x;}
    private void sql(String sql)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(sql);}}
    private String scalar(String sql)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}}
    private void reject(int status,Runnable action){try{action.run();fail("Unsafe inventory operation accepted");}catch(DofFailure e){assertEquals(e.getMessage(),status,e.status);}}
    private void reset(){ReflectionTestUtils.setField(access,"dataSource",ds);ThreadUtil.setUserId(1L);}
    private void fault(String prefix,boolean lose){ReflectionTestUtils.invokeMethod(ReflectionTestUtils.getField(fixture,"fixture"),"fault",prefix,lose);}
    private ManagedCdkInventoryService.Edit edit() {
        ManagedCdkInventoryService.Edit e=new ManagedCdkInventoryService.Edit();e.requestId=UUID.randomUUID().toString();e.type=json.valueToTree(0);e.face=json.valueToTree(5);e.count=json.valueToTree(2);e.reason="synthetic isolated issuance";
        e.revision=(String)map(service.validate(e)).get("revision");return e;
    }
    private ManagedCdkInventoryService.Edit create(){ManagedCdkInventoryService.Edit e=edit();service.preview(e);assertEquals("PERSISTED",map(service.save(e)).get("state"));return e;}
    private long firstId(){List<?> rows=(List<?>)map(service.query(new ManagedCdkInventoryService.Query())).get("list");return ((Number)map(rows.get(0)).get("id")).longValue();}
    private ManagedCdkInventoryService.KeyEdit keyEdit(long id,String action,String source){ManagedCdkInventoryService.KeyEdit e=new ManagedCdkInventoryService.KeyEdit();e.requestId=UUID.randomUUID().toString();e.revision=(String)map(service.keyRead(id)).get("revision");e.operation=action;e.targetRequestId=source;e.reason="synthetic pause/resume";return e;}
    private ManagedCdkInventoryService.ExportEdit exportEdit(String batch){ManagedCdkInventoryService.ExportEdit e=new ManagedCdkInventoryService.ExportEdit();e.requestId=UUID.randomUUID().toString();e.revision=(String)map(service.read(batch)).get("exportRevision");e.reason="synthetic explicit export";return e;}
    @Test public void issuanceReplayDoesNotRecreateCodesAfterUseOrDeletion()throws Exception{
        ManagedCdkInventoryService.Edit e=create();String raw=scalar("SELECT content FROM dnf_service.recharge_key ORDER BY id LIMIT 1");
        sql("UPDATE dnf_service.recharge_key SET status=1,use_uid=22,use_account='synthetic_player',use_time=NOW()");service.save(e);assertEquals("2",scalar("SELECT COUNT(*) FROM dnf_service.recharge_key"));
        assertFalse(json.writeValueAsString(service.history()).contains(raw));assertFalse(scalar("SELECT CONCAT(before_json,after_json) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE namespace='cdk.issue'").contains(raw));
        sql("DELETE FROM dnf_service.recharge_key");service.save(e);assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.recharge_key"));e.count=json.valueToTree(3);reject(409,()->service.save(e));
    }
    @Test public void issuanceRollbackIsAtomicAndReconcilesWithoutInferringFromIds()throws Exception{
        ManagedCdkInventoryService.Edit e=edit();service.preview(e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json=",false);reject(503,()->service.save(e));reset();assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.recharge_key"));assertEquals("NOT_APPLIED",map(service.reconcile(e.requestId)).get("state"));service.save(e);assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.recharge_key"));
    }
    @Test public void lostCommitReplyReturnsPersistedBatchWithoutMintingAgain()throws Exception{
        ManagedCdkInventoryService.Edit e=edit();service.preview(e);fault(null,true);reject(503,()->service.save(e));reset();assertEquals("2",scalar("SELECT COUNT(*) FROM dnf_service.recharge_key"));assertEquals("PERSISTED",map(service.reconcile(e.requestId)).get("state"));service.save(e);assertEquals("2",scalar("SELECT COUNT(*) FROM dnf_service.recharge_key"));
    }
    @Test public void strictNumericAndEncodedNameLimitsRejectBeforeMinting()throws Exception{
        ManagedCdkInventoryService.Edit e=edit();e.count=json.valueToTree(1.5);reject(400,()->service.validate(e));e.count=json.valueToTree(1001);reject(400,()->service.validate(e));e.count=json.valueToTree(1);e.faceName=String.join("",Collections.nCopies(64,"测"));reject(400,()->service.validate(e));e.faceName="测试名称";assertNotNull(service.validate(e));assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.recharge_key"));
    }
    @Test public void explicitExportReplaysFrozenCsvAfterLegitimateConsumption()throws Exception{
        ManagedCdkInventoryService.Edit e=create();ManagedCdkInventoryService.ExportEdit x=exportEdit(e.requestId);ManagedCdkInventoryService.Download a=service.export(e.requestId,x);String raw=scalar("SELECT content FROM dnf_service.recharge_key LIMIT 1");assertTrue(new String(a.bytes,StandardCharsets.UTF_8).contains(raw));assertEquals(a.sha256,NativeUiState.sha256(a.bytes));sql("UPDATE dnf_service.recharge_key SET status=1,use_uid=22,use_account='synthetic_player',use_time=NOW()");assertArrayEquals(a.bytes,service.export(e.requestId,x).bytes);assertFalse(json.writeValueAsString(service.exportHistory()).contains(raw));assertFalse(scalar("SELECT CONCAT(before_json,after_json) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE namespace='cdk.export'").contains(raw));sql("UPDATE dnf_service.recharge_key SET face=6");reject(409,()->service.export(e.requestId,x));
    }
    @Test public void pauseResumeChangesOnlyAvailabilityAndCannotResurrectUsedCode()throws Exception{
        create();long id=firstId();ManagedCdkInventoryService.KeyEdit pause=keyEdit(id,"pause",null);service.keyPreview(id,pause);service.keySave(id,pause);service.keySave(id,pause);assertEquals("paused",map(service.keyRead(id)).get("availability"));assertEquals("1:NULL:NULL:NULL",scalar("SELECT CONCAT(status,':',IFNULL(use_uid,'NULL'),':',IFNULL(use_account,'NULL'),':',IFNULL(use_time,'NULL')) FROM dnf_service.recharge_key WHERE id="+id));
        ManagedCdkInventoryService.KeyEdit resume=keyEdit(id,"resume",pause.requestId);service.keyPreview(id,resume);service.keySave(id,resume);assertEquals("available",map(service.keyRead(id)).get("availability"));reject(409,()->service.keyPreview(id,keyEdit(id,"resume",pause.requestId)));
        sql("UPDATE dnf_service.recharge_key SET status=1,use_uid=22,use_account='synthetic_player',use_time=NOW() WHERE id="+id);reject(409,()->service.keyPreview(id,keyEdit(id,"resume",pause.requestId)));reject(409,()->service.keyPreview(id,keyEdit(id,"pause",null)));
    }
    @Test public void pauseRollsBackAndPreparedResultReconcilesUnderNativeLocks()throws Exception{
        create();long id=firstId();ManagedCdkInventoryService.KeyEdit e=keyEdit(id,"pause",null);service.keyPreview(id,e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=",false);reject(503,()->service.keySave(id,e));reset();assertEquals("available",map(service.keyRead(id)).get("availability"));assertEquals("NOT_APPLIED",map(service.reconcile(id,e.requestId)).get("state"));
    }
    @Test public void metadataQueryPaginatesWithoutSecretsAndReportsLegacyNameEncoding()throws Exception{
        create();String code=scalar("SELECT content FROM dnf_service.recharge_key ORDER BY id DESC LIMIT 1");sql("UPDATE dnf_service.recharge_key SET face_name='bad!' WHERE content='"+code+"'");ManagedCdkInventoryService.Query q=new ManagedCdkInventoryService.Query();q.pageSize=1;Map<?,?> first=map(service.query(q));assertEquals(2L,first.get("totalSize"));assertEquals(2L,first.get("totalPageSize"));assertNotNull(map(((List<?>)first.get("list")).get(0)).get("nameError"));assertFalse(json.writeValueAsString(first).contains(code));q.content=code;assertEquals(1L,map(service.query(q)).get("totalSize"));q.content=code.toLowerCase(Locale.ROOT);assertEquals(0L,map(service.query(q)).get("totalSize"));
    }
    @Test public void activeRedemptionReservationPreventsPauseAndRewardIsNeverIssuedByManagement()throws Exception{
        create();String code=scalar("SELECT content FROM dnf_service.recharge_key ORDER BY id DESC LIMIT 1");long id=firstId();ManagedCdkService redeem=(ManagedCdkService)ReflectionTestUtils.getField(fixture,"service");ManagedCdkService.Claim claim=ReflectionTestUtils.invokeMethod(fixture,"claim",code,0);ThreadUtil.setUserId(0L);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET target_key=",false);reject(503,()->redeem.claim(claim));reset();reject(409,()->service.keyPreview(id,keyEdit(id,"pause",null)));assertEquals("37",scalar("SELECT cera FROM taiwan_billing.cash_cera WHERE account='22'"));
    }
    @Test public void equipmentDefinitionUsesVerifiedPvfAndUnknownSchemaRefuses()throws Exception{
        ReflectionTestUtils.invokeMethod(ReflectionTestUtils.getField(fixture,"fixture"),"equipment",3);ManagedCdkInventoryService.Edit e=edit();e.type=json.valueToTree(1);e.face=json.valueToTree(1001);e.revision=(String)map(service.validate(e)).get("revision");service.preview(e);service.save(e);assertEquals("1:1001",scalar("SELECT CONCAT(type,':',face) FROM dnf_service.recharge_key LIMIT 1"));sql("ALTER TABLE dnf_service.recharge_key ADD unknown_parameter INT NULL");reject(409,()->service.query(new ManagedCdkInventoryService.Query()));assertEquals("2",scalar("SELECT COUNT(*) FROM dnf_service.recharge_key"));
    }
    @Test public void pausedCodeIsRejectedByExistingPublicIssuerWithoutReward()throws Exception{
        create();long id=firstId();String code=scalar("SELECT content FROM dnf_service.recharge_key WHERE id="+id);ManagedCdkInventoryService.KeyEdit pause=keyEdit(id,"pause",null);service.keyPreview(id,pause);service.keySave(id,pause);
        ManagedCdkService redeem=(ManagedCdkService)ReflectionTestUtils.getField(fixture,"service");ManagedCdkService.Claim claim=ReflectionTestUtils.invokeMethod(fixture,"claim",code,0);ThreadUtil.setUserId(0L);reject(409,()->redeem.claim(claim));reset();assertEquals("37",scalar("SELECT cera FROM taiwan_billing.cash_cera WHERE account='22'"));assertEquals("paused",map(service.keyRead(id)).get("availability"));
    }
    @Test public void interruptedExportReturnsNoArtifactAndBlocksNewIdUntilReconciled()throws Exception{
        ManagedCdkInventoryService.Edit batch=create();ManagedCdkInventoryService.ExportEdit export=exportEdit(batch.requestId);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json=",false);reject(503,()->service.export(batch.requestId,export));reset();reject(409,()->service.export(batch.requestId,export));ManagedCdkInventoryService.ExportEdit next=exportEdit(batch.requestId);reject(409,()->service.export(batch.requestId,next));assertEquals("NOT_APPLIED",map(service.exportReconcile(batch.requestId,export.requestId)).get("state"));assertNotNull(service.export(batch.requestId,next).bytes);
    }
    @Test public void sourceDefinitionAndVersionChangesRefusePreviewAndFrozenExport()throws Exception{
        ManagedCdkInventoryService.Edit batch=create();long id=firstId();ManagedCdkInventoryService.ExportEdit export=exportEdit(batch.requestId);ManagedCdkInventoryService.KeyEdit pause=keyEdit(id,"pause",null);sql("UPDATE dnf_service.recharge_key SET face=6 WHERE id="+id);reject(409,()->service.keyPreview(id,pause));reject(409,()->service.export(batch.requestId,export));assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE namespace='cdk.export'"));
    }

}
