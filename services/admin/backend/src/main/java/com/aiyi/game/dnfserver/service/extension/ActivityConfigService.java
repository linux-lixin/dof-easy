package com.aiyi.game.dnfserver.service.extension;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.service.dof.ManagedOperationJournal;
import com.aiyi.game.dnfserver.service.dof.DofFailure;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.stream.Collectors;

/** Edits only native activity configuration. Player progress is never a write target. */
@Service
public class ActivityConfigService {
    @Resource private DataSource dataSource;
    @Resource private AccountVODao accountVODao;
    @Resource private ManagedOperationJournal journal;
    private final ObjectMapper json = new ObjectMapper();
    private final List<Section> sections;
    private static final String AUDIT = "`dnf_service`.`EXTENSION_CONFIG_AUDIT`";

    public static class Field {
        public String name, label, type, group, hint;
        public long min, max;
        public boolean readonly;
        @com.fasterxml.jackson.annotation.JsonProperty("default") public Object initial;
    }
    public static class Section {
        public String key, title, table, primaryKey;
        public boolean autoId;
        public boolean writable = true, deleteAllowed = true;
        public String notice;
        public List<Field> fields;
    }
    public static class Edit {
        public String revision, requestId, operation, targetRequestId, reason;
        public Map<String,Object> values;
    }
    public static class Failure extends RuntimeException {
        public final int status;
        public Failure(int status, String message) { super(message); this.status = status; }
    }
    public ActivityConfigService() {
        try (InputStream in = getClass().getResourceAsStream("/extension/activities.json")) {
            sections = json.readValue(in, new TypeReference<List<Section>>() {});
        } catch (Exception e) { throw new IllegalStateException("活动字段清单无法加载", e); }
    }
    private long admin() {
        AccountVO user = accountVODao.get(ThreadUtil.getUserId());
        if (user == null || !user.isAdmin()) throw new Failure(403, "仅管理员可以管理活动配置");
        return ThreadUtil.getUserId();
    }
    private Section section(String key) {
        return sections.stream().filter(s -> s.key.equals(key)).findFirst()
            .orElseThrow(() -> new Failure(404, "未知活动配置"));
    }
    private String table(Section s) { return "`dof_native`.`" + s.table + "`"; }
    private String columns(Section s) {
        return s.fields.stream().map(f -> "`" + f.name + "`").collect(Collectors.joining(","));
    }
    public List<Section> catalog() { admin(); return sections; }

