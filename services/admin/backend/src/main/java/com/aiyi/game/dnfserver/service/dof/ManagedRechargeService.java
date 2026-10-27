package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.sql.*;
import java.util.*;

/** Administrative balance changes. Native billing transaction markers remain untouched. */
@Service
public class ManagedRechargeService {
    @Resource private ManagedOperationJournal journal;
    private static final String WALLET="`taiwan_billing`.`cash_cera`",ACCOUNTS="`d_taiwan`.`accounts`";
    private static final String PROTOCOL="recharge-atomic-v1";
    private static final long LIMIT=1999999999L,MAX_DELTA=9999999L;
    private final ObjectMapper json=new ObjectMapper();
    public static class Edit { public JsonNode uid,cera;public String requestId,revision,operation,targetRequestId,reason; }
    private String namespace(long uid){return "recharge."+uid;}
    private String resource(long uid){return "taiwan_billing.cash_cera:"+uid;}
    private void target(long uid){if(uid<1||uid>Integer.MAX_VALUE)throw new DofFailure(400,"账号编号无效");}
    public long uid(Edit e){
        if(e==null||e.uid==null)throw new DofFailure(400,"缺少目标账号编号");
        long value;
        if(e.uid.isIntegralNumber()&&e.uid.canConvertToLong())value=e.uid.longValue();
        else if(e.uid.isTextual()&&e.uid.textValue().matches("[1-9][0-9]{0,9}"))value=Long.parseLong(e.uid.textValue());
        else throw new DofFailure(400,"账号编号必须为整数");
        target(value);return value;
    }
    private long amount(Edit e){
        if("accept-current".equals(e.operation)){
            if(e.cera!=null&&!e.cera.isNull())throw new DofFailure(400,"保留当前余额不能携带充值数额");return 0;
        }
        if(e.cera==null||!e.cera.isIntegralNumber()||!e.cera.canConvertToLong())throw new DofFailure(400,"点券数额必须为整数");
        long n=e.cera.longValue();
        if(("credit".equals(e.operation)&&(n<1||n>MAX_DELTA))||("compensate".equals(e.operation)&&(n> -1||n< -MAX_DELTA)))
            throw new DofFailure(400,"充值须为 1～9999999 点券；补偿扣回须为对应的负数");
        return n;
    }
    private long validate(long uid,Edit e){
        if(uid(e)!=uid)throw new DofFailure(400,"请求账号与预览目标不同");
        ManagedOperationJournal.id(e.requestId);
        if(e.revision==null||!e.revision.matches("[0-9a-f]{64}"))throw new DofFailure(400,"缺少余额版本，请重新读取");
        if(!Arrays.asList("credit","compensate","accept-current").contains(e.operation))throw new DofFailure(400,"未知点券管理操作");
        if(e.reason==null||e.reason.trim().isEmpty()||e.reason.length()>100||e.reason.codePoints().anyMatch(x->x<32||x>65535||Character.isSurrogate((char)x)))
            throw new DofFailure(400,"请填写 1～100 字操作原因");
        if("credit".equals(e.operation)){if(e.targetRequestId!=null)throw new DofFailure(400,"普通充值不接受历史目标");}
        else ManagedOperationJournal.id(e.targetRequestId);
        return amount(e);
    }
    private DofFailure incompatible(){return new DofFailure(409,"余额表、账号表、触发器或管理日志与已知结构不兼容；停止写入，不更改原表结构");}
    private DofFailure unavailable(){return new DofFailure(503,"点券操作结果尚未确认，请保留原操作编号，核对历史；不要换编号重新充值");}
    private void triggerVisibility(Connection c)throws SQLException{
        String identity;try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT CURRENT_USER()")){if(!r.next())throw incompatible();identity=r.getString(1);}
        if(identity==null||identity.lastIndexOf('@')<1)throw incompatible();int at=identity.lastIndexOf('@');
        String grantee="'"+identity.substring(0,at).replace("'","''")+"'@'"+identity.substring(at+1).replace("'","''")+"'";
        for(String[] table:new String[][]{{"taiwan_billing","cash_cera"},{"dnf_service","DOF_OPERATION_JOURNAL"}}){
            String sql="SELECT (EXISTS(SELECT 1 FROM information_schema.USER_PRIVILEGES WHERE GRANTEE=? AND PRIVILEGE_TYPE='TRIGGER') OR "
                +"EXISTS(SELECT 1 FROM information_schema.SCHEMA_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA IN (?,?) AND PRIVILEGE_TYPE='TRIGGER') OR "
                +"EXISTS(SELECT 1 FROM information_schema.TABLE_PRIVILEGES WHERE GRANTEE=? AND TABLE_SCHEMA=? AND TABLE_NAME=? AND PRIVILEGE_TYPE='TRIGGER'))";
            try(PreparedStatement p=c.prepareStatement(sql)){
                p.setString(1,grantee);p.setString(2,grantee);p.setString(3,table[0]);p.setString(4,table[0].replace("_","\\_"));p.setString(5,grantee);p.setString(6,table[0]);p.setString(7,table[1]);
                try(ResultSet r=p.executeQuery()){if(!r.next()||!r.getBoolean(1))throw new DofFailure(409,"绑定数据库账号无权确认余额或操作日志的触发器；停止写入，不将不可见当作不存在");}
            }
        }
    }
    Map<String,Object> schema(Connection c)throws SQLException{
        triggerVisibility(c);
        Map<String,Object> descriptor=new TreeMap<>();String engine=null;
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='taiwan_billing' AND TABLE_NAME='cash_cera'")){
            if(!r.next()||!Arrays.asList("InnoDB","MyISAM").contains(engine=r.getString(1))||r.next())throw incompatible();
        }
        Map<String,String> expected=new HashMap<>();expected.put("account","varchar");expected.put("cera","int");expected.put("cera_cold","int");expected.put("mod_tran","bigint");expected.put("mod_date","datetime");expected.put("reg_date","datetime");
        Map<String,Object> columns=new TreeMap<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COLUMN_NAME,DATA_TYPE,COLUMN_TYPE,IS_NULLABLE,CHARACTER_MAXIMUM_LENGTH,COLUMN_KEY,CHARACTER_SET_NAME,EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='taiwan_billing' AND TABLE_NAME='cash_cera'")){
            while(r.next()){
                String k=r.getString(1);if(!Objects.equals(expected.get(k),r.getString(2))||!"NO".equals(r.getString(4))||!"".equals(r.getString(8)))throw incompatible();
                if(k.equals("account")){if(r.getInt(5)!=30||!"PRI".equals(r.getString(6))||!"utf8".equals(r.getString(7)))throw incompatible();}
                else if(Arrays.asList("cera","cera_cold","mod_tran").contains(k)&&!r.getString(3).toLowerCase(Locale.ROOT).contains("unsigned"))throw incompatible();
                columns.put(k,Arrays.asList(r.getString(2),r.getString(3),r.getString(4),r.getString(7),r.getString(8)));
            }
        }
        if(!columns.keySet().equals(expected.keySet()))throw incompatible();descriptor.put("columns",columns);
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT INDEX_NAME,COLUMN_NAME,SEQ_IN_INDEX,SUB_PART FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='taiwan_billing' AND TABLE_NAME='cash_cera' AND NON_UNIQUE=0")){
            if(!r.next()||!"PRIMARY".equals(r.getString(1))||!"account".equals(r.getString(2))||r.getInt(3)!=1||r.getObject(4)!=null||r.next())throw incompatible();
        }
        String trigger="none";
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT ACTION_TIMING,EVENT_MANIPULATION,ACTION_STATEMENT FROM information_schema.TRIGGERS WHERE EVENT_OBJECT_SCHEMA='taiwan_billing' AND EVENT_OBJECT_TABLE='cash_cera'")){
            if(r.next()){
                String body=r.getString(3).replaceAll("[\\s`]","").toLowerCase(Locale.ROOT).replaceFirst(";$","");
                if(!"BEFORE".equals(r.getString(1))||!"UPDATE".equals(r.getString(2))||!body.equals("if(new.cera>=2000000000ornew.cera<=0)thensetnew.cera=0;endif")||r.next())throw incompatible();
                trigger="seed-cera-boundary-v1";
            }
        }
        descriptor.put("trigger",trigger);descriptor.put("engine",engine);
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE EVENT_OBJECT_SCHEMA='dnf_service' AND EVENT_OBJECT_TABLE='DOF_OPERATION_JOURNAL'")){if(!r.next()||r.getInt(1)!=0)throw incompatible();}
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_NAME IS NOT NULL AND ((TABLE_SCHEMA='taiwan_billing' AND TABLE_NAME='cash_cera') OR (TABLE_SCHEMA='dnf_service' AND TABLE_NAME='DOF_OPERATION_JOURNAL') OR (REFERENCED_TABLE_SCHEMA='taiwan_billing' AND REFERENCED_TABLE_NAME='cash_cera') OR (REFERENCED_TABLE_SCHEMA='dnf_service' AND REFERENCED_TABLE_NAME='DOF_OPERATION_JOURNAL'))")){if(!r.next()||r.getInt(1)!=0)throw incompatible();}
        Map<String,String> accounts=new HashMap<>();boolean primary=false;
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COLUMN_NAME,DATA_TYPE,COLUMN_KEY FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='d_taiwan' AND TABLE_NAME='accounts' AND COLUMN_NAME IN ('UID','accountname','admin')")){
            while(r.next()){accounts.put(r.getString(1),r.getString(2));if("UID".equals(r.getString(1))&&"PRI".equals(r.getString(3)))primary=true;}
        }
        if(!primary||!"int".equals(accounts.get("UID"))||!"int".equals(accounts.get("admin"))||!"varchar".equals(accounts.get("accountname")))throw incompatible();
        return descriptor;
    }
    private void lock(Connection c)throws SQLException{
        try{journal.lock(c,WALLET+" WRITE,"+ACCOUNTS+" READ");schema(c);}
        catch(SQLException|RuntimeException failure){try{unlockSafely(c);}catch(SQLException|RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    private void unlockSafely(Connection c)throws SQLException{
        try{journal.unlock(c);}catch(SQLException|RuntimeException failure){discardPhysical(c,failure);throw failure;}
    }
    private void actor(Connection c,long actor)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT admin FROM "+ACCOUNTS+" WHERE UID=?")){p.setLong(1,actor);try(ResultSet r=p.executeQuery()){
            if(!r.next()||r.getInt(1)==0||r.wasNull()||r.next())throw new DofFailure(403,"管理员权限已改变，停止点券操作");
        }}
    }
    Map<String,Object> read(Connection c,long uid)throws SQLException{
        Map<String,Object> descriptor=schema(c),state=new LinkedHashMap<>();state.put("protocol",PROTOCOL);state.put("uid",uid);state.put("engine",descriptor.get("engine"));state.put("schemaRevision",journal.hash(descriptor));
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT @@server_uuid")){if(!r.next()||r.getString(1)==null)throw incompatible();state.put("databaseUuid",r.getString(1));}
        try(PreparedStatement p=c.prepareStatement("SELECT accountname FROM "+ACCOUNTS+" WHERE UID=?")){p.setLong(1,uid);try(ResultSet r=p.executeQuery()){
            if(!r.next())throw new DofFailure(404,"目标账号不存在");String name=r.getString(1);if(name==null||r.next())throw incompatible();state.put("accountIdentity",journal.hash(Collections.singletonMap("accountname",name)));
        }}
        try(PreparedStatement p=c.prepareStatement("SELECT account,cera FROM "+WALLET+" WHERE account=?")){p.setString(1,Long.toString(uid));try(ResultSet r=p.executeQuery()){
            if(!r.next())throw new DofFailure(409,"账号余额记录缺失，未自动初始化");
            if(!Long.toString(uid).equals(r.getString(1)))throw incompatible();long balance=r.getLong(2);if(r.wasNull()||balance<0||balance>4294967295L||r.next())throw incompatible();state.put("balance",balance);
        }}return state;
    }
    private Map<String,Object> snapshot(long uid,String text){
        try{
            Map<String,Object> state=json.readValue(text,new TypeReference<Map<String,Object>>(){});
            if(!state.keySet().equals(new HashSet<>(Arrays.asList("protocol","uid","engine","schemaRevision","accountIdentity","balance","databaseUuid")))||!PROTOCOL.equals(state.get("protocol"))
                ||!whole(state.get("uid"))||((Number)state.get("uid")).longValue()!=uid||!whole(state.get("balance"))
                ||((Number)state.get("balance")).longValue()<0||((Number)state.get("balance")).longValue()>4294967295L
                ||!Arrays.asList("InnoDB","MyISAM").contains(state.get("engine"))
                ||!String.valueOf(state.get("databaseUuid")).matches("[0-9a-fA-F-]{36}")
                ||!String.valueOf(state.get("accountIdentity")).matches("[0-9a-f]{64}")||!String.valueOf(state.get("schemaRevision")).matches("[0-9a-f]{64}"))throw incompatible();
            return state;
        }catch(java.io.IOException e){throw new DofFailure(409,"点券历史快照损坏，停止核对");}
    }
    private boolean whole(Object value){return value instanceof Integer||value instanceof Long;}
    private long balance(Map<String,Object> state){return ((Number)state.get("balance")).longValue();}
    Map<String,Object> creditPlan(Map<String,Object> before,long delta){
        if(delta<1||delta>MAX_DELTA||balance(before)>LIMIT-delta)throw new DofFailure(409,"卡密面值无效或余额将触发原生点券上限，未兑换");
        Map<String,Object> after=new LinkedHashMap<>(before);after.put("balance",balance(before)+delta);return after;
    }
    /** Shared typed native writer; caller owns locks, durable intent and transaction. */
    void applyBalance(Connection c,long uid,Map<String,Object> before,Map<String,Object> after)throws SQLException{
        if(c.getAutoCommit())throw new DofFailure(409,"点券写入须与业务回执在同一事务执行");
        try(PreparedStatement p=c.prepareStatement("UPDATE "+WALLET+" SET cera=? WHERE BINARY account=? AND cera=?")){
            p.setLong(1,balance(after));p.setString(2,Long.toString(uid));p.setLong(3,balance(before));if(p.executeUpdate()!=1)throw new SQLException("Wallet update count mismatch");
        }
        if(!journal.hash(read(c,uid)).equals(journal.hash(after)))throw new SQLException("Wallet readback mismatch");
    }
    private ManagedOperationJournal.Entry source(Connection c,long uid,String id)throws SQLException{
        ManagedOperationJournal.Entry e=journal.require(c,namespace(uid),id);
        if(!resource(uid).equals(e.resource)||!Long.toString(uid).equals(e.target)||!Arrays.asList("credit","compensate","accept-current").contains(e.operation))throw incompatible();
        Map<String,Object> a=snapshot(uid,e.beforeJson),b=snapshot(uid,e.afterJson),copy=new LinkedHashMap<>(a);copy.put("balance",balance(b));
        if(!journal.hash(copy).equals(journal.hash(b)))throw incompatible();return e;
    }
    private String fingerprint(long uid,Edit e){
        Map<String,Object> data=new LinkedHashMap<>();data.put("uid",uid);data.put("revision",e.revision);data.put("operation",e.operation);data.put("delta",amount(e));data.put("reason",e.reason);data.put("targetRequest",e.targetRequestId==null?null:ManagedOperationJournal.id(e.targetRequestId));return journal.hash(data);
    }
    private void noCompensation(Connection c,long uid,String original)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT request_id FROM "+ManagedOperationJournal.TABLE+" WHERE instance_id=? AND namespace=? AND operation_name='compensate' AND target_request=? AND state IN ('PREPARED','PERSISTED','CONFLICT','ACKNOWLEDGED') LIMIT 1")){
            p.setString(1,journal.instance());p.setString(2,namespace(uid));p.setString(3,ManagedOperationJournal.id(original));try(ResultSet r=p.executeQuery()){if(r.next())throw new DofFailure(409,"原充值已有扣回或结果待核对的补偿，不能再次扣款");}
        }
    }
    private Map<String,Object> cdkObject(Object value){if(!(value instanceof Map))throw incompatible();return (Map<String,Object>)value;}
    /** Read-only adapter for the pinned CDK receipt; all debit execution stays here. */
    private Map<String,Object>[] originalCredit(Connection c,long uid,String id)throws SQLException{
        ManagedOperationJournal.Entry e=journal.find(c,id);if(e==null)throw new DofFailure(404,"未找到原充值或CDK兑换回执");
        if(!e.state.equals("PERSISTED"))throw new DofFailure(409,"只能对已确认写入的点券奖励执行补偿，未知结果不能扣回");
        Map<String,Object> a,b;
        if(e.namespace.equals(namespace(uid))){e=source(c,uid,id);if(!e.operation.equals("credit"))throw incompatible();a=snapshot(uid,e.beforeJson);b=snapshot(uid,e.afterJson);}
        else if(e.namespace.equals("cdk.use")){
            try{
                Map<String,Object> before=json.readValue(e.beforeJson,new TypeReference<Map<String,Object>>(){}),outcome=json.readValue(e.afterJson,new TypeReference<Map<String,Object>>(){}),key=cdkObject(before.get("key"));
                if(!"cdk-use-v1".equals(before.get("protocol"))||!whole(before.get("uid"))||((Number)before.get("uid")).longValue()!=uid||!whole(before.get("type"))||((Number)before.get("type")).longValue()!=0||!whole(key.get("id"))||!e.target.equals(key.get("id").toString())||!e.operation.equals("redeem")||!resource(uid).equals(e.resource)||e.actor!=uid||e.targetRequest!=null)throw incompatible();
                a=snapshot(uid,journal.encode(before.get("nativeBefore")));b=snapshot(uid,journal.encode(before.get("plan")));
                if(!journal.hash(b).equals(journal.hash(outcome.get("plan")))||!whole(key.get("face"))||balance(b)-balance(a)!=((Number)key.get("face")).longValue())throw incompatible();
                Map<String,Object> used=cdkObject(outcome.get("usedKey"));if(!Objects.equals(used.get("codeHash"),key.get("codeHash"))||!whole(used.get("status"))||((Number)used.get("status")).longValue()!=1||!whole(used.get("useUid"))||((Number)used.get("useUid")).longValue()!=uid)throw incompatible();
            }catch(java.io.IOException failure){throw incompatible();}
        }else throw new DofFailure(409,"原回执不是该账号的已验证点券充值或CDK奖励");
        Map<String,Object> expected=new LinkedHashMap<>(a);expected.put("balance",balance(b));if(!journal.hash(expected).equals(journal.hash(b)))throw incompatible();
        return new Map[]{a,b};
    }
    private Map<String,Object> plan(Connection c,long uid,Edit e,Map<String,Object> before)throws SQLException{
        if(!journal.hash(before).equals(e.revision))throw new DofFailure(409,"余额、账号身份或结构已改变，请重新读取并预览");
        if(e.operation.equals("accept-current")){
            if(!source(c,uid,e.targetRequestId).state.equals("CONFLICT"))throw new DofFailure(409,"只能显式保留结果不明操作的当前余额");return before;
        }
        if(e.operation.equals("compensate")){
            Map<String,Object>[] original=originalCredit(c,uid,e.targetRequestId);
            long credited=balance(original[1])-balance(original[0]);
            if(credited<1||credited>MAX_DELTA||amount(e)!=-credited)throw new DofFailure(409,"补偿金额必须对应原充值，不恢复整份旧余额");
            if(!Objects.equals(before.get("accountIdentity"),original[1].get("accountIdentity")))throw new DofFailure(409,"原充值账号身份已改变，停止补偿");
            if(!Objects.equals(before.get("databaseUuid"),original[1].get("databaseUuid")))throw new DofFailure(409,"原充值属于其他数据库，停止补偿");
            noCompensation(c,uid,e.targetRequestId);
        }
        long after=balance(before)+amount(e);
        if(after<0||after>LIMIT)throw new DofFailure(409,"余额不足或将触发原生点券上限；未写入");
        Map<String,Object> out=new LinkedHashMap<>(before);out.put("balance",after);return out;
    }
    private Map<String,Object> result(long uid,ManagedOperationJournal.Entry e){
        Map<String,Object> out=journal.result(e),before=snapshot(uid,e.beforeJson),after=snapshot(uid,e.afterJson);
        out.put("instanceId",journal.instance());out.put("revision",e.beforeHash);
        out.put("databaseUuid",before.get("databaseUuid"));
        out.put("uid",uid);out.put("beforeCera",balance(before));out.put("afterCera",balance(after));out.put("delta",balance(after)-balance(before));out.put("engine",before.get("engine"));
        out.put("atomicReceipt",before.get("engine").equals("InnoDB"));out.put("balanceEqualityIsProof",false);
        out.put("note",e.state.equals("ACKNOWLEDGED")?"已明确保留当前余额；原发放结果仍未知，不代表已到账或已撤销":e.state.equals("CONFLICT")?"非事务余额操作结果不明，余额相等不能证明是否执行；先人工核对，再明确保留当前值":"持久化结果与玩家消费、到账表现分别核验");return out;
    }
    public Object status(long uid){
        long who=journal.admin();target(uid);try(Connection c=journal.connection()){lock(c);try{
            actor(c,who);Map<String,Object> state=read(c,uid),out=new LinkedHashMap<>();out.put("instanceId",journal.instance());out.put("databaseUuid",state.get("databaseUuid"));out.put("actor",who);out.put("uid",uid);out.put("cera",balance(state));out.put("revision",journal.hash(state));out.put("engine",state.get("engine"));out.put("effectState","not_observed");out.put("atomicReceipt",state.get("engine").equals("InnoDB"));return out;
        }finally{unlockSafely(c);}}catch(SQLException e){throw unavailable();}
    }
    public Object preview(long uid,Edit edit){
        long who=journal.admin();target(uid);validate(uid,edit);
        try(Connection c=journal.connection()){lock(c);try{
            actor(c,who);ManagedOperationJournal.Entry old=journal.find(c,edit.requestId);
            if(old!=null){journal.same(old,namespace(uid),who,fingerprint(uid,edit));return result(uid,source(c,uid,edit.requestId));}
            journal.noPending(c,resource(uid),edit.operation.equals("accept-current")?ManagedOperationJournal.id(edit.targetRequestId):null);
            Map<String,Object> before=read(c,uid),after=plan(c,uid,edit,before);
            return result(uid,journal.preview(c,edit.requestId,who,namespace(uid),resource(uid),Long.toString(uid),edit.operation,edit.targetRequestId,fingerprint(uid,edit),edit.reason,before,after));
        }finally{unlockSafely(c);}}catch(SQLException e){throw unavailable();}
    }
    public Object save(Edit edit){
        long who=journal.admin(),uid=uid(edit);validate(uid,edit);
        try(Connection c=journal.connection()){boolean discarded=false;lock(c);try{
            actor(c,who);ManagedOperationJournal.Entry e=source(c,uid,edit.requestId);journal.same(e,namespace(uid),who,fingerprint(uid,edit));
            if(!e.state.equals("PREVIEWED"))return result(uid,e);
            journal.noPending(c,resource(uid),edit.operation.equals("accept-current")?ManagedOperationJournal.id(edit.targetRequestId):null);
            Map<String,Object> before=read(c,uid),after=plan(c,uid,edit,before);
            if(!journal.hash(before).equals(e.beforeHash)||!journal.hash(after).equals(e.afterHash))throw new DofFailure(409,"余额预览已失效；没有提交新的数额");
            e=journal.prepare(c,e,Long.toString(uid),before,after); // Durable before starting native transaction.
            if(edit.operation.equals("accept-current"))return result(uid,journal.acknowledge(c,source(c,uid,edit.targetRequestId),e,who));
            // Reacquire table locks with autocommit disabled. LOCK TABLES can commit;
            // no native change precedes it. Never START TRANSACTION or unlock before rollback/commit.
            c.setAutoCommit(false);lock(c);
            try{
                e=source(c,uid,edit.requestId);if(!e.state.equals("PREPARED")){c.rollback();return result(uid,e);}
                actor(c,who);Map<String,Object> current=read(c,uid);
                if(!journal.hash(current).equals(e.beforeHash)){
                    e=journal.finish(c,e,"NOT_APPLIED",who);c.commit();return result(uid,e);
                }
                applyBalance(c,uid,before,after);
                e=journal.finish(c,e,"PERSISTED",who);c.commit();return result(uid,e);
            }catch(SQLException|RuntimeException failure){
                try{c.rollback();}catch(SQLException rollbackFailure){
                    failure.addSuppressed(rollbackFailure);discarded=true;discardPhysical(c,failure);
                }
                // After durable preparation, even a runtime/schema refusal may
                // follow a nontransactional write. Report uncertainty explicitly.
                throw unavailable();
            }
        }finally{
            // Never enable autocommit after an unsuccessful rollback. A broken
            // connection is closed instead of risking an implicit commit.
            if(!discarded&&!c.isClosed()){unlockSafely(c);if(!c.getAutoCommit())c.setAutoCommit(true);}
        }}catch(SQLException e){throw unavailable();}
    }
    private void discardPhysical(Connection c,Throwable original){
        // Hikari's Connection.close() returns a proxy to the pool. Its verified
        // unwrap(Connection.class) exposes the actual driver connection instead.
        try{Connection physical=c.unwrap(Connection.class);if(physical==null)throw new SQLException("Missing physical connection");physical.close();}
        catch(SQLException|RuntimeException closeFailure){
            original.addSuppressed(closeFailure);
            try{c.abort(Runnable::run);}catch(SQLException|RuntimeException abortFailure){original.addSuppressed(abortFailure);}
        }
    }
    public Object reconcile(long uid,String requestId){
        long who=journal.admin();target(uid);
        try(Connection c=journal.connection()){lock(c);try{
            actor(c,who);ManagedOperationJournal.Entry e=source(c,uid,requestId);
            if(!e.state.equals("PREPARED"))return result(uid,e);
            Map<String,Object> before=snapshot(uid,e.beforeJson),current=read(c,uid);
            if(!Objects.equals(before.get("schemaRevision"),current.get("schemaRevision"))||!Objects.equals(before.get("databaseUuid"),current.get("databaseUuid")))throw new DofFailure(409,"余额结构或数据库身份已改变，不能自动推断旧操作结果");
            // InnoDB wallet+PERSISTED receipt commit together on one connection.
            // A still-PREPARED receipt under the same locks proves no such commit.
            // For MyISAM neither before nor after balance proves execution.
            return result(uid,journal.finish(c,e,before.get("engine").equals("InnoDB")?"NOT_APPLIED":"CONFLICT",who));
        }finally{unlockSafely(c);}}catch(SQLException e){throw unavailable();}
    }
    public Object history(long uid){
        journal.admin();target(uid);try(Connection c=journal.connection()){
            List<Object> rows=new ArrayList<>();for(ManagedOperationJournal.Entry e:journal.history(c,namespace(uid)))rows.add(result(uid,source(c,uid,e.requestId)));return rows;
        }catch(SQLException e){throw unavailable();}
    }
}
