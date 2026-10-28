package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import static org.junit.Assert.*;

/** Same administrative writer, distinct native coupon wallet. No score-shop semantics inferred. */
public class ManagedPointsContractTest {
    private ManagedCdkContractTest fixture;private DriverManagerDataSource ds;private DofAdminService access;
    private ManagedRechargeService service,cera;private final ObjectMapper json=new ObjectMapper();
    @Before public void setup()throws Exception{
        fixture=new ManagedCdkContractTest();fixture.setup();ds=(DriverManagerDataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"access");cera=(ManagedRechargeService)ReflectionTestUtils.getField(fixture,"recharge");service=cera.points();ThreadUtil.setUserId(1L);
        String seed=new String(Files.readAllBytes(Paths.get(System.getProperty("dof.recharge.contract.seed"))),StandardCharsets.UTF_8);Matcher wallet=Pattern.compile("CREATE TABLE `cash_cera_point` \\(.*?\\) ENGINE=InnoDB DEFAULT CHARSET=utf8;",Pattern.DOTALL).matcher(seed);assertTrue(wallet.find());sql(wallet.group().replace("CREATE TABLE `cash_cera_point`","CREATE TABLE taiwan_billing.cash_cera_point"));
        sql("INSERT INTO taiwan_billing.cash_cera_point VALUES('22',17,'2019-01-01 00:00:00','2020-01-01 00:00:00')");sql("CREATE TRIGGER taiwan_billing.cash_cera_point BEFORE UPDATE ON taiwan_billing.cash_cera_point FOR EACH ROW IF (new.cera_point >= 2000000000 OR new.cera_point <= 0 ) THEN SET new.cera_point = 0;END IF");
    }
    @After public void cleanup()throws Exception{if(fixture!=null)fixture.cleanup();}
    private Map<?,?> map(Object x){return (Map<?,?>)x;}
    private void sql(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(text);}}
    private String scalar(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(text)){assertTrue(r.next());return r.getString(1);}}
    private void reject(int status,Runnable call){try{call.run();fail("unsafe coupon operation accepted");}catch(DofFailure e){assertEquals(e.getMessage(),status,e.status);}}
    private void fault(String prefix,boolean lose){ReflectionTestUtils.invokeMethod(ReflectionTestUtils.getField(fixture,"fixture"),"fault",prefix,lose);}
    private void reset(){ReflectionTestUtils.setField(access,"dataSource",ds);ThreadUtil.setUserId(1L);}
    private String balance()throws Exception{return scalar("SELECT cera_point FROM taiwan_billing.cash_cera_point WHERE account='22'");}
    private ManagedRechargeService.Edit edit(long amount){ManagedRechargeService.Edit e=new ManagedRechargeService.Edit();e.uid=json.valueToTree(22);e.cera=json.valueToTree(amount);e.operation="credit";e.requestId=UUID.randomUUID().toString();e.revision=(String)map(service.status(22)).get("revision");e.reason="synthetic coupon contract";return e;}
    private ManagedRechargeService.Edit credit(){ManagedRechargeService.Edit e=edit(5);service.preview(22,e);assertEquals("PERSISTED",map(service.save(e)).get("state"));return e;}
    @Test public void creditReplayUsesSeparateWalletNamespaceAndPreservesNativeMarkers()throws Exception{
        ManagedRechargeService.Edit e=credit();assertEquals("22",balance());service.save(e);assertEquals("22",balance());assertEquals("37",scalar("SELECT cera FROM taiwan_billing.cash_cera WHERE account='22'"));assertEquals("2020-01-01 00:00:00:2019-01-01 00:00:00",scalar("SELECT CONCAT(mod_date,':',reg_date) FROM taiwan_billing.cash_cera_point"));Map<?,?> status=map(service.status(22));assertEquals("cera_point",status.get("currency"));assertEquals(22L,status.get("amount"));assertFalse(status.containsKey("cera"));assertEquals("recharge.points.22",scalar("SELECT namespace FROM dnf_service.DOF_OPERATION_JOURNAL WHERE request_id='"+e.requestId+"'"));assertTrue(((List<?>)cera.history(22)).isEmpty());
    }
    @Test public void atomicFailureAndCommitReplyLossDoNotReplayCredit()throws Exception{
        ManagedRechargeService.Edit e=edit(5);service.preview(22,e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=",false);reject(503,()->service.save(e));reset();assertEquals("17",balance());assertEquals("NOT_APPLIED",map(service.reconcile(22,e.requestId)).get("state"));service.save(e);assertEquals("17",balance());
        ManagedRechargeService.Edit next=edit(5);service.preview(22,next);fault(null,true);reject(503,()->service.save(next));reset();assertEquals("PERSISTED",map(service.reconcile(22,next.requestId)).get("state"));sql("UPDATE taiwan_billing.cash_cera_point SET cera_point=19");service.save(next);assertEquals("19",balance());
    }
    @Test public void oneTimeCompensationPreservesLaterChangesAndCannotCrossCurrencies()throws Exception{
        ManagedRechargeService.Edit e=credit();sql("UPDATE taiwan_billing.cash_cera_point SET cera_point=40");ManagedRechargeService.Edit refund=edit(-5);refund.operation="compensate";refund.targetRequestId=e.requestId;service.preview(22,refund);service.save(refund);service.save(refund);assertEquals("35",balance());refund.requestId=UUID.randomUUID().toString();refund.revision=(String)map(service.status(22)).get("revision");reject(409,()->service.preview(22,refund));
        refund.revision=(String)map(cera.status(22)).get("revision");reject(409,()->cera.preview(22,refund));assertEquals("37",scalar("SELECT cera FROM taiwan_billing.cash_cera WHERE account='22'"));
    }
    @Test public void myisamUnknownBlocksCreditAndAcknowledgmentDoesNotRevokeOrReissue()throws Exception{
        sql("ALTER TABLE taiwan_billing.cash_cera_point ENGINE=MyISAM");ManagedRechargeService.Edit e=edit(5);service.preview(22,e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET state=",false);reject(503,()->service.save(e));reset();assertEquals("22",balance());assertEquals("CONFLICT",map(service.reconcile(22,e.requestId)).get("state"));ManagedRechargeService.Edit blocked=edit(1);reject(409,()->service.preview(22,blocked));
        ManagedRechargeService.Edit ack=edit(1);ack.operation="accept-current";ack.cera=null;ack.targetRequestId=e.requestId;service.preview(22,ack);assertEquals("NO_CHANGE",map(service.save(ack)).get("state"));assertEquals("22",balance());assertEquals("ACKNOWLEDGED",map(service.save(e)).get("state"));
    }
    @Test public void staleVersionsBoundaryAndMissingRowsNeverInitializeOrOverwrite()throws Exception{
        ManagedRechargeService.Edit e=edit(1);sql("UPDATE taiwan_billing.cash_cera_point SET cera_point=18");reject(409,()->service.preview(22,e));sql("UPDATE taiwan_billing.cash_cera_point SET cera_point=1999999999");ManagedRechargeService.Edit max=edit(1);reject(409,()->service.preview(22,max));sql("DELETE FROM taiwan_billing.cash_cera_point");reject(409,()->service.status(22));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_billing.cash_cera_point"));
    }
    @Test public void unknownColumnsAndTriggerChangesStopWrites()throws Exception{
        sql("ALTER TABLE taiwan_billing.cash_cera_point ADD unknown_parameter INT NULL");reject(409,()->service.status(22));sql("ALTER TABLE taiwan_billing.cash_cera_point DROP unknown_parameter");sql("DROP TRIGGER taiwan_billing.cash_cera_point");sql("CREATE TRIGGER taiwan_billing.cash_cera_point BEFORE UPDATE ON taiwan_billing.cash_cera_point FOR EACH ROW SET new.cera_point=1");reject(409,()->service.status(22));assertEquals("17",balance());
    }
    @Test public void simultaneousServiceInstancesCannotOverwriteOneAnother()throws Exception{
        ManagedRechargeService other=cera.points();ManagedRechargeService.Edit a=edit(5),b=edit(7);service.preview(22,a);other.preview(22,b);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{Future<String> x=pool.submit(()->{ThreadUtil.setUserId(1L);start.await();try{return String.valueOf(map(service.save(a)).get("state"));}catch(DofFailure e){return Integer.toString(e.status);}});Future<String> y=pool.submit(()->{ThreadUtil.setUserId(1L);start.await();try{return String.valueOf(map(other.save(b)).get("state"));}catch(DofFailure e){return Integer.toString(e.status);}});start.countDown();List<String> results=Arrays.asList(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS));assertTrue(results.contains("PERSISTED"));assertTrue(results.contains("409")||results.contains("NOT_APPLIED"));assertTrue(Arrays.asList("22","24").contains(balance()));assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE namespace='recharge.points.22' AND state='PERSISTED'"));}finally{pool.shutdownNow();}
    }
    @Test public void zeroBalanceRoundTripPreservesAllDatesAndNeverCreditsCera()throws Exception{
        sql("UPDATE taiwan_billing.cash_cera_point SET cera_point=0");ManagedRechargeService.Edit e=edit(1);service.preview(22,e);service.save(e);assertEquals("1",balance());ManagedRechargeService.Edit refund=edit(-1);refund.operation="compensate";refund.targetRequestId=e.requestId;service.preview(22,refund);service.save(refund);assertEquals("0",balance());assertEquals("37",scalar("SELECT cera FROM taiwan_billing.cash_cera WHERE account='22'"));assertEquals("2020-01-01 00:00:00:2019-01-01 00:00:00",scalar("SELECT CONCAT(mod_date,':',reg_date) FROM taiwan_billing.cash_cera_point"));
    }

}
