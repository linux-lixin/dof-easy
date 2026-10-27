package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.sql.*;
import java.util.*;

/** Typed administration of existing operational registration policy/codes. */
@Service
public class ManagedRegistrationAdmin {
    @Resource private ManagedOperationJournal journal;
    @Resource private RegistrationProof proof;
    @Resource private com.aiyi.game.dnfserver.service.extension.NativeAccountRegistration nativeRegistration;
    private final ObjectMapper json=new ObjectMapper();
    private static final String POLICY="`dnf_service`.`REGISTRATION_POLICY`",CODES="`dnf_service`.`REGISTRATION_CODES`",RECEIPTS="`dnf_service`.`DOF_REGISTRATION_RECEIPT`",NS="registration-admin";
    public static class Edit {public String requestId,revision,operation,targetRequestId,reason;public Boolean enabled;public Integer days;public Long codeId;}
    private String resource(String op){return op.equals("policy")?"registration.policy":op.equals("check-receipt")?"registration.receipts":"registration.codes";}
    private String table(String op){return op.equals("policy")?POLICY:op.equals("check-receipt")?RECEIPTS:CODES;}
    private DofFailure unavailable(){return new DofFailure(503,"注册管理操作结果未确认，请保留操作编号核对；没有自动重复生成、扣码或写入玩家数据");}
    private String action(Edit e){
        if(e==null||e.revision==null||!e.revision.matches("[0-9a-f]{64}"))throw new DofFailure(400,"缺少注册管理版本");e.requestId=ManagedOperationJournal.id(e.requestId);
        if(!Arrays.asList("policy","create","revoke","check-receipt","accept-current").contains(e.operation))throw new DofFailure(400,"未知注册管理动作");
        if(e.reason==null||e.reason.trim().isEmpty()||e.reason.length()>128||e.reason.codePoints().anyMatch(x->x<32||x>65535||Character.isSurrogate((char)x)))throw new DofFailure(400,"请填写 1–128 字操作原因");
        if(e.operation.equals("policy")){if(e.enabled==null||e.days!=null||e.codeId!=null||e.targetRequestId!=null)throw new DofFailure(400,"注册策略参数不匹配");}
        else if(e.operation.equals("create")){if(e.days==null||e.days<1||e.days>365||e.enabled!=null||e.codeId!=null||e.targetRequestId!=null)throw new DofFailure(400,"激活码有效期须为 1–365 天");}
        else if(e.operation.equals("revoke")){if(e.codeId==null||e.codeId<1||e.enabled!=null||e.days!=null||e.targetRequestId!=null)throw new DofFailure(400,"撤销目标无效");}
        else{e.targetRequestId=ManagedOperationJournal.id(e.targetRequestId);if(e.enabled!=null||e.days!=null||e.codeId!=null)throw new DofFailure(400,"注册核对不接受业务补写参数");}return e.operation;
    }
    private String fingerprint(Edit e){Map<String,Object> m=new LinkedHashMap<>();m.put("operation",e.operation);m.put("revision",e.revision);m.put("enabled",e.enabled);m.put("days",e.days);m.put("codeId",e.codeId);m.put("target",e.targetRequestId==null?null:ManagedOperationJournal.id(e.targetRequestId));m.put("reason",e.reason);return journal.hash(m);}
    private Map<String,Object> state(String kind,String target,Object value,String keyId){Map<String,Object> m=new LinkedHashMap<>();m.put("kind",kind);m.put("target",target);m.put("value",value);m.put("keyId",keyId);return m;}
    private Map<String,Object> policy(Connection c)throws SQLException {
        Map<String,Object> value=null;try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT id,enabled,revision FROM "+POLICY+" LIMIT 2")){
            if(r.next()){if(r.getInt(1)!=1||!Arrays.asList(0,1).contains(r.getInt(2))||r.getLong(3)<0)throw RegistrationSchema.bad();value=new LinkedHashMap<>();value.put("enabled",r.getInt(2)==1);value.put("revision",r.getLong(3));if(r.next())throw RegistrationSchema.bad();}
        }return state("policy","1",value,null);
    }
    private long nextId(Connection c)throws SQLException {try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA='dnf_service' AND TABLE_NAME='REGISTRATION_CODES'")){if(!r.next()||r.getString(1)==null||r.getLong(1)<1)throw RegistrationSchema.bad();return r.getLong(1);}}
    private String generationRevision(Connection c,byte[] key)throws SQLException {return journal.hash(Arrays.asList(nextId(c),proof.keyId(key)));}
    private Map<String,Object> code(Connection c,long id,byte[] key)throws SQLException {
        Map<String,Object> value=null;try(PreparedStatement p=c.prepareStatement("SELECT id,request_id,code_hash,state,expires_at,created_at,actor,used_uid,used_at FROM "+CODES+" WHERE id=?")){
            p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(r.next()){value=new LinkedHashMap<>();for(String k:Arrays.asList("id","expires_at","created_at","actor","used_uid","used_at"))value.put(k,r.getObject(k));value.put("requestId",r.getString("request_id"));value.put("state",r.getString("state"));if(!Arrays.asList("ready","used","revoked").contains(r.getString("state")))throw RegistrationSchema.bad();String hash=r.getString("code_hash");if(hash==null||!hash.matches("[0-9a-f]{64}"))throw RegistrationSchema.bad();value.put("codeTag",proof.tag(key,"stored-code",hash));if(r.next())throw RegistrationSchema.bad();}}
        }return state("code",Long.toString(id),value,proof.keyId(key));
    }
    private Map<String,Object> receipt(Connection c,String id)throws SQLException {
        Map<String,Object> value=null;try(PreparedStatement p=c.prepareStatement("SELECT request_id,state,uid,gift_amount,gift_currency,parent_request FROM "+RECEIPTS+" WHERE instance_id=? AND request_id=?")){
            p.setString(1,journal.instance());p.setString(2,ManagedOperationJournal.id(id));try(ResultSet r=p.executeQuery()){if(r.next()){value=new LinkedHashMap<>();for(String k:Arrays.asList("request_id","state","uid","gift_amount","gift_currency","parent_request"))value.put(k,r.getObject(k));if(r.next())throw RegistrationSchema.bad();}}
        }return state("receipt",ManagedOperationJournal.id(id),value,null);
    }
    @SuppressWarnings("unchecked") private Map<String,Object> value(Map<String,Object> state){return (Map<String,Object>)state.get("value");}
    private Map<String,Object> snapshot(String text){try{Map<String,Object> m=json.readValue(text,new TypeReference<Map<String,Object>>(){});if(m==null||!m.keySet().equals(new HashSet<>(Arrays.asList("kind","target","value","keyId")))||!(m.get("target") instanceof String)||!Arrays.asList("code","policy","receipt").contains(m.get("kind"))||m.get("value")!=null&&!(m.get("value") instanceof Map))throw new Exception();return m;}catch(Exception e){throw new DofFailure(409,"注册管理快照损坏，停止恢复或重新生成");}}
    private Map<String,Object> current(Connection c,ManagedOperationJournal.Entry e,byte[] key)throws SQLException {
        Map<String,Object> after=snapshot(e.afterJson),before=snapshot(e.beforeJson);String op=e.operation;
        if(op.equals("accept-current")){ManagedOperationJournal.Entry old=source(c,e.targetRequest);if(!e.resource.equals(old.resource)||!e.target.equals(old.target)||!before.equals(after))throw new DofFailure(409,"冲突确认快照不匹配");return current(c,old,key);}
        if(!Arrays.asList("policy","create","revoke","check-receipt").contains(op))throw new DofFailure(409,"注册管理日志动作不匹配");
        String kind=op.equals("policy")?"policy":op.equals("check-receipt")?"receipt":"code";
        if(!kind.equals(before.get("kind"))||!kind.equals(after.get("kind")))throw new DofFailure(409,"注册管理快照类型不匹配");
        if(!NS.equals(e.namespace)||!resource(op).equals(e.resource)||!Objects.equals(before.get("kind"),after.get("kind"))||!e.target.equals(after.get("target"))||!before.get("target").equals(e.target))throw new DofFailure(409,"注册管理操作来源不匹配");
        if(op.equals("policy"))return policy(c);if(op.equals("check-receipt"))return receipt(c,e.target);
        if(!proof.keyId(key).equals(after.get("keyId"))||!proof.keyId(key).equals(before.get("keyId")))throw new DofFailure(409,"激活码核对材料变化，停止判断或重新生成；请恢复原材料");return code(c,Long.parseLong(e.target),key);
    }
    private void lock(Connection c,String op)throws SQLException {RegistrationSchema.check(c);journal.lock(c,table(op)+" WRITE");RegistrationSchema.check(c);}
    private Map<String,Object> result(ManagedOperationJournal.Entry e,boolean reveal,byte[] key){
        Map<String,Object> out=journal.result(e),after=snapshot(e.afterJson);Map<String,Object> v=value(after);out.put("target",e.target);
        if(v!=null){Map<String,Object> safe=new LinkedHashMap<>(v);safe.remove("codeTag");out.put("values",safe);}
        if(reveal&&e.operation.equals("create")&&e.state.equals("PERSISTED")){
            if(!proof.keyId(key).equals(after.get("keyId")))throw new DofFailure(409,"激活码取回材料变化，请恢复原材料；没有创建新码");
            int days=(int)((((Number)v.get("expires_at")).longValue()-((Number)v.get("created_at")).longValue())/86400000L);
            out.put("id",v.get("id"));out.put("expiresAt",v.get("expires_at"));out.put("code",proof.code(key,e.requestId,e.actor,days,((Number)v.get("expires_at")).longValue()));
        }return out;
    }
    public Object status(){journal.admin();try(Connection c=journal.connection()){RegistrationSchema.check(c);Map<String,Object> p=policy(c),out=new LinkedHashMap<>();Map<String,Object> v=value(p);out.put("enabled",v!=null&&Boolean.TRUE.equals(v.get("enabled")));out.put("initialized",v!=null);out.put("revision",journal.hash(p));out.put("protocol","own-launcher-optional-activationCode");out.put("registrationReadiness",nativeRegistration.readiness(c,journal.instance()));try{out.put("codeRevision",generationRevision(c,proof.key()));out.put("generationAvailable",true);}catch(DofFailure e){out.put("generationAvailable",false);out.put("generationReason",e.getMessage());}return out;}catch(SQLException e){throw unavailable();}}
    public Object codeState(long id){journal.admin();if(id<1)throw new DofFailure(400,"激活码编号无效");byte[] key=proof.key();try(Connection c=journal.connection()){RegistrationSchema.check(c);Map<String,Object> s=code(c,id,key);if(value(s)==null)throw new DofFailure(404,"激活码不存在");Map<String,Object> out=new LinkedHashMap<>();out.put("revision",journal.hash(s));return out;}catch(SQLException e){throw unavailable();}}
    public Object preview(Edit e){long actor=journal.admin();String op=action(e);if(op.equals("accept-current"))return previewAcknowledgment(e,actor);byte[] key=op.equals("create")||op.equals("revoke")?proof.key():null;
        try(Connection c=journal.connection()){lock(c,op);try{
            ManagedOperationJournal.Entry old=journal.find(c,e.requestId);if(old!=null){journal.same(old,NS,actor,fingerprint(e));return result(old,false,key);}journal.noPending(c,resource(op),null);
            Map<String,Object> before,after;String revision;
            if(op.equals("policy")){before=policy(c);revision=journal.hash(before);Map<String,Object> v=value(before);if(Boolean.TRUE.equals(e.enabled))com.aiyi.game.dnfserver.service.extension.NativeAccountRegistration.verifyTransactional(c,false);Map<String,Object> n=new LinkedHashMap<>();n.put("enabled",e.enabled);n.put("revision",v==null?0L:((Number)v.get("revision")).longValue()+(Objects.equals(v.get("enabled"),e.enabled)?0:1));after=state("policy","1",n,null);}
            else if(op.equals("create")){long id=nextId(c),now=System.currentTimeMillis(),expires=now+e.days*86400000L;revision=generationRevision(c,key);before=code(c,id,key);if(value(before)!=null)throw RegistrationSchema.bad();Map<String,Object> v=new LinkedHashMap<>();v.put("id",id);v.put("requestId",e.requestId);v.put("actor",actor);v.put("state","ready");v.put("expires_at",expires);v.put("created_at",now);v.put("used_uid",null);v.put("used_at",null);String raw=proof.code(key,e.requestId,actor,e.days,expires);v.put("codeTag",proof.tag(key,"stored-code",com.aiyi.game.dnfserver.service.extension.RegistrationCodesService.hash(raw)));after=state("code",Long.toString(id),v,proof.keyId(key));}
            else if(op.equals("revoke")){before=code(c,e.codeId,key);revision=journal.hash(before);if(value(before)==null||!"ready".equals(value(before).get("state")))throw new DofFailure(409,"激活码已使用、撤销或不存在");Map<String,Object> v=new LinkedHashMap<>(value(before));v.put("state","revoked");after=state("code",e.codeId.toString(),v,proof.keyId(key));}
            else{before=receipt(c,e.targetRequestId);revision=journal.hash(before);Map<String,Object> v=value(before);if(v==null||!"PREPARED".equals(v.get("state"))||v.get("uid")!=null)throw new DofFailure(409,"注册回执已结束或不符合安全核对条件");Map<String,Object> next=new LinkedHashMap<>(v);next.put("state","NOT_APPLIED");after=state("receipt",e.targetRequestId,next,null);}
            if(!revision.equals(e.revision))throw new DofFailure(409,"注册管理版本改变，请重新读取并预览");
            return result(journal.preview(c,e.requestId,actor,NS,resource(op),after.get("target").toString(),op,e.targetRequestId,fingerprint(e),e.reason,before,after),false,key);
        }finally{journal.unlock(c);}}catch(SQLException x){throw unavailable();}
    }
    public Object save(Edit e,String expected){long actor=journal.admin();String op=action(e);if(!op.equals(expected))throw new DofFailure(400,"HTTP 动作与预览不同");if(op.equals("accept-current"))return saveAcknowledgment(e,actor);byte[] key=op.equals("create")||op.equals("revoke")?proof.key():null;
        try(Connection c=journal.connection()){lock(c,op);try{
            ManagedOperationJournal.Entry entry=journal.require(c,NS,e.requestId);journal.same(entry,NS,actor,fingerprint(e));if(!entry.state.equals("PREVIEWED"))return result(entry,true,key);journal.noPending(c,resource(op),null);
            Map<String,Object> before=current(c,entry,key),after=snapshot(entry.afterJson);if(!journal.hash(before).equals(entry.beforeHash)||op.equals("create")&&!generationRevision(c,key).equals(e.revision))throw new DofFailure(409,"预览中的注册管理状态改变");
            if(op.equals("policy")&&Boolean.TRUE.equals(e.enabled))com.aiyi.game.dnfserver.service.extension.NativeAccountRegistration.verifyTransactional(c,false);
            entry=journal.prepare(c,entry,entry.target,before,after);if(entry.beforeHash.equals(entry.afterHash))return result(journal.finish(c,entry,"NO_CHANGE",actor),false,key);
            Map<String,Object> v=value(after);
            if(op.equals("policy")){if(value(before)==null)try(PreparedStatement p=c.prepareStatement("INSERT INTO "+POLICY+"(id,enabled,revision) VALUES(1,?,?)")){p.setBoolean(1,(Boolean)v.get("enabled"));p.setLong(2,((Number)v.get("revision")).longValue());p.executeUpdate();}else try(PreparedStatement p=c.prepareStatement("UPDATE "+POLICY+" SET enabled=?,revision=? WHERE id=1")){p.setBoolean(1,(Boolean)v.get("enabled"));p.setLong(2,((Number)v.get("revision")).longValue());if(p.executeUpdate()!=1)throw new SQLException("Policy update count");}}
            else if(op.equals("create")){String raw=proof.code(key,entry.requestId,actor,e.days,((Number)v.get("expires_at")).longValue());try(PreparedStatement p=c.prepareStatement("INSERT INTO "+CODES+"(id,request_id,code_hash,state,expires_at,created_at,actor) VALUES(?,?,?,'ready',?,?,?)")){p.setLong(1,Long.parseLong(entry.target));p.setString(2,entry.requestId);p.setString(3,com.aiyi.game.dnfserver.service.extension.RegistrationCodesService.hash(raw));p.setLong(4,((Number)v.get("expires_at")).longValue());p.setLong(5,((Number)v.get("created_at")).longValue());p.setLong(6,actor);p.executeUpdate();}}
            else if(op.equals("revoke"))try(PreparedStatement p=c.prepareStatement("UPDATE "+CODES+" SET state='revoked' WHERE id=? AND state='ready'")){p.setLong(1,Long.parseLong(entry.target));if(p.executeUpdate()!=1)throw new SQLException("Code state changed");}
            else try(PreparedStatement p=c.prepareStatement("UPDATE "+RECEIPTS+" SET state='NOT_APPLIED',finished_at=NOW() WHERE instance_id=? AND request_id=? AND state='PREPARED' AND uid IS NULL")){p.setString(1,journal.instance());p.setString(2,entry.target);if(p.executeUpdate()!=1)throw new SQLException("Registration receipt changed");}
            if(!journal.hash(current(c,entry,key)).equals(entry.afterHash))throw new SQLException("Registration management readback mismatch");return result(journal.finish(c,entry,"PERSISTED",actor),true,key);
        }finally{journal.unlock(c);}}catch(SQLException x){throw unavailable();}
    }
    public Object reconcile(String id){long actor=journal.admin();ManagedOperationJournal.id(id);try(Connection c=journal.connection()){
        ManagedOperationJournal.Entry e=journal.require(c,NS,id);byte[] key=needsKey(e.operation.equals("accept-current")?source(c,e.targetRequest).operation:e.operation)?proof.key():null;lock(c,e.operation.equals("accept-current")?source(c,e.targetRequest).operation:e.operation);try{e=journal.require(c,NS,id);if(!e.state.equals("PREPARED"))return result(e,true,key);String h=journal.hash(current(c,e,key));if(e.operation.equals("accept-current"))return result(h.equals(e.afterHash)?journal.acknowledge(c,source(c,e.targetRequest),e,actor):journal.finish(c,e,"NOT_APPLIED",actor),false,key);String state=h.equals(e.afterHash)?"PERSISTED":h.equals(e.beforeHash)?"NOT_APPLIED":"CONFLICT";return result(journal.finish(c,e,state,actor),true,key);}finally{journal.unlock(c);}
    }catch(SQLException e){throw unavailable();}}
    private boolean needsKey(String op){return op.equals("create")||op.equals("revoke");}
    private ManagedOperationJournal.Entry source(Connection c,String id)throws SQLException {
        ManagedOperationJournal.Entry old=journal.require(c,NS,ManagedOperationJournal.id(id));
        if(!Arrays.asList("policy","create","revoke","check-receipt").contains(old.operation))throw new DofFailure(409,"不能引用非原始管理操作");return old;
    }
    public Object conflictState(String id){journal.admin();try(Connection c=journal.connection()){
        ManagedOperationJournal.Entry old=source(c,id);byte[] key=needsKey(old.operation)?proof.key():null;
        if(!old.state.equals("CONFLICT"))throw new DofFailure(409,"仅冲突记录可确认保留当前状态");
        Map<String,Object> out=new LinkedHashMap<>();out.put("revision",journal.hash(current(c,old,key)));return out;
    }catch(SQLException x){throw unavailable();}}
    private Object previewAcknowledgment(Edit edit,long actor){try(Connection c=journal.connection()){
        ManagedOperationJournal.Entry old=source(c,edit.targetRequestId);byte[] key=needsKey(old.operation)?proof.key():null;
        lock(c,old.operation);try{
            ManagedOperationJournal.Entry existing=journal.find(c,edit.requestId);if(existing!=null){journal.same(existing,NS,actor,fingerprint(edit));return result(existing,false,key);}
            old=source(c,edit.targetRequestId);if(!old.state.equals("CONFLICT"))throw new DofFailure(409,"原操作不再处于冲突状态");journal.noPending(c,old.resource,old.requestId);
            Map<String,Object> before=current(c,old,key);if(!edit.revision.equals(journal.hash(before)))throw new DofFailure(409,"冲突当前值已改变，请重新预览");
            return result(journal.preview(c,edit.requestId,actor,NS,old.resource,old.target,"accept-current",old.requestId,fingerprint(edit),edit.reason,before,before),false,key);
        }finally{journal.unlock(c);}
    }catch(SQLException x){throw unavailable();}}
    private Object saveAcknowledgment(Edit edit,long actor){try(Connection c=journal.connection()){
        ManagedOperationJournal.Entry old=source(c,edit.targetRequestId);byte[] key=needsKey(old.operation)?proof.key():null;lock(c,old.operation);try{
            ManagedOperationJournal.Entry e=journal.require(c,NS,edit.requestId);journal.same(e,NS,actor,fingerprint(edit));if(!e.state.equals("PREVIEWED"))return result(e,false,key);
            old=source(c,edit.targetRequestId);if(!old.state.equals("CONFLICT"))throw new DofFailure(409,"原冲突状态改变");journal.noPending(c,old.resource,old.requestId);
            Map<String,Object> now=current(c,e,key);if(!journal.hash(now).equals(e.beforeHash)||!edit.revision.equals(e.beforeHash))throw new DofFailure(409,"当前值变化，请重新预览；未写入原生数据");
            e=journal.prepare(c,e,e.target,now,now);return result(journal.acknowledge(c,old,e,actor),false,key);
        }finally{journal.unlock(c);}
    }catch(SQLException x){throw unavailable();}}
    public Object operations(){journal.admin();try(Connection c=journal.connection()){List<Object> out=new ArrayList<>();for(ManagedOperationJournal.Entry e:journal.history(c,NS))out.add(result(e,false,null));return out;}catch(SQLException e){throw unavailable();}}
    public Object receiptState(String id){journal.admin();try(Connection c=journal.connection()){RegistrationSchema.check(c);Map<String,Object> m=receipt(c,id),out=new LinkedHashMap<>();if(value(m)==null)throw new DofFailure(404,"注册回执不存在");out.put("revision",journal.hash(m));return out;}catch(SQLException e){throw unavailable();}}
}
