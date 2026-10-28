package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.*;
import com.aiyi.game.dnfserver.entity.*;
import com.aiyi.game.dnfserver.service.impl.PostalServiceImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Populated synthetic accounts/characters against the seed postal schema and actual mapper. */
public class PostalReadContractTest {
    private DriverManagerDataSource ds;private String uuid;private SqlSession session;
    private final List<String> created=new ArrayList<>();
    private PostalServiceImpl service;private AccountVO actor;
    @Before public void setup()throws Exception {
        String path=System.getProperty("dof.postal.contract.properties");
        Assume.assumeTrue("requires dedicated disposable postal MySQL fixture",path!=null);
        Properties config=new Properties();try(InputStream in=Files.newInputStream(Paths.get(path))){config.load(in);}
        uuid=config.getProperty("uuid");assertNotNull(uuid);
        ds=new DriverManagerDataSource(config.getProperty("url"),config.getProperty("username"),config.getProperty("password"));
        guard();
        assertEquals("0",scalar("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('d_taiwan','taiwan_cain','taiwan_cain_2nd')"));
        for(String schema:Arrays.asList("d_taiwan","taiwan_cain","taiwan_cain_2nd")){sql("CREATE DATABASE "+schema+" DEFAULT CHARACTER SET utf8");created.add(schema);}
        sql("CREATE TABLE d_taiwan.accounts(UID INT PRIMARY KEY,accountname VARCHAR(128),parent_uid INT) ENGINE=InnoDB");
        sql("INSERT INTO d_taiwan.accounts VALUES(1,'self',0),(10,'alpha',1),(20,'beta',9),(30,'wild_%',9)");
        sql("CREATE TABLE taiwan_cain.charac_info(charac_no INT PRIMARY KEY,m_id INT,delete_flag TINYINT) ENGINE=InnoDB");
        sql("INSERT INTO taiwan_cain.charac_info VALUES(101,10,0),(102,10,1),(10,20,0),(201,1,0),(301,30,0)");
        String seed=new String(Files.readAllBytes(Paths.get(System.getProperty("dof.postal.contract.seed"))),StandardCharsets.UTF_8);
        Matcher table=Pattern.compile("CREATE TABLE `postal` \\(.*?\\) ENGINE=InnoDB DEFAULT CHARSET=utf8;",Pattern.DOTALL).matcher(seed);
        assertTrue("actual seed postal definition required",table.find());
        sql(table.group().replace("CREATE TABLE `postal`","CREATE TABLE taiwan_cain_2nd.`postal`"));
        sql("INSERT INTO taiwan_cain_2nd.postal(postal_id,occ_time,receive_charac_no,item_id,add_info,send_charac_name,`upgrade`,amplify_option,amplify_value,gold,delete_flag,seal_flag,letter_id,seperate_upgrade) VALUES "
            +"(1,'2026-01-01 12:00:00',101,5001,2,'測試郵件',12,3,45,789,0,1,0,7),"
            +"(2,'2026-01-02 23:59:59',102,5002,1,'sender',0,0,0,0,1,0,0,0),"
            +"(3,'2026-01-02 12:00:00',10,5003,1,'sender',0,0,0,0,0,0,0,0),"
            +"(4,'2026-01-03 12:00:00',999,5004,1,'orphan',0,0,0,0,0,0,0,0),"
            +"(5,'2026-01-02 10:00:00',201,5005,1,'self',0,0,0,0,0,0,0,0),"
            +"(6,'2026-01-02 14:00:00',301,5006,1,'literal',0,0,0,0,0,0,0,0)");
        Configuration mapping=new Configuration(new Environment("postal-read-contract",new JdbcTransactionFactory(),ds));mapping.addMapper(PostalQueryDao.class);
        session=new SqlSessionFactoryBuilder().build(mapping).openSession(true);
        AccountVODao accounts=mock(AccountVODao.class);actor=new AccountVO();actor.setUid(1L);actor.setAdmin(true);when(accounts.get(1L)).thenReturn(actor);
        ThreadUtil.setUserId(1L);service=new PostalServiceImpl();ReflectionTestUtils.setField(service,"accountVODao",accounts);
        ReflectionTestUtils.setField(service,"postalQueryDao",session.getMapper(PostalQueryDao.class));
    }
    private String scalar(String sql)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}}
    private void sql(String sql)throws Exception{try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute(sql);}}
    private void guard()throws Exception {
        assertEquals(uuid,scalar("SELECT @@server_uuid"));
        assertEquals("disposable-postal-read-contract",scalar("SELECT purpose FROM dnf_service.DOF_POSTAL_TEST_MARKER"));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dnf_service.DOF_POSTAL_TEST_MARKER"));
    }
    @After public void cleanup()throws Exception {
        try {if(session!=null)session.close();if(!created.isEmpty()){guard();for(String schema:created)sql("DROP DATABASE "+schema);}}
        finally{ThreadUtil.setUserId(1L);}
    }
    private List<Long> ids(ResultPage<Postal> page){List<Long> ids=new ArrayList<>();for(Postal p:page.getList())ids.add(p.getPostalId());return ids;}
    @Test public void accountUsesAllCharactersIncludingHistoricalDeletedRolesWithoutUidCollision() {
        ResultPage<Postal> page=service.list("alpha",null,null,1,10);assertEquals(2,page.getTotalSize());assertEquals(Arrays.asList(2L,1L),ids(page));
        Postal p=page.getList().get(1);assertEquals("101",p.getReceiveCharacNo());assertEquals(5001,p.getItemId());assertEquals(2,p.getAddInfo());
        assertEquals("測試郵件",p.getSendCharacName());assertEquals(12,p.getUpgrade());assertEquals(7,p.getSeperateUpgrade());
        assertEquals(3,p.getAmplifyOption());assertEquals(45,p.getAmplifyValue());assertEquals(789,p.getGold());assertTrue(p.isSealFlag());assertFalse(p.isDeleteFlag());
    }
    @Test public void unknownSqlLookingAndWildcardAccountsNeverFallBackToAllMail() {
        for(String filter:Arrays.asList("missing","absent' OR 1=1 -- ","wild%","%"))assertTrue(service.list(filter,null,null,1,10).getList().isEmpty());
        assertEquals(Collections.singletonList(6L),ids(service.list("wild_%",null,null,1,10)));
    }
    @Test public void guildScopeIncludesSelfAndChildrenAndExcludesForeignAndOrphanRows() {
        actor.setAdmin(false);assertEquals(Arrays.asList(5L,2L,1L),ids(service.list(null,null,null,1,10)));
        assertTrue(service.list("beta",null,null,1,10).getList().isEmpty());
        assertEquals(Arrays.asList(2L,1L),ids(service.list("alpha",null,null,1,10)));
    }
    @Test public void stablePaginationCountsAndInclusiveTimeBoundariesUsePopulatedRows()throws Exception {
        ResultPage<Postal> first=service.list(null,null,null,1,2),second=service.list(null,null,null,2,2),last=service.list(null,null,null,3,2);
        for(ResultPage<Postal> page:Arrays.asList(first,second,last)){assertEquals(6,page.getTotalSize());assertEquals(3,page.getTotalPageSize());}
        assertEquals(Arrays.asList(6L,5L),ids(first));assertEquals(Arrays.asList(4L,3L),ids(second));assertEquals(Arrays.asList(2L,1L),ids(last));
        // Administrator's unfiltered history includes postal 4 even without a current character.
        assertEquals("6",scalar("SELECT COUNT(*) FROM taiwan_cain_2nd.postal"));
        java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        assertEquals(Arrays.asList(6L,5L,3L,2L),ids(service.list(null,f.parse("2026-01-02 00:00:00"),f.parse("2026-01-02 23:59:59"),1,10)));
        assertTrue(service.list("alpha",null,null,9,2).getList().isEmpty());
    }
}
