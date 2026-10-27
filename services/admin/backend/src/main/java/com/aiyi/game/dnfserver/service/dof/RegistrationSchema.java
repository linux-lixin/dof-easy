package com.aiyi.game.dnfserver.service.dof;

import java.sql.*;
import java.util.*;

/** Read-only compatibility checks. DDL belongs to the installer migration. */
public final class RegistrationSchema {
    private RegistrationSchema(){}
    static final Map<String,Map<String,String>> TABLES=new LinkedHashMap<>();
    static {spec("REGISTRATION_POLICY","id:int,enabled:tinyint,revision:bigint");spec("REGISTRATION_CODES","id:bigint,request_id:varchar,code_hash:char,state:varchar,expires_at:bigint,created_at:bigint,actor:bigint,used_uid:bigint,used_at:bigint");spec("REGISTRATION_AUDIT","id:bigint,actor:bigint,code_id:bigint,action:varchar,created_at:bigint");spec("DOF_REGISTRATION_RECEIPT","instance_id:varchar,request_id:char,name_tag:char,ip_tag:char,request_tag:char,key_id:char,state:varchar,uid:int,gift_amount:int,gift_currency:tinyint,parent_request:char,created_at:timestamp,finished_at:timestamp");spec("DOF_REGISTRATION_GATE","instance_id:varchar,name_tag:char");}
    private static void spec(String table,String fields){Map<String,String> types=new LinkedHashMap<>();for(String s:fields.split(",")){String[] p=s.split(":");types.put(p[0],p[1]);}TABLES.put(table,types);}
    public static void check(Connection c)throws SQLException {
        for(Map.Entry<String,Map<String,String>> entry:TABLES.entrySet()){
            try(PreparedStatement p=c.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME=?")){p.setString(1,entry.getKey());try(ResultSet r=p.executeQuery()){if(!r.next()||!"InnoDB".equals(r.getString(1))||r.next())throw bad();}}
            Map<String,String> types=new HashMap<>();Set<String> primary=new HashSet<>();
            try(PreparedStatement p=c.prepareStatement("SELECT COLUMN_NAME,DATA_TYPE,COLUMN_KEY,IS_NULLABLE,CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME=?")){p.setString(1,entry.getKey());try(ResultSet r=p.executeQuery()){while(r.next()){types.put(r.getString(1),r.getString(2));if("PRI".equals(r.getString(3)))primary.add(r.getString(1));String column=r.getString(1);if(nullable(entry.getKey(),column)!="YES".equals(r.getString(4)))throw bad();int width=width(entry.getKey(),column);if(width>0&&r.getLong(5)!=width)throw bad();}}}
            if(!types.equals(entry.getValue()))throw bad();Set<String> expected=entry.getKey().equals("DOF_REGISTRATION_RECEIPT")?new HashSet<>(Arrays.asList("instance_id","request_id")):entry.getKey().equals("DOF_REGISTRATION_GATE")?new HashSet<>(Arrays.asList("instance_id","name_tag")):Collections.singleton("id");if(!primary.equals(expected))throw bad();
        }
        codeIndexes(c);
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX),MAX(NON_UNIQUE),MAX(SUB_PART) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='DOF_REGISTRATION_RECEIPT' AND INDEX_NAME='registration_uid'")){
            if(!r.next()||!"instance_id,uid".equals(r.getString(1))||r.getInt(2)!=0||r.getObject(3)!=null)throw bad();
        }
    }
    private static boolean nullable(String table,String column){
        if(table.equals("REGISTRATION_CODES"))return Arrays.asList("used_uid","used_at").contains(column);
        if(table.equals("REGISTRATION_AUDIT"))return column.equals("code_id");
        return table.equals("DOF_REGISTRATION_RECEIPT")&&Arrays.asList("uid","gift_amount","gift_currency","parent_request","finished_at").contains(column);
    }
    private static int width(String table,String column){
        if(table.equals("REGISTRATION_CODES"))return column.equals("request_id")||column.equals("code_hash")?64:column.equals("state")?16:0;
        if(table.equals("REGISTRATION_AUDIT"))return column.equals("action")?32:0;
        if(table.equals("DOF_REGISTRATION_RECEIPT")||table.equals("DOF_REGISTRATION_GATE"))return column.equals("request_id")||column.equals("parent_request")?36:column.equals("state")?16:Arrays.asList("instance_id","name_tag","ip_tag","request_tag","key_id").contains(column)?64:0;
        return 0;
    }
    static void codeIndexes(Connection c)throws SQLException {
        Map<String,List<String>> indexes=new HashMap<>();Set<String> partial=new HashSet<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT INDEX_NAME,COLUMN_NAME,SUB_PART FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='REGISTRATION_CODES' AND NON_UNIQUE=0 ORDER BY INDEX_NAME,SEQ_IN_INDEX")){
            while(r.next()){if(r.getObject(3)!=null)partial.add(r.getString(1));indexes.computeIfAbsent(r.getString(1),k->new ArrayList<>()).add(r.getString(2));}
        }
        for(String name:partial)indexes.remove(name);
        for(String field:Arrays.asList("request_id","code_hash"))if(!indexes.containsValue(Collections.singletonList(field)))throw bad();
    }
    static DofFailure bad(){return new DofFailure(409,"注册管理迁移缺失或结构不兼容，请先更新管理表；未初始化或修改玩家表");}
}
