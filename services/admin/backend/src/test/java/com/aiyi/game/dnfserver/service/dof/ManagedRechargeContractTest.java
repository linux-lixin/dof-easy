package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
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
import java.util.regex.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual service/SQL on disposable MySQL 5.6, with no private UI or game fixtures. */
public class ManagedRechargeContractTest {
    private DriverManagerDataSource ds;private DofAdminService access;private ManagedRechargeService service;private String uuid;
    private final List<String> created=new ArrayList<>();private final ObjectMapper json=new ObjectMapper();
    @Before public void setup()throws Exception{
        String path=System.getProperty("dof.recharge.contract.properties");Assume.assumeTrue("requires dedicated disposable recharge MySQL fixture",path!=null);
        Properties properties=new Properties();try(InputStream in=Files.newInputStream(Paths.get(path))){properties.load(in);}
        uuid=properties.getProperty("uuid");assertNotNull(uuid);
        ds=new DriverManagerDataSource(properties.getProperty("url"),properties.getProperty("username"),properties.getProperty("password"));guard();
        assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('d_taiwan','taiwan_billing')"));
        for(String name:Arrays.asList("d_taiwan","taiwan_billing")){sql("CREATE DATABASE "+name+" DEFAULT CHARACTER SET utf8");created.add(name);}
        sql("CREATE TABLE d_taiwan.accounts(UID INT PRIMARY KEY,accountname VARCHAR(255) NOT NULL,admin INT,untouched VARBINARY(8)) ENGINE=InnoDB");
        sql("INSERT INTO d_taiwan.accounts VALUES(1,'admin',7,X'00FF'),(22,'player',NULL,X'00FF')");
        String seed=new String(Files.readAllBytes(Paths.get(System.getProperty("dof.recharge.contract.seed"))),StandardCharsets.UTF_8);
        Matcher table=Pattern.compile("CREATE TABLE `cash_cera` \\(.*?\\) ENGINE=InnoDB DEFAULT CHARSET=utf8;",Pattern.DOTALL).matcher(seed);assertTrue(table.find());
        sql(table.group().replace("CREATE TABLE `cash_cera`","CREATE TABLE taiwan_billing.cash_cera"));
        sql("INSERT INTO taiwan_billing.cash_cera VALUES('22',37,11,23,'2020-01-01 00:00:00','2019-01-01 00:00:00')");
        sql("CREATE TRIGGER taiwan_billing.cash_cera BEFORE UPDATE ON taiwan_billing.cash_cera FOR EACH ROW IF (new.cera >= 2000000000 OR new.cera <= 0 ) THEN SET new.cera = 0;END IF");
        sql("DELETE FROM dnf_service.DOF_OPERATION_JOURNAL");
        access=new DofAdminService();ReflectionTestUtils.setField(access,"dataSource",ds);ReflectionTestUtils.setField(access,"enabled",true);
        ReflectionTestUtils.setField(access,"environment","isolated");ReflectionTestUtils.setField(access,"instanceId","dof-recharge-contract");
        ReflectionTestUtils.setField(access,"databaseUuid",uuid);ReflectionTestUtils.setField(access,"adapterVersion","dof-native-1");
        AccountVODao accounts=mock(AccountVODao.class);AccountVO admin=new AccountVO();admin.setUid(1L);admin.setAdmin(true);when(accounts.get(1L)).thenReturn(admin);ReflectionTestUtils.setField(access,"accountVODao",accounts);
        ManagedOperationJournal journal=new ManagedOperationJournal();ReflectionTestUtils.setField(journal,"access",access);
        service=new ManagedRechargeService();ReflectionTestUtils.setField(service,"journal",journal);ThreadUtil.setUserId(1L);
    }
    private String scalar(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(text)){assertTrue(r.next());return r.getString(1);}}
    private void sql(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(text);}}
    private void guard()throws Exception{assertEquals(uuid,scalar("SELECT @@server_uuid"));assertEquals("disposable-recharge-contract",scalar("SELECT purpose FROM dnf_service.DOF_RECHARGE_TEST_MARKER"));assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_RECHARGE_TEST_MARKER"));}
    @After public void cleanup()throws Exception{try{if(!created.isEmpty()){guard();for(String name:created)sql("DROP DATABASE "+name);}}finally{ThreadUtil.setUserId(1L);}}
    private Map<?,?> map(Object value){return (Map<?,?>)value;}
    private ManagedRechargeService.Edit edit(long delta){ManagedRechargeService.Edit e=new ManagedRechargeService.Edit();e.uid=json.valueToTree(22);e.cera=json.valueToTree(delta);e.requestId=UUID.randomUUID().toString();e.revision=(String)map(service.status(22)).get("revision");e.operation="credit";e.reason="synthetic recharge reason";return e;}
    private void reject(int code,Runnable action){try{action.run();fail("Accepted unsafe request");}catch(DofFailure e){assertEquals(e.getMessage(),code,e.status);}}
    private void fault(String prefix,boolean loseCommittedReply,boolean failRollback)throws Exception{
        DataSource broken=mock(DataSource.class);AtomicBoolean armed=new AtomicBoolean(true);
        when(broken.getConnection()).thenAnswer(call->{Connection c=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,m,args)->{
            if(m.getName().equals("prepareStatement")&&prefix!=null&&args[0].toString().startsWith(prefix)&&armed.compareAndSet(true,false))throw new SQLException("synthetic write interruption");
            if(m.getName().equals("rollback")&&failRollback)throw new SQLException("synthetic rollback unavailable");
            try{Object result=m.invoke(c,args);if(m.getName().equals("commit")&&loseCommittedReply&&armed.compareAndSet(true,false))throw new SQLException("synthetic lost committed response");return result;}
            catch(InvocationTargetException e){throw e.getCause();}
        });});ReflectionTestUtils.setField(access,"dataSource",broken);
    }
    private void originalConnection(){ReflectionTestUtils.setField(access,"dataSource",ds);}
    private String balance()throws Exception{return scalar("SELECT cera FROM taiwan_billing.cash_cera WHERE account='22'");}
    @Test public void previewRequiredAndOriginalRequestReplaysWithoutCreditingAgain()throws Exception{
        ManagedRechargeService.Edit e=edit(5);reject(404,()->service.save(e));assertEquals("PREVIEWED",map(service.preview(22,e)).get("state"));assertEquals("37",balance());
        assertEquals("PERSISTED",map(service.save(e)).get("state"));assertEquals("42",balance());
        sql("UPDATE taiwan_billing.cash_cera SET cera=60 WHERE account='22'");assertEquals("PERSISTED",map(service.save(e)).get("state"));assertEquals("60",balance());
        e.cera=json.valueToTree(6);reject(409,()->service.save(e));
        assertEquals("11:23:2020-01-01 00:00:00:2019-01-01 00:00:00",scalar("SELECT CONCAT(cera_cold,':',mod_tran,':',mod_date,':',reg_date) FROM taiwan_billing.cash_cera"));
        assertEquals("00FF",scalar("SELECT HEX(untouched) FROM d_taiwan.accounts WHERE UID=22"));
    }
    @Test public void staleVersionsAccountReuseAndWrongInstanceCannotWrite()throws Exception{
        ManagedRechargeService.Edit e=edit(5);service.preview(22,e);sql("UPDATE taiwan_billing.cash_cera SET cera=38 WHERE account='22'");reject(409,()->service.save(e));
        ManagedRechargeService.Edit renamed=edit(5);service.preview(22,renamed);sql("UPDATE d_taiwan.accounts SET accountname='replacement' WHERE UID=22");reject(409,()->service.save(renamed));
        ReflectionTestUtils.setField(access,"databaseUuid",UUID.randomUUID().toString());reject(409,()->service.status(22));assertEquals("38",balance());
    }
    @Test public void innodbFailureAfterBalanceWriteRollsBackReceiptAndNeverUsesBalanceAsProof()throws Exception{
        ManagedRechargeService.Edit e=edit(5);service.preview(22,e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?",false,false);reject(503,()->service.save(e));originalConnection();
        assertEquals("37",balance());assertEquals("PREPARED",map(((List<?>)service.history(22)).get(0)).get("state"));
        reject(409,()->service.preview(22,edit(8)));
        sql("UPDATE taiwan_billing.cash_cera SET cera=42 WHERE account='22'");
        assertEquals("NOT_APPLIED",map(service.reconcile(22,e.requestId)).get("state"));assertEquals("42",balance());
        assertEquals(false,map(service.reconcile(22,e.requestId)).get("balanceEqualityIsProof"));
    }
    @Test public void interruptionBeforeNativeWriteHasDurablePreparationAndNoAward()throws Exception{
        ManagedRechargeService.Edit e=edit(5);service.preview(22,e);fault("UPDATE `taiwan_billing`.`cash_cera`",false,false);reject(503,()->service.save(e));originalConnection();
        assertEquals("NOT_APPLIED",map(service.reconcile(22,e.requestId)).get("state"));assertEquals("37",balance());
    }
    @Test public void lostCommitReplyReconcilesAtomicReceiptWithoutReissuingCredit()throws Exception{
        ManagedRechargeService.Edit e=edit(5);service.preview(22,e);fault(null,true,false);reject(503,()->service.save(e));originalConnection();
        assertEquals("PERSISTED",map(service.reconcile(22,e.requestId)).get("state"));assertEquals("42",balance());assertEquals("PERSISTED",map(service.save(e)).get("state"));assertEquals("42",balance());
    }
    @Test public void failedRollbackClosesConnectionWithoutImplicitlyCommitting()throws Exception{
        ManagedRechargeService.Edit e=edit(5);service.preview(22,e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?",false,true);
        com.zaxxer.hikari.HikariConfig config=new com.zaxxer.hikari.HikariConfig();config.setDataSource((DataSource)ReflectionTestUtils.getField(access,"dataSource"));config.setMaximumPoolSize(1);config.setMinimumIdle(0);config.setConnectionTimeout(5000);
        try(com.zaxxer.hikari.HikariDataSource pool=new com.zaxxer.hikari.HikariDataSource(config)){
            ReflectionTestUtils.setField(access,"dataSource",pool);reject(503,()->service.save(e));
        }finally{originalConnection();}
        assertEquals("NOT_APPLIED",map(service.reconcile(22,e.requestId)).get("state"));assertEquals("37",balance());
    }
    @Test public void sameRequestConcurrentSubmissionsCreditOnlyOnce()throws Exception{
        ManagedRechargeService.Edit e=edit(5);service.preview(22,e);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{Callable<String> action=()->{ThreadUtil.setUserId(1L);start.await();return String.valueOf(map(service.save(e)).get("state"));};Future<String> a=pool.submit(action),b=pool.submit(action);start.countDown();assertTrue(Arrays.asList("PREPARED","PERSISTED").contains(a.get(20,TimeUnit.SECONDS)));assertTrue(Arrays.asList("PREPARED","PERSISTED").contains(b.get(20,TimeUnit.SECONDS)));assertEquals("PERSISTED",map(service.reconcile(22,e.requestId)).get("state"));assertEquals("42",balance());}
        finally{pool.shutdownNow();}
    }
    @Test public void distinctConcurrentPreviewsCannotApplyTheSameBalanceVersionTwice()throws Exception{
        ManagedRechargeService.Edit a=edit(5),b=edit(5);service.preview(22,a);service.preview(22,b);service.save(a);reject(409,()->service.save(b));assertEquals("42",balance());
    }
    @Test public void compensationSubtractsOriginalAmountFromCurrentBalanceOnlyOnce()throws Exception{
        ManagedRechargeService.Edit original=edit(5);service.preview(22,original);service.save(original);sql("UPDATE taiwan_billing.cash_cera SET cera=100 WHERE account='22'");
        ManagedRechargeService.Edit comp=edit(-5);comp.operation="compensate";comp.targetRequestId=original.requestId;service.preview(22,comp);assertEquals("PERSISTED",map(service.save(comp)).get("state"));assertEquals("95",balance());
        assertEquals("PERSISTED",map(service.save(comp)).get("state"));assertEquals("95",balance());
        ManagedRechargeService.Edit duplicate=edit(-5);duplicate.operation="compensate";duplicate.targetRequestId=original.requestId;reject(409,()->service.preview(22,duplicate));
        assertEquals(original.requestId,map(service.save(comp)).get("targetRequestId"));
    }
    @Test public void compensationRefusesInsufficientBalanceAndMismatchedAmount()throws Exception{
        ManagedRechargeService.Edit original=edit(5);service.preview(22,original);service.save(original);sql("UPDATE taiwan_billing.cash_cera SET cera=3 WHERE account='22'");
        ManagedRechargeService.Edit comp=edit(-5);comp.operation="compensate";comp.targetRequestId=original.requestId;reject(409,()->service.preview(22,comp));comp.cera=json.valueToTree(-4);reject(409,()->service.preview(22,comp));assertEquals("3",balance());
    }
    @Test public void myisamInterruptedWriteStaysUnknownEvenAtExpectedBalanceUntilExplicitAcknowledgment()throws Exception{
        sql("ALTER TABLE taiwan_billing.cash_cera ENGINE=MyISAM");ManagedRechargeService.Edit e=edit(5);service.preview(22,e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=?",false,false);reject(503,()->service.save(e));originalConnection();assertEquals("42",balance());
        assertEquals("CONFLICT",map(service.reconcile(22,e.requestId)).get("state"));reject(409,()->service.preview(22,edit(5)));
        ManagedRechargeService.Edit keep=edit(1);keep.operation="accept-current";keep.cera=null;keep.targetRequestId=e.requestId;keep.reason="synthetic reviewed current value, original outcome unknown";
        service.preview(22,keep);assertEquals("NO_CHANGE",map(service.save(keep)).get("state"));assertEquals("ACKNOWLEDGED",map(service.reconcile(22,e.requestId)).get("state"));assertEquals("42",balance());
        ManagedRechargeService.Edit next=edit(1);service.preview(22,next);service.save(next);assertEquals("43",balance());
    }
    @Test public void myisamInterruptionBeforeWriteAlsoDoesNotInferNotAppliedFromOldBalance()throws Exception{
        sql("ALTER TABLE taiwan_billing.cash_cera ENGINE=MyISAM");ManagedRechargeService.Edit e=edit(5);service.preview(22,e);fault("UPDATE `taiwan_billing`.`cash_cera`",false,false);reject(503,()->service.save(e));originalConnection();assertEquals("37",balance());assertEquals("CONFLICT",map(service.reconcile(22,e.requestId)).get("state"));
    }
    @Test public void knownBoundaryTriggerIsPreservedAndOverflowIsRejectedBeforeUpdate()throws Exception{
        sql("UPDATE taiwan_billing.cash_cera SET cera=1999999995 WHERE account='22'");ManagedRechargeService.Edit e=edit(5);reject(409,()->service.preview(22,e));assertEquals("1999999995",balance());
        assertEquals("1",scalar("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE EVENT_OBJECT_SCHEMA='taiwan_billing' AND EVENT_OBJECT_TABLE='cash_cera'"));
    }
    @Test public void unknownSchemaTriggerAndActualPermissionChangesRejectWithoutMutatingWallet()throws Exception{
        sql("CREATE USER 'recharge_probe'@'localhost' IDENTIFIED BY 'synthetic-only'");
        try{
            for(String name:Arrays.asList("d_taiwan","taiwan_billing","dnf_service"))sql("GRANT SELECT,INSERT,UPDATE,LOCK TABLES ON `"+name.replace("_","\\_")+"`.* TO 'recharge_probe'@'localhost'");
            ReflectionTestUtils.setField(access,"dataSource",new DriverManagerDataSource(ds.getUrl(),"recharge_probe","synthetic-only"));
            reject(409,()->service.status(22)); // The known trigger exists but is invisible to this role.
            assertEquals("1",scalar("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE EVENT_OBJECT_SCHEMA='taiwan_billing' AND EVENT_OBJECT_TABLE='cash_cera'"));
            for(String name:Arrays.asList("taiwan_billing","dnf_service"))sql("GRANT TRIGGER ON `"+name.replace("_","\\_")+"`.* TO 'recharge_probe'@'localhost'");
            assertEquals("InnoDB",map(service.status(22)).get("engine"));
        }finally{originalConnection();sql("DROP USER 'recharge_probe'@'localhost'");}
        sql("ALTER TABLE taiwan_billing.cash_cera ADD unknown_parameter INT NULL");
        com.zaxxer.hikari.HikariConfig config=new com.zaxxer.hikari.HikariConfig();config.setDataSource(ds);config.setMaximumPoolSize(1);config.setMinimumIdle(0);config.setConnectionTimeout(5000);
        try(com.zaxxer.hikari.HikariDataSource pool=new com.zaxxer.hikari.HikariDataSource(config)){
            ReflectionTestUtils.setField(access,"dataSource",pool);reject(409,()->service.status(22));
            // A connection returned to the pool after validation failure must not
            // retain LOCK TABLES and block another real connection.
            try(Connection c=ds.getConnection();Statement s=c.createStatement()){
                s.execute("SET SESSION lock_wait_timeout=1");try(ResultSet r=s.executeQuery("SELECT cera FROM taiwan_billing.cash_cera WHERE account='22'")){assertTrue(r.next());assertEquals(37,r.getInt(1));}
            }
        }finally{originalConnection();}
        sql("ALTER TABLE taiwan_billing.cash_cera DROP unknown_parameter");
        sql("ALTER TABLE taiwan_billing.cash_cera MODIFY mod_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");reject(409,()->service.status(22));
        sql("ALTER TABLE taiwan_billing.cash_cera MODIFY mod_date DATETIME NOT NULL");
        sql("CREATE TABLE taiwan_billing.unknown_reference(account VARCHAR(30),FOREIGN KEY(account) REFERENCES cash_cera(account)) ENGINE=InnoDB DEFAULT CHARSET=utf8");reject(409,()->service.status(22));sql("DROP TABLE taiwan_billing.unknown_reference");
        sql("DROP TRIGGER taiwan_billing.cash_cera");sql("CREATE TRIGGER taiwan_billing.unverified BEFORE UPDATE ON taiwan_billing.cash_cera FOR EACH ROW SET NEW.cera=0");reject(409,()->service.status(22));sql("DROP TRIGGER taiwan_billing.unverified");
        sql("UPDATE d_taiwan.accounts SET admin=0 WHERE UID=1");reject(403,()->service.status(22));assertEquals("37",balance());
        ThreadUtil.setUserId(99L);reject(403,()->service.status(22));
    }
}
