package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.*;

/** Fixed-path plugin files with a durable write-ahead journal and targeted restore. */
public final class NativePluginFiles {
    public static class Edit { public String requestId,revision,content; public Map<String,String> values; }
    public static class Journal {
        public String requestId,instanceId,module,operation,targetRequest,state,revision,afterRevision;
        public String before,after,createdAt,finishedAt;
        public long actor;
    }
    private final Path root,audit;
    private final String instance;
    private final ObjectMapper json=new ObjectMapper();
    public NativePluginFiles(Path root,Path audit,String instance) {
        this.root=root.toAbsolutePath().normalize();this.audit=audit.toAbsolutePath().normalize();this.instance=instance;
        if(instance==null || !instance.matches("[a-z0-9][a-z0-9-]{2,63}"))throw new DofFailure(409,"插件实例身份无效");
    }
    private static void links(Path path)throws IOException {
        for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new DofFailure(409,"插件配置及审计不能通过符号链接访问");
    }
    private Path file(String id)throws IOException {
        links(root);Path file=root.resolve(NativePluginConfig.module(id).file);links(file);
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
            throw new DofFailure(409,"当前实例缺少插件配置文件，需先安装对应插件资源");
        return file;
    }
    private static byte[] bytes(Path file)throws IOException {
        links(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>384*1024)throw new DofFailure(409,"插件配置或审计文件缺失、异常或超过上限");
        try(InputStream in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>384*1024)throw new DofFailure(409,"插件文件超过读取上限");out.write(buffer,0,n);}return out.toByteArray();
        }
    }
    private static String hash(byte[] bytes){return NativeUiState.sha256(bytes);}
    private static boolean id(String id){return id!=null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    private static void request(Edit edit) {
        if(edit==null || !id(edit.requestId) || edit.revision==null || !edit.revision.matches("[0-9a-f]{64}"))throw new DofFailure(400,"缺少配置版本或操作编号");
    }
    private void auditRoot()throws IOException {
        links(audit);Files.createDirectories(audit,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));links(audit);
        if(!Files.isDirectory(audit,LinkOption.NOFOLLOW_LINKS))throw new DofFailure(409,"插件审计目录无效");
    }
    private Path journalPath(String request)throws IOException {
        if(!id(request))throw new DofFailure(400,"操作编号无效");Path path=audit.resolve(request+".json");links(path);return path;
    }
    private Journal journal(String request)throws IOException {
        Path path=journalPath(request);if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return null;
        Journal j;
        try {
            j=json.readValue(bytes(path),Journal.class);
            if(!request.equals(j.requestId) || !instance.equals(j.instanceId) || j.actor<1
                || !Arrays.asList("edit","restore").contains(j.operation)
                || ("edit".equals(j.operation)?j.targetRequest!=null:!id(j.targetRequest)||request.equals(j.targetRequest))
                || !Arrays.asList("PREPARED","PERSISTED","NO_CHANGE","NOT_APPLIED","RESTORED").contains(j.state)
                || !hash(Base64.getDecoder().decode(j.before)).equals(j.revision)
                || !hash(Base64.getDecoder().decode(j.after)).equals(j.afterRevision))throw new IllegalArgumentException();
            NativePluginConfig.module(j.module);
            Instant.parse(j.createdAt);
        }catch(RuntimeException | IOException e){throw new DofFailure(409,"插件审计记录损坏或不属于当前实例，停止覆盖");}
        return j;
    }
    private void writeJournal(Journal j)throws IOException {
        Path path=journalPath(j.requestId),tmp=Files.createTempFile(audit,".journal-",".tmp",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            durable(tmp,json.writeValueAsBytes(j));
            Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            forceDirectory(audit);
        }finally{Files.deleteIfExists(tmp);}
    }
    private static void durable(Path path,byte[] value)throws IOException {
        try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer=ByteBuffer.wrap(value);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
        }
    }
    private static void forceDirectory(Path directory)throws IOException {
        try(FileChannel channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}
    }
    private Map<String,Object> state(String module,byte[] data) {
        NativePluginConfig.Module spec=NativePluginConfig.module(module);NativePluginConfig.Document doc=NativePluginConfig.parse(data);
        Map<String,String> values=doc.values();List<Map<String,Object>> fields=new ArrayList<>();
        for(NativePluginConfig.Field f:spec.fields) {
            Map<String,Object> row=new LinkedHashMap<>();row.put("key",f.key);row.put("label",f.label);row.put("kind",f.kind);row.put("arity",f.arity);
            row.put("available",values.containsKey(f.key));row.put("value",values.get(f.key));fields.add(row);
        }
        Map<String,Object> out=new LinkedHashMap<>();out.put("module",module);out.put("title",spec.title);out.put("instanceId",instance);
        out.put("revision",hash(data));out.put("fields",fields);out.put("content",doc.text);
        out.put("encoding",doc.encoding.name());out.put("effectState","not_observed");
        out.put("activation",module.equals("plugin")?"配置保存后需按插件支持的方式刷新或重启游戏进程；当前页面不重启服务。":"配置保存后，请由有权限的 GM 在游戏内执行当前配置的刷新命令；实际读取和团本效果待核对。");
        return out;
    }
    public Map<String,Object> read(String module)throws IOException {return state(module,bytes(file(module)));}
    private byte[] target(String module,byte[] before,Edit edit) {
        if(edit.content!=null && edit.values!=null)throw new DofFailure(400,"配置导入和字段修改不能混用");
        Map<String,String> patch=edit.content!=null?NativePluginConfig.imported(module,before,edit.content):edit.values;
        return NativePluginConfig.apply(module,before,patch);
    }
    private void same(byte[] current,String revision){if(!hash(current).equals(revision))throw new DofFailure(409,"配置已被其他操作修改，请重新读取并预览");}
    private List<Map<String,String>> changes(byte[] before,byte[] after) {
        Map<String,String> a=NativePluginConfig.parse(before).values(),b=NativePluginConfig.parse(after).values();List<Map<String,String>> out=new ArrayList<>();
        for(String key:a.keySet())if(!Objects.equals(a.get(key),b.get(key))){Map<String,String> row=new LinkedHashMap<>();row.put("key",key);row.put("before",a.get(key));row.put("after",b.get(key));out.add(row);}return out;
    }
    public Map<String,Object> preview(String module,Edit edit)throws IOException {
        request(edit);byte[] before=bytes(file(module));same(before,edit.revision);byte[] after=target(module,before,edit);
        Map<String,Object> out=state(module,after);out.put("changes",changes(before,after));out.put("persisted",false);return out;
    }
    private Map<String,Object> result(Journal j) {
        Map<String,Object> out=new LinkedHashMap<>();out.put("instanceId",instance);out.put("module",j.module);out.put("requestId",j.requestId);
        out.put("actor",j.actor);out.put("state",j.state);out.put("operation",j.operation);out.put("targetRequest",j.targetRequest);
        out.put("createdAt",j.createdAt);out.put("finishedAt",j.finishedAt);out.put("beforeRevision",j.revision);out.put("afterRevision",j.afterRevision);
        out.put("persisted",Arrays.asList("PERSISTED","NO_CHANGE").contains(j.state));out.put("effectState","not_observed");return out;
    }
    private List<Journal> journals()throws IOException {
        List<Journal> rows=new ArrayList<>();links(audit);if(!Files.exists(audit,LinkOption.NOFOLLOW_LINKS))return rows;
        try(DirectoryStream<Path> paths=Files.newDirectoryStream(audit,"*.json")) {
            for(Path path:paths){String name=path.getFileName().toString();if(id(name.substring(0,name.length()-5)))rows.add(journal(name.substring(0,name.length()-5)));}
        }
        return rows;
    }
    public List<Map<String,Object>> history(String module)throws IOException {
        NativePluginConfig.module(module);List<Journal> rows=journals();rows.removeIf(j->!j.module.equals(module));
        rows.sort((a,b)->b.createdAt.compareTo(a.createdAt));List<Map<String,Object>> out=new ArrayList<>();
        for(Journal j:rows.subList(0,Math.min(rows.size(),100)))out.add(result(j));return out;
    }
    void requireSettled(String module)throws IOException {
        NativePluginConfig.module(module);
        for(Journal j:journals())if(module.equals(j.module) && "PREPARED".equals(j.state))throw new DofFailure(409,"配置保存结果尚未核对，请先核对操作："+j.requestId);
    }
    private FileChannel lockChannel()throws IOException {
        auditRoot();Path path=root.resolve(".admin-operation.lock");links(path);
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new DofFailure(409,"配置锁不是普通文件");
        return FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
    }
    private FileLock lock(FileChannel channel)throws IOException {
        try {FileLock lock=channel.tryLock();if(lock==null)throw new DofFailure(409,"另一个插件配置操作正在执行");return lock;}
        catch(OverlappingFileLockException e){throw new DofFailure(409,"另一个插件配置操作正在执行");}
    }
    private void finish(Journal j,String state)throws IOException{j.state=state;j.finishedAt=Instant.now().toString();writeJournal(j);}
    public synchronized Map<String,Object> save(String module,Edit edit,long actor,String restoreId)throws IOException {
        request(edit);NativePluginConfig.module(module);
        if(actor<1)throw new DofFailure(403,"管理员身份无效");
        if(restoreId!=null && (!id(restoreId) || restoreId.equals(edit.requestId) || edit.values!=null || edit.content!=null))throw new DofFailure(400,"恢复需使用独立操作编号且不能混入新配置");
        try(FileChannel channel=lockChannel();FileLock ignored=lock(channel)) {
            String operation=restoreId==null?"edit":"restore";Journal existing=journal(edit.requestId);
            if(existing!=null) {
                if(!module.equals(existing.module) || actor!=existing.actor || !operation.equals(existing.operation)
                    || !Objects.equals(restoreId,existing.targetRequest) || !edit.revision.equals(existing.revision))throw new DofFailure(409,"操作编号已用于其他请求");
                if(restoreId==null && !hash(target(module,Base64.getDecoder().decode(existing.before),edit)).equals(existing.afterRevision))throw new DofFailure(409,"相同操作编号的配置内容不同");
                return result(existing);
            }
            for(Journal j:journals())if(j.module.equals(module) && j.state.equals("PREPARED") && !j.requestId.equals(restoreId))throw new DofFailure(409,"存在结果待核对的操作："+j.requestId);
            Path destination=file(module);byte[] before=bytes(destination);same(before,edit.revision);byte[] after;
            Journal restored=null;
            if(restoreId==null)after=target(module,before,edit);
            else {
                restored=journal(restoreId);
                if(restored==null || !module.equals(restored.module) || !Arrays.asList("PREPARED","PERSISTED").contains(restored.state))throw new DofFailure(409,"原操作不存在或不可恢复");
                Map<String,String> current=NativePluginConfig.parse(before).values(),patch=new LinkedHashMap<>();
                for(Map<String,String> change:changes(Base64.getDecoder().decode(restored.before),Base64.getDecoder().decode(restored.after))) {
                    String value=current.get(change.get("key"));
                    if(!Objects.equals(value,change.get("after")) && !Objects.equals(value,change.get("before")))throw new DofFailure(409,"本次字段已有其他修改，停止恢复");
                    patch.put(change.get("key"),change.get("before"));
                }
                after=patch.isEmpty()?before:NativePluginConfig.apply(module,before,patch);
            }
            Journal j=new Journal();j.requestId=edit.requestId;j.instanceId=instance;j.module=module;j.operation=operation;j.targetRequest=restoreId;
            j.actor=actor;j.state="PREPARED";j.createdAt=Instant.now().toString();j.revision=hash(before);j.afterRevision=hash(after);
            j.before=Base64.getEncoder().encodeToString(before);j.after=Base64.getEncoder().encodeToString(after);writeJournal(j);
            if(!Arrays.equals(before,after)) {
                Path temporary=Files.createTempFile(root,".plugin-",".tmp");
                try {
                    Files.setPosixFilePermissions(temporary,Files.getPosixFilePermissions(destination,LinkOption.NOFOLLOW_LINKS));durable(temporary,after);
                    same(bytes(file(module)),j.revision);
                    Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);forceDirectory(root);
                    same(bytes(file(module)),j.afterRevision);
                }finally{Files.deleteIfExists(temporary);}
            }
            if(restored!=null)finish(restored,"RESTORED");
            finish(j,Arrays.equals(before,after)?"NO_CHANGE":"PERSISTED");return result(j);
        }
    }
    public synchronized Map<String,Object> reconcile(String module,String request)throws IOException {
        NativePluginConfig.module(module);
        try(FileChannel channel=lockChannel();FileLock ignored=lock(channel)) {
            Journal j=journal(request);if(j==null || !module.equals(j.module))throw new DofFailure(404,"插件操作记录不存在");
            if(j.state.equals("PREPARED")) {
                String current=hash(bytes(file(module)));
                if(current.equals(j.afterRevision)) {
                    if(j.targetRequest!=null){Journal old=journal(j.targetRequest);if(old==null || !module.equals(old.module))throw new DofFailure(409,"恢复目标记录损坏");finish(old,"RESTORED");}
                    finish(j,j.revision.equals(j.afterRevision)?"NO_CHANGE":"PERSISTED");
                }else if(current.equals(j.revision))finish(j,"NOT_APPLIED");
                else throw new DofFailure(409,"配置已有其他修改，保留现场，停止自动恢复");
            }
            return result(j);
        }
    }
}
