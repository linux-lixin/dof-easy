package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native scalar text controls. No trimming, inferred defaults or gameplay execution. */
public final class NativeFeatureValues {
    public static final class Field { public String key,label,group,nativeControl,nativeName; public int index; public Integer part; }
    private static final int MAX_TEXT=65535;
    private static final Map<String,Field> FIELDS=load();
    private NativeFeatureValues() { }
    private static Map<String,Field> load() {
        try(InputStream in=NativeFeatureValues.class.getResourceAsStream("/dof/feature-values.json")) {
            List<Field> rows=new ObjectMapper().readValue(in,new TypeReference<List<Field>>(){});
            Map<String,Field> result=new LinkedHashMap<>();Set<String> indices=new HashSet<>();
            if(rows.size()!=87)throw new IllegalArgumentException("Incomplete scalar catalog");
            for(Field row:rows) {
                boolean scalar=row.part==null && row.index>=31 && row.index<=121;
                boolean composite=row.part!=null && row.index==131 && row.part>=0 && row.part<67 && row.part!=31 && row.part!=62;
                if(row.key==null || row.label==null || row.label.isEmpty() || row.group==null || (!scalar && !composite)
                    || !indices.add(row.index+":"+row.part) || result.put(row.key,row)!=null)throw new IllegalArgumentException("Invalid scalar catalog");
            }
            return Collections.unmodifiableMap(result);
        }catch(Exception e){throw new IllegalStateException("晴空文本设置映射无法加载",e);}
    }
    private static String current(NativeHotkeys keys,Field field) {
        if(keys==null || keys.size()<=field.index)return null;
        try {
            String value=keys.text(field.index);
            if(field.part==null)return value;
            String[] parts=value.split("##",-1);return parts.length>=67 ? parts[field.part] : null;
        }catch(IllegalArgumentException e){return null;}
    }
    public static List<Map<String,Object>> inspect(NativeUiState state) {
        NativeHotkeys keys=null;try {keys=NativeHotkeys.decode(state.text("Hotkeys"));}catch(IllegalArgumentException ignored){ }
        List<Map<String,Object>> result=new ArrayList<>();
        for(Field field:FIELDS.values()) {
            Map<String,Object> item=new LinkedHashMap<>();String value=current(keys,field);
            item.put("key",field.key);item.put("label",field.label);item.put("group",field.group);item.put("value",value);
            item.put("available",value!=null);item.put("maxLength",MAX_TEXT);result.add(item);
        }
        return result;
    }
    public static NativeUiState apply(NativeUiState state,Map<String,String> changes) {
        if(changes==null || changes.isEmpty() || changes.size()>FIELDS.size())throw new DofFailure(400,"请选择需要修改的文本设置");
        NativeHotkeys keys;
        try {keys=NativeHotkeys.decode(state.text("Hotkeys"));}
        catch(IllegalArgumentException e){throw new DofFailure(409,"原生设置无法读取，保留原值");}
        Map<Integer,String> patch=new LinkedHashMap<>();
        for(Map.Entry<String,String> change:changes.entrySet()) {
            Field field=FIELDS.get(change.getKey());String value=change.getValue();
            if(field==null || value==null || value.length()>MAX_TEXT || value.indexOf('*')>=0 || value.chars().anyMatch(Character::isISOControl)
                || (field.part!=null && (value.contains("##") || value.endsWith("#"))))
                throw new DofFailure(400,"设置名称或文本无效：不能包含保留分隔符、控制字符或超长文本");
            String old=current(keys,field);
            if(old==null)throw new DofFailure(409,"当前版本缺少此设置或编码异常，不能补写默认值");
            if(!old.equals(value)) {
                if(field.part==null)patch.put(field.index,value);
                else {
                    String[] parts=patch.getOrDefault(field.index,keys.text(field.index)).split("##",-1);
                    parts[field.part]=value;patch.put(field.index,String.join("##",parts));
                }
            }
        }
        if(patch.isEmpty())return state;
        try {
            Map<String,byte[]> fields=new LinkedHashMap<>();for(String key:NativeUiState.COLUMNS)fields.put(key,state.get(key));
            fields.put("Hotkeys",keys.withText(patch).encode().getBytes(StandardCharsets.US_ASCII));return new NativeUiState(fields);
        }catch(IllegalArgumentException e){throw new DofFailure(400,"文本含原工具不支持的字符或总长度超限，未修改配置");}
    }
}
