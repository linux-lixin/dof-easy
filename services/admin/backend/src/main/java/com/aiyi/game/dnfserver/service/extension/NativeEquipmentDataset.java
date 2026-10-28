package com.aiyi.game.dnfserver.service.extension;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Streaming web format. Structural integrity is not a native write plan or tool-format parity. */
final class NativeEquipmentDataset {
    static final String FORMAT="dof-equipment-rules-v1";
    static final long MAX_UPLOAD=256L*1024*1024;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Set<String> KEYS=new HashSet<>(Arrays.asList("format","schema","section","instanceId","databaseUuid","fields","rows","rowCount","revision"));
    private NativeEquipmentDataset() { }
    static final class Spec {
        final ActivityConfigService.Section section; final String instance,database;
        Spec(ActivityConfigService.Section section,String instance,String database){this.section=section;this.instance=instance;this.database=database;}
        Map<String,Object> header(){Map<String,Object> out=new LinkedHashMap<>();out.put("format",FORMAT);out.put("schema",1);out.put("section",section.key);out.put("instanceId",instance);out.put("databaseUuid",database);List<String> names=new ArrayList<>();for(ActivityConfigService.Field f:section.fields)names.add(f.name);out.put("fields",names);return out;}
        Map<String,Object> canonical(Map<String,Object> row){
            Set<String> names=new HashSet<>();for(ActivityConfigService.Field f:section.fields)names.add(f.name);
            if(row==null || !names.equals(row.keySet()))throw invalid("文件规则字段不匹配");
            Map<String,Object> out=new LinkedHashMap<>();
            for(ActivityConfigService.Field field:section.fields){Object value=row.get(field.name);
                if("int".equals(field.type)){
                    if(!(value instanceof Integer||value instanceof Long)||((Number)value).longValue()<0||((Number)value).longValue()>4294967295L)throw invalid("文件整数不是原生 unsigned INT");
                    out.put(field.name,((Number)value).longValue());
                }else {if(!(value instanceof String)||((String)value).codePointCount(0,((String)value).length())>255)throw invalid("文件文本字段不匹配原生 VARCHAR255");out.put(field.name,value);}
            }
            return out;
        }
    }
    interface Rows { Map<String,Object> next() throws Exception; }
    static ActivityConfigService.Failure invalid(String text){return new ActivityConfigService.Failure(400,text);}
    static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static void append(MessageDigest d,Object value)throws IOException{d.update(JSON.writeValueAsBytes(value));d.update((byte)'\n');}
    static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format("%02x",v&255));return b.toString();}
    static Map<String,Object> write(Spec spec,Rows rows,OutputStream output)throws Exception{
        MessageDigest d=digest();Map<String,Object> header=spec.header();append(d,header);long count=0,previous=-1;
        JsonGenerator g=JSON.getFactory().createGenerator(output);g.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        try{g.writeStartObject();for(Map.Entry<String,Object> v:header.entrySet()){g.writeFieldName(v.getKey());g.writeObject(v.getValue());}g.writeArrayFieldStart("rows");Map<String,Object> row;
            while((row=rows.next())!=null){Map<String,Object> next=spec.canonical(row);long id=((Number)next.get("code")).longValue();if(id<=previous)throw invalid("文件规则代码必须唯一并递增");previous=id;append(d,next);g.writeObject(next);count++;}
            g.writeEndArray();String revision=hex(d.digest());g.writeNumberField("rowCount",count);g.writeStringField("revision",revision);g.writeEndObject();g.flush();Map<String,Object> result=new LinkedHashMap<>();result.put("rowCount",count);result.put("revision",revision);return result;
        }finally{g.close();}
    }
    interface RowConsumer {void accept(Map<String,Object> row)throws IOException;}
    static Map<String,Object> inspect(Spec spec,InputStream input)throws IOException{return inspect(spec,input,row->{});}
    static Map<String,Object> inspect(Spec spec,InputStream input,RowConsumer consumer)throws IOException{
        MessageDigest d=digest();append(d,spec.header());Set<String> seen=new HashSet<>();Map<String,Object> header=new LinkedHashMap<>();long count=0,previous=-1;Object declaredCount=null,declaredRevision=null;
        InputStream bounded=new FilterInputStream(input){
            long bytes;int tokenBytes,depth;boolean string,escape;
            private void accepted(int value){if(value<0)return;if(++bytes>MAX_UPLOAD)throw new ActivityConfigService.Failure(413,"规则文件超过256 MiB上限");
                if(string){if(++tokenBytes>4096)throw invalid("规则文件单个文本过长");if(escape)escape=false;else if(value=='\\')escape=true;else if(value=='"'){string=false;tokenBytes=0;}}
                else if(value=='"'){string=true;tokenBytes=0;}else if(value=='{'||value=='['){if(++depth>8)throw invalid("规则文件不接受嵌套对象");tokenBytes=0;}else if(value=='}'||value==']'){depth--;tokenBytes=0;}else if(value==','||value==':'||value<=32)tokenBytes=0;else if(++tokenBytes>128)throw invalid("规则文件标量过长");
            }
            @Override public int read()throws IOException{int v=in.read();accepted(v);return v;}
            @Override public int read(byte[] b,int o,int n)throws IOException{int size=in.read(b,o,n);for(int i=0;i<size;i++)accepted(b[o+i]&255);return size;}
        };
        try(JsonParser p=JSON.getFactory().createParser(bounded)){
            p.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            if(p.nextToken()!=JsonToken.START_OBJECT)throw invalid("规则文件必须是完整的网页格式对象");
            while(p.nextToken()!=JsonToken.END_OBJECT){
                if(p.currentToken()!=JsonToken.FIELD_NAME)throw invalid("规则文件不完整");String key=p.getCurrentName();if(!KEYS.contains(key)||!seen.add(key))throw invalid("规则文件有未知或重复字段");JsonToken token=p.nextToken();
                if(key.equals("rows")){
                    if(token!=JsonToken.START_ARRAY)throw invalid("规则行必须是数组");
                    while(p.nextToken()!=JsonToken.END_ARRAY){if(p.currentToken()!=JsonToken.START_OBJECT)throw invalid("规则行必须是对象");Map<String,Object> raw=new LinkedHashMap<>();while(p.nextToken()!=JsonToken.END_OBJECT){if(p.currentToken()!=JsonToken.FIELD_NAME||raw.size()>=16)throw invalid("规则行字段无效");String name=p.getCurrentName();JsonToken scalar=p.nextToken();Object value;if(scalar==JsonToken.VALUE_STRING)value=p.getText();else if(scalar==JsonToken.VALUE_NUMBER_INT){try{value=p.getLongValue();}catch(JsonProcessingException bad){throw invalid("规则整数超出范围");}}else throw invalid("规则行只接受标量字段");if(raw.put(name,value)!=null)throw invalid("规则行字段重复");}Map<String,Object> row=spec.canonical(raw);long id=((Number)row.get("code")).longValue();if(id<=previous)throw invalid("文件规则代码必须唯一并递增");previous=id;append(d,row);consumer.accept(row);count++;}
                }else if(key.equals("fields")){
                    if(token!=JsonToken.START_ARRAY)throw invalid("规则列目录无效");List<String> fields=new ArrayList<>();while(p.nextToken()!=JsonToken.END_ARRAY){if(p.currentToken()!=JsonToken.VALUE_STRING||fields.size()>=16)throw invalid("规则列目录无效");fields.add(p.getText());}header.put(key,fields);
                }else if(key.equals("schema")||key.equals("rowCount")){
                    if(token!=JsonToken.VALUE_NUMBER_INT||!p.getNumberType().equals(JsonParser.NumberType.INT)&&!p.getNumberType().equals(JsonParser.NumberType.LONG))throw invalid("规则文件计数或版本无效");long n=p.getLongValue();if(key.equals("schema"))header.put(key,n);else declaredCount=n;
                }else {if(token!=JsonToken.VALUE_STRING||p.getTextLength()>128)throw invalid("规则文件元数据无效");if(key.equals("revision"))declaredRevision=p.getText();else header.put(key,p.getText());}
            }
            if(p.nextToken()!=null||!seen.equals(KEYS))throw invalid("规则文件缺少字段或包含尾随内容");
        }catch(JsonProcessingException e){throw invalid("规则文件不是有效的完整JSON或包含重复字段");}
        Map<String,Object> expected=spec.header();expected.put("schema",1L);if(!header.equals(expected))throw new ActivityConfigService.Failure(409,"规则文件格式、模块、实例或数据库UUID不匹配");
        String revision=hex(d.digest());if(!Objects.equals(declaredCount,count)||!Objects.equals(declaredRevision,revision))throw new ActivityConfigService.Failure(409,"规则文件行数或摘要不匹配，可能损坏或未完整下载");
        Map<String,Object> result=new LinkedHashMap<>();result.putAll(spec.header());result.put("rowCount",count);result.put("revision",revision);result.put("integrityVerified",true);result.put("nativeWrites",false);result.put("writePlanCreated",false);result.put("notice","仅验证网页规则文件的完整性和绑定；尚未生成批量写入计划，也未保存配置。原工具导入格式仍待对照。");return result;
    }
}
