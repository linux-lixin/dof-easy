package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Durable protocol only. Native SQL and business rules stay in typed services. */
@Service
public class ManagedOperationJournal {
    public static final String TABLE="`dnf_service`.`DOF_OPERATION_JOURNAL`";
    @Resource private DofAdminService access;
    private final ObjectMapper json=new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    public static class Entry {
        public String requestId,namespace,resource,target,operation,targetRequest,requestHash,beforeHash,afterHash,beforeJson,afterJson,state,createdAt,reason;
        public long actor;
    }
    public long admin(){return access.admin();}
    public Connection connection()throws SQLException{
        access.admin();Connection c=access.boundConnection();
        if(!c.getAutoCommit()){c.close();throw new DofFailure(409,"管理写入连接必须启用自动提交以持久化写前记录；停止操作");}
        try(Statement s=c.createStatement()){s.execute("SET SESSION sql_mode=CONCAT_WS(',',NULLIF(@@SESSION.sql_mode,''),'STRICT_ALL_TABLES')");return c;}
        catch(SQLException | RuntimeException e){c.close();throw e;}
    }
    public String instance(){return access.context().get("instanceId").toString();}
    public static String id(String value) {
        if(value==null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new DofFailure(400,"缺少有效操作编号");
        return value.toLowerCase(Locale.ROOT);
    }
    public String encode(Object value) {
        try {
            String text=json.writeValueAsString(value);
            if(text.length()>2*1024*1024 || !StandardCharsets.UTF_8.newEncoder().canEncode(text))throw new DofFailure(400,"操作内容过大或包含无效 Unicode 文字");
            return text;
        }catch(java.io.IOException e){throw new DofFailure(400,"操作内容无法编码");}
    }
    public String hash(Object value){return digest(encode(value));}
    private String digest(String value){return NativeUiState.sha256(value.getBytes(StandardCharsets.UTF_8));}
    public void schema(Connection c)throws SQLException {
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='DOF_OPERATION_JOURNAL'")) {
            if(!r.next() || !"InnoDB".equals(r.getString(1)) || r.next())throw new DofFailure(409,"管理操作日志迁移缺失或引擎不兼容，请先更新管理表；未修改原生数据");
        }
        Map<String,String> types=new HashMap<>();Set<String> primary=new HashSet<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COLUMN_NAME,DATA_TYPE,COLUMN_KEY FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='DOF_OPERATION_JOURNAL'")) {
            while(r.next()){types.put(r.getString(1),r.getString(2));if("PRI".equals(r.getString(3)))primary.add(r.getString(1));}
        }
        for(String key:Arrays.asList("instance_id","namespace","resource_key","target_key","operation_name","state","reason"))if(!"varchar".equals(types.get(key)))throw badSchema();
        for(String key:Arrays.asList("request_id","target_request","request_hash","before_hash","after_hash"))if(!"char".equals(types.get(key)))throw badSchema();
        for(String key:Arrays.asList("before_json","after_json"))if(!"mediumtext".equals(types.get(key)))throw badSchema();
        if(!"bigint".equals(types.get("actor")) || !"bigint".equals(types.get("checked_by")) || !"timestamp".equals(types.get("created_at")) || !"timestamp".equals(types.get("finished_at"))
            || !primary.equals(new HashSet<>(Arrays.asList("instance_id","request_id"))))throw badSchema();
    }
    private DofFailure badSchema(){return new DofFailure(409,"管理操作日志结构不兼容，停止写入");}
    public void lock(Connection c,String nativeLocks)throws SQLException {
        schema(c);
        try(Statement s=c.createStatement()){s.execute("LOCK TABLES "+nativeLocks+","+TABLE+" WRITE");}
        schema(c);
    }
    public void unlock(Connection c)throws SQLException {try(Statement s=c.createStatement()){s.execute("UNLOCK TABLES");}}
    private Entry entry(ResultSet r)throws SQLException {
        Entry e=new Entry();e.requestId=r.getString("request_id");e.actor=r.getLong("actor");e.namespace=r.getString("namespace");e.resource=r.getString("resource_key");e.target=r.getString("target_key");
        e.operation=r.getString("operation_name");e.targetRequest=r.getString("target_request");e.requestHash=r.getString("request_hash");e.beforeHash=r.getString("before_hash");e.afterHash=r.getString("after_hash");
        e.beforeJson=r.getString("before_json");e.afterJson=r.getString("after_json");e.state=r.getString("state");e.createdAt=r.getString("created_at");e.reason=r.getString("reason");
        if(e.beforeJson==null || e.afterJson==null || !digest(e.beforeJson).equals(e.beforeHash) || !digest(e.afterJson).equals(e.afterHash)
            || !Arrays.asList("PREVIEWED","PREPARED","PERSISTED","NOT_APPLIED","CONFLICT","NO_CHANGE","ACKNOWLEDGED").contains(e.state))
            throw new DofFailure(409,"操作日志快照或状态损坏，停止恢复");
        return e;
    }
    public Entry find(Connection c,String requestId)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT * FROM "+TABLE+" WHERE instance_id=? AND request_id=?")) {
            p.setString(1,instance());p.setString(2,id(requestId));try(ResultSet r=p.executeQuery()){if(!r.next())return null;Entry e=entry(r);if(r.next())throw badSchema();return e;}
        }
    }
    public Entry require(Connection c,String namespace,String requestId)throws SQLException {
        Entry e=find(c,requestId);if(e==null || !namespace.equals(e.namespace))throw new DofFailure(404,"未找到当前模块的操作记录");return e;
    }
    public void same(Entry e,String namespace,long actor,String fingerprint) {
        if(!e.namespace.equals(namespace) || e.actor!=actor || !e.requestHash.equals(fingerprint))throw new DofFailure(409,"操作编号已被其他内容或操作者使用，请保留原编号核对结果");
    }
    public void noPending(Connection c,String resource,String allowedSource)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT request_id FROM "+TABLE+" WHERE instance_id=? AND resource_key=? AND state IN ('PREPARED','CONFLICT')")) {
            p.setString(1,instance());p.setString(2,resource);try(ResultSet r=p.executeQuery()){while(r.next())if(!r.getString(1).equals(allowedSource))throw new DofFailure(409,"该配置仍有结果待核对或冲突操作，请先处理历史记录");}
        }
    }
    public Entry preview(Connection c,String requestId,long actor,String namespace,String resource,String target,String operation,String targetRequest,String fingerprint,String reason,Object before,Object after)throws SQLException {
        Entry old=find(c,requestId);if(old!=null){same(old,namespace,actor,fingerprint);return old;}
        String a=encode(before),b=encode(after);
        try(PreparedStatement p=c.prepareStatement("INSERT INTO "+TABLE+" (instance_id,request_id,actor,namespace,resource_key,target_key,operation_name,target_request,request_hash,reason,before_hash,after_hash,before_json,after_json,state) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PREVIEWED')")) {
            int i=1;p.setString(i++,instance());p.setString(i++,id(requestId));p.setLong(i++,actor);p.setString(i++,namespace);p.setString(i++,resource);p.setString(i++,target);p.setString(i++,operation);p.setString(i++,targetRequest==null?null:id(targetRequest));p.setString(i++,fingerprint);p.setString(i++,reason==null?"":reason);p.setString(i++,digest(a));p.setString(i++,digest(b));p.setString(i++,a);p.setString(i,b);p.executeUpdate();
        }
        return require(c,namespace,requestId);
    }
    public Entry prepare(Connection c,Entry e,String target,Object before,Object after)throws SQLException {
        if(!"PREVIEWED".equals(e.state))throw new DofFailure(409,"操作已提交，请核对历史，不要重复执行");
        String a=encode(before),b=encode(after);
        try(PreparedStatement p=c.prepareStatement("UPDATE "+TABLE+" SET target_key=?,before_hash=?,after_hash=?,before_json=?,after_json=?,state='PREPARED' WHERE instance_id=? AND request_id=? AND state='PREVIEWED'")) {
            p.setString(1,target);p.setString(2,digest(a));p.setString(3,digest(b));p.setString(4,a);p.setString(5,b);p.setString(6,instance());p.setString(7,e.requestId);if(p.executeUpdate()!=1)throw new SQLException("Journal preparation changed");
        }
        return require(c,e.namespace,e.requestId);
    }
    public Entry finish(Connection c,Entry e,String state,long actor)throws SQLException {
        if(!Arrays.asList("PERSISTED","NOT_APPLIED","CONFLICT","NO_CHANGE").contains(state))throw new IllegalArgumentException("Invalid journal outcome");
        try(PreparedStatement p=c.prepareStatement("UPDATE "+TABLE+" SET state=?,checked_by=?,finished_at=NOW() WHERE instance_id=? AND request_id=? AND state='PREPARED'")) {
            p.setString(1,state);p.setLong(2,actor);p.setString(3,instance());p.setString(4,e.requestId);if(p.executeUpdate()!=1)throw new SQLException("Journal completion changed");
        }
        return require(c,e.namespace,e.requestId);
    }
    /**
     * For native INSERTs whose identifiers are only known after execution.
     * The caller must durably prepare first, then use this SAME InnoDB
     * transaction for the native rows and this receipt, and commit both.
     * This does not make MyISAM writes transactional or prove player delivery.
     */
    public Entry finishWithGeneratedOutcome(Connection c,Entry e,Object outcome,long actor)throws SQLException {
        if(c.getAutoCommit())throw new DofFailure(409,"生成编号回执必须与原生写入在同一事务中提交");
        return finishOutcome(c,e,outcome,actor);
    }
    /** Metadata-only aggregation after the independently durable business receipts exist. */
    public Entry finishMetadataTask(Connection c,Entry e,Object outcome,long actor)throws SQLException {
        if(!c.getAutoCommit())throw new DofFailure(409,"任务汇总必须独立持久化，不能代替原生业务事务");
        return finishOutcome(c,e,outcome,actor);
    }
    /** Atomic metadata controls only: source task, child refusals and control receipt commit together. */
    public Entry finishMetadataTransaction(Connection c,Entry e,Object outcome,long actor)throws SQLException {
        if(c.getAutoCommit())throw new DofFailure(409,"任务控制必须在同一日志事务中提交");
        return finishOutcome(c,e,outcome,actor);
    }
    private Entry finishOutcome(Connection c,Entry e,Object outcome,long actor)throws SQLException {
        if(!"PREPARED".equals(e.state))throw new DofFailure(409,"操作不是待提交状态，请核对原回执");
        String text=encode(outcome);
        try(PreparedStatement p=c.prepareStatement("UPDATE "+TABLE+" SET after_json=?,after_hash=?,state='PERSISTED',checked_by=?,finished_at=NOW() WHERE instance_id=? AND request_id=? AND namespace=? AND request_hash=? AND before_hash=? AND after_hash=? AND state='PREPARED'")) {
            p.setString(1,text);p.setString(2,digest(text));p.setLong(3,actor);p.setString(4,instance());p.setString(5,e.requestId);
            p.setString(6,e.namespace);p.setString(7,e.requestHash);p.setString(8,e.beforeHash);p.setString(9,e.afterHash);
            if(p.executeUpdate()!=1)throw new SQLException("Generated operation receipt changed; rollback native inserts");
        }
        return require(c,e.namespace,e.requestId);
    }
    /** Only after the issuer has returned and native locks prove the intent is still PREVIEWED. */
    public Entry rejectUnsubmittedPreview(Connection c,Entry e,Object outcome,long actor)throws SQLException {
        if(!c.getAutoCommit()||!"PREVIEWED".equals(e.state))throw new DofFailure(409,"只能记录尚未提交的预览拒绝结果");
        return rejectPreview(c,e,outcome,actor);
    }
    public Entry rejectPreviewInTransaction(Connection c,Entry e,Object outcome,long actor)throws SQLException {
        if(c.getAutoCommit()||!"PREVIEWED".equals(e.state))throw new DofFailure(409,"任务终止只能在日志事务内处理未提交预览");
        return rejectPreview(c,e,outcome,actor);
    }
    private Entry rejectPreview(Connection c,Entry e,Object outcome,long actor)throws SQLException {
        String text=encode(outcome);
        try(PreparedStatement p=c.prepareStatement("UPDATE "+TABLE+" SET after_json=?,after_hash=?,state='NOT_APPLIED',checked_by=?,finished_at=NOW() WHERE instance_id=? AND request_id=? AND namespace=? AND request_hash=? AND before_hash=? AND after_hash=? AND state='PREVIEWED'")){
            p.setString(1,text);p.setString(2,digest(text));p.setLong(3,actor);p.setString(4,instance());p.setString(5,e.requestId);p.setString(6,e.namespace);p.setString(7,e.requestHash);p.setString(8,e.beforeHash);p.setString(9,e.afterHash);
            if(p.executeUpdate()!=1)throw new SQLException("Unsubmitted preview changed while recording refusal");
        }
        return require(c,e.namespace,e.requestId);
    }
    public Entry acknowledge(Connection c,Entry source,Entry acknowledgment,long actor)throws SQLException {
        if(!"CONFLICT".equals(source.state) || !"PREPARED".equals(acknowledgment.state))throw new DofFailure(409,"冲突记录状态已改变");
        // One InnoDB statement changes both rows atomically; no native data is written.
        try(PreparedStatement p=c.prepareStatement("UPDATE "+TABLE+" SET state=CASE WHEN request_id=? THEN 'ACKNOWLEDGED' ELSE 'NO_CHANGE' END,checked_by=?,finished_at=NOW() WHERE instance_id=? AND ((request_id=? AND state='CONFLICT') OR (request_id=? AND state='PREPARED'))")) {
            p.setString(1,source.requestId);p.setLong(2,actor);p.setString(3,instance());p.setString(4,source.requestId);p.setString(5,acknowledgment.requestId);if(p.executeUpdate()!=2)throw new SQLException("Journal acknowledgment changed");
        }
        return require(c,acknowledgment.namespace,acknowledgment.requestId);
    }
    /** Metadata controls may atomically change multiple journal rows, never native business tables. */
    public void metadataTransactionGuard(Connection c)throws SQLException {
        schema(c);String identity;
        try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT CURRENT_USER()")){if(!r.next())throw badSchema();identity=r.getString(1);}
        int at=identity==null?-1:identity.lastIndexOf('@');if(at<1)throw badSchema();
        String grantee="'"+identity.substring(0,at).replace("'","''")+"'@'"+identity.substring(at+1).replace("'","''")+"'";
        String privilege="SELECT (EXISTS(SELECT 1 FROM information_schema.USER_PRIVILEGES WHERE GRANTEE=? AND PRIVILEGE_TYPE='TRIGGER') OR EXISTS(SELECT 1 FROM information_schema.SCHEMA_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA IN (?,?) AND PRIVILEGE_TYPE='TRIGGER') OR EXISTS(SELECT 1 FROM information_schema.TABLE_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA='dnf_service' AND TABLE_NAME='DOF_OPERATION_JOURNAL' AND PRIVILEGE_TYPE='TRIGGER'))";
        try(PreparedStatement st=c.prepareStatement(privilege)){st.setString(1,grantee);st.setString(2,grantee);st.setString(3,"dnf_service");st.setString(4,"dnf\\_service");st.setString(5,grantee);try(ResultSet r=st.executeQuery()){if(!r.next()||!r.getBoolean(1))throw new DofFailure(409,"无法确认管理日志触发器，停止元数据控制");}}
        try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE EVENT_OBJECT_SCHEMA='dnf_service' AND EVENT_OBJECT_TABLE='DOF_OPERATION_JOURNAL'")){if(!r.next()||r.getInt(1)!=0)throw badSchema();}
        try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_NAME IS NOT NULL AND ((TABLE_SCHEMA='dnf_service' AND TABLE_NAME='DOF_OPERATION_JOURNAL') OR (REFERENCED_TABLE_SCHEMA='dnf_service' AND REFERENCED_TABLE_NAME='DOF_OPERATION_JOURNAL'))")){if(!r.next()||r.getInt(1)!=0)throw badSchema();}
    }
    /** Fence late submissions while preserving native snapshot formats and the original actor. */
    public Entry cancelPreviewInTransaction(Connection c,Entry e,long actor)throws SQLException {
        if(c.getAutoCommit()||!"PREVIEWED".equals(e.state))throw new DofFailure(409,"只能在日志事务中取消未提交预览");
        try(PreparedStatement p=c.prepareStatement("UPDATE "+TABLE+" SET state='NOT_APPLIED',checked_by=?,finished_at=NOW() WHERE instance_id=? AND request_id=? AND namespace=? AND request_hash=? AND before_hash=? AND after_hash=? AND state='PREVIEWED'")){
            p.setLong(1,actor);p.setString(2,instance());p.setString(3,e.requestId);p.setString(4,e.namespace);p.setString(5,e.requestHash);p.setString(6,e.beforeHash);p.setString(7,e.afterHash);if(p.executeUpdate()!=1)throw new SQLException("Unsubmitted preview changed while cancelling");
        }return require(c,e.namespace,e.requestId);
    }
    public List<Entry> history(Connection c,String namespace)throws SQLException {
        schema(c);List<Entry> result=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("SELECT * FROM "+TABLE+" WHERE instance_id=? AND namespace=? ORDER BY (state IN ('PREPARED','CONFLICT')) DESC,created_at DESC,request_id DESC LIMIT 50")) {
            p.setString(1,instance());p.setString(2,namespace);try(ResultSet r=p.executeQuery()){while(r.next())result.add(entry(r));}
        }
        return result;
    }
    public Map<String,Object> result(Entry e) {
        Map<String,Object> out=new LinkedHashMap<>();out.put("requestId",e.requestId);out.put("state",e.state);out.put("actor",e.actor);out.put("operation",e.operation);out.put("targetRequestId",e.targetRequest);out.put("createdAt",e.createdAt);
        out.put("targetKey",e.target);
        out.put("reason",e.reason);
        out.put("persisted",Arrays.asList("PERSISTED","NO_CHANGE").contains(e.state));out.put("effectState","not_observed");return out;
    }
}
