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
            s.execute("DROP TABLE IF EXISTS qk.sign_in_checkin_records");
            s.execute("CREATE TABLE qk.sign_in_checkin_records (user_id INT,checkin_date DATE,is_double INT) ENGINE=MyISAM");
            s.execute("INSERT INTO qk.sign_in_checkin_records VALUES (1,'2026-10-24',0),(1,'2026-10-24',1),(2,'2026-10-23',NULL),(3,'2026-10-22',0)");
            s.execute("DROP TABLE IF EXISTS qk.`transaction`");
            s.execute("CREATE TABLE qk.`transaction` (ID INT,UID INT,accname VARCHAR(255),code INT,codenum INT,MING VARCHAR(255),ITime DATETIME) ENGINE=MyISAM DEFAULT CHARSET=utf8");
            s.execute("INSERT INTO qk.`transaction` VALUES (1,11,'a%_!b',1,2,'today',CURDATE()),(2,11,'axxxb',1,2,'yesterday',DATE_SUB(CURDATE(),INTERVAL 1 DAY)),(3,12,'a%_!b',1,2,'old',DATE_SUB(CURDATE(),INTERVAL 7 DAY)),(4,11,'future',1,2,NULL,DATE_ADD(CURDATE(),INTERVAL 1 DAY))");
            s.execute("DROP TABLE IF EXISTS qk.game_charts_logs");
            s.execute("CREATE TABLE qk.game_charts_logs (id INT,charts_day DATETIME,logs VARCHAR(255)) ENGINE=MyISAM DEFAULT CHARSET=utf8");
            s.execute("INSERT INTO qk.game_charts_logs VALUES(1,CURDATE(),'test-only-no-reward')");
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
        assertEquals("live",first.get("consistency")); assertEquals("qk-contract",first.get("instanceId"));
    }
    @Test public void invalidParametersAndUnknownTablesCannotBecomeSql() {
        rejects(404,()->records.records("sign_in_checkin_records;DROP TABLE qk.qingkong_config",null,1,50));
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
        try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM qk.`transaction`")){r.next();assertEquals(4,r.getInt(1));}
        assertEquals(14,((List<?>)((Map<?,?>)records.catalog()).get("views")).size());
    }
    @Test public void unsupportedOwnerDateSearchAndQueryInjectionAreRejected() {
        rejects(400,()->rows("power-settlements",1L,"all",null,null));
        rejects(400,()->rows("gift-history",null,"today",null,null));
        rejects(400,()->rows("transactions",null,"today; DROP TABLE qk.transaction",null,null));
        rejects(400,()->rows("transactions",null,"all","accname` OR 1=1 --","x"));
        rejects(400,()->rows("transactions",null,"all","accname",null));
        rejects(400,()->rows("transactions",null,"all",null,"x"));
        rejects(400,()->rows("transactions",null,"all","accname",String.join("",Collections.nCopies(129,"x"))));
        rejects(400,()->rows("transactions",null,"all","accname","a\0b"));
    }
    @Test public void missingNativeColumnsAreReportedWithoutInitializingThem() throws Exception {
        try(Connection c=ds.getConnection(); Statement s=c.createStatement()){s.execute("ALTER TABLE qk.sign_in_checkin_records DROP is_double");}
        rejects(409,()->page(null,1,50));
        try(Connection c=ds.getConnection(); Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='qk' AND TABLE_NAME='sign_in_checkin_records' AND COLUMN_NAME='is_double'")) {
            r.next();assertEquals(0,r.getInt(1));
        }
    }
}
