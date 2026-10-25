package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Opt-in tests against a disposable MySQL 5.6 database, never a game database. */
public class DofMyisamContractTest {
    private DriverManagerDataSource ds;
    private DofAdminService service;
    private Properties config;
    private NativeUiState before, after;
    private final ObjectMapper json = new ObjectMapper();
    @Before public void setup() throws Exception {
        String path = System.getProperty("dof.contract.properties");
        Assume.assumeTrue("requires dedicated disposable MySQL fixture", path != null);
        config = new Properties(); try (InputStream in = Files.newInputStream(Paths.get(path))) { config.load(in); }
        ds = new DriverManagerDataSource(config.getProperty("url"), config.getProperty("username"), config.getProperty("password"));
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("SELECT @@server_uuid,purpose FROM dnf_service.DOF_CONTRACT_TEST_MARKER")) {
                assertTrue(r.next()); assertEquals(config.getProperty("uuid"), r.getString(1));
                assertEquals("disposable-native-ui-contract", r.getString(2)); assertFalse(r.next());
            }
            try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('d_taiwan','taiwan_cain','taiwan_cain_2nd')")) {
                r.next(); assertEquals("must never run in a player database", 0, r.getInt(1));
            }
            String migration = new String(Files.readAllBytes(Paths.get(System.getProperty("dof.contract.migration"))), StandardCharsets.UTF_8);
            for (String sql : migration.replaceAll("(?m)^--.*$", "").split(";")) if (!sql.trim().isEmpty()) s.execute(sql);
            s.execute("DELETE FROM dnf_service.DOF_INSTANCE_BINDING");
            s.execute("DELETE FROM dnf_service.DOF_CHANGE_AUDIT");
            s.execute("DROP TABLE IF EXISTS dof_native.config");
            s.execute("CREATE TABLE dof_native.config (ID INT PRIMARY KEY,Hotkeys MEDIUMTEXT,temp_2 TEXT,temp_3 TEXT,unknown_setting VARBINARY(128)) ENGINE=MyISAM DEFAULT CHARSET=utf8");
            try (PreparedStatement p = c.prepareStatement("INSERT INTO dnf_service.DOF_INSTANCE_BINDING VALUES ('dof-contract','isolated',?,'dof-native-1')")) {
                p.setString(1, config.getProperty("uuid")); p.executeUpdate();
            }
        }
        Map<String,Object> fixtures = json.readValue(PrivateNativeFixtures.open("ui-native-100.json"), new TypeReference<Map<String,Object>>() {});
        before = NativeUiState.fromText(json.convertValue(fixtures.get("before"), new TypeReference<Map<String,String>>() {}));
        after = NativeUiState.fromText(json.convertValue(fixtures.get("after"), new TypeReference<Map<String,String>>() {}));
        try (Connection c = ds.getConnection(); PreparedStatement p = c.prepareStatement("INSERT INTO dof_native.config VALUES (1,?,?,?,X'00FF01')")) {
            for (int i=0;i<3;i++) p.setBytes(i+1,before.get(NativeUiState.COLUMNS.get(i))); p.executeUpdate();
        }
        ThreadUtil.setUserId(1L); service = newService(ds);
    }
    private DofAdminService newService(DataSource source) {
        DofAdminService result = new DofAdminService();
        AccountVODao dao = mock(AccountVODao.class); AccountVO admin = new AccountVO(); admin.setAdmin(true); when(dao.get(1L)).thenReturn(admin);
        ReflectionTestUtils.setField(result,"accountVODao",dao); ReflectionTestUtils.setField(result,"dataSource",source);
        ReflectionTestUtils.setField(result,"enabled",true); ReflectionTestUtils.setField(result,"environment","isolated");
        ReflectionTestUtils.setField(result,"instanceId","dof-contract"); ReflectionTestUtils.setField(result,"databaseUuid",config.getProperty("uuid"));
        ReflectionTestUtils.setField(result,"adapterVersion","dof-native-1"); return result;
    }
    private DofAdminService.Edit edit(String revision) {
        DofAdminService.Edit e = new DofAdminService.Edit(); e.revision=revision; e.preset="native-100"; e.requestId=UUID.randomUUID().toString(); return e;
    }
    private Map<?,?> map(Object value) { return (Map<?,?>)value; }
    private void rejects(int status, Runnable action) {
        try { action.run(); fail("must fail"); } catch(DofFailure e) { assertEquals(e.getMessage(),status,e.status); }
    }
    private String scalar(String sql) throws SQLException {
        try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getString(1);}
    }
    @Test public void persistsReplaysAndRestoresOnlyTheChangedFields() throws Exception {
        DofAdminService.Edit e=edit(before.revision());
        assertEquals("PERSISTED",map(service.save(e,null)).get("state"));
        assertEquals(after.revision(),map(service.ui()).get("revision"));
        assertEquals("not_observed",map(service.ui()).get("effectState"));
        assertEquals("PERSISTED",map(service.save(e,null)).get("state"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.executeUpdate("UPDATE dof_native.config SET unknown_setting=X'FE00FE'");}
        DofAdminService.Edit restore=edit(after.revision()); restore.preset=null;
        assertEquals("PERSISTED",map(service.save(restore,e.requestId)).get("state"));
        assertEquals(before.revision(),map(service.ui()).get("revision"));
        assertEquals("FE00FE",scalar("SELECT HEX(unknown_setting) FROM dof_native.config"));
    }
    @Test public void staleRevisionAndRequestReuseCannotOverwrite() {
        DofAdminService.Edit e=edit(before.revision());service.save(e,null);
        rejects(409,()->service.save(edit(before.revision()),null));
        e.preset="native-110";rejects(409,()->service.save(e,null));
        assertEquals(after.revision(),map(service.ui()).get("revision"));
    }
    @Test public void onlyOneConcurrentEditorCanCommit() throws Exception {
        CountDownLatch start=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> task=()->{ThreadUtil.setUserId(1L);start.await();try{service.save(edit(before.revision()),null);return 200;}catch(DofFailure e){return e.status;}};
            Future<Integer> a=pool.submit(task),b=pool.submit(task);start.countDown();
            List<Integer> results=Arrays.asList(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
            assertTrue(results.contains(200));assertTrue(results.contains(409));
            assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
        } finally {pool.shutdownNow();}
    }
    private DataSource failOnce(String prefix) throws SQLException {
        DataSource fault=mock(DataSource.class);AtomicBoolean armed=new AtomicBoolean(true);
        when(fault.getConnection()).thenAnswer(call -> {
            Connection real=ds.getConnection();
            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
                if(method.getName().equals("prepareStatement") && args[0].toString().startsWith(prefix) && armed.compareAndSet(true,false)) throw new SQLException("injected interruption");
                try{return method.invoke(real,args);}catch(InvocationTargetException e){throw e.getCause();}
            });
        });return fault;
    }
    @Test public void interruptedBeforeNativeWriteBlocksRetriesUntilReconciled() throws Exception {
        DofAdminService failing=newService(failOnce("UPDATE `dof_native`.`config`"));
        DofAdminService.Edit e=edit(before.revision());rejects(503,()->failing.save(e,null));
        assertEquals("PREPARED",map(((List<?>)service.history()).get(0)).get("state"));
        rejects(409,()->service.save(edit(before.revision()),null));
        assertEquals("NOT_APPLIED",map(service.reconcile(e.requestId)).get("state"));
        assertEquals(before.revision(),map(service.ui()).get("revision"));
    }
    @Test public void interruptedAfterMyisamWriteDoesNotPretendTransactionRolledBack() throws Exception {
        DofAdminService failing=newService(failOnce("UPDATE `dnf_service`.`DOF_CHANGE_AUDIT`"));
        DofAdminService.Edit e=edit(before.revision());rejects(503,()->failing.save(e,null));
        assertEquals(after.revision(),map(service.ui()).get("revision"));
        assertEquals("PERSISTED",map(service.reconcile(e.requestId)).get("state"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
    }
    @Test public void differentDatabaseIdentityOrMissingNativeRowIsRejected() throws Exception {
        ReflectionTestUtils.setField(service,"databaseUuid","00000000-0000-0000-0000-000000000000");
        rejects(409,()->service.ui());
        ReflectionTestUtils.setField(service,"databaseUuid",config.getProperty("uuid"));
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.executeUpdate("DELETE FROM dof_native.config");}
        rejects(409,()->service.ui());
    }
    @Test public void corruptRestoreSnapshotCannotBecomeNativeConfiguration() throws Exception {
        DofAdminService.Edit e=edit(before.revision());service.save(e,null);
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("UPDATE dnf_service.DOF_CHANGE_AUDIT SET before_json=? WHERE request_id=?")) {
            p.setString(1,json.writeValueAsString(after.encode()));p.setString(2,e.requestId);p.executeUpdate();
        }
        DofAdminService.Edit restore=edit(after.revision());restore.preset=null;
        rejects(409,()->service.save(restore,e.requestId));
        assertEquals(after.revision(),map(service.ui()).get("revision"));
    }
    @Test public void noChangeDoesNotRewriteNativeRowAndThirdPartyEditBlocksRecovery() throws Exception {
        DofAdminService.Edit e=edit(before.revision());service.save(e,null);
        assertEquals("NO_CHANGE",map(service.save(edit(after.revision()),null)).get("state"));
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.executeUpdate("UPDATE dof_native.config SET temp_3=CONCAT(temp_3,'##unknown')");}
        String current=map(service.ui()).get("revision").toString();
        DofAdminService.Edit recovery=edit(current);recovery.preset=null;
        rejects(409,()->service.save(recovery,e.requestId));
        assertEquals("NO_CHANGE",map(service.save(edit(current),null)).get("state"));
        assertEquals(current,map(service.ui()).get("revision"));
    }
    private DofAdminService.Edit coordinateEdit(String revision,int x) throws Exception {
        return json.readValue("{\"revision\":\""+revision+"\",\"requestId\":\""+UUID.randomUUID()+"\",\"coordinates\":{\"healthPercentX\":"+x+"}}",DofAdminService.Edit.class);
    }
    @Test public void coordinatesHavePreviewReplayPayloadBindingAndExactRestore() throws Exception {
        DofAdminService.Edit e=coordinateEdit(before.revision(),250);
        NativeUiState expected=NativeUiCoordinates.apply(before,Collections.singletonMap("healthPercentX",250));
        assertEquals(Collections.singletonList("Hotkeys"),map(service.preview(e)).get("changedFields"));
        assertEquals(before.revision(),map(service.ui()).get("revision"));
        assertEquals("PERSISTED",map(service.save(e,null)).get("state"));
        assertEquals(expected.revision(),map(service.ui()).get("revision"));
        assertEquals("PERSISTED",map(service.save(e,null)).get("state"));
        e.coordinates.put("healthPercentX",json.readTree("251"));rejects(409,()->service.save(e,null));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
        DofAdminService.Edit restore=edit(expected.revision());restore.preset=null;
        service.save(restore,e.requestId);
        assertEquals(before.revision(),map(service.ui()).get("revision"));
        assertEquals("00FF01",scalar("SELECT HEX(unknown_setting) FROM dof_native.config"));
    }
    @Test public void coordinateValidationRejectsFloatsStringsUnknownControlsAndMixedPreset() throws Exception {
        for(String value:Arrays.asList("1.5","\"250\"","null","2147483648","32768")){
            DofAdminService.Edit e=coordinateEdit(before.revision(),250);e.coordinates.put("healthPercentX",json.readTree(value));
            rejects(400,()->service.save(e,null));
        }
        DofAdminService.Edit e=coordinateEdit(before.revision(),250);e.preset="native-100";rejects(400,()->service.save(e,null));
        assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
        assertEquals(before.revision(),map(service.ui()).get("revision"));
    }
    @Test public void interruptedCoordinateWriteReconcilesWithoutBlindRetry() throws Exception {
        DofAdminService.Edit e=coordinateEdit(before.revision(),250);
        DofAdminService failing=newService(failOnce("UPDATE `dnf_service`.`DOF_CHANGE_AUDIT`"));
        rejects(503,()->failing.save(e,null));
        assertEquals("PERSISTED",map(service.reconcile(e.requestId)).get("state"));
        assertEquals(NativeUiCoordinates.apply(before,Collections.singletonMap("healthPercentX",250)).revision(),map(service.ui()).get("revision"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
    }
    private DofAdminService.Edit switchEdit(String revision,boolean value) throws Exception {
        return json.readValue("{\"revision\":\""+revision+"\",\"requestId\":\""+UUID.randomUUID()+"\",\"switches\":{\"healthPercent\":"+value+"}}",DofAdminService.Edit.class);
    }
    @Test public void switchesBindReplayPayloadAndRestoreOnlyChangedFields() throws Exception {
        DofAdminService.Edit e=switchEdit(before.revision(),false);
        NativeUiState expected=NativeUiSwitches.apply(before,Collections.singletonMap("healthPercent",false));
        assertEquals(Collections.singletonList("Hotkeys"),map(service.preview(e)).get("changedFields"));
        assertEquals(before.revision(),map(service.ui()).get("revision"));
        assertEquals("PERSISTED",map(service.save(e,null)).get("state"));
        assertEquals(expected.revision(),map(service.ui()).get("revision"));
        assertEquals("not_observed",map(service.ui()).get("effectState"));
        assertEquals("PERSISTED",map(service.save(e,null)).get("state"));
        e.switches.put("healthPercent",json.readTree("true"));rejects(409,()->service.save(e,null));
        DofAdminService.Edit recovery=edit(expected.revision());recovery.preset=null;
        service.save(recovery,e.requestId);
        assertEquals(before.revision(),map(service.ui()).get("revision"));
        assertEquals("00FF01",scalar("SELECT HEX(unknown_setting) FROM dof_native.config"));
    }
    @Test public void switchValidationRejectsCoercionMissingFieldsAndMixedRequests() throws Exception {
        for(String value:Arrays.asList("1","0","\"true\"","null","{}")) {
            DofAdminService.Edit e=switchEdit(before.revision(),false);e.switches.put("healthPercent",json.readTree(value));
            rejects(400,()->service.save(e,null));
        }
        DofAdminService.Edit missing=switchEdit(before.revision(),false);missing.switches.clear();missing.switches.put("equipmentBuffIcons",json.readTree("true"));
        rejects(409,()->service.save(missing,null));
        DofAdminService.Edit mixed=switchEdit(before.revision(),false);mixed.preset="native-100";rejects(400,()->service.save(mixed,null));
        mixed.preset=null;mixed.coordinates=Collections.singletonMap("healthPercentX",json.readTree("250"));rejects(400,()->service.save(mixed,null));
        rejects(400,()->service.save(mixed,UUID.randomUUID().toString()));
        assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
        assertEquals(before.revision(),map(service.ui()).get("revision"));
    }
    @Test public void interruptedSwitchSaveReconcilesWithoutRepeatingTheWrite() throws Exception {
        DofAdminService.Edit e=switchEdit(before.revision(),false);
        DofAdminService failing=newService(failOnce("UPDATE `dnf_service`.`DOF_CHANGE_AUDIT`"));
        rejects(503,()->failing.save(e,null));
        assertEquals("PERSISTED",map(service.reconcile(e.requestId)).get("state"));
        assertEquals("PERSISTED",map(service.save(e,null)).get("state"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
    }
    @Test public void allFifteenPresetsPersistPreserveExtensionsAndRestoreFromFreshService() throws Exception {
        service.save(edit(before.revision()),null);
        try(Connection c=ds.getConnection();Statement s=c.createStatement()) {
            s.executeUpdate("UPDATE dof_native.config SET temp_3=CONCAT(temp_3,'##future-extension'),unknown_setting=X'AB00CD'");
        }
        Map<String,String> texts=new LinkedHashMap<>();for(String key:NativeUiState.COLUMNS) texts.put(key,after.text(key));
        texts.put("temp_3",texts.get("temp_3")+"##future-extension");NativeUiState baseline=NativeUiState.fromText(texts);
        assertEquals(true,map(service.ui()).get("nativePresetsCompatible"));
        for(Map<String,Object> row:new NativeUiPresetCatalog().list()) {
            DofAdminService.Edit e=edit(baseline.revision());e.preset=row.get("id").toString();
            NativeUiState expected=new NativeUiPresetApplication().apply(e.preset,baseline);
            Map<?,?> preview=map(service.preview(e));assertEquals(false,preview.get("persisted"));
            assertEquals(baseline.revision(),map(service.ui()).get("revision"));
            Map<?,?> saved=map(service.save(e,null));assertEquals(expected.revision(),map(newService(ds).ui()).get("revision"));
            assertEquals("not_observed",map(service.ui()).get("effectState"));
            assertEquals(saved.get("state"),map(newService(ds).save(e,null)).get("state"));
            if (!expected.revision().equals(baseline.revision())) {
                DofAdminService.Edit recovery=edit(expected.revision());recovery.preset=null;
                assertEquals("PERSISTED",map(newService(ds).save(recovery,e.requestId)).get("state"));
            }
            assertEquals(baseline.revision(),map(service.ui()).get("revision"));
            assertEquals("AB00CD",scalar("SELECT HEX(unknown_setting) FROM dof_native.config"));
        }
    }
    @Test public void otherPresetsRejectLegacyWithoutAuditAndPendingPresetWriteReconciles() throws Exception {
        DofAdminService.Edit old=edit(before.revision());old.preset="native-60";rejects(409,()->service.save(old,null));
        assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
        service.save(edit(before.revision()),null);
        DofAdminService.Edit next=edit(after.revision());next.preset="native-60";
        DofAdminService failing=newService(failOnce("UPDATE `dnf_service`.`DOF_CHANGE_AUDIT`"));
        rejects(503,()->failing.save(next,null));
        assertEquals("PERSISTED",map(newService(ds).reconcile(next.requestId)).get("state"));
        assertEquals("PERSISTED",map(service.save(next,null)).get("state"));
        assertEquals("2",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
    }
    @Test public void creationPresetsPersistReloadReplayAndRestoreWithoutEnablingCreation() throws Exception {
        NativeUiState baseline=NativeUiLayout.candidate(before,Collections.singletonMap("create.0",-111));
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("UPDATE dof_native.config SET temp_3=?")) {
            p.setBytes(1,baseline.get("temp_3"));p.executeUpdate();
        }
        for(Map<String,Object> row:new NativeCreationPresets().list()) {
            DofAdminService.Edit change=edit(baseline.revision());change.preset=row.get("id").toString();
            NativeUiState expected=new NativeCreationPresets().apply(change.preset,baseline);
            assertEquals(Collections.singletonList("temp_3"),map(service.preview(change)).get("changedFields"));
            assertEquals("PERSISTED",map(service.save(change,null)).get("state"));
            assertEquals(expected.revision(),map(newService(ds).ui()).get("revision"));
            assertEquals(before.text("Hotkeys"),scalar("SELECT Hotkeys FROM dof_native.config"));
            assertEquals(before.text("temp_2"),scalar("SELECT temp_2 FROM dof_native.config"));
            assertEquals("PERSISTED",map(newService(ds).save(change,null)).get("state"));
            DofAdminService.Edit unchanged=edit(expected.revision());unchanged.preset=change.preset;
            assertEquals("NO_CHANGE",map(newService(ds).save(unchanged,null)).get("state"));
            String id=change.preset;change.preset=id.equals("creation-60")?"creation-85":"creation-60";
            rejects(409,()->service.save(change,null));change.preset=id;
            DofAdminService.Edit restore=edit(expected.revision());restore.preset=null;
            assertEquals("PERSISTED",map(newService(ds).save(restore,change.requestId)).get("state"));
            assertEquals(baseline.revision(),map(service.ui()).get("revision"));
        }
    }
    private DofAdminService.Edit featureEdit(String revision,boolean value) throws Exception {
        return json.readValue("{\"revision\":\""+revision+"\",\"requestId\":\""+UUID.randomUUID()+"\",\"features\":{\"native-136\":"+value+"}}",DofAdminService.Edit.class);
    }
    @Test public void featureSwitchesPersistReplayRejectChangedPayloadAndRestore() throws Exception {
        DofAdminService.Edit missing=featureEdit(before.revision(),true);
        rejects(409,()->service.save(missing,null));
        assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
        service.save(edit(before.revision()),null);
        boolean old="真".equals(NativeHotkeys.decode(after.text("Hotkeys")).text(136));
        DofAdminService.Edit change=featureEdit(after.revision(),!old);
        assertEquals(Collections.singletonList("Hotkeys"),map(service.preview(change)).get("changedFields"));
        assertEquals("PERSISTED",map(service.save(change,null)).get("state"));
        String changed=map(newService(ds).ui()).get("revision").toString();
        assertEquals("PERSISTED",map(newService(ds).save(change,null)).get("state"));
        change.features.put("native-136",json.readTree(Boolean.toString(old)));rejects(409,()->service.save(change,null));
        DofAdminService.Edit restore=edit(changed);restore.preset=null;service.save(restore,change.requestId);
        assertEquals(after.revision(),map(service.ui()).get("revision"));
        assertEquals("00FF01",scalar("SELECT HEX(unknown_setting) FROM dof_native.config"));
    }
    @Test public void featureInterruptionReconcilesWithoutRetryAndMixedMutationIsRejected() throws Exception {
        service.save(edit(before.revision()),null);
        DofAdminService.Edit change=featureEdit(after.revision(),!"真".equals(NativeHotkeys.decode(after.text("Hotkeys")).text(136)));
        change.switches=Collections.singletonMap("bloodLayout",json.readTree("true"));
        rejects(400,()->service.save(change,null));change.switches=null;
        DofAdminService failing=newService(failOnce("UPDATE `dnf_service`.`DOF_CHANGE_AUDIT`"));
        rejects(503,()->failing.save(change,null));
        assertEquals("PERSISTED",map(newService(ds).reconcile(change.requestId)).get("state"));
        assertEquals("PERSISTED",map(newService(ds).save(change,null)).get("state"));
        assertEquals("2",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
    }
    private DofAdminService.Edit layoutEdit(String revision) throws Exception {
        return json.readValue("{\"revision\":\""+revision+"\",\"requestId\":\""+UUID.randomUUID()+"\",\"layout\":{\"blood.40\":5.2,\"create.0\":99}}",DofAdminService.Edit.class);
    }
    @Test public void customLayoutKeepsHotkeysSupportsDecimalAndRestoresExactSnapshot() throws Exception {
        DofAdminService.Edit change=layoutEdit(before.revision());
        assertEquals(Collections.singletonList("temp_3"),map(service.preview(change)).get("changedFields"));
        assertEquals("PERSISTED",map(service.save(change,null)).get("state"));
        String current=map(newService(ds).ui()).get("revision").toString();
        assertEquals(before.text("Hotkeys"),scalar("SELECT Hotkeys FROM dof_native.config"));
        String[] parts=scalar("SELECT temp_3 FROM dof_native.config").split("##");
        assertEquals("5.2",parts[0].split("&")[40]);assertEquals("99",parts[1].split("&")[0]);
        assertEquals("PERSISTED",map(newService(ds).save(change,null)).get("state"));
        change.layout.put("create.0",json.readTree("100"));rejects(409,()->service.save(change,null));
        DofAdminService.Edit restore=edit(current);restore.preset=null;service.save(restore,change.requestId);
        assertEquals(before.revision(),map(service.ui()).get("revision"));
    }
    @Test public void customLayoutFailureReconcilesAndInvalidNumbersNeverCreateAudit() throws Exception {
        DofAdminService.Edit change=layoutEdit(before.revision());
        for(String bad:Arrays.asList("\"99\"","null","1.5","2147483648")) {
            change.layout.put("create.0",json.readTree(bad));rejects(400,()->service.save(change,null));
        }
        assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
        change.layout.put("create.0",json.readTree("99"));
        DofAdminService failing=newService(failOnce("UPDATE `dnf_service`.`DOF_CHANGE_AUDIT`"));
        rejects(503,()->failing.save(change,null));
        assertEquals("PERSISTED",map(newService(ds).reconcile(change.requestId)).get("state"));
        assertEquals("PERSISTED",map(service.save(change,null)).get("state"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
    }

    private DofAdminService.Edit valueEdit(String revision) throws Exception {
        DofAdminService.Edit e=new DofAdminService.Edit();e.revision=revision;e.requestId=UUID.randomUUID().toString();
        e.values=new LinkedHashMap<>();e.values.put("contractLevel",json.getNodeFactory().textNode(" 007 "));
        e.values.put("externalMusic",json.getNodeFactory().textNode("测试音乐"));return e;
    }
    @Test public void scalarValuesPersistExactBytesReplayAndRestoreWithNoGameplayClaim() throws Exception {
        DofAdminService.Edit change=valueEdit(before.revision());
        assertEquals(Collections.singletonList("Hotkeys"),map(service.preview(change)).get("changedFields"));
        assertEquals("PERSISTED",map(service.save(change,null)).get("state"));
        Map<?,?> saved=map(newService(ds).ui());String revision=saved.get("revision").toString();
        assertEquals("not_observed",saved.get("effectState"));
        NativeHotkeys actual=NativeHotkeys.decode(scalar("SELECT Hotkeys FROM dof_native.config"));
        assertEquals(" 007 ",actual.text(31));assertEquals("测试音乐",actual.text(58));
        assertEquals(before.text("temp_2"),scalar("SELECT temp_2 FROM dof_native.config"));
        assertEquals(before.text("temp_3"),scalar("SELECT temp_3 FROM dof_native.config"));
        assertEquals("PERSISTED",map(newService(ds).save(change,null)).get("state"));
        change.values.put("contractLevel",json.getNodeFactory().textNode("008"));rejects(409,()->service.save(change,null));
        DofAdminService.Edit restore=edit(revision);restore.preset=null;service.save(restore,change.requestId);
        assertEquals(before.revision(),map(service.ui()).get("revision"));
        assertEquals("00FF01",scalar("SELECT HEX(unknown_setting) FROM dof_native.config"));
    }
    @Test public void scalarValuesRejectWrongTypesMixedEditsAndMissingFieldsWithoutAudit() throws Exception {
        DofAdminService.Edit e=valueEdit(before.revision());
        for(String invalid:Arrays.asList("null","true","7","[]","{}")) {
            e.values.put("contractLevel",json.readTree(invalid));rejects(400,()->service.save(e,null));
        }
        e.values.put("contractLevel",json.getNodeFactory().textNode("a*b"));rejects(400,()->service.save(e,null));
        e.values.put("contractLevel",json.getNodeFactory().textNode("80"));e.preset="native-100";rejects(400,()->service.save(e,null));e.preset=null;
        e.values.put("dungeonList",json.getNodeFactory().textNode("1"));rejects(409,()->service.save(e,null));
        assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
        assertEquals(before.revision(),map(service.ui()).get("revision"));
    }
    @Test public void scalarInterruptedWriteReconcilesWithoutAutomaticRetry() throws Exception {
        DofAdminService.Edit e=valueEdit(before.revision());
        DofAdminService failing=newService(failOnce("UPDATE `dnf_service`.`DOF_CHANGE_AUDIT`"));
        rejects(503,()->failing.save(e,null));
        assertEquals("PREPARED",map(((List<?>)service.history()).get(0)).get("state"));
        DofAdminService.Edit another=valueEdit(map(service.ui()).get("revision").toString());rejects(409,()->service.save(another,null));
        assertEquals("PERSISTED",map(newService(ds).reconcile(e.requestId)).get("state"));
        assertEquals("PERSISTED",map(newService(ds).save(e,null)).get("state"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
    }

    @Test public void earringCompositePersistsOnlySelectedPartsAndRestoresExactBytes() throws Exception {
        service.save(edit(before.revision()),null);
        String[] parts=new String[69];for(int i=0;i<parts.length;i++)parts[i]="old"+i;
        String wire=NativeHotkeys.decode(after.text("Hotkeys")).withText(Collections.singletonMap(131,String.join("##",parts))).encode();
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("UPDATE dof_native.config SET Hotkeys=?")) {p.setString(1,wire);p.executeUpdate();}
        String baseline=map(service.ui()).get("revision").toString();
        DofAdminService.Edit e=new DofAdminService.Edit();e.revision=baseline;e.requestId=UUID.randomUUID().toString();e.values=new LinkedHashMap<>();
        e.values.put("earring.typeName",json.getNodeFactory().textNode(" 测试耳环 "));e.values.put("earring.position66",json.getNodeFactory().textNode("-20"));
        assertEquals(Collections.singletonList("Hotkeys"),map(service.preview(e)).get("changedFields"));
        assertEquals("PERSISTED",map(service.save(e,null)).get("state"));
        String current=map(newService(ds).ui()).get("revision").toString();
        String[] actual=NativeHotkeys.decode(scalar("SELECT Hotkeys FROM dof_native.config")).text(131).split("##",-1);
        assertEquals(69,actual.length);for(int i=0;i<69;i++)assertEquals(i==0?" 测试耳环 ":i==66?"-20":parts[i],actual[i]);
        assertEquals("PERSISTED",map(newService(ds).save(e,null)).get("state"));
        DofAdminService.Edit restore=edit(current);restore.preset=null;service.save(restore,e.requestId);
        assertEquals(wire,scalar("SELECT Hotkeys FROM dof_native.config"));assertEquals(baseline,map(service.ui()).get("revision"));
    }
    @Test public void earringConflictsDelimitersAndInterruptedWriteUseSameRecoveryGuard() throws Exception {
        service.save(edit(before.revision()),null);
        String[] parts=new String[67];Arrays.fill(parts,"0");String wire=NativeHotkeys.decode(after.text("Hotkeys")).withText(Collections.singletonMap(131,String.join("##",parts))).encode();
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("UPDATE dof_native.config SET Hotkeys=?")) {p.setString(1,wire);p.executeUpdate();}
        DofAdminService.Edit e=new DofAdminService.Edit();e.revision=map(service.ui()).get("revision").toString();e.requestId=UUID.randomUUID().toString();e.values=new LinkedHashMap<>();
        e.values.put("earring.typeName",json.getNodeFactory().textNode("a##b"));rejects(400,()->service.save(e,null));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_CHANGE_AUDIT"));
        e.values.put("earring.typeName",json.getNodeFactory().textNode("新耳环"));
        rejects(503,()->newService(uncheckedFailure()).save(e,null));
        assertEquals("PERSISTED",map(newService(ds).reconcile(e.requestId)).get("state"));
        assertEquals("PERSISTED",map(newService(ds).save(e,null)).get("state"));
        e.values.put("earring.typeName",json.getNodeFactory().textNode("different"));rejects(409,()->service.save(e,null));
    }
    private DataSource uncheckedFailure() {
        try{return failOnce("UPDATE `dnf_service`.`DOF_CHANGE_AUDIT`");}catch(SQLException e){throw new RuntimeException(e);}
    }
}
