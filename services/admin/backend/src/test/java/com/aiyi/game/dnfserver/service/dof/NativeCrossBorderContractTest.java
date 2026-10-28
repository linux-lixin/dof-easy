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
public class NativeCrossBorderContractTest {
    private ManagedPostalContractTest fixture; private DataSource ds; private DofAdminService access;
    private ActivityConfigService service;
    @Before @SuppressWarnings("unchecked") public void setup() throws Exception {
        fixture=new ManagedPostalContractTest();fixture.setup();
        ds=(DataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"access");
        assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='dof_native'"));
        sql("CREATE DATABASE dof_native DEFAULT CHARSET=utf8");
        ((List<String>)ReflectionTestUtils.getField(fixture,"created")).add("dof_native");
        sql("CREATE TABLE dof_native.cross_border(code INT UNSIGNED NOT NULL PRIMARY KEY,restitution VARCHAR(255) NOT NULL,Return_quantity VARCHAR(255) NOT NULL,announcement INT UNSIGNED NOT NULL,codenum INT UNSIGNED NOT NULL) ENGINE=MyISAM DEFAULT CHARSET=utf8");
        sql("INSERT INTO dof_native.cross_border VALUES(0,'3037/3340','10/20',1,1)");
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
    private Map<?,?> row(){return (Map<?,?>)((List<?>)service.list("cross-border").get("rows")).get(0);}
    @SuppressWarnings("unchecked") private ActivityConfigService.Edit edit() {
        Map<?,?> row=row();ActivityConfigService.Edit e=new ActivityConfigService.Edit();e.requestId=UUID.randomUUID().toString();e.operation="upsert";e.revision=row.get("revision").toString();e.values=new LinkedHashMap<>((Map<String,Object>)row.get("values"));return e;
    }
    private ActivityConfigService.Edit restore(ActivityConfigService.Edit original) {
        ActivityConfigService.Edit e=new ActivityConfigService.Edit();e.requestId=UUID.randomUUID().toString();e.operation="restore";e.targetRequestId=original.requestId;e.revision=row().get("revision").toString();return e;
    }
    private void reject(int status,Runnable action){try{action.run();fail();}catch(ActivityConfigService.Failure e){assertEquals(e.getMessage(),status,e.status);}catch(DofFailure e){assertEquals(e.getMessage(),status,e.status);}}
    @Test public void defaultRulePersistsReplaysAndRestoresThresholdWithoutChangingReservedField() throws Exception {
        Map<?,?> wire=(Map<?,?>)row().get("values");
        assertTrue("unsigned zero-default code must retain numeric HTTP value",wire.get("code") instanceof Integer);
        assertTrue("material threshold must drive a separate numeric control",wire.get("codenum") instanceof Integer);
        ActivityConfigService.Edit e=edit();e.values.put("codenum",10);
        assertEquals("PREVIEWED",service.preview("cross-border",e).get("state"));
        assertEquals("PERSISTED",service.save("cross-border",e,false).get("state"));
        sql("UPDATE dof_native.cross_border SET announcement=9");
        assertEquals("PERSISTED",service.save("cross-border",e,false).get("state"));
        ActivityConfigService.Edit undo=restore(e);service.preview("cross-border",undo);service.restore("cross-border",e.requestId,undo);
        assertEquals("1:3037/3340:10/20:9",scalar("SELECT CONCAT(codenum,':',restitution,':',Return_quantity,':',announcement) FROM dof_native.cross_border WHERE code=0"));
    }
    @Test public void createsDeletesAndRestoresDedicatedRuleWithoutTouchingDefault() throws Exception {
        ActivityConfigService.Edit create=edit();create.requestId=UUID.randomUUID().toString();create.revision="new";create.values.put("code",27600);
        service.preview("cross-border",create);service.save("cross-border",create,false);
        assertEquals("2",scalar("SELECT COUNT(*) FROM dof_native.cross_border"));
        Map<?,?> target=(Map<?,?>)((List<?>)service.list("cross-border").get("rows")).get(1);
        ActivityConfigService.Edit delete=new ActivityConfigService.Edit();delete.requestId=UUID.randomUUID().toString();delete.revision=target.get("revision").toString();delete.operation="delete";delete.values=create.values;
        service.preview("cross-border",delete);service.save("cross-border",delete,true);
        assertEquals("1",scalar("SELECT COUNT(*) FROM dof_native.cross_border"));
        ActivityConfigService.Edit undo=new ActivityConfigService.Edit();undo.requestId=UUID.randomUUID().toString();undo.revision="new";undo.operation="restore";undo.targetRequestId=delete.requestId;
        service.preview("cross-border",undo);service.restore("cross-border",delete.requestId,undo);
        assertEquals("2",scalar("SELECT COUNT(*) FROM dof_native.cross_border"));
        assertEquals("3037/3340:10/20:1",scalar("SELECT CONCAT(restitution,':',Return_quantity,':',codenum) FROM dof_native.cross_border WHERE code=0"));
    }
    @Test public void legacyCostCanBeRestoredExactlyButForeignQuantityOrThresholdStopsUndo() throws Exception {
        sql("UPDATE dof_native.cross_border SET restitution='legacy-items',Return_quantity='legacy-amounts'");
        ActivityConfigService.Edit e=edit();e.values.put("restitution","3037");e.values.put("Return_quantity","1");
        service.preview("cross-border",e);service.save("cross-border",e,false);
        ActivityConfigService.Edit undo=restore(e);service.preview("cross-border",undo);service.restore("cross-border",e.requestId,undo);
        assertEquals("legacy-items:legacy-amounts",scalar("SELECT CONCAT(restitution,':',Return_quantity) FROM dof_native.cross_border"));
        sql("UPDATE dof_native.cross_border SET restitution='3037',Return_quantity='1'");
        ActivityConfigService.Edit next=edit();next.values.put("restitution","3340");service.preview("cross-border",next);service.save("cross-border",next,false);
        sql("UPDATE dof_native.cross_border SET Return_quantity='2'");
        reject(409,()->service.preview("cross-border",restore(next)));
        assertEquals("3340:2",scalar("SELECT CONCAT(restitution,':',Return_quantity) FROM dof_native.cross_border"));
        sql("UPDATE dof_native.cross_border SET Return_quantity='1',codenum=2");reject(409,()->service.preview("cross-border",restore(next)));
    }
    @Test public void responseLossAfterMyisamWriteReconcilesOriginalIdAndFencesLaterRequests() throws Exception {
        ActivityConfigService.Edit e=edit();e.values.put("codenum",10);service.preview("cross-border",e);
        DataSource broken=mock(DataSource.class);AtomicBoolean once=new AtomicBoolean(true);
        when(broken.getConnection()).thenAnswer(call->{Connection real=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,m,a)->{
            if(m.getName().equals("prepareStatement")&&a[0].toString().startsWith("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?")&&once.compareAndSet(true,false))throw new SQLException("synthetic response uncertainty");
            try{return m.invoke(real,a);}catch(InvocationTargetException f){throw f.getCause();}
        });});
        ReflectionTestUtils.setField(access,"dataSource",broken);reject(503,()->service.save("cross-border",e,false));ReflectionTestUtils.setField(access,"dataSource",ds);
        ActivityConfigService.Edit next=edit();next.values.put("codenum",11);reject(409,()->service.preview("cross-border",next));
        assertEquals("PERSISTED",service.reconcile("cross-border",e.requestId).get("state"));
        assertEquals("PERSISTED",service.save("cross-border",e,false).get("state"));assertEquals("10",scalar("SELECT codenum FROM dof_native.cross_border"));
    }
    @Test public void nativeInnoDbEnginePreservesOpaqueUneditedMaterialText() throws Exception {
        sql("ALTER TABLE dof_native.cross_border ENGINE=InnoDB");
        sql("UPDATE dof_native.cross_border SET restitution='native-extension',Return_quantity='legacy-count'");
        ActivityConfigService.Edit e=edit();e.values.put("codenum",0);service.preview("cross-border",e);service.save("cross-border",e,false);
        assertEquals("native-extension:legacy-count:0",scalar("SELECT CONCAT(restitution,':',Return_quantity,':',codenum) FROM dof_native.cross_border"));
    }
    @Test public void concurrentEditorsCannotCommitTwoDifferentCostsFromOneRevision() throws Exception {
        ActivityConfigService.Edit first=edit(),second=edit();first.values.put("Return_quantity","1/2");second.values.put("Return_quantity","3/4");
        service.preview("cross-border",first);service.preview("cross-border",second);
        java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<String>> results=new ArrayList<>();
            for(ActivityConfigService.Edit e:Arrays.asList(first,second))results.add(pool.submit(()->{
                com.aiyi.core.util.thread.ThreadUtil.setUserId(1L);start.await();
                try{return service.save("cross-border",e,false).get("state").toString();}
                catch(ActivityConfigService.Failure failure){return Integer.toString(failure.status);}
            }));
            start.countDown();List<String> states=new ArrayList<>();for(java.util.concurrent.Future<String> r:results)states.add(r.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,Collections.frequency(states,"PERSISTED"));assertEquals(1,Collections.frequency(states,"409"));
        }finally{pool.shutdownNow();}
    }
    @Test public void failedPostLockSchemaValidationNeverReturnsLockedDriverToPool() throws Exception {
        sql("ALTER TABLE dof_native.cross_border ENGINE=MEMORY");
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
                ActivityConfigService.Edit e=edit();e.values.put("codenum",10);ReflectionTestUtils.setField(access,"dataSource",pool);
                try{reject(409,()->service.preview("cross-border",e));}finally{ReflectionTestUtils.setField(access,"dataSource",ds);}
                java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor();
                try{assertEquals("1",executor.submit(()->scalar("SELECT codenum FROM dof_native.cross_border")).get(8,java.util.concurrent.TimeUnit.SECONDS));}
                finally{executor.shutdownNow();}
            }finally{ReflectionTestUtils.setField(access,"dataSource",ds);}
        }
    }
    @Test public void unknownColumnOrderTriggersAndRevokedAdministratorStopBeforeWrite() throws Exception {
        ActivityConfigService.Edit e=edit();e.values.put("codenum",10);
        sql("ALTER TABLE dof_native.cross_border MODIFY COLUMN Return_quantity VARCHAR(255) NOT NULL AFTER code");
        reject(409,()->service.preview("cross-border",e));
        sql("ALTER TABLE dof_native.cross_border MODIFY COLUMN Return_quantity VARCHAR(255) NOT NULL AFTER restitution");
        sql("CREATE TRIGGER dnf_service.unexpected_cross_journal BEFORE INSERT ON dnf_service.DOF_OPERATION_JOURNAL FOR EACH ROW SET NEW.checked_by=NEW.checked_by");
        reject(409,()->service.preview("cross-border",e));sql("DROP TRIGGER dnf_service.unexpected_cross_journal");
        sql("CREATE TRIGGER dof_native.unexpected_cross BEFORE UPDATE ON dof_native.cross_border FOR EACH ROW SET NEW.codenum=NEW.codenum");
        reject(409,()->service.preview("cross-border",e));sql("DROP TRIGGER dof_native.unexpected_cross");
        service.preview("cross-border",e);sql("UPDATE d_taiwan.accounts SET admin=0 WHERE UID=1");
        reject(403,()->service.save("cross-border",e,false));assertEquals("1",scalar("SELECT codenum FROM dof_native.cross_border"));
    }
}
