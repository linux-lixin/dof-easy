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

/** Only runs after inherited disposable UUID, marker and absent-player-schema guards. */
public class DofScriptReleaseContractTest {
    private DofScriptReleaseService service;private DofAdminService access;private DofClientService clients;private DataSource ds;
    private Path pvf;private String bundleId,parameter;
    @Before public void setup()throws Exception {
        DofMyisamContractTest fixture=new DofMyisamContractTest();fixture.setup();
        ds=(DataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"service");
        pvf=Files.createTempFile("dof_native-script-release-test-",".pvf");Files.write(pvf,NativeScriptJobsTest.fixture(12));
        bundleId=UUID.randomUUID().toString();parameter=NativeScriptParameters.create(new java.security.SecureRandom()).envelope(NativeScriptContext.CLIENT_DLL_SHA256);
        clients=mock(DofClientService.class);when(clients.releaseCandidate(bundleId)).thenReturn(new DofScriptReleaseService.Candidate(bundleId,UUID.randomUUID().toString(),NativeScriptJobs.hash(pvf),NativeScriptJobsTest.REV,parameter));
        service=new DofScriptReleaseService();ReflectionTestUtils.setField(service,"access",access);ReflectionTestUtils.setField(service,"clients",clients);ReflectionTestUtils.setField(service,"gamePvf",pvf.toString());
        try(Connection c=ds.getConnection();Statement s=c.createStatement()) {
            s.execute("DELETE FROM dnf_service.DOF_SCRIPT_RELEASE");s.execute("DROP TABLE IF EXISTS dof_native.billing");
            s.execute("CREATE TABLE dof_native.billing(ID INT PRIMARY KEY,decrypt VARCHAR(255),unrelated INT) ENGINE=MyISAM DEFAULT CHARSET=utf8");s.execute("INSERT INTO dof_native.billing VALUES(1,'previous-billing',17)");
            s.execute("ALTER TABLE dof_native.config ADD pvfencryption VARCHAR(255),ADD Scriptmd5 VARCHAR(255),ADD Scriptverification VARCHAR(255),ADD updatec VARCHAR(255),ADD updatetype VARCHAR(255)");
            s.execute("UPDATE dof_native.config SET pvfencryption='previous-dof_native',Scriptmd5='unknown-old-metadata',Scriptverification='0',updatec='0',updatetype='1'");
        }
    }
    @After public void cleanup()throws Exception {if(pvf!=null)Files.deleteIfExists(pvf);}
    private Map<?,?> map(Object v){return (Map<?,?>)v;}
    private DofScriptReleaseService.Edit edit() {
        DofScriptReleaseService.Edit e=new DofScriptReleaseService.Edit();e.requestId=UUID.randomUUID().toString();e.bundleId=bundleId;e.revision=map(service.status()).get("revision").toString();return e;
    }
    private String scalar(String sql)throws Exception {try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    private void execute(String sql)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(sql);}}
    private interface Action{void run()throws Exception;}
    private void rejects(int status,Action r)throws Exception{try{r.run();fail("Expected failure");}catch(DofFailure e){assertEquals(e.getMessage(),status,e.status);}}
    private DofScriptReleaseService failing(String prefix)throws Exception {
        DataSource fault=mock(DataSource.class);AtomicBoolean armed=new AtomicBoolean(true);
        when(fault.getConnection()).thenAnswer(call->{Connection real=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("prepareStatement")&&args[0].toString().startsWith(prefix)&&armed.compareAndSet(true,false))throw new SQLException("injected disconnect");
            try{return method.invoke(real,args);}catch(InvocationTargetException e){throw e.getCause();}
        });});
        DofAdminService copy=new DofAdminService();for(String name:Arrays.asList("accountVODao","enabled","instanceId","environment","databaseUuid","adapterVersion"))ReflectionTestUtils.setField(copy,name,ReflectionTestUtils.getField(access,name));ReflectionTestUtils.setField(copy,"dataSource",fault);
        DofScriptReleaseService result=new DofScriptReleaseService();ReflectionTestUtils.setField(result,"access",copy);ReflectionTestUtils.setField(result,"clients",clients);ReflectionTestUtils.setField(result,"gamePvf",pvf.toString());return result;
    }
    @Test public void appliesOnlyParametersReplaysAndRestoresUnequalOriginalValues()throws Exception {
        DofScriptReleaseService.Edit e=edit();assertFalse((Boolean)map(service.preview(e)).get("persisted"));
        String response=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(service.apply(e,null));assertFalse(response.contains(parameter));assertFalse(response.contains("beforeJson"));
        assertEquals("PERSISTED",map(service.apply(e,null)).get("state"));assertEquals(parameter,scalar("SELECT decrypt FROM dof_native.billing"));assertEquals(parameter,scalar("SELECT pvfencryption FROM dof_native.config"));
        assertEquals("unknown-old-metadata",scalar("SELECT Scriptmd5 FROM dof_native.config"));assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_SCRIPT_RELEASE"));
        execute("UPDATE dof_native.billing SET unrelated=22");execute("UPDATE dof_native.config SET Scriptmd5='later-metadata'");
        DofScriptReleaseService.Edit restore=edit();restore.bundleId=null;service.apply(restore,e.requestId);
        assertEquals("previous-billing",scalar("SELECT decrypt FROM dof_native.billing"));assertEquals("previous-dof_native",scalar("SELECT pvfencryption FROM dof_native.config"));assertEquals("later-metadata",scalar("SELECT Scriptmd5 FROM dof_native.config"));assertEquals("22",scalar("SELECT unrelated FROM dof_native.billing"));
    }
    @Test public void staleRevisionWrongSourceAndEnabledUpdateFailWithoutWrites()throws Exception {
        DofScriptReleaseService.Edit e=edit();execute("UPDATE dof_native.config SET Scriptverification='1'");rejects(409,()->service.apply(e,null));rejects(409,()->service.apply(edit(),null));
        execute("UPDATE dof_native.config SET Scriptverification='0'");Files.write(pvf,new byte[]{1,2});rejects(409,()->service.apply(edit(),null));assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_SCRIPT_RELEASE"));
    }
    @Test public void partialMyisamWriteIsReconciledAndFieldScopedRecoveryWorks()throws Exception {
        DofScriptReleaseService.Edit e=edit();rejects(503,()->failing("UPDATE `dof_native`.`config`").apply(e,null));
        assertEquals(parameter,scalar("SELECT decrypt FROM dof_native.billing"));assertEquals("previous-dof_native",scalar("SELECT pvfencryption FROM dof_native.config"));
        assertEquals("PARTIAL",map(service.reconcile(e.requestId)).get("state"));rejects(409,()->service.apply(edit(),null));
        DofScriptReleaseService.Edit restore=edit();restore.bundleId=null;service.apply(restore,e.requestId);assertEquals("previous-billing",scalar("SELECT decrypt FROM dof_native.billing"));
    }
    @Test public void disconnectBeforeOrAfterWritesDoesNotTriggerAutomaticRetry()throws Exception {
        DofScriptReleaseService.Edit first=edit();rejects(503,()->failing("UPDATE `dof_native`.`billing`").apply(first,null));assertEquals("NOT_APPLIED",map(service.reconcile(first.requestId)).get("state"));
        assertEquals("NOT_APPLIED",map(service.apply(first,null)).get("state"));
        DofScriptReleaseService.Edit next=edit();rejects(503,()->failing("UPDATE `dnf_service`.`DOF_SCRIPT_RELEASE`").apply(next,null));assertEquals("PERSISTED",map(service.reconcile(next.requestId)).get("state"));
    }
    @Test public void concurrentApplicationsCannotOverwriteAndIdCannotChangePayload()throws Exception {
        DofScriptReleaseService.Edit a=edit(),b=edit();ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Integer> one=()->{ThreadUtil.setUserId(1L);start.await();try{service.apply(a,null);return 200;}catch(DofFailure e){return e.status;}};
            Callable<Integer> two=()->{ThreadUtil.setUserId(1L);start.await();try{service.apply(b,null);return 200;}catch(DofFailure e){return e.status;}};
            Future<Integer>x=pool.submit(one),y=pool.submit(two);start.countDown();List<Integer> results=Arrays.asList(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS));assertTrue(results.contains(200));assertTrue(results.contains(409));
        }finally{pool.shutdownNow();}
        String applied=scalar("SELECT request_id FROM dnf_service.DOF_SCRIPT_RELEASE");DofScriptReleaseService.Edit original=applied.equals(a.requestId)?a:b;original.bundleId=UUID.randomUUID().toString();rejects(409,()->service.apply(original,null));
    }
    @Test public void thirdPartyParameterChangesBlockRecoveryAndAuditEngineMustBeDurable()throws Exception {
        DofScriptReleaseService.Edit e=edit();service.apply(e,null);execute("UPDATE dof_native.billing SET decrypt='third-party'");DofScriptReleaseService.Edit restore=edit();restore.bundleId=null;rejects(409,()->service.apply(restore,e.requestId));
        execute("ALTER TABLE dnf_service.DOF_SCRIPT_RELEASE ENGINE=MyISAM");
        try{rejects(409,()->service.status());}finally{execute("ALTER TABLE dnf_service.DOF_SCRIPT_RELEASE ENGINE=InnoDB");}
    }
}
