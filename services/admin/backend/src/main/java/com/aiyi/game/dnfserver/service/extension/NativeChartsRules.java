package com.aiyi.game.dnfserver.service.extension;

import java.sql.*;
import java.util.*;

/** The native gateway owns ranking, worship, scheduling and reward delivery. */
final class NativeChartsRules {
    private NativeChartsRules() { }
    private static final List<String> FIELDS=Arrays.asList("id","min_zhanli","jiangli_day","jiangli_hour","is_mobai","status","next_time");
    private static boolean differs(Map<String,Object> a,Map<String,Object> b,String field) {
        return !Objects.equals(String.valueOf(a.get(field)),String.valueOf(b.get(field)));
    }
    static void validate(Map<String,Object> values,Map<String,Object> before) {
        if(before==null)throw new ActivityConfigService.Failure(409,"排行榜规则只支持修改已有记录");
        if(differs(values,before,"jiangli_day")&&Long.parseLong(values.get("jiangli_day").toString())<1)
            throw new ActivityConfigService.Failure(400,"修改后的奖励间隔至少为1天");
        if(differs(values,before,"jiangli_hour")&&Long.parseLong(values.get("jiangli_hour").toString())>23)
            throw new ActivityConfigService.Failure(400,"修改后的奖励小时须为0～23");
    }
    static void scheduleRange(Connection c,Map<String,Object> before,Map<String,Object> after)throws SQLException {
        if(before==null||after==null||!differs(before,after,"jiangli_day")&&!differs(before,after,"jiangli_hour"))return;
        try(PreparedStatement p=c.prepareStatement("SELECT (DATE_ADD(CURDATE(),INTERVAL ? DAY) + INTERVAL ? HOUR) IS NOT NULL")) {
            p.setObject(1,after.get("jiangli_day"));p.setObject(2,after.get("jiangli_hour"));
            try(ResultSet r=p.executeQuery()){if(!r.next()||!r.getBoolean(1))throw new ActivityConfigService.Failure(400,"奖励间隔超出数据库有效日期范围");}
        }
    }
    static void restoreGuard(Map<String,Object> before,Map<String,Object> after,Map<String,Object> current) {
        boolean changedSchedule=differs(before,after,"jiangli_day")||differs(before,after,"jiangli_hour");
        if(changedSchedule&&(differs(current,after,"jiangli_day")||differs(current,after,"jiangli_hour")||differs(current,after,"next_time")))
            throw new ActivityConfigService.Failure(409,"奖励周期、小时或原生下次结算时间已有其他修改，停止成组恢复");
    }
    private static ActivityConfigService.Failure incompatible() {
        return new ActivityConfigService.Failure(409,"排行榜规则表结构、索引或记录不匹配已知原生版本，停止写入");
    }
    static void requireSchema(Connection c)throws SQLException {
        int index=0;
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COLUMN_NAME,DATA_TYPE,COLUMN_TYPE,IS_NULLABLE,EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='game_charts_config' ORDER BY ORDINAL_POSITION")) {
            while(r.next()) {
                if(index>=FIELDS.size()||!FIELDS.get(index).equals(r.getString(1)))throw incompatible();
                if(index<6) {
                    if(!"int".equals(r.getString(2))||r.getString(3).toLowerCase(Locale.ROOT).contains("unsigned")||!"NO".equals(r.getString(4)))throw incompatible();
                }else if(!"datetime".equals(r.getString(2))||!"datetime".equalsIgnoreCase(r.getString(3))||!"YES".equals(r.getString(4)))throw incompatible();
                if(!Objects.equals(index==0?"auto_increment":"",r.getString(5)))throw incompatible();
                index++;
            }
        }
        if(index!=FIELDS.size())throw incompatible();
        boolean primary=false;
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT INDEX_NAME,COLUMN_NAME,SEQ_IN_INDEX,NON_UNIQUE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='game_charts_config'")) {
            while(r.next()) {
                // The verified 86-level instance uses a named UNIQUE key, not
                // a SQL PRIMARY key. Both known layouts require the same
                // single, non-null integer identity; unknown names stay closed.
                if(Arrays.asList("PRIMARY","game_charts_config_pk").contains(r.getString(1))) {if(primary||r.getInt(4)!=0||!"id".equals(r.getString(2))||r.getInt(3)!=1)throw incompatible();primary=true;}
                else if(r.getInt(4)==0&&(!"id".equals(r.getString(2))||r.getInt(3)!=1))throw incompatible();
            }
        }
        if(!primary)throw incompatible();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='game_charts_config'")) {
            if(!r.next()||!Arrays.asList("MyISAM","InnoDB").contains(r.getString(1)))throw incompatible();
        }
        NativeConfigSideEffects.require(c,"game_charts_config");
    }
}
