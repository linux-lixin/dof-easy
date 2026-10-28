package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

/** Edits only named replacement-edition plugin fields; preserves unedited bytes. */
public final class NativePluginConfig {
    public static class Field { public String key, label, kind; public int arity; }
    public static class Module { public String id, title, file; public List<Field> fields; }
    public static class Catalog { public int schema; public String edition; public List<Module> modules; }
    private static final Catalog CATALOG;
    private static final Pattern KEY=Pattern.compile("(?m)^\\uFEFF?([A-Za-z_][A-Za-z0-9_]*)[ \\t]*=[ \\t]*");
    static {
        try(InputStream in=NativePluginConfig.class.getResourceAsStream("/dof/plugin-files.json")) {
            CATALOG=new ObjectMapper().readValue(in,Catalog.class);
        } catch(IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private NativePluginConfig() {}
    public static List<Module> modules() { return Collections.unmodifiableList(CATALOG.modules); }
    public static Module module(String id) {
        return modules().stream().filter(m->m.id.equals(id)).findFirst()
            .orElseThrow(()->new DofFailure(404,"未知插件配置"));
    }
    private static final class Value {
        final int start,end; final String text;
        Value(int start,int end,String text){this.start=start;this.end=end;this.text=text;}
    }
    public static final class Document {
        final String text; final Charset encoding; final Map<String,Value> values;
        Document(String text,Charset encoding,Map<String,Value> values){this.text=text;this.encoding=encoding;this.values=values;}
        public Map<String,String> values() {
            Map<String,String> result=new LinkedHashMap<>();values.forEach((k,v)->result.put(k,v.text));return result;
        }
    }
    public static Document parse(byte[] bytes) {
        if(bytes.length>128*1024 || bytes.length==0)throw new DofFailure(409,"插件配置为空或超过 128 KiB");
        for(Charset charset:Arrays.asList(StandardCharsets.UTF_8,Charset.forName("GB18030"))) {
            try {
                String text=charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                if(text.indexOf(0)>=0)throw new DofFailure(409,"插件配置含无效字符");
                Matcher matcher=KEY.matcher(text);Map<String,Value> values=new LinkedHashMap<>();
                while(matcher.find()) {
                    if(!values.isEmpty() && values.values().stream().anyMatch(v->v.end>matcher.start()))
                        throw new DofFailure(409,"插件配置字段发生重叠");
                    int start=matcher.end(),end=text.indexOf('\n',start);
                    if(end<0)end=text.length();
                    if(end>start && text.charAt(end-1)=='\r')end--;
                    if(start<text.length() && text.charAt(start)=='[') {
                        end=text.indexOf(']',start);if(end<0)throw new DofFailure(409,"插件奖励列表缺少结束括号");end++;
                    } else {
                        Matcher comment=Pattern.compile("[ \\t]+//").matcher(text);comment.region(start,end);
                        if(comment.find())end=comment.start();
                        while(end>start && (text.charAt(end-1)==' ' || text.charAt(end-1)=='\t'))end--;
                    }
                    if(values.put(matcher.group(1),new Value(start,end,text.substring(start,end).trim()))!=null)
                        throw new DofFailure(409,"插件配置包含重复字段："+matcher.group(1));
                }
                return new Document(text,charset,values);
            } catch(CharacterCodingException e) { /* Try the paired legacy text encoding. */ }
        }
        throw new DofFailure(409,"插件配置编码无法识别");
    }
    static long number(String text,long min,long max) {
        if(!text.matches("-?[0-9]{1,10}"))throw new IllegalArgumentException();
        long value=Long.parseLong(text);if(value<min || value>max)throw new IllegalArgumentException();return value;
    }
    static void validate(Field field,String value) {
        try {
            if(value==null || value.length()>64*1024 || value.indexOf(0)>=0)throw new IllegalArgumentException();
            String text=value.trim();
            switch(field.kind) {
                case "boolean": number(text,0,1);break;
                case "integer": number(text,0,Integer.MAX_VALUE);break;
                case "tuple": {
                    String[] parts=text.split("\\s+");if(parts.length!=field.arity)throw new IllegalArgumentException();
                    for(String p:parts)number(p,0,Integer.MAX_VALUE);break;
                }
                case "weekdays":case "item-list": {
                    String[] parts=text.split(",",-1);Set<Long> seen=new HashSet<>();
                    if(parts.length>(field.kind.equals("weekdays")?7:6))throw new IllegalArgumentException();
                    for(String p:parts){long n=number(p.trim(),1,field.kind.equals("weekdays")?7:Integer.MAX_VALUE);if(field.kind.equals("weekdays") && !seen.add(n))throw new IllegalArgumentException();}break;
                }
                case "time-range":
                    if(!text.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]-(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]"))throw new IllegalArgumentException();break;
                case "rewards": {
                    if(!text.startsWith("[") || !text.endsWith("]"))throw new IllegalArgumentException();
                    String body=text.substring(1,text.length()-1).trim();if(body.isEmpty())break;
                    String[] parts=body.split("\\s+");if(parts.length%field.arity!=0 || parts.length>4096)throw new IllegalArgumentException();
                    int defaults=0;long weight=0;
                    for(int i=0;i<parts.length;i+=field.arity){number(parts[i],1,Integer.MAX_VALUE);number(parts[i+1],1,Integer.MAX_VALUE);
                        long w=number(parts[i+2],-1,100000);if(w==-1)defaults++;else weight+=w;
                        if(field.arity==4)number(parts[i+3],0,1);
                    }
                    if(defaults>1 || weight>100000)throw new IllegalArgumentException();break;
                }
                case "text":
                    if(text.isEmpty() || text.length()>256 || text.chars().anyMatch(c->c<32 || c==127))throw new IllegalArgumentException();break;
                default:throw new IllegalArgumentException();
            }
        }catch(IllegalArgumentException e){throw new DofFailure(400,"配置值不符合格式："+field.label+"（"+field.key+"）");}
    }
    public static byte[] apply(String id,byte[] before,Map<String,String> patch) {
        Module module=module(id);Document source=parse(before);
        if(patch==null || patch.isEmpty())throw new DofFailure(400,"没有提交配置变更");
        Map<String,Field> fields=new HashMap<>();module.fields.forEach(f->fields.put(f.key,f));
        List<String> keys=new ArrayList<>(patch.keySet());
        for(String key:keys) {
            Field f=fields.get(key);if(f==null || !source.values.containsKey(key))throw new DofFailure(400,"配置字段未知或当前文件缺失："+key);
            validate(f,patch.get(key));
            if(!id.equals("plugin") && Arrays.asList(id+"_name",id+"_open_day",id+"_open_time").contains(key)) {
                try {
                    int bytes=source.encoding.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                        .encode(CharBuffer.wrap(patch.get(key).trim())).remaining();
                    if(bytes>254)throw new DofFailure(400,"原生配置字符串超过255字节缓冲区（含结束符）："+key);
                }catch(CharacterCodingException e){throw new DofFailure(400,"配置文字无法用原文件编码保存");}
            }
        }
        keys.sort((a,b)->Integer.compare(source.values.get(b).start,source.values.get(a).start));
        StringBuilder result=new StringBuilder(source.text);
        for(String key:keys){Value old=source.values.get(key);String value=patch.get(key).trim();
            if(!old.text.equals(value))result.replace(old.start,old.end,value);}
        try {
            ByteBuffer encoded=source.encoding.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(result));
            byte[] bytes=new byte[encoded.remaining()];encoded.get(bytes);parse(bytes);return bytes;
        }catch(CharacterCodingException e){throw new DofFailure(400,"配置文字无法用原文件编码保存");}
    }
    public static Map<String,String> imported(String id,byte[] original,String imported) {
        if(imported==null || !StandardCharsets.UTF_8.newEncoder().canEncode(imported))
            throw new DofFailure(400,"导入配置含无效的 Unicode 文字");
        Document before=parse(original),after=parse(imported.getBytes(StandardCharsets.UTF_8));
        Set<String> editable=new HashSet<>();module(id).fields.forEach(f->editable.add(f.key));
        if(!before.values.keySet().equals(after.values.keySet()))throw new DofFailure(400,"导入配置的字段与当前实例不同");
        Map<String,String> patch=new LinkedHashMap<>();
        for(String key:before.values.keySet())if(!before.values.get(key).text.equals(after.values.get(key).text)) {
            if(!editable.contains(key))throw new DofFailure(400,"导入不能修改未知字段："+key);
            patch.put(key,after.values.get(key).text);
        }
        if(patch.isEmpty())throw new DofFailure(400,"导入配置没有变化");return patch;
    }
}
