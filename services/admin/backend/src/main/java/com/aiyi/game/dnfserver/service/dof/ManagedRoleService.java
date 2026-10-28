package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.sql.*;
import java.util.*;

/** Fixed offline character attribute adapter; never writes an entire ORM entity. */
@Service
public class ManagedRoleService {
    @Resource private ManagedOperationJournal journal;
    private final ObjectMapper json=new ObjectMapper();
    private static final String TABLE="`taiwan_cain`.`charac_info`",ONLINE="`taiwan_login`.`login_account_3`";
    private static class Field {
        final String key,column,label;final boolean unsigned;
        Field(String key,String column,String label,boolean unsigned){this.key=key;this.column=column;this.label=label;this.unsigned=unsigned;}
        int max(){return unsigned?65535:32767;}
    }
    private static final List<Field> FIELDS=Arrays.asList(
        new Field("maxHp","maxHP","HP",true),new Field("maxMp","maxMP","MP",true),
        new Field("phyAttack","phy_attack","物攻",true),new Field("phyDefense","phy_defense","物防",true),
        new Field("magAttack","mag_attack","魔攻",true),new Field("magDefense","mag_defense","魔防",true),
        new Field("attackSpeed","attack_speed","攻速",true),new Field("castSpeed","cast_speed","施速",true),new Field("moveSpeed","move_speed","移速",true),
        new Field("hitRecovery","hit_recovery","硬直",false),new Field("jump","jump","跳跃",false),new Field("fatigue","fatigue","疲劳值",false));
    public static class Edit {public long characNo;public String requestId,revision,operation,targetRequestId,reason;public Map<String,JsonNode> values;}
    private String namespace(long id){return "role."+id;}
    private String resource(long id){return "taiwan_cain.charac_info:"+id;}
    private void target(long id){if(id<1||id>Integer.MAX_VALUE)throw new DofFailure(400,"角色编号无效");}
    private void schema(Connection c)throws SQLException {
        Map<String,String> actual=new HashMap<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT TABLE_SCHEMA,TABLE_NAME,COLUMN_NAME,DATA_TYPE,COLUMN_TYPE FROM information_schema.COLUMNS WHERE (TABLE_SCHEMA='taiwan_cain' AND TABLE_NAME='charac_info') OR (TABLE_SCHEMA='taiwan_login' AND TABLE_NAME='login_account_3')")) {
            while(r.next())actual.put(r.getString(1)+"."+r.getString(2)+"."+r.getString(3),r.getString(4)+(r.getString(5).toLowerCase(Locale.ROOT).contains("unsigned")?" unsigned":""));
        }
        for(Field f:FIELDS)if(!("smallint"+(f.unsigned?" unsigned":"")).equals(actual.get("taiwan_cain.charac_info."+f.column)))throw new DofFailure(409,"角色属性字段与已验证的 86 级结构不匹配："+f.label);
        for(String key:Arrays.asList("taiwan_cain.charac_info.charac_no","taiwan_cain.charac_info.m_id","taiwan_login.login_account_3.m_id"))if(!Arrays.asList("int","int unsigned").contains(actual.get(key)))throw new DofFailure(409,"角色或在线状态编号字段不兼容");
        if(!"tinyint".equals(actual.get("taiwan_login.login_account_3.login_status")))throw new DofFailure(409,"在线状态字段不兼容，停止写入");
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT TABLE_SCHEMA,TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE (TABLE_SCHEMA='taiwan_cain' AND TABLE_NAME='charac_info') OR (TABLE_SCHEMA='taiwan_login' AND TABLE_NAME='login_account_3')")) {
            int found=0;while(r.next()){if(!Arrays.asList("InnoDB","MyISAM").contains(r.getString(3)))throw new DofFailure(409,"角色或在线状态表引擎不受支持");found++;}if(found!=2)throw new DofFailure(409,"缺少角色或在线状态表");
        }
    }
    private Map<String,Object> read(Connection c,long id)throws SQLException {
        List<String> columns=new ArrayList<>(Arrays.asList("charac_no","m_id"));for(Field f:FIELDS)columns.add(f.column);
        try(PreparedStatement p=c.prepareStatement("SELECT `"+String.join("`,`",columns)+"` FROM "+TABLE+" WHERE charac_no=? LIMIT 2")) {
            p.setLong(1,id);try(ResultSet r=p.executeQuery()) {
                if(!r.next())return null;Map<String,Object> result=new LinkedHashMap<>();result.put("characNo",r.getLong("charac_no"));result.put("accountId",r.getLong("m_id"));Map<String,Integer> values=new LinkedHashMap<>();
                for(Field f:FIELDS){int n=r.getInt(f.column);if(r.wasNull())throw new DofFailure(409,"角色属性存在未适配的空值");values.put(f.key,n);}result.put("values",values);
                if(r.next())throw new DofFailure(409,"角色编号不唯一，停止写入");return result;
            }
        }
    }
    @SuppressWarnings("unchecked") private Map<String,Object> values(Map<String,Object> state){return (Map<String,Object>)state.get("values");}
    private String online(Connection c,Map<String,Object> state)throws SQLException {
        if(state==null)return "missing";
        try(PreparedStatement p=c.prepareStatement("SELECT login_status FROM "+ONLINE+" WHERE m_id=? LIMIT 2")) {
            p.setLong(1,((Number)state.get("accountId")).longValue());try(ResultSet r=p.executeQuery()) {
                if(!r.next())return "unknown";int value=r.getInt(1);boolean empty=r.wasNull();if(r.next()||empty)return "unknown";return value==0?"offline":value==1?"online":"unknown";
            }
        }
    }
    private void offline(Connection c,Map<String,Object> state)throws SQLException {
        if(state==null)throw new DofFailure(404,"角色不存在");String status=online(c,state);
        if(!status.equals("offline"))throw new DofFailure(409,status.equals("online")?"账号仍在线，角色属性写入已拒绝；请退出账号后重新读取":"无法可靠确认账号离线，停止属性写入");
    }
    public Map<String,Object> read(long id) {
        journal.admin();target(id);
        try(Connection c=journal.connection()) {
            schema(c);Map<String,Object> state=read(c,id);if(state==null)throw new DofFailure(404,"角色不存在");
            Map<String,Object> out=new LinkedHashMap<>(state);out.put("revision",journal.hash(state));String status=online(c,state);out.put("onlineState",status);out.put("editable",status.equals("offline"));out.put("effectState","not_observed");
            List<Object> fields=new ArrayList<>();for(Field f:FIELDS){Map<String,Object> row=new LinkedHashMap<>();row.put("key",f.key);row.put("label",f.label);row.put("min",0);row.put("max",f.max());fields.add(row);}out.put("fields",fields);return out;
        }catch(SQLException e){throw unavailable();}
    }
    private DofFailure unavailable(){return new DofFailure(503,"角色管理暂不可用；写入中断时请保留操作编号，先核对历史");}
    private String action(long id,Edit e) {
        target(id);if(e==null||e.characNo!=id||e.revision==null||!e.revision.matches("[0-9a-f]{64}"))throw new DofFailure(400,"缺少角色编号或有效属性版本");ManagedOperationJournal.id(e.requestId);
        String op=e.operation==null?"patch":e.operation;
        if(!Arrays.asList("patch","restore","accept-current").contains(op))throw new DofFailure(400,"未知角色管理操作");
        if(!op.equals("patch"))ManagedOperationJournal.id(e.targetRequestId);else if(e.targetRequestId!=null)throw new DofFailure(400,"普通属性修改不能指定历史操作");
        if(op.equals("accept-current")){if(e.reason==null||e.reason.trim().isEmpty()||e.reason.length()>128||e.reason.codePoints().anyMatch(x->x<32||x>65535||Character.isSurrogate((char)x)))throw new DofFailure(400,"请填写保留当前属性并关闭冲突的原因");}
        else if(e.reason!=null)throw new DofFailure(400,"此操作不接受原因字段");return op;
    }
    private String fingerprint(Edit e,String op){Map<String,Object> data=new LinkedHashMap<>();data.put("characNo",e.characNo);data.put("revision",e.revision);data.put("operation",op);data.put("values",e.values);data.put("targetRequestId",e.targetRequestId==null?null:ManagedOperationJournal.id(e.targetRequestId));data.put("reason",e.reason);return journal.hash(data);}
    private Map<String,Object> copy(Map<String,Object> state){Map<String,Object> result=new LinkedHashMap<>(state);result.put("values",new LinkedHashMap<>(values(state)));return result;}
    private long number(Field f,JsonNode value){if(value==null||!value.isIntegralNumber()||!value.canConvertToLong()||value.longValue()<0||value.longValue()>f.max())throw new DofFailure(400,f.label+"必须是 0–"+f.max()+" 的整数");return value.longValue();}
    private ManagedOperationJournal.Entry source(Connection c,long id,String requestId)throws SQLException {
        ManagedOperationJournal.Entry e=journal.require(c,namespace(id),requestId);check(id,e);return e;
    }
    private void check(long id,ManagedOperationJournal.Entry e) {
        if(!namespace(id).equals(e.namespace)||!resource(id).equals(e.resource)||!Long.toString(id).equals(e.target))throw new DofFailure(409,"角色操作日志目标不匹配");
        if(snapshot(id,e.beforeJson)==null||snapshot(id,e.afterJson)==null)throw new DofFailure(409,"角色操作快照缺失，停止恢复");
    }
    private Map<String,Object> snapshot(long id,String text) {
        try {
            Map<String,Object> state=json.readValue(text,new TypeReference<Map<String,Object>>(){});if(state==null)return null;
            if(!state.keySet().equals(new HashSet<>(Arrays.asList("characNo","accountId","values")))||!(state.get("characNo") instanceof Number)||((Number)state.get("characNo")).longValue()!=id||!(state.get("accountId") instanceof Number))throw new IllegalArgumentException();
            if(!json.valueToTree(state.get("characNo")).isIntegralNumber()||!json.valueToTree(state.get("accountId")).isIntegralNumber()||((Number)state.get("accountId")).longValue()<1)throw new IllegalArgumentException();
            Map<String,Object> v=values(state);Set<String> keys=new HashSet<>();for(Field f:FIELDS)keys.add(f.key);if(!keys.equals(v.keySet()))throw new IllegalArgumentException();
            for(Field f:FIELDS){Object value=v.get(f.key);JsonNode node=json.valueToTree(value);if(!(value instanceof Number)||!node.isIntegralNumber()||!node.canConvertToLong()||node.longValue()<(f.unsigned?0:-32768)||node.longValue()>f.max())throw new IllegalArgumentException();}return state;
        }catch(Exception e){throw new DofFailure(409,"角色属性快照损坏，停止恢复");}
    }
    private Map<String,Object> next(Connection c,long id,Edit e,String op,Map<String,Object> current)throws SQLException {
        if(!journal.hash(current).equals(e.revision))throw new DofFailure(409,"角色属性版本已改变，请重新读取并预览");
        if(op.equals("patch")) {
            if(e.values==null||e.values.isEmpty())throw new DofFailure(400,"没有提交属性补丁");Map<String,Object> result=copy(current);
            for(Map.Entry<String,JsonNode> entry:e.values.entrySet()) {
                Field f=FIELDS.stream().filter(x->x.key.equals(entry.getKey())).findFirst().orElseThrow(()->new DofFailure(400,"未知或禁止修改的角色字段"));values(result).put(f.key,number(f,entry.getValue()));
            }return result;
        }
        if(e.values!=null)throw new DofFailure(400,"历史操作不接受浏览器提供属性快照");ManagedOperationJournal.Entry old=source(c,id,e.targetRequestId);
        if(op.equals("accept-current")){if(!old.state.equals("CONFLICT"))throw new DofFailure(409,"只能确认保留冲突记录的当前属性");return current;}
        if(!Arrays.asList("PERSISTED","NO_CHANGE").contains(old.state))throw new DofFailure(409,"只能恢复已确认持久化的属性修改");
        Map<String,Object> a=snapshot(id,old.beforeJson),b=snapshot(id,old.afterJson);
        if(a==null||b==null||!Objects.equals(a.get("accountId").toString(),current.get("accountId").toString())||!Objects.equals(b.get("accountId").toString(),current.get("accountId").toString()))throw new DofFailure(409,"角色归属已改变，不能恢复旧属性");
        Map<String,Object> result=copy(current);
        for(Field f:FIELDS)if(!values(a).get(f.key).toString().equals(values(b).get(f.key).toString())) {
            if(!values(current).get(f.key).toString().equals(values(b).get(f.key).toString()))throw new DofFailure(409,"本次涉及的属性已有第三方修改："+f.label);
            values(result).put(f.key,values(a).get(f.key));
        }return result;
    }
    private void lock(Connection c)throws SQLException{journal.lock(c,TABLE+" WRITE,"+ONLINE+" READ");schema(c);}
    private List<Map<String,Object>> changes(Map<String,Object> a,Map<String,Object> b){List<Map<String,Object>> out=new ArrayList<>();for(Field f:FIELDS)if(!values(a).get(f.key).toString().equals(values(b).get(f.key).toString())){Map<String,Object> row=new LinkedHashMap<>();row.put("key",f.key);row.put("label",f.label);row.put("before",values(a).get(f.key));row.put("after",values(b).get(f.key));out.add(row);}return out;}
    public Object preview(long id,Edit edit) {
        long actor=journal.admin();String op=action(id,edit);
        try(Connection c=journal.connection()) {lock(c);try {
            ManagedOperationJournal.Entry existing=journal.find(c,edit.requestId);
            if(existing!=null){journal.same(existing,namespace(id),actor,fingerprint(edit,op));check(id,existing);Map<String,Object> out=journal.result(existing);out.put("changes",changes(snapshot(id,existing.beforeJson),snapshot(id,existing.afterJson)));return out;}
            journal.noPending(c,resource(id),op.equals("accept-current")?ManagedOperationJournal.id(edit.targetRequestId):null);
            Map<String,Object> before=read(c,id);offline(c,before);Map<String,Object> after=next(c,id,edit,op,before);
            ManagedOperationJournal.Entry e=journal.preview(c,edit.requestId,actor,namespace(id),resource(id),Long.toString(id),op,edit.targetRequestId,fingerprint(edit,op),edit.reason,before,after);
            Map<String,Object> out=journal.result(e);out.put("changes",changes(before,after));return out;
        }finally{journal.unlock(c);}}catch(SQLException e){throw unavailable();}
    }
    public Object save(Edit edit) {
        long actor=journal.admin();String op=action(edit==null?0:edit.characNo,edit);long id=edit.characNo;
        try(Connection c=journal.connection()) {lock(c);try {
            ManagedOperationJournal.Entry e=source(c,id,edit.requestId);journal.same(e,namespace(id),actor,fingerprint(edit,op));if(!e.state.equals("PREVIEWED"))return journal.result(e);
            journal.noPending(c,resource(id),op.equals("accept-current")?ManagedOperationJournal.id(edit.targetRequestId):null);
            Map<String,Object> before=read(c,id);offline(c,before);Map<String,Object> after=next(c,id,edit,op,before);e=journal.prepare(c,e,Long.toString(id),before,after);
            if(op.equals("accept-current"))return journal.result(journal.acknowledge(c,source(c,id,edit.targetRequestId),e,actor));
            List<Map<String,Object>> changes=changes(before,after);if(changes.isEmpty())return journal.result(journal.finish(c,e,"NO_CHANGE",actor));
            List<Field> fields=new ArrayList<>();for(Field f:FIELDS)if(!values(before).get(f.key).toString().equals(values(after).get(f.key).toString()))fields.add(f);
            List<String> assignments=new ArrayList<>();for(Field f:fields)assignments.add("`"+f.column+"`=?");
            try(PreparedStatement p=c.prepareStatement("UPDATE "+TABLE+" SET "+String.join(",",assignments)+" WHERE charac_no=? AND m_id=?")) {
                int n=1;for(Field f:fields)p.setLong(n++,((Number)values(after).get(f.key)).longValue());p.setLong(n++,id);p.setLong(n,((Number)before.get("accountId")).longValue());if(p.executeUpdate()!=1)throw new SQLException("Character update count mismatch");
            }
            if(!journal.hash(read(c,id)).equals(journal.hash(after)))throw new SQLException("Character update readback mismatch");return journal.result(journal.finish(c,e,"PERSISTED",actor));
        }finally{journal.unlock(c);}}catch(SQLException e){throw unavailable();}
    }
    public Object reconcile(long id,String requestId) {
        long actor=journal.admin();target(id);
        try(Connection c=journal.connection()){lock(c);try {
            ManagedOperationJournal.Entry e=source(c,id,requestId);if(!e.state.equals("PREPARED"))return journal.result(e);String hash=journal.hash(read(c,id));
            if(e.operation.equals("accept-current")){ManagedOperationJournal.Entry old=source(c,id,e.targetRequest);if(!old.state.equals("CONFLICT"))throw new DofFailure(409,"原冲突状态改变，停止自动核对");return journal.result(hash.equals(e.afterHash)?journal.acknowledge(c,old,e,actor):journal.finish(c,e,"NOT_APPLIED",actor));}
            return journal.result(journal.finish(c,e,hash.equals(e.afterHash)?"PERSISTED":hash.equals(e.beforeHash)?"NOT_APPLIED":"CONFLICT",actor));
        }finally{journal.unlock(c);}}catch(SQLException e){throw unavailable();}
    }
    public Object history(long id) {
        journal.admin();target(id);try(Connection c=journal.connection()){List<Object> rows=new ArrayList<>();for(ManagedOperationJournal.Entry e:journal.history(c,namespace(id))){check(id,e);rows.add(journal.result(e));}return rows;}catch(SQLException e){throw unavailable();}
    }
}
