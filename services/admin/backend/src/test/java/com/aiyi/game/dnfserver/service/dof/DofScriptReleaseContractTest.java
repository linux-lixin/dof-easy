package com.aiyi.game.dnfserver.service.dof;

import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.aiyi.core.util.thread.ThreadUtil;

/** Real shared-journal/MyISAM contracts on disposable synthetic configuration; no original tool equivalence claim. */
public class DofScriptReleaseContractTest {
    private DofScriptReleaseService service;private DofAdminService access;private DofClientService clients;private DataSource ds;
    private final java.util.concurrent.atomic.AtomicReference<String> sqlDiagnostic=new java.util.concurrent.atomic.AtomicReference<>();
    private Path pvf;private String bundleId,parameter;private ManagedPostalContractTest fixture;private ManagedOperationJournal journal;
    @Before public void setup()throws Exception {
        fixture=new ManagedPostalContractTest();fixture.setup();
        ds=(DataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"access");
        journal=new ManagedOperationJournal();ReflectionTestUtils.setField(journal,"access",access);
        assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='dof_native'"));
        execute("CREATE DATABASE dof_native DEFAULT CHARACTER SET utf8");((List<String>)ReflectionTestUtils.getField(fixture,"created")).add("dof_native");
        ThreadUtil.setUserId(1L);
        pvf=Files.createTempFile("dof_native-script-release-test-",".pvf");Files.write(pvf,NativeScriptJobsTest.fixture(12));
        bundleId=UUID.randomUUID().toString();parameter=NativeScriptParameters.create(new java.security.SecureRandom()).envelope(NativeScriptContext.CLIENT_DLL_SHA256);
        clients=mock(DofClientService.class);when(clients.releaseCandidate(bundleId)).thenReturn(new DofScriptReleaseService.Candidate(bundleId,UUID.randomUUID().toString(),NativeScriptJobs.hash(pvf),NativeScriptJobsTest.REV,parameter));
        service=new DofScriptReleaseService();ReflectionTestUtils.setField(service,"access",access);ReflectionTestUtils.setField(service,"clients",clients);ReflectionTestUtils.setField(service,"journal",journal);ReflectionTestUtils.setField(service,"gamePvf",pvf.toString());
        try(Connection c=ds.getConnection();Statement s=c.createStatement()) {
            s.execute("DELETE FROM dnf_service.DOF_SCRIPT_RELEASE");s.execute("DROP TABLE IF EXISTS dof_native.billing");
            s.execute("CREATE TABLE dof_native.billing(ID INT PRIMARY KEY,decrypt VARCHAR(255),unrelated INT) ENGINE=MyISAM DEFAULT CHARSET=utf8");s.execute("INSERT INTO dof_native.billing VALUES(1,'previous-billing',17)");
            s.execute("CREATE TABLE dof_native.config(ID INT PRIMARY KEY,pvfencryption VARCHAR(255),Scriptmd5 VARCHAR(255),Scriptverification VARCHAR(255),updatec VARCHAR(255),updatetype VARCHAR(255)) ENGINE=MyISAM DEFAULT CHARSET=utf8");s.execute("INSERT INTO dof_native.config(ID) VALUES(1)");
            s.execute("UPDATE dof_native.config SET pvfencryption='previous-dof_native',Scriptmd5='unknown-old-metadata',Scriptverification='0',updatec='0',updatetype='1'");
        }
    }
    @After public void cleanup()throws Exception {try{if(pvf!=null)Files.deleteIfExists(pvf);}finally{if(fixture!=null){if(access!=null&&ds!=null)ReflectionTestUtils.setField(access,"dataSource",ds);fixture.cleanup();}}}
    private Map<?,?> map(Object v){return (Map<?,?>)v;}
    private DofScriptReleaseService.Edit edit() {
        DofScriptReleaseService.Edit e=new DofScriptReleaseService.Edit();e.requestId=UUID.randomUUID().toString();e.protocol="script-release-preview-v1";e.bundleId=bundleId;e.revision=map(service.status()).get("revision").toString();return e;
    }
    private String scalar(String sql)throws Exception {try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    private void execute(String sql)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(sql);}}
    private interface Action{void run()throws Exception;}
    private void rejects(int status,Action r)throws Exception{try{r.run();fail("Expected failure");}catch(DofFailure e){assertEquals(e.getMessage()+"; SQL diagnostic="+sqlDiagnostic.get(),status,e.status);}}
    private DataSource traced(DataSource source)throws Exception {
        DataSource proxySource=mock(DataSource.class);
        when(proxySource.getConnection()).thenAnswer(call->{Connection real=source.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
            try {
                Object result=method.invoke(real,args);
                if(result instanceof Statement) {
                    String sql=method.getName().equals("prepareStatement")?args[0].toString():null;Statement statement=(Statement)result;
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{result instanceof PreparedStatement?PreparedStatement.class:Statement.class},(p,m,a)->{
                        try{return m.invoke(statement,a);}catch(InvocationTargetException failure){Throwable cause=failure.getCause();if(cause instanceof SQLException){SQLException error=(SQLException)cause;sqlDiagnostic.set(error.getErrorCode()+":"+error.getSQLState()+":"+error.getMessage()+":"+(sql==null&&a!=null&&a.length>0?a[0]:sql));}throw cause;}
                    });
                }return result;
            }catch(InvocationTargetException failure){Throwable cause=failure.getCause();if(cause instanceof SQLException){SQLException error=(SQLException)cause;sqlDiagnostic.set(error.getErrorCode()+":"+error.getSQLState()+":"+error.getMessage()+":"+method.getName());}throw cause;}
        });});return proxySource;
    }
    private DofScriptReleaseService failing(String prefix)throws Exception {
        DataSource fault=mock(DataSource.class);AtomicBoolean armed=new AtomicBoolean(true);
        when(fault.getConnection()).thenAnswer(call->{Connection real=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("prepareStatement")&&args[0].toString().startsWith(prefix)&&armed.compareAndSet(true,false))throw new SQLException("injected disconnect");
            try{return method.invoke(real,args);}catch(InvocationTargetException e){throw e.getCause();}
        });});
        DofAdminService copy=new DofAdminService();for(String name:Arrays.asList("accountVODao","enabled","instanceId","environment","databaseUuid","adapterVersion"))ReflectionTestUtils.setField(copy,name,ReflectionTestUtils.getField(access,name));ReflectionTestUtils.setField(copy,"dataSource",fault);
        DofScriptReleaseService result=new DofScriptReleaseService();ReflectionTestUtils.setField(result,"access",copy);ReflectionTestUtils.setField(result,"clients",clients);ManagedOperationJournal copyJournal=new ManagedOperationJournal();ReflectionTestUtils.setField(copyJournal,"access",copy);ReflectionTestUtils.setField(result,"journal",copyJournal);ReflectionTestUtils.setField(result,"gamePvf",pvf.toString());return result;
    }
    @Test public void appliesOnlyParametersReplaysAndRestoresUnequalOriginalValues()throws Exception {
        DofScriptReleaseService.Edit e=edit();assertFalse((Boolean)map(service.preview(e)).get("persisted"));
        String response=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(service.apply(e,null));assertFalse(response.contains(parameter));assertFalse(response.contains("beforeJson"));
        assertEquals("PERSISTED",map(service.apply(e,null)).get("state"));assertEquals(parameter,scalar("SELECT decrypt FROM dof_native.billing"));assertEquals(parameter,scalar("SELECT pvfencryption FROM dof_native.config"));
        assertEquals("unknown-old-metadata",scalar("SELECT Scriptmd5 FROM dof_native.config"));assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_SCRIPT_RELEASE"));
        execute("UPDATE dof_native.billing SET unrelated=22");execute("UPDATE dof_native.config SET Scriptmd5='later-metadata'");
        DofScriptReleaseService.Edit restore=edit();restore.bundleId=null;service.preview(restore,e.requestId);service.apply(restore,e.requestId);
        assertEquals("previous-billing",scalar("SELECT decrypt FROM dof_native.billing"));assertEquals("previous-dof_native",scalar("SELECT pvfencryption FROM dof_native.config"));assertEquals("later-metadata",scalar("SELECT Scriptmd5 FROM dof_native.config"));assertEquals("22",scalar("SELECT unrelated FROM dof_native.billing"));
    }
    @Test public void staleRevisionWrongSourceAndEnabledUpdateFailWithoutWrites()throws Exception {
        DofScriptReleaseService.Edit e=edit();service.preview(e);execute("UPDATE dof_native.config SET Scriptverification='1'");rejects(409,()->service.apply(e,null));rejects(409,()->service.preview(edit()));
        execute("UPDATE dof_native.config SET Scriptverification='0'");Files.write(pvf,new byte[]{1,2});rejects(409,()->service.preview(edit()));assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_SCRIPT_RELEASE"));
    }
    @Test public void partialMyisamWriteIsReconciledAndFieldScopedRecoveryWorks()throws Exception {
        DofScriptReleaseService.Edit e=edit();service.preview(e);rejects(503,()->failing("UPDATE `dof_native`.`config`").apply(e,null));
        assertEquals(parameter,scalar("SELECT decrypt FROM dof_native.billing"));assertEquals("previous-dof_native",scalar("SELECT pvfencryption FROM dof_native.config"));
        assertEquals("CONFLICT",map(service.reconcile(e.requestId)).get("state"));rejects(409,()->service.preview(edit()));
        DofScriptReleaseService.Edit restore=edit();restore.bundleId=null;service.preview(restore,e.requestId);service.apply(restore,e.requestId);assertEquals("previous-billing",scalar("SELECT decrypt FROM dof_native.billing"));
    }
    @Test public void disconnectBeforeOrAfterWritesDoesNotTriggerAutomaticRetry()throws Exception {
        DofScriptReleaseService.Edit first=edit();service.preview(first);rejects(503,()->failing("UPDATE `dof_native`.`billing`").apply(first,null));assertEquals("NOT_APPLIED",map(service.reconcile(first.requestId)).get("state"));
        assertEquals("NOT_APPLIED",map(service.apply(first,null)).get("state"));
        DofScriptReleaseService.Edit next=edit();service.preview(next);rejects(503,()->failing("UPDATE `dnf_service`.`DOF_SCRIPT_RELEASE`").apply(next,null));assertEquals("PERSISTED",map(service.reconcile(next.requestId)).get("state"));
    }
    @Test public void concurrentApplicationsCannotOverwriteAndIdCannotChangePayload()throws Exception {
        DofScriptReleaseService.Edit a=edit(),b=edit();service.preview(a);service.preview(b);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Integer> one=()->{ThreadUtil.setUserId(1L);start.await();try{service.apply(a,null);return 200;}catch(DofFailure e){return e.status;}};
            Callable<Integer> two=()->{ThreadUtil.setUserId(1L);start.await();try{service.apply(b,null);return 200;}catch(DofFailure e){return e.status;}};
            Future<Integer>x=pool.submit(one),y=pool.submit(two);start.countDown();List<Integer> results=Arrays.asList(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS));assertTrue(results.contains(200));assertTrue(results.contains(409));
        }finally{pool.shutdownNow();}
        String applied=scalar("SELECT request_id FROM dnf_service.DOF_SCRIPT_RELEASE");DofScriptReleaseService.Edit original=applied.equals(a.requestId)?a:b;original.bundleId=UUID.randomUUID().toString();rejects(409,()->service.apply(original,null));
    }
    @Test public void thirdPartyParameterChangesBlockRecoveryAndAuditEngineMustBeDurable()throws Exception {
        DofScriptReleaseService.Edit e=edit();service.preview(e);service.apply(e,null);execute("UPDATE dof_native.billing SET decrypt='third-party'");DofScriptReleaseService.Edit restore=edit();restore.bundleId=null;rejects(409,()->service.preview(restore,e.requestId));
        execute("ALTER TABLE dnf_service.DOF_SCRIPT_RELEASE ENGINE=MyISAM");
        try{rejects(409,()->service.status());}finally{execute("ALTER TABLE dnf_service.DOF_SCRIPT_RELEASE ENGINE=InnoDB");}
    }
    private DofScriptReleaseService.CancelEdit cancel(String source) {
        DofScriptReleaseService.CancelEdit c=new DofScriptReleaseService.CancelEdit();c.requestId=UUID.randomUUID().toString();c.revision=map(service.operation(source)).get("receiptRevision").toString();c.reason="synthetic cancellation";return c;
    }
    private void fault(String prefix,boolean lose)throws Exception {ReflectionTestUtils.invokeMethod(fixture,"fault",prefix,lose);}
    private void reset(){ReflectionTestUtils.setField(access,"dataSource",ds);}
    @Test public void durablePreviewRequiredAndOriginalRequestRecoveredWithoutSecrets()throws Exception {
        DofScriptReleaseService.Edit e=edit();e.protocol=null;rejects(400,()->service.preview(e));rejects(400,()->service.apply(e,null));e.protocol="script-release-preview-v1";rejects(409,()->service.apply(e,null));service.preview(e);
        Map<?,?> row=map(service.operation(e.requestId));assertEquals("PREVIEWED",row.get("state"));assertEquals(true,row.get("requestReplayAvailable"));
        DofScriptReleaseService.Edit recovered=new com.fasterxml.jackson.databind.ObjectMapper().convertValue(row.get("request"),DofScriptReleaseService.Edit.class);
        assertEquals(e.requestId,recovered.requestId);assertEquals(e.revision,recovered.revision);assertEquals(e.bundleId,recovered.bundleId);
        assertEquals("PREVIEWED",map(service.preview(recovered)).get("state"));assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_SCRIPT_RELEASE"));
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(service.history()).contains(parameter));
        assertFalse(scalar("SELECT CONCAT(before_json,after_json) FROM dnf_service.DOF_OPERATION_JOURNAL").contains(parameter));
        assertEquals("previous-billing",scalar("SELECT decrypt FROM dof_native.billing"));
    }
    @Test public void restoreAlsoNeedsItsOwnDurablePreview()throws Exception {
        DofScriptReleaseService.Edit e=edit();service.preview(e);service.apply(e,null);DofScriptReleaseService.Edit r=edit();r.bundleId=null;
        rejects(409,()->service.apply(r,e.requestId));service.preview(r,e.requestId);assertEquals("restore",map(service.operation(r.requestId)).get("operation"));service.apply(r,e.requestId);
        assertEquals("RESTORED",map(service.operation(e.requestId)).get("state"));assertEquals("previous-billing",scalar("SELECT decrypt FROM dof_native.billing"));
    }
    @Test public void cancellationFencesLateApplyAndPreservesOriginalPreview()throws Exception {
        DofScriptReleaseService.Edit e=edit();service.preview(e);String original=scalar("SELECT CONCAT(before_json,after_json,actor) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+e.requestId+"'");
        DofScriptReleaseService.CancelEdit c=cancel(e.requestId);service.cancelPreview(e.requestId,c,false);assertEquals("PERSISTED",map(service.cancelPreview(e.requestId,c,true)).get("state"));
        assertEquals("NOT_APPLIED",map(service.apply(e,null)).get("state"));assertEquals("PERSISTED",map(service.cancelPreview(e.requestId,c,true)).get("state"));
        assertEquals(original,scalar("SELECT CONCAT(before_json,after_json,actor) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+e.requestId+"'"));assertEquals("previous-billing",scalar("SELECT decrypt FROM dof_native.billing"));
    }
    @Test public void cancellationFailureRollsBackMetadataAndBlocksLateWriteUntilReconciled()throws Exception {
        DofScriptReleaseService.Edit e=edit();service.preview(e);DofScriptReleaseService.CancelEdit c=cancel(e.requestId);service.cancelPreview(e.requestId,c,false);
        fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json=",false);rejects(503,()->service.cancelPreview(e.requestId,c,true));reset();
        assertEquals("PREVIEWED",map(service.operation(e.requestId)).get("state"));rejects(409,()->service.apply(e,null));
        assertEquals("NOT_APPLIED",map(service.reconcile(c.requestId)).get("state"));assertEquals("previous-billing",scalar("SELECT decrypt FROM dof_native.billing"));
    }
    @Test public void committedCancelReplyLossReplaysOriginalControlWithoutNativeWrite()throws Exception {
        DofScriptReleaseService.Edit e=edit();service.preview(e);DofScriptReleaseService.CancelEdit c=cancel(e.requestId);service.cancelPreview(e.requestId,c,false);
        fault(null,true);rejects(503,()->service.cancelPreview(e.requestId,c,true));reset();assertEquals("PERSISTED",map(service.reconcile(c.requestId)).get("state"));assertEquals("NOT_APPLIED",map(service.apply(e,null)).get("state"));
    }
    @Test public void cancelledRestorePreviewCannotChangeAppliedParameters()throws Exception {
        DofScriptReleaseService.Edit e=edit();service.preview(e);service.apply(e,null);DofScriptReleaseService.Edit r=edit();r.bundleId=null;service.preview(r,e.requestId);
        DofScriptReleaseService.CancelEdit c=cancel(r.requestId);service.cancelPreview(r.requestId,c,false);service.cancelPreview(r.requestId,c,true);
        assertEquals("NOT_APPLIED",map(service.apply(r,e.requestId)).get("state"));assertEquals(parameter,scalar("SELECT decrypt FROM dof_native.billing"));
    }
    @Test public void cachedAdministratorFlagCannotBypassFreshRevocation()throws Exception {
        DofScriptReleaseService.Edit e=edit();service.preview(e);execute("UPDATE d_taiwan.accounts SET admin=0 WHERE UID=1");rejects(403,()->service.apply(e,null));
        assertEquals("previous-billing",scalar("SELECT decrypt FROM dof_native.billing"));
    }
    @Test public void nativeTriggerAndUnknownIdShapeRefuseWrites()throws Exception {
        DofScriptReleaseService.Edit e=edit();execute("CREATE TRIGGER dof_native.synthetic_trigger BEFORE UPDATE ON dof_native.billing FOR EACH ROW SET new.unrelated=new.unrelated");
        try{rejects(409,()->service.preview(e));}finally{execute("DROP TRIGGER dof_native.synthetic_trigger");}
        execute("ALTER TABLE dof_native.billing DROP PRIMARY KEY");rejects(409,()->service.preview(e));assertEquals("previous-billing",scalar("SELECT decrypt FROM dof_native.billing"));
    }
    @Test public void preparationWithoutPrivateSnapshotIsConclusiveNoWrite()throws Exception {
        DofScriptReleaseService.Edit e=edit();service.preview(e);rejects(503,()->failing("INSERT INTO `dnf_service`.`DOF_SCRIPT_RELEASE`").apply(e,null));
        assertEquals("NOT_APPLIED",map(service.reconcile(e.requestId)).get("state"));assertEquals("previous-billing",scalar("SELECT decrypt FROM dof_native.billing"));assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_SCRIPT_RELEASE"));
    }
    @Test public void privateCompletionRemainsProofAfterLaterConfigurationChange()throws Exception {
        DofScriptReleaseService.Edit e=edit();service.preview(e);rejects(503,()->failing("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?").apply(e,null));
        assertEquals("PERSISTED",scalar("SELECT state FROM dnf_service.DOF_SCRIPT_RELEASE"));execute("UPDATE dof_native.billing SET decrypt='later-third-party'");
        assertEquals("PERSISTED",map(service.reconcile(e.requestId)).get("state"));assertEquals("later-third-party",scalar("SELECT decrypt FROM dof_native.billing"));
    }
    @Test public void sourceOrFrozenCandidateChangesAfterPreviewCannotBeApplied()throws Exception {
        DofScriptReleaseService.Edit e=edit();service.preview(e);Files.write(pvf,new byte[]{1,2});rejects(409,()->service.apply(e,null));assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_SCRIPT_RELEASE"));
    }
    @Test public void legacyHistoryCanBeReadButNewUnpreviewedRequestsAreRefused()throws Exception {
        DofScriptReleaseService.Edit e=edit();service.preview(e);service.apply(e,null);execute("DELETE FROM dnf_service.DOF_OPERATION_JOURNAL");
        assertEquals("PERSISTED",map(service.operation(e.requestId)).get("state"));assertEquals("PERSISTED",map(service.apply(e,null)).get("state"));rejects(409,()->service.apply(edit(),null));assertEquals(parameter,scalar("SELECT decrypt FROM dof_native.billing"));
        DofScriptReleaseService.Edit next=edit();service.preview(next);DofScriptReleaseService.CancelEdit cancel=cancel(next.requestId);cancel.requestId=e.requestId;rejects(409,()->service.cancelPreview(next.requestId,cancel,false));
    }
    @Test public void privateMetadataShapeFailureReleasesActualPooledLocks()throws Exception {
        com.zaxxer.hikari.HikariConfig config=new com.zaxxer.hikari.HikariConfig();config.setDataSource(ds);config.setMaximumPoolSize(1);config.setMinimumIdle(0);config.setConnectionTimeout(5000);
        try(com.zaxxer.hikari.HikariDataSource pool=new com.zaxxer.hikari.HikariDataSource(config)) {
            DofScriptReleaseService.Edit e=edit();execute("ALTER TABLE dnf_service.DOF_SCRIPT_RELEASE DROP INDEX request_identity");ReflectionTestUtils.setField(access,"dataSource",pool);
            try{rejects(409,()->service.preview(e));}finally{ReflectionTestUtils.setField(access,"dataSource",ds);}
            ExecutorService executor=Executors.newSingleThreadExecutor();try{assertEquals("previous-billing",executor.submit(()->scalar("SELECT decrypt FROM dof_native.billing")).get(8,TimeUnit.SECONDS));}finally{executor.shutdownNow();}
            execute("ALTER TABLE dnf_service.DOF_SCRIPT_RELEASE ADD UNIQUE KEY request_identity(instance_id,request_id)");assertEquals("PREVIEWED",map(service.preview(e)).get("state"));
        }finally{ReflectionTestUtils.setField(access,"dataSource",ds);}
    }
    @Test public void hiddenNativeAndPrivateAuditTriggersRequireExactPrivilegeVisibility()throws Exception {
        String role="'script_limited'@'localhost'";execute("CREATE USER "+role+" IDENTIFIED BY 'synthetic'");
        try {
            for(String db:Arrays.asList("dnf_service","dof_native","d_taiwan")){String privileges=db.equals("dnf_service")?"SELECT,INSERT,UPDATE,LOCK TABLES":db.equals("dof_native")?"SELECT,UPDATE,LOCK TABLES":"SELECT,LOCK TABLES";execute("GRANT "+privileges+" ON `"+db.replace("_","\\_")+"`.* TO "+role);}execute("GRANT TRIGGER ON dnf_service.DOF_OPERATION_JOURNAL TO "+role);
            DofScriptReleaseService.Edit e=edit();org.springframework.jdbc.datasource.DriverManagerDataSource limited=new org.springframework.jdbc.datasource.DriverManagerDataSource(((org.springframework.jdbc.datasource.DriverManagerDataSource)ds).getUrl(),"script_limited","synthetic");try(Connection c=limited.getConnection();Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT CURRENT_USER()")){assertTrue(r.next());assertEquals("script_limited@localhost",r.getString(1));}ReflectionTestUtils.setField(access,"dataSource",traced(limited));
            rejects(409,()->service.preview(e));execute("GRANT TRIGGER ON `"+"dof_native".replace("_","\\_")+"`.* TO "+role);rejects(409,()->service.preview(e));execute("GRANT TRIGGER ON dnf_service.DOF_SCRIPT_RELEASE TO "+role);
            assertEquals("PREVIEWED",map(service.preview(e)).get("state"));assertEquals("PERSISTED",map(service.apply(e,null)).get("state"));
        }finally{ReflectionTestUtils.setField(access,"dataSource",ds);execute("DROP USER "+role);}
    }
    @Test public void failedCancellationRollbackDiscardsActualPooledDriverWithoutImplicitCommit()throws Exception {
        DofScriptReleaseService.Edit e=edit();service.preview(e);DofScriptReleaseService.CancelEdit cancel=cancel(e.requestId);service.cancelPreview(e.requestId,cancel,false);
        DataSource broken=mock(DataSource.class);AtomicBoolean once=new AtomicBoolean(true);
        when(broken.getConnection()).thenAnswer(call->{Connection real=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("prepareStatement")&&args[0].toString().startsWith("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json=")&&once.compareAndSet(true,false))throw new SQLException("synthetic metadata interruption");
            if(method.getName().equals("rollback"))throw new SQLException("synthetic rollback unavailable");
            try{return method.invoke(real,args);}catch(InvocationTargetException failure){throw failure.getCause();}
        });});
        com.zaxxer.hikari.HikariConfig config=new com.zaxxer.hikari.HikariConfig();config.setDataSource(broken);config.setMaximumPoolSize(1);config.setMinimumIdle(0);config.setConnectionTimeout(5000);
        try(com.zaxxer.hikari.HikariDataSource pool=new com.zaxxer.hikari.HikariDataSource(config)){
            ReflectionTestUtils.setField(access,"dataSource",pool);rejects(503,()->service.cancelPreview(e.requestId,cancel,true));
        }finally{ReflectionTestUtils.setField(access,"dataSource",ds);}
        assertEquals("PREVIEWED",map(service.operation(e.requestId)).get("state"));assertEquals("NOT_APPLIED",map(service.reconcile(cancel.requestId)).get("state"));assertEquals("previous-billing",scalar("SELECT decrypt FROM dof_native.billing"));
    }
}
