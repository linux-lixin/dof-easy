package com.aiyi.game.dnfserver.service.dof;

import org.junit.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Atomic generated-ID receipts on seed postal/letter tables, not game delivery proof. */
public class ManagedMailReceiptContractTest {
    private DriverManagerDataSource dataSource;
    private ManagedOperationJournal journal;
    private String uuid;
    private boolean created;

    @Before public void setup()throws Exception {
        String path=System.getProperty("dof.recharge.contract.properties");
        Assume.assumeTrue("requires dedicated disposable management MySQL fixture",path!=null);
        Properties p=new Properties();try(InputStream in=Files.newInputStream(Paths.get(path))){p.load(in);}
        uuid=p.getProperty("uuid");assertNotNull(uuid);
        dataSource=new DriverManagerDataSource(p.getProperty("url"),p.getProperty("username"),p.getProperty("password"));
        guard();assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='taiwan_cain_2nd'"));
        sql("CREATE DATABASE taiwan_cain_2nd DEFAULT CHARACTER SET utf8");created=true;
        String seed=new String(Files.readAllBytes(Paths.get(System.getProperty("dof.recharge.contract.seed"))),StandardCharsets.UTF_8);
        for(String name:Arrays.asList("postal","letter")){
            Matcher m=Pattern.compile("CREATE TABLE `"+name+"` \\(.*?\\) ENGINE=InnoDB DEFAULT CHARSET=utf8;",Pattern.DOTALL).matcher(seed);
            assertTrue("native seed table missing: "+name,m.find());
            sql(m.group().replace("CREATE TABLE `"+name+"`","CREATE TABLE taiwan_cain_2nd.`"+name+"`"));
        }
        sql("DELETE FROM dnf_service.DOF_OPERATION_JOURNAL");
        DofAdminService access=mock(DofAdminService.class);
        when(access.context()).thenReturn(Collections.singletonMap("instanceId","dof-mail-receipt-contract"));
        journal=new ManagedOperationJournal();ReflectionTestUtils.setField(journal,"access",access);
    }
    private void sql(String sql)throws Exception{try(Connection c=dataSource.getConnection();Statement s=c.createStatement()){s.execute(sql);}}
    private String scalar(String sql)throws Exception{try(Connection c=dataSource.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}}
    private void guard()throws Exception {
        assertEquals(uuid,scalar("SELECT @@server_uuid"));
        assertEquals("disposable-recharge-contract",scalar("SELECT purpose FROM dnf_service.DOF_RECHARGE_TEST_MARKER"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_RECHARGE_TEST_MARKER"));
    }
    @After public void cleanup()throws Exception{if(created){guard();sql("DROP DATABASE taiwan_cain_2nd");}}
    private ManagedOperationJournal.Entry prepared(Connection c)throws Exception {
        String id=UUID.randomUUID().toString();
        Map<String,Object> before=Collections.singletonMap("recipient",123);
        Map<String,Object> intent=Collections.singletonMap("text","synthetic contract text");
        ManagedOperationJournal.Entry e=journal.preview(c,id,1,"mail-contract","mail:123","123","send",null,journal.hash(intent),"synthetic fixture",before,intent);
        return journal.prepare(c,e,"123",before,intent); // autocommit: durable before the native INSERT
    }
    private long insert(Connection c,String sql)throws Exception {
        try(PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            assertEquals(1,p.executeUpdate());try(ResultSet r=p.getGeneratedKeys()){assertTrue(r.next());long id=r.getLong(1);assertTrue(id>0);assertFalse(r.next());return id;}
        }
    }
    private Map<String,Object> nativeMail(Connection c)throws Exception {
        long letter=insert(c,"INSERT INTO taiwan_cain_2nd.letter(charac_no,send_charac_name,letter_text,reg_date) VALUES(123,'DNF Manager','synthetic contract text',NOW())");
        long postal=insert(c,"INSERT INTO taiwan_cain_2nd.postal(receive_charac_no,send_charac_name,occ_time,gold,letter_id) VALUES(123,'DNF Manager',NOW(),1,"+letter+")");
        Map<String,Object> out=new TreeMap<>();out.put("postalId",postal);out.put("letterId",letter);out.put("recipient",123);return out;
    }
    @Test public void commitPersistsGeneratedIdsAndConsumedMailDoesNotEraseReceipt()throws Exception {
        ManagedOperationJournal.Entry original;
        Map<String,Object> outcome;
        try(Connection c=dataSource.getConnection()){
            original=prepared(c);c.setAutoCommit(false);outcome=nativeMail(c);
            ManagedOperationJournal.Entry receipt=journal.finishWithGeneratedOutcome(c,original,outcome,1);
            assertEquals("PERSISTED",receipt.state);assertEquals(journal.encode(outcome),receipt.afterJson);
            assertEquals(journal.hash(outcome),receipt.afterHash);assertEquals(original.beforeHash,receipt.beforeHash);
            assertEquals("PREPARED",scalar("SELECT state FROM dnf_service.DOF_OPERATION_JOURNAL"));
            assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
            c.commit();
        }
        assertEquals("1",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal p JOIN taiwan_cain_2nd.letter l ON p.letter_id=l.letter_id WHERE p.receive_charac_no=l.charac_no"));
        // A native consumer may remove a postal row. The durable receipt remains
        // authoritative; disappearance must never be interpreted as permission to resend.
        sql("DELETE FROM taiwan_cain_2nd.postal");sql("DELETE FROM taiwan_cain_2nd.letter");
        try(Connection c=dataSource.getConnection()){
            ManagedOperationJournal.Entry receipt=journal.require(c,"mail-contract",original.requestId);
            assertEquals("PERSISTED",receipt.state);assertEquals(journal.encode(outcome),receipt.afterJson);
            c.setAutoCommit(false);
            try{journal.finishWithGeneratedOutcome(c,original,Collections.singletonMap("postalId",999),1);fail("stale receipt overwritten");}
            catch(SQLException expected){c.rollback();}
        }
        assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
    @Test public void rollbackRemovesBothNativeRowsAndGeneratedReceiptButKeepsDurablePreparation()throws Exception {
        ManagedOperationJournal.Entry original;
        try(Connection c=dataSource.getConnection()){
            original=prepared(c);c.setAutoCommit(false);
            journal.finishWithGeneratedOutcome(c,original,nativeMail(c),1);c.rollback();
        }
        assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
        assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.letter"));
        try(Connection c=dataSource.getConnection()){
            ManagedOperationJournal.Entry receipt=journal.require(c,"mail-contract",original.requestId);
            assertEquals("PREPARED",receipt.state);assertEquals(original.afterJson,receipt.afterJson);assertEquals(original.afterHash,receipt.afterHash);
        }
    }
    @Test public void autoCommitAndNonPreparedStatesCannotCreateAFalseReceipt()throws Exception {
        try(Connection c=dataSource.getConnection()){
            ManagedOperationJournal.Entry e=prepared(c);
            try{journal.finishWithGeneratedOutcome(c,e,Collections.singletonMap("postalId",999),1);fail("autocommit receipt accepted");}
            catch(DofFailure expected){assertEquals(409,expected.status);}
            c.setAutoCommit(false);e.state="PERSISTED";
            try{journal.finishWithGeneratedOutcome(c,e,Collections.singletonMap("postalId",999),1);fail("completed receipt accepted");}
            catch(DofFailure expected){assertEquals(409,expected.status);}finally{c.rollback();}
        }
        assertEquals("PREPARED",scalar("SELECT state FROM dnf_service.DOF_OPERATION_JOURNAL"));
        assertEquals("0",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
    }
}
