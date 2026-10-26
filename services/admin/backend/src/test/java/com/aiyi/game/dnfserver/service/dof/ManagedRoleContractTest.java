package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Creates only synthetic role schemas after the existing disposable-fixture guards pass. */
public class ManagedRoleContractTest {
    private DataSource ds;private DofAdminService access;private ManagedRoleService service;
    private String uuid;private boolean rolesCreated,loginCreated;
    @Before public void setup()throws Exception {
        DofMyisamContractTest fixture=new DofMyisamContractTest();fixture.setup();
        ds=(DataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"service");
        uuid=scalar("SELECT @@server_uuid");
        assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('taiwan_cain','taiwan_login')"));
        sql("CREATE DATABASE taiwan_cain");rolesCreated=true;sql("CREATE DATABASE taiwan_login");loginCreated=true;
        sql("CREATE TABLE taiwan_cain.charac_info(charac_no INT PRIMARY KEY,m_id INT,maxHP SMALLINT UNSIGNED,maxMP SMALLINT UNSIGNED,phy_attack SMALLINT UNSIGNED,phy_defense SMALLINT UNSIGNED,mag_attack SMALLINT UNSIGNED,mag_defense SMALLINT UNSIGNED,attack_speed SMALLINT UNSIGNED,cast_speed SMALLINT UNSIGNED,move_speed SMALLINT UNSIGNED,hit_recovery SMALLINT,jump SMALLINT,fatigue SMALLINT,unknown_setting VARBINARY(8)) ENGINE=MyISAM");
        sql("INSERT INTO taiwan_cain.charac_info VALUES(1,101,100,100,10,10,10,10,10,10,10,0,0,156,X'00FF')");
        sql("CREATE TABLE taiwan_login.login_account_3(m_id INT,login_status TINYINT) ENGINE=MyISAM");
        sql("INSERT INTO taiwan_login.login_account_3 VALUES(101,0)");
        sql("DELETE FROM dnf_service.DOF_OPERATION_JOURNAL");
        ManagedOperationJournal journal=new ManagedOperationJournal();ReflectionTestUtils.setField(journal,"access",access);
        service=new ManagedRoleService();ReflectionTestUtils.setField(service,"journal",journal);
    }
    @After public void clean()throws Exception {
        try {
            if(rolesCreated||loginCreated) {
                assertEquals(uuid,scalar("SELECT @@server_uuid"));
                assertEquals("disposable-native-ui-contract",scalar("SELECT purpose FROM dnf_service.DOF_CONTRACT_TEST_MARKER"));
                if(rolesCreated)sql("DROP DATABASE taiwan_cain");if(loginCreated)sql("DROP DATABASE taiwan_login");
            }
        }finally{ThreadUtil.setUserId(1L);}
    }
    private String scalar(String sql)throws Exception {try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}}
    private void sql(String sql)throws Exception {try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(sql);}}
    private Map<?,?> map(Object value){return (Map<?,?>)value;}
    private ManagedRoleService.Edit edit(String field,int value) {
        ManagedRoleService.Edit e=new ManagedRoleService.Edit();e.characNo=1;e.requestId=UUID.randomUUID().toString();e.revision=(String)service.read(1).get("revision");e.values=new LinkedHashMap<>();e.values.put(field,new ObjectMapper().valueToTree(value));return e;
    }
    private ManagedRoleService.Edit restore(String target) {
        ManagedRoleService.Edit e=edit("maxHp",100);e.values=null;e.operation="restore";e.targetRequestId=target;return e;
    }
    private void reject(int status,Runnable action){try{action.run();fail("Expected refusal");}catch(DofFailure e){assertEquals(e.getMessage(),status,e.status);}}
    private void fault(String prefix)throws Exception {
        DataSource broken=mock(DataSource.class);AtomicBoolean armed=new AtomicBoolean(true);
        when(broken.getConnection()).thenAnswer(call->{Connection c=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("prepareStatement")&&a[0].toString().startsWith(prefix)&&armed.compareAndSet(true,false))throw new SQLException("synthetic interruption");
            try{return m.invoke(c,a);}catch(InvocationTargetException e){throw e.getCause();}
        });});ReflectionTestUtils.setField(access,"dataSource",broken);
    }
    @Test public void fixedPatchRequiresPreviewAndReplayDoesNotRewriteLaterChanges()throws Exception {
        ManagedRoleService.Edit e=edit("maxHp",200);reject(404,()->service.save(e));
        assertEquals("PREVIEWED",map(service.preview(1,e)).get("state"));assertEquals("100",scalar("SELECT maxHP FROM taiwan_cain.charac_info"));
        assertEquals("PERSISTED",map(service.save(e)).get("state"));sql("UPDATE taiwan_cain.charac_info SET maxHP=300");
        assertEquals("PERSISTED",map(service.save(e)).get("state"));assertEquals("300",scalar("SELECT maxHP FROM taiwan_cain.charac_info"));
        e.values.put("maxHp",new ObjectMapper().valueToTree(201));reject(409,()->service.save(e));
        assertEquals("00FF",scalar("SELECT HEX(unknown_setting) FROM taiwan_cain.charac_info"));
    }
    @Test public void rechecksOfflineStateAtCommitAndUnknownStateIsNotOffline()throws Exception {
        ManagedRoleService.Edit e=edit("fatigue",0);service.preview(1,e);sql("UPDATE taiwan_login.login_account_3 SET login_status=1");
        reject(409,()->service.save(e));assertEquals("156",scalar("SELECT fatigue FROM taiwan_cain.charac_info"));
        sql("DELETE FROM taiwan_login.login_account_3");assertEquals(false,service.read(1).get("editable"));reject(409,()->service.preview(1,edit("fatigue",1)));
        sql("INSERT INTO taiwan_login.login_account_3 VALUES(101,0),(101,0)");reject(409,()->service.save(e));
    }
    @Test public void boundedIntegersUnknownFieldsAndVersionsAreRejected()throws Exception {
        reject(400,()->service.preview(1,edit("jump",32768)));reject(400,()->service.preview(1,edit("fatigue",-1)));
        reject(400,()->service.preview(1,edit("maxHp",65536)));reject(400,()->service.preview(1,edit("m_id",99)));
        ManagedRoleService.Edit e=edit("maxHp",200);e.values.put("maxHp",new ObjectMapper().valueToTree(1.5));reject(400,()->service.preview(1,e));
        ManagedRoleService.Edit a=edit("maxHp",200),b=edit("maxHp",300);service.preview(1,a);service.preview(1,b);service.save(a);reject(409,()->service.save(b));
    }
    @Test public void interruptionBeforeNativeWriteBlocksFreshIdsUntilReconciliation()throws Exception {
        ManagedRoleService.Edit e=edit("maxHp",200);service.preview(1,e);fault("UPDATE `taiwan_cain`.`charac_info`");reject(503,()->service.save(e));
        reject(409,()->service.preview(1,edit("maxHp",300)));assertEquals("NOT_APPLIED",map(service.reconcile(1,e.requestId)).get("state"));
        assertEquals("100",scalar("SELECT maxHP FROM taiwan_cain.charac_info"));
    }
    @Test public void interruptionAfterNativeWriteAndTargetedRestorePreserveOtherFields()throws Exception {
        ManagedRoleService.Edit e=edit("maxHp",200);service.preview(1,e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");reject(503,()->service.save(e));
        assertEquals("PERSISTED",map(service.reconcile(1,e.requestId)).get("state"));sql("UPDATE taiwan_cain.charac_info SET fatigue=99");
        ManagedRoleService.Edit r=restore(e.requestId);service.preview(1,r);assertEquals("PERSISTED",map(service.save(r)).get("state"));
        assertEquals("100",scalar("SELECT maxHP FROM taiwan_cain.charac_info"));assertEquals("99",scalar("SELECT fatigue FROM taiwan_cain.charac_info"));
    }
    @Test public void restorePreservesValidSignedNativeValues()throws Exception {
        sql("UPDATE taiwan_cain.charac_info SET fatigue=-1");ManagedRoleService.Edit e=edit("fatigue",156);service.preview(1,e);service.save(e);
        ManagedRoleService.Edit r=restore(e.requestId);service.preview(1,r);service.save(r);assertEquals("-1",scalar("SELECT fatigue FROM taiwan_cain.charac_info"));
    }
    @Test public void restoreRefusesOwnershipAndTouchedFieldConflicts()throws Exception {
        ManagedRoleService.Edit e=edit("maxHp",200);service.preview(1,e);service.save(e);
        sql("UPDATE taiwan_cain.charac_info SET maxHP=300");reject(409,()->service.preview(1,restore(e.requestId)));
        sql("UPDATE taiwan_cain.charac_info SET maxHP=200,m_id=102");sql("INSERT INTO taiwan_login.login_account_3 VALUES(102,0)");
        reject(409,()->service.preview(1,restore(e.requestId)));
    }
    @Test public void conflictAcknowledgmentDoesNotWriteNativeAttributes()throws Exception {
        ManagedRoleService.Edit e=edit("maxHp",200);service.preview(1,e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?");reject(503,()->service.save(e));
        sql("UPDATE taiwan_cain.charac_info SET maxHP=300");assertEquals("CONFLICT",map(service.reconcile(1,e.requestId)).get("state"));
        ManagedRoleService.Edit keep=restore(e.requestId);keep.operation="accept-current";keep.reason="synthetic conflict verified";service.preview(1,keep);assertEquals("NO_CHANGE",map(service.save(keep)).get("state"));
        assertEquals("300",scalar("SELECT maxHP FROM taiwan_cain.charac_info"));assertEquals("ACKNOWLEDGED",scalar("SELECT state FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+e.requestId+"'"));
    }
    @Test public void incompatibleSchemaAndNonAdminsCannotWrite()throws Exception {
        sql("ALTER TABLE taiwan_cain.charac_info MODIFY maxHP INT UNSIGNED");reject(409,()->service.read(1));
        ThreadUtil.setUserId(999L);reject(403,()->service.read(1));
    }
}
