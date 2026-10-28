package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.sql.*;
import java.util.*;

/** Fixed seed mail adapter. Metadata is part of each preview revision. */
final class ManagedPostalSchema {
    private final Map<String,Map<String,Map<String,Object>>> expected;
    ManagedPostalSchema(){
        try(InputStream in=getClass().getResourceAsStream("/dof/postal-schema.json")){
            if(in==null)throw new IllegalStateException("Missing fixed postal schema");
            expected=new ObjectMapper().readValue(in,new TypeReference<Map<String,Map<String,Map<String,Object>>>>(){});
        }catch(java.io.IOException e){throw new IllegalStateException("Invalid fixed postal schema",e);}
    }
    private DofFailure bad(){return new DofFailure(409,"邮件、信件、收件身份或日志结构不兼容，停止发送；未自动修改原表");}
    private void visible(Connection c,String schema,String table)throws SQLException {
        String identity;try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT CURRENT_USER()")){if(!r.next())throw bad();identity=r.getString(1);}
        if(identity==null||identity.lastIndexOf('@')<1)throw bad();int at=identity.lastIndexOf('@');
        String grantee="'"+identity.substring(0,at).replace("'","''")+"'@'"+identity.substring(at+1).replace("'","''")+"'";
        try(PreparedStatement p=c.prepareStatement("SELECT (EXISTS(SELECT 1 FROM information_schema.USER_PRIVILEGES WHERE GRANTEE=? AND PRIVILEGE_TYPE='TRIGGER') OR EXISTS(SELECT 1 FROM information_schema.SCHEMA_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA IN (?,?) AND PRIVILEGE_TYPE='TRIGGER') OR EXISTS(SELECT 1 FROM information_schema.TABLE_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA=? AND TABLE_NAME=? AND PRIVILEGE_TYPE='TRIGGER'))")){
            p.setString(1,grantee);p.setString(2,grantee);p.setString(3,schema);p.setString(4,schema.replace("_","\\_"));p.setString(5,grantee);p.setString(6,schema);p.setString(7,table);
            try(ResultSet r=p.executeQuery()){if(!r.next()||!r.getBoolean(1))throw new DofFailure(409,"数据库账号无法确认邮件或日志触发器，不能将不可见当作不存在");}
        }
    }
    Map<String,Object> inspect(Connection c,ManagedOperationJournal journal)throws SQLException {
        journal.schema(c);Map<String,Object> out=new TreeMap<>();boolean atomic=true;
        for(String table:Arrays.asList("postal","letter","DOF_OPERATION_JOURNAL")){
            String db=table.equals("DOF_OPERATION_JOURNAL")?"dnf_service":"taiwan_cain_2nd";
            visible(c,db,table);String engine;
            try(PreparedStatement p=c.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")){
                p.setString(1,db);p.setString(2,table);try(ResultSet r=p.executeQuery()){
                    if(!r.next()||!Arrays.asList("InnoDB","MyISAM").contains(engine=r.getString(1))||r.next())throw bad();
                }
            }
            atomic &= engine.equals("InnoDB");out.put(table+"Engine",engine);
            if(!db.equals("dnf_service")){
                Map<String,Map<String,Object>> columns=new TreeMap<>();
                try(PreparedStatement p=c.prepareStatement("SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,CHARACTER_SET_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")){
                    p.setString(1,db);p.setString(2,table);try(ResultSet r=p.executeQuery()){while(r.next()){
                        String type=r.getString(2).toLowerCase(Locale.ROOT).replaceFirst("^(tinyint|smallint|mediumint|int|bigint)\\([0-9]+\\)","$1");
                        Map<String,Object> v=new TreeMap<>();v.put("type",type);v.put("nullable",r.getString(3));v.put("default",r.getString(4));v.put("extra",r.getString(5));v.put("charset",r.getString(6));columns.put(r.getString(1),v);
                    }}
                }
                if(!expected.get(table).equals(columns))throw bad();out.put(table+"Columns",columns);
                try(PreparedStatement p=c.prepareStatement("SELECT INDEX_NAME,COLUMN_NAME,SEQ_IN_INDEX,SUB_PART FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND NON_UNIQUE=0")){
                    p.setString(1,db);p.setString(2,table);try(ResultSet r=p.executeQuery()){
                        if(!r.next()||!"PRIMARY".equals(r.getString(1))||!(table+"_id").equals(r.getString(2))||r.getInt(3)!=1||r.getObject(4)!=null||r.next())throw bad();
                    }
                }
            }
            // Existing postal UPDATE/DELETE archival triggers are not invoked by
            // our INSERT. INSERT triggers or ANY journal trigger are unsupported.
            try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE EVENT_OBJECT_SCHEMA=? AND EVENT_OBJECT_TABLE=? AND (?='dnf_service' OR EVENT_MANIPULATION='INSERT')")){
                p.setString(1,db);p.setString(2,table);p.setString(3,db);try(ResultSet r=p.executeQuery()){if(!r.next()||r.getInt(1)!=0)throw bad();}
            }
            try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_NAME IS NOT NULL AND ((TABLE_SCHEMA=? AND TABLE_NAME=?) OR (REFERENCED_TABLE_SCHEMA=? AND REFERENCED_TABLE_NAME=?))")){
                p.setString(1,db);p.setString(2,table);p.setString(3,db);p.setString(4,table);try(ResultSet r=p.executeQuery()){if(!r.next()||r.getInt(1)!=0)throw bad();}
            }
        }
        Map<String,String> identity=new TreeMap<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT TABLE_SCHEMA,TABLE_NAME,COLUMN_NAME,DATA_TYPE,COLUMN_KEY FROM information_schema.COLUMNS WHERE (TABLE_SCHEMA='taiwan_cain' AND TABLE_NAME='charac_info' AND COLUMN_NAME IN ('charac_no','m_id','charac_name','delete_flag','create_time')) OR (TABLE_SCHEMA='d_taiwan' AND TABLE_NAME='accounts' AND COLUMN_NAME IN ('UID','accountname','admin'))")){
            while(r.next()){String k=r.getString(1)+"."+r.getString(2)+"."+r.getString(3);identity.put(k,r.getString(4));if(Arrays.asList("charac_no","UID").contains(r.getString(3))&&!"PRI".equals(r.getString(5)))throw bad();}
        }
        Map<String,String> want=new TreeMap<>();
        for(String name:Arrays.asList("charac_no","m_id"))want.put("taiwan_cain.charac_info."+name,"int");
        want.put("taiwan_cain.charac_info.charac_name","varchar");want.put("taiwan_cain.charac_info.delete_flag","tinyint");want.put("taiwan_cain.charac_info.create_time","datetime");
        want.put("d_taiwan.accounts.UID","int");want.put("d_taiwan.accounts.accountname","varchar");want.put("d_taiwan.accounts.admin","int");
        if(!want.equals(identity))throw bad();out.put("identityColumns",identity);out.put("atomicReceipt",atomic);return out;
    }
}
