package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.service.extension.ActivityConfigService;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Disposable SQL only. No player consumption, scheduler or native reward execution. */
public class NativeChartsRewardsContractTest {
    private NativeChartsContractTest fixture;private DataSource ds;private DofAdminService access;private ActivityConfigService service;
    @Before public void setup()throws Exception {
        fixture=new NativeChartsContractTest();fixture.setup();
        ds=(DataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"access");service=(ActivityConfigService)ReflectionTestUtils.getField(fixture,"service");
    }
    @After public void cleanup()throws Exception {if(access!=null&&ds!=null)ReflectionTestUtils.setField(access,"dataSource",ds);if(fixture!=null)fixture.cleanup();}
    private void sql(String text)throws Exception {try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(text);}}
    private String scalar(String text)throws Exception {try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(text)){assertTrue(r.next());return r.getString(1);}}
    private String csv(String value,int n){return String.join(",",Collections.nCopies(n,value));}
    @SuppressWarnings("unchecked") private ActivityConfigService.Edit edit(int rank) {
        Map<String,Object> row=null;String revision="new";
        for(Object item:(List<?>)service.list("charts-rewards").get("rows")) {
            Map<?,?> envelope=(Map<?,?>)item;Map<String,Object> values=(Map<String,Object>)envelope.get("values");
            if(Integer.parseInt(values.get("top_index").toString())==rank){row=new LinkedHashMap<>(values);revision=envelope.get("revision").toString();break;}
        }
        if(row==null){row=new LinkedHashMap<>();row.put("top_index",rank);row.put("code_data","3037");row.put("code_num","1");}
        ActivityConfigService.Edit e=new ActivityConfigService.Edit();e.operation="upsert";e.requestId=UUID.randomUUID().toString();e.revision=revision;e.values=row;return e;
    }
    private ActivityConfigService.Edit restore(ActivityConfigService.Edit original,int rank) {
        ActivityConfigService.Edit e=new ActivityConfigService.Edit();e.operation="restore";e.requestId=UUID.randomUUID().toString();e.targetRequestId=original.requestId;e.revision=edit(rank).revision;return e;
    }
    private void persist(ActivityConfigService.Edit e){service.preview("charts-rewards",e);assertEquals("PERSISTED",service.save("charts-rewards",e,e.operation.equals("delete")).get("state"));}
    private void undo(ActivityConfigService.Edit e,int rank){ActivityConfigService.Edit r=restore(e,rank);service.preview("charts-rewards",r);assertEquals("PERSISTED",service.restore("charts-rewards",e.requestId,r).get("state"));}
    private void reject(int status,Runnable action){try{action.run();fail("Expected rejection");}catch(ActivityConfigService.Failure e){assertEquals(e.getMessage(),status,e.status);}catch(DofFailure e){assertEquals(e.getMessage(),status,e.status);}}
    private void faultOnce(String prefix)throws Exception {
        DataSource faulty=mock(DataSource.class);AtomicBoolean armed=new AtomicBoolean(true);
        when(faulty.getConnection()).thenAnswer(call->{Connection c=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,args)->{
            if(m.getName().equals("prepareStatement")&&args[0].toString().startsWith(prefix)&&armed.compareAndSet(true,false))throw new SQLException("synthetic interruption");
            try{return m.invoke(c,args);}catch(InvocationTargetException e){throw e.getCause();}
        });});ReflectionTestUtils.setField(access,"dataSource",faulty);
    }
    @Test public void longPairedRewardsPersistReplayAndRestoreWithoutTruncation()throws Exception {
        ActivityConfigService.Edit e=edit(0);String ids=csv("2147483647",50),counts=csv("2147483646",50);assertTrue(ids.length()>255);
        e.values.put("code_data",ids);e.values.put("code_num",counts);persist(e);
        assertEquals(ids,scalar("SELECT code_data FROM dof_native.game_charts_code WHERE top_index=0"));assertEquals(counts,scalar("SELECT code_num FROM dof_native.game_charts_code WHERE top_index=0"));
        assertEquals("PERSISTED",service.save("charts-rewards",e,false).get("state"));undo(e,0);
        assertEquals("3037:1",scalar("SELECT CONCAT(code_data,':',code_num) FROM dof_native.game_charts_code"));
    }
    @Test public void restoringIdsCannotMixWithSubsequentlyChangedQuantities()throws Exception {
        ActivityConfigService.Edit e=edit(0);e.values.put("code_data","3340");persist(e);
        sql("UPDATE dof_native.game_charts_code SET code_num='2'");reject(409,()->service.preview("charts-rewards",restore(e,0)));
        assertEquals("3340:2",scalar("SELECT CONCAT(code_data,':',code_num) FROM dof_native.game_charts_code"));
        sql("UPDATE dof_native.game_charts_code SET code_num='1'");undo(e,0);
    }
    @Test public void createDeleteAndRestoreIncludingWorshipRankAreReversible()throws Exception {
        ActivityConfigService.Edit create=edit(13);persist(create);undo(create,13);
        assertEquals("1",scalar("SELECT COUNT(*) FROM dof_native.game_charts_code"));
        assertEquals("PERSISTED",service.save("charts-rewards",create,false).get("state"));assertEquals("1",scalar("SELECT COUNT(*) FROM dof_native.game_charts_code"));
        ActivityConfigService.Edit delete=edit(0);delete.operation="delete";persist(delete);assertEquals("0",scalar("SELECT COUNT(*) FROM dof_native.game_charts_code"));undo(delete,0);
        assertEquals("3037:1",scalar("SELECT CONCAT(code_data,':',code_num) FROM dof_native.game_charts_code WHERE top_index=0"));
    }
    @Test public void malformedNewValuesAndStorageOverflowRejectBeforeWrite()throws Exception {
        for(String[] pair:new String[][]{{"1,2","1"},{"0","1"},{"2147483648","1"},{csv("1",51),csv("1",51)},{String.join("",Collections.nCopies(3001,"1")),"1"}}) {
            ActivityConfigService.Edit e=edit(0);e.values.put("code_data",pair[0]);e.values.put("code_num",pair[1]);reject(400,()->service.preview("charts-rewards",e));
        }
        assertEquals("3037:1",scalar("SELECT CONCAT(code_data,':',code_num) FROM dof_native.game_charts_code"));
    }
    @Test public void exactLegacySnapshotsRestoreBeyondNewEntrySemanticLimit()throws Exception {
        String oldIds=csv("3037",51),oldCounts=csv("1",51);sql("UPDATE dof_native.game_charts_code SET code_data='"+oldIds+"',code_num='"+oldCounts+"'");
        ActivityConfigService.Edit e=edit(0);e.values.put("code_data","3340");e.values.put("code_num","1");persist(e);undo(e,0);
        assertEquals(oldIds,scalar("SELECT code_data FROM dof_native.game_charts_code"));
        ActivityConfigService.Edit delete=edit(0);delete.operation="delete";persist(delete);undo(delete,0);
        assertEquals(oldCounts,scalar("SELECT code_num FROM dof_native.game_charts_code"));
    }
    @Test public void incompatibleColumnsAndUniqueKeysReject()throws Exception {
        ActivityConfigService.Edit e=edit(0);e.values.put("code_num","2");
        sql("ALTER TABLE dof_native.game_charts_code MODIFY code_data VARCHAR(255) NOT NULL");reject(409,()->service.preview("charts-rewards",e));
        sql("ALTER TABLE dof_native.game_charts_code MODIFY code_data VARCHAR(3000) NOT NULL");
        sql("ALTER TABLE dof_native.game_charts_code MODIFY code_num VARCHAR(3000) NULL");reject(409,()->service.preview("charts-rewards",e));
        sql("ALTER TABLE dof_native.game_charts_code MODIFY code_num VARCHAR(3000) NOT NULL");
        sql("ALTER TABLE dof_native.game_charts_code ADD UNIQUE KEY unexpected(code_data(10))");reject(409,()->service.preview("charts-rewards",e));
        sql("ALTER TABLE dof_native.game_charts_code DROP KEY unexpected,DROP PRIMARY KEY");reject(409,()->service.preview("charts-rewards",e));
    }
    @Test public void targetTriggersAndIncomingForeignKeysReject()throws Exception {
        ActivityConfigService.Edit e=edit(0);e.values.put("code_num","2");
        sql("CREATE TRIGGER dof_native.unexpected_reward BEFORE UPDATE ON dof_native.game_charts_code FOR EACH ROW SET NEW.code_num=NEW.code_num");
        reject(409,()->service.preview("charts-rewards",e));sql("DROP TRIGGER dof_native.unexpected_reward");
        sql("CREATE TABLE dof_native.reward_reference(rank_id INT PRIMARY KEY,FOREIGN KEY(rank_id) REFERENCES game_charts_code(top_index)) ENGINE=InnoDB");
        reject(409,()->service.preview("charts-rewards",e));sql("DROP TABLE dof_native.reward_reference");
        assertEquals("1",scalar("SELECT code_num FROM dof_native.game_charts_code"));
    }
    @Test public void bothEntriesAndActualPermissionsAreRecheckedAtCommit()throws Exception {
        int amount=2;
        for(String flag:Arrays.asList("status","is_mobai")) {
            ActivityConfigService.Edit e=edit(0);e.values.put("code_num",String.valueOf(amount++));service.preview("charts-rewards",e);
            sql("UPDATE dof_native.game_charts_config SET "+flag+"=1");reject(409,()->service.save("charts-rewards",e,false));
            sql("UPDATE dof_native.game_charts_config SET "+flag+"=0");service.save("charts-rewards",e,false);
        }
        ActivityConfigService.Edit e=edit(0);e.values.put("code_num","4");service.preview("charts-rewards",e);sql("UPDATE d_taiwan.accounts SET admin=0 WHERE UID=1");reject(403,()->service.save("charts-rewards",e,false));
        assertEquals("3",scalar("SELECT code_num FROM dof_native.game_charts_code"));
    }
    @Test public void interruptedBeforeWriteRequiresOriginalIdForBothEngines()throws Exception {
        for(String engine:Arrays.asList("InnoDB","MyISAM")) {
            sql("ALTER TABLE dof_native.game_charts_code ENGINE="+engine);ActivityConfigService.Edit e=edit(0);e.values.put("code_num","2");service.preview("charts-rewards",e);faultOnce("UPDATE `dof_native`.`game_charts_code`");
            reject(503,()->service.save("charts-rewards",e,false));ReflectionTestUtils.setField(access,"dataSource",ds);
            reject(409,()->service.preview("charts-rewards",edit(0)));assertEquals("NOT_APPLIED",service.reconcile("charts-rewards",e.requestId).get("state"));assertEquals("1",scalar("SELECT code_num FROM dof_native.game_charts_code"));
        }
    }
    @Test public void lostWriteResultReconcilesThenRestoresForBothEngines()throws Exception {
        for(String engine:Arrays.asList("InnoDB","MyISAM")) {
            sql("ALTER TABLE dof_native.game_charts_code ENGINE="+engine);ActivityConfigService.Edit e=edit(0);e.values.put("code_num","2");service.preview("charts-rewards",e);faultOnce("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");
            reject(503,()->service.save("charts-rewards",e,false));ReflectionTestUtils.setField(access,"dataSource",ds);
            assertEquals("2",scalar("SELECT code_num FROM dof_native.game_charts_code"));assertEquals("PERSISTED",service.reconcile("charts-rewards",e.requestId).get("state"));undo(e,0);
            assertEquals("1",scalar("SELECT code_num FROM dof_native.game_charts_code"));
        }
    }
    @Test public void concurrentNewRankDoesNotDuplicateOrOverwriteTheWinner()throws Exception {
        ActivityConfigService.Edit a=edit(13),b=edit(13);a.values.put("code_num","2");b.values.put("code_num","3");service.preview("charts-rewards",a);service.preview("charts-rewards",b);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<String>> runs=new ArrayList<>();for(ActivityConfigService.Edit e:Arrays.asList(a,b))runs.add(pool.submit(()->{ThreadUtil.setUserId(1L);start.await();try{return service.save("charts-rewards",e,false).get("state").toString();}catch(ActivityConfigService.Failure failure){return String.valueOf(failure.status);}catch(DofFailure failure){return String.valueOf(failure.status);}}));
            start.countDown();List<String> outcomes=new ArrayList<>();for(Future<String> run:runs)outcomes.add(run.get(10,TimeUnit.SECONDS));Collections.sort(outcomes);assertEquals(Arrays.asList("409","PERSISTED"),outcomes);
        }finally{pool.shutdownNow();}
        assertEquals("1",scalar("SELECT COUNT(*) FROM dof_native.game_charts_code WHERE top_index=13"));
    }
    @Test public void installedSharedWelfareConsumerMustAlsoBeDisabled()throws Exception {
        sql("CREATE TABLE dof_native.fenghuo_charts_config(min_zhanli INT,jiangli_day INT,jiangli_hour INT,min_baodi_zhanli INT,status INT,next_time DATETIME) ENGINE=InnoDB");
        sql("INSERT INTO dof_native.fenghuo_charts_config VALUES(0,1,3,0,0,NULL)");
        ActivityConfigService.Edit e=edit(0);e.values.put("code_num","2");service.preview("charts-rewards",e);
        sql("UPDATE dof_native.fenghuo_charts_config SET status=1");reject(409,()->service.save("charts-rewards",e,false));
        sql("UPDATE dof_native.fenghuo_charts_config SET status=0");service.save("charts-rewards",e,false);
        sql("UPDATE dof_native.fenghuo_charts_config SET status=NULL");reject(409,()->service.preview("charts-rewards",edit(0)));
        sql("UPDATE dof_native.fenghuo_charts_config SET status=0");sql("ALTER TABLE dof_native.fenghuo_charts_config DROP min_baodi_zhanli");reject(409,()->service.preview("charts-rewards",edit(0)));
        sql("DROP TABLE dof_native.fenghuo_charts_config");
        sql("CREATE VIEW dof_native.fenghuo_charts_config AS SELECT 0 min_zhanli,1 jiangli_day,3 jiangli_hour,0 min_baodi_zhanli,0 status,CAST(NULL AS DATETIME) next_time");
        reject(409,()->service.preview("charts-rewards",edit(0)));
        assertEquals("2",scalar("SELECT code_num FROM dof_native.game_charts_code"));
    }
}
