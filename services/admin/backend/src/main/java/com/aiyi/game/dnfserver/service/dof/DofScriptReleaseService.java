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
    @Resource private ManagedOperationJournal journal;
    @Value("${DOF_GAME_PVF:}") private String gamePvf;
    private static final String AUDIT="`dnf_service`.`DOF_SCRIPT_RELEASE`";
    private static final List<String> KEYS=Arrays.asList("billing.decrypt","config.pvfencryption",
        "config.Scriptmd5","config.Scriptverification","config.updatec","config.updatetype");
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String NS="script.release",CANCEL="script.release.cancel",RESOURCE="script.parameters",PROTOCOL="script-release-preview-v1";
    public static class Edit {public String requestId,revision,bundleId,protocol;}
    public static class CancelEdit {public String requestId,revision,reason;}
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
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT TABLE_SCHEMA,TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE (TABLE_SCHEMA='dof_native' AND TABLE_NAME IN ('billing','config')) OR (TABLE_SCHEMA='dnf_service' AND TABLE_NAME='DOF_SCRIPT_RELEASE')")) {
            while(r.next())engines.put(r.getString(1)+"."+r.getString(2),r.getString(3));
        }
        for(String table:Arrays.asList("billing","config"))if(!Arrays.asList("MyISAM","InnoDB").contains(engines.get("dof_native."+table)))throw new DofFailure(409,"原生配置表引擎不兼容");
        if(!"InnoDB".equals(engines.get("dnf_service.DOF_SCRIPT_RELEASE")))throw new DofFailure(409,"发布审计表尚未准备或引擎不兼容");
        Set<String> valid=new HashSet<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME IN ('billing','config')")) {
            while(r.next())if("varchar".equals(r.getString(3))&&r.getLong(4)>=128)valid.add(r.getString(1)+"."+r.getString(2));
        }
        if(!valid.containsAll(KEYS))throw new DofFailure(409,"原生 Script 字段类型不兼容，不初始化或修改结构");
        for(String table:Arrays.asList("billing","config")) {
            try(PreparedStatement st=c.prepareStatement("SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME=? AND COLUMN_NAME='ID'")){st.setString(1,table);try(ResultSet r=st.executeQuery()){if(!r.next()||!"int".equals(r.getString(1))||r.next())throw new DofFailure(409,"原生配置主键类型不兼容");}}
            try(PreparedStatement st=c.prepareStatement("SELECT COLUMN_NAME,SEQ_IN_INDEX,SUB_PART FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME=? AND INDEX_NAME='PRIMARY'")){st.setString(1,table);try(ResultSet r=st.executeQuery()){if(!r.next()||!"ID".equals(r.getString(1))||r.getInt(2)!=1||r.getObject(3)!=null||r.next())throw new DofFailure(409,"原生配置必须使用单列ID主键");}}
        }
        Map<String,String> expected=new HashMap<>(),actual=new HashMap<>();
        for(String key:Arrays.asList("id","actor"))expected.put(key,"bigint");
        for(String key:Arrays.asList("instance_id","adapter_version","operation_name","state"))expected.put(key,"varchar");
        for(String key:Arrays.asList("request_id","bundle_id","task_id","target_request","before_revision","after_revision"))expected.put(key,"char");
        for(String key:Arrays.asList("before_json","after_json"))expected.put(key,"mediumtext");
        for(String key:Arrays.asList("created_at","finished_at"))expected.put(key,"timestamp");
        try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT COLUMN_NAME,DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='DOF_SCRIPT_RELEASE'")){while(r.next())actual.put(r.getString(1),r.getString(2));}
        if(!expected.equals(actual))throw new DofFailure(409,"私有发布审计结构不兼容，停止写入");
        Map<String,List<String>> unique=new HashMap<>();
        try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT INDEX_NAME,COLUMN_NAME,SEQ_IN_INDEX,SUB_PART FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='DOF_SCRIPT_RELEASE' AND NON_UNIQUE=0 ORDER BY INDEX_NAME,SEQ_IN_INDEX")) {
            while(r.next()){List<String> columns=unique.computeIfAbsent(r.getString(1),key->new ArrayList<>());if(r.getInt(3)!=columns.size()+1||r.getObject(4)!=null)throw new DofFailure(409,"私有发布审计索引不兼容");columns.add(r.getString(2));}
        }
        if(unique.size()!=2||!Collections.singletonList("id").equals(unique.get("PRIMARY"))||!unique.containsValue(Arrays.asList("instance_id","request_id")))throw new DofFailure(409,"私有发布编号约束不兼容");
    }
    private Map<String,String> read(Connection c)throws SQLException {
        schema(c);Map<String,String> found=new HashMap<>(),out=new LinkedHashMap<>();
        for(String table:Arrays.asList("billing","config")) {
            List<String> keys=new ArrayList<>(),columns=new ArrayList<>();for(String key:KEYS)if(key.startsWith(table+".")){keys.add(key);columns.add("BINARY `"+key.substring(table.length()+1)+"`");}
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT ID,"+String.join(",",columns)+" FROM `dof_native`.`"+table+"` LIMIT 2")) {
                if(!r.next()||r.getInt(1)!=1)throw new DofFailure(409,"原生配置行缺失，停止应用");
                for(int i=0;i<keys.size();i++){byte[] value=r.getBytes(i+2);if(value!=null&&value.length>4096)throw new DofFailure(409,"原生 Script 字段超限");found.put(keys.get(i),value==null?null:Base64.getEncoder().encodeToString(value));}
                if(r.next())throw new DofFailure(409,"原生配置存在多行，停止应用");
            }
        }
        for(String key:KEYS)out.put(key,found.get(key));return out;
    }
    private static List<String> changed(Map<String,String>a,Map<String,String>b){List<String> out=new ArrayList<>();for(String k:KEYS)if(!Objects.equals(a.get(k),b.get(k)))out.add(k);return out;}
    private void lock(Connection c)throws SQLException{
        try{journal.lock(c,"dof_native.billing WRITE,dof_native.config WRITE,"+AUDIT+" WRITE,`d_taiwan`.`accounts` READ");}
        catch(SQLException|RuntimeException failure){try{unlock(c);}catch(SQLException|RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    private void unlock(Connection c)throws SQLException{
        try(Statement s=c.createStatement()){s.execute("UNLOCK TABLES");}
        catch(SQLException|RuntimeException failure){discard(c,failure);throw failure;}
    }
    private void discard(Connection c,Throwable original) {
        try{Connection physical=c.unwrap(Connection.class);if(physical==null)throw new SQLException("Missing driver connection");physical.close();}
        catch(SQLException|RuntimeException failure){original.addSuppressed(failure);try{c.abort(Runnable::run);}catch(SQLException|RuntimeException abort){original.addSuppressed(abort);}}
    }
    public Object status() {
        access.admin();try(Connection c=journal.connection()) {
            Map<String,String>s=read(c);Map<String,Object> out=access.context();out.put("revision",revision(s));
            out.put("parameterCopiesEqual",Objects.equals(s.get(KEYS.get(0)),s.get(KEYS.get(1))));
            out.put("manualModeCompatible",manual(s));out.put("sourceConfigured",gamePvf!=null&&!gamePvf.isEmpty());out.put("nativeClientAcceptance",false);out.put("protocol",PROTOCOL);return out;
        }catch(SQLException e){throw new DofFailure(503,"隔离发布配置或审计表不可用");}
    }
    private static boolean manual(Map<String,String>s){return "0".equals(text(s,"config.Scriptverification"))&&"0".equals(text(s,"config.updatec"));}
    public Object preview(Edit edit)throws IOException {
        return preview(edit,null);
    }
    public Object preview(Edit edit,String restoreId)throws IOException {
        long actor=access.admin();validate(edit,restoreId);
        try(Connection c=journal.connection()) {
            ManagedOperationJournal.Entry existing=journal.find(c,edit.requestId);
            if(existing!=null){same(existing,edit,restoreId,actor);return result(c,existing,actor);}
            Candidate candidate=restoreId==null?clients.releaseCandidate(edit.bundleId):null;
            if(candidate!=null)verifySource(candidate);
            lock(c);try {
                writeGuard(c,actor);existing=journal.find(c,edit.requestId);
                if(existing!=null){same(existing,edit,restoreId,actor);return result(c,existing,actor);}
                if(audit(c,edit.requestId,false)!=null)throw new DofFailure(409,"编号属于旧发布记录，请从历史核对");
                pending(c,restoreId);cancellationBarrier(c,edit.requestId);Map<String,String> current=read(c);
                if(!revision(current).equals(edit.revision))throw new DofFailure(409,"隔离配置已改变，请刷新");
                Map<String,String> target=target(c,current,candidate,restoreId);
                ManagedOperationJournal.Entry e=journal.preview(c,edit.requestId,actor,NS,RESOURCE,"1",restoreId==null?"apply":"restore",restoreId,
                    fingerprint(edit,restoreId),"",Collections.singletonMap("revision",revision(current)),summary(c,target,candidate,restoreId));
                return result(c,e,actor);
            }finally{unlock(c);}
        }catch(SQLException e){throw new DofFailure(503,"隔离配置或审计暂不可用");}
    }
    private void validate(Edit edit,String restoreId) {
        if(edit==null)throw new DofFailure(400,"缺少发布参数");if(!PROTOCOL.equals(edit.protocol))throw new DofFailure(400,"配套发布协议版本不匹配，请同步更新管理页面");id(edit.requestId);
        if(edit.revision==null||!edit.revision.matches("[0-9a-f]{64}"))throw new DofFailure(400,"缺少发布配置版本");
        if(restoreId==null)id(edit.bundleId);else {id(restoreId);if(edit.bundleId!=null||restoreId.equals(edit.requestId))throw new DofFailure(400,"恢复必须使用独立编号且不能混入配套包");}
    }
    private void same(ManagedOperationJournal.Entry e,Edit edit,String restoreId,long actor) {
        journal.same(e,NS,actor,fingerprint(edit,restoreId));
        if(!RESOURCE.equals(e.resource)||!"1".equals(e.target)||!Objects.equals(e.targetRequest,restoreId)||!Objects.equals(e.operation,restoreId==null?"apply":"restore"))throw new DofFailure(409,"发布操作归属或类型不一致");
    }
    private String fingerprint(Edit edit,String restoreId){return journal.hash(Arrays.asList(PROTOCOL,edit.protocol,edit.requestId,edit.revision,edit.bundleId,restoreId));}
    private Map<String,Object> metadata(String text) {
        try{return JSON.readValue(text,new TypeReference<Map<String,Object>>(){});}catch(IOException error){throw new DofFailure(409,"发布元数据损坏");}
    }
    private void pending(Connection c,String allowed)throws SQLException {
        journal.noPending(c,RESOURCE,allowed);
        try(PreparedStatement st=c.prepareStatement("SELECT request_id FROM "+AUDIT+" WHERE instance_id=? AND state IN ('PREPARED','PARTIAL')")) {
            st.setString(1,instance());try(ResultSet r=st.executeQuery()){while(r.next())if(!r.getString(1).equals(allowed))throw new DofFailure(409,"有发布结果待核对，禁止重复应用");}
        }
    }
    private Map<String,String> target(Connection c,Map<String,String> current,Candidate candidate,String restore)throws SQLException {
        Map<String,String> out=new LinkedHashMap<>(current);
        if(candidate!=null) {
            if(!manual(current))throw new DofFailure(409,"当前已开启原生校验或自动更新，手动配套发布不会自动关闭它们");
            NativeScriptParameters.decode(candidate.parameter,NativeScriptContext.CLIENT_DLL_SHA256);
            String value=Base64.getEncoder().encodeToString(candidate.parameter.getBytes(StandardCharsets.US_ASCII));out.put(KEYS.get(0),value);out.put(KEYS.get(1),value);
        }else {
            Map<String,Object> original=audit(c,restore,true);compatible(original);
            ManagedOperationJournal.Entry source=journal.find(c,restore);if(source!=null){if(!NS.equals(source.namespace))throw new DofFailure(409,"恢复来源类型不一致");privateMatches(source,original);}
            if(!"apply".equals(original.get("operation"))||!Arrays.asList("PERSISTED","PREPARED","PARTIAL").contains(original.get("state")))throw new DofFailure(409,"该记录没有可恢复的参数应用");
            Map<String,String> a=before(original),b=after(original);
            for(String key:changed(a,b)){if(!KEYS.subList(0,2).contains(key)||(!Objects.equals(current.get(key),a.get(key))&&!Objects.equals(current.get(key),b.get(key))))throw new DofFailure(409,"本次参数已有第三方修改，停止覆盖");out.put(key,a.get(key));}
        }
        try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT TABLE_NAME,COLUMN_NAME,CHARACTER_MAXIMUM_LENGTH,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME IN ('billing','config')")) {
            while(r.next()) {String key=r.getString(1)+"."+r.getString(2);if(!KEYS.subList(0,2).contains(key))continue;String value=out.get(key);
                if(value==null&&!"YES".equals(r.getString(4))||value!=null&&Base64.getDecoder().decode(value).length>r.getLong(3))throw new DofFailure(409,"参数与当前字段长度或空值约束不兼容");}
        }
        return out;
    }
    private Map<String,Object> summary(Connection c,Map<String,String> target,Candidate candidate,String restore)throws SQLException {
        Map<String,Object> out=new LinkedHashMap<>();out.put("revision",revision(target));out.put("bundleId",candidate==null?null:candidate.bundleId);out.put("taskId",candidate==null?null:candidate.taskId);
        out.put("sourceSha256",candidate==null?null:candidate.sourceSha256);out.put("artifactSha256",candidate==null?null:candidate.artifactSha256);out.put("targetRequest",restore);
        List<Object> shape=new ArrayList<>();
        try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dof_native' AND ((TABLE_NAME='billing' AND COLUMN_NAME IN ('ID','decrypt')) OR (TABLE_NAME='config' AND COLUMN_NAME IN ('ID','pvfencryption','Scriptmd5','Scriptverification','updatec','updatetype'))) ORDER BY TABLE_NAME,COLUMN_NAME")) {while(r.next())shape.add(Arrays.asList(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)));}
        out.put("schemaRevision",journal.hash(shape));return out;
    }
    private void writeGuard(Connection c,long actor)throws SQLException {
        schema(c);journal.metadataTransactionGuard(c);
        try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT COLUMN_NAME,DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='d_taiwan' AND TABLE_NAME='accounts' AND COLUMN_NAME IN ('UID','admin')")) {
            Map<String,String> fields=new HashMap<>();while(r.next())fields.put(r.getString(1),r.getString(2));
            if(!"int".equals(fields.get("UID"))||!"int".equals(fields.get("admin")))throw new DofFailure(409,"管理员表结构不兼容");
        }
        try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT COLUMN_NAME,SEQ_IN_INDEX,SUB_PART FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='d_taiwan' AND TABLE_NAME='accounts' AND INDEX_NAME='PRIMARY'")){if(!r.next()||!"UID".equals(r.getString(1))||r.getInt(2)!=1||r.getObject(3)!=null||r.next())throw new DofFailure(409,"管理员表主键不兼容");}
        try(PreparedStatement st=c.prepareStatement("SELECT admin FROM `d_taiwan`.`accounts` WHERE UID=?")){st.setLong(1,actor);try(ResultSet r=st.executeQuery()){if(!r.next()||r.getInt(1)==0||r.wasNull()||r.next())throw new DofFailure(403,"管理员权限已改变，停止发布操作");}}
        for(String table:Arrays.asList("billing","config","DOF_SCRIPT_RELEASE")) {
            String db=table.equals("DOF_SCRIPT_RELEASE")?"dnf_service":"dof_native";
            triggerVisibility(c,db,table);
            try(PreparedStatement st=c.prepareStatement("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE EVENT_OBJECT_SCHEMA=? AND EVENT_OBJECT_TABLE=?")){st.setString(1,db);st.setString(2,table);try(ResultSet r=st.executeQuery()){if(!r.next()||r.getInt(1)!=0)throw new DofFailure(409,"发布表存在未知触发器，停止写入");}}
            try(PreparedStatement st=c.prepareStatement("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_NAME IS NOT NULL AND ((TABLE_SCHEMA=? AND TABLE_NAME=?) OR (REFERENCED_TABLE_SCHEMA=? AND REFERENCED_TABLE_NAME=?))")){st.setString(1,db);st.setString(2,table);st.setString(3,db);st.setString(4,table);try(ResultSet r=st.executeQuery()){if(!r.next()||r.getInt(1)!=0)throw new DofFailure(409,"发布表存在未知外键，停止写入");}}
        }
    }
    private void triggerVisibility(Connection c,String db,String table)throws SQLException {
        String who;try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT CURRENT_USER()")){if(!r.next())throw new SQLException("Missing database identity");who=r.getString(1);}
        int at=who.lastIndexOf('@');if(at<1)throw new SQLException("Invalid database identity");String grantee="'"+who.substring(0,at).replace("'","''")+"'@'"+who.substring(at+1).replace("'","''")+"'";
        try(PreparedStatement st=c.prepareStatement("SELECT (EXISTS(SELECT 1 FROM information_schema.USER_PRIVILEGES WHERE GRANTEE=? AND PRIVILEGE_TYPE='TRIGGER') OR EXISTS(SELECT 1 FROM information_schema.SCHEMA_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA IN (?,?) AND PRIVILEGE_TYPE='TRIGGER') OR EXISTS(SELECT 1 FROM information_schema.TABLE_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA=? AND TABLE_NAME=? AND PRIVILEGE_TYPE='TRIGGER'))")) {
            st.setString(1,grantee);st.setString(2,grantee);st.setString(3,db);st.setString(4,db.replace("_","\\_"));st.setString(5,grantee);st.setString(6,db);st.setString(7,table);
            try(ResultSet r=st.executeQuery()){if(!r.next()||!r.getBoolean(1))throw new DofFailure(409,"无法确认发布表触发器，停止写入");}
        }
    }
    private Map<String,Object> proof(ManagedOperationJournal.Entry e) {
        Map<String,Object> out=new LinkedHashMap<>();out.put("requestId",e.requestId);out.put("state",e.state);out.put("actor",e.actor);out.put("operation",e.operation);out.put("targetRequest",e.targetRequest);out.put("requestHash",e.requestHash);out.put("beforeHash",e.beforeHash);out.put("afterHash",e.afterHash);return out;
    }
    private Map<String,Object> result(Connection c,ManagedOperationJournal.Entry e,long actor)throws SQLException {
        Map<String,Object> out=access.context();out.putAll(journal.result(e));out.put("protocol",PROTOCOL);out.put("journalState",e.state);out.put("nativeClientAcceptance",false);out.put("receiptRevision",journal.hash(proof(e)));out.put("requestReplayAvailable",false);
        if(NS.equals(e.namespace)) {
            Map<String,Object> a=metadata(e.beforeJson),b=metadata(e.afterJson);out.put("beforeRevision",a.get("revision"));out.put("revision",a.get("revision"));out.put("afterRevision",b.get("revision"));
            for(String key:Arrays.asList("bundleId","taskId","sourceSha256","artifactSha256"))out.put(key,b.get(key));
            Map<String,Object> nativeRow=audit(c,e.requestId,false);out.put("nativeState",nativeRow==null?"NOT_SUBMITTED":nativeRow.get("state"));
            if(nativeRow!=null&&"RESTORED".equals(nativeRow.get("state")))out.put("state","RESTORED");
            if(e.actor==actor&&"PREVIEWED".equals(e.state)){Map<String,Object> request=new LinkedHashMap<>();request.put("protocol",PROTOCOL);request.put("requestId",e.requestId);request.put("revision",a.get("revision"));request.put("bundleId",b.get("bundleId"));out.put("request",request);out.put("requestReplayAvailable",true);}
        }else {
            out.put("sourceRequest",e.targetRequest);
            if(e.actor==actor&&"PREVIEWED".equals(e.state)){Map<String,Object> request=new LinkedHashMap<>();request.put("requestId",e.requestId);request.put("revision",e.beforeHash);request.put("reason",e.reason);out.put("cancelRequest",request);}
        }
        return out;
    }
    private void privateMatches(ManagedOperationJournal.Entry e,Map<String,Object> row) {
        if(!Objects.equals(e.operation,row.get("operation"))||e.actor!=((Number)row.get("actor")).longValue()||!Objects.equals(e.targetRequest,row.get("targetRequest"))
            ||!Objects.equals(metadata(e.afterJson).get("bundleId"),row.get("bundleId"))||!Objects.equals(metadata(e.afterJson).get("taskId"),row.get("taskId"))
            ||!Objects.equals(metadata(e.beforeJson).get("revision"),row.get("beforeRevision"))||!Objects.equals(metadata(e.afterJson).get("revision"),row.get("afterRevision")))throw new DofFailure(409,"私有发布快照与操作日志不一致，停止核对");
    }
    private ManagedOperationJournal.Entry complete(Connection c,ManagedOperationJournal.Entry e,String state,long actor)throws SQLException {
        if(e.state.equals(state))return e;
        if("PREPARED".equals(e.state))return journal.finish(c,e,state,actor);
        if(!"CONFLICT".equals(e.state)||!Arrays.asList("PERSISTED","NOT_APPLIED").contains(state))throw new DofFailure(409,"发布核对状态冲突");
        try(PreparedStatement st=c.prepareStatement("UPDATE "+ManagedOperationJournal.TABLE+" SET state=?,checked_by=?,finished_at=NOW() WHERE instance_id=? AND request_id=? AND namespace=? AND state='CONFLICT' AND before_hash=? AND after_hash=?")) {
            st.setString(1,state);st.setLong(2,actor);st.setString(3,instance());st.setString(4,e.requestId);st.setString(5,NS);st.setString(6,e.beforeHash);st.setString(7,e.afterHash);if(st.executeUpdate()!=1)throw new SQLException("Conflict receipt changed");
        }return journal.require(c,NS,e.requestId);
    }
    private void closeRestoredSource(Connection c,ManagedOperationJournal.Entry restore,long actor)throws SQLException {
        if(!NS.equals(restore.namespace)||!"restore".equals(restore.operation)||!"PERSISTED".equals(restore.state))return;
        ManagedOperationJournal.Entry source=journal.find(c,restore.targetRequest);if(source==null||!Arrays.asList("PREPARED","CONFLICT").contains(source.state))return;
        Map<String,Object> original=audit(c,source.requestId,false);
        if(!NS.equals(source.namespace)||!"apply".equals(source.operation)||original==null||!"RESTORED".equals(original.get("state")))throw new DofFailure(409,"恢复来源不一致");
        try(PreparedStatement st=c.prepareStatement("UPDATE "+ManagedOperationJournal.TABLE+" SET state='ACKNOWLEDGED',checked_by=?,finished_at=NOW() WHERE instance_id=? AND request_id=? AND namespace=? AND state=? AND before_hash=? AND after_hash=?")) {
            st.setLong(1,actor);st.setString(2,instance());st.setString(3,source.requestId);st.setString(4,NS);st.setString(5,source.state);st.setString(6,source.beforeHash);st.setString(7,source.afterHash);if(st.executeUpdate()!=1)throw new SQLException("Restored source changed");
        }
    }
    private String cancelFingerprint(String source,CancelEdit edit){return journal.hash(Arrays.asList(PROTOCOL,source,edit.requestId,edit.revision,edit.reason));}
    public Object cancelPreview(String source,CancelEdit edit,boolean commit) {
        long actor=access.admin();id(source);if(edit==null)throw new DofFailure(400,"缺少取消参数");id(edit.requestId);
        if(source.equals(edit.requestId)||edit.revision==null||!edit.revision.matches("[0-9a-f]{64}")||edit.reason==null||edit.reason.trim().isEmpty()||edit.reason.length()>256)throw new DofFailure(400,"取消需要独立编号、记录版本及原因");
        boolean discarded=false;
        try(Connection c=journal.connection()) {lock(c);try {
            writeGuard(c,actor);ManagedOperationJournal.Entry control=journal.find(c,edit.requestId);
            if(control!=null){journal.same(control,CANCEL,actor,cancelFingerprint(source,edit));if(!"PREVIEWED".equals(control.state))return result(c,control,actor);}
            else if(audit(c,edit.requestId,false)!=null)throw new DofFailure(409,"取消编号属于旧发布记录，不能复用");
            ManagedOperationJournal.Entry original=journal.require(c,NS,source);
            if(!"PREVIEWED".equals(original.state)||!journal.hash(proof(original)).equals(edit.revision))throw new DofFailure(409,"只能取消版本匹配的未提交预览");
            String resource=RESOURCE+":cancel:"+source;journal.noPending(c,resource,null);
            if(!commit)return result(c,journal.preview(c,edit.requestId,actor,CANCEL,resource,source,"cancel-preview",source,cancelFingerprint(source,edit),edit.reason,proof(original),proof(original)),actor);
            if(control==null)throw new DofFailure(404,"请先预览取消操作");
            control=journal.prepare(c,control,source,proof(original),proof(original));c.setAutoCommit(false);lock(c);
            try {
                writeGuard(c,actor);control=journal.require(c,CANCEL,edit.requestId);original=journal.require(c,NS,source);
                if(!"PREPARED".equals(control.state)||!journal.hash(proof(original)).equals(control.beforeHash))throw new DofFailure(409,"取消记录或来源已改变，请核对原编号");
                journal.cancelPreviewInTransaction(c,original,actor);
                Map<String,Object> outcome=new LinkedHashMap<>();outcome.put("sourceRequest",source);outcome.put("sourceState","NOT_APPLIED");outcome.put("nativeDataChanged",false);
                control=journal.finishMetadataTransaction(c,control,outcome,actor);c.commit();return result(c,control,actor);
            }catch(SQLException|RuntimeException failure) {
                try{c.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);discarded=true;try{c.unwrap(Connection.class).close();}catch(SQLException close){failure.addSuppressed(close);c.abort(Runnable::run);}}
                throw new DofFailure(503,"取消结果待核对，请保留取消操作编号，不要创建新的应用");
            }
        }finally{if(!discarded&&!c.isClosed()){unlock(c);if(!c.getAutoCommit())c.setAutoCommit(true);}}}catch(SQLException error){throw new DofFailure(503,"取消结果待核对，请保留原编号");}
    }
    private Object reconcileCancel(Connection c,ManagedOperationJournal.Entry e,long actor)throws SQLException {
        if(!CANCEL.equals(e.namespace))throw new DofFailure(404,"未找到取消记录");
        if("PREPARED".equals(e.state)) {
            ManagedOperationJournal.Entry source=journal.require(c,NS,e.targetRequest);
            if(!"PREVIEWED".equals(source.state)||!journal.hash(proof(source)).equals(e.beforeHash))throw new DofFailure(409,"取消元数据不一致，保留现场");
            e=journal.finish(c,e,"NOT_APPLIED",actor);
        }
        return result(c,e,actor);
    }
    private void cancellationBarrier(Connection c,String source)throws SQLException {
        try(PreparedStatement st=c.prepareStatement("SELECT COUNT(*) FROM "+ManagedOperationJournal.TABLE+" WHERE instance_id=? AND namespace=? AND target_request=? AND state IN ('PREPARED','CONFLICT')")) {
            st.setString(1,instance());st.setString(2,CANCEL);st.setString(3,source);try(ResultSet r=st.executeQuery()){if(!r.next()||r.getInt(1)!=0)throw new DofFailure(409,"该预览有取消结果待核对，停止应用");}
        }
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
                if(snapshots){out.put("beforeJson",r.getString(8));out.put("afterJson",r.getString(9));}if(r.next())throw new DofFailure(409,"发布记录编号不唯一，停止操作");return out;
            }
        }
    }
    private Map<String,String> before(Map<String,Object> row){return snapshot(row.get("beforeJson").toString(),row.get("beforeRevision").toString());}
    private Map<String,String> after(Map<String,Object> row){return snapshot(row.get("afterJson").toString(),row.get("afterRevision").toString());}
    private void finish(Connection c,String id,String state)throws SQLException {
        Map<String,Object> row=audit(c,id,false);compatible(row);String old=row.get("state").toString();
        if(old.equals(state))return;
        if(!Arrays.asList("PREPARED","PARTIAL").contains(old)&&!("RESTORED".equals(state)&&"PERSISTED".equals(old)))throw new DofFailure(409,"发布记录状态已改变，停止完成记录");
        try(PreparedStatement p=c.prepareStatement("UPDATE "+AUDIT+" SET state=?,finished_at=CURRENT_TIMESTAMP WHERE instance_id=? AND request_id=? AND state=?")){p.setString(1,state);p.setString(2,instance());p.setString(3,id);p.setString(4,old);if(p.executeUpdate()!=1)throw new SQLException("Audit row changed");}
    }
    private void compatible(Map<String,Object> row){if(row==null||!DofAdminService.VERSION.equals(row.get("adapter"))||!Arrays.asList("apply","restore").contains(row.get("operation"))||!Arrays.asList("PREPARED","PARTIAL","PERSISTED","RESTORED","NOT_APPLIED").contains(row.get("state")))throw new DofFailure(409,"发布记录缺失、状态或版本不同");}
    private void write(Connection c,Map<String,String> old,Map<String,String> target)throws SQLException {
        for(String key:changed(old,target)) {
            if(!KEYS.subList(0,2).contains(key))throw new DofFailure(409,"手动配套发布不能更改更新协议字段");
            String[] parts=key.split("\\.");try(PreparedStatement p=c.prepareStatement("UPDATE `dof_native`.`"+parts[0]+"` SET `"+parts[1]+"`=? WHERE ID=1")) {
                String value=target.get(key);p.setBytes(1,value==null?null:Base64.getDecoder().decode(value));if(p.executeUpdate()!=1)throw new SQLException("Native row changed");
            }
        }
        if(!revision(read(c)).equals(revision(target)))throw new SQLException("Native readback mismatch");
    }
    public Object apply(Edit edit,String restoreId)throws IOException {
        long actor=access.admin();validate(edit,restoreId);boolean prepared=false;
        try(Connection c=journal.connection()) {
            ManagedOperationJournal.Entry e=journal.find(c,edit.requestId);
            if(e==null){Map<String,Object> old=audit(c,edit.requestId,false);if(old!=null)return replay(old,edit,actor,restoreId==null?"apply":"restore",restoreId);throw new DofFailure(409,"请先使用同一操作编号完成持久化预览");}
            same(e,edit,restoreId,actor);
            if(!"PREVIEWED".equals(e.state))return result(c,e,actor);
            Candidate candidate=restoreId==null?clients.releaseCandidate(edit.bundleId):null;
            if(candidate!=null)verifySource(candidate);
            lock(c);try {
                writeGuard(c,actor);e=journal.require(c,NS,edit.requestId);same(e,edit,restoreId,actor);
                if(!"PREVIEWED".equals(e.state))return result(c,e,actor);
                pending(c,restoreId);cancellationBarrier(c,edit.requestId);Map<String,String> current=read(c);
                if(!revision(current).equals(edit.revision))throw new DofFailure(409,"隔离配置已改变，请重新预览");
                Map<String,String> target=target(c,current,candidate,restoreId);
                if(!journal.hash(summary(c,target,candidate,restoreId)).equals(e.afterHash))throw new DofFailure(409,"配套来源或恢复内容已改变，停止应用");
                if(audit(c,edit.requestId,false)!=null)throw new DofFailure(409,"私有发布记录与未提交预览冲突，请核对");
                e=journal.prepare(c,e,"1",metadata(e.beforeJson),metadata(e.afterJson));prepared=true;
                try(PreparedStatement st=c.prepareStatement("INSERT INTO "+AUDIT+" (instance_id,request_id,actor,adapter_version,bundle_id,task_id,operation_name,target_request,before_revision,after_revision,before_json,after_json,state) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'PREPARED')")) {
                    st.setString(1,instance());st.setString(2,edit.requestId);st.setLong(3,actor);st.setString(4,DofAdminService.VERSION);st.setString(5,candidate==null?null:candidate.bundleId);st.setString(6,candidate==null?null:candidate.taskId);st.setString(7,e.operation);st.setString(8,restoreId);st.setString(9,revision(current));st.setString(10,revision(target));st.setString(11,encode(current));st.setString(12,encode(target));st.executeUpdate();
                }
                write(c,current,target);if(restoreId!=null)finish(c,restoreId,"RESTORED");finish(c,edit.requestId,"PERSISTED");
                e=journal.finish(c,e,"PERSISTED",actor);closeRestoredSource(c,e,actor);return result(c,e,actor);
            }finally{unlock(c);}
        }catch(SQLException error){throw new DofFailure(503,prepared?"应用结果待核对，请保留原操作编号，不要重复提交":"隔离数据库或审计不可用，尚未确认应用");}
    }
    private Object replay(Map<String,Object> row,Edit edit,long actor,String operation,String restore) {
        compatible(row);if(((Number)row.get("actor")).longValue()!=actor||!operation.equals(row.get("operation"))||!Objects.equals(edit.bundleId,row.get("bundleId"))||!Objects.equals(restore,row.get("targetRequest"))||!edit.revision.equals(row.get("beforeRevision")))throw new DofFailure(409,"发布编号已用于不同请求");return row;
    }
    public Object reconcile(String id) {
        long actor=access.admin();id(id);
        try(Connection c=journal.connection()) {lock(c);try {
            writeGuard(c,actor);ManagedOperationJournal.Entry e=journal.find(c,id);
            if(e!=null&&CANCEL.equals(e.namespace))return reconcileCancel(c,e,actor);
            if(e!=null&&!NS.equals(e.namespace))throw new DofFailure(404,"未找到发布记录");
            Map<String,Object> row=audit(c,id,true);
            if(e!=null&&!Arrays.asList("PREPARED","CONFLICT").contains(e.state)){closeRestoredSource(c,e,actor);return result(c,e,actor);}
            if(row==null){if(e==null)throw new DofFailure(404,"未找到发布记录");e=complete(c,e,"NOT_APPLIED",actor);return result(c,e,actor);}
            compatible(row);
            if(e!=null)privateMatches(e,row);
            if(!Arrays.asList("PREPARED","PARTIAL").contains(row.get("state"))) {
                if(e==null)return audit(c,id,false);
                e=complete(c,e,"RESTORED".equals(row.get("state"))?"PERSISTED":row.get("state").toString(),actor);closeRestoredSource(c,e,actor);return result(c,e,actor);
            }
            Map<String,String> a=before(row),b=after(row),current=read(c);boolean allBefore=true,allAfter=true;
            for(String key:changed(a,b)){allBefore&=Objects.equals(current.get(key),a.get(key));allAfter&=Objects.equals(current.get(key),b.get(key));}
            String state=allAfter?"PERSISTED":allBefore?"NOT_APPLIED":"PARTIAL";
            if(Arrays.asList("PREPARED","PARTIAL").contains(row.get("state"))){if(allAfter&&row.get("targetRequest")!=null)finish(c,row.get("targetRequest").toString(),"RESTORED");finish(c,id,state);}
            if(e==null)return audit(c,id,false);
            e=complete(c,e,allAfter?"PERSISTED":allBefore?"NOT_APPLIED":"CONFLICT",actor);closeRestoredSource(c,e,actor);return result(c,e,actor);
        }finally{unlock(c);}}catch(SQLException error){throw new DofFailure(503,"发布结果核对失败，请保留现场");}
    }
    public Object operation(String id) {
        long actor=access.admin();id(id);try(Connection c=journal.connection()) {
            ManagedOperationJournal.Entry e=journal.find(c,id);if(e!=null){if(!Arrays.asList(NS,CANCEL).contains(e.namespace))throw new DofFailure(404,"未找到发布记录");return result(c,e,actor);}
            Map<String,Object> old=audit(c,id,false);if(old==null)throw new DofFailure(404,"未找到发布记录");return old;
        }catch(SQLException error){throw new DofFailure(503,"发布记录暂不可用");}
    }
    public Object history() {
        long actor=access.admin();try(Connection c=journal.connection()) {
            Map<String,Object> rows=new LinkedHashMap<>();
            for(String ns:Arrays.asList(NS,CANCEL))for(ManagedOperationJournal.Entry e:journal.history(c,ns))rows.put(e.requestId,result(c,e,actor));
            try(PreparedStatement st=c.prepareStatement("SELECT request_id FROM "+AUDIT+" WHERE instance_id=? ORDER BY (state IN ('PREPARED','PARTIAL')) DESC,id DESC LIMIT 100")) {
                st.setString(1,instance());try(ResultSet r=st.executeQuery()){while(r.next()){String id=r.getString(1);if(!rows.containsKey(id))rows.put(id,audit(c,id,false));}}
            }
            List<Object> out=new ArrayList<>(rows.values());out.sort((a,b)->{Map<?,?> x=(Map<?,?>)a,y=(Map<?,?>)b;List<String> pending=Arrays.asList("PREPARED","PARTIAL","CONFLICT");int first=Boolean.compare(pending.contains(y.get("state")),pending.contains(x.get("state")));return first!=0?first:String.valueOf(y.get("createdAt")).compareTo(String.valueOf(x.get("createdAt")));});return out;
        }catch(SQLException error){throw new DofFailure(503,"发布历史暂不可用");}
    }
}
