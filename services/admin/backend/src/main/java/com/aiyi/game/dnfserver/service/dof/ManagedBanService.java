package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.utils.cache.UserTokenCacheUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.sql.*;
import java.util.*;

/** Adapter for the verified 86-level penalty schema; never overwrites another issuer. */
@Service
public class ManagedBanService {
    @Resource private ManagedOperationJournal journal;
    private final ObjectMapper json=new ObjectMapper();
    private static final String TABLE="`d_taiwan`.`member_punish_info`",ACCOUNTS="`d_taiwan`.`accounts`",OWNER="dof-easy";
    private static final List<String> COLUMNS=Arrays.asList("m_id","punish_type","occ_time","punish_value","apply_flag","start_time","end_time","admin_id","reason");
    private static final Set<String> NUMBERS=new HashSet<>(Arrays.asList("m_id","punish_type","punish_value","apply_flag"));
    public static class Edit {public String requestId,revision,operation,targetRequestId,reason;public int days;}
    private String ns(long uid){return "account-ban."+uid;}
    private String resource(long uid){return "d_taiwan.member_punish_info:"+uid;}
    private void target(long uid){if(uid<1||uid>Integer.MAX_VALUE)throw new DofFailure(400,"账号编号无效");}
    private DofFailure unavailable(){return new DofFailure(503,"处罚结果未确认，请保留操作编号核对历史；没有自动重发或覆盖其他来源处罚");}
    private void schema(Connection c)throws SQLException {
        Map<String,String> types=new HashMap<>();Set<String> pk=new HashSet<>();Map<String,Integer> lengths=new HashMap<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COLUMN_NAME,DATA_TYPE,COLUMN_KEY,CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='d_taiwan' AND TABLE_NAME='member_punish_info'")) {
            while(r.next()){types.put(r.getString(1),r.getString(2));if("PRI".equals(r.getString(3)))pk.add(r.getString(1));lengths.put(r.getString(1),r.getInt(4));}
        }
        if(!types.keySet().equals(new HashSet<>(COLUMNS))||!pk.equals(new HashSet<>(Arrays.asList("m_id","punish_type"))))throw incompatible();
        for(String key:NUMBERS)if(!(key.equals("apply_flag")?"tinyint":"int").equals(types.get(key)))throw incompatible();
        for(String key:Arrays.asList("occ_time","start_time","end_time"))if(!"datetime".equals(types.get(key)))throw incompatible();
        if(!"varchar".equals(types.get("admin_id"))||lengths.get("admin_id")!=25||!"varchar".equals(types.get("reason"))||lengths.get("reason")!=100)throw incompatible();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COLUMN_NAME,DATA_TYPE,COLUMN_KEY FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='d_taiwan' AND TABLE_NAME='accounts' AND COLUMN_NAME IN ('UID','admin','accountname')")) {
            Map<String,String> account=new HashMap<>();boolean primary=false;while(r.next()){account.put(r.getString(1),r.getString(2));if(r.getString(1).equals("UID")&&r.getString(3).equals("PRI"))primary=true;}
            if(!primary||!"int".equals(account.get("UID"))||!"int".equals(account.get("admin"))||!"varchar".equals(account.get("accountname")))throw incompatible();
        }
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='d_taiwan' AND TABLE_NAME IN ('accounts','member_punish_info')")) {
            int n=0;while(r.next()){if(!Arrays.asList("InnoDB","MyISAM").contains(r.getString(1)))throw incompatible();n++;}if(n!=2)throw incompatible();
        }
    }
    private DofFailure incompatible(){return new DofFailure(409,"处罚或账号表与已验证的 86 级结构不匹配，停止写入；没有更改表结构或引擎");}
    private void lock(Connection c)throws SQLException {journal.lock(c,TABLE+" WRITE,"+ACCOUNTS+" READ");schema(c);}
    private void account(Connection c,long uid,boolean writing,long actor)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT admin FROM "+ACCOUNTS+" WHERE UID=?")){p.setLong(1,uid);try(ResultSet r=p.executeQuery()){
            if(!r.next())throw new DofFailure(404,"账号不存在");boolean admin=r.getInt(1)!=0;if(r.next())throw incompatible();
            if(writing&&(admin||uid==actor))throw new DofFailure(409,"不能修改管理员或自己的处罚");
        }}
    }
    private Map<String,Object> read(Connection c,long uid)throws SQLException {
        Map<String,Object> state=new LinkedHashMap<>();state.put("uid",(int)uid);
        try(PreparedStatement p=c.prepareStatement("SELECT accountname FROM "+ACCOUNTS+" WHERE UID=?")){p.setLong(1,uid);try(ResultSet r=p.executeQuery()){if(!r.next()||r.getString(1)==null)throw new DofFailure(409,"账号身份已改变，停止处罚操作");state.put("accountIdentity",journal.hash(Collections.singletonMap("accountname",r.getString(1))));if(r.next())throw incompatible();}}
        Map<String,Object> row=null;
        try(PreparedStatement p=c.prepareStatement("SELECT `"+String.join("`,`",COLUMNS)+"` FROM "+TABLE+" WHERE m_id=? AND punish_type=1")) {
            p.setLong(1,uid);try(ResultSet r=p.executeQuery()){if(r.next()){
                row=new LinkedHashMap<>();for(String k:COLUMNS){Object v;if(NUMBERS.contains(k)){v=r.getInt(k);if(r.wasNull())throw incompatible();}else{v=r.getString(k);if(v==null)throw incompatible();if(k.endsWith("time"))v=v.toString().replaceFirst("\\.0+$","");}row.put(k,v);}if(r.next())throw incompatible();
            }}
        }state.put("penalty",row);return state;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> penalty(Map<String,Object> state){return (Map<String,Object>)state.get("penalty");}
    private boolean own(Map<String,Object> row){return row!=null&&OWNER.equals(row.get("admin_id"))&&((Number)row.get("punish_value")).intValue()==101;}
    private Map<String,Object> copy(Map<String,Object> state){Map<String,Object> out=new LinkedHashMap<>(state);out.put("penalty",penalty(state)==null?null:new LinkedHashMap<>(penalty(state)));return out;}
    private String action(Edit e) {
        if(e==null||e.revision==null||!e.revision.matches("[0-9a-f]{64}"))throw new DofFailure(400,"缺少有效处罚版本");ManagedOperationJournal.id(e.requestId);
        if(!Arrays.asList("ban","unban","restore","accept-current").contains(e.operation))throw new DofFailure(400,"未知处罚操作");
        if(e.reason==null||e.reason.trim().isEmpty()||e.reason.length()>100||e.reason.codePoints().anyMatch(x->x<32||x>65535||Character.isSurrogate((char)x)))throw new DofFailure(400,"请填写 1–100 字操作原因");
        if(e.operation.equals("ban")){if(e.days<1||e.days>3650)throw new DofFailure(400,"封禁期限须为 1–3650 天");}else if(e.days!=0)throw new DofFailure(400,"此操作不接受封禁天数");
        if(Arrays.asList("restore","accept-current").contains(e.operation))ManagedOperationJournal.id(e.targetRequestId);else if(e.targetRequestId!=null)throw new DofFailure(400,"此操作不接受历史目标");return e.operation;
    }
    private String fingerprint(long uid,Edit e){Map<String,Object> out=new LinkedHashMap<>();out.put("uid",uid);out.put("revision",e.revision);out.put("operation",e.operation);out.put("days",e.days);out.put("reason",e.reason);out.put("targetRequestId",e.targetRequestId==null?null:ManagedOperationJournal.id(e.targetRequestId));return journal.hash(out);}
    private Map<String,Object> snapshot(long uid,String text) {
        try {
            Map<String,Object> state=json.readValue(text,new TypeReference<Map<String,Object>>(){});
            if(state==null||!state.keySet().equals(new HashSet<>(Arrays.asList("uid","accountIdentity","penalty")))||!json.valueToTree(state.get("uid")).isIntegralNumber()||((Number)state.get("uid")).longValue()!=uid||!String.valueOf(state.get("accountIdentity")).matches("[0-9a-f]{64}"))throw new IllegalArgumentException();
            Map<String,Object> row=penalty(state);if(row!=null){if(!row.keySet().equals(new HashSet<>(COLUMNS)))throw new IllegalArgumentException();for(String k:COLUMNS){Object v=row.get(k);if(NUMBERS.contains(k)){if(!(v instanceof Number)||!json.valueToTree(v).isIntegralNumber()||!json.valueToTree(v).canConvertToInt())throw new IllegalArgumentException();}else if(!(v instanceof String))throw new IllegalArgumentException();}if(((Number)row.get("m_id")).longValue()!=uid||((Number)row.get("punish_type")).intValue()!=1)throw new IllegalArgumentException();}return state;
        }catch(Exception ex){throw new DofFailure(409,"处罚操作快照损坏，停止恢复");}
    }
    private ManagedOperationJournal.Entry source(Connection c,long uid,String id)throws SQLException {
        ManagedOperationJournal.Entry e=journal.require(c,ns(uid),id);if(!resource(uid).equals(e.resource)||!Long.toString(uid).equals(e.target))throw new DofFailure(409,"处罚历史目标不匹配");snapshot(uid,e.beforeJson);snapshot(uid,e.afterJson);return e;
    }
    private Map<String,Object> plan(Connection c,long uid,Edit e,Map<String,Object> before)throws SQLException {
        if(!journal.hash(before).equals(e.revision))throw new DofFailure(409,"处罚已改变，请重新读取并预览");Map<String,Object> out=copy(before);Map<String,Object> row=penalty(before);
        if(e.operation.equals("ban")) {
            if(row!=null&&!own(row))throw new DofFailure(409,"原生处罚键已被其他来源或处罚类型占用，不能覆盖");
            Map<String,Object> next=new LinkedHashMap<>();next.put("m_id",(int)uid);next.put("punish_type",1);next.put("punish_value",101);next.put("apply_flag",2);next.put("admin_id",OWNER);next.put("reason",e.reason);
            try(PreparedStatement p=c.prepareStatement("SELECT DATE_FORMAT(NOW(),'%Y-%m-%d %H:%i:%s'),DATE_FORMAT(TIMESTAMPADD(DAY,?,NOW()),'%Y-%m-%d %H:%i:%s')")){p.setInt(1,e.days);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("No server clock");next.put("occ_time",r.getString(1));next.put("start_time",r.getString(1));next.put("end_time",r.getString(2));}}
            out.put("penalty",next);return out;
        }
        if(e.operation.equals("unban")){if(own(row))penalty(out).put("apply_flag",0);return out;}
        ManagedOperationJournal.Entry old=source(c,uid,e.targetRequestId);
        if(e.operation.equals("accept-current")){if(!old.state.equals("CONFLICT"))throw new DofFailure(409,"只能确认冲突记录的当前值");return before;}
        if(!old.state.equals("PERSISTED"))throw new DofFailure(409,"只能恢复已确认写入的处罚操作");
        Map<String,Object> a=penalty(snapshot(uid,old.beforeJson)),b=penalty(snapshot(uid,old.afterJson));
        if(b==null||!own(b)||!own(row))throw new DofFailure(409,"当前处罚已被删除或变为其他来源，拒绝恢复");
        if(a==null){if(!journal.hash(row).equals(journal.hash(b)))throw new DofFailure(409,"新增处罚已有其他修改，拒绝移除");out.put("penalty",null);return out;}
        if(!own(a))throw new DofFailure(409,"旧处罚来源不是本后台，拒绝恢复");
        for(String k:COLUMNS)if(!Objects.equals(String.valueOf(a.get(k)),String.valueOf(b.get(k)))){
            if(!Objects.equals(String.valueOf(row.get(k)),String.valueOf(b.get(k))))throw new DofFailure(409,"恢复字段已有第三方修改："+k);penalty(out).put(k,a.get(k));
        }return out;
    }
    private List<Map<String,Object>> changes(Map<String,Object> a,Map<String,Object> b){List<Map<String,Object>> out=new ArrayList<>();for(String k:COLUMNS){Object x=penalty(a)==null?null:penalty(a).get(k),y=penalty(b)==null?null:penalty(b).get(k);if(!Objects.equals(String.valueOf(x),String.valueOf(y))){Map<String,Object> item=new LinkedHashMap<>();item.put("field",k);item.put("before",x);item.put("after",y);out.add(item);}}return out;}
    public Object status(long uid){journal.admin();target(uid);try(Connection c=journal.connection()){schema(c);account(c,uid,false,0);Map<String,Object> state=read(c,uid);Map<String,Object> out=new LinkedHashMap<>(state);out.put("revision",journal.hash(state));out.put("managedByThisBackend",own(penalty(state)));out.put("effectState","not_observed");return out;}catch(SQLException e){throw unavailable();}}
    public Object preview(long uid,Edit edit) {
        long actor=journal.admin();target(uid);action(edit);
        try(Connection c=journal.connection()){lock(c);try{
            ManagedOperationJournal.Entry existing=journal.find(c,edit.requestId);if(existing!=null){journal.same(existing,ns(uid),actor,fingerprint(uid,edit));source(c,uid,edit.requestId);Map<String,Object> out=journal.result(existing);out.put("changes",changes(snapshot(uid,existing.beforeJson),snapshot(uid,existing.afterJson)));return out;}
            journal.noPending(c,resource(uid),edit.operation.equals("accept-current")?ManagedOperationJournal.id(edit.targetRequestId):null);account(c,uid,true,actor);
            Map<String,Object> before=read(c,uid),after=plan(c,uid,edit,before);ManagedOperationJournal.Entry e=journal.preview(c,edit.requestId,actor,ns(uid),resource(uid),Long.toString(uid),edit.operation,edit.targetRequestId,fingerprint(uid,edit),edit.reason,before,after);
            Map<String,Object> out=journal.result(e);out.put("changes",changes(before,after));return out;
        }finally{journal.unlock(c);}}catch(SQLException e){throw unavailable();}
    }
    private void write(Connection c,long uid,Map<String,Object> before,Map<String,Object> after)throws SQLException {
        Map<String,Object> a=penalty(before),b=penalty(after);List<String> fields=new ArrayList<>();String sql;
        if(b==null)sql="DELETE FROM "+TABLE+" WHERE m_id=? AND punish_type=1 AND BINARY admin_id='dof-easy' AND punish_value=101";
        else if(a==null){fields.addAll(COLUMNS);sql="INSERT INTO "+TABLE+" (`"+String.join("`,`",fields)+"`) VALUES("+String.join(",",Collections.nCopies(fields.size(),"?"))+")";}
        else {for(String k:COLUMNS)if(!Objects.equals(String.valueOf(a.get(k)),String.valueOf(b.get(k))))fields.add(k);List<String> assignments=new ArrayList<>();for(String k:fields)assignments.add("`"+k+"`=?");sql="UPDATE "+TABLE+" SET "+String.join(",",assignments)+" WHERE m_id=? AND punish_type=1 AND BINARY admin_id='dof-easy' AND punish_value=101";}
        try(PreparedStatement p=c.prepareStatement(sql)){int n=1;for(String k:fields)p.setObject(n++,b.get(k));if(a!=null)p.setLong(n,uid);if(p.executeUpdate()!=1)throw new SQLException("Penalty write count mismatch");}
        if(!journal.hash(read(c,uid)).equals(journal.hash(after)))throw new SQLException("Penalty readback mismatch");
    }
    public Object save(long uid,Edit edit,String methodOperation) {
        long actor=journal.admin();target(uid);String op=action(edit);if(!op.equals(methodOperation))throw new DofFailure(400,"HTTP 动作与预览不同");boolean prepared=false;
        try(Connection c=journal.connection()){lock(c);try{
            ManagedOperationJournal.Entry e=source(c,uid,edit.requestId);journal.same(e,ns(uid),actor,fingerprint(uid,edit));if(!e.state.equals("PREVIEWED"))return journal.result(e);
            journal.noPending(c,resource(uid),op.equals("accept-current")?ManagedOperationJournal.id(edit.targetRequestId):null);account(c,uid,true,actor);Map<String,Object> before=read(c,uid);if(!journal.hash(before).equals(edit.revision)||!journal.hash(before).equals(e.beforeHash))throw new DofFailure(409,"处罚版本已改变，请重新读取并预览");
            Map<String,Object> after=snapshot(uid,e.afterJson);if(!op.equals("ban")&&!journal.hash(plan(c,uid,edit,before)).equals(e.afterHash))throw new DofFailure(409,"处罚预览目标已改变");
            if(op.equals("ban"))try(PreparedStatement p=c.prepareStatement("SELECT ? > NOW()")){p.setString(1,String.valueOf(penalty(after).get("end_time")));try(ResultSet r=p.executeQuery()){if(!r.next()||!r.getBoolean(1))throw new DofFailure(409,"预览中的封禁期限已过，请重新预览；没有自动延长期限");}}
            e=journal.prepare(c,e,Long.toString(uid),before,after);
            if(op.equals("accept-current"))return journal.result(journal.acknowledge(c,source(c,uid,edit.targetRequestId),e,actor));
            if(journal.hash(before).equals(journal.hash(after)))return journal.result(journal.finish(c,e,"NO_CHANGE",actor));
            prepared=true;write(c,uid,before,after);return journal.result(journal.finish(c,e,"PERSISTED",actor));
        }finally{journal.unlock(c);}}catch(SQLException e){throw unavailable();}finally{if(prepared)UserTokenCacheUtil.clear(uid);}
    }
    public Object reconcile(long uid,String requestId) {
        long actor=journal.admin();target(uid);try(Connection c=journal.connection()){lock(c);try{
            ManagedOperationJournal.Entry e=source(c,uid,requestId);if(!e.state.equals("PREPARED"))return journal.result(e);String h=journal.hash(read(c,uid));
            if(e.operation.equals("accept-current")){ManagedOperationJournal.Entry old=source(c,uid,e.targetRequest);if(!old.state.equals("CONFLICT"))throw new DofFailure(409,"原处罚冲突状态改变");return journal.result(h.equals(e.afterHash)?journal.acknowledge(c,old,e,actor):journal.finish(c,e,"NOT_APPLIED",actor));}
            return journal.result(journal.finish(c,e,h.equals(e.afterHash)?(e.beforeHash.equals(e.afterHash)?"NO_CHANGE":"PERSISTED"):h.equals(e.beforeHash)?"NOT_APPLIED":"CONFLICT",actor));
        }finally{journal.unlock(c);}}catch(SQLException e){throw unavailable();}
    }
    public Object history(long uid){journal.admin();target(uid);try(Connection c=journal.connection()){List<Object> out=new ArrayList<>();for(ManagedOperationJournal.Entry e:journal.history(c,ns(uid))){source(c,uid,e.requestId);out.add(journal.result(e));}return out;}catch(SQLException e){throw unavailable();}}
}
