package com.aiyi.game.dnfserver.service.dof;

import java.sql.*;
import java.util.*;

/** The existing admin recharge_key table, not a native companion-file adapter. */
final class ManagedCdkSchema {
    private DofFailure bad(){return new DofFailure(409,"CDK表结构、引擎、索引或依赖不兼容；未初始化或改写原表");}
    Map<String,Object> inspect(Connection c)throws SQLException{
        String identity;try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT CURRENT_USER()")){if(!r.next())throw bad();identity=r.getString(1);}
        int at=identity==null?-1:identity.lastIndexOf('@');if(at<1)throw bad();String grantee="'"+identity.substring(0,at).replace("'","''")+"'@'"+identity.substring(at+1).replace("'","''")+"'";
        try(PreparedStatement p=c.prepareStatement("SELECT (EXISTS(SELECT 1 FROM information_schema.USER_PRIVILEGES WHERE GRANTEE=? AND PRIVILEGE_TYPE='TRIGGER') OR EXISTS(SELECT 1 FROM information_schema.SCHEMA_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA IN (?,?) AND PRIVILEGE_TYPE='TRIGGER') OR EXISTS(SELECT 1 FROM information_schema.TABLE_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA='dnf_service' AND TABLE_NAME='recharge_key' AND PRIVILEGE_TYPE='TRIGGER'))")){
            p.setString(1,grantee);p.setString(2,grantee);p.setString(3,"dnf_service");p.setString(4,"dnf\\_service");p.setString(5,grantee);try(ResultSet r=p.executeQuery()){if(!r.next()||!r.getBoolean(1))throw new DofFailure(409,"数据库账号无法确认CDK触发器，不能将不可见当作不存在");}
        }
        Map<String,Object> out=new TreeMap<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='recharge_key'")){if(!r.next()||!"InnoDB".equals(r.getString(1))||r.next())throw bad();}out.put("engine","InnoDB");
        Map<String,List<String>> expected=new TreeMap<>();
        for(String name:Arrays.asList("id","type","face","status","use_uid"))expected.put(name,Arrays.asList("int",name.equals("use_uid")?"YES":"NO",name.equals("id")?"auto_increment":"",null));
        for(String name:Arrays.asList("content","face_name","use_account"))expected.put(name,Arrays.asList("varchar(255)",name.equals("content")?"NO":"YES","","utf8"));
        expected.put("create_time",Arrays.asList("datetime","NO","",null));expected.put("use_time",Arrays.asList("datetime","YES","",null));
        Map<String,List<String>> actual=new TreeMap<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,EXTRA,CHARACTER_SET_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='recharge_key'")){
            while(r.next())actual.put(r.getString(1),Arrays.asList(r.getString(2).toLowerCase(Locale.ROOT).replaceFirst("^int\\([0-9]+\\)","int"),r.getString(3),r.getString(4),r.getString(5)));
        }if(!actual.equals(expected))throw bad();out.put("columns",actual);
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT INDEX_NAME,COLUMN_NAME,SEQ_IN_INDEX,SUB_PART FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='recharge_key' AND NON_UNIQUE=0")){
            if(!r.next()||!"PRIMARY".equals(r.getString(1))||!"id".equals(r.getString(2))||r.getInt(3)!=1||r.getObject(4)!=null||r.next())throw bad();
        }
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE EVENT_OBJECT_SCHEMA='dnf_service' AND EVENT_OBJECT_TABLE='recharge_key'")){if(!r.next()||r.getInt(1)!=0)throw bad();}
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_NAME IS NOT NULL AND ((TABLE_SCHEMA='dnf_service' AND TABLE_NAME='recharge_key') OR (REFERENCED_TABLE_SCHEMA='dnf_service' AND REFERENCED_TABLE_NAME='recharge_key'))")){if(!r.next()||r.getInt(1)!=0)throw bad();}
        return out;
    }
}
