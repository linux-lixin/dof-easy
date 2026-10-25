package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native configuration only; does not implement gameplay or invoke rewards/schedulers. */
public final class NativeFeatureSwitches {
    public static final class Field {
        public String key,label,nativeControl,nativeName;
        public int index;
        public boolean writable;
    }
    private static final Map<String,Field> FIELDS=load();
    private NativeFeatureSwitches() { }
    private static Map<String,Field> load() {
        try(InputStream in=NativeFeatureSwitches.class.getResourceAsStream("/dof/feature-switches.json")) {
            List<Field> rows=new ObjectMapper().readValue(in,new TypeReference<List<Field>>(){});
            Map<String,Field> fields=new LinkedHashMap<>();Set<Integer> indices=new HashSet<>();
            if(rows.size()!=70)throw new IllegalArgumentException("Incomplete checkbox catalog");
            for(Field row:rows) {
                if(row.key==null || row.label==null || row.label.isEmpty() || row.index<10 || row.index>136
                    || !indices.add(row.index) || fields.put(row.key,row)!=null)throw new IllegalArgumentException("Invalid checkbox catalog");
            }
            return Collections.unmodifiableMap(fields);
        } catch(Exception e) {throw new IllegalStateException("晴空原生开关映射无法加载",e);}
    }
    private static Boolean value(NativeHotkeys keys,Field field) {
        if(keys==null || keys.size()<=field.index)return null;
        try {
            String raw=keys.text(field.index);
            return "真".equals(raw)?Boolean.TRUE:("假".equals(raw)||"0".equals(raw)?Boolean.FALSE:null);
        } catch(IllegalArgumentException e) {return null;}
    }
    public static List<Map<String,Object>> inspect(NativeUiState state) {
        NativeHotkeys keys=null;
        try {keys=NativeHotkeys.decode(state.text("Hotkeys"));}catch(IllegalArgumentException ignored) { }
        List<Map<String,Object>> result=new ArrayList<>();
        for(Field field:FIELDS.values()) {
            Map<String,Object> item=new LinkedHashMap<>();Boolean current=value(keys,field);
            item.put("key",field.key);item.put("label",field.label);item.put("value",current);item.put("available",current!=null);
            item.put("editable",field.writable && current!=null);
            if(!field.writable)item.put("blockedReason","原工具此开关的独立保存行为仍待核对");
            result.add(item);
        }
        return result;
    }
    public static NativeUiState apply(NativeUiState state,Map<String,Boolean> changes) {
        if(changes==null || changes.isEmpty() || changes.size()>FIELDS.size())throw new DofFailure(400,"请选择需要修改的功能开关");
        try {
            NativeHotkeys keys=NativeHotkeys.decode(state.text("Hotkeys"));Map<Integer,String> patch=new LinkedHashMap<>();
            for(Map.Entry<String,Boolean> change:changes.entrySet()) {
                Field field=FIELDS.get(change.getKey());
                if(field==null || change.getValue()==null)throw new DofFailure(400,"功能开关名称或数值无效");
                if(!field.writable)throw new DofFailure(409,"此功能的原生保存行为尚未核实，停止修改");
                Boolean current=value(keys,field);
                if(current==null)throw new DofFailure(409,"原生配置缺少此开关或值无法识别，保留原值");
                if(!current.equals(change.getValue()))patch.put(field.index,change.getValue()?"真":"假");
            }
            if(patch.isEmpty())return state;
            Map<String,byte[]> fields=new LinkedHashMap<>();for(String key:NativeUiState.COLUMNS)fields.put(key,state.get(key));
            fields.put("Hotkeys",keys.withText(patch).encode().getBytes(StandardCharsets.US_ASCII));return new NativeUiState(fields);
        } catch(IllegalArgumentException e) {throw new DofFailure(409,"原生功能配置无法无损修改，停止保存");}
    }
}
