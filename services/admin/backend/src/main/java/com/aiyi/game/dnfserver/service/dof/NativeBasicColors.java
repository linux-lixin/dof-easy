package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Basic_color only. Storage/activation are separate; never rewrites colour/flash/list settings. */
public final class NativeBasicColors {
    private static final String[] KEYS={"npcName","characterName","otherName","bubblePrimary",null,null,
        "customNames","channel","hintPrimary","bubbleSecondary","hintSecondary"};
    private static final String[] LABELS={"NPC 名称颜色","角色名称颜色","他人名称颜色","泡点主颜色",null,null,
        "自定义名称颜色","频道颜色","内辅提示主颜色","泡点副颜色","内辅提示副颜色"};
    private static String[] parts(String source) {
        if(source==null || source.length()>8192)throw new DofFailure(409,"基础颜色配置缺失或超过读取上限");
        String[] parts=source.split("\\*\\*\\*",-1);
        // The original loader explicitly requires eleven fields. Unknown shapes
        // must not be normalized into a version the consumer silently ignores.
        if(parts.length!=11)throw new DofFailure(409,"基础颜色格式不匹配，保留原配置");
        return parts;
    }
    private static Object value(String[] parts,int index) {
        String raw=parts[index];
        if(index==6)return "1".equals(raw)?Boolean.TRUE:"0".equals(raw)?Boolean.FALSE:null;
        return raw.matches("[0-9a-fA-F]{6}")?"#"+raw.toUpperCase(Locale.ROOT):null;
    }
    public static List<Map<String,Object>> inspect(String source) {
        String[] parts=parts(source);List<Map<String,Object>> out=new ArrayList<>();
        for(int i=0;i<KEYS.length;i++)if(KEYS[i]!=null) {
            Map<String,Object> field=new LinkedHashMap<>();Object current=value(parts,i);
            field.put("key",KEYS[i]);field.put("label",LABELS[i]);field.put("kind",i==6?"boolean":"color");
            field.put("value",current);field.put("available",current!=null);out.add(field);
        }
        return out;
    }
    public static String edit(String source,Map<String,JsonNode> changes) {
        String[] parts=parts(source);
        if(changes==null || changes.isEmpty() || changes.size()>9)throw new DofFailure(400,"请选择需要修改的基础颜色");
        for(Map.Entry<String,JsonNode> entry:changes.entrySet()) {
            int index=Arrays.asList(KEYS).indexOf(entry.getKey());JsonNode node=entry.getValue();
            if(entry.getKey()==null || index<0 || node==null || node.isNull())throw new DofFailure(400,"未知基础颜色或空值");
            Object current=value(parts,index);if(current==null)throw new DofFailure(409,"原颜色无法识别，停止覆盖");
            if(index==6) {
                if(!node.isBoolean())throw new DofFailure(400,"名称颜色开关必须为开启或关闭");
                if(!current.equals(node.booleanValue()))parts[index]=node.booleanValue()?"1":"0";
            } else {
                if(!node.isTextual() || !node.textValue().matches("#[0-9a-fA-F]{6}"))throw new DofFailure(400,"颜色必须是 #RRGGBB 格式");
                String requested=node.textValue().toUpperCase(Locale.ROOT);
                if(!current.equals(requested))parts[index]=requested.substring(1);
            }
        }
        // Slots 4/5 are not exposed. Even when their native meaning is unknown,
        // their original text survives every edit and semantic no-op.
        return String.join("***",parts);
    }
}
