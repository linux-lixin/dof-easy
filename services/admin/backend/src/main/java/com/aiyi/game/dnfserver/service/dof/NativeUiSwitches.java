package com.aiyi.game.dnfserver.service.dof;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** UI-only controls with native read mappings; missing/unknown values stay untouched. */
public final class NativeUiSwitches {
    private static final Map<String,Integer> INDEX = new LinkedHashMap<>();
    private static final Map<String,String> LABEL = new LinkedHashMap<>();
    static {
        add("healthPercent",20,"血量百分比"); add("chatHistory",26,"聊天记录");
        add("cooldownNumbers",51,"冷却数字"); add("damageStatistics",55,"DPS 显示");
        add("goldUnit",56,"金币 G 显示"); add("codeButton",60,"口令码按钮");
        add("roundMinimap",69,"圆形地图"); add("animatedActivityIcon",70,"动态活动图标");
        add("channelColor",71,"频道颜色"); add("fourteenSkills",74,"十四技能栏");
        add("centerWidescreen",89,"宽屏居中"); add("enhancementLevel",99,"装备强化等级显示");
        add("minimizeButton",101,"最小化游戏按钮"); add("gameLogo",102,"游戏 LOGO");
        add("skillTransparency",125,"技能透明"); add("equipmentBuffIcons",126,"装备 BUFF 图标");
        LABEL.put("bloodLayout", "血槽 UI 调整");
        LABEL.put("createRoleLayout", "创建角色 UI 调整");
    }
    private NativeUiSwitches() { }
    private static void add(String key, int index, String label) { INDEX.put(key,index); LABEL.put(key,label); }
    private static Boolean layoutBool(String value) {
        return "0".equals(value) ? Boolean.FALSE : "1".equals(value) ? Boolean.TRUE : null;
    }
    private static Boolean hotkeyBool(String value) {
        // Older native samples contain literal 0 for newly appended switches.
        return "假".equals(value) || "0".equals(value) ? Boolean.FALSE : "真".equals(value) ? Boolean.TRUE : null;
    }
    private static String[] layout(NativeUiState state) {
        String raw = state.text("temp_2");
        return raw == null ? new String[0] : raw.split("##", -1);
    }
    public static List<Map<String,Object>> inspect(NativeUiState state) {
        NativeHotkeys hotkeys = null;
        try { hotkeys = NativeHotkeys.decode(state.text("Hotkeys")); } catch (IllegalArgumentException ignored) { }
        String[] layout;
        try { layout = layout(state); } catch (IllegalArgumentException ignored) { layout = new String[0]; }
        List<Map<String,Object>> out = new ArrayList<>();
        for (String key : LABEL.keySet()) {
            Boolean value = null;
            try {
                Integer index = INDEX.get(key);
                if (index != null && hotkeys != null && index < hotkeys.size()) value = hotkeyBool(hotkeys.text(index));
                if (index == null && layout.length >= 2) value = layoutBool(layout["bloodLayout".equals(key) ? 0 : 1]);
            } catch (IllegalArgumentException ignored) { }
            Map<String,Object> field = new LinkedHashMap<>();
            field.put("key",key); field.put("label",LABEL.get(key)); field.put("value",value);
            field.put("available",value != null); out.add(field);
        }
        return out;
    }
    public static NativeUiState apply(NativeUiState state, Map<String,Boolean> edits) {
        if (edits == null || edits.isEmpty() || edits.size() > LABEL.size())
            throw new DofFailure(400,"请选择需要修改的界面开关");
        Map<String,Boolean> available = new HashMap<>();
        Map<String,Boolean> current = new HashMap<>();
        for (Map<String,Object> field : inspect(state)) {
            available.put((String)field.get("key"),(Boolean)field.get("available"));
            current.put((String)field.get("key"),(Boolean)field.get("value"));
        }
        Map<Integer,String> hotkeyPatch = new LinkedHashMap<>();
        Map<String,byte[]> fields = new LinkedHashMap<>();
        for (String key : NativeUiState.COLUMNS) fields.put(key,state.get(key));
        try {
            String[] layout = null;
            for (Map.Entry<String,Boolean> edit : edits.entrySet()) {
                String key = edit.getKey();
                if (!LABEL.containsKey(key) || edit.getValue() == null) throw new DofFailure(400,"界面开关名称或数值无效");
                if (!Boolean.TRUE.equals(available.get(key))) throw new DofFailure(409,"原生配置缺少此开关或数值无法识别，保留原值");
                if (Objects.equals(current.get(key),edit.getValue())) continue;
                if (INDEX.containsKey(key)) hotkeyPatch.put(INDEX.get(key),edit.getValue() ? "真" : "假");
                else {
                    if (layout == null) layout = layout(state);
                    layout["bloodLayout".equals(key) ? 0 : 1] = edit.getValue() ? "1" : "0";
                }
            }
            if (!hotkeyPatch.isEmpty()) fields.put("Hotkeys", NativeHotkeys.decode(state.text("Hotkeys")).withText(hotkeyPatch).encode().getBytes(StandardCharsets.US_ASCII));
            if (layout != null) fields.put("temp_2",String.join("##",layout).getBytes(StandardCharsets.UTF_8));
            return new NativeUiState(fields);
        } catch (IllegalArgumentException e) { throw new DofFailure(409,"原生界面配置无法无损修改，已停止保存"); }
    }
}
