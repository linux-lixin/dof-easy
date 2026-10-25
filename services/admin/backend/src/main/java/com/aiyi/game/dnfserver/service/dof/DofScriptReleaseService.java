package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Manual bundle activation: only the paired two decryption columns are changed.
 * Native automatic update/checksum must already be disabled. Never rewrites PVF,
 * identities, player data or updater metadata. Persistence != client acceptance.
 */
@Service
public class DofScriptReleaseService {
    @Resource private DofAdminService access;
    @Resource private DofClientService clients;
    @Value("${QINGKONG_GAME_PVF:}") private String gamePvf;
    private static final String AUDIT="`dnf_service`.`QINGKONG_SCRIPT_RELEASE`";
    private static final List<String> KEYS=Arrays.asList("billing.decrypt","qingkong_config.pvfencryption",
        "qingkong_config.Scriptmd5","qingkong_config.Scriptverification","qingkong_config.updatec","qingkong_config.updatetype");
    private static final ObjectMapper JSON=new ObjectMapper();
    public static class Edit {public String requestId,revision,bundleId;}
    static final class Candidate {
        final String bundleId,taskId,sourceSha256,artifactSha256,parameter;
        Candidate(String b,String t,String s,String a,String p){bundleId=b;taskId=t;sourceSha256=s;artifactSha256=a;parameter=p;}
        @Override public String toString(){return "ScriptReleaseCandidate[private]";}
    }
    private String instance(){return access.context().get("instanceId").toString();}
    private static void id(String s){if(s==null||!s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new DofFailure(400,"发布操作编号无效");}
    private static String encode(Map<String,String> s){try{return JSON.writeValueAsString(s);}catch(IOException e){throw new IllegalStateException(e);}}
    private static String revision(Map<String,String> s){return NativeUiState.sha256(encode(s).getBytes(StandardCharsets.UTF_8));}
    private static String text(Map<String,String>s,String key){String b=s.get(key);return b==null?null:new String(Base64.getDecoder().decode(b),StandardCharsets.US_ASCII);}
    private static Map<String,String> snapshot(String raw,String hash) {
        try {
            Map<String,String> found=JSON.readValue(raw,new TypeReference<Map<String,String>>(){}),out=new LinkedHashMap<>();
            if(!found.keySet().equals(new HashSet<>(KEYS)))throw new IllegalArgumentException();
            for(String key:KEYS){String v=found.get(key);if(v!=null&&(v.length()>8192||!Base64.getEncoder().encodeToString(Base64.getDecoder().decode(v)).equals(v)))throw new IllegalArgumentException();out.put(key,v);}
            if(!revision(out).equals(hash))throw new IllegalArgumentException();return out;
        }catch(Exception e){throw new DofFailure(409,"发布快照损坏，停止恢复");}
    }
    private void schema(Connection c)throws SQLException {
        Map<String,String> engines=new HashMap<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT TABLE_SCHEMA,TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE (TABLE_SCHEMA='qk' AND TABLE_NAME IN ('billing','qingkong_config')) OR (TABLE_SCHEMA='dnf_service' AND TABLE_NAME='QINGKONG_SCRIPT_RELEASE')")) {
            while(r.next())engines.put(r.getString(1)+"."+r.getString(2),r.getString(3));
        }
        for(String table:Arrays.asList("billing","qingkong_config"))if(!Arrays.asList("MyISAM","InnoDB").contains(engines.get("qk."+table)))throw new DofFailure(409,"原生配置表引擎不兼容");
        if(!"InnoDB".equals(engines.get("dnf_service.QINGKONG_SCRIPT_RELEASE")))throw new DofFailure(409,"发布审计表尚未准备或引擎不兼容");
        Set<String> valid=new HashSet<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='qk' AND TABLE_NAME IN ('billing','qingkong_config')")) {
            while(r.next())if("varchar".equals(r.getString(3))&&r.getLong(4)>=128)valid.add(r.getString(1)+"."+r.getString(2));
        }
        if(!valid.containsAll(KEYS))throw new DofFailure(409,"原生 Script 字段类型不兼容，不初始化或修改结构");
    }
    private Map<String,String> read(Connection c)throws SQLException {
        schema(c);Map<String,String> found=new HashMap<>(),out=new LinkedHashMap<>();
        for(String table:Arrays.asList("billing","qingkong_config")) {
            List<String> keys=new ArrayList<>(),columns=new ArrayList<>();for(String key:KEYS)if(key.startsWith(table+".")){keys.add(key);columns.add("BINARY `"+key.substring(table.length()+1)+"`");}
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT ID,"+String.join(",",columns)+" FROM `qk`.`"+table+"` LIMIT 2")) {
                if(!r.next()||r.getInt(1)!=1)throw new DofFailure(409,"原生配置行缺失，停止应用");
                for(int i=0;i<keys.size();i++){byte[] value=r.getBytes(i+2);if(value!=null&&value.length>4096)throw new DofFailure(409,"原生 Script 字段超限");found.put(keys.get(i),value==null?null:Base64.getEncoder().encodeToString(value));}
                if(r.next())throw new DofFailure(409,"原生配置存在多行，停止应用");
            }
        }
        for(String key:KEYS)out.put(key,found.get(key));return out;
    }
    private static List<String> changed(Map<String,String>a,Map<String,String>b){List<String> out=new ArrayList<>();for(String k:KEYS)if(!Objects.equals(a.get(k),b.get(k)))out.add(k);return out;}
    private void lock(Connection c)throws SQLException{try(Statement s=c.createStatement()){s.execute("LOCK TABLES qk.billing WRITE,qk.qingkong_config WRITE,"+AUDIT+" WRITE");}}
    private void unlock(Connection c)throws SQLException{try(Statement s=c.createStatement()){s.execute("UNLOCK TABLES");}}
    public Object status() {
        access.admin();try(Connection c=access.connection()) {
            Map<String,String>s=read(c);Map<String,Object> out=access.context();out.put("revision",revision(s));
            out.put("parameterCopiesEqual",Objects.equals(s.get(KEYS.get(0)),s.get(KEYS.get(1))));
            out.put("manualModeCompatible",manual(s));out.put("sourceConfigured",gamePvf!=null&&!gamePvf.isEmpty());out.put("nativeClientAcceptance",false);return out;
        }catch(SQLException e){throw new DofFailure(503,"隔离发布配置或审计表不可用");}
    }
    private static boolean manual(Map<String,String>s){return "0".equals(text(s,"qingkong_config.Scriptverification"))&&"0".equals(text(s,"qingkong_config.updatec"));}
    public Object preview(Edit edit)throws IOException {
        access.admin();if(edit==null)throw new DofFailure(400,"缺少发布参数");id(edit.requestId);id(edit.bundleId);
        try(Connection c=access.connection()) {
            Candidate candidate=clients.releaseCandidate(edit.bundleId);verifySource(candidate);Map<String,String> s=read(c);
            if(!revision(s).equals(edit.revision))throw new DofFailure(409,"隔离配置已改变，请刷新");
            if(!manual(s))throw new DofFailure(409,"当前已开启校验或自动更新，不能应用手动配套包");
            Map<String,Object> out=access.context();out.put("revision",revision(s));out.put("bundleId",edit.bundleId);out.put("scriptTaskId",candidate.taskId);
            out.put("sourceSha256",candidate.sourceSha256);out.put("artifactSha256",candidate.artifactSha256);
            out.put("changes",Arrays.asList("网关客户端解密参数","配套服务端解密参数"));out.put("persisted",false);out.put("nativeClientAcceptance",false);return out;
        }catch(SQLException e){throw new DofFailure(503,"隔离配置或审计暂不可用");}
    }
    private void verifySource(Candidate candidate)throws IOException {
        if(gamePvf==null||gamePvf.isEmpty())throw new DofFailure(409,"未绑定隔离游戏 PVF，只能生成候选文件");
        Path p=Paths.get(gamePvf).toAbsolutePath().normalize();
        if(!p.equals(p.toRealPath())||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!NativeScriptJobs.hash(p).equals(candidate.sourceSha256))throw new DofFailure(409,"上传 PVF 与隔离游戏当前来源不同，停止参数应用；不会覆盖游戏 PVF");
    }
    private Map<String,Object> audit(Connection c,String id,boolean snapshots)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT actor,bundle_id,task_id,operation_name,target_request,before_revision,after_revision,before_json,after_json,state,adapter_version,created_at FROM "+AUDIT+" WHERE instance_id=? AND request_id=?")) {
            p.setString(1,instance());p.setString(2,id);try(ResultSet r=p.executeQuery()) {
                if(!r.next())return null;Map<String,Object> out=access.context();out.put("requestId",id);out.put("actor",r.getLong(1));out.put("bundleId",r.getString(2));out.put("taskId",r.getString(3));out.put("operation",r.getString(4));out.put("targetRequest",r.getString(5));out.put("beforeRevision",r.getString(6));out.put("afterRevision",r.getString(7));out.put("state",r.getString(10));out.put("adapter",r.getString(11));out.put("createdAt",r.getString(12));out.put("nativeClientAcceptance",false);out.put("effectState","not_observed");
                if(snapshots){out.put("beforeJson",r.getString(8));out.put("afterJson",r.getString(9));}return out;
            }
        }
    }
    private Map<String,String> before(Map<String,Object> row){return snapshot(row.get("beforeJson").toString(),row.get("beforeRevision").toString());}
    private Map<String,String> after(Map<String,Object> row){return snapshot(row.get("afterJson").toString(),row.get("afterRevision").toString());}
    private void finish(Connection c,String id,String state)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("UPDATE "+AUDIT+" SET state=?,finished_at=CURRENT_TIMESTAMP WHERE instance_id=? AND request_id=?")){p.setString(1,state);p.setString(2,instance());p.setString(3,id);if(p.executeUpdate()!=1)throw new SQLException("Audit row missing");}
    }
    private void compatible(Map<String,Object> row){if(row==null||!DofAdminService.VERSION.equals(row.get("adapter")))throw new DofFailure(409,"发布记录缺失或版本不同");}
    private void write(Connection c,Map<String,String> old,Map<String,String> target)throws SQLException {
        for(String key:changed(old,target)) {
            if(!KEYS.subList(0,2).contains(key))throw new DofFailure(409,"手动配套发布不能更改更新协议字段");
            String[] parts=key.split("\\.");try(PreparedStatement p=c.prepareStatement("UPDATE `qk`.`"+parts[0]+"` SET `"+parts[1]+"`=? WHERE ID=1")) {
                String value=target.get(key);p.setBytes(1,value==null?null:Base64.getDecoder().decode(value));if(p.executeUpdate()!=1)throw new SQLException("Native row changed");
            }
        }
        if(!revision(read(c)).equals(revision(target)))throw new SQLException("Native readback mismatch");
    }
    public Object apply(Edit edit,String restoreId)throws IOException {
        long actor=access.admin();if(edit==null)throw new DofFailure(400,"缺少发布参数");id(edit.requestId);
        if(edit.revision==null||!edit.revision.matches("[0-9a-f]{64}"))throw new DofFailure(400,"缺少发布配置版本");
        if(restoreId==null)id(edit.bundleId);else{id(restoreId);if(edit.bundleId!=null||restoreId.equals(edit.requestId))throw new DofFailure(400,"恢复使用独立编号且不能混入配套包");}
        String operation=restoreId==null?"apply":"restore";boolean prepared=false;
        try(Connection c=access.connection()) {
            // Resolve expensive immutable files before taking MyISAM locks.
            Map<String,Object> oldRequest=audit(c,edit.requestId,false);
            if(oldRequest!=null)return replay(oldRequest,edit,actor,operation,restoreId);
            Candidate candidate=restoreId==null?clients.releaseCandidate(edit.bundleId):null;
            if(candidate!=null)verifySource(candidate);
            lock(c);try {
                oldRequest=audit(c,edit.requestId,false);if(oldRequest!=null)return replay(oldRequest,edit,actor,operation,restoreId);
                try(PreparedStatement p=c.prepareStatement("SELECT request_id FROM "+AUDIT+" WHERE instance_id=? AND state IN ('PREPARED','PARTIAL')")) {
                    p.setString(1,instance());try(ResultSet r=p.executeQuery()){while(r.next())if(!r.getString(1).equals(restoreId))throw new DofFailure(409,"有发布结果待核对，禁止重复应用");}
                }
                Map<String,String> current=read(c),target=new LinkedHashMap<>(current);
                if(!revision(current).equals(edit.revision))throw new DofFailure(409,"隔离配置已改变，请刷新后预览");
                if(candidate!=null) {
                    if(!manual(current))throw new DofFailure(409,"当前已开启原生校验或自动更新，手动配套发布不会自动关闭它们");
                    String value=Base64.getEncoder().encodeToString(candidate.parameter.getBytes(StandardCharsets.US_ASCII));target.put(KEYS.get(0),value);target.put(KEYS.get(1),value);
                }else {
                    Map<String,Object> original=audit(c,restoreId,true);compatible(original);
                    if(!Arrays.asList("PERSISTED","PREPARED","PARTIAL").contains(original.get("state")))throw new DofFailure(409,"该记录没有可恢复的参数修改");
                    Map<String,String> a=before(original),b=after(original);
                    for(String key:changed(a,b)){if(!Objects.equals(current.get(key),a.get(key))&&!Objects.equals(current.get(key),b.get(key)))throw new DofFailure(409,"本次参数已有第三方修改，停止覆盖");target.put(key,a.get(key));}
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO "+AUDIT+" (instance_id,request_id,actor,adapter_version,bundle_id,task_id,operation_name,target_request,before_revision,after_revision,before_json,after_json,state) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'PREPARED')")) {
                    p.setString(1,instance());p.setString(2,edit.requestId);p.setLong(3,actor);p.setString(4,DofAdminService.VERSION);p.setString(5,candidate==null?null:candidate.bundleId);p.setString(6,candidate==null?null:candidate.taskId);p.setString(7,operation);p.setString(8,restoreId);p.setString(9,revision(current));p.setString(10,revision(target));p.setString(11,encode(current));p.setString(12,encode(target));p.executeUpdate();prepared=true;
                }
                write(c,current,target);if(restoreId!=null)finish(c,restoreId,"RESTORED");finish(c,edit.requestId,"PERSISTED");return audit(c,edit.requestId,false);
            }finally{unlock(c);}
        }catch(SQLException e){throw new DofFailure(503,prepared?"应用结果待核对，请保留原操作编号，不要重复提交":"隔离数据库或审计不可用，尚未确认应用");}
    }
    private Object replay(Map<String,Object> row,Edit edit,long actor,String operation,String restore) {
        compatible(row);if(((Number)row.get("actor")).longValue()!=actor||!operation.equals(row.get("operation"))||!Objects.equals(edit.bundleId,row.get("bundleId"))||!Objects.equals(restore,row.get("targetRequest"))||!edit.revision.equals(row.get("beforeRevision")))throw new DofFailure(409,"发布编号已用于不同请求");return row;
    }
    public Object reconcile(String id) {
        access.admin();id(id);try(Connection c=access.connection()){lock(c);try{
            Map<String,Object> row=audit(c,id,true);compatible(row);
            if(Arrays.asList("PREPARED","PARTIAL").contains(row.get("state"))) {
                Map<String,String>a=before(row),b=after(row),current=read(c);boolean allBefore=true,allAfter=true;
                for(String key:changed(a,b)){boolean old=Objects.equals(current.get(key),a.get(key)),next=Objects.equals(current.get(key),b.get(key));if(!old&&!next)throw new DofFailure(409,"存在第三方参数修改，保留现场");allBefore&=old;allAfter&=next;}
                if(allAfter){if(row.get("targetRequest")!=null)finish(c,row.get("targetRequest").toString(),"RESTORED");finish(c,id,"PERSISTED");}
                else finish(c,id,allBefore?"NOT_APPLIED":"PARTIAL");
            }return audit(c,id,false);
        }finally{unlock(c);}}catch(SQLException e){throw new DofFailure(503,"发布结果核对失败，请保留现场");}
    }
    public Object history() {
        access.admin();try(Connection c=access.connection()){List<String> ids=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT request_id FROM "+AUDIT+" WHERE instance_id=? ORDER BY id DESC LIMIT 100")){p.setString(1,instance());try(ResultSet r=p.executeQuery()){while(r.next())ids.add(r.getString(1));}}
            List<Object> rows=new ArrayList<>();for(String id:ids)rows.add(audit(c,id,false));return rows;
        }catch(SQLException e){throw new DofFailure(503,"发布历史暂不可用");}
    }
}
