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

/** Actual service/SQL on disposable rule rows; no original-tool or gameplay claim. */
public class NativeInheritanceContractTest {
    private ManagedPostalContractTest fixture; private DataSource ds; private DofAdminService access;
    private ActivityConfigService service;
    @Before @SuppressWarnings("unchecked") public void setup() throws Exception {
        fixture=new ManagedPostalContractTest();fixture.setup();
        ds=(DataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"access");
        assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='dof_native'"));
        sql("CREATE DATABASE dof_native DEFAULT CHARSET=utf8");
        ((List<String>)ReflectionTestUtils.getField(fixture,"created")).add("dof_native");
        sql("CREATE TABLE dof_native.inheritance(code INT UNSIGNED NOT NULL PRIMARY KEY,restitution VARCHAR(255) NOT NULL,Return_quantity VARCHAR(255) NOT NULL,differ_lv INT UNSIGNED NOT NULL) ENGINE=MyISAM DEFAULT CHARSET=utf8");
        sql("INSERT INTO dof_native.inheritance VALUES(0,'3037/3340','10/20',5)");
        ManagedOperationJournal journal=new ManagedOperationJournal();ReflectionTestUtils.setField(journal,"access",access);
        service=new ActivityConfigService();ReflectionTestUtils.setField(service,"journal",journal);
        ReflectionTestUtils.setField(service,"accountVODao",ReflectionTestUtils.getField(access,"accountVODao"));
    }
    @After public void cleanup() throws Exception {
        if(access!=null&&ds!=null)ReflectionTestUtils.setField(access,"dataSource",ds);
        if(fixture!=null)fixture.cleanup();
    }
    private void sql(String text) throws Exception {try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(text);}}
    private String scalar(String text) throws Exception {try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(text)){assertTrue(r.next());return r.getString(1);}}
    private Map<?,?> row(){return (Map<?,?>)((List<?>)service.list("inheritance").get("rows")).get(0);}
    @SuppressWarnings("unchecked") private ActivityConfigService.Edit edit() {
        Map<?,?> row=row();ActivityConfigService.Edit e=new ActivityConfigService.Edit();e.requestId=UUID.randomUUID().toString();e.operation="upsert";e.revision=row.get("revision").toString();e.values=new LinkedHashMap<>((Map<String,Object>)row.get("values"));return e;
    }
    private ActivityConfigService.Edit restore(ActivityConfigService.Edit original) {
        ActivityConfigService.Edit e=new ActivityConfigService.Edit();e.requestId=UUID.randomUUID().toString();e.operation="restore";e.targetRequestId=original.requestId;e.revision=row().get("revision").toString();return e;
    }
    private void reject(int status,Runnable action){try{action.run();fail();}catch(ActivityConfigService.Failure e){assertEquals(e.getMessage(),status,e.status);}catch(DofFailure e){assertEquals(e.getMessage(),status,e.status);}}
    @Test public void defaultRulePersistsReplaysAndRestoresOnlyItsChangedLevel() throws Exception {
        ActivityConfigService.Edit e=edit();e.values.put("differ_lv",10);
        assertEquals("PREVIEWED",service.preview("inheritance",e).get("state"));
        assertEquals("PERSISTED",service.save("inheritance",e,false).get("state"));
        sql("UPDATE dof_native.inheritance SET restitution='1/2',Return_quantity='3/4'");
        assertEquals("PERSISTED",service.save("inheritance",e,false).get("state"));
        ActivityConfigService.Edit undo=restore(e);service.preview("inheritance",undo);service.restore("inheritance",e.requestId,undo);
        assertEquals("5:1/2:3/4",scalar("SELECT CONCAT(differ_lv,':',restitution,':',Return_quantity) FROM dof_native.inheritance WHERE code=0"));
    }
    @Test public void createsDeletesAndRestoresDedicatedRuleWithoutTouchingDefault() throws Exception {
        ActivityConfigService.Edit create=edit();create.requestId=UUID.randomUUID().toString();create.revision="new";create.values.put("code",27600);
        service.preview("inheritance",create);service.save("inheritance",create,false);
        assertEquals("2",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));
        Map<?,?> target=(Map<?,?>)((List<?>)service.list("inheritance").get("rows")).get(1);
        ActivityConfigService.Edit delete=new ActivityConfigService.Edit();delete.requestId=UUID.randomUUID().toString();delete.revision=target.get("revision").toString();delete.operation="delete";delete.values=create.values;
        service.preview("inheritance",delete);service.save("inheritance",delete,true);
        assertEquals("1",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));
        ActivityConfigService.Edit undo=new ActivityConfigService.Edit();undo.requestId=UUID.randomUUID().toString();undo.revision="new";undo.operation="restore";undo.targetRequestId=delete.requestId;
        service.preview("inheritance",undo);service.restore("inheritance",delete.requestId,undo);
        assertEquals("2",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));
        assertEquals("3037/3340:10/20:5",scalar("SELECT CONCAT(restitution,':',Return_quantity,':',differ_lv) FROM dof_native.inheritance WHERE code=0"));
    }
    @Test public void legacyCostCanBeRestoredExactlyButForeignPairedQuantityStopsUndo() throws Exception {
        sql("UPDATE dof_native.inheritance SET restitution='legacy-items',Return_quantity='legacy-amounts'");
        ActivityConfigService.Edit e=edit();e.values.put("restitution","3037");e.values.put("Return_quantity","1");
        service.preview("inheritance",e);service.save("inheritance",e,false);
        ActivityConfigService.Edit undo=restore(e);service.preview("inheritance",undo);service.restore("inheritance",e.requestId,undo);
        assertEquals("legacy-items:legacy-amounts",scalar("SELECT CONCAT(restitution,':',Return_quantity) FROM dof_native.inheritance"));
        sql("UPDATE dof_native.inheritance SET restitution='3037',Return_quantity='1'");
        ActivityConfigService.Edit next=edit();next.values.put("restitution","3340");service.preview("inheritance",next);service.save("inheritance",next,false);
        sql("UPDATE dof_native.inheritance SET Return_quantity='2'");
        reject(409,()->service.preview("inheritance",restore(next)));
        assertEquals("3340:2",scalar("SELECT CONCAT(restitution,':',Return_quantity) FROM dof_native.inheritance"));
    }
    @Test public void responseLossAfterMyisamWriteReconcilesOriginalIdAndFencesLaterRequests() throws Exception {
        ActivityConfigService.Edit e=edit();e.values.put("differ_lv",10);service.preview("inheritance",e);
        DataSource broken=mock(DataSource.class);AtomicBoolean once=new AtomicBoolean(true);
        when(broken.getConnection()).thenAnswer(call->{Connection real=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,m,a)->{
            if(m.getName().equals("prepareStatement")&&a[0].toString().startsWith("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?")&&once.compareAndSet(true,false))throw new SQLException("synthetic response uncertainty");
            try{return m.invoke(real,a);}catch(InvocationTargetException f){throw f.getCause();}
        });});
        ReflectionTestUtils.setField(access,"dataSource",broken);reject(503,()->service.save("inheritance",e,false));ReflectionTestUtils.setField(access,"dataSource",ds);
        ActivityConfigService.Edit next=edit();next.values.put("differ_lv",11);reject(409,()->service.preview("inheritance",next));
        assertEquals("PERSISTED",service.reconcile("inheritance",e.requestId).get("state"));
        assertEquals("PERSISTED",service.save("inheritance",e,false).get("state"));assertEquals("10",scalar("SELECT differ_lv FROM dof_native.inheritance"));
    }
    @Test public void nativeInnoDbEnginePreservesOpaqueUneditedMaterialText() throws Exception {
        sql("ALTER TABLE dof_native.inheritance ENGINE=InnoDB");
        sql("UPDATE dof_native.inheritance SET restitution='native-extension',Return_quantity='legacy-count'");
        ActivityConfigService.Edit e=edit();e.values.put("differ_lv",0);service.preview("inheritance",e);service.save("inheritance",e,false);
        assertEquals("native-extension:legacy-count:0",scalar("SELECT CONCAT(restitution,':',Return_quantity,':',differ_lv) FROM dof_native.inheritance"));
    }
    @Test public void concurrentEditorsCannotCommitTwoDifferentCostsFromOneRevision() throws Exception {
        ActivityConfigService.Edit first=edit(),second=edit();first.values.put("Return_quantity","1/2");second.values.put("Return_quantity","3/4");
        service.preview("inheritance",first);service.preview("inheritance",second);
        java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<String>> results=new ArrayList<>();
            for(ActivityConfigService.Edit e:Arrays.asList(first,second))results.add(pool.submit(()->{
                com.aiyi.core.util.thread.ThreadUtil.setUserId(1L);start.await();
                try{return service.save("inheritance",e,false).get("state").toString();}
                catch(ActivityConfigService.Failure failure){return Integer.toString(failure.status);}
            }));
            start.countDown();List<String> states=new ArrayList<>();for(java.util.concurrent.Future<String> r:results)states.add(r.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,Collections.frequency(states,"PERSISTED"));assertEquals(1,Collections.frequency(states,"409"));
        }finally{pool.shutdownNow();}
    }
    @Test public void failedPostLockSchemaValidationNeverReturnsLockedDriverToPool() throws Exception {
        sql("ALTER TABLE dof_native.inheritance ENGINE=MEMORY");
        for(boolean failUnlock:new boolean[]{false,true}) {
            DataSource source=ds;
            if(failUnlock) {
                DataSource broken=mock(DataSource.class);
                when(broken.getConnection()).thenAnswer(call->{Connection real=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,m,a)->{
                    try {
                        Object value=m.invoke(real,a);
                        if(m.getName().equals("createStatement")) {
                            Statement st=(Statement)value;
                            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Statement.class},(p,sm,sa)->{
                                if(sm.getName().equals("execute")&&"UNLOCK TABLES".equals(sa[0]))throw new SQLException("synthetic unlock failure");
                                try{return sm.invoke(st,sa);}catch(InvocationTargetException failure){throw failure.getCause();}
                            });
                        }
                        return value;
                    }catch(InvocationTargetException failure){throw failure.getCause();}
                });});source=broken;
            }
            com.zaxxer.hikari.HikariConfig config=new com.zaxxer.hikari.HikariConfig();config.setDataSource(source);config.setMaximumPoolSize(1);config.setMinimumIdle(0);config.setConnectionTimeout(5000);
            try(com.zaxxer.hikari.HikariDataSource pool=new com.zaxxer.hikari.HikariDataSource(config)) {
                ActivityConfigService.Edit e=edit();e.values.put("differ_lv",10);ReflectionTestUtils.setField(access,"dataSource",pool);
                try{reject(409,()->service.preview("inheritance",e));}finally{ReflectionTestUtils.setField(access,"dataSource",ds);}
                java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor();
                try{assertEquals("5",executor.submit(()->scalar("SELECT differ_lv FROM dof_native.inheritance")).get(8,java.util.concurrent.TimeUnit.SECONDS));}
                finally{executor.shutdownNow();}
            }finally{ReflectionTestUtils.setField(access,"dataSource",ds);}
        }
    }
    @Test public void unknownColumnOrderTriggersAndRevokedAdministratorStopBeforeWrite() throws Exception {
        ActivityConfigService.Edit e=edit();e.values.put("differ_lv",10);
        sql("ALTER TABLE dof_native.inheritance MODIFY COLUMN Return_quantity VARCHAR(255) NOT NULL AFTER code");
        reject(409,()->service.preview("inheritance",e));
        sql("ALTER TABLE dof_native.inheritance MODIFY COLUMN Return_quantity VARCHAR(255) NOT NULL AFTER restitution");
        sql("CREATE TRIGGER dnf_service.unexpected_inherit_journal BEFORE INSERT ON dnf_service.DOF_OPERATION_JOURNAL FOR EACH ROW SET NEW.checked_by=NEW.checked_by");
        reject(409,()->service.preview("inheritance",e));sql("DROP TRIGGER dnf_service.unexpected_inherit_journal");
        sql("CREATE TRIGGER dof_native.unexpected_inherit BEFORE UPDATE ON dof_native.inheritance FOR EACH ROW SET NEW.differ_lv=NEW.differ_lv");
        reject(409,()->service.preview("inheritance",e));sql("DROP TRIGGER dof_native.unexpected_inherit");
        service.preview("inheritance",e);sql("UPDATE d_taiwan.accounts SET admin=0 WHERE UID=1");
        reject(403,()->service.save("inheritance",e,false));assertEquals("5",scalar("SELECT differ_lv FROM dof_native.inheritance"));
    }
}
