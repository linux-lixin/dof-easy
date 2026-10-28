package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.entity.common.AttachType;
import com.aiyi.game.dnfserver.entity.equipment.Equipment;
import com.aiyi.game.dnfserver.entity.equipment.EquipmentType;
import com.aiyi.game.dnfserver.entity.stackable.Stackable;
import com.aiyi.game.dnfserver.pvf.*;
import com.fasterxml.jackson.databind.*;
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

/** Real service/SQL on disposable native seed tables. Synthetic PVF item metadata is explicit. */
public class ManagedPostalContractTest {
    private DriverManagerDataSource ds;private DofAdminService access;private ManagedPostalService service;private PvfManager pvf;
    private String uuid;private Path pvfFile;private final List<String> created=new ArrayList<>();private final ObjectMapper json=new ObjectMapper();
    @Before public void setup()throws Exception{
        String path=System.getProperty("dof.recharge.contract.properties");Assume.assumeTrue("requires disposable mail contract database",path!=null);
        Properties p=new Properties();try(InputStream in=Files.newInputStream(Paths.get(path))){p.load(in);}
        uuid=p.getProperty("uuid");assertNotNull(uuid);ds=new DriverManagerDataSource(p.getProperty("url"),p.getProperty("username"),p.getProperty("password"));guard();
        assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('d_taiwan','taiwan_cain','taiwan_cain_2nd')"));
        for(String name:Arrays.asList("d_taiwan","taiwan_cain","taiwan_cain_2nd")){sql("CREATE DATABASE "+name+" DEFAULT CHARACTER SET utf8");created.add(name);}
        sql("CREATE TABLE d_taiwan.accounts(UID INT PRIMARY KEY,accountname VARCHAR(255) NOT NULL,admin INT) ENGINE=InnoDB");sql("INSERT INTO d_taiwan.accounts VALUES(1,'admin',7),(22,'synthetic_player',NULL)");
        sql("CREATE TABLE taiwan_cain.charac_info(charac_no INT PRIMARY KEY,m_id INT,charac_name VARCHAR(20),delete_flag TINYINT,create_time DATETIME,untouched VARBINARY(8)) ENGINE=MyISAM");
        sql("INSERT INTO taiwan_cain.charac_info VALUES(123,22,'测试角色',0,'2020-01-01 00:00:00',X'00FF')");
        String seed=new String(Files.readAllBytes(Paths.get(System.getProperty("dof.recharge.contract.seed"))),StandardCharsets.UTF_8);
        for(String name:Arrays.asList("postal","letter")){
            Matcher m=Pattern.compile("CREATE TABLE `"+name+"` \\(.*?\\) ENGINE=InnoDB DEFAULT CHARSET=utf8;",Pattern.DOTALL).matcher(seed);assertTrue(m.find());
            sql(m.group().replace("CREATE TABLE `"+name+"`","CREATE TABLE taiwan_cain_2nd.`"+name+"`"));
        }
        sql("DELETE FROM dnf_service.DOF_OPERATION_JOURNAL");
        access=new DofAdminService();ReflectionTestUtils.setField(access,"dataSource",ds);ReflectionTestUtils.setField(access,"enabled",true);ReflectionTestUtils.setField(access,"environment","isolated");
        ReflectionTestUtils.setField(access,"instanceId","dof-recharge-contract");ReflectionTestUtils.setField(access,"databaseUuid",uuid);ReflectionTestUtils.setField(access,"adapterVersion","dof-native-1");
        AccountVODao accounts=mock(AccountVODao.class);AccountVO admin=new AccountVO();admin.setUid(1L);admin.setAdmin(true);when(accounts.get(1L)).thenReturn(admin);ReflectionTestUtils.setField(access,"accountVODao",accounts);
        ManagedOperationJournal journal=new ManagedOperationJournal();ReflectionTestUtils.setField(journal,"access",access);
        service=new ManagedPostalService();ReflectionTestUtils.setField(service,"journal",journal);pvf=mock(PvfManager.class);ReflectionTestUtils.setField(service,"pvf",pvf);
        pvfFile=Files.createTempFile("synthetic-mail-pvf-",".bin");Files.write(pvfFile,"not a native PVF; contract fingerprint only".getBytes(StandardCharsets.UTF_8));when(pvf.sourceFile()).thenReturn(pvfFile.toFile());ThreadUtil.setUserId(1L);
    }
    private void guard()throws Exception{assertEquals(uuid,scalar("SELECT @@server_uuid"));assertEquals("disposable-recharge-contract",scalar("SELECT purpose FROM dnf_service.DOF_RECHARGE_TEST_MARKER"));assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_RECHARGE_TEST_MARKER"));}
    private String scalar(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(text)){assertTrue(r.next());return r.getString(1);}}
    private void sql(String text)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(text);}}
    @After public void cleanup()throws Exception{try{if(!created.isEmpty()){guard();for(String name:created)sql("DROP DATABASE "+name);}}finally{if(pvfFile!=null)Files.deleteIfExists(pvfFile);ThreadUtil.setUserId(1L);}}
    private Map<?,?> map(Object x){return (Map<?,?>)x;}
    private ManagedPostalService.Edit edit(String mail)throws Exception{
        ManagedPostalService.Edit e=new ManagedPostalService.Edit();e.receiveCharacNo=json.valueToTree(123);e.mail=json.readTree(mail);e.requestId=UUID.randomUUID().toString();e.operation="send";e.reason="synthetic isolated mail contract";e.revision=(String)map(service.status(123)).get("revision");return e;
    }
    private void reject(int status,Runnable action){try{action.run();fail("Unsafe mail accepted");}catch(DofFailure e){assertEquals(e.getMessage(),status,e.status);}}
    private void fault(String prefix,boolean loseCommit)throws Exception{
        DataSource broken=mock(DataSource.class);AtomicBoolean once=new AtomicBoolean(true);
        when(broken.getConnection()).thenAnswer(call->{Connection c=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,m,args)->{
            if(m.getName().equals("prepareStatement")&&prefix!=null&&args[0].toString().startsWith(prefix)&&once.compareAndSet(true,false))throw new SQLException("synthetic mail receipt interruption");
            try{Object out=m.invoke(c,args);if(m.getName().equals("commit")&&loseCommit&&once.compareAndSet(true,false))throw new SQLException("synthetic lost commit reply");return out;}catch(InvocationTargetException e){throw e.getCause();}
        });});ReflectionTestUtils.setField(access,"dataSource",broken);
    }
    private void resetConnection(){ReflectionTestUtils.setField(access,"dataSource",ds);}
    private void equipment(int rarity)throws Exception{
        Equipment item=new Equipment();item.setId(1001);item.setName("synthetic equipment");item.setRarity(rarity);item.setStackLimit(1);item.setAttachType(AttachType.sealing);item.setEquipmentType(EquipmentType.weapon);
        PvfManager.VerifiedMailItem checked=new PvfManager.VerifiedMailItem();checked.item=item;checked.kind="equipment";checked.sha256=ResourceIndex.hash(pvfFile.toFile());when(pvf.verifiedMailItem(1001)).thenReturn(checked);
    }
    @Test public void chineseLetterAndGoldCommitAndReplaySurvivesNativeConsumption()throws Exception{
        ManagedPostalService.Edit e=edit("{\"gold\":5,\"message\":\"测试正文\\n第二行\"}");reject(404,()->service.save(e));service.preview(123,e);
        assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));Map<?,?> receipt=map(service.save(e));assertEquals("PERSISTED",receipt.get("state"));assertEquals(false,receipt.get("actualReceiptVerified"));
        assertEquals("5:123:123:测试正文\n第二行",scalar("SELECT CONCAT(gold,':',receive_charac_no,':',charac_no,':',letter_text) FROM taiwan_cain_2nd.postal JOIN taiwan_cain_2nd.letter USING(letter_id)"));
        sql("DELETE FROM taiwan_cain_2nd.postal");sql("DELETE FROM taiwan_cain_2nd.letter");assertEquals("PERSISTED",map(service.save(e)).get("state"));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
        assertEquals("00FF",scalar("SELECT HEX(untouched) FROM taiwan_cain.charac_info"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)e.mail).put("gold",6);reject(409,()->service.save(e));
    }
    @Test public void goldOnlyAndTextOnlyDoNotFabricateAttachments()throws Exception{
        ManagedPostalService.Edit gold=edit("{\"gold\":1}");service.preview(123,gold);service.save(gold);assertEquals("0:0:0:1",scalar("SELECT CONCAT(item_id,':',add_info,':',letter_id,':',gold) FROM taiwan_cain_2nd.postal"));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.letter"));
        ManagedPostalService.Edit text=edit("{\"message\":\"synthetic text only\"}");service.preview(123,text);service.save(text);assertEquals("2",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.letter"));verifyZeroInteractions(pvf);
    }
    @Test public void receiptFailureRollsBackBothRowsAndReconcileDoesNotResend()throws Exception{
        ManagedPostalService.Edit e=edit("{\"gold\":1,\"message\":\"synthetic\"}");service.preview(123,e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json=",false);reject(503,()->service.save(e));resetConnection();
        assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.letter"));assertEquals("NOT_APPLIED",map(service.reconcile(123,e.requestId)).get("state"));assertEquals("NOT_APPLIED",map(service.save(e)).get("state"));
    }
    @Test public void lostCommitResponseReturnsOriginalGeneratedIdentifiersOnReconcile()throws Exception{
        ManagedPostalService.Edit e=edit("{\"gold\":1}");service.preview(123,e);fault(null,true);reject(503,()->service.save(e));resetConnection();
        Map<?,?> r=map(service.reconcile(123,e.requestId));assertEquals("PERSISTED",r.get("state"));assertTrue(((Number)map(r.get("outcome")).get("postalId")).longValue()>0);service.save(e);assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void myisamUnknownOutcomeRequiresAcknowledgmentAndNeverInfersFromMailAbsence()throws Exception{
        sql("ALTER TABLE taiwan_cain_2nd.postal ENGINE=MyISAM");ManagedPostalService.Edit e=edit("{\"gold\":1}");service.preview(123,e);fault("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json=",false);reject(503,()->service.save(e));resetConnection();assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
        sql("DELETE FROM taiwan_cain_2nd.postal");assertEquals("CONFLICT",map(service.reconcile(123,e.requestId)).get("state"));ManagedPostalService.Edit next=edit("{\"gold\":1}");reject(409,()->service.preview(123,next));
        ManagedPostalService.Edit ack=edit("{\"gold\":1}");ack.mail=null;ack.operation="accept-current";ack.targetRequestId=e.requestId;service.preview(123,ack);assertEquals("NO_CHANGE",map(service.save(ack)).get("state"));assertEquals("ACKNOWLEDGED",map(service.save(e)).get("state"));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void staleIdentityGenerationAndWrongDatabaseRejectBeforeWrite()throws Exception{
        ManagedPostalService.Edit a=edit("{\"gold\":1}"),b=edit("{\"gold\":2}");service.preview(123,a);service.preview(123,b);service.save(a);reject(409,()->service.save(b));
        ManagedPostalService.Edit renamed=edit("{\"gold\":1}");service.preview(123,renamed);sql("UPDATE taiwan_cain.charac_info SET charac_name='replacement'");reject(409,()->service.save(renamed));
        sql("UPDATE taiwan_cain.charac_info SET delete_flag=1");reject(409,()->service.status(123));ReflectionTestUtils.setField(access,"databaseUuid",UUID.randomUUID().toString());reject(409,()->service.history(123));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void equipmentIsValidatedAgainstServerMetadataAndFrozenPvf()throws Exception{
        equipment(5);ManagedPostalService.Edit epic=edit("{\"itemId\":1001,\"sealFlag\":true}");reject(400,()->service.preview(123,epic));
        equipment(3);Equipment special=(Equipment)pvf.verifiedMailItem(1001).item;
        for(EquipmentType type:Arrays.asList(EquipmentType.hatAvatar,EquipmentType.creature,EquipmentType.aartifactRed)){
            special.setEquipmentType(type);ManagedPostalService.Edit unsupported=edit("{\"itemId\":1001}");reject(409,()->service.preview(123,unsupported));
        }
        equipment(3);ManagedPostalService.Edit e=edit("{\"itemId\":1001,\"quantity\":1,\"upgrade\":12,\"seperateUpgrade\":8,\"amplifyOption\":3,\"amplifyValue\":55,\"sealFlag\":true}");service.preview(123,e);service.save(e);
        assertEquals("1001:1:12:8:3:55:1",scalar("SELECT CONCAT(item_id,':',add_info,':',`upgrade`,':',seperate_upgrade,':',amplify_option,':',amplify_value,':',seal_flag) FROM taiwan_cain_2nd.postal"));
        ManagedPostalService.Edit changed=edit("{\"itemId\":1001}");service.preview(123,changed);Files.write(pvfFile,"changed synthetic fingerprint".getBytes(StandardCharsets.UTF_8));reject(409,()->service.save(changed));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void stackLimitsUnknownFieldsFractionsAndEmptyLettersCannotBypassValidation()throws Exception{
        Stackable item=new Stackable();item.setId(2001);item.setName("synthetic stackable");item.setStackLimit(10);PvfManager.VerifiedMailItem checked=new PvfManager.VerifiedMailItem();checked.item=item;checked.kind="stackable";checked.sha256=ResourceIndex.hash(pvfFile.toFile());when(pvf.verifiedMailItem(2001)).thenReturn(checked);
        for(String body:Arrays.asList("{}","{\"gold\":1.5}","{\"gold\":2147483648}","{\"gold\":1,\"sender\":\"other\"}","{\"itemId\":2001,\"quantity\":11}","{\"itemId\":2001,\"upgrade\":1}","{\"message\":\"\\ud83d\\ude00\"}")){
            ManagedPostalService.Edit e=edit(body);reject(400,()->service.preview(123,e));
        }
        ManagedPostalService.Edit good=edit("{\"itemId\":2001,\"quantity\":10}");service.preview(123,good);service.save(good);assertEquals("2001:10",scalar("SELECT CONCAT(item_id,':',add_info) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void unknownSchemaAndInsertTriggersRefuseWithoutLeakingPooledLocks()throws Exception{
        sql("ALTER TABLE taiwan_cain_2nd.postal ADD unknown_parameter INT NULL");com.zaxxer.hikari.HikariConfig cfg=new com.zaxxer.hikari.HikariConfig();cfg.setDataSource(ds);cfg.setMaximumPoolSize(1);cfg.setMinimumIdle(0);cfg.setConnectionTimeout(5000);
        try(com.zaxxer.hikari.HikariDataSource pool=new com.zaxxer.hikari.HikariDataSource(cfg)){
            ReflectionTestUtils.setField(access,"dataSource",pool);reject(409,()->service.status(123));
            try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute("SET SESSION lock_wait_timeout=1");try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM taiwan_cain_2nd.postal")){assertTrue(r.next());assertEquals(0,r.getInt(1));}}
        }finally{resetConnection();}
        sql("ALTER TABLE taiwan_cain_2nd.postal DROP unknown_parameter");sql("CREATE TRIGGER taiwan_cain_2nd.unknown_mail BEFORE INSERT ON taiwan_cain_2nd.postal FOR EACH ROW SET NEW.gold=0");reject(409,()->service.status(123));sql("DROP TRIGGER taiwan_cain_2nd.unknown_mail");
        sql("UPDATE d_taiwan.accounts SET admin=0 WHERE UID=1");reject(403,()->service.status(123));
    }
    @Test public void concurrentSameRequestIsIssuedOnlyOnce()throws Exception{
        ManagedPostalService.Edit e=edit("{\"gold\":1}");service.preview(123,e);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{Callable<String> send=()->{ThreadUtil.setUserId(1L);start.await();return String.valueOf(map(service.save(e)).get("state"));};Future<String>a=pool.submit(send),b=pool.submit(send);start.countDown();assertTrue(Arrays.asList("PREPARED","PERSISTED").contains(a.get(20,TimeUnit.SECONDS)));assertTrue(Arrays.asList("PREPARED","PERSISTED").contains(b.get(20,TimeUnit.SECONDS)));assertEquals("PERSISTED",map(service.reconcile(123,e.requestId)).get("state"));assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));}finally{pool.shutdownNow();}
    }
    @Test public void pvfChangeAfterNativeInsertRollsBackBeforeReceiptCommit()throws Exception{
        equipment(3);ManagedPostalService.Edit e=edit("{\"itemId\":1001,\"message\":\"synthetic\"}");service.preview(123,e);
        DataSource broken=mock(DataSource.class);when(broken.getConnection()).thenAnswer(call->{Connection c=ds.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,m,args)->{
            try{
                Object result=m.invoke(c,args);
                if(m.getName().equals("prepareStatement")&&args[0].toString().startsWith("INSERT INTO `taiwan_cain_2nd`.`postal`")){
                    PreparedStatement real=(PreparedStatement)result;
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,method,values)->{
                        try{Object out=method.invoke(real,values);if(method.getName().equals("executeUpdate"))Files.write(pvfFile,"changed during native insert".getBytes(StandardCharsets.UTF_8));return out;}
                        catch(InvocationTargetException ex){throw ex.getCause();}
                    });
                }return result;
            }catch(InvocationTargetException ex){throw ex.getCause();}
        });});ReflectionTestUtils.setField(access,"dataSource",broken);reject(503,()->service.save(e));resetConnection();
        assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.letter"));assertEquals("NOT_APPLIED",map(service.reconcile(123,e.requestId)).get("state"));
    }
    @Test public void failedRollbackDiscardsActualPooledConnectionInsteadOfCommittingMail()throws Exception{
        ManagedPostalService.Edit e=edit("{\"gold\":1,\"message\":\"synthetic\"}");service.preview(123,e);
        com.zaxxer.hikari.HikariConfig cfg=new com.zaxxer.hikari.HikariConfig();cfg.setDataSource(ds);cfg.setMaximumPoolSize(1);cfg.setMinimumIdle(0);cfg.setConnectionTimeout(5000);
        try(com.zaxxer.hikari.HikariDataSource pool=new com.zaxxer.hikari.HikariDataSource(cfg)){
            DataSource broken=mock(DataSource.class);when(broken.getConnection()).thenAnswer(call->{Connection c=pool.getConnection();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,m,args)->{
                if(m.getName().equals("rollback"))throw new SQLException("synthetic rollback unavailable");
                if(m.getName().equals("prepareStatement")&&args[0].toString().startsWith("UPDATE `dnf_service`.`DOF_OPERATION_JOURNAL` SET after_json="))throw new SQLException("synthetic receipt failure");
                try{return m.invoke(c,args);}catch(InvocationTargetException ex){throw ex.getCause();}
            });});ReflectionTestUtils.setField(access,"dataSource",broken);reject(503,()->service.save(e));
        }finally{resetConnection();}
        assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.letter"));assertEquals("NOT_APPLIED",map(service.reconcile(123,e.requestId)).get("state"));
    }
}
