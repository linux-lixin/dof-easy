package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.service.extension.ActivityConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real metadata transactions/native MyISAM locks on explicit synthetic configuration only. */
public class ActivityPreviewControlContractTest {
    private ManagedCdkContractTest fixture;private DriverManagerDataSource ds;private DofAdminService access;
    private ActivityConfigService service;private final ObjectMapper json=new ObjectMapper();
    @Before public void setup()throws Exception{
        fixture=new ManagedCdkContractTest();fixture.setup();ds=(DriverManagerDataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"access");ThreadUtil.setUserId(1L);
        assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='dof_native'"));sql("CREATE DATABASE dof_native DEFAULT CHARACTER SET utf8");((List<String>)ReflectionTestUtils.getField(ReflectionTestUtils.getField(fixture,"fixture"),"created")).add("dof_native");
        sql("CREATE TABLE dof_native.list_of_warorder_rewards(lv INT PRIMARY KEY,item1 INT,item_code1 INT,item2 INT,item_code2 INT,item3 INT,item_code3 INT,unknown_setting VARBINARY(8)) ENGINE=MyISAM");sql("INSERT INTO dof_native.list_of_warorder_rewards VALUES(1,100,1,0,0,0,0,X'00FF')");
        sql("CREATE TABLE dof_native.Collection_box(id INT PRIMARY KEY AUTO_INCREMENT,box_id INT,Isunique INT,Dname VARCHAR(255),te_date_end DATETIME,item_id INT,physical_attack INT,magical_attack INT,physical_defense INT,magical_defense INT,equipment_physical_attack INT,equipment_magical_attack INT,separate_attack INT,fire_attack INT,light_attack INT,water_attack INT,dark_attack INT,is_unloaded INT) ENGINE=MyISAM AUTO_INCREMENT=41 DEFAULT CHARSET=utf8");sql("CREATE TABLE dof_native.Collection_box_user_item(CID INT,Slot INT) ENGINE=MyISAM");
        service=new ActivityConfigService();ReflectionTestUtils.setField(service,"journal",ReflectionTestUtils.getField(fixture,"journal"));ReflectionTestUtils.setField(service,"dataSource",ds);ReflectionTestUtils.setField(service,"accountVODao",ReflectionTestUtils.getField(access,"accountVODao"));
    }
    @After public void cleanup()throws Exception{if(fixture!=null)fixture.cleanup();}
    private String scalar(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(text)){assertTrue(r.next());return r.getString(1);}}
    private void sql(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(text);}}
    private void reject(int code,Runnable task){try{task.run();fail("Unsafe activity control accepted");}catch(ActivityConfigService.Failure e){assertEquals(e.getMessage(),code,e.status);}catch(DofFailure e){assertEquals(e.getMessage(),code,e.status);}}
    private ActivityConfigService.Edit edit(){Map<?,?> row=(Map<?,?>)((List<?>)service.list("rewards").get("rows")).get(0);ActivityConfigService.Edit e=new ActivityConfigService.Edit();e.requestId=UUID.randomUUID().toString();e.operation="upsert";e.revision=(String)row.get("revision");e.values=new LinkedHashMap<>((Map<String,Object>)row.get("values"));e.values.put("item_code1",2);return e;}
    private ActivityConfigService.LegacyEdit cancel(String key,String source){ActivityConfigService.LegacyEdit e=new ActivityConfigService.LegacyEdit();e.requestId=UUID.randomUUID().toString();e.revision=(String)service.operation(key,source).get("receiptRevision");e.reason="synthetic cancel original preview";return e;}
    private void fault(String prefix,boolean lose){ReflectionTestUtils.invokeMethod(ReflectionTestUtils.getField(fixture,"fixture"),"fault",prefix,lose);}
    private void reset(){ReflectionTestUtils.setField(access,"dataSource",ds);ThreadUtil.setUserId(1L);}
    @Test public void canonicalPreviewCanBeReadAndReplayedExactlyWithoutAnyNativeWrite()throws Exception{
        ActivityConfigService.Edit e=edit();service.preview("rewards",e);Map<String,Object> read=service.operation("rewards",e.requestId);assertEquals(true,read.get("requestReplayAvailable"));ActivityConfigService.Edit recovered=json.convertValue(read.get("request"),ActivityConfigService.Edit.class);assertEquals(e.requestId,recovered.requestId);assertEquals(e.revision,recovered.revision);assertEquals("PREVIEWED",service.preview("rewards",recovered).get("state"));assertEquals("1:00FF",scalar("SELECT CONCAT(item_code1,':',HEX(unknown_setting)) FROM dof_native.list_of_warorder_rewards"));
    }
    @Test public void noncanonicalRequestTypesAreNotInventedDuringRecovery()throws Exception{
        ActivityConfigService.Edit e=edit();e.values.put("item_code1","2");service.preview("rewards",e);Map<String,Object> read=service.operation("rewards",e.requestId);assertEquals(false,read.get("requestReplayAvailable"));assertFalse(read.containsKey("request"));ActivityConfigService.LegacyEdit c=cancel("rewards",e.requestId);service.cancelPreview("rewards",e.requestId,c,false);service.cancelPreview("rewards",e.requestId,c,true);assertEquals("NOT_APPLIED",service.save("rewards",e,false).get("state"));assertEquals("1",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));
    }
    @Test public void cancellationFencesLateSubmissionAndPreservesSnapshotsAndOriginalActor()throws Exception{
        ActivityConfigService.Edit e=edit();service.preview("rewards",e);String before=scalar("SELECT CONCAT(before_json,after_json) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+e.requestId+"'");ActivityConfigService.LegacyEdit c=cancel("rewards",e.requestId);service.cancelPreview("rewards",e.requestId,c,false);assertEquals("PERSISTED",service.cancelPreview("rewards",e.requestId,c,true).get("state"));service.cancelPreview("rewards",e.requestId,c,true);assertEquals("NOT_APPLIED",service.save("rewards",e,false).get("state"));assertEquals(before,scalar("SELECT CONCAT(before_json,after_json) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+e.requestId+"'"));assertEquals("1",scalar("SELECT actor FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+e.requestId+"'"));assertEquals(1,service.cancellations("rewards",e.requestId).size());assertEquals("1:00FF",scalar("SELECT CONCAT(item_code1,':',HEX(unknown_setting)) FROM dof_native.list_of_warorder_rewards"));
    }
    @Test public void cancellingNewAutoIdPreviewNeverAllocatesOrCreatesNativeRow()throws Exception{
        ActivityConfigService.Section s=service.catalog().stream().filter(x->x.key.equals("collection")).findFirst().get();ActivityConfigService.Edit e=new ActivityConfigService.Edit();e.requestId=UUID.randomUUID().toString();e.revision="new";e.operation="upsert";e.values=new LinkedHashMap<>();for(ActivityConfigService.Field f:s.fields)e.values.put(f.name,f.initial);e.values.put("Dname","synthetic collection");e.values.put("te_date_end","2030-12-31 23:59:59");service.preview("collection",e);ActivityConfigService.LegacyEdit c=cancel("collection",e.requestId);service.cancelPreview("collection",e.requestId,c,false);service.cancelPreview("collection",e.requestId,c,true);assertEquals("NOT_APPLIED",service.operation("collection",e.requestId).get("state"));reject(409,()->service.currentForHistory("collection",e.requestId));assertEquals("NOT_APPLIED",service.save("collection",e,false).get("state"));assertEquals("0",scalar("SELECT COUNT(*) FROM dof_native.Collection_box"));assertEquals("41",scalar("SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='Collection_box'"));
    }
    @Test public void failedControlReceiptRollsBackSourceCancellationAndReconciles()throws Exception{
        ActivityConfigService.Edit e=edit();service.preview("rewards",e);ActivityConfigService.LegacyEdit c=cancel("rewards",e.requestId);service.cancelPreview("rewards",e.requestId,c,false);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json=",false);reject(503,()->service.cancelPreview("rewards",e.requestId,c,true));reset();assertEquals("PREVIEWED",service.operation("rewards",e.requestId).get("state"));assertEquals("NOT_APPLIED",service.reconcileCancellation("rewards",e.requestId,c.requestId).get("state"));assertEquals("1",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));
    }
    @Test public void committedCancellationReplyLossDoesNotReleaseOriginalIdToWrite()throws Exception{
        ActivityConfigService.Edit e=edit();service.preview("rewards",e);ActivityConfigService.LegacyEdit c=cancel("rewards",e.requestId);service.cancelPreview("rewards",e.requestId,c,false);fault(null,true);reject(503,()->service.cancelPreview("rewards",e.requestId,c,true));reset();assertEquals("PERSISTED",service.reconcileCancellation("rewards",e.requestId,c.requestId).get("state"));assertEquals("NOT_APPLIED",service.save("rewards",e,false).get("state"));assertEquals("1",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));
    }
    @Test public void preparedPersistedOrStaleSourceCannotBeCancelled()throws Exception{
        ActivityConfigService.Edit e=edit();service.preview("rewards",e);ActivityConfigService.LegacyEdit stale=cancel("rewards",e.requestId);service.save("rewards",e,false);reject(409,()->service.cancelPreview("rewards",e.requestId,stale,false));ActivityConfigService.Edit next=edit();next.values.put("item_code1",3);service.preview("rewards",next);fault("UPDATE `dof_native`.`list_of_warorder_rewards`",false);reject(503,()->service.save("rewards",next,false));reset();ActivityConfigService.LegacyEdit c=cancel("rewards",next.requestId);reject(409,()->service.cancelPreview("rewards",next.requestId,c,false));assertEquals("2",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));
    }
    @Test public void otherAdministratorCannotReplayButCanExplicitlyCancelWithOwnAudit()throws Exception{
        ActivityConfigService.Edit e=edit();service.preview("rewards",e);AccountVODao accounts=(AccountVODao)ReflectionTestUtils.getField(access,"accountVODao");sql("INSERT INTO d_taiwan.accounts VALUES(2,'other_admin',7)");AccountVO other=new AccountVO();other.setUid(2L);other.setAdmin(true);when(accounts.get(2L)).thenReturn(other);ThreadUtil.setUserId(2L);assertEquals(false,service.operation("rewards",e.requestId).get("requestReplayAvailable"));reject(409,()->service.preview("rewards",e));ActivityConfigService.LegacyEdit c=cancel("rewards",e.requestId);service.cancelPreview("rewards",e.requestId,c,false);service.cancelPreview("rewards",e.requestId,c,true);assertEquals("1:2",scalar("SELECT CONCAT(actor,':',checked_by) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+e.requestId+"'"));
    }
    @Test public void unexpectedJournalTriggerBlocksCancellationButDoesNotHideReadHistory()throws Exception{
        ActivityConfigService.Edit e=edit();service.preview("rewards",e);ActivityConfigService.LegacyEdit c=cancel("rewards",e.requestId);sql("CREATE TRIGGER dnf_service.synthetic_journal_trigger BEFORE UPDATE ON dnf_service.DOF_OPERATION_JOURNAL FOR EACH ROW SET new.checked_by=new.checked_by");try{reject(409,()->service.cancelPreview("rewards",e.requestId,c,false));assertEquals("PREVIEWED",service.operation("rewards",e.requestId).get("state"));}finally{sql("DROP TRIGGER dnf_service.synthetic_journal_trigger");}
    }
    @Test public void cachedAdministratorFlagCannotBypassDatabaseRevocation()throws Exception{
        ActivityConfigService.Edit e=edit();service.preview("rewards",e);ActivityConfigService.LegacyEdit c=cancel("rewards",e.requestId);sql("UPDATE d_taiwan.accounts SET admin=0 WHERE UID=1");reject(403,()->service.cancelPreview("rewards",e.requestId,c,false));assertEquals("PREVIEWED",service.operation("rewards",e.requestId).get("state"));assertEquals("1",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));
    }

}
