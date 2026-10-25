package com.aiyi.game.dnfserver.service.dof;
import com.aiyi.core.util.thread.ThreadUtil;
import com.fasterxml.jackson.databind.*;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
/** Disposable qk tables only; inherited UUID/marker/player-schema guards run before all writes. */
public class DofSettingsContractTest {
    private DofSettingsService service;private DofAdminService access;private DataSource ds;
    private final ObjectMapper json=new ObjectMapper();
    @Before public void setup()throws Exception {
        DofMyisamContractTest fixture=new DofMyisamContractTest();fixture.setup();
        ds=(DataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"service");
        service=new DofSettingsService();ReflectionTestUtils.setField(service,"access",access);
        try(Connection c=ds.getConnection();Statement s=c.createStatement()) {
            s.execute("DELETE FROM dnf_service.QINGKONG_SETTINGS_AUDIT");s.execute("DROP TABLE IF EXISTS qk.billing");
            s.execute("CREATE TABLE qk.billing(ID INT PRIMARY KEY,ip_reg_control INT,regnumber INT,regtype INT,bubbleCount INT,bubbleTime INT,bubbleType INT,unknown_field VARBINARY(16)) ENGINE=MyISAM");
            s.execute("INSERT INTO qk.billing VALUES(1,1,1,1,1,1,1,X'00FF02')");
            s.execute("ALTER TABLE qk.qingkong_config ADD is_open_up_pwd INT,ADD switch_regCdkey INT,ADD `Switch` MEDIUMTEXT");
            try(PreparedStatement p=c.prepareStatement("UPDATE qk.qingkong_config SET is_open_up_pwd=1,switch_regCdkey=1,`Switch`=?")) {
                p.setString(1,NativeGeneralSettings.raw(NativeGeneralSettingsTest.fixture(),"qingkong_config.Switch"));p.executeUpdate();
            }
        }
    }
    private Map<?,?> map(Object o){return (Map<?,?>)o;}
    private String rev(){return map(service.read("registration")).get("revision").toString();}
    private DofSettingsService.Edit edit(String body)throws Exception {
        DofSettingsService.Edit e=new DofSettingsService.Edit();e.revision=rev();e.requestId=UUID.randomUUID().toString();
        if(body!=null)e.values=json.readValue(body,json.getTypeFactory().constructMapType(LinkedHashMap.class,String.class,JsonNode.class));return e;
    }
    private void rejects(int code,Runnable r){try{r.run();fail("Expected rejection");}catch(DofFailure e){assertEquals(e.getMessage(),code,e.status);}}
    private String scalar(String sql)throws Exception {try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    private void execute(String sql)throws Exception {try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(sql);}}
    private DofSettingsService failing(String sqlPrefix)throws Exception {
        DataSource fault=mock(DataSource.class);AtomicBoolean armed=new AtomicBoolean(true);
        when(fault.getConnection()).thenAnswer(call->{Connection real=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("prepareStatement") && args[0].toString().startsWith(sqlPrefix) && armed.compareAndSet(true,false))throw new SQLException("injected disconnect");
            try{return method.invoke(real,args);}catch(InvocationTargetException e){throw e.getCause();}
        });});
        DofAdminService copy=new DofAdminService();for(String name:Arrays.asList("accountVODao","enabled","instanceId","environment","databaseUuid","adapterVersion"))ReflectionTestUtils.setField(copy,name,ReflectionTestUtils.getField(access,name));ReflectionTestUtils.setField(copy,"dataSource",fault);
        DofSettingsService result=new DofSettingsService();ReflectionTestUtils.setField(result,"access",copy);return result;
    }
    @Test public void crossTableSaveReplayAndTargetedRestorePreserveUnrelatedChanges()throws Exception {
        DofSettingsService.Edit e=edit("{\"registrationGift\":50,\"activationCode\":false}");
        assertEquals(2,((List<?>)map(service.preview("registration",e)).get("changes")).size());assertEquals("1",scalar("SELECT regnumber FROM qk.billing"));
        assertEquals("PERSISTED",map(service.save("registration",e,null)).get("state"));assertEquals("PERSISTED",map(service.save("registration",e,null)).get("state"));
        execute("UPDATE qk.billing SET bubbleCount=17,unknown_field=X'1234'");
        assertEquals("PERSISTED",map(service.save("registration",edit(null),e.requestId)).get("state"));
        assertEquals("1",scalar("SELECT regnumber FROM qk.billing"));assertEquals("1",scalar("SELECT switch_regCdkey FROM qk.qingkong_config"));assertEquals("17",scalar("SELECT bubbleCount FROM qk.billing"));assertEquals("1234",scalar("SELECT HEX(unknown_field) FROM qk.billing"));
        assertEquals("RESTORED",map(((List<?>)service.history("registration")).get(1)).get("state"));
    }
    @Test public void interruptedBetweenMyisamTablesIsPartialAndRecoverable()throws Exception {
        DofSettingsService.Edit e=edit("{\"registrationGift\":50,\"activationCode\":false}");DofSettingsService broken=failing("UPDATE `qk`.`qingkong_config`");
        rejects(503,()->broken.save("registration",e,null));assertEquals("50",scalar("SELECT regnumber FROM qk.billing"));assertEquals("1",scalar("SELECT switch_regCdkey FROM qk.qingkong_config"));
        assertEquals("PARTIAL",map(service.reconcile("registration",e.requestId)).get("state"));
        DofSettingsService.Edit another=edit("{\"rewardAmount\":2}");rejects(409,()->service.save("online-reward",another,null));
        service.save("registration",edit(null),e.requestId);assertEquals("1",scalar("SELECT regnumber FROM qk.billing"));
        assertEquals("RESTORED",map(service.reconcile("registration",e.requestId)).get("state"));
    }
    @Test public void interruptionBeforeWriteAndAfterWriteHaveDifferentReconciliation()throws Exception {
        DofSettingsService.Edit first=edit("{\"registrationGift\":50}");DofSettingsService before=failing("UPDATE `qk`.`billing`");rejects(503,()->before.save("registration",first,null));
        assertEquals("NOT_APPLIED",map(service.reconcile("registration",first.requestId)).get("state"));
        DofSettingsService.Edit next=edit("{\"registrationGift\":60}");DofSettingsService after=failing("UPDATE `dnf_service`.`QINGKONG_SETTINGS_AUDIT`");rejects(503,()->after.save("registration",next,null));
        assertEquals("60",scalar("SELECT regnumber FROM qk.billing"));assertEquals("PERSISTED",map(service.reconcile("registration",next.requestId)).get("state"));
    }
    @Test public void thirdPartyChangesBlockRecoveryAndNeverOverwriteNativeData()throws Exception {
        DofSettingsService.Edit e=edit("{\"registrationGift\":50}");service.save("registration",e,null);execute("UPDATE qk.billing SET regnumber=99");
        DofSettingsService.Edit restore=edit(null);rejects(409,()->service.save("registration",restore,e.requestId));assertEquals("99",scalar("SELECT regnumber FROM qk.billing"));
    }
    @Test public void requestIdPayloadModuleAndVersionRemainBound()throws Exception {
        DofSettingsService.Edit e=edit("{\"registrationGift\":50}"),stale=edit("{\"registrationGift\":70}");service.save("registration",e,null);
        rejects(409,()->service.save("registration",stale,null));rejects(409,()->service.save("online-reward",e,null));
        e.values.put("registrationGift",json.readTree("51"));rejects(409,()->service.save("registration",e,null));assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.QINGKONG_SETTINGS_AUDIT"));
    }
    @Test public void concurrentEditorsCannotBothOverwriteSameRevision()throws Exception {
        DofSettingsService.Edit a=edit("{\"registrationGift\":50}"),b=edit("{\"registrationGift\":60}");CountDownLatch start=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures=new ArrayList<>();for(DofSettingsService.Edit e:Arrays.asList(a,b))futures.add(pool.submit(()->{ThreadUtil.setUserId(1L);start.await();try{service.save("registration",e,null);return 200;}catch(DofFailure failure){return failure.status;}}));
            start.countDown();Set<Integer> codes=new HashSet<>();for(Future<Integer> f:futures)codes.add(f.get(20,TimeUnit.SECONDS));assertEquals(new HashSet<>(Arrays.asList(200,409)),codes);
        }finally{pool.shutdownNow();}
    }
    @Test public void switchSaveAndRestoreKeepUnknownExtension()throws Exception {
        String original=scalar("SELECT `Switch` FROM qk.qingkong_config"),hud=scalar("SELECT MD5(Hotkeys) FROM qk.qingkong_config");
        DofSettingsService.Edit e=edit("{\"shortcut-0\":false,\"shortcut-39\":false}");service.save("shortcuts",e,null);
        assertTrue(scalar("SELECT `Switch` FROM qk.qingkong_config").endsWith("*future-value**"));service.save("shortcuts",edit(null),e.requestId);
        assertEquals(original,scalar("SELECT `Switch` FROM qk.qingkong_config"));assertEquals(hud,scalar("SELECT MD5(Hotkeys) FROM qk.qingkong_config"));
    }
    @Test public void corruptSnapshotsAndWrongDatabaseIdentityFailClosed()throws Exception {
        DofSettingsService.Edit e=edit("{\"registrationGift\":50}");service.save("registration",e,null);execute("UPDATE dnf_service.QINGKONG_SETTINGS_AUDIT SET before_json='{}'");
        DofSettingsService.Edit restore=edit(null);rejects(409,()->service.save("registration",restore,e.requestId));
        ReflectionTestUtils.setField(access,"databaseUuid","00000000-0000-0000-0000-000000000000");rejects(409,()->service.read("registration"));
    }
    @Test public void nativeSignedLimitsAndDistinctCurrencyEncodingsSurvivePersistence()throws Exception {
        service.save("registration",edit("{\"ipLimit\":-1,\"registrationCurrency\":0}"),null);
        assertEquals("-1",scalar("SELECT ip_reg_control FROM qk.billing"));assertEquals("0",scalar("SELECT regtype FROM qk.billing"));
        service.save("online-reward",edit("{\"rewardInterval\":0,\"rewardCurrency\":2}"),null);
        assertEquals("0",scalar("SELECT bubbleTime FROM qk.billing"));assertEquals("2",scalar("SELECT bubbleType FROM qk.billing"));
    }
    @Test public void incompatibleTypesAndNonDurableEnginesCannotBeWritten()throws Exception {
        DofSettingsService.Edit request=edit("{\"registrationGift\":50}");
        execute("ALTER TABLE qk.billing MODIFY regnumber VARCHAR(20)");
        rejects(409,()->service.save("registration",request,null));
        assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.QINGKONG_SETTINGS_AUDIT"));
        execute("ALTER TABLE qk.billing MODIFY regnumber INT");
        execute("ALTER TABLE qk.billing ENGINE=MEMORY");
        rejects(409,()->service.read("registration"));
        execute("ALTER TABLE qk.billing ENGINE=MyISAM");
        execute("ALTER TABLE dnf_service.QINGKONG_SETTINGS_AUDIT ENGINE=MyISAM");
        try {
            rejects(409,()->service.save("registration",request,null));
            assertEquals("1",scalar("SELECT regnumber FROM qk.billing"));
            assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.QINGKONG_SETTINGS_AUDIT"));
        }finally{execute("ALTER TABLE dnf_service.QINGKONG_SETTINGS_AUDIT ENGINE=InnoDB");}
    }
}
