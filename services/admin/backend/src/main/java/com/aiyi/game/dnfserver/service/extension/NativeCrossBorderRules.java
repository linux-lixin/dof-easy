package com.aiyi.game.dnfserver.service.extension;

import java.sql.*;
import java.util.*;

/** Fixed native configuration; the plugin remains the only cross-character/item executor. */
final class NativeCrossBorderRules {
    private NativeCrossBorderRules() { }
    // The native loop checks each material's inventory count against codenum,
    // then independently deducts Return_quantity. Preserve that distinction.
    private static final List<String> MATERIALS = Arrays.asList("restitution","Return_quantity","codenum");
    static void validate(Map<String,Object> values,Map<String,Object> before) {
        if (before!=null && Objects.equals(values.get("restitution"),before.get("restitution"))
            && Objects.equals(values.get("Return_quantity"),before.get("Return_quantity"))) return;
        String items=values.get("restitution").toString(),counts=values.get("Return_quantity").toString();
        if (items.isEmpty() && counts.isEmpty()) return;
        String[] ids=items.split("/",-1),quantities=counts.split("/",-1);
        if (ids.length!=quantities.length || ids.length>5)
            throw new ActivityConfigService.Failure(400,"跨界材料代码和数量必须逐项对应，用 / 分隔且最多 5 组");
        for (int i=0;i<ids.length;i++) {positive(ids[i],"跨界材料代码");positive(quantities[i],"跨界扣除数量");}
    }
    private static void positive(String value,String label) {
        if (!value.matches("[0-9]{1,10}")) throw new ActivityConfigService.Failure(400,label+"必须为正整数，以 / 分隔，不含空项或空格");
        long n=Long.parseLong(value);if (n<1 || n>Integer.MAX_VALUE) throw new ActivityConfigService.Failure(400,label+"超出原生整数范围");
    }
    static void restoreGuard(Map<String,Object> before,Map<String,Object> after,Map<String,Object> current) {
        if (MATERIALS.stream().anyMatch(k->!Objects.equals(String.valueOf(before.get(k)),String.valueOf(after.get(k))))
            && MATERIALS.stream().anyMatch(k->!Objects.equals(String.valueOf(current.get(k)),String.valueOf(after.get(k)))))
            throw new ActivityConfigService.Failure(409,"跨界材料、数量或持有校验下限已有其他修改，停止成组恢复");
    }
    static void requireSchema(Connection c) throws SQLException {
        NativeEquipmentRuleSchema.requireSchema(c,"cross_border",Arrays.asList("code","restitution","Return_quantity","announcement","codenum"),new HashSet<>(Arrays.asList("code","announcement","codenum")));
    }
}
