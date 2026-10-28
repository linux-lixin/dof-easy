package com.aiyi.game.dnfserver.dao;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;
import java.sql.PreparedStatement;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Execute the actual annotation mapper and parameter binder, without a database. */
public class AccountQueryBindingTest {
    private void checkBinding(String method) throws Exception {
        Configuration configuration=new Configuration();configuration.addMapper(AccountDao.class);
        MappedStatement statement=configuration.getMappedStatement(AccountDao.class.getName()+"."+method);
        String payload="probe' OR 1=1 -- ";
        Map<String,Object> parameters=new HashMap<>();parameters.put("account",payload);
        parameters.put("parentUid",5L);parameters.put("page",0);parameters.put("pageSize",10);
        parameters.put("loginStatus",null);parameters.put("lastLoginDate",null);parameters.put("lastLoginDateEnd",null);
        BoundSql bound=statement.getBoundSql(parameters);
        assertFalse(bound.getSql().toLowerCase(Locale.ROOT).contains("password"));
        assertFalse(bound.getSql().contains(payload));assertTrue(bound.getSql().contains("LIKE CONCAT('%', ?, '%')"));
        PreparedStatement prepared=mock(PreparedStatement.class);
        new DefaultParameterHandler(statement,parameters,bound).setParameters(prepared);
        int accountIndex=-1;
        for(int i=0;i<bound.getParameterMappings().size();i++)if("account".equals(bound.getParameterMappings().get(i).getProperty()))accountIndex=i+1;
        assertTrue(accountIndex>0);verify(prepared).setString(accountIndex,payload);
        if(method.equals("list")||method.equals("count")) {
            assertTrue(bound.getSql().contains("(parent_uid=? or UID=?)"));
            verify(prepared).setLong(1,5L);verify(prepared).setLong(2,5L);
        }
        parameters.put("account","");BoundSql empty=statement.getBoundSql(parameters);
        assertFalse(empty.getSql().contains("accountname LIKE"));
        parameters.put("account","normal_%");BoundSql wildcard=statement.getBoundSql(parameters);
        assertFalse(wildcard.getSql().contains("normal_%")); // Existing LIKE wildcard semantics remain bound data.
    }
    @Test public void guildListBindsSearchWithoutChangingScope()throws Exception {checkBinding("list");}
    @Test public void guildCountBindsSearchWithoutChangingScope()throws Exception {checkBinding("count");}
    @Test public void adminListBindsSearch()throws Exception {checkBinding("listAll");}
    @Test public void adminCountBindsSearch()throws Exception {checkBinding("countAll");}
}
