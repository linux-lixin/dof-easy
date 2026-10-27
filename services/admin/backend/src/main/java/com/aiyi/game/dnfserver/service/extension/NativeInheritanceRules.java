package com.aiyi.game.dnfserver.service.extension;

import java.sql.*;
import java.util.*;

/** The pinned native reader uses SELECT *, ordinal fields and slash-separated cost pairs.
 * This adapter edits rules only; the native game operation remains the sole executor.
 */
final class NativeInheritanceRules {
    private NativeInheritanceRules() { }
    private static ActivityConfigService.Failure incompatible() {
        return new ActivityConfigService.Failure(409,"装备继承表结构、索引或副作用未匹配当前原生版本，停止写入");
    }
    static void validate(Map<String,Object> values, Map<String,Object> before) {
        // An unchanged legacy representation must survive edits to unrelated fields.
        if (before != null && Objects.equals(values.get("restitution"),before.get("restitution"))
            && Objects.equals(values.get("Return_quantity"),before.get("Return_quantity"))) return;
        String items = String.valueOf(values.get("restitution")), counts = String.valueOf(values.get("Return_quantity"));
        if (items.isEmpty() && counts.isEmpty()) return; // Native stream parser yields two empty vectors.
        List<Integer> ids = list(items,"扣除道具代码"), quantities = list(counts,"扣除道具数量");
        if (ids.size() != quantities.size())
            throw new ActivityConfigService.Failure(400,"继承扣除道具与数量必须逐项对应，用 / 分隔且最多 5 组");
    }
    private static List<Integer> list(String text,String label) {
        String[] parts = text.split("/",-1);
        if (parts.length > 5) throw new ActivityConfigService.Failure(400,label+"最多支持 5 组");
        List<Integer> result = new ArrayList<>();
        for (String part : parts) {
            if (!part.matches("[0-9]{1,10}"))
                throw new ActivityConfigService.Failure(400,label+"须为正整数，以 / 分隔，不含空项、空格或小数");
            long value = Long.parseLong(part);
            if (value < 1 || value > Integer.MAX_VALUE) throw new ActivityConfigService.Failure(400,label+"超出原生整数范围");
            result.add((int)value);
        }
        return result;
    }
    static void restoreGuard(Map<String,Object> before,Map<String,Object> after,Map<String,Object> current) {
        List<String> pair = Arrays.asList("restitution","Return_quantity");
        boolean changed = pair.stream().anyMatch(k -> !Objects.equals(before.get(k),after.get(k)));
        if (changed && pair.stream().anyMatch(k -> !Objects.equals(current.get(k),after.get(k))))
            throw new ActivityConfigService.Failure(409,"继承材料或对应数量已有其他修改，停止成对恢复");
    }
    static void requireSchema(Connection c) throws SQLException {
        List<String> names = Arrays.asList("code","restitution","Return_quantity","differ_lv"); int index = 0;
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COLUMN_NAME,DATA_TYPE,COLUMN_TYPE,CHARACTER_MAXIMUM_LENGTH,CHARACTER_SET_NAME,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='inheritance' ORDER BY ORDINAL_POSITION")) {
            while (r.next()) {
                if (index >= names.size() || !names.get(index).equals(r.getString(1)) || !"NO".equals(r.getString(6))) throw incompatible();
                if (index == 0 || index == 3) {
                    if (!"int".equals(r.getString(2)) || !r.getString(3).toLowerCase(Locale.ROOT).contains("unsigned")) throw incompatible();
                } else if (!"varchar".equals(r.getString(2)) || r.getLong(4) != 255
                    || !Arrays.asList("utf8","utf8mb3","utf8mb4").contains(r.getString(5))) throw incompatible();
                index++;
            }
        }
        if (index != names.size()) throw incompatible();
        boolean primary = false;
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT INDEX_NAME,COLUMN_NAME,SEQ_IN_INDEX,NON_UNIQUE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='inheritance'")) {
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
        try (PreparedStatement s = c.prepareStatement("SELECT (EXISTS(SELECT 1 FROM information_schema.USER_PRIVILEGES WHERE GRANTEE=? AND PRIVILEGE_TYPE='TRIGGER') OR EXISTS(SELECT 1 FROM information_schema.SCHEMA_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA IN (?,?) AND PRIVILEGE_TYPE='TRIGGER') OR EXISTS(SELECT 1 FROM information_schema.TABLE_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA='dof_native' AND TABLE_NAME='inheritance' AND PRIVILEGE_TYPE='TRIGGER'))")) {
            s.setString(1,grantee); s.setString(2,grantee); s.setString(3,"dof_native"); s.setString(4,"dof\\_native"); s.setString(5,grantee);
            try (ResultSet r=s.executeQuery()) { if (!r.next() || !r.getBoolean(1)) throw incompatible(); }
        }
        for (String sql : Arrays.asList(
            "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE EVENT_OBJECT_SCHEMA='dof_native' AND EVENT_OBJECT_TABLE='inheritance'",
            "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_NAME IS NOT NULL AND ((TABLE_SCHEMA='dof_native' AND TABLE_NAME='inheritance') OR (REFERENCED_TABLE_SCHEMA='dof_native' AND REFERENCED_TABLE_NAME='inheritance'))"))
            try (Statement s=c.createStatement(); ResultSet r=s.executeQuery(sql)) { if (!r.next() || r.getInt(1)!=0) throw incompatible(); }
    }
}
