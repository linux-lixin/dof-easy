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

/** Real SQL on a disposable fixture. Codes, players and PVF metadata are explicitly synthetic. */
public class ManagedCdkContractTest {
    private ManagedPostalContractTest fixture;private DriverManagerDataSource ds;private DofAdminService access;
    private ManagedPostalService postal;private ManagedRechargeService recharge;private ManagedCdkService service;private ManagedOperationJournal journal;
    private final ObjectMapper json=new ObjectMapper();private boolean keyCreated;
    @Before public void setup()throws Exception{
        fixture=new ManagedPostalContractTest();fixture.setup();ds=(DriverManagerDataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"access");postal=(ManagedPostalService)ReflectionTestUtils.getField(fixture,"service");journal=(ManagedOperationJournal)ReflectionTestUtils.getField(postal,"journal");
        assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='taiwan_billing'"));sql("CREATE DATABASE taiwan_billing DEFAULT CHARACTER SET utf8");((List<String>)ReflectionTestUtils.getField(fixture,"created")).add("taiwan_billing");
        String seed=new String(Files.readAllBytes(Paths.get(System.getProperty("dof.recharge.contract.seed"))),StandardCharsets.UTF_8);Matcher wallet=Pattern.compile("CREATE TABLE `cash_cera` \\(.*?\\) ENGINE=InnoDB DEFAULT CHARSET=utf8;",Pattern.DOTALL).matcher(seed);assertTrue(wallet.find());sql(wallet.group().replace("CREATE TABLE `cash_cera`","CREATE TABLE taiwan_billing.cash_cera"));
        sql("INSERT INTO taiwan_billing.cash_cera VALUES('22',37,11,23,'2020-01-01 00:00:00','2019-01-01 00:00:00')");sql("CREATE TRIGGER taiwan_billing.cash_cera BEFORE UPDATE ON taiwan_billing.cash_cera FOR EACH ROW IF (new.cera >= 2000000000 OR new.cera <= 0 ) THEN SET new.cera = 0;END IF");
        assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='recharge_key'"));
        String admin=new String(Files.readAllBytes(Paths.get(System.getProperty("dof.cdk.contract.schema"))),StandardCharsets.UTF_8);Matcher key=Pattern.compile("CREATE TABLE `recharge_key` \\(.*?\\) ENGINE=InnoDB.*?;",Pattern.DOTALL).matcher(admin);assertTrue(key.find());sql(key.group().replace("CREATE TABLE `recharge_key`","CREATE TABLE dnf_service.recharge_key"));keyCreated=true;
        recharge=new ManagedRechargeService();ReflectionTestUtils.setField(recharge,"journal",journal);service=service();ThreadUtil.setUserId(0L);
    }
    private ManagedCdkService service(){ManagedCdkService s=new ManagedCdkService();ReflectionTestUtils.setField(s,"access",access);ReflectionTestUtils.setField(s,"journal",journal);ReflectionTestUtils.setField(s,"postal",postal);ReflectionTestUtils.setField(s,"recharge",recharge);return s;}
    @After public void cleanup()throws Exception{if(fixture!=null){if(access!=null&&ds!=null)ReflectionTestUtils.setField(access,"dataSource",ds);if(keyCreated){ReflectionTestUtils.invokeMethod(fixture,"guard");sql("DROP TABLE dnf_service.recharge_key");}fixture.cleanup();}}
    private void sql(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(text);}}
    private String scalar(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(text)){assertTrue(r.next());return r.getString(1);}}
    private Map<?,?> map(Object x){return (Map<?,?>)x;}
    private void reject(int status,Runnable action){try{action.run();fail("Unsafe CDK request accepted");}catch(DofFailure e){assertEquals(e.getMessage(),status,e.status);}}
    private String code(int type,int face)throws Exception{String code=UUID.randomUUID().toString().toUpperCase(Locale.ROOT);try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO dnf_service.recharge_key(content,type,face,status,create_time) VALUES(?,?,?,0,'2020-01-01 00:00:00')")){p.setString(1,code);p.setInt(2,type);p.setInt(3,face);p.executeUpdate();}return code;}
    private ManagedCdkService.Claim claim(String code,int recipient){ManagedCdkService.Claim e=new ManagedCdkService.Claim();e.requestId=UUID.randomUUID().toString();e.content=code;e.useAccount="synthetic_player";e.charach=json.valueToTree(recipient);captcha(service,e);return e;}
    private void captcha(ManagedCdkService s,ManagedCdkService.Claim e){e.valiId=UUID.randomUUID().toString();e.valicode="SYNTH";s.issueChallenge(e.valiId,e.valicode);}
    private void fault(String prefix,boolean lose)throws Exception{ReflectionTestUtils.invokeMethod(fixture,"fault",prefix,lose);}
    private void reset(){ReflectionTestUtils.setField(access,"dataSource",ds);}
    private String balance()throws Exception{return scalar("SELECT cera FROM taiwan_billing.cash_cera WHERE account='22'");}
    private ManagedCdkService.Control control(String source,String operation){ThreadUtil.setUserId(1L);Map<?,?> row=((List<?>)service.history()).stream().map(this::map).filter(r->source.equals(r.get("requestId"))).findFirst().get();ManagedCdkService.Control e=new ManagedCdkService.Control();e.requestId=UUID.randomUUID().toString();e.revision=(String)row.get("receiptRevision");e.operation=operation;e.reason="synthetic administrator review";return e;}
    @Test public void storedCurrencyAndCodeConsumptionCommitAndReplayWithoutNewCaptcha()throws Exception{
        ManagedCdkService.Claim e=claim(code(0,5),0);assertEquals("PERSISTED",map(service.claim(e)).get("state"));assertEquals("42",balance());e.valicode=null;assertEquals("PERSISTED",map(service.claim(e)).get("state"));assertEquals("PERSISTED",map(service.reconcile(e)).get("state"));assertEquals("42",balance());assertEquals("1:22:synthetic_player",scalar("SELECT CONCAT(status,':',use_uid,':',use_account) FROM dnf_service.recharge_key"));
        String snapshot=scalar("SELECT CONCAT(before_json,after_json) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE namespace='cdk.use'");assertFalse(snapshot.contains(e.content));assertFalse(snapshot.contains("SYNTH"));assertEquals("11:23:2020-01-01 00:00:00",scalar("SELECT CONCAT(cera_cold,':',mod_tran,':',mod_date) FROM taiwan_billing.cash_cera"));e.useAccount="other";reject(409,()->service.claim(e));
    }
    @Test public void equipmentUsesStoredFaceAndChecksRecipientOwnership()throws Exception{
        ReflectionTestUtils.invokeMethod(fixture,"equipment",3);String code=code(1,1001);ManagedCdkService.Claim wrong=claim(code,123);wrong.useAccount="admin";reject(403,()->service.claim(wrong));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
        ManagedCdkService.Claim e=json.readValue("{\"requestId\":\""+UUID.randomUUID()+"\",\"content\":\""+code+"\",\"useAccount\":\"synthetic_player\",\"charach\":123,\"face\":999999}",ManagedCdkService.Claim.class);captcha(service,e);service.claim(e);assertEquals("1001:1:123",scalar("SELECT CONCAT(item_id,':',add_info,':',receive_charac_no) FROM taiwan_cain_2nd.postal"));sql("DELETE FROM taiwan_cain_2nd.postal");service.claim(e);assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void twoServiceInstancesAndDifferentIdsCannotRedeemTheSameCodeTwice()throws Exception{
        String code=code(0,5);ManagedCdkService second=service();ManagedCdkService.Claim a=claim(code,0),b=claim(code,0);captcha(second,b);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{Callable<String> first=()->{ThreadUtil.setUserId(0L);start.await();try{return String.valueOf(map(service.claim(a)).get("state"));}catch(DofFailure e){return Integer.toString(e.status);}};Callable<String> other=()->{ThreadUtil.setUserId(0L);start.await();try{return String.valueOf(map(second.claim(b)).get("state"));}catch(DofFailure e){return Integer.toString(e.status);}};Future<String> x=pool.submit(first),y=pool.submit(other);start.countDown();List<String> outcomes=Arrays.asList(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS));assertTrue(outcomes.contains("PERSISTED"));assertTrue(outcomes.contains("409"));assertEquals("42",balance());assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL WHERE namespace='cdk.use' AND state='PERSISTED'"));}finally{pool.shutdownNow();}
    }
    @Test public void receiptFailureRollsBackCurrencyAndKeyTogetherThenReconciles()throws Exception{
        ManagedCdkService.Claim e=claim(code(0,5),0);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json=",false);reject(503,()->service.claim(e));reset();assertEquals("37",balance());assertEquals("0",scalar("SELECT status FROM dnf_service.recharge_key"));assertEquals("NOT_APPLIED",map(service.reconcile(e)).get("state"));assertEquals("NOT_APPLIED",map(service.claim(e)).get("state"));ManagedCdkService.Claim retry=claim(e.content,0);service.claim(retry);assertEquals("42",balance());
    }
    @Test public void committedResponseLossKeepsOriginalReceiptAfterBalanceConsumption()throws Exception{
        ManagedCdkService.Claim e=claim(code(0,5),0);fault(null,true);reject(503,()->service.claim(e));reset();assertEquals("42",balance());sql("UPDATE taiwan_billing.cash_cera SET cera=35");assertEquals("PERSISTED",map(service.reconcile(e)).get("state"));service.claim(e);assertEquals("35",balance());assertEquals("1",scalar("SELECT status FROM dnf_service.recharge_key"));
    }
    @Test public void myisamCurrencyUnknownBlocksOtherAccountsAndQuarantinesCodeAfterAck()throws Exception{
        sql("ALTER TABLE taiwan_billing.cash_cera ENGINE=MyISAM");ManagedCdkService.Claim e=claim(code(0,5),0);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json=",false);reject(503,()->service.claim(e));reset();assertEquals("42",balance());assertEquals("0",scalar("SELECT status FROM dnf_service.recharge_key"));assertEquals("CONFLICT",map(service.reconcile(e)).get("state"));
        sql("INSERT INTO d_taiwan.accounts VALUES(33,'other_player',NULL)");sql("INSERT INTO taiwan_billing.cash_cera SELECT '33',37,cera_cold,mod_tran,mod_date,reg_date FROM taiwan_billing.cash_cera WHERE account='22'");ManagedCdkService.Claim other=claim(e.content,0);other.useAccount="other_player";reject(409,()->service.claim(other));
        ThreadUtil.setUserId(1L);ManagedRechargeService.Edit credit=new ManagedRechargeService.Edit();credit.uid=json.valueToTree(22);credit.cera=json.valueToTree(1);credit.requestId=UUID.randomUUID().toString();credit.revision=(String)map(recharge.status(22)).get("revision");credit.operation="credit";credit.reason="synthetic cross issuer pending guard";reject(409,()->recharge.preview(22,credit));
        ManagedCdkService.Control ack=control(e.requestId,"ack-unknown");service.controlPreview(e.requestId,ack);assertEquals("NO_CHANGE",map(service.controlSave(e.requestId,ack)).get("state"));assertEquals("ACKNOWLEDGED",map(service.claim(e)).get("state"));ManagedCdkService.Claim again=claim(e.content,0);reject(409,()->service.claim(again));assertEquals("42",balance());assertEquals("0",scalar("SELECT status FROM dnf_service.recharge_key"));
    }
    @Test public void myisamMailConsumptionCannotTurnUnknownIntoASecondReward()throws Exception{
        ReflectionTestUtils.invokeMethod(fixture,"equipment",3);sql("ALTER TABLE taiwan_cain_2nd.postal ENGINE=MyISAM");ManagedCdkService.Claim e=claim(code(1,1001),123);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json=",false);reject(503,()->service.claim(e));reset();assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));sql("DELETE FROM taiwan_cain_2nd.postal");assertEquals("CONFLICT",map(service.reconcile(e)).get("state"));ManagedCdkService.Claim again=claim(e.content,123);reject(409,()->service.claim(again));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void reconciliationDoesNotRequireLiveKeyRoleAccountOrBalanceRows()throws Exception{
        ManagedCdkService.Claim e=claim(code(0,5),0);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json=",false);reject(503,()->service.claim(e));reset();sql("DELETE FROM dnf_service.recharge_key");sql("DELETE FROM taiwan_billing.cash_cera");sql("DELETE FROM taiwan_cain.charac_info");sql("DELETE FROM d_taiwan.accounts WHERE UID=22");assertEquals("NOT_APPLIED",map(service.reconcile(e)).get("state"));
    }
    @Test public void hiddenTriggersUnknownColumnsAndActualPrivilegeChangesRefuseWrites()throws Exception{
        ManagedCdkService.Claim e=claim(code(0,5),0);sql("CREATE USER 'cdk_probe'@'localhost' IDENTIFIED BY 'synthetic-only'");try{
            for(String db:Arrays.asList("d_taiwan","taiwan_billing","dnf_service"))sql("GRANT SELECT,INSERT,UPDATE,LOCK TABLES ON `"+db.replace("_","\\_")+"`.* TO 'cdk_probe'@'localhost'");ReflectionTestUtils.setField(access,"dataSource",new DriverManagerDataSource(ds.getUrl(),"cdk_probe","synthetic-only"));reject(409,()->service.claim(e));
            for(String db:Arrays.asList("taiwan_billing","dnf_service"))sql("GRANT TRIGGER ON `"+db.replace("_","\\_")+"`.* TO 'cdk_probe'@'localhost'");captcha(service,e);assertEquals("PERSISTED",map(service.claim(e)).get("state"));
        }finally{reset();sql("DROP USER 'cdk_probe'@'localhost'");}
        sql("ALTER TABLE dnf_service.recharge_key ADD unknown_parameter INT NULL");ManagedCdkService.Claim bad=claim(code(0,1),0);reject(409,()->service.claim(bad));sql("ALTER TABLE dnf_service.recharge_key DROP unknown_parameter");ThreadUtil.setUserId(1L);sql("UPDATE d_taiwan.accounts SET admin=0 WHERE UID=1");reject(403,()->service.history());assertEquals("42",balance());
    }
    @Test public void captchaIsPurposeScopedOneUseAndNumbersAreStrict()throws Exception{
        ManagedCdkService.Claim e=claim(code(0,5),0);reject(400,()->service().claim(e));e.valicode="wrong";reject(400,()->service.claim(e));e.valicode="SYNTH";reject(400,()->service.claim(e));captcha(service,e);e.charach=json.readTree("1.5");reject(400,()->service.claim(e));e.charach=json.valueToTree(0);service.claim(e);assertEquals("42",balance());
    }
    @Test public void cancelledUnsubmittedPreviewReleasesOnlyTheCodeAndNeverReusesOldId()throws Exception{
        ManagedCdkService.Claim e=claim(code(0,5),0);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET target_key=",false);reject(503,()->service.claim(e));reset();assertEquals("PREVIEWED",map(service.reconcile(e)).get("state"));ManagedCdkService.Claim duplicate=claim(e.content,0);reject(409,()->service.claim(duplicate));
        ManagedCdkService.Control cancel=control(e.requestId,"cancel-preview");service.controlPreview(e.requestId,cancel);service.controlSave(e.requestId,cancel);assertEquals("NOT_APPLIED",map(service.claim(e)).get("state"));assertEquals("37",balance());ManagedCdkService.Claim fresh=claim(e.content,0);service.claim(fresh);assertEquals("42",balance());
    }
    @Test public void equipmentReceiptFailureRollsBackMailAndCodeInTheSameTransaction()throws Exception{
        ReflectionTestUtils.invokeMethod(fixture,"equipment",3);ManagedCdkService.Claim e=claim(code(1,1001),123);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json=",false);reject(503,()->service.claim(e));reset();assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));assertEquals("0",scalar("SELECT status FROM dnf_service.recharge_key"));assertEquals("NOT_APPLIED",map(service.reconcile(e)).get("state"));
    }
    @Test public void confirmedCdkCreditUsesExistingOneTimeCompensationAndKeepsLaterChanges()throws Exception{
        ManagedCdkService.Claim original=claim(code(0,5),0);service.claim(original);sql("UPDATE taiwan_billing.cash_cera SET cera=60");ThreadUtil.setUserId(1L);
        ManagedRechargeService.Edit refund=new ManagedRechargeService.Edit();refund.uid=json.valueToTree(22);refund.cera=json.valueToTree(-5);refund.requestId=UUID.randomUUID().toString();refund.revision=(String)map(recharge.status(22)).get("revision");refund.operation="compensate";refund.targetRequestId=original.requestId;refund.reason="synthetic CDK compensation preserves later changes";recharge.preview(22,refund);assertEquals("PERSISTED",map(recharge.save(refund)).get("state"));assertEquals("55",balance());recharge.save(refund);assertEquals("55",balance());
        refund.requestId=UUID.randomUUID().toString();refund.revision=(String)map(recharge.status(22)).get("revision");reject(409,()->recharge.preview(22,refund));assertEquals("1",scalar("SELECT status FROM dnf_service.recharge_key"));service.claim(original);assertEquals("55",balance());
    }
}
