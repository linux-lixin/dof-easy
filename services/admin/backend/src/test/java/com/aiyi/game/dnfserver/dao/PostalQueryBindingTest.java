package com.aiyi.game.dnfserver.dao;

import org.apache.ibatis.mapping.*;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;
import java.sql.PreparedStatement;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PostalQueryBindingTest {
    private BoundSql bind(String method,boolean admin,String account)throws Exception {
        Configuration config=new Configuration();config.addMapper(PostalQueryDao.class);
        MappedStatement statement=config.getMappedStatement(PostalQueryDao.class.getName()+"."+method);
        Map<String,Object> args=new HashMap<>();args.put("account",account);args.put("administrator",admin);args.put("actor",7L);
        args.put("start",new Date(1000));args.put("end",new Date(2000));args.put("offset",20);args.put("size",10);
        BoundSql sql=statement.getBoundSql(args);PreparedStatement prepared=mock(PreparedStatement.class);
        new DefaultParameterHandler(statement,args,sql).setParameters(prepared);
        for(int i=0;i<sql.getParameterMappings().size();i++) {
            String name=sql.getParameterMappings().get(i).getProperty();
            if(name.equals("account"))verify(prepared).setString(i+1,account);
            if(name.equals("actor"))verify(prepared).setLong(i+1,7L);
        }
        return sql;
    }
    @Test public void listAndCountBindLiteralAccountAndKeepCharacterRelationship()throws Exception {
        String payload="absent' OR 1=1 -- &account=other";
        for(String method:Arrays.asList("list","count")) {
            String sql=bind(method,true,payload).getSql();assertFalse(sql.contains(payload));
            assertTrue(sql.contains("a.UID=c.m_id"));assertTrue(sql.contains("c.charac_no=p.receive_charac_no"));
            assertTrue(sql.contains("a.accountname=?"));assertFalse(sql.contains("LIKE"));
        }
    }
    @Test public void guildScopeIsEnforcedWithAndWithoutAccountFilter()throws Exception {
        for(String method:Arrays.asList("list","count"))for(String account:Arrays.asList(null,"other")) {
            String sql=bind(method,false,account).getSql();assertTrue(sql.contains("(a.UID=? OR a.parent_uid=?)"));
            assertTrue(sql.contains("EXISTS"));
        }
    }
    @Test public void adminUnfilteredHistoryRetainsOrphanedCharacterMail()throws Exception {
        for(String method:Arrays.asList("list","count")) {
            String sql=bind(method,true,null).getSql();assertFalse(sql.contains("charac_info"));
            assertTrue(sql.contains("p.occ_time >= ?"));assertTrue(sql.contains("p.occ_time <= ?"));
        }
        String sql=bind("list",true,null).getSql();assertTrue(sql.contains("ORDER BY p.postal_id DESC LIMIT ?,?"));
    }
}
