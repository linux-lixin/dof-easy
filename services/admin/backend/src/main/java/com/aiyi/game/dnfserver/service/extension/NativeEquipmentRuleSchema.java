package com.aiyi.game.dnfserver.service.extension;

import java.sql.*;
import java.util.*;

/** Pinned ordinal-field equipment tables; unknown shapes and hidden side effects reject writes. */
final class NativeEquipmentRuleSchema {
    private NativeEquipmentRuleSchema() { }
    private static ActivityConfigService.Failure incompatible() {
        return new ActivityConfigService.Failure(409,"装备规则表结构、索引或副作用未匹配当前原生版本，停止写入");
    }
    static void requireSchema(Connection c,String table,List<String> names,Set<String> integers) throws SQLException {
        if (!Arrays.asList("inheritance","item_recovery","cross_border").contains(table)) throw incompatible();
        int index = 0;
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COLUMN_NAME,DATA_TYPE,COLUMN_TYPE,CHARACTER_MAXIMUM_LENGTH,CHARACTER_SET_NAME,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='"+table+"' ORDER BY ORDINAL_POSITION")) {
            while (r.next()) {
                if (index >= names.size() || !names.get(index).equals(r.getString(1)) || !"NO".equals(r.getString(6))) throw incompatible();
                if (integers.contains(names.get(index))) {
                    if (!"int".equals(r.getString(2)) || !r.getString(3).toLowerCase(Locale.ROOT).contains("unsigned")) throw incompatible();
                } else if (!"varchar".equals(r.getString(2)) || r.getLong(4) != 255
                    || !Arrays.asList("utf8","utf8mb3","utf8mb4").contains(r.getString(5))) throw incompatible();
                index++;
            }
        }
        if (index != names.size()) throw incompatible();
        boolean primary = false;
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT INDEX_NAME,COLUMN_NAME,SEQ_IN_INDEX,NON_UNIQUE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='"+table+"'")) {
            while (r.next()) {
                if ("PRIMARY".equals(r.getString(1))) {
                    if (primary || !"code".equals(r.getString(2)) || r.getInt(3) != 1) throw incompatible();
                    primary = true;
                } else if (r.getInt(4) == 0 && (!"code".equals(r.getString(2)) || r.getInt(3) != 1)) throw incompatible();
            }
        }
        if (!primary) throw incompatible();
        // Without TRIGGER visibility an empty information_schema result is not evidence.
        String identity;
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT CURRENT_USER()")) {
            if (!r.next()) throw incompatible(); identity = r.getString(1);
        }
        int at = identity == null ? -1 : identity.lastIndexOf('@'); if (at < 1) throw incompatible();
        String grantee = "'"+identity.substring(0,at).replace("'","''")+"'@'"+identity.substring(at+1).replace("'","''")+"'";
        try (PreparedStatement s = c.prepareStatement("SELECT (EXISTS(SELECT 1 FROM information_schema.USER_PRIVILEGES WHERE GRANTEE=? AND PRIVILEGE_TYPE='TRIGGER') OR EXISTS(SELECT 1 FROM information_schema.SCHEMA_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA IN (?,?) AND PRIVILEGE_TYPE='TRIGGER') OR EXISTS(SELECT 1 FROM information_schema.TABLE_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA='dof_native' AND TABLE_NAME='"+table+"' AND PRIVILEGE_TYPE='TRIGGER'))")) {
            s.setString(1,grantee); s.setString(2,grantee); s.setString(3,"dof_native"); s.setString(4,"dof\\_native"); s.setString(5,grantee);
            try (ResultSet r=s.executeQuery()) { if (!r.next() || !r.getBoolean(1)) throw incompatible(); }
        }
        for (String sql : Arrays.asList(
            "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE EVENT_OBJECT_SCHEMA='dof_native' AND EVENT_OBJECT_TABLE='"+table+"'",
            "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_NAME IS NOT NULL AND ((TABLE_SCHEMA='dof_native' AND TABLE_NAME='"+table+"') OR (REFERENCED_TABLE_SCHEMA='dof_native' AND REFERENCED_TABLE_NAME='"+table+"'))"))
            try (Statement s=c.createStatement(); ResultSet r=s.executeQuery(sql)) { if (!r.next() || r.getInt(1)!=0) throw incompatible(); }
    }
}
