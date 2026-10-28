package com.aiyi.game.dnfserver.service.dof;

import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;

public class DofRecordsContractTest {
    private DofRecordsService records;
    private DofAdminService access;
    private DataSource ds;
    @Before public void setup() throws Exception {
        // This first verifies fixture UUID/marker and absence of all player databases.
        DofMyisamContractTest fixture=new DofMyisamContractTest(); fixture.setup();
        ds=(DataSource)ReflectionTestUtils.getField(fixture,"ds");
        access=(DofAdminService)ReflectionTestUtils.getField(fixture,"service");
        records=new DofRecordsService(); ReflectionTestUtils.setField(records,"access",access);
        try(Connection c=ds.getConnection(); Statement s=c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS dof_native.sign_in_checkin_records");
            s.execute("CREATE TABLE dof_native.sign_in_checkin_records (user_id INT,checkin_date DATE,is_double INT) ENGINE=MyISAM");
            s.execute("INSERT INTO dof_native.sign_in_checkin_records VALUES (1,'2026-10-24',0),(1,'2026-10-24',1),(2,'2026-10-23',NULL),(3,'2026-10-22',0)");
            s.execute("DROP TABLE IF EXISTS dof_native.`transaction`");
            s.execute("CREATE TABLE dof_native.`transaction` (ID INT,UID INT,accname VARCHAR(255),code INT,codenum INT,MING VARCHAR(255),ITime DATETIME) ENGINE=MyISAM DEFAULT CHARSET=utf8");
            s.execute("INSERT INTO dof_native.`transaction` VALUES (1,11,'a%_!b',1,2,'today',CURDATE()),(2,11,'axxxb',1,2,'yesterday',DATE_SUB(CURDATE(),INTERVAL 1 DAY)),(3,12,'a%_!b',1,2,'old',DATE_SUB(CURDATE(),INTERVAL 7 DAY)),(4,11,'future',1,2,NULL,DATE_ADD(CURDATE(),INTERVAL 1 DAY))");
            s.execute("DROP TABLE IF EXISTS dof_native.game_charts_logs");
            s.execute("CREATE TABLE dof_native.game_charts_logs (id INT,charts_day DATETIME,logs VARCHAR(255)) ENGINE=MyISAM DEFAULT CHARSET=utf8");
            s.execute("INSERT INTO dof_native.game_charts_logs VALUES(1,CURDATE(),'test-only-no-reward')");
        }
    }
    @SuppressWarnings("unchecked") private Map<String,Object> page(Long owner,int page,int size) {
        return (Map<String,Object>)records.records("signin-checkins",owner,page,size);
    }
    private void rejects(int status,Runnable task) {try{task.run();fail();}catch(DofFailure e){assertEquals(status,e.status);}}
    @Test public void paginationFilteringTiesAndNullValuesArePreserved() {
        Map<String,Object> first=page(null,1,2),second=page(null,2,2);
        assertEquals(true,first.get("hasMore"));assertEquals(false,second.get("hasMore"));
        List<?> a=(List<?>)first.get("rows"),b=(List<?>)second.get("rows");
        assertEquals("1",((Map<?,?>)a.get(0)).get("is_double")); assertEquals("0",((Map<?,?>)a.get(1)).get("is_double"));
        assertNull(((Map<?,?>)b.get(0)).get("is_double"));assertEquals("3",((Map<?,?>)b.get(1)).get("user_id"));
        assertEquals(2,((List<?>)page(1L,1,50).get("rows")).size());
        assertEquals(0,((List<?>)page(99L,1,50).get("rows")).size());
        assertEquals("live",first.get("consistency")); assertEquals("dof-contract",first.get("instanceId"));
    }
    @Test public void invalidParametersAndUnknownTablesCannotBecomeSql() {
        rejects(404,()->records.records("sign_in_checkin_records;DROP TABLE dof_native.config",null,1,50));
        rejects(400,()->page(-1L,1,50));rejects(400,()->page(null,0,50));rejects(400,()->page(null,1,101));
        ReflectionTestUtils.setField(access,"databaseUuid","00000000-0000-0000-0000-000000000001");
        rejects(409,()->page(null,1,50));rejects(409,()->records.catalog());
    }
    private List<?> rows(String key,Long owner,String period,String field,String text) {
        return (List<?>)((Map<?,?>)records.records(key,owner,1,100,period,field,text)).get("rows");
    }
    @Test public void nativeDateWindowsAndLiteralSearchAreBoundedAndReadOnly() throws Exception {
        assertEquals(1,rows("transactions",null,"today",null,null).size());
        assertEquals(1,rows("transactions",null,"yesterday",null,null).size());
        assertEquals(2,rows("transactions",null,"last7days",null,null).size());
        assertEquals(2,rows("transactions",null,"all","accname","%_!").size());
        assertEquals(1,rows("transactions",11L,"all","accname","%_!").size());
        assertEquals(0,rows("transactions",null,"all","accname","' OR 1=1 --").size());
        assertEquals(1,rows("power-settlements",null,"today","logs","no-reward").size());
        try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM dof_native.`transaction`")){r.next();assertEquals(4,r.getInt(1));}
        List<?> views=(List<?>)((Map<?,?>)records.catalog()).get("views");
        Set<String> identities=new HashSet<>();
        for(Object value:views){Map<?,?> v=(Map<?,?>)value;assertTrue(identities.add(v.get("database")+"."+v.get("table")));}
        assertTrue(views.size()>14);
    }
    @Test public void schemaDiagnosisShowsMissingColumnsWithoutReadingOrRepairingProgress() throws Exception {
        try(Connection c=ds.getConnection();Statement s=c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS dof_native.sign_in_users");
            s.execute("CREATE TABLE dof_native.sign_in_users(user_id INT,current_checkins INT,end_date DATE,private_note VARCHAR(100)) ENGINE=MyISAM");
            s.execute("INSERT INTO dof_native.sign_in_users VALUES(999,12,'2026-10-25','PRIVATE_ROW_CANARY')");
        }
        Map<?,?> diagnosis=(Map<?,?>)records.diagnose("signin-users");
        assertEquals(true,diagnosis.get("tablePresent"));
        assertEquals(Collections.singletonList("last_checkin_date"),((Map<?,?>)diagnosis.get("view")).get("missingColumns"));
        assertFalse(diagnosis.toString().contains("PRIVATE_ROW_CANARY"));assertFalse(diagnosis.toString().contains("private_note"));
        assertNotNull(diagnosis.get("databaseTimezone"));assertEquals(4,((List<?>)diagnosis.get("fields")).size());
        rejects(404,()->records.diagnose("../accounts"));
        try(Connection c=ds.getConnection();Statement s=c.createStatement()) {
            s.execute("ALTER TABLE dof_native.sign_in_users ADD last_checkin_date DATE");
        }
        assertEquals(true,((Map<?,?>)((Map<?,?>)records.diagnose("signin-users")).get("view")).get("available"));
        assertEquals(1,rows("signin-users",999L,"all",null,null).size());
        ReflectionTestUtils.setField(access,"databaseUuid","00000000-0000-0000-0000-000000000001");
        rejects(409,()->records.diagnose("signin-users"));
    }
    @Test public void raidProgressUsesAccountFilterWithoutChangingRewardCounters() throws Exception {
        try(Connection c=ds.getConnection();Statement s=c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS dof_native.raid_anton");
            s.execute("CREATE TABLE dof_native.raid_anton(acc_id INT,charac_no INT,reset_timestamp INT,reward1_count INT,reward2_count INT) ENGINE=MyISAM");
            s.execute("INSERT INTO dof_native.raid_anton VALUES(10,101,123,1,2),(20,202,124,3,4)");
        }
        List<?> found=rows("anton",10L,"all",null,null);
        assertEquals(1,found.size());assertEquals("101",((Map<?,?>)found.get(0)).get("charac_no"));
        assertEquals("1",((Map<?,?>)found.get(0)).get("reward1_count"));
        assertEquals(2,rows("anton",null,"all",null,null).size());
        rejects(400,()->rows("anton",null,"today",null,null));
        try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT SUM(reward1_count),SUM(reward2_count) FROM dof_native.raid_anton")) {
            r.next();assertEquals(4,r.getInt(1));assertEquals(6,r.getInt(2));
        }
    }
    @Test public void dungeonRecordsUseReplacementMapHistoryAndPreserveReadOnlyCounters() throws Exception {
        try(Connection c=ds.getConnection();Statement s=c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS dof_native.map_history");
            s.execute("CREATE TABLE dof_native.map_history(ID INT,uid INT,charactName VARCHAR(255),dungeonId INT,dungeonName VARCHAR(255),difficulty INT,mapCount INT,killMobCount INT,useCubepieceClearCount INT,hell INT,passingTime INT,clearInfo INT,success INT,leaveTime DATETIME,reward_state INT,is_captain INT,is_team INT,c_id INT,msg_text VARCHAR(255)) ENGINE=MyISAM DEFAULT CHARSET=utf8");
            s.execute("INSERT INTO dof_native.map_history VALUES(1,10,'role%_!',100,'fixture-dungeon',3,5,12,8,0,90,1,1,CURDATE(),1,1,4,101,'NOT_IN_PROJECTION'),(2,20,'other',200,'old-dungeon',1,2,3,4,1,80,0,0,DATE_SUB(CURDATE(),INTERVAL 1 DAY),0,0,1,202,'NOT_IN_PROJECTION')");
        }
        List<?> found=rows("dungeon-runs",10L,"today","charactName","%_!");
        assertEquals(1,found.size());Map<?,?> row=(Map<?,?>)found.get(0);
        assertEquals("101",row.get("c_id"));assertEquals("1",row.get("reward_state"));assertFalse(row.containsKey("msg_text"));
        assertEquals(1,rows("dungeon-runs",null,"yesterday","dungeonName","old-dungeon").size());
        assertEquals(0,rows("dungeon-runs",null,"today","charactName","' OR 1=1 --").size());
        assertEquals("map_history",((Map<?,?>)((Map<?,?>)records.diagnose("dungeon-runs")).get("view")).get("table"));
        try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*),SUM(reward_state),SUM(is_team) FROM dof_native.map_history")) {
            r.next();assertEquals(2,r.getInt(1));assertEquals(1,r.getInt(2));assertEquals(5,r.getInt(3));
        }
    }
    @Test public void unsupportedOwnerDateSearchAndQueryInjectionAreRejected() {
        rejects(400,()->rows("power-settlements",1L,"all",null,null));
        rejects(400,()->rows("gift-history",null,"today",null,null));
        rejects(400,()->rows("transactions",null,"today; DROP TABLE dof_native.transaction",null,null));
        rejects(400,()->rows("transactions",null,"all","accname` OR 1=1 --","x"));
        rejects(400,()->rows("transactions",null,"all","accname",null));
        rejects(400,()->rows("transactions",null,"all",null,"x"));
        rejects(400,()->rows("transactions",null,"all","accname",String.join("",Collections.nCopies(129,"x"))));
        rejects(400,()->rows("transactions",null,"all","accname","a\0b"));
    }
    @Test public void missingNativeColumnsAreReportedWithoutInitializingThem() throws Exception {
        try(Connection c=ds.getConnection(); Statement s=c.createStatement()){s.execute("ALTER TABLE dof_native.sign_in_checkin_records DROP is_double");}
        rejects(409,()->page(null,1,50));
        try(Connection c=ds.getConnection(); Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='sign_in_checkin_records' AND COLUMN_NAME='is_double'")) {
            r.next();assertEquals(0,r.getInt(1));
        }
    }
    @Test public void catalogReportsMissingFieldsAndOtherViewsRemainUsableWithoutUiColumns() throws Exception {
        try(Connection c=ds.getConnection();Statement s=c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS dof_native.sign_in_users");
            s.execute("CREATE TABLE dof_native.sign_in_users(user_id INT,current_checkins INT,end_date DATE) ENGINE=MyISAM");
            s.execute("ALTER TABLE dof_native.config DROP Hotkeys");
        }
        Map<?,?> catalog=(Map<?,?>)records.catalog();
        Map<?,?> signin=(Map<?,?>)((List<?>)catalog.get("views")).get(0);
        assertEquals(false,signin.get("available"));
        assertEquals(Collections.singletonList("last_checkin_date"),signin.get("missingColumns"));
        rejects(409,()->records.records("signin-users",null,1,50));
        assertEquals(1,rows("transactions",null,"today",null,null).size());
        rejects(409,()->access.ui());
        try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='sign_in_users' AND COLUMN_NAME='last_checkin_date'")) {
            r.next();assertEquals(0,r.getInt(1));
        }
    }
}
