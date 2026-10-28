package com.aiyi.game.dnfserver.service.dof;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Authenticated facade; all requests re-check the isolated database binding. */
@Service
public class DofClientService {
    @Resource private DofAdminService adminService;
    @Value("${DOF_CLIENT_WORKSPACE:}") private String workspace;
    @Value("${DOF_GAME_PVF:}") private String gamePvf;
    private NativeClientJobs jobs;
    private volatile NativeScriptJobs scripts;
    private boolean configured() {
        if (workspace == null || workspace.trim().isEmpty()) return false;
        Path root = Paths.get(workspace).toAbsolutePath().normalize();
        Path inputs = root.resolve("inputs"), binding = inputs.resolve("binding.json");
        if (Files.isSymbolicLink(root) || Files.isSymbolicLink(inputs) || Files.isSymbolicLink(binding))
            throw new DofFailure(409, "客户端发布来源不能通过符号链接访问");
        if (!Files.exists(binding, LinkOption.NOFOLLOW_LINKS)) return false;
        if (!Files.isRegularFile(binding, LinkOption.NOFOLLOW_LINKS))
            throw new DofFailure(409, "客户端发布来源绑定文件异常");
        return true;
    }
    private long guard() {
        long actor = adminService.admin();
        try (Connection ignored = adminService.connection()) { return actor; }
        catch (SQLException e) { throw new DofFailure(503, "隔离数据库不可用，停止客户端发布操作"); }
    }
    private synchronized NativeClientJobs jobs() throws IOException {
        if (!configured()) throw new DofFailure(409, "当前实例尚未导入客户端发布材料");
        if (jobs == null) jobs = new NativeClientJobs(Paths.get(workspace), (String)adminService.context().get("instanceId"),
            p -> Files.getFileStore(p).getUsableSpace(), (id,revision) -> {
                NativeScriptJobs store=scripts;
                if(store==null)throw new DofFailure(409,"Script 任务服务尚未初始化");
                return store.artifact(id,revision);
            });
        jobs.requireDatabase(adminService.boundDatabaseUuid());
        return jobs;
    }
    private synchronized NativeScriptJobs scripts() throws IOException {
        NativeClientJobs client=jobs();client.requireScriptVersion();
        if(scripts==null) {
            Path root=NativeScriptJobs.directory(Paths.get(workspace).toAbsolutePath().normalize().resolve("script"));
            scripts=new NativeScriptJobs(root,(String)adminService.context().get("instanceId"));
        }
        return scripts;
    }
    public static final class PairedSourceRequest {public String requestId,revision;}
    public Object importPairedScript(PairedSourceRequest request)throws IOException {
        long actor=guard();
        if(request==null || !currentRevision().equals(request.revision))throw new DofFailure(409,"发布来源版本改变，请刷新后导入");
        if(gamePvf==null || gamePvf.isEmpty())throw new DofFailure(409,"尚未绑定当前服务端 PVF");
        return scripts().uploadPaired(request.requestId,request.revision,actor,Paths.get(gamePvf));
    }
    private String currentRevision()throws IOException{return (String)jobs().status().get("revision");}
    public Object scriptStatus()throws IOException {
        guard();Map<String,Object> out=new LinkedHashMap<>();
        if(!configured()){
            out.putAll(adminService.context());out.put("enabled",false);
            out.put("clientFormatVersion",NativeClientFormat.VERSION);
            out.put("blocker","当前实例尚未导入客户端发布材料");return out;
        }
        out.putAll(jobs().status());jobs().requireScriptVersion();out.put("enabled",true);
        out.put("pairedSourceAvailable",gamePvf!=null&&!gamePvf.isEmpty());
        out.put("mode","uncompressed");out.put("maxSourceBytes",NativePvfSource.MAX_BYTES);
        out.put("nativeClientAcceptance",false);out.put("applicationEnabled",gamePvf!=null&&!gamePvf.isEmpty());
        out.put("applicationBlocker","尚未配置隔离游戏 PVF 的只读来源绑定");return out;
    }
    public Object scriptUploads()throws IOException{guard();return scripts().uploads();}
    public Object scriptUpload(String id)throws IOException{guard();return scripts().readUpload(id);}
    public Object uploadScript(String id,String revision,long length,java.io.InputStream input)throws IOException {
        long actor=guard();if(!currentRevision().equals(revision))throw new DofFailure(409,"发布来源版本改变，请刷新后上传");
        return scripts().upload(id,revision,actor,length,input);
    }
    public Object scriptTasks()throws IOException{guard();return scripts().list();}
    public Object scriptTask(String id)throws IOException{guard();return scripts().read(id);}
    public Object submitScript(NativeScriptJobs.Request request)throws IOException {
        long actor=guard();if(request==null||!currentRevision().equals(request.revision))throw new DofFailure(409,"发布来源版本改变，请刷新后生成");
        return scripts().submit(request,actor);
    }
    public Path downloadScript(String id)throws IOException {
        guard();NativeScriptJobs store=scripts();return store.artifact(id,store.read(id).revision).path;
    }
    DofScriptReleaseService.Candidate releaseCandidate(String bundleId)throws IOException {
        guard();NativeClientJobs client=jobs();NativeClientJobs.Job bundle=client.read(bundleId);
        if(bundle.scriptTaskId==null||!"isolated-dof-pvf-bundle".equals(bundle.packageKind))throw new DofFailure(409,"请选择包含加密 PVF 的配套包");
        if(!bundle.revision.equals(currentRevision()))throw new DofFailure(409,"配套包的组件版本已经改变");
        client.download(bundleId);NativeScriptJobs store=scripts();NativeScriptJobs.Artifact artifact=store.artifact(bundle.scriptTaskId,bundle.revision);
        if(!artifact.job.sha256.equals(bundle.scriptSha256)||artifact.job.bytes!=bundle.scriptBytes)throw new DofFailure(409,"配套包与 PVF 任务不匹配");
        NativeClientJobs.requireScriptBatch(bundle,artifact.job);
        return new DofScriptReleaseService.Candidate(bundleId,bundle.scriptTaskId,artifact.job.sourceSha256,artifact.job.sha256,store.parameters(bundle.scriptTaskId));
    }
    public Object status() throws IOException {
        guard();
        if (!configured()) {
            Map<String,Object> out = new LinkedHashMap<>(adminService.context()); out.put("enabled", false);
            out.put("clientFormatVersion",NativeClientFormat.VERSION);
            out.put("blocker","当前实例尚未导入客户端发布材料");return out;
        }
        Map<String,Object> out = new LinkedHashMap<>(jobs().status()); out.put("enabled", true); return out;
    }
    public Object list() throws IOException { guard(); return jobs().list(); }
    public Object submit(NativeClientJobs.Request request) throws IOException {
        long actor = guard();if(request!=null&&request.scriptTaskId!=null)scripts();return jobs().submit(request, actor);
    }
    public Object read(String id) throws IOException { guard(); return jobs().read(id); }
    public Path download(String id) throws IOException { guard(); return jobs().download(id); }
    @PreDestroy public void close() throws IOException {
        // Do not hold this service monitor while joining a client worker which
        // may be resolving a Script artifact through scripts().
        if (jobs != null) { jobs.close(); jobs = null; }
        if (scripts != null) { scripts.close(); scripts = null; }
    }
}
