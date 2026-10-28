package com.aiyi.game.dnfserver.service.extension;

import java.sql.*;
import java.util.*;

/** Fixed ranking/worship reward rules; no player rewards are issued here. */
final class NativeChartsRewardsRules {
    private NativeChartsRewardsRules() { }
    private static final List<String> FIELDS=Arrays.asList("top_index","code_data","code_num");
    static void restoreGuard(Map<String,Object> before,Map<String,Object> after,Map<String,Object> current) {
        boolean changed=false,conflict=false;
        for(String key:Arrays.asList("code_data","code_num")) {
            changed|=!Objects.equals(String.valueOf(before.get(key)),String.valueOf(after.get(key)));
            conflict|=!Objects.equals(String.valueOf(current.get(key)),String.valueOf(after.get(key)));
        }
        if(changed&&conflict)throw new ActivityConfigService.Failure(409,"排行奖励代码或数量已有其他修改，停止成组恢复");
    }
    private static ActivityConfigService.Failure incompatible() {
        return new ActivityConfigService.Failure(409,"排行榜奖励表结构或索引未匹配原生3000字符版本，停止写入");
    }
    private static boolean hasSharedConsumer(Connection c)throws SQLException {
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='fenghuo_charts_config'")) {
            if(!r.next())throw incompatible();return r.getInt(1)==1;
        }
    }
    static String dependencyLocks(Connection c)throws SQLException {
        return hasSharedConsumer(c)?",`dof_native`.`fenghuo_charts_config` READ":"";
    }
    private static void sharedConsumerGuard(Connection c)throws SQLException {
        // This optional native query has no source rows if its table is absent.
        // Recheck under the lock: a concurrently introduced table must never be
        // silently treated as disabled or read without a LOCK TABLES entry.
        if(!hasSharedConsumer(c))return;
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT TABLE_TYPE,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='fenghuo_charts_config'")) {
            if(!r.next()||!"BASE TABLE".equals(r.getString(1))||!Arrays.asList("MyISAM","InnoDB").contains(r.getString(2)))throw incompatible();
        }
        Set<String> required=new HashSet<>(Arrays.asList("min_zhanli","jiangli_day","jiangli_hour","min_baodi_zhanli","status","next_time"));
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COLUMN_NAME,DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='fenghuo_charts_config'")) {
            while(r.next())if(required.contains(r.getString(1))) {
                if(!("next_time".equals(r.getString(1))?"datetime".equals(r.getString(2)):Arrays.asList("int","bigint","smallint","mediumint","tinyint").contains(r.getString(2))))throw incompatible();
                required.remove(r.getString(1));
            }
        }
        if(!required.isEmpty())throw incompatible();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*)>0 AND COUNT(*)=SUM(status=0) FROM `dof_native`.`fenghuo_charts_config`")) {
            if(!r.next()||!r.getBoolean(1))throw new ActivityConfigService.Failure(409,"共享奖励表的公益榜仍启用或状态不明确，停止奖励写入");
        }
    }
    static void requireSchema(Connection c)throws SQLException {
        int index=0;
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COLUMN_NAME,DATA_TYPE,COLUMN_TYPE,CHARACTER_MAXIMUM_LENGTH,CHARACTER_SET_NAME,IS_NULLABLE,EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='game_charts_code' ORDER BY ORDINAL_POSITION")) {
            while(r.next()) {
                if(index>=FIELDS.size()||!FIELDS.get(index).equals(r.getString(1))||!"NO".equals(r.getString(6))||!"".equals(r.getString(7)))throw incompatible();
                if(index==0) {
                    if(!"int".equals(r.getString(2))||r.getString(3).toLowerCase(Locale.ROOT).contains("unsigned"))throw incompatible();
                }else if(!"varchar".equals(r.getString(2))||r.getLong(4)!=3000||!Arrays.asList("utf8","utf8mb3","utf8mb4").contains(r.getString(5)))throw incompatible();
                index++;
            }
        }
        if(index!=FIELDS.size())throw incompatible();
        boolean primary=false;
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT INDEX_NAME,COLUMN_NAME,SEQ_IN_INDEX,NON_UNIQUE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='game_charts_code'")) {
            while(r.next()) {
                if("PRIMARY".equals(r.getString(1))) {if(primary||!"top_index".equals(r.getString(2))||r.getInt(3)!=1)throw incompatible();primary=true;}
                else if(r.getInt(4)==0&&(!"top_index".equals(r.getString(2))||r.getInt(3)!=1))throw incompatible();
            }
        }
        if(!primary)throw incompatible();
        NativeConfigSideEffects.require(c,"game_charts_code");
        sharedConsumerGuard(c);
    }
}
