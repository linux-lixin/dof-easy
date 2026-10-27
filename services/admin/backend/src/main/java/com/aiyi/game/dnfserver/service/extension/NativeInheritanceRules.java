package com.aiyi.game.dnfserver.service.extension;

import java.sql.*;
import java.util.*;

/** The pinned native reader uses SELECT *, ordinal fields and slash-separated cost pairs.
 * This adapter edits rules only; the native game operation remains the sole executor.
 */
final class NativeInheritanceRules {
    private NativeInheritanceRules() { }
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
        NativeEquipmentRuleSchema.requireSchema(c,"inheritance",Arrays.asList("code","restitution","Return_quantity","differ_lv"),new HashSet<>(Arrays.asList("code","differ_lv")));
    }
}