    private void schema(Connection c, Section s) throws SQLException {
        Map<String,String> actual = new HashMap<>();
        try (PreparedStatement p = c.prepareStatement("SELECT COLUMN_NAME,DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME=?")) {
            p.setString(1, s.table);
            try (ResultSet r = p.executeQuery()) { while(r.next()) actual.put(r.getString(1), r.getString(2)); }
        }
        if (actual.isEmpty()) throw new Failure(409, "扩展配置表尚未初始化，请先启用团本扩展并完成游戏服务启动");
        for (Field f : s.fields) if (!f.type.equals(actual.get(f.name)))
            throw new Failure(409, "配置表版本不兼容，已停止读写，请核对扩展版本");
    }
    private List<Map<String,Object>> rows(Connection c, Section s, Long id) throws SQLException {
        String where = id == null ? "" : " WHERE `" + s.primaryKey + "`=?";
        String order = s.primaryKey == null ? "" : " ORDER BY `" + s.primaryKey + "`";
        try (PreparedStatement p = c.prepareStatement("SELECT " + columns(s) + " FROM " + table(s) + where + order + " LIMIT 1001")) {
            if(id != null) p.setLong(1,id);
            try(ResultSet r=p.executeQuery()) {
                List<Map<String,Object>> result=new ArrayList<>();
                while(r.next()) {
                    Map<String,Object> row=new LinkedHashMap<>();
                    for(Field f:s.fields) {
                        Object value = "int".equals(f.type) ? r.getObject(f.name) : r.getString(f.name);
                        // Connector/J 5.1 may append .0 to DATETIME strings; native schema has no fractional seconds.
                        if (value != null && "datetime".equals(f.type)) value = value.toString().replaceFirst("\\.0+$", "");
                        row.put(f.name, value);
                    }
                    result.add(row);
                }
                if(s.primaryKey==null && result.size()>1) throw new Failure(409,"活动设置存在多条记录，拒绝覆盖，请先核查原始配置");
                if(result.size()>1000) throw new Failure(409,"配置超过本页支持的 1000 条，请先整理配置后再编辑");
                return result;
            }
        }
    }
    private String revision(Map<String,Object> row) {
        if(row==null) return "new";
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(row));
            StringBuilder out=new StringBuilder();
            for(byte b:bytes) out.append(String.format("%02x",b & 255));
            return out.toString();
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    private Map<String,Object> envelope(Map<String,Object> row) {
        Map<String,Object> out=new LinkedHashMap<>();out.put("values",row);out.put("revision",revision(row));return out;
    }
    public Map<String,Object> list(String key) {
        admin();Section s=section(key);
        try(Connection c=journal.connection()) {
            schema(c,s);
            Map<String,Object> out=new LinkedHashMap<>();
            out.put("writable",s.writable);
            out.put("rows",rows(c,s,null).stream().map(this::envelope).collect(Collectors.toList()));
            out.put("moduleNotice",s.notice);
            out.put("notice","配置保存到游戏插件使用的数据库。领取与进度继续由游戏处理；请重新打开游戏活动窗口验证，生效方式仍需逐项核对；保存不会自动重启实例。");
            return out;
        } catch(SQLException e) { throw unavailable(); }
    }
    private Failure unavailable() { return new Failure(503,"活动数据库暂不可用或权限不足；未自动初始化或覆盖原生表"); }
    // Strict scalar validation: never accept SQL names, nested objects, coercive numbers or unknown fields.
    Map<String,Object> validate(Section s, Map<String,Object> input, Map<String,Object> old, boolean creating) {
        return validate(s,input,old,creating,false);
    }
    private Map<String,Object> validate(Section s, Map<String,Object> input, Map<String,Object> old, boolean creating, boolean trustedRestore) {
        if(input==null) throw new Failure(400,"配置不能为空");
        Set<String> names=s.fields.stream().map(f->f.name).collect(Collectors.toSet());
        if(!names.equals(input.keySet())) throw new Failure(400,"配置字段缺失或包含未知字段，请刷新页面");
        Map<String,Object> result=new LinkedHashMap<>();
        for(Field f:s.fields) {
            Object value=input.get(f.name);
            if(f.readonly) {
                Object expected=old==null ? f.initial : old.get(f.name);
                if(!Objects.equals(String.valueOf(value),String.valueOf(expected))) throw new Failure(400,f.label+"不允许在此修改");
                result.put(f.name,expected);continue;
            }
            if(s.autoId && creating && f.name.equals(s.primaryKey)) { result.put(f.name,0L);continue; }
            if(value==null) { // Preserve legacy nullable reward cells; new cells use explicit zero.
                if(old!=null && old.get(f.name)==null) {result.put(f.name,null);continue;}
                throw new Failure(400,f.label+"不能为空");
            }
            String text=String.valueOf(value);
            if("int".equals(f.type)) {
                if(!text.matches("[0-9]{1,10}")) throw new Failure(400,f.label+"必须为整数");
                long n=Long.parseLong(text);
                if(n<f.min || n>f.max) throw new Failure(400,f.label+"超出允许范围");
                result.put(f.name,n);
            } else if("varchar".equals(f.type)) {
                if(!(value instanceof String) || text.length()>255 || text.codePoints().anyMatch(n->n<32 || n>65535)) throw new Failure(400,f.label+"格式错误或超过 255 字符");
                if((f.name.equals("name") || f.name.equals("d_Name") || f.name.equals("Dname")) && text.trim().isEmpty()) throw new Failure(400,"请填写活动名称");
                result.put(f.name,text);
            } else {
                try {
                    if(!text.matches("[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}"+("datetime".equals(f.type)?" [0-9]{2}:[0-9]{2}:[0-9]{2}":""))) throw new IllegalArgumentException();
                    if("date".equals(f.type)) LocalDate.parse(text); else LocalDateTime.parse(text,DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT));
                } catch(Exception e) {throw new Failure(400,f.label+"不是有效日期");}
                result.put(f.name,text);
            }
        }
        if(s.key.equals("signin")) {
            if(result.get("start_time").toString().compareTo(result.get("end_time").toString())>0) throw new Failure(400,"结束日期不能早于开始日期");
            for(int i=1;i<=28;i++) pair(result,"code_"+i,"num_"+i);
            for(int i=1;i<=4;i++) pair(result,"Cum_"+i,"C_num_"+i);
        }
        if(s.key.equals("rewards")) for(int i=1;i<=3;i++) pair(result,"item"+i,"item_code"+i);
        if(s.key.equals("charts-rewards")) {
            String[] ids=result.get("code_data").toString().split(",",-1), amounts=result.get("code_num").toString().split(",",-1);
            if(ids.length!=amounts.length || ids.length>50)throw new Failure(400,"排行奖励道具和数量不匹配");
            for(int i=0;i<ids.length;i++)if(!ids[i].matches("[1-9][0-9]{0,9}") || !amounts[i].matches("[1-9][0-9]{0,9}") || Long.parseLong(ids[i])>2147483647L || Long.parseLong(amounts[i])>2147483647L)throw new Failure(400,"排行奖励编号或数量无效");
        }
        if(s.key.equals("shop")){pair(result,"item_id","item_num");pair(result,"deduction_item_id","deduction_item_num");pair(result,"deduction_item_id2","deduction_item_num2");}
        if(s.key.equals("online")) {
            for(int i=1;i<=4;i++) {
                pair(result,"stage"+i+"_item_id","stage"+i+"_item_count");
                pair(result,"cumulative"+i+"_item_id","cumulative"+i+"_item_count");
                if(Long.parseLong(result.get("stage"+i+"_minutes").toString())<1 || Long.parseLong(result.get("cumulative"+i+"_required").toString())<1)
                    throw new Failure(400,"在线奖励时间和累计门槛必须大于零");
            }
        }
        if(s.key.equals("inheritance")&&!trustedRestore)NativeInheritanceRules.validate(result,old);
        return result;
    }
    private void pair(Map<String,Object> row,String a,String b) {
        long x=row.get(a)==null?0:Long.parseLong(row.get(a).toString()), y=row.get(b)==null?0:Long.parseLong(row.get(b).toString());
        if((x==0)!=(y==0)) throw new Failure(400,"奖励道具与数量必须同时填写；不发放时两项均为 0");
    }
    private boolean referenced(Connection c,long id) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM `dof_native`.`Collection_box_user_item` WHERE Slot=? LIMIT 1")) {
            p.setLong(1,id);try(ResultSet r=p.executeQuery()) {return r.next();}
        }
    }
    private void guard(Connection c,Section s) throws SQLException {
        if(s.key.equals("inheritance")){journal.metadataTransactionGuard(c);NativeInheritanceRules.requireSchema(c);cancelActor(c,admin());}
        String sql=null;
        if(s.key.startsWith("pot-") && !s.key.equals("pot-switch"))sql="SELECT COUNT(*)=1 AND COALESCE(MAX(item_off_1),0)=0 AND COALESCE(MAX(item_off_2),0)=0 AND COALESCE(MAX(item_off_3),0)=0 FROM dof_native.earthenware_pot_item";
        if(s.key.equals("shop"))sql="SELECT (SELECT COUNT(*) FROM dof_native.new_shop)=1 AND (SELECT COUNT(*) FROM dof_native.new_shop WHERE swoff<>0)=0 AND (SELECT COUNT(*) FROM dof_native.timed_task WHERE task_state=1)=0";
        if(s.key.equals("charts-rewards"))sql="SELECT COUNT(*)=1 AND COALESCE(MAX(status),0)=0 FROM dof_native.game_charts_config";
        if(s.key.equals("appearance"))sql="SELECT COUNT(*)<=1 AND COALESCE(MAX(swoff),0)=0 FROM dof_native.transformation_item";
        if(sql!=null)try(Statement st=c.createStatement();ResultSet r=st.executeQuery(sql)){if(!r.next() || !r.getBoolean(1))throw new Failure(409,"原生功能仍在启用或开关状态不明确，拒绝改写，请先完成停用及兼容验证");}
    }
    private String namespace(String key){return "activity."+key;}
    private String resource(Section s){return "dof_native."+s.table;}
    private String action(Edit edit) {
        if(edit==null || edit.revision==null || !(edit.revision.equals("new") || edit.revision.matches("[0-9a-f]{64}")))throw new Failure(400,"缺少有效配置版本");
        ManagedOperationJournal.id(edit.requestId);
        String op=edit.operation==null?"upsert":edit.operation;
        if(!Arrays.asList("upsert","delete","restore","accept-current").contains(op))throw new Failure(400,"未知活动操作");
        if(Arrays.asList("restore","accept-current").contains(op))ManagedOperationJournal.id(edit.targetRequestId);
        else if(edit.targetRequestId!=null)throw new Failure(400,"普通修改不能指定历史操作");
        if(op.equals("accept-current") && (edit.reason==null || edit.reason.trim().isEmpty() || edit.reason.length()>128 || edit.reason.codePoints().anyMatch(x->x<32||x>65535||Character.isSurrogate((char)x))))throw new Failure(400,"请填写保留当前配置并关闭冲突的原因");
        if(!op.equals("accept-current")&&edit.reason!=null)throw new Failure(400,"此操作不接受额外原因字段");
        return op;
    }
    private String fingerprint(Edit e,String op) {
        Map<String,Object> data=new LinkedHashMap<>();data.put("revision",e.revision);data.put("operation",op);data.put("values",e.values);
        data.put("targetRequestId",e.targetRequestId==null?null:ManagedOperationJournal.id(e.targetRequestId));data.put("reason",e.reason);
        return journal.hash(data);
    }
    private boolean legacyExists(Connection c)throws SQLException {
        try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='EXTENSION_CONFIG_AUDIT'")){return r.next();}
    }
    private void lock(Connection c,Section s)throws SQLException {lock(c,s,false);}
    private void lock(Connection c,Section s,boolean actorLock)throws SQLException {
        String locks=table(s)+" WRITE";
        if(actorLock || s.key.equals("inheritance"))locks+=",`d_taiwan`.`accounts` READ";
        if(s.key.startsWith("pot-")&&!s.key.equals("pot-switch"))locks+=",dof_native.earthenware_pot_item READ";
        if(s.key.equals("shop"))locks+=",dof_native.new_shop READ,dof_native.timed_task READ";
        if(s.key.equals("charts-rewards"))locks+=",dof_native.game_charts_config READ";
        if(s.key.equals("collection"))locks+=",dof_native.Collection_box_user_item READ";
        if(legacyExists(c))locks+=","+AUDIT+" READ";
        try {
            journal.lock(c,locks);schema(c,s);
            try(PreparedStatement p=c.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME=?")) {
                p.setString(1,s.table);try(ResultSet r=p.executeQuery()){if(!r.next()||!Arrays.asList("MyISAM","InnoDB").contains(r.getString(1)))throw new Failure(409,"活动表引擎不受支持，停止写入");}
            }
        }catch(SQLException|RuntimeException failure) {
            try{unlock(c);}catch(SQLException|RuntimeException cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    private void discard(Connection c,Throwable failure) {
        try{c.unwrap(Connection.class).close();}
        catch(SQLException|RuntimeException close){failure.addSuppressed(close);try{c.abort(Runnable::run);}catch(SQLException|RuntimeException abort){failure.addSuppressed(abort);}}
    }
    private void unlock(Connection c)throws SQLException {
        try{journal.unlock(c);}catch(SQLException|RuntimeException failure){discard(c,failure);throw failure;}
    }
    private void noLegacyPending(Connection c,Section s)throws SQLException {
        if(!legacyExists(c))return;
        List<String> related=sections.stream().filter(x->x.table.equals(s.table)).map(x->x.key).collect(Collectors.toList());
        try(PreparedStatement p=c.prepareStatement("SELECT id FROM "+AUDIT+" WHERE section_name IN ("+String.join(",",Collections.nCopies(related.size(),"?"))+") AND state='pending'")) {
            for(int i=0;i<related.size();i++)p.setString(i+1,related.get(i));
            try(ResultSet r=p.executeQuery()){while(r.next())if(legacyResolution(c,sectionForLegacy(c,r.getLong(1)),r.getLong(1),true)==null)throw new Failure(409,"存在旧格式 pending 操作，请先预览并确认旧日志核对；没有自动重试或覆盖旧审计");}
        }
    }
    public static class LegacyEdit {public String requestId,revision,reason;}
    private String legacyNamespace(String key){return "activity-legacy."+key;}
    private Section sectionForLegacy(Connection c,long id)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT section_name FROM "+AUDIT+" WHERE id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new Failure(404,"旧日志不存在");return section(r.getString(1));}}
    }
    private Map<String,Object> legacy(Connection c,Section s,long id)throws SQLException {
        if(id<1||!legacyExists(c))throw new Failure(404,"旧日志不存在");Map<String,Object> out=new LinkedHashMap<>();
        try(PreparedStatement p=c.prepareStatement("SELECT id,actor,section_name,operation_name,before_json,after_json,state,created_at FROM "+AUDIT+" WHERE id=? AND section_name=?")) {
            p.setLong(1,id);p.setString(2,s.key);try(ResultSet r=p.executeQuery()){
                if(!r.next())throw new Failure(404,"未找到本模块旧日志");
                for(String k:Arrays.asList("id","actor","section_name","operation_name","before_json","after_json","state","created_at"))out.put(k,r.getString(k));
                if(r.next())throw new Failure(409,"旧日志编号不唯一");
            }
        }
        return out;
    }
    private Map<String,Object> legacyProof(Connection c,Section s,long id)throws SQLException {
        Map<String,Object> log=legacy(c,s,id);
        if(!"pending".equals(log.get("state")))throw new Failure(409,"旧日志已经不是 pending，停止核对");
        String op=String.valueOf(log.get("operation_name"));if(!Arrays.asList("create","update","delete").contains(op))throw new Failure(409,"未知旧操作类型，不能确认");
        Map<String,Object> before=snapshot(s,(String)log.get("before_json")),after=snapshot(s,(String)log.get("after_json"));
        if(before==null&&after==null||op.equals("create")&&before!=null||op.equals("delete")&&(before==null||after!=null)||op.equals("update")&&(before==null||after==null))throw new Failure(409,"旧日志快照与操作不匹配，停止核对");
        Long target=null;boolean known=true;
        if(s.primaryKey!=null) {
            if(op.equals("create")&&s.autoId)known=false; // Old code never recorded the assigned native ID.
            else {target=rowId(s,before==null?after:before);if(after!=null&&!target.equals(rowId(s,after)))throw new Failure(409,"旧日志前后编号不一致");}
        }
        List<Map<String,Object>> current=rows(c,s,known?target:null);
        String assessment="UNKNOWN";
        if(known) {Map<String,Object> now=current.isEmpty()?null:current.get(0);if(current.size()>1)throw new Failure(409,"配置编号不唯一");
            String h=journal.hash(now);assessment=h.equals(journal.hash(after))?"MATCHES_AFTER":h.equals(journal.hash(before))?"MATCHES_BEFORE":"CONFLICT";
        }
        Map<String,Object> proof=new LinkedHashMap<>();proof.put("legacyHash",journal.hash(log));proof.put("assessment",assessment);proof.put("targetKnown",known);proof.put("targetKey",known?(target==null?"singleton":target.toString()):"unknown");proof.put("currentRows",current);return proof;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> legacySnapshot(ManagedOperationJournal.Entry e,Section s,long id) {
        try {
            if(!legacyNamespace(s.key).equals(e.namespace)||!resource(s).equals(e.resource)||!Long.toString(id).equals(e.target)||!"legacy-check".equals(e.operation)||!e.beforeHash.equals(e.afterHash))throw new IllegalArgumentException();
            Map<String,Object> proof=json.readValue(e.afterJson,new TypeReference<Map<String,Object>>(){});
            if(!proof.keySet().equals(new HashSet<>(Arrays.asList("legacyHash","assessment","targetKnown","targetKey","currentRows")))||!String.valueOf(proof.get("legacyHash")).matches("[0-9a-f]{64}")||!(proof.get("currentRows") instanceof List)||e.reason==null||e.reason.trim().isEmpty())throw new IllegalArgumentException();return proof;
        }catch(Exception ex){throw new Failure(409,"旧日志核对证据损坏，停止解除阻塞");}
    }
    private ManagedOperationJournal.Entry legacyResolution(Connection c,Section s,long id,boolean confirmedOnly)throws SQLException {
        String hash=journal.hash(legacy(c,s,id));
        try(PreparedStatement p=c.prepareStatement("SELECT request_id FROM "+ManagedOperationJournal.TABLE+" WHERE instance_id=? AND namespace=? AND target_key=?"+(confirmedOnly?" AND state='NO_CHANGE'":"")+" ORDER BY (state='PREPARED') DESC,created_at DESC,request_id DESC")) {
            p.setString(1,journal.instance());p.setString(2,legacyNamespace(s.key));p.setString(3,Long.toString(id));
            try(ResultSet r=p.executeQuery()){while(r.next()){ManagedOperationJournal.Entry e=journal.require(c,legacyNamespace(s.key),r.getString(1));if(hash.equals(legacySnapshot(e,s,id).get("legacyHash")))return e;}}
        }return null;
    }
    private void legacyEdit(LegacyEdit e){if(e==null||e.revision==null||!e.revision.matches("[0-9a-f]{64}"))throw new Failure(400,"缺少旧日志核对版本");ManagedOperationJournal.id(e.requestId);if(e.reason==null||e.reason.trim().isEmpty()||e.reason.length()>128||e.reason.codePoints().anyMatch(x->x<32||x>65535||Character.isSurrogate((char)x)))throw new Failure(400,"请填写 1–128 字核对原因");}
    private String legacyFingerprint(long id,LegacyEdit e){Map<String,Object> out=new LinkedHashMap<>();out.put("legacyId",id);out.put("revision",e.revision);out.put("reason",e.reason);return journal.hash(out);}
    public Map<String,Object> legacyCurrent(String key,long id) {
        admin();Section s=section(key);try(Connection c=journal.connection()){lock(c,s);try{
            Map<String,Object> proof=legacyProof(c,s,id);Map<String,Object> out=new LinkedHashMap<>(proof);out.put("revision",journal.hash(proof));out.put("legacyId",Long.toString(id));return out;
        }finally{unlock(c);}}catch(SQLException e){throw unavailable();}
    }
    public Map<String,Object> legacyReview(String key,long id,LegacyEdit edit,boolean commit) {
        long actor=admin();Section s=section(key);legacyEdit(edit);
        try(Connection c=journal.connection()){lock(c,s);try {
            ManagedOperationJournal.Entry e=journal.find(c,edit.requestId);
            if(e!=null){journal.same(e,legacyNamespace(key),actor,legacyFingerprint(id,edit));legacySnapshot(e,s,id);if(!e.state.equals("PREVIEWED")||!commit)return journal.result(e);}
            else if(commit)throw new Failure(409,"请先预览旧日志核对");
            journal.noPending(c,resource(s),null);
            if(legacyResolution(c,s,id,true)!=null)throw new Failure(409,"此版本旧日志已有确认记录");
            Map<String,Object> proof=legacyProof(c,s,id);if(!journal.hash(proof).equals(edit.revision))throw new Failure(409,"旧日志或当前配置改变，请重新读取并预览");
            if(!commit)return journal.result(journal.preview(c,edit.requestId,actor,legacyNamespace(key),resource(s),Long.toString(id),"legacy-check",null,legacyFingerprint(id,edit),edit.reason,proof,proof));
            e=journal.prepare(c,e,Long.toString(id),proof,proof);return journal.result(journal.finish(c,e,"NO_CHANGE",actor));
        }finally{unlock(c);}}catch(SQLException e){throw new Failure(503,"核对记录结果未确认；未改写原生配置或旧审计，请保留编号核对");}
    }
    public Map<String,Object> legacyReconcile(String key,long id,String requestId) {
        long actor=admin();Section s=section(key);try(Connection c=journal.connection()){lock(c,s);try {
            ManagedOperationJournal.Entry e=journal.require(c,legacyNamespace(key),requestId);legacySnapshot(e,s,id);if(!e.state.equals("PREPARED"))return journal.result(e);
            boolean matches=false;
            try{matches=journal.hash(legacyProof(c,s,id)).equals(e.afterHash);}catch(Failure changed){/* This review never writes native data; changed/deleted legacy evidence cannot confirm it. */}
            return journal.result(journal.finish(c,e,matches?"NO_CHANGE":"NOT_APPLIED",actor));
        }finally{unlock(c);}}catch(SQLException e){throw unavailable();}
    }
    private Long rowId(Section s,Map<String,Object> values) {
        if(s.primaryKey==null)return null;
        Object value=values==null?null:values.get(s.primaryKey);
        if(value==null || !value.toString().matches(Arrays.asList("charts-rewards","inheritance").contains(s.key)?"(?:0|[1-9][0-9]{0,9})":"[1-9][0-9]{0,9}"))throw new Failure(400,"记录编号无效");
        long id=Long.parseLong(value.toString());if(id>2147483647L)throw new Failure(400,"记录编号超出范围");return id;
    }
    private Map<String,Object> current(Connection c,Section s,Long id)throws SQLException {
        List<Map<String,Object>> found=rows(c,s,id);if(found.size()>1)throw new Failure(409,"同一配置编号存在多行，拒绝覆盖");return found.isEmpty()?null:found.get(0);
    }
    private Map<String,Object> snapshot(Section s,String text) {
        try {
            Map<String,Object> row=json.readValue(text,new TypeReference<Map<String,Object>>(){});
            if(row==null)return null;
            Set<String> keys=s.fields.stream().map(f->f.name).collect(Collectors.toSet());
            if(!keys.equals(row.keySet()) || row.values().stream().anyMatch(v->v!=null && !(v instanceof String) && !(v instanceof Number)))throw new IllegalArgumentException();
            return row;
        }catch(Exception e){throw new Failure(409,"历史快照与当前活动字段不匹配，停止恢复");}
    }
    private void checkEntry(Section s,ManagedOperationJournal.Entry entry) {
        if(!namespace(s.key).equals(entry.namespace)||!resource(s).equals(entry.resource))throw new Failure(409,"操作日志来源与当前配置不一致");
        if(entry.state.equals("PREVIEWED"))return;
        if(entry.state.equals("NOT_APPLIED")&&entry.target.equals("new")&&(s.primaryKey==null||s.autoId&&entry.beforeJson.equals("null")))return;
        try {
            if(s.primaryKey==null){if(!entry.target.equals("singleton"))throw new IllegalArgumentException();}
            else {if(!entry.target.matches("(?:0|[1-9][0-9]{0,9})")||Long.parseLong(entry.target)>2147483647L)throw new IllegalArgumentException();for(String text:Arrays.asList(entry.beforeJson,entry.afterJson)) {
                Map<String,Object> row=snapshot(s,text);
                if(row!=null && !rowId(s,row).toString().equals(entry.target))throw new IllegalArgumentException();
            }}
        }catch(IllegalArgumentException e){throw new Failure(409,"日志中的目标编号与快照不一致，停止恢复");}
    }
    private static class Change {Map<String,Object> before,after;Long id;ManagedOperationJournal.Entry source;String action;}
    private Change plan(Connection c,Section s,Edit e,String op,boolean commit)throws SQLException {
        if(!s.writable)throw new Failure(409,"此配置尚未完成规则验证，只读");
        Change change=new Change();change.action=op;
        if(op.equals("restore")||op.equals("accept-current")) {
            if(e.values!=null)throw new Failure(400,"历史操作不接受浏览器提供快照");
            change.source=journal.require(c,namespace(s.key),e.targetRequestId);
            checkEntry(s,change.source);
            if(!resource(s).equals(change.source.resource))throw new Failure(409,"历史配置来源不匹配");
            Map<String,Object> before=snapshot(s,change.source.beforeJson),after=snapshot(s,change.source.afterJson);
            if(op.equals("restore")&&!Arrays.asList("PERSISTED","NO_CHANGE").contains(change.source.state))throw new Failure(409,"只能恢复已核对持久化的操作");
            if(op.equals("accept-current")&&!change.source.state.equals("CONFLICT"))throw new Failure(409,"只能确认保留冲突记录的当前配置");
            change.id=s.primaryKey==null?null:Long.valueOf(change.source.target);
            change.before=current(c,s,change.id);
            if(!revision(change.before).equals(e.revision))throw new Failure(409,"当前配置版本改变，请重新预览");
            if(op.equals("accept-current")){change.after=change.before;return change;}
            if(before==null) {
                if(!s.deleteAllowed || s.primaryKey==null)throw new Failure(409,"该配置不支持删除，不能通过恢复移除初始化设置");
                if(!journal.hash(change.before).equals(journal.hash(after)))throw new Failure(409,"新增后的配置已被其他操作修改，拒绝删除");
                change.after=null;
            }else if(after==null) {
                if(change.before!=null)throw new Failure(409,"被删除的配置编号已重新使用，拒绝覆盖");
                change.after=validate(s,before,before,false);
            }else {
                if(change.before==null)throw new Failure(409,"当前配置已被删除，停止恢复");
                if(s.key.equals("inheritance"))NativeInheritanceRules.restoreGuard(before,after,change.before);
                change.after=new LinkedHashMap<>(change.before);
                for(Field f:s.fields)if(!Objects.equals(String.valueOf(before.get(f.name)),String.valueOf(after.get(f.name)))) {
                    if(!Objects.equals(String.valueOf(change.before.get(f.name)),String.valueOf(after.get(f.name))))throw new Failure(409,"本次涉及的字段已有第三方修改："+f.label);
                    change.after.put(f.name,before.get(f.name));
                }
                change.after=validate(s,change.after,change.before,false,true);
            }
        }else {
            boolean creating=e.revision.equals("new");
            if(e.values==null)throw new Failure(400,"缺少配置内容");
            if(!e.values.keySet().equals(s.fields.stream().map(f->f.name).collect(Collectors.toSet())))throw new Failure(400,"配置字段缺失或包含未知字段");
            if(op.equals("delete")&&(!s.deleteAllowed||creating||s.primaryKey==null))throw new Failure(409,"此配置不支持删除");
            change.id=creating&&s.autoId?null:rowId(s,e.values);
            change.before=creating&&s.autoId?null:current(c,s,change.id);
            if(!revision(change.before).equals(e.revision))throw new Failure(409,"配置版本已改变，请重新读取并预览");
            change.after=op.equals("delete")?null:validate(s,e.values,change.before,creating);
            if(creating&&s.autoId&&commit) {
                // Reserve the exact ID under the native WRITE lock, before PREPARED.
                try(PreparedStatement p=c.prepareStatement("SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME=?")) {
                    p.setString(1,s.table);try(ResultSet r=p.executeQuery()) {
                        if(!r.next()||r.getString(1)==null||r.getLong(1)<1||r.getLong(1)>2147483647L)throw new Failure(409,"原生自增编号不兼容，停止新增");change.id=r.getLong(1);
                    }
                }
                change.after.put(s.primaryKey,change.id);
            }
        }
        if(change.after!=null && s.key.equals("collection") && change.before==null) {
            try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM "+table(s)+" WHERE box_id=? AND item_id=? LIMIT 1")) {
                p.setObject(1,change.after.get("box_id"));p.setObject(2,change.after.get("item_id"));try(ResultSet r=p.executeQuery()){if(r.next())throw new Failure(409,"同一收集箱已存在该道具，不能重复新增");}
            }
        }
        if(s.key.equals("collection")&&change.before!=null&&referenced(c,change.id)) {
            if(change.after==null || !Objects.equals(String.valueOf(change.before.get("box_id")),String.valueOf(change.after.get("box_id"))) || !Objects.equals(String.valueOf(change.before.get("item_id")),String.valueOf(change.after.get("item_id"))))
                throw new Failure(409,"配置已有玩家收集记录，不能删除或更换箱号及道具");
        }
        return change;
    }
    private List<Map<String,Object>> changes(Section s,Change change) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(Field f:s.fields) {
            Object a=change.before==null?null:change.before.get(f.name),b=change.after==null?null:change.after.get(f.name);
            if(!Objects.equals(a==null?null:a.toString(),b==null?null:b.toString())){Map<String,Object> row=new LinkedHashMap<>();row.put("key",f.name);row.put("label",f.label);row.put("before",a);row.put("after",b);result.add(row);}
        }
        return result;
    }
    private Map<String,Object> result(ManagedOperationJournal.Entry entry,Section s) {
        checkEntry(s,entry);
        Map<String,Object> out=journal.result(entry);Map<String,Object> after=snapshot(s,entry.afterJson);out.putAll(envelope(after));out.put("deleted",after==null);return out;
    }
    public Map<String,Object> preview(String key,Edit edit) {
        long actor=admin();Section s=section(key);String op=action(edit);
        try(Connection c=journal.connection()) {
            lock(c,s);try {
                ManagedOperationJournal.Entry existing=journal.find(c,edit.requestId);
                if(existing!=null){journal.same(existing,namespace(key),actor,fingerprint(edit,op));Map<String,Object> out=result(existing,s);Change ch=new Change();ch.before=snapshot(s,existing.beforeJson);ch.after=snapshot(s,existing.afterJson);out.put("changes",changes(s,ch));return out;}
                noLegacyPending(c,s);journal.noPending(c,resource(s),op.equals("accept-current")?ManagedOperationJournal.id(edit.targetRequestId):null);guard(c,s);
                Change change=plan(c,s,edit,op,false);
                ManagedOperationJournal.Entry entry=journal.preview(c,edit.requestId,actor,namespace(key),resource(s),change.id==null?"new":change.id.toString(),op,edit.targetRequestId,fingerprint(edit,op),edit.reason,change.before,change.after);
                Map<String,Object> out=result(entry,s);out.put("changes",changes(s,change));out.put("persisted",false);return out;
            }finally{unlock(c);}
        }catch(SQLException e){throw unavailable();}
    }
    private void write(Connection c,Section s,Change ch)throws SQLException {
        String sql;List<Field> changed=new ArrayList<>();
        if(ch.after!=null)for(Field f:s.fields)if(ch.before==null||!Objects.equals(ch.before.get(f.name)==null?null:ch.before.get(f.name).toString(),ch.after.get(f.name)==null?null:ch.after.get(f.name).toString()))changed.add(f);
        if(ch.after==null)sql="DELETE FROM "+table(s)+" WHERE `"+s.primaryKey+"`=?";
        else if(ch.before==null)sql="INSERT INTO "+table(s)+" (`"+changed.stream().map(f->f.name).collect(Collectors.joining("`,`"))+"`) VALUES ("+String.join(",",Collections.nCopies(changed.size(),"?"))+")";
        else sql="UPDATE "+table(s)+" SET "+changed.stream().map(f->"`"+f.name+"`=?").collect(Collectors.joining(","))+(s.primaryKey==null?"":" WHERE `"+s.primaryKey+"`=?");
        try(PreparedStatement p=c.prepareStatement(sql)) {
            int i=1;for(Field f:changed)p.setObject(i++,ch.after.get(f.name));
            if(ch.after==null||ch.before!=null&&s.primaryKey!=null)p.setLong(i,ch.id);
            if(p.executeUpdate()!=1)throw new SQLException("Native configuration write count mismatch");
        }
        if(!journal.hash(current(c,s,ch.id)).equals(journal.hash(ch.after)))throw new SQLException("Native configuration readback mismatch");
    }
    public Map<String,Object> save(String key,Edit edit,boolean remove) {
        admin();Section s=section(key);if(!s.writable)throw new Failure(409,"此配置尚未完成规则验证，只读");
        String op=action(edit);if(!(remove?op.equals("delete"):op.equals("upsert")))throw new Failure(400,"HTTP 方法与预览操作不同");return execute(s,edit,op);
    }
    public Map<String,Object> restore(String key,String id,Edit edit) {
        admin();Section s=section(key);String op=action(edit);
        if(!op.equals("restore")||!ManagedOperationJournal.id(id).equals(ManagedOperationJournal.id(edit.targetRequestId)))throw new Failure(400,"恢复目标与预览不一致");return execute(s,edit,op);
    }
    public Map<String,Object> acceptCurrent(String key,String id,Edit edit) {
        admin();Section s=section(key);String op=action(edit);
        if(!op.equals("accept-current")||!ManagedOperationJournal.id(id).equals(ManagedOperationJournal.id(edit.targetRequestId)))throw new Failure(400,"冲突目标与预览不一致");return execute(s,edit,op);
    }
    private Map<String,Object> execute(Section s,Edit edit,String op) {
        long actor=admin();
        try(Connection c=journal.connection()) {
            lock(c,s);try {
                ManagedOperationJournal.Entry entry=journal.require(c,namespace(s.key),edit.requestId);journal.same(entry,namespace(s.key),actor,fingerprint(edit,op));
                checkEntry(s,entry);
                if(!entry.state.equals("PREVIEWED"))return result(entry,s);
                noLegacyPending(c,s);journal.noPending(c,resource(s),op.equals("accept-current")?ManagedOperationJournal.id(edit.targetRequestId):null);guard(c,s);
                Change change=plan(c,s,edit,op,true);
                entry=journal.prepare(c,entry,change.id==null?"singleton":change.id.toString(),change.before,change.after);
                if(op.equals("accept-current"))return result(journal.acknowledge(c,change.source,entry,actor),s);
                if(journal.hash(change.before).equals(journal.hash(change.after)))return result(journal.finish(c,entry,"NO_CHANGE",actor),s);
                write(c,s,change);return result(journal.finish(c,entry,"PERSISTED",actor),s);
            }finally{unlock(c);}
        }catch(SQLException e){throw new Failure(503,"活动操作可能尚未写入或结果尚未确认；请保留操作编号并核对历史，不要更换编号重试");}
    }
    public Map<String,Object> reconcile(String key,String id) {
        long actor=admin();Section s=section(key);
        try(Connection c=journal.connection()) {
            lock(c,s);try {
                ManagedOperationJournal.Entry e=journal.require(c,namespace(key),id);if(!e.state.equals("PREPARED"))return result(e,s);
                checkEntry(s,e);
                Map<String,Object> row=current(c,s,s.primaryKey==null?null:Long.valueOf(e.target));String hash=journal.hash(row);
                if(e.operation.equals("accept-current")) {
                    ManagedOperationJournal.Entry source=journal.require(c,namespace(key),e.targetRequest);
                    if(!source.state.equals("CONFLICT"))throw new Failure(409,"原冲突状态改变，停止自动核对");
                    return result(hash.equals(e.afterHash)?journal.acknowledge(c,source,e,actor):journal.finish(c,e,"NOT_APPLIED",actor),s);
                }
                return result(journal.finish(c,e,hash.equals(e.afterHash)?(e.beforeHash.equals(e.afterHash)?"NO_CHANGE":"PERSISTED"):hash.equals(e.beforeHash)?"NOT_APPLIED":"CONFLICT",actor),s);
            }finally{unlock(c);}
        }catch(SQLException e){throw unavailable();}
    }
    private Map<String,Object> receiptProof(ManagedOperationJournal.Entry e){
        Map<String,Object> out=new TreeMap<>();out.put("requestId",e.requestId);out.put("namespace",e.namespace);out.put("resource",e.resource);out.put("target",e.target);out.put("operation",e.operation);out.put("source",e.targetRequest);out.put("actor",e.actor);out.put("requestHash",e.requestHash);out.put("beforeHash",e.beforeHash);out.put("afterHash",e.afterHash);out.put("state",e.state);return out;
    }
    /** Only return a reconstructed preview if its exact fingerprint still matches. */
    public Map<String,Object> operation(String key,String id){
        long actor=admin();Section s=section(key);
        try(Connection c=journal.connection()){
            journal.schema(c);ManagedOperationJournal.Entry e=journal.require(c,namespace(key),id);Map<String,Object> out=result(e,s);out.put("receiptRevision",journal.hash(receiptProof(e)));out.put("requestReplayAvailable",false);
            if(e.state.equals("PREVIEWED")&&e.actor==actor){
                Map<String,Object> before=snapshot(s,e.beforeJson),ordered=before==null?null:new LinkedHashMap<>();if(ordered!=null)for(Field f:s.fields)ordered.put(f.name,before.get(f.name));
                Edit request=new Edit();request.requestId=e.requestId;request.revision=revision(ordered);request.operation=e.operation;request.targetRequestId=e.targetRequest;request.reason=e.operation.equals("accept-current")?e.reason:null;
                request.values=Arrays.asList("restore","accept-current").contains(e.operation)?null:snapshot(s,e.operation.equals("delete")?e.beforeJson:e.afterJson);
                if(fingerprint(request,e.operation).equals(e.requestHash)){out.put("requestReplayAvailable",true);out.put("request",request);}else out.put("requestReplayUnavailableReason","原始请求的类型或默认值无法精确重建；保留原编号，可核对或取消预览，不能用近似内容重提");
            }else if(e.state.equals("PREVIEWED"))out.put("requestReplayUnavailableReason","原预览属于其他管理员，不能代其提交");
            return out;
        }catch(SQLException e){throw unavailable();}
    }
    private void cancelActor(Connection c,long actor)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT admin FROM d_taiwan.accounts WHERE UID=?")){p.setLong(1,actor);try(ResultSet r=p.executeQuery()){if(!r.next()||r.getInt(1)==0||r.wasNull()||r.next())throw new Failure(403,"管理员权限已改变，停止管理写入");}}
    }
    private Map<String,Object> cancelResult(ManagedOperationJournal.Entry e){Map<String,Object> out=journal.result(e);out.put("revision",e.beforeHash);return out;}
    private String cancelNamespace(String key){return "activity.cancel."+key;}
    private String cancelFingerprint(String source,LegacyEdit e){
        ManagedOperationJournal.id(source);if(e==null)throw new Failure(400,"缺少取消请求");ManagedOperationJournal.id(e.requestId);
        if(e.revision==null||!e.revision.matches("[0-9a-f]{64}")||e.reason==null||e.reason.trim().isEmpty()||e.reason.length()>128||e.reason.codePoints().anyMatch(x->x<32||x>65535||Character.isSurrogate((char)x)))throw new Failure(400,"缺少原回执版本或有效取消原因");
        Map<String,Object> out=new TreeMap<>();out.put("source",ManagedOperationJournal.id(source));out.put("revision",e.revision);out.put("reason",e.reason);return journal.hash(out);
    }
    private ManagedOperationJournal.Entry cancellation(Connection c,Section s,String source,String id)throws SQLException{
        ManagedOperationJournal.Entry e=journal.require(c,cancelNamespace(s.key),id);
        if(!e.target.equals(ManagedOperationJournal.id(source))||!e.target.equals(e.targetRequest)||!e.operation.equals("cancel-preview")||!e.resource.equals(resource(s)+":cancel:"+e.target))throw new Failure(409,"取消回执归属不匹配");return e;
    }
    public Map<String,Object> cancelPreview(String key,String source,LegacyEdit edit,boolean commit){
        long actor=admin();Section s=section(key);String fp=cancelFingerprint(source,edit);
        try(Connection c=journal.connection()){boolean discarded=false;lock(c,s,true);try{
            journal.metadataTransactionGuard(c);cancelActor(c,actor);ManagedOperationJournal.Entry control=journal.find(c,edit.requestId);
            if(control!=null){journal.same(control,cancelNamespace(key),actor,fp);control=cancellation(c,s,source,edit.requestId);if(!control.state.equals("PREVIEWED")||!commit)return cancelResult(control);}
            ManagedOperationJournal.Entry original=journal.require(c,namespace(key),source);checkEntry(s,original);Map<String,Object> proof=receiptProof(original);
            if(!original.state.equals("PREVIEWED")||!journal.hash(proof).equals(edit.revision))throw new Failure(409,"只能取消版本匹配的未提交预览；待核对或已提交操作不能取消");
            String resource=resource(s)+":cancel:"+ManagedOperationJournal.id(source);journal.noPending(c,resource,null);
            if(!commit)return cancelResult(journal.preview(c,edit.requestId,actor,cancelNamespace(key),resource,ManagedOperationJournal.id(source),"cancel-preview",source,fp,edit.reason,proof,proof));
            if(control==null)throw new Failure(404,"请先预览取消操作");
            control=journal.prepare(c,control,control.target,proof,proof);c.setAutoCommit(false);lock(c,s,true);
            try{
                journal.metadataTransactionGuard(c);cancelActor(c,actor);control=cancellation(c,s,source,edit.requestId);if(!control.state.equals("PREPARED")){c.rollback();return cancelResult(control);}
                original=journal.require(c,namespace(key),source);if(!journal.hash(receiptProof(original)).equals(control.beforeHash)){control=journal.finish(c,control,"NOT_APPLIED",actor);c.commit();return cancelResult(control);}
                journal.cancelPreviewInTransaction(c,original,actor);Map<String,Object> outcome=new TreeMap<>();outcome.put("cancelledRequestId",original.requestId);outcome.put("sourceState","NOT_APPLIED");outcome.put("nativeDataChanged",false);control=journal.finishMetadataTransaction(c,control,outcome,actor);c.commit();return cancelResult(control);
            }catch(SQLException|RuntimeException failure){try{c.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);discarded=true;try{c.unwrap(Connection.class).close();}catch(SQLException close){failure.addSuppressed(close);c.abort(Runnable::run);}}throw new Failure(503,"取消结果待核对，请保留取消操作编号，不要新建原操作");}
        }finally{if(!discarded&&!c.isClosed()){unlock(c);if(!c.getAutoCommit())c.setAutoCommit(true);}}}catch(SQLException e){throw unavailable();}
    }
    public List<Map<String,Object>> cancellations(String key,String source){
        admin();Section s=section(key);source=ManagedOperationJournal.id(source);try(Connection c=journal.connection()){
            journal.require(c,namespace(key),source);List<Map<String,Object>> out=new ArrayList<>();for(ManagedOperationJournal.Entry e:journal.history(c,cancelNamespace(key)))if(source.equals(e.targetRequest))out.add(cancelResult(cancellation(c,s,source,e.requestId)));return out;
        }catch(SQLException e){throw unavailable();}
    }
    public Map<String,Object> reconcileCancellation(String key,String source,String id){
        long actor=admin();Section s=section(key);try(Connection c=journal.connection()){lock(c,s,true);try{
            journal.metadataTransactionGuard(c);cancelActor(c,actor);ManagedOperationJournal.Entry e=cancellation(c,s,source,id);
            // Source cancellation and completion commit in the same known InnoDB log transaction.
            if(e.state.equals("PREPARED"))e=journal.finish(c,e,"NOT_APPLIED",actor);return cancelResult(e);
        }finally{unlock(c);}}catch(SQLException e){throw unavailable();}
    }
    public Map<String,Object> currentForHistory(String key,String id) {
        admin();Section s=section(key);
        try(Connection c=journal.connection()) {
            journal.schema(c);ManagedOperationJournal.Entry e=journal.require(c,namespace(key),id);checkEntry(s,e);
            if(e.state.equals("PREVIEWED"))throw new Failure(409,"该操作仅预览，尚无可恢复写入");
            if(s.primaryKey!=null&&!e.target.matches("[0-9]+"))throw new Failure(409,"该预览未分配原生记录编号，没有可恢复写入");
            schema(c,s);return envelope(current(c,s,s.primaryKey==null?null:Long.valueOf(e.target)));
        }catch(SQLException e){throw unavailable();}
    }
    public List<Map<String,Object>> history(String key){return history(key,1);}
    public List<Map<String,Object>> history(String key,int legacyPage) {
        admin();Section s=section(key);if(legacyPage<1||legacyPage>10000)throw new Failure(400,"旧日志页码无效");
        try(Connection c=journal.connection()) {
            List<Map<String,Object>> out=new ArrayList<>();
            for(ManagedOperationJournal.Entry e:journal.history(c,namespace(key))) {
                Map<String,Object> row=result(e,s);row.put("id",e.requestId);row.put("created_at",e.createdAt);row.put("operation_name",e.operation);row.put("before_json",e.beforeJson);row.put("after_json",e.afterJson);row.put("legacy",false);out.add(row);
            }
            if(legacyExists(c))try(PreparedStatement p=c.prepareStatement("SELECT id,actor,operation_name,state,created_at,before_json,after_json FROM "+AUDIT+" WHERE section_name=? ORDER BY (state='pending') DESC,id DESC LIMIT 100 OFFSET ?")) {
                p.setString(1,key);p.setInt(2,(legacyPage-1)*100);try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(String n:Arrays.asList("id","actor","operation_name","state","created_at","before_json","after_json"))row.put(n,r.getString(n));row.put("legacy",true);ManagedOperationJournal.Entry resolution=legacyResolution(c,section(key),Long.parseLong(r.getString("id")),false);row.put("resolution",resolution==null?null:journal.result(resolution));out.add(row);}}
            }
            return out;
        }catch(SQLException e){throw unavailable();}
    }
}
