package com.aiyi.game.dnfserver.service.extension;

import java.sql.*;
import java.util.*;

/** Configuration only. The pinned native component remains the sole item/reward executor. */
final class NativeRecoveryRules {
    private NativeRecoveryRules() { }
    private static final List<List<String>> PAIRS = Arrays.asList(
        Arrays.asList("restitution","Return_quantity"),
        Arrays.asList("extra_consumption_id","extra_consumption_num"));
    static void validate(Map<String,Object> values,Map<String,Object> before) {
        validatePair(values,before,PAIRS.get(0),false,"回收奖励");
        validatePair(values,before,PAIRS.get(1),true,"额外消耗");
        if (changed(values,before,"codenum") && Long.parseLong(values.get("codenum").toString()) < 1)
            throw new ActivityConfigService.Failure(400,"回收扣除数量必须大于 0");
        if (changed(values,before,"announcement") && Long.parseLong(values.get("announcement").toString()) > 1)
            throw new ActivityConfigService.Failure(400,"是否播报只能为 0 或 1");
    }
    private static boolean changed(Map<String,Object> values,Map<String,Object> before,String field) {
        return before==null || !Objects.equals(String.valueOf(values.get(field)),String.valueOf(before.get(field)));
    }
    private static void validatePair(Map<String,Object> values,Map<String,Object> before,List<String> fields,boolean gold,String label) {
        if (before!=null && fields.stream().noneMatch(k->changed(values,before,k))) return;
        String ids=values.get(fields.get(0)).toString(),counts=values.get(fields.get(1)).toString();
        if (ids.isEmpty() && counts.isEmpty()) return;
        String[] items=ids.split("/",-1),quantities=counts.split("/",-1);
        if (items.length!=quantities.length || items.length>7)
            throw new ActivityConfigService.Failure(400,label+"代码和数量必须逐项对应，用 / 分隔且最多 7 组");
        for (int i=0;i<items.length;i++) {
            integer(items[i],gold?0:1,label+"代码");integer(quantities[i],1,label+"数量");
        }
    }
    private static void integer(String value,int min,String label) {
        if (!value.matches("[0-9]{1,10}")) throw new ActivityConfigService.Failure(400,label+"必须为原生整数，以 / 分隔，不含空项或空格");
        long number=Long.parseLong(value);
        if (number<min || number>Integer.MAX_VALUE) throw new ActivityConfigService.Failure(400,label+"超出允许范围");
    }
    static void restoreGuard(Map<String,Object> before,Map<String,Object> after,Map<String,Object> current) {
        for (List<String> fields:PAIRS) {
            if (fields.stream().anyMatch(k->!Objects.equals(before.get(k),after.get(k)))
                && fields.stream().anyMatch(k->!Objects.equals(current.get(k),after.get(k))))
                throw new ActivityConfigService.Failure(409,"回收奖励或额外消耗的配对字段已有其他修改，停止成对恢复");
        }
    }
    static void requireSchema(Connection c) throws SQLException {
        NativeEquipmentRuleSchema.requireSchema(c,"item_recovery",Arrays.asList("code","restitution","Return_quantity","announcement","codenum","extra_consumption_id","extra_consumption_num"),new HashSet<>(Arrays.asList("code","announcement","codenum")));
    }
}
