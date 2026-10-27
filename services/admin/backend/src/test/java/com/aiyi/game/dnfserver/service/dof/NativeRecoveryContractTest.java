package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.service.extension.ActivityConfigService;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Disposable rule configuration only. Does not prove original-tool or player-item equivalence. */
public class NativeRecoveryContractTest {
    private ManagedPostalContractTest fixture;private DataSource ds;private DofAdminService access;private ActivityConfigService service;
    @Before @SuppressWarnings("unchecked") public void setup() throws Exception {
        fixture=new ManagedPostalContractTest();fixture.setup();ds=(DataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"access");
        assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='dof_native'"));
        sql("CREATE DATABASE dof_native DEFAULT CHARSET=utf8");((List<String>)ReflectionTestUtils.getField(fixture,"created")).add("dof_native");
        sql("CREATE TABLE dof_native.item_recovery(code INT UNSIGNED NOT NULL PRIMARY KEY,restitution VARCHAR(255) NOT NULL,Return_quantity VARCHAR(255) NOT NULL,announcement INT UNSIGNED NOT NULL,codenum INT UNSIGNED NOT NULL,extra_consumption_id VARCHAR(255) NOT NULL,extra_consumption_num VARCHAR(255) NOT NULL) ENGINE=MyISAM DEFAULT CHARSET=utf8");
        sql("INSERT INTO dof_native.item_recovery VALUES(27600,'3037/3340','1/2',0,1,'0/3037','500/1')");
        ManagedOperationJournal journal=new ManagedOperationJournal();ReflectionTestUtils.setField(journal,"access",access);service=new ActivityConfigService();ReflectionTestUtils.setField(service,"journal",journal);ReflectionTestUtils.setField(service,"accountVODao",ReflectionTestUtils.getField(access,"accountVODao"));
    }
    @After public void cleanup() throws Exception {if(access!=null&&ds!=null)ReflectionTestUtils.setField(access,"dataSource",ds);if(fixture!=null)fixture.cleanup();}
    private void sql(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(text);}}
    private String scalar(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(text)){assertTrue(r.next());return r.getString(1);}}
    private Map<?,?> row(){return (Map<?,?>)((List<?>)service.list("recovery").get("rows")).get(0);}
    @SuppressWarnings("unchecked") private ActivityConfigService.Edit edit(){Map<?,?> row=row();ActivityConfigService.Edit e=new ActivityConfigService.Edit();e.requestId=UUID.randomUUID().toString();e.operation="upsert";e.revision=row.get("revision").toString();e.values=new LinkedHashMap<>((Map<String,Object>)row.get("values"));return e;}
    private ActivityConfigService.Edit undo(ActivityConfigService.Edit original){ActivityConfigService.Edit e=new ActivityConfigService.Edit();e.requestId=UUID.randomUUID().toString();e.revision=row().get("revision").toString();e.operation="restore";e.targetRequestId=original.requestId;return e;}
    private void reject(int status,Runnable action){try{action.run();fail();}catch(ActivityConfigService.Failure e){assertEquals(e.getMessage(),status,e.status);}catch(DofFailure e){assertEquals(e.getMessage(),status,e.status);}}
    @Test public void pairedRulesNumericReadbackAndIdempotentSaveRestoreExactly()throws Exception{
        Map<?,?> values=(Map<?,?>)row().get("values");for(String key:Arrays.asList("code","announcement","codenum"))assertTrue(values.get(key) instanceof Integer);
        ActivityConfigService.Edit e=edit();e.values.put("extra_consumption_num","600/2");service.preview("recovery",e);assertEquals("PERSISTED",service.save("recovery",e,false).get("state"));
        assertEquals("PERSISTED",service.save("recovery",e,false).get("state"));ActivityConfigService.Edit mismatch=edit();mismatch.requestId=e.requestId;reject(409,()->service.save("recovery",mismatch,false));
        ActivityConfigService.Edit restore=undo(e);service.preview("recovery",restore);service.restore("recovery",e.requestId,restore);assertEquals("500/1",scalar("SELECT extra_consumption_num FROM dof_native.item_recovery"));
    }
    @Test public void dedicatedCreationDeletionAndBothRestorationsLeaveOriginalRuleUntouched()throws Exception{
        ActivityConfigService.Edit create=edit();create.revision="new";create.values.put("code",27601);service.preview("recovery",create);service.save("recovery",create,false);assertEquals("2",scalar("SELECT COUNT(*) FROM dof_native.item_recovery"));
        Map<?,?> target=(Map<?,?>)((List<?>)service.list("recovery").get("rows")).get(1);
        ActivityConfigService.Edit del=new ActivityConfigService.Edit();del.requestId=UUID.randomUUID().toString();del.operation="delete";del.revision=target.get("revision").toString();del.values=create.values;service.preview("recovery",del);service.save("recovery",del,true);
        ActivityConfigService.Edit restore=new ActivityConfigService.Edit();restore.requestId=UUID.randomUUID().toString();restore.operation="restore";restore.targetRequestId=del.requestId;restore.revision="new";service.preview("recovery",restore);service.restore("recovery",del.requestId,restore);
        target=(Map<?,?>)((List<?>)service.list("recovery").get("rows")).get(1);restore=new ActivityConfigService.Edit();restore.requestId=UUID.randomUUID().toString();restore.operation="restore";restore.targetRequestId=create.requestId;restore.revision=target.get("revision").toString();service.preview("recovery",restore);service.restore("recovery",create.requestId,restore);
        assertEquals("1:27600:500/1",scalar("SELECT CONCAT(COUNT(*),':',MIN(code),':',MIN(extra_consumption_num)) FROM dof_native.item_recovery"));
    }
    @Test public void bothLegacyCostPairsRestoreByteExactlyAndForeignPairChangesStopUndo()throws Exception{
        sql("UPDATE dof_native.item_recovery SET restitution='old rewards',Return_quantity='old counts',extra_consumption_id='old costs',extra_consumption_num='old amounts',announcement=9,codenum=0");
        ActivityConfigService.Edit e=edit();e.values.put("restitution","3037");e.values.put("Return_quantity","1");e.values.put("extra_consumption_id","0");e.values.put("extra_consumption_num","500");service.preview("recovery",e);service.save("recovery",e,false);
        ActivityConfigService.Edit restore=undo(e);service.preview("recovery",restore);service.restore("recovery",e.requestId,restore);
        assertEquals("old rewards:old counts:old costs:old amounts:9:0",scalar("SELECT CONCAT(restitution,':',Return_quantity,':',extra_consumption_id,':',extra_consumption_num,':',announcement,':',codenum) FROM dof_native.item_recovery"));
        for(String[] pair:new String[][]{{"restitution","Return_quantity"},{"extra_consumption_id","extra_consumption_num"}}){
            sql("UPDATE dof_native.item_recovery SET restitution='3037',Return_quantity='1',extra_consumption_id='0',extra_consumption_num='500'");e=edit();e.values.put(pair[0],"3340");service.preview("recovery",e);service.save("recovery",e,false);sql("UPDATE dof_native.item_recovery SET "+pair[1]+"='2'");final ActivityConfigService.Edit source=e;reject(409,()->service.preview("recovery",undo(source)));
        }
    }
    @Test public void lostMyisamJournalReplyFencesNextWriteUntilOriginalIdReconciles()throws Exception{
        ActivityConfigService.Edit e=edit();e.values.put("codenum",2);service.preview("recovery",e);DataSource broken=mock(DataSource.class);AtomicBoolean once=new AtomicBoolean(true);
        when(broken.getConnection()).thenAnswer(call->{Connection real=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,m,a)->{
            if(m.getName().equals("prepareStatement")&&a[0].toString().startsWith("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?")&&once.compareAndSet(true,false))throw new SQLException("synthetic journal reply loss");try{return m.invoke(real,a);}catch(InvocationTargetException f){throw f.getCause();}
        });});ReflectionTestUtils.setField(access,"dataSource",broken);reject(503,()->service.save("recovery",e,false));ReflectionTestUtils.setField(access,"dataSource",ds);
        ActivityConfigService.Edit next=edit();next.values.put("codenum",3);reject(409,()->service.preview("recovery",next));assertEquals("PERSISTED",service.reconcile("recovery",e.requestId).get("state"));assertEquals("PERSISTED",service.save("recovery",e,false).get("state"));assertEquals("2",scalar("SELECT codenum FROM dof_native.item_recovery"));
    }
    @Test public void unknownOrdinalsUniqueIndexesTriggersAndForeignKeysRefuseWrites()throws Exception{
        ActivityConfigService.Edit e=edit();e.values.put("codenum",2);
        sql("ALTER TABLE dof_native.item_recovery MODIFY COLUMN codenum INT UNSIGNED NOT NULL AFTER code");reject(409,()->service.preview("recovery",e));sql("ALTER TABLE dof_native.item_recovery MODIFY COLUMN codenum INT UNSIGNED NOT NULL AFTER announcement");
        sql("ALTER TABLE dof_native.item_recovery ADD UNIQUE unknown_reward(restitution)");reject(409,()->service.preview("recovery",e));sql("ALTER TABLE dof_native.item_recovery DROP INDEX unknown_reward");
        sql("CREATE TRIGGER dof_native.unexpected_recovery BEFORE UPDATE ON dof_native.item_recovery FOR EACH ROW SET NEW.codenum=NEW.codenum");reject(409,()->service.preview("recovery",e));sql("DROP TRIGGER dof_native.unexpected_recovery");
        sql("CREATE TRIGGER dnf_service.unexpected_recovery_journal BEFORE INSERT ON dnf_service.DOF_OPERATION_JOURNAL FOR EACH ROW SET NEW.checked_by=NEW.checked_by");reject(409,()->service.preview("recovery",e));sql("DROP TRIGGER dnf_service.unexpected_recovery_journal");
        sql("ALTER TABLE dof_native.item_recovery ENGINE=InnoDB");sql("CREATE TABLE dof_native.child_recovery(code INT UNSIGNED,FOREIGN KEY(code) REFERENCES item_recovery(code)) ENGINE=InnoDB");reject(409,()->service.preview("recovery",e));sql("DROP TABLE dof_native.child_recovery");
        assertEquals("1",scalar("SELECT codenum FROM dof_native.item_recovery"));
    }
    @Test public void revokedActorAndMalformedGoldOrRewardInputNeverCreatePreview()throws Exception{
        ActivityConfigService.Edit e=edit();e.values.put("codenum",2);service.preview("recovery",e);sql("UPDATE d_taiwan.accounts SET admin=0 WHERE UID=1");reject(403,()->service.save("recovery",e,false));sql("UPDATE d_taiwan.accounts SET admin=7 WHERE UID=1");
        for(String key:Arrays.asList("restitution","Return_quantity","extra_consumption_id","extra_consumption_num")){ActivityConfigService.Edit bad=edit();bad.values.put(key,"1//2");reject(400,()->service.preview("recovery",bad));assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+bad.requestId+"'"));}
        assertEquals("1",scalar("SELECT codenum FROM dof_native.item_recovery"));
    }
    @Test public void actualInnoDbRuleSavePreservesOtherLegacyPairsAndUnrelatedFieldsOnUndo()throws Exception{
        sql("ALTER TABLE dof_native.item_recovery ENGINE=InnoDB");sql("UPDATE dof_native.item_recovery SET restitution='opaque',Return_quantity='opaque counts'");ActivityConfigService.Edit e=edit();e.values.put("extra_consumption_num","600/1");service.preview("recovery",e);service.save("recovery",e,false);sql("UPDATE dof_native.item_recovery SET announcement=1,codenum=3");ActivityConfigService.Edit restore=undo(e);service.preview("recovery",restore);service.restore("recovery",e.requestId,restore);
        assertEquals("opaque:opaque counts:500/1:1:3",scalar("SELECT CONCAT(restitution,':',Return_quantity,':',extra_consumption_num,':',announcement,':',codenum) FROM dof_native.item_recovery"));
    }
    @Test public void concurrentSameRevisionWritersCannotMixRewardAndCostRules()throws Exception{
        ActivityConfigService.Edit first=edit(),second=edit();first.values.put("Return_quantity","3/4");second.values.put("extra_consumption_num","700/2");service.preview("recovery",first);service.preview("recovery",second);
        java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{List<java.util.concurrent.Future<String>> futures=new ArrayList<>();for(ActivityConfigService.Edit e:Arrays.asList(first,second))futures.add(pool.submit(()->{com.aiyi.core.util.thread.ThreadUtil.setUserId(1L);start.await();try{return service.save("recovery",e,false).get("state").toString();}catch(ActivityConfigService.Failure f){return Integer.toString(f.status);}}));start.countDown();List<String> states=new ArrayList<>();for(java.util.concurrent.Future<String> f:futures)states.add(f.get(20,java.util.concurrent.TimeUnit.SECONDS));assertEquals(1,Collections.frequency(states,"PERSISTED"));assertEquals(1,Collections.frequency(states,"409"));}
        finally{pool.shutdownNow();}
    }
}
