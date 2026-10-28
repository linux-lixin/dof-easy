package com.aiyi.game.dnfserver.service.extension;

import java.sql.*;
import java.util.*;

/** Known native tables only; absence of trigger visibility is not proof of safety. */
final class NativeConfigSideEffects {
    private NativeConfigSideEffects() { }
    private static ActivityConfigService.Failure incompatible() {
        return new ActivityConfigService.Failure(409,"原生配置的触发器、外键或检查权限不兼容，停止写入");
    }
    static void require(Connection c,String table) throws SQLException {
        if(!Arrays.asList("inheritance","item_recovery","cross_border","game_charts_config").contains(table))throw incompatible();
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
