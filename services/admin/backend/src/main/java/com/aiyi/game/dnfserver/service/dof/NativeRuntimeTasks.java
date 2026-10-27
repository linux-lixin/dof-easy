package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;

/** Fixed raid refresh mailbox. Game receipts prove a native call, never gameplay. */
public final class NativeRuntimeTasks {
    public static final String PLUGIN_SHA256="44b1481d503083227a298fb47b5d0f0c4b6a5bb4b4e8d07dfa88e46241a8a42b";
    public static final String EXECUTOR_SHA256,GAME_ABI_SHA256,HELPER_SOURCE_SHA256;
    static {
        try(InputStream in=NativeRuntimeTasks.class.getResourceAsStream("/dof/runtime-apply.json")) {
            Map<?,?> contract=new ObjectMapper().readValue(in,Map.class);
            if(!Objects.equals(contract.get("schema"),1))throw new IOException("Unknown runtime protocol contract");
            EXECUTOR_SHA256=(String)contract.get("executorScriptSHA256");GAME_ABI_SHA256=(String)contract.get("gameAbiSHA256");
            HELPER_SOURCE_SHA256=(String)contract.get("digestHelperSourceSHA256");
            if(!EXECUTOR_SHA256.matches("[0-9a-f]{64}")||!GAME_ABI_SHA256.matches("[0-9a-f]{64}")||!HELPER_SOURCE_SHA256.matches("[0-9a-f]{64}"))throw new IOException("Invalid runtime contract");
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    public static class Edit {public String requestId,revision;}
    public static class Resolve {public String requestId,taskRevision;public boolean keepCurrent;}
    private final Path root,mail,audit;
    private final String instance,database;
    private final NativePluginFiles configuration;
    private final ObjectMapper json=new ObjectMapper();
    private static final class Snapshot extends LinkedHashMap<String,Object> {
        private String fileRevision;
        Snapshot(Map<String,Object> data,String revision){super(data);this.fileRevision=revision;}
    }
    private final java.util.function.LongSupplier clock;
    public NativeRuntimeTasks(Path root,Path audit,Path configurationAudit,String instance,String database) {
        this(root,audit,configurationAudit,instance,database,System::currentTimeMillis);
    }
    NativeRuntimeTasks(Path root,Path audit,Path configurationAudit,String instance,String database,java.util.function.LongSupplier clock) {
        this.root=root.toAbsolutePath().normalize();this.mail=this.root.resolve(".runtime-apply");
        this.audit=audit.toAbsolutePath().normalize();this.instance=instance;this.database=database;this.clock=clock;
        this.configuration=new NativePluginFiles(this.root,configurationAudit,instance);
    }
    private static void module(String module){if(!Arrays.asList("anton","luke").contains(module))throw new DofFailure(400,"仅支持安徒恩或卢克配置刷新");}
    private static void id(String id){if(id==null || !id.matches("[a-zA-Z0-9][a-zA-Z0-9-]{7,63}"))throw new DofFailure(400,"操作编号格式无效");}
    private static void revision(String value){if(value==null || !value.matches("[0-9a-f]{64}"))throw new DofFailure(400,"配置版本无效");}
    private static void links(Path p)throws IOException{for(Path q=p;q!=null;q=q.getParent())if(Files.isSymbolicLink(q))throw new DofFailure(409,"刷新目录或文件包含符号链接");}
    private static String hash(byte[] raw){try{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))out.append(String.format("%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    @SuppressWarnings("unchecked") private Map<String,Object> read(Path p,boolean optional)throws IOException {
        links(p);if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS)){if(optional)return null;throw new DofFailure(409,"刷新执行组件尚未提供绑定信息");}
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS) || Files.size(p)>131072)throw new DofFailure(409,"刷新记录不是有效的小型普通文件");
        try{
            byte[] raw=boundedBytes(p);Map<String,Object> out=json.readValue(raw,Map.class);if(out==null)throw new IOException("empty object");return new Snapshot(out,hash(raw));
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new DofFailure(409,"刷新记录损坏，请核对操作结果");}
    }
    private static long number(Map<String,Object> m,String key){Object value=m.get(key);return value instanceof Number?((Number)value).longValue():Long.MIN_VALUE;}
    private void bound(Map<String,Object> m){if(m==null || number(m,"schema")!=1 || !instance.equals(m.get("instanceId")) || !database.equals(m.get("databaseUuid")))throw new DofFailure(409,"刷新组件绑定与当前数据库实例不一致");}
    private byte[] boundedBytes(Path p)throws IOException {
        links(p);if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>131072)throw new DofFailure(409,"刷新文件不可用或过大");
        try(InputStream in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>131072)throw new DofFailure(409,"刷新文件在读取时超过大小限制");out.write(buffer,0,n);}return out.toByteArray();
        }
    }
    @SuppressWarnings("unchecked") private Map<String,Object> binding()throws IOException {
        Path path=mail.resolve("binding.json");if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new DofFailure(409,"游戏组件尚未启用网页刷新");
        byte[] raw=boundedBytes(path);Map<String,Object> b;
        try{b=json.readValue(raw,Map.class);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new DofFailure(409,"刷新组件绑定记录损坏");}bound(b);
        if(!PLUGIN_SHA256.equals(b.get("pluginSHA256")) || !EXECUTOR_SHA256.equals(b.get("executorScriptSHA256"))
            || !GAME_ABI_SHA256.equals(b.get("gameAbiSHA256")) || !HELPER_SOURCE_SHA256.equals(b.get("digestHelperSourceSHA256"))
            || !(b.get("digestHelperSHA256") instanceof String) || !((String)b.get("digestHelperSHA256")).matches("[0-9a-f]{64}")
            || number(b,"digestHelperBytes")<=0 || number(b,"digestHelperBytes")>1048576 || !(b.get("channels") instanceof List))throw new DofFailure(409,"刷新组件协议、摘要库、执行器或插件版本未匹配");
        List<Object> channels=(List<Object>)b.get("channels");Set<Object> unique=new HashSet<>(channels);
        if(channels.isEmpty() || channels.size()>56 || unique.size()!=channels.size())throw new DofFailure(409,"刷新频道目录无效");
        for(Object channel:channels)if(!(channel instanceof String) || !((String)channel).matches("(?:cain|diregie|siroco|prey|casillas|hilder)[0-9]{2}|local"))throw new DofFailure(409,"刷新频道名称无效");
        b=new LinkedHashMap<>(b);b.put("bindingRevision",hash(raw));return b;
    }
    private boolean fresh(Map<String,Object> s){long age=clock.getAsLong()-number(s,"updatedAt");return age>=-5000 && age<=15000;}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> channels(Map<String,Object> b,boolean requireFresh)throws IOException {
        List<Map<String,Object>> out=new ArrayList<>();
        for(Object name:(List<?>)b.get("channels")){
            Map<String,Object> s=read(mail.resolve("status-"+name+".json"),true);
            if(s==null)throw new DofFailure(409,"频道尚无原生刷新心跳："+name);
            bound(s);
            if(!name.equals(s.get("channel")) || !b.get("bindingRevision").equals(s.get("bindingRevision"))
                || !PLUGIN_SHA256.equals(s.get("pluginSHA256")) || !(s.get("bootId") instanceof String)
                || !((String)s.get("bootId")).matches("[0-9a-f]{32}") || number(s,"pid")<=0 || number(s,"dispatcherThreadId")<=0 || !Boolean.TRUE.equals(s.get("idle")))
                throw new DofFailure(409,"频道刷新状态无效或正在执行："+name);
            if(requireFresh && !fresh(s))throw new DofFailure(409,"频道刷新心跳已过期："+name);
            out.add(s);
        }
        return out;
    }
    public Map<String,Object> status()throws IOException {
        Map<String,Object> out=new LinkedHashMap<>();out.put("webExecutionSupported",false);out.put("runtimeObserved",false);out.put("gameplayVerified",false);
        out.put("verificationFields",Arrays.asList("name","open_day","open_time","member_min"));
        try{Map<String,Object> b=binding();List<Map<String,Object>> rows=channels(b,true);out.put("channels",rows);out.put("bindingRevision",b.get("bindingRevision"));out.put("runtimeObserved",true);out.put("webExecutionSupported",true);}
        catch(DofFailure e){out.put("reason",e.getMessage());}
        catch(IOException e){out.put("reason","刷新组件的状态文件暂不可读；配置管理仍可使用");}return out;
    }
    private void write(Path destination,Map<String,Object> value)throws IOException {
        links(destination);Path parent=destination.getParent();links(parent);
        if(!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS))throw new DofFailure(409,"刷新目录不存在");
        if(value instanceof Snapshot){
            String expected=((Snapshot)value).fileRevision;boolean exists=Files.exists(destination,LinkOption.NOFOLLOW_LINKS);
            if(expected==null?exists:!exists || !expected.equals(hash(boundedBytes(destination))))throw new DofFailure(409,"刷新审计在操作期间已被修改或移除");
        }
        byte[] raw=json.writeValueAsBytes(value);
        Path tmp=Files.createTempFile(parent,".apply-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            try(FileChannel c=FileChannel.open(tmp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS)){
                ByteBuffer bytes=ByteBuffer.wrap(raw);while(bytes.hasRemaining())c.write(bytes);c.force(true);
            }
            Files.move(tmp,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            try(FileChannel c=FileChannel.open(parent,StandardOpenOption.READ)){c.force(true);}
            if(value instanceof Snapshot)((Snapshot)value).fileRevision=hash(raw);
        }finally{Files.deleteIfExists(tmp);}
    }
    private Path journalPath(String id){id(id);return audit.resolve(id+".json");}
    private Map<String,Object> journal(String id)throws IOException {
        Map<String,Object> j=read(journalPath(id),true);if(j==null)return null;bound(j);
        if(!id.equals(j.get("requestId")) || !Arrays.asList("anton","luke").contains(j.get("module")) || number(j,"actor")<1 || number(j,"createdAt")<0
            || !Arrays.asList("PREVIEWED","PUBLISHING","SUBMITTED","PARTIAL","UNKNOWN","APPLIED","REJECTED","KEEP_CURRENT","NOT_CONFIRMED").contains(j.get("state")))throw new DofFailure(409,"刷新审计身份或状态错误");
        if("KEEP_CURRENT".equals(j.get("state"))){id((String)j.get("targetRequest"));revision((String)j.get("targetTaskRevision"));return j;}
        revision((String)j.get("revision"));revision((String)j.get("bindingRevision"));
        if(!(j.get("executor") instanceof Map) || !j.get("bindingRevision").equals(((Map<?,?>)j.get("executor")).get("bindingRevision")))throw new DofFailure(409,"刷新审计缺少对应执行器版本快照");
        if(number(j,"expiresAt")-number(j,"createdAt")!=120000 || !(j.get("targets") instanceof List) || !(j.get("expected") instanceof Map))throw new DofFailure(409,"刷新审计预览内容损坏");
        Set<Object> names=new HashSet<>();for(Object value:(List<?>)j.get("targets")){
            if(!(value instanceof Map))throw new DofFailure(409,"刷新目标损坏");Map<?,?> t=(Map<?,?>)value;
            if(!(t.get("channel") instanceof String) || !((String)t.get("channel")).matches("(?:cain|diregie|siroco|prey|casillas|hilder)[0-9]{2}|local") || !names.add(t.get("channel"))
                || !(t.get("bootId") instanceof String) || !((String)t.get("bootId")).matches("[0-9a-f]{32}") || !(t.get("pid") instanceof Number) || ((Number)t.get("pid")).longValue()<=0
                || !(t.get("dispatcherThreadId") instanceof Number) || ((Number)t.get("dispatcherThreadId")).longValue()<=0)throw new DofFailure(409,"刷新目标身份无效");
        }
        if(names.isEmpty() || names.size()>56)throw new DofFailure(409,"刷新目标数量无效");return j;
    }
    private FileChannel lock()throws IOException {
        links(root);links(audit);Files.createDirectories(audit,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));links(audit);
        Path p=root.resolve(".admin-operation.lock");links(p);
        if(Files.exists(p,LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new DofFailure(409,"配置共享锁不是普通文件");
        return FileChannel.open(p,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
    }
    private FileLock acquire(FileChannel c)throws IOException {try{FileLock lock=c.tryLock();if(lock==null)throw new DofFailure(409,"另一个配置或刷新操作正在执行");return lock;}catch(OverlappingFileLockException e){throw new DofFailure(409,"另一个配置或刷新操作正在执行");}}
    private String current(String module)throws IOException {Path p=root.resolve(module+"-raid.ini");links(p);if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>131072)throw new DofFailure(409,"团本配置文件无效");return hash(Files.readAllBytes(p));}
    private Map<String,Object> base(String id,String module,long actor){Map<String,Object> j=new Snapshot(Collections.emptyMap(),null);j.put("schema",1);j.put("instanceId",instance);j.put("databaseUuid",database);j.put("requestId",id);j.put("module",module);j.put("actor",actor);return j;}
    private boolean pending(Map<String,Object> j){return Arrays.asList("PUBLISHING","SUBMITTED","PARTIAL","UNKNOWN").contains(j.get("state"));}
    private void noPending(String module,String self)throws IOException {
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(audit,"*.json")){
            for(Path p:entries){String name=p.getFileName().toString();Map<String,Object> j=journal(name.substring(0,name.length()-5));
                if(module.equals(j.get("module")) && !self.equals(j.get("requestId")) && pending(j))throw new DofFailure(409,"存在待核对刷新的操作："+j.get("requestId"));}
        }
    }
    private Map<String,Object> result(Map<String,Object> j)throws IOException {Map<String,Object> out=new LinkedHashMap<>(j);String revision=j instanceof Snapshot?((Snapshot)j).fileRevision:null;if(revision==null)throw new DofFailure(409,"刷新审计快照缺少版本");out.put("taskRevision",revision);out.put("gameplayVerified",false);out.put("verificationFields",Arrays.asList("name","open_day","open_time","member_min"));return out;}
    public synchronized Map<String,Object> preview(String module,Edit edit,long actor)throws IOException {
        module(module);if(edit==null || actor<1)throw new DofFailure(400,"缺少刷新请求或操作者");id(edit.requestId);revision(edit.revision);
        try(FileChannel c=lock();FileLock ignored=acquire(c)){
            Map<String,Object> old=journal(edit.requestId);
            if(old!=null){same(old,module,edit,actor);return result(old);}
            noPending(module,edit.requestId);configuration.requireSettled(module);if(!current(module).equals(edit.revision))throw new DofFailure(409,"配置版本已改变，请重新读取");
            NativePluginConfig.Document doc=NativePluginConfig.parse(Files.readAllBytes(root.resolve(module+"-raid.ini")));
            if(!StandardCharsets.UTF_8.equals(doc.encoding))throw new DofFailure(409,"原生刷新目前只验证了UTF-8配套，请先核对当前文件编码");
            Map<String,Object> b=binding(),j=base(edit.requestId,module,actor);List<Map<String,Object>> states=channels(b,true),targets=new ArrayList<>();
            for(Map<String,Object> s:states){Map<String,Object> target=new LinkedHashMap<>();for(String key:Arrays.asList("channel","bootId","pid","dispatcherThreadId"))target.put(key,s.get(key));targets.add(target);}
            long now=clock.getAsLong();j.put("revision",edit.revision);j.put("bindingRevision",b.get("bindingRevision"));j.put("executor",new LinkedHashMap<>(b));j.put("targets",targets);j.put("createdAt",now);j.put("expiresAt",now+120000);j.put("state","PREVIEWED");
            Map<String,String> expected=new LinkedHashMap<>();for(String key:Arrays.asList("name","open_day","open_time","member_min"))expected.put(key,doc.values().get(module+"_"+key));
            if(expected.containsValue(null))throw new DofFailure(409,"当前配置缺少已验证的原生回读字段");
            for(String key:Arrays.asList("name","open_day","open_time"))if(expected.get(key).getBytes(StandardCharsets.UTF_8).length>254)throw new DofFailure(409,"原生回读字符串超过已确认的255字节缓冲区："+key);
            try{expected.put("member_min",Long.toString(NativePluginConfig.number(expected.get("member_min"),1,Integer.MAX_VALUE)));}
            catch(IllegalArgumentException e){throw new DofFailure(409,"最少人数不符合原生32位正整数格式");}j.put("expected",expected);
            write(journalPath(edit.requestId),j);return result(j);
        }
    }
    private void same(Map<String,Object> j,String module,Edit e,long actor){if(!module.equals(j.get("module")) || !e.revision.equals(j.get("revision")) || number(j,"actor")!=actor)throw new DofFailure(409,"相同操作编号的刷新内容或操作者不同");}
    @SuppressWarnings("unchecked") public synchronized Map<String,Object> submit(String module,Edit edit,long actor)throws IOException {
        module(module);if(edit==null)throw new DofFailure(400,"缺少刷新请求");id(edit.requestId);revision(edit.revision);
        try(FileChannel c=lock();FileLock ignored=acquire(c)){
            Map<String,Object> j=journal(edit.requestId);if(j==null)throw new DofFailure(409,"请先预览本次刷新");same(j,module,edit,actor);
            if(!"PREVIEWED".equals(j.get("state")))return result(j);
            noPending(module,edit.requestId);if(clock.getAsLong()>number(j,"expiresAt"))throw new DofFailure(409,"刷新预览已过期");
            configuration.requireSettled(module);
            if(!current(module).equals(j.get("revision")))throw new DofFailure(409,"配置已变更，请重新预览");
            Map<String,Object> b=binding();if(!b.get("bindingRevision").equals(j.get("bindingRevision")))throw new DofFailure(409,"执行组件绑定发生变化");
            List<Map<String,Object>> live=channels(b,true);
            for(Map<String,Object> target:(List<Map<String,Object>>)j.get("targets")){
                Map<String,Object> s=live.stream().filter(v->v.get("channel").equals(target.get("channel"))).findFirst().orElseThrow(()->new DofFailure(409,"目标频道缺失"));
                if(!s.get("bootId").equals(target.get("bootId")) || number(s,"pid")!=number(target,"pid") || number(s,"dispatcherThreadId")!=number(target,"dispatcherThreadId"))throw new DofFailure(409,"目标频道或派发线程已经改变，请重新预览");
            }
            j.put("state","PUBLISHING");j.put("submittedAt",clock.getAsLong());write(journalPath(edit.requestId),j);
            Map<String,Object> command=new LinkedHashMap<>(j);command.remove("state");command.remove("createdAt");command.remove("actor");command.remove("executor");
            write(mail.resolve("request-"+module+".json"),command);
            j.put("state","SUBMITTED");write(journalPath(edit.requestId),j);return result(j);
        }
    }
    @SuppressWarnings("unchecked") public synchronized Map<String,Object> reconcile(String module,String id)throws IOException {
        module(module);id(id);
        try(FileChannel c=lock();FileLock ignored=acquire(c)){
            Map<String,Object> j=journal(id);if(j==null || !module.equals(j.get("module")))throw new DofFailure(404,"刷新操作不存在");
            if(!pending(j))return result(j);
            List<Map<String,Object>> receipts=new ArrayList<>();int applied=0,rejected=0;boolean unknown=false;
            for(Map<String,Object> target:(List<Map<String,Object>>)j.get("targets")){
                String channel=(String)target.get("channel");Map<String,Object> r=read(mail.resolve("receipt-"+channel+"-"+module+".json"),true),row=new LinkedHashMap<>(target);
                if(r!=null && id.equals(r.get("requestId"))){
                    bound(r);if(!module.equals(r.get("module")) || !channel.equals(r.get("channel")) || !j.get("revision").equals(r.get("revision")) || !target.get("bootId").equals(r.get("bootId")) || !j.get("bindingRevision").equals(r.get("bindingRevision"))
                        || number(r,"executorThreadId")!=number(target,"dispatcherThreadId"))throw new DofFailure(409,"原生刷新回执内容或执行线程不一致，停止自动判断");
                    String state=String.valueOf(r.get("state"));if(!Arrays.asList("RUNNING","APPLIED","REJECTED","UNKNOWN").contains(state))throw new DofFailure(409,"原生刷新回执状态无效");
                    if("APPLIED".equals(state)){
                        Map<?,?> observed=r.get("observed") instanceof Map?(Map<?,?>)r.get("observed"):Collections.emptyMap();
                        for(Map.Entry<String,Object> e:((Map<String,Object>)j.get("expected")).entrySet())if(!String.valueOf(e.getValue()).equals(String.valueOf(observed.get(e.getKey()))))throw new DofFailure(409,"原生刷新回读与预览不一致");applied++;
                    }
                    if("REJECTED".equals(state))rejected++;unknown|="UNKNOWN".equals(state);row.putAll(r);
                }else{
                    row.put("state","WAITING");Map<String,Object> live=read(mail.resolve("status-"+channel+".json"),true);
                    if(live!=null && !target.get("bootId").equals(live.get("bootId"))){row.put("state","BOOT_CHANGED");unknown=true;}
                }
                receipts.add(row);
            }
            String state=applied==receipts.size()?"APPLIED":applied+rejected==receipts.size()?"REJECTED":unknown || clock.getAsLong()>number(j,"expiresAt")?"UNKNOWN":applied+rejected>0?"PARTIAL":"SUBMITTED";
            j.put("receipts",receipts);j.put("state",state);write(journalPath(id),j);return result(j);
        }
    }
    public synchronized Map<String,Object> resolve(String module,String original,Resolve edit,long actor)throws IOException {
        module(module);id(original);if(edit==null || !edit.keepCurrent || actor<1)throw new DofFailure(400,"需要明确保留当前配置的核对决定");id(edit.requestId);revision(edit.taskRevision);
        if(original.equals(edit.requestId))throw new DofFailure(400,"核对决定须使用新操作编号");
        try(FileChannel c=lock();FileLock ignored=acquire(c)){
            Map<String,Object> replay=journal(edit.requestId);if(replay!=null){
                if(!module.equals(replay.get("module")) || !original.equals(replay.get("targetRequest")) || number(replay,"actor")!=actor || !edit.taskRevision.equals(replay.get("targetTaskRevision")))throw new DofFailure(409,"核对编号已被其他内容使用");
                Map<String,Object> parent=journal(original);if(parent!=null && pending(parent)){parent.put("state","NOT_CONFIRMED");parent.put("resolvedBy",edit.requestId);write(journalPath(original),parent);}return result(replay);
            }
            Map<String,Object> j=journal(original);if(j==null || !module.equals(j.get("module")) || !pending(j))throw new DofFailure(409,"原刷新操作无需人工核对");
            if(!edit.taskRevision.equals(hash(Files.readAllBytes(journalPath(original)))))throw new DofFailure(409,"任务状态已改变，请重新读取后确认");
            if(clock.getAsLong()<=number(j,"expiresAt"))throw new DofFailure(409,"任务尚未过期，请先等待频道回执");
            channels(binding(),true);Map<String,Object> decision=base(edit.requestId,module,actor);decision.put("state","KEEP_CURRENT");decision.put("targetRequest",original);decision.put("targetTaskRevision",edit.taskRevision);decision.put("createdAt",clock.getAsLong());
            write(journalPath(edit.requestId),decision);j.put("state","NOT_CONFIRMED");j.put("resolvedBy",edit.requestId);write(journalPath(original),j);return result(decision);
        }
    }
    public List<Map<String,Object>> history(String module)throws IOException {
        module(module);links(audit);if(!Files.exists(audit))return Collections.emptyList();List<Map<String,Object>> rows=new ArrayList<>();
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(audit,"*.json")){for(Path p:entries){String name=p.getFileName().toString();Map<String,Object> j=journal(name.substring(0,name.length()-5));if(module.equals(j.get("module")))rows.add(result(j));}}
        rows.sort((a,b)->Long.compare(number(b,"createdAt"),number(a,"createdAt")));return rows.subList(0,Math.min(rows.size(),100));
    }
}
