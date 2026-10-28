package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.service.extension.ActivityConfigService;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Synthetic MyISAM data only; fixture first rejects all real player databases. */
public class ActivityOperationsContractTest {
    private DataSource ds;private DofAdminService access;private ActivityConfigService service;
    @Before public void setup()throws Exception {
        DofMyisamContractTest fixture=new DofMyisamContractTest();fixture.setup();
        ds=(DataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"service");
        try(Connection c=ds.getConnection();Statement s=c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS dnf_service.DOF_OPERATION_JOURNAL");
            String ddl=new String(Files.readAllBytes(Paths.get(System.getProperty("dof.contract.migration"))),java.nio.charset.StandardCharsets.UTF_8);
            for(String sql:ddl.replaceAll("(?m)^--.*$","").split(";"))if(!sql.trim().isEmpty())s.execute(sql);
            s.execute("DROP TABLE IF EXISTS dnf_service.EXTENSION_CONFIG_AUDIT");
            s.execute("DROP TABLE IF EXISTS dof_native.list_of_warorder_rewards");
            s.execute("CREATE TABLE dof_native.list_of_warorder_rewards(lv INT PRIMARY KEY,item1 INT,item_code1 INT,item2 INT,item_code2 INT,item3 INT,item_code3 INT,unknown_setting VARBINARY(8)) ENGINE=MyISAM");
            s.execute("INSERT INTO dof_native.list_of_warorder_rewards VALUES(1,100,1,0,0,0,0,X'00FF')");
            s.execute("DROP TABLE IF EXISTS dof_native.Collection_box");
            s.execute("CREATE TABLE dof_native.Collection_box(id INT PRIMARY KEY AUTO_INCREMENT,box_id INT,Isunique INT,Dname VARCHAR(255),te_date_end DATETIME,item_id INT,physical_attack INT,magical_attack INT,physical_defense INT,magical_defense INT,equipment_physical_attack INT,equipment_magical_attack INT,separate_attack INT,fire_attack INT,light_attack INT,water_attack INT,dark_attack INT,is_unloaded INT) ENGINE=MyISAM AUTO_INCREMENT=41 DEFAULT CHARSET=utf8");
            s.execute("DROP TABLE IF EXISTS dof_native.Collection_box_user_item");
            s.execute("CREATE TABLE dof_native.Collection_box_user_item(CID INT,Slot INT) ENGINE=MyISAM");
        }
        service=new ActivityConfigService();ManagedOperationJournal journal=new ManagedOperationJournal();ReflectionTestUtils.setField(journal,"access",access);
        ReflectionTestUtils.setField(service,"journal",journal);ReflectionTestUtils.setField(service,"dataSource",ds);ReflectionTestUtils.setField(service,"accountVODao",ReflectionTestUtils.getField(access,"accountVODao"));
    }
    private Map<?,?> row(String key) {return (Map<?,?>)((List<?>)service.list(key).get("rows")).get(0);}
    @SuppressWarnings("unchecked") private ActivityConfigService.Edit update(int amount) {
        Map<?,?> row=row("rewards");ActivityConfigService.Edit e=new ActivityConfigService.Edit();e.requestId=UUID.randomUUID().toString();e.revision=(String)row.get("revision");e.operation="upsert";
        e.values=new LinkedHashMap<>((Map<String,Object>)row.get("values"));e.values.put("item_code1",amount);return e;
    }
    private ActivityConfigService.Edit historical(String operation,String target,String revision) {
        ActivityConfigService.Edit e=new ActivityConfigService.Edit();e.operation=operation;e.targetRequestId=target;e.requestId=UUID.randomUUID().toString();e.revision=revision;return e;
    }
    private void rejects(int code,Runnable task){try{task.run();fail("Expected rejection");}catch(ActivityConfigService.Failure e){assertEquals(code,e.status);}catch(DofFailure e){assertEquals(code,e.status);}}
    private String scalar(String sql)throws Exception {try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}}
    private void sql(String sql)throws Exception {try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(sql);}}
    private void faultOnce(String prefix)throws Exception {
        DataSource faulty=mock(DataSource.class);AtomicBoolean armed=new AtomicBoolean(true);
        when(faulty.getConnection()).thenAnswer(call->{Connection c=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,args)->{
            if(m.getName().equals("prepareStatement") && args[0].toString().startsWith(prefix) && armed.compareAndSet(true,false))throw new SQLException("synthetic interruption");
            try{return m.invoke(c,args);}catch(InvocationTargetException e){throw e.getCause();}
        });});ReflectionTestUtils.setField(access,"dataSource",faulty);
    }
    @Test public void previewIsDurableAndReplayNeverOverwritesLaterEdits()throws Exception {
        ActivityConfigService.Edit e=update(2);rejects(404,()->service.save("rewards",e,false));
        assertEquals("PREVIEWED",service.preview("rewards",e).get("state"));assertEquals("1",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));
        assertEquals("PERSISTED",service.save("rewards",e,false).get("state"));
        sql("UPDATE dof_native.list_of_warorder_rewards SET item_code1=7");
        assertEquals("PERSISTED",service.save("rewards",e,false).get("state"));assertEquals("7",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));
        e.values.put("item_code1",9);rejects(409,()->service.save("rewards",e,false));
        assertEquals("00FF",scalar("SELECT HEX(unknown_setting) FROM dof_native.list_of_warorder_rewards"));
    }
    @Test public void interruptedBeforeWriteIsNotAppliedAndBlocksFreshRequestsUntilChecked()throws Exception {
        ActivityConfigService.Edit e=update(2);service.preview("rewards",e);faultOnce("UPDATE `dof_native`.`list_of_warorder_rewards`");
        rejects(503,()->service.save("rewards",e,false));assertEquals("1",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));
        ActivityConfigService.Edit next=update(3);rejects(409,()->service.preview("rewards",next));
        assertEquals("NOT_APPLIED",service.reconcile("rewards",e.requestId).get("state"));
        assertEquals(false,service.save("rewards",e,false).get("persisted"));service.preview("rewards",next);assertEquals(true,service.save("rewards",next,false).get("persisted"));
    }
    @Test public void interruptedAfterMyisamWriteIsReconciledAndRestoresOnlyTouchedFields()throws Exception {
        ActivityConfigService.Edit e=update(2);service.preview("rewards",e);faultOnce("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");
        rejects(503,()->service.save("rewards",e,false));assertEquals("2",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));
        assertEquals("PERSISTED",service.reconcile("rewards",e.requestId).get("state"));
        sql("UPDATE dof_native.list_of_warorder_rewards SET item2=222,item_code2=4");
        ActivityConfigService.Edit restore=historical("restore",e.requestId,(String)row("rewards").get("revision"));
        service.preview("rewards",restore);service.restore("rewards",e.requestId,restore);
        assertEquals("1",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));assertEquals("4",scalar("SELECT item_code2 FROM dof_native.list_of_warorder_rewards"));
    }
    @Test public void explicitConflictAcceptancePreservesCurrentDataAndRecordsReason()throws Exception {
        ActivityConfigService.Edit e=update(2);service.preview("rewards",e);faultOnce("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");rejects(503,()->service.save("rewards",e,false));
        sql("UPDATE dof_native.list_of_warorder_rewards SET item_code1=8");assertEquals("CONFLICT",service.reconcile("rewards",e.requestId).get("state"));
        ActivityConfigService.Edit next=update(9);rejects(409,()->service.preview("rewards",next));
        ActivityConfigService.Edit keep=historical("accept-current",e.requestId,(String)row("rewards").get("revision"));keep.reason="synthetic operator verified current configuration";
        service.preview("rewards",keep);assertEquals("NO_CHANGE",service.acceptCurrent("rewards",e.requestId,keep).get("state"));
        assertEquals("8",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));
        assertEquals("ACKNOWLEDGED",service.history("rewards").stream().filter(r->e.requestId.equals(r.get("requestId"))).findFirst().get().get("state"));
        assertEquals(keep.reason,service.history("rewards").stream().filter(r->keep.requestId.equals(r.get("requestId"))).findFirst().get().get("reason"));
        service.preview("rewards",next);assertEquals(true,service.save("rewards",next,false).get("persisted"));
    }
    @Test public void explicitAutoIdSurvivesCrashAndPlayerReferencesPreventRemoval()throws Exception {
        ActivityConfigService.Section s=service.catalog().stream().filter(x->x.key.equals("collection")).findFirst().get();
        ActivityConfigService.Edit e=new ActivityConfigService.Edit();e.requestId=UUID.randomUUID().toString();e.revision="new";e.operation="upsert";e.values=new LinkedHashMap<>();
        for(ActivityConfigService.Field f:s.fields)e.values.put(f.name,f.initial);
        e.values.put("Dname","synthetic collection");e.values.put("te_date_end","2026-12-31 23:59:59");
        service.preview("collection",e);faultOnce("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");rejects(503,()->service.save("collection",e,false));
        assertEquals("41",scalar("SELECT id FROM dof_native.Collection_box"));assertEquals("PERSISTED",service.reconcile("collection",e.requestId).get("state"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dof_native.Collection_box"));
        sql("INSERT INTO dof_native.Collection_box_user_item VALUES(999,41)");
        ActivityConfigService.Edit restore=historical("restore",e.requestId,(String)row("collection").get("revision"));rejects(409,()->service.preview("collection",restore));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dof_native.Collection_box_user_item"));
    }
    @Test public void tamperedSnapshotAndTargetCannotBeUsedForRestore()throws Exception {
        ActivityConfigService.Edit e=update(2);service.preview("rewards",e);service.save("rewards",e,false);
        sql("UPDATE dnf_service.DOF_OPERATION_JOURNAL SET target_key='2' WHERE request_id='"+e.requestId+"'");
        ActivityConfigService.Edit restore=historical("restore",e.requestId,(String)row("rewards").get("revision"));rejects(409,()->service.preview("rewards",restore));
        sql("UPDATE dnf_service.DOF_OPERATION_JOURNAL SET target_key='1',before_json='{}' WHERE request_id='"+e.requestId+"'");rejects(409,()->service.history("rewards"));
        assertEquals("2",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));
    }
    @Test public void missingMigrationOldPendingAndWrongBindingNeverInitializeNativeData()throws Exception {
        ActivityConfigService.Edit e=update(2);sql("ALTER TABLE dnf_service.DOF_OPERATION_JOURNAL ENGINE=MyISAM");rejects(409,()->service.preview("rewards",e));sql("ALTER TABLE dnf_service.DOF_OPERATION_JOURNAL ENGINE=InnoDB");
        sql("CREATE TABLE dnf_service.EXTENSION_CONFIG_AUDIT(id BIGINT PRIMARY KEY,actor BIGINT,section_name VARCHAR(32),operation_name VARCHAR(16),before_json MEDIUMTEXT,after_json MEDIUMTEXT,state VARCHAR(16),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB");
        sql("INSERT INTO dnf_service.EXTENSION_CONFIG_AUDIT VALUES(1,1,'rewards','update','null','null','pending',NOW())");rejects(409,()->service.preview("rewards",e));
        assertEquals(true,service.history("rewards").get(0).get("legacy"));
        ReflectionTestUtils.setField(access,"databaseUuid","00000000-0000-0000-0000-000000000000");rejects(409,()->service.list("rewards"));
        assertEquals("1",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));
    }
    private void legacy(long id,String operation,Object before,Object after)throws Exception {
        sql("CREATE TABLE IF NOT EXISTS dnf_service.EXTENSION_CONFIG_AUDIT(id BIGINT PRIMARY KEY,actor BIGINT,section_name VARCHAR(32),operation_name VARCHAR(16),before_json MEDIUMTEXT,after_json MEDIUMTEXT,state VARCHAR(16),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB");
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO dnf_service.EXTENSION_CONFIG_AUDIT VALUES(?,1,'rewards',?,?,?,'pending',NOW())")){p.setLong(1,id);p.setString(2,operation);p.setString(3,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(before));p.setString(4,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(after));p.executeUpdate();}
    }
    private ActivityConfigService.LegacyEdit legacyEdit(long id) {
        ActivityConfigService.LegacyEdit e=new ActivityConfigService.LegacyEdit();e.requestId=UUID.randomUUID().toString();e.reason="synthetic operator verified current rules";e.revision=(String)service.legacyCurrent("rewards",id).get("revision");return e;
    }
    @Test public void legacyReviewPreservesOriginalLogAndDoesNotRepeatNativeWrite()throws Exception {
        Map<?,?> before=(Map<?,?>)row("rewards").get("values");Map<String,Object> after=new LinkedHashMap<>();before.forEach((k,v)->after.put(k.toString(),v));after.put("item_code1",2);legacy(1,"update",before,after);
        assertEquals("MATCHES_BEFORE",service.legacyCurrent("rewards",1).get("assessment"));ActivityConfigService.LegacyEdit e=legacyEdit(1);
        rejects(409,()->service.legacyReview("rewards",1,e,true));service.legacyReview("rewards",1,e,false);assertEquals("1",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));
        assertEquals("NO_CHANGE",service.legacyReview("rewards",1,e,true).get("state"));assertEquals("NO_CHANGE",service.legacyReview("rewards",1,e,true).get("state"));
        assertEquals("pending",scalar("SELECT state FROM dnf_service.EXTENSION_CONFIG_AUDIT WHERE id=1"));assertEquals("1",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));
        ActivityConfigService.Edit next=update(3);service.preview("rewards",next);service.save("rewards",next,false);
        e.reason="changed content";rejects(409,()->service.legacyReview("rewards",1,e,true));
    }
    @Test public void oneResolvedLegacyRowDoesNotHideAnotherPendingRow()throws Exception {
        Map<?,?> before=(Map<?,?>)row("rewards").get("values");Map<String,Object> after=new LinkedHashMap<>();before.forEach((k,v)->after.put(k.toString(),v));after.put("item_code1",2);legacy(1,"update",before,after);legacy(2,"update",before,after);
        ActivityConfigService.LegacyEdit e=legacyEdit(1);service.legacyReview("rewards",1,e,false);service.legacyReview("rewards",1,e,true);rejects(409,()->service.preview("rewards",update(3)));
        ActivityConfigService.LegacyEdit two=legacyEdit(2);service.legacyReview("rewards",2,two,false);service.legacyReview("rewards",2,two,true);service.preview("rewards",update(3));
    }
    @Test public void changedCurrentAndChangedLegacyEvidenceInvalidateConfirmation()throws Exception {
        Map<?,?> before=(Map<?,?>)row("rewards").get("values");Map<String,Object> after=new LinkedHashMap<>();before.forEach((k,v)->after.put(k.toString(),v));after.put("item_code1",2);legacy(1,"update",before,after);
        ActivityConfigService.LegacyEdit e=legacyEdit(1);service.legacyReview("rewards",1,e,false);sql("UPDATE dof_native.list_of_warorder_rewards SET item_code1=4");rejects(409,()->service.legacyReview("rewards",1,e,true));
        ActivityConfigService.LegacyEdit n=legacyEdit(1);assertEquals("CONFLICT",service.legacyCurrent("rewards",1).get("assessment"));service.legacyReview("rewards",1,n,false);service.legacyReview("rewards",1,n,true);
        sql("UPDATE dnf_service.EXTENSION_CONFIG_AUDIT SET actor=99 WHERE id=1");rejects(409,()->service.preview("rewards",update(3)));
    }
    @Test public void interruptedLegacyConfirmationIsReconciledWithoutNativeWrites()throws Exception {
        Map<?,?> before=(Map<?,?>)row("rewards").get("values");Map<String,Object> after=new LinkedHashMap<>();before.forEach((k,v)->after.put(k.toString(),v));after.put("item_code1",2);legacy(1,"update",before,after);
        ActivityConfigService.LegacyEdit e=legacyEdit(1);service.legacyReview("rewards",1,e,false);faultOnce("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");rejects(503,()->service.legacyReview("rewards",1,e,true));
        rejects(409,()->service.preview("rewards",update(3)));assertEquals("NO_CHANGE",service.legacyReconcile("rewards",1,e.requestId).get("state"));assertEquals("1",scalar("SELECT item_code1 FROM dof_native.list_of_warorder_rewards"));
    }
    @Test public void changedLegacyStateDoesNotLeaveReviewPermanentlyPrepared()throws Exception {
        Map<?,?> before=(Map<?,?>)row("rewards").get("values");Map<String,Object> after=new LinkedHashMap<>();before.forEach((k,v)->after.put(k.toString(),v));after.put("item_code1",2);legacy(1,"update",before,after);
        ActivityConfigService.LegacyEdit e=legacyEdit(1);service.legacyReview("rewards",1,e,false);faultOnce("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");rejects(503,()->service.legacyReview("rewards",1,e,true));sql("UPDATE dnf_service.EXTENSION_CONFIG_AUDIT SET state='applied' WHERE id=1");assertEquals("NOT_APPLIED",service.legacyReconcile("rewards",1,e.requestId).get("state"));service.preview("rewards",update(3));
    }
    @Test public void oldAutoIdIsExplicitlyUnknownAndMalformedSnapshotsStayBlocked()throws Exception {
        ActivityConfigService.Section section=service.catalog().stream().filter(x->x.key.equals("collection")).findFirst().get();Map<String,Object> row=new LinkedHashMap<>();for(ActivityConfigService.Field f:section.fields)row.put(f.name,f.initial);row.put("id",0);
        legacy(1,"create",null,row);sql("UPDATE dnf_service.EXTENSION_CONFIG_AUDIT SET section_name='collection' WHERE id=1");
        assertEquals("UNKNOWN",service.legacyCurrent("collection",1).get("assessment"));sql("UPDATE dnf_service.EXTENSION_CONFIG_AUDIT SET after_json='{}' WHERE id=1");rejects(409,()->service.legacyCurrent("collection",1));
    }
    @Test public void concurrentVersionsYieldOneWriteAndOneExplicitConflict()throws Exception {
        ActivityConfigService.Edit a=update(2),b=update(3);service.preview("rewards",a);service.preview("rewards",b);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try {
            List<Future<Integer>> results=new ArrayList<>();for(ActivityConfigService.Edit e:Arrays.asList(a,b))results.add(pool.submit(()->{ThreadUtil.setUserId(1L);gate.await();try{service.save("rewards",e,false);return 200;}catch(ActivityConfigService.Failure failure){return failure.status;}}));
            gate.countDown();List<Integer> codes=Arrays.asList(results.get(0).get(20,TimeUnit.SECONDS),results.get(1).get(20,TimeUnit.SECONDS));assertTrue(codes.contains(200));assertTrue(codes.contains(409));
            assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE state='PERSISTED'"));
        }finally{pool.shutdownNow();}
    }
}
