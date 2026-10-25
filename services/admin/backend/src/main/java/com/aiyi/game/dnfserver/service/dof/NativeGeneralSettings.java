package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Version-pinned native configuration. This class never runs rewards or changes identity. */
public final class NativeGeneralSettings {
    public static final class Field {
        public final String key,label,module,column,kind;
        public final int min,max,index;
        Field(String key,String label,String module,String column,String kind,int min,int max,int index) {
            this.key=key;this.label=label;this.module=module;this.column=column;this.kind=kind;this.min=min;this.max=max;this.index=index;
        }
    }
    static final List<String> COLUMNS=Collections.unmodifiableList(Arrays.asList(
        "billing.ip_reg_control","billing.regnumber","billing.regtype","config.is_open_up_pwd","config.switch_regCdkey",
        "billing.bubbleCount","billing.bubbleTime","billing.bubbleType","config.Switch"));
    static final Map<String,Field> FIELDS=fields();
    private static Map<String,Field> fields() {
        Map<String,Field> out=new LinkedHashMap<>();
        add(out,"ipLimit","每个 IP 注册限制","registration",COLUMNS.get(0),"integer",Integer.MIN_VALUE,Integer.MAX_VALUE,-1);
        add(out,"registrationGift","注册赠送数量","registration",COLUMNS.get(1),"integer",0,Integer.MAX_VALUE,-1);
        add(out,"registrationCurrency","注册赠送类型","registration",COLUMNS.get(2),"currency",0,1,-1);
        add(out,"passwordChange","允许修改密码","registration",COLUMNS.get(3),"boolean",0,1,-1);
        add(out,"activationCode","注册激活码","registration",COLUMNS.get(4),"boolean",0,1,-1);
        add(out,"rewardAmount","泡点奖励数量","online-reward",COLUMNS.get(5),"integer",0,Integer.MAX_VALUE,-1);
        add(out,"rewardInterval","泡点间隔（秒）","online-reward",COLUMNS.get(6),"integer",Integer.MIN_VALUE,Integer.MAX_VALUE,-1);
        add(out,"rewardCurrency","泡点奖励类型","online-reward",COLUMNS.get(7),"currency",1,2,-1);
        try(java.io.InputStream in=NativeGeneralSettings.class.getResourceAsStream("/dof/general-switches.json")) {
            List<Map<String,Object>> rows=new ObjectMapper().readValue(in,new TypeReference<List<Map<String,Object>>>(){});
            if(rows.size()!=40)throw new IllegalArgumentException("Incomplete native Switch catalog");
            for(int i=0;i<rows.size();i++) {
                Map<String,Object> row=rows.get(i);
                if(((Number)row.get("index")).intValue()!=i)throw new IllegalArgumentException("Switch order differs");
                add(out,"shortcut-"+i,row.get("label").toString(),"shortcuts",COLUMNS.get(8),"boolean",0,1,i);
            }
        } catch(Exception e) {throw new IllegalStateException("原生快捷功能对应表无法加载",e);}
        return Collections.unmodifiableMap(out);
    }
    private static void add(Map<String,Field> out,String key,String label,String module,String column,String kind,int min,int max,int index) {
        out.put(key,new Field(key,label,module,column,kind,min,max,index));
    }
    static void module(String value) {
        if(!Arrays.asList("registration","online-reward","shortcuts").contains(value))throw new DofFailure(404,"管理模块不存在");
    }
    static String raw(Map<String,String> state,String key) {
        String encoded=state.get(key);if(encoded==null)return null;
        try {return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(Base64.getDecoder().decode(encoded))).toString();}
        catch(Exception e) {throw new DofFailure(409,"原生设置编码无法无损转换");}
    }
    private static String[] switches(Map<String,String> state) {
        String raw=raw(state,COLUMNS.get(8));return raw==null?new String[0]:raw.split("\\*",-1);
    }
    static Object value(Map<String,String> state,Field field) {
        try {
            String text;
            if(field.index>=0) {
                String[] parts=switches(state);if(parts.length<40 || field.index>=parts.length)return null;text=parts[field.index];
                return "真".equals(text)?Boolean.TRUE:("假".equals(text)||"0".equals(text)?Boolean.FALSE:null);
            }
            text=raw(state,field.column);if(text==null || !text.matches("-?[0-9]{1,10}"))return null;
            long number=Long.parseLong(text);if(number<field.min || number>field.max)return null;
            return "boolean".equals(field.kind)?(Object)(number==1):number;
        } catch(IllegalArgumentException|DofFailure e) {return null;}
    }
    static Map<String,String> apply(String module,Map<String,String> before,Map<String,JsonNode> changes) {
        module(module);
        if(changes==null || changes.isEmpty() || changes.size()>FIELDS.size())throw new DofFailure(400,"请选择需要修改的设置");
        Map<String,String> out=new LinkedHashMap<>(before);String[] parts=null;
        for(Map.Entry<String,JsonNode> entry:changes.entrySet()) {
            Field f=FIELDS.get(entry.getKey());JsonNode node=entry.getValue();
            if(f==null || !f.module.equals(module) || node==null || node.isNull())throw new DofFailure(400,"包含未知字段、其他模块字段或空值");
            Object current=value(before,f);if(current==null)throw new DofFailure(409,"原配置缺失或无法识别，停止覆盖："+f.label);
            String encoded;
            if("boolean".equals(f.kind)) {
                if(!node.isBoolean())throw new DofFailure(400,f.label+"必须是开启或关闭");
                if(current.equals(node.booleanValue()))continue;
                encoded=f.index>=0?(node.booleanValue()?"真":"假"):(node.booleanValue()?"1":"0");
            } else {
                if(!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue()<f.min || node.intValue()>f.max)
                    throw new DofFailure(400,f.label+"必须是允许范围内的整数");
                if(((Number)current).longValue()==node.longValue())continue;
                encoded=node.asText();
            }
            if(f.index>=0) {if(parts==null)parts=switches(before);parts[f.index]=encoded;}
            else out.put(f.column,Base64.getEncoder().encodeToString(encoded.getBytes(StandardCharsets.UTF_8)));
        }
        if(parts!=null)out.put(COLUMNS.get(8),Base64.getEncoder().encodeToString(String.join("*",parts).getBytes(StandardCharsets.UTF_8)));
        return out;
    }
    static List<Map<String,Object>> inspect(String module,Map<String,String> state) {
        module(module);List<Map<String,Object>> result=new ArrayList<>();
        for(Field f:FIELDS.values())if(f.module.equals(module)) {
            Map<String,Object> row=new LinkedHashMap<>();Object value=value(state,f);
            row.put("key",f.key);row.put("label",f.label);row.put("kind",f.kind);row.put("min",f.min);row.put("max",f.max);row.put("value",value);row.put("available",value!=null);
            if("ipLimit".equals(f.key))row.put("hint","0 关闭注册；负数不限制每个 IP 的数量；正数为每个 IP 的账号上限。激活码限制独立保留。");
            if("rewardInterval".equals(f.key))row.put("hint","QK 原生循环以秒等待；非正数按 60 秒等待，修改不会新增网页奖励调度。");
            if("rewardAmount".equals(f.key))row.put("hint","0 停止泡点奖励；正数由 QK 原有流程发放。");
            if("currency".equals(f.kind)) {
                List<Map<String,Object>> options=new ArrayList<>();
                for(int i=0;i<2;i++){Map<String,Object> option=new LinkedHashMap<>();option.put("label",i==0?"点券":"代币");option.put("value",i==0?1:("registrationCurrency".equals(f.key)?0:2));options.add(option);}
                row.put("options",options);
            }
            result.add(row);
        }
        return result;
    }
}
