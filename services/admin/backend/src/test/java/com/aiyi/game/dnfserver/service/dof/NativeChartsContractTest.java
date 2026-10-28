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

/** Real SQL/service tests on disposable rules, never a native gameplay claim. */
public class NativeChartsContractTest {
    private ManagedPostalContractTest fixture;private DataSource ds;private DofAdminService access;
    private ActivityConfigService service;
    @Before @SuppressWarnings("unchecked") public void setup()throws Exception {
        fixture=new ManagedPostalContractTest();fixture.setup();
        ds=(DataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"access");
        assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='dof_native'"));
        sql("CREATE DATABASE dof_native DEFAULT CHARSET=utf8");
        ((List<String>)ReflectionTestUtils.getField(fixture,"created")).add("dof_native");
        sql("CREATE TABLE dof_native.game_charts_config(id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,min_zhanli INT NOT NULL DEFAULT 0,jiangli_day INT NOT NULL DEFAULT 1,jiangli_hour INT NOT NULL,is_mobai INT NOT NULL DEFAULT 0,status INT NOT NULL DEFAULT 1,next_time DATETIME NULL) ENGINE=MyISAM");
        sql("INSERT INTO dof_native.game_charts_config VALUES(1,100,1,3,0,0,'2026-11-01 03:00:00')");
        sql("CREATE TABLE dof_native.game_charts_code(top_index INT PRIMARY KEY,code_data VARCHAR(3000) NOT NULL,code_num VARCHAR(3000) NOT NULL) ENGINE=InnoDB");
        sql("INSERT INTO dof_native.game_charts_code VALUES(0,'3037','1')");
        ManagedOperationJournal journal=new ManagedOperationJournal();ReflectionTestUtils.setField(journal,"access",access);
        service=new ActivityConfigService();ReflectionTestUtils.setField(service,"journal",journal);
        ReflectionTestUtils.setField(service,"accountVODao",ReflectionTestUtils.getField(access,"accountVODao"));
    }
    @After public void cleanup()throws Exception {
        if(access!=null&&ds!=null)ReflectionTestUtils.setField(access,"dataSource",ds);
        if(fixture!=null)fixture.cleanup();
    }
    private void sql(String text)throws Exception {try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(text);}}
    private String scalar(String text)throws Exception {try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(text)){assertTrue(r.next());return r.getString(1);}}
    private Map<?,?> row(String section){return (Map<?,?>)((List<?>)service.list(section).get("rows")).get(0);}
    @SuppressWarnings("unchecked") private ActivityConfigService.Edit edit(String section) {
        Map<?,?> row=row(section);ActivityConfigService.Edit e=new ActivityConfigService.Edit();e.requestId=UUID.randomUUID().toString();e.operation="upsert";e.revision=row.get("revision").toString();e.values=new LinkedHashMap<>((Map<String,Object>)row.get("values"));return e;
    }
    private ActivityConfigService.Edit restore(ActivityConfigService.Edit source) {
        ActivityConfigService.Edit e=new ActivityConfigService.Edit();e.requestId=UUID.randomUUID().toString();e.operation="restore";e.targetRequestId=source.requestId;e.revision=row("charts").get("revision").toString();return e;
    }
    private void reject(int status,Runnable operation){try{operation.run();fail("Expected rejection");}catch(ActivityConfigService.Failure e){assertEquals(e.getMessage(),status,e.status);}catch(DofFailure e){assertEquals(e.getMessage(),status,e.status);}}
    private void faultOnce(String prefix)throws Exception {
        DataSource faulty=mock(DataSource.class);AtomicBoolean armed=new AtomicBoolean(true);
        when(faulty.getConnection()).thenAnswer(call->{Connection c=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,args)->{
            if(m.getName().equals("prepareStatement")&&args[0].toString().startsWith(prefix)&&armed.compareAndSet(true,false))throw new SQLException("synthetic interruption");
            try{return m.invoke(c,args);}catch(InvocationTargetException e){throw e.getCause();}
        });});ReflectionTestUtils.setField(access,"dataSource",faulty);
    }
    @Test public void verifiedNamedUniqueIdentityWorksWithoutInventingAPrimaryKey()throws Exception {
        sql("ALTER TABLE dof_native.game_charts_config DROP PRIMARY KEY,ADD UNIQUE KEY game_charts_config_pk(id)");
        ActivityConfigService.Edit e=edit("charts");e.values.put("min_zhanli",250);
        service.preview("charts",e);assertEquals("PERSISTED",service.save("charts",e,false).get("state"));
        ActivityConfigService.Edit undo=restore(e);service.preview("charts",undo);service.restore("charts",e.requestId,undo);
        assertEquals("100",scalar("SELECT min_zhanli FROM dof_native.game_charts_config"));
        sql("ALTER TABLE dof_native.game_charts_config DROP KEY game_charts_config_pk,ADD KEY identity_without_unique(id)");
        reject(409,()->service.preview("charts",edit("charts")));
        sql("ALTER TABLE dof_native.game_charts_config ADD UNIQUE KEY unknown_identity(id)");
        reject(409,()->service.preview("charts",edit("charts")));
    }
    @Test public void thresholdPreviewReplayAndRestorePreserveNativeSchedule()throws Exception {
        ActivityConfigService.Edit e=edit("charts");e.values.put("min_zhanli",250);
        assertEquals("PREVIEWED",service.preview("charts",e).get("state"));assertEquals("100",scalar("SELECT min_zhanli FROM dof_native.game_charts_config"));
        assertEquals("PERSISTED",service.save("charts",e,false).get("state"));
        ActivityConfigService.Edit stale=edit("charts");stale.revision=e.revision;stale.values.put("min_zhanli",300);
        reject(409,()->service.preview("charts",stale));
        sql("UPDATE dof_native.game_charts_config SET next_time='2026-11-02 03:00:00'");
        assertEquals("PERSISTED",service.save("charts",e,false).get("state"));
        ActivityConfigService.Edit undo=restore(e);service.preview("charts",undo);service.restore("charts",e.requestId,undo);
        assertEquals("100:0:0:2026-11-02 03:00:00",scalar("SELECT CONCAT(min_zhanli,':',status,':',is_mobai,':',next_time) FROM dof_native.game_charts_config"));
    }
    @Test public void periodEditDoesNotRescheduleAndRestoreRejectsChangedNativeDeadline()throws Exception {
        ActivityConfigService.Edit e=edit("charts");e.values.put("jiangli_day",2);e.values.put("jiangli_hour",4);
        service.preview("charts",e);service.save("charts",e,false);
        assertEquals("2026-11-01 03:00:00",scalar("SELECT DATE_FORMAT(next_time,'%Y-%m-%d %H:%i:%s') FROM dof_native.game_charts_config"));
        sql("UPDATE dof_native.game_charts_config SET next_time='2026-11-03 04:00:00'");
        reject(409,()->service.preview("charts",restore(e)));
        assertEquals("2:4",scalar("SELECT CONCAT(jiangli_day,':',jiangli_hour) FROM dof_native.game_charts_config"));
    }
    @Test public void rankingAndWorshipFlagsIndependentlyBlockRulesAndRewards()throws Exception {
        int threshold=200;
        for(String flag:Arrays.asList("status","is_mobai")) {
            ActivityConfigService.Edit e=edit("charts");e.values.put("min_zhanli",threshold++);service.preview("charts",e);
            sql("UPDATE dof_native.game_charts_config SET "+flag+"=1");
            reject(409,()->service.save("charts",e,false));
            ActivityConfigService.Edit reward=edit("charts-rewards");reward.values.put("code_num","2");
            reject(409,()->service.preview("charts-rewards",reward));
            sql("UPDATE dof_native.game_charts_config SET "+flag+"=0");service.save("charts",e,false);
        }
        assertEquals("1",scalar("SELECT code_num FROM dof_native.game_charts_code"));
    }
    @Test public void protectedFieldsCreateDeleteAndAmbiguousSingletonReject()throws Exception {
        for(String field:Arrays.asList("status","is_mobai","next_time")) {
            ActivityConfigService.Edit e=edit("charts");e.values.put(field,field.equals("next_time")?"2026-11-02 00:00:00":1);
            reject(400,()->service.preview("charts",e));
        }
        ActivityConfigService.Edit create=edit("charts");create.revision="new";reject(409,()->service.preview("charts",create));
        ActivityConfigService.Edit delete=edit("charts");delete.operation="delete";reject(409,()->service.preview("charts",delete));
        sql("INSERT INTO dof_native.game_charts_config VALUES(2,0,1,1,0,0,NULL)");
        reject(409,()->service.preview("charts",edit("charts")));
    }
    @Test public void invalidCalendarRangesRejectWithoutNormalizingLegacyValues()throws Exception {
        for(int[] pair:new int[][]{{0,3},{1,24},{2147483647,3}}) {
            ActivityConfigService.Edit e=edit("charts");e.values.put("jiangli_day",pair[0]);e.values.put("jiangli_hour",pair[1]);
            reject(400,()->service.preview("charts",e));
        }
        sql("UPDATE dof_native.game_charts_config SET jiangli_day=0,jiangli_hour=30,next_time=NULL");
        ActivityConfigService.Edit e=edit("charts");e.values.put("min_zhanli",200);service.preview("charts",e);service.save("charts",e,false);
        assertEquals("0:30",scalar("SELECT CONCAT(jiangli_day,':',jiangli_hour) FROM dof_native.game_charts_config"));
        assertNull(scalar("SELECT next_time FROM dof_native.game_charts_config"));
    }
    @Test public void unknownFieldsTriggersAndActualPermissionRevocationReject()throws Exception {
        ActivityConfigService.Edit e=edit("charts");e.values.put("min_zhanli",200);
        sql("ALTER TABLE dof_native.game_charts_config ADD unknown_setting INT");reject(409,()->service.preview("charts",e));
        sql("ALTER TABLE dof_native.game_charts_config DROP unknown_setting");
        sql("ALTER TABLE dof_native.game_charts_config MODIFY next_time DATETIME(3) NULL");reject(409,()->service.preview("charts",e));
        sql("ALTER TABLE dof_native.game_charts_config MODIFY next_time DATETIME NULL");
        sql("CREATE TRIGGER dof_native.unexpected_charts BEFORE UPDATE ON dof_native.game_charts_config FOR EACH ROW SET NEW.min_zhanli=NEW.min_zhanli");
        reject(409,()->service.preview("charts",e));sql("DROP TRIGGER dof_native.unexpected_charts");
        service.preview("charts",e);sql("UPDATE d_taiwan.accounts SET admin=0 WHERE UID=1");
        reject(403,()->service.save("charts",e,false));assertEquals("100",scalar("SELECT min_zhanli FROM dof_native.game_charts_config"));
    }
    @Test public void interruptedBeforeMyisamWriteRequiresOriginalIdReconciliation()throws Exception {
        ActivityConfigService.Edit e=edit("charts");e.values.put("min_zhanli",200);service.preview("charts",e);faultOnce("UPDATE `dof_native`.`game_charts_config`");
        reject(503,()->service.save("charts",e,false));ReflectionTestUtils.setField(access,"dataSource",ds);
        assertEquals("100",scalar("SELECT min_zhanli FROM dof_native.game_charts_config"));
        reject(409,()->service.preview("charts",edit("charts")));
        assertEquals("NOT_APPLIED",service.reconcile("charts",e.requestId).get("state"));
    }
    @Test public void interruptedAfterMyisamWriteReconcilesAndRestores()throws Exception {
        ActivityConfigService.Edit e=edit("charts");e.values.put("min_zhanli",200);service.preview("charts",e);faultOnce("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");
        reject(503,()->service.save("charts",e,false));ReflectionTestUtils.setField(access,"dataSource",ds);
        assertEquals("200",scalar("SELECT min_zhanli FROM dof_native.game_charts_config"));
        assertEquals("PERSISTED",service.reconcile("charts",e.requestId).get("state"));
        ActivityConfigService.Edit undo=restore(e);service.preview("charts",undo);service.restore("charts",e.requestId,undo);
        assertEquals("100",scalar("SELECT min_zhanli FROM dof_native.game_charts_config"));
    }
}
