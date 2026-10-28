package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.service.extension.ActivityConfigService;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Actual read-only SQL, large dataset and abort cleanup. No original-tool format or game claim. */
public class NativeEquipmentDatasetContractTest {
    private ManagedPostalContractTest fixture;private DataSource ds;private DofAdminService access;private ActivityConfigService service;
    @Before @SuppressWarnings("unchecked") public void setup()throws Exception{
        fixture=new ManagedPostalContractTest();fixture.setup();ds=(DataSource)ReflectionTestUtils.getField(fixture,"ds");access=(DofAdminService)ReflectionTestUtils.getField(fixture,"access");
        assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='dof_native'"));sql("CREATE DATABASE dof_native DEFAULT CHARSET=utf8mb4");((List<String>)ReflectionTestUtils.getField(fixture,"created")).add("dof_native");
        sql("CREATE TABLE dof_native.inheritance(code INT UNSIGNED NOT NULL PRIMARY KEY,restitution VARCHAR(255) NOT NULL,Return_quantity VARCHAR(255) NOT NULL,differ_lv INT UNSIGNED NOT NULL) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4");
        sql("CREATE TABLE dof_native.cross_border(code INT UNSIGNED NOT NULL PRIMARY KEY,restitution VARCHAR(255) NOT NULL,Return_quantity VARCHAR(255) NOT NULL,announcement INT UNSIGNED NOT NULL,codenum INT UNSIGNED NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        sql("CREATE TABLE dof_native.item_recovery(code INT UNSIGNED NOT NULL PRIMARY KEY,restitution VARCHAR(255) NOT NULL,Return_quantity VARCHAR(255) NOT NULL,announcement INT UNSIGNED NOT NULL,codenum INT UNSIGNED NOT NULL,extra_consumption_id VARCHAR(255) NOT NULL,extra_consumption_num VARCHAR(255) NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        sql("INSERT INTO dof_native.cross_border VALUES(0,'old-cost','opaque',9,1)");sql("INSERT INTO dof_native.item_recovery VALUES(1,'3037','1',0,1,'0','500')");
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO dof_native.inheritance VALUES(?,'3037/3340','opaque/01',5)")){for(int i=0;i<1005;i++){p.setInt(1,i);p.addBatch();}p.executeBatch();}
        ManagedOperationJournal journal=new ManagedOperationJournal();ReflectionTestUtils.setField(journal,"access",access);service=new ActivityConfigService();ReflectionTestUtils.setField(service,"journal",journal);ReflectionTestUtils.setField(service,"accountVODao",ReflectionTestUtils.getField(access,"accountVODao"));
    }
    @After public void cleanup()throws Exception{if(access!=null&&ds!=null)ReflectionTestUtils.setField(access,"dataSource",ds);if(fixture!=null)fixture.cleanup();}
    private void sql(String q)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(q);}}
    private String scalar(String q)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(q)){assertTrue(r.next());return r.getString(1);}}
    private byte[] exported(String key)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();service.exportDataset(key,out);return out.toByteArray();}
    private void reject(int status,Checked action)throws Exception{try{action.run();fail();}catch(ActivityConfigService.Failure e){assertEquals(e.getMessage(),status,e.status);}}
    private interface Checked{void run()throws Exception;}
    @Test public void pagesBeyondOneThousandAndCompleteExportPreserveAllRulesAndAudit()throws Exception{
        Map<String,Object> page=service.page("inheritance",11,100);assertEquals(1005L,page.get("total"));List<?> rows=(List<?>)page.get("rows");assertEquals(5,rows.size());assertEquals(1000,((Map<?,?>)((Map<?,?>)rows.get(0)).get("values")).get("code"));
        reject(409,()->service.list("inheritance"));byte[] file=exported("inheritance");Map<String,Object> checked=service.checkDataset("inheritance",new ByteArrayInputStream(file));assertEquals(1005L,checked.get("rowCount"));assertEquals(false,checked.get("writePlanCreated"));assertEquals("1005",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));assertEquals("0",scalar("SELECT COUNT(*) FROM dnf_service.DOF_OPERATION_JOURNAL"));
    }
    @Test public void bothOtherRuleSchemasRoundTripIncludingZeroAndReservedLegacyFields()throws Exception{
        for(String key:Arrays.asList("cross-border","recovery")){Map<String,Object> checked=service.checkDataset(key,new ByteArrayInputStream(exported(key)));assertEquals(1L,checked.get("rowCount"));assertEquals(false,checked.get("nativeWrites"));}assertEquals("9:old-cost:opaque",scalar("SELECT CONCAT(announcement,':',restitution,':',Return_quantity) FROM dof_native.cross_border"));
    }
    @Test public void wrongBindingChangedFileAndUnknownSchemaNeverMasqueradeAsEmptySuccess()throws Exception{
        byte[] file=exported("inheritance");String text=new String(file,StandardCharsets.UTF_8);reject(409,()->service.checkDataset("inheritance",new ByteArrayInputStream(text.replace("dof-recharge-contract","other-instance").getBytes(StandardCharsets.UTF_8))));reject(409,()->service.checkDataset("inheritance",new ByteArrayInputStream(text.replace("opaque/01","opaque/02").getBytes(StandardCharsets.UTF_8))));
        sql("ALTER TABLE dof_native.inheritance ADD unknown_column INT");reject(409,()->exported("inheritance"));assertEquals("1005",scalar("SELECT COUNT(*) FROM dof_native.inheritance"));
    }
    @Test public void abortedStreamReleasesNativeAndAccountLocksAndDoesNotPoisonHikariPool()throws Exception{
        com.zaxxer.hikari.HikariConfig config=new com.zaxxer.hikari.HikariConfig();config.setDataSource(ds);config.setMaximumPoolSize(1);config.setMinimumIdle(0);config.setConnectionTimeout(5000);
        try(com.zaxxer.hikari.HikariDataSource pool=new com.zaxxer.hikari.HikariDataSource(config)){
            ReflectionTestUtils.setField(access,"dataSource",pool);try{service.exportDataset("inheritance",new OutputStream(){int written;public void write(int value)throws IOException{if(++written>300)throw new IOException("synthetic disconnected client");}});fail();}catch(IOException expected){}finally{ReflectionTestUtils.setField(access,"dataSource",ds);}
            ExecutorService executor=Executors.newSingleThreadExecutor();try{assertEquals("1005",executor.submit(()->{sql("UPDATE dof_native.inheritance SET differ_lv=6 WHERE code=0");return scalar("SELECT COUNT(*) FROM dof_native.inheritance");}).get(8,TimeUnit.SECONDS));}finally{executor.shutdownNow();}
            ReflectionTestUtils.setField(access,"dataSource",pool);assertEquals(1005L,service.page("inheritance",1,20).get("total"));
        }finally{ReflectionTestUtils.setField(access,"dataSource",ds);}
    }
    @Test public void invalidPageAndRevokedAdministratorStopReadAndUploadCompletion()throws Exception{
        reject(400,()->service.page("inheritance",0,20));reject(400,()->service.page("inheritance",1,101));reject(409,()->service.page("signin",1,20));byte[] file=exported("inheritance");
        InputStream revoke=new ByteArrayInputStream(file){boolean first=true;@Override public synchronized int read(byte[] b,int off,int len){if(first){first=false;try{sql("UPDATE d_taiwan.accounts SET admin=0 WHERE UID=1");}catch(Exception e){throw new IllegalStateException(e);}}return super.read(b,off,len);}};
        reject(403,()->service.checkDataset("inheritance",revoke));reject(403,()->service.page("inheritance",1,20));
    }
    @Test public void exportUsesOriginalUtf8BytesAndRefusesUnexpectedBinaryColumns()throws Exception{
        sql("UPDATE dof_native.inheritance SET restitution=CONVERT(X'F09F9880' USING utf8mb4) WHERE code=0");assertEquals("F09F9880",scalar("SELECT HEX(restitution) FROM dof_native.inheritance WHERE code=0"));byte[] file=exported("inheritance");assertEquals("\ud83d\ude00",new com.fasterxml.jackson.databind.ObjectMapper().readTree(file).get("rows").get(0).get("restitution").asText());service.checkDataset("inheritance",new ByteArrayInputStream(file));
        sql("ALTER TABLE dof_native.inheritance MODIFY restitution VARBINARY(255) NOT NULL");reject(409,()->exported("inheritance"));
    }
}
