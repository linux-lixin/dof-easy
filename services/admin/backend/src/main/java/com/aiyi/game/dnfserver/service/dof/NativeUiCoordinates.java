package com.aiyi.game.dnfserver.service.dof;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Coordinates mapped from native UI load code and its control resource table. */
public final class NativeUiCoordinates {
    private static final Map<String,Integer> INDEX = new LinkedHashMap<>();
    private static final Map<String,String> LABEL = new LinkedHashMap<>();
    static {
        add("healthPercentX",27,"血量百分比 X"); add("healthPercentY",28,"血量百分比 Y");
        add("manaPercentX",29,"蓝量百分比 X"); add("manaPercentY",30,"蓝量百分比 Y");
        add("fpsX",66,"帧数 X"); add("fpsY",67,"帧数 Y");
        add("activityIconX",72,"活动图标 X"); add("activityIconY",73,"活动图标 Y");
        add("skillBar7X",75,"技能栏 7 X"); add("skillBar7Y",76,"技能栏 7 Y");
        add("skillBar8X",77,"技能栏 8 X"); add("skillBar8Y",78,"技能栏 8 Y");
    }
    private NativeUiCoordinates() { }
    private static void add(String key,int index,String label) { INDEX.put(key,index); LABEL.put(key,label); }
    public static List<Map<String,Object>> inspect(NativeUiState state) {
        List<Map<String,Object>> result = new ArrayList<>();
        NativeHotkeys decoded;
        try { decoded = NativeHotkeys.decode(state.text("Hotkeys")); }
        catch (IllegalArgumentException e) { return result; }
        for (String key : INDEX.keySet()) {
            Map<String,Object> field = new LinkedHashMap<>();
            field.put("key",key); field.put("label",LABEL.get(key)); field.put("min",-32768); field.put("max",32767);
            Integer value = null;
            try {
                if (INDEX.get(key) < decoded.size()) {
                    String raw = decoded.text(INDEX.get(key));
                    if (raw.matches("-?[0-9]{1,10}")) value = Integer.valueOf(raw);
                }
            } catch (IllegalArgumentException e) { /* Show unsupported native values without normalizing them. */ }
            field.put("available",value != null && value >= -32768 && value <= 32767);
            field.put("value",value); result.add(field);
        }
        return result;
    }
    public static NativeUiState apply(NativeUiState state, Map<String,Integer> edits) {
        if (edits == null || edits.isEmpty() || edits.size() > INDEX.size()) throw new DofFailure(400,"请选择需要修改的界面坐标");
        Map<Integer,String> patch = new LinkedHashMap<>();
        for (Map.Entry<String,Integer> edit : edits.entrySet()) {
            Integer index = INDEX.get(edit.getKey()), value = edit.getValue();
            if (index == null || value == null || value < -32768 || value > 32767)
                throw new DofFailure(400,"界面坐标名称或数值无效");
            patch.put(index,value.toString());
        }
        try {
            NativeHotkeys decoded = NativeHotkeys.decode(state.text("Hotkeys"));
            // Missing extension fields must not be synthesized or initialized implicitly.
            NativeHotkeys changed = decoded.withText(patch);
            Map<String,byte[]> fields = new LinkedHashMap<>();
            for (String key : NativeUiState.COLUMNS) fields.put(key,state.get(key));
            fields.put("Hotkeys",changed.encode().getBytes(StandardCharsets.US_ASCII));
            return new NativeUiState(fields);
        } catch (IllegalArgumentException e) { throw new DofFailure(409,"原生界面配置无法无损修改，已停止保存"); }
    }
}
