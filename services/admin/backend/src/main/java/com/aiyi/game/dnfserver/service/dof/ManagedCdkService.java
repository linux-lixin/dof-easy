package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.service.extension.PasswordChallenges;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.sql.*;
import java.util.*;

/** One issuer for the existing recharge_key redemption. No native companion-file guesses. */
@Service
public class ManagedCdkService {
    @Resource private DofAdminService access;
    @Resource private ManagedOperationJournal journal;
    @Resource private ManagedRechargeService recharge;
    @Resource private ManagedPostalService postal;
    private final ManagedCdkSchema schema=new ManagedCdkSchema();
    private final PasswordChallenges challenges=new PasswordChallenges();
    private final ObjectMapper json=new ObjectMapper();
    private static final String KEYS="`dnf_service`.`recharge_key`",ACCOUNTS="`d_taiwan`.`accounts`",NS="cdk.use",PROTOCOL="cdk-use-v1",CTRL="cdk.control";
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public static class Claim {
        public String requestId,useAccount;
        @com.alibaba.fastjson.annotation.JSONField(serialize=false) @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) public String content;
        @com.alibaba.fastjson.annotation.JSONField(serialize=false) @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) public String valiId,valicode;
        public com.fasterxml.jackson.databind.JsonNode charach;
    }
    public static class Control {public String requestId,revision,operation,reason;}
    public void requireAdmin(){journal.admin();}
    public void issueChallenge(String id,String value){if(id==null||!id.matches("[a-zA-Z0-9-]{16,64}"))throw new DofFailure(400,"验证码编号无效");challenges.issue(id,value);}
    private DofFailure uncertain(){return new DofFailure(503,"CDK兑换结果尚未确认，请保留原编号并核对，不能换编号再次兑换");}
    private Map<String,Object> map(Object v){if(!(v instanceof Map))throw new DofFailure(409,"CDK回执结构不兼容，停止核对");return (Map<String,Object>)v;}
    private Map<String,Object> decode(String text){try{return json.readValue(text,new TypeReference<Map<String,Object>>(){});}catch(java.io.IOException e){throw new DofFailure(409,"CDK回执损坏，停止核对");}}
    private long n(Map<String,Object> v,String key){Object x=v.get(key);if(!(x instanceof Integer||x instanceof Long))throw new DofFailure(409,"CDK回执数值不完整");return ((Number)x).longValue();}
    private String text(String value,int max,String label){if(value==null||value.trim().isEmpty()||value.length()>max||value.chars().anyMatch(x->x<32||Character.isSurrogate((char)x)))throw new DofFailure(400,label+"格式无效");return value;}
    private String code(String value){text(value,255,"卡密");return value.matches("[0-9a-fA-F-]{36}")?value.toUpperCase(Locale.ROOT):value;}
    private long recipient(Claim request){if(request.charach==null||request.charach.isNull())return 0;if(!request.charach.isIntegralNumber()||!request.charach.canConvertToInt()||request.charach.intValue()<0)throw new DofFailure(400,"收件角色须为非负整数");return request.charach.longValue();}
    private String fingerprint(Claim request){
        if(request==null)throw new DofFailure(400,"缺少兑换请求");ManagedOperationJournal.id(request.requestId);String code=code(request.content);text(request.useAccount,64,"收取账号");
        recipient(request);
        Map<String,Object> v=new TreeMap<>();v.put("codeHash",journal.hash(Collections.singletonMap("code",code)));v.put("account",request.useAccount);v.put("recipient",recipient(request));return journal.hash(v);
    }
    private Connection connection()throws SQLException{
        Connection c=access.boundConnection();try(Statement s=c.createStatement()){s.execute("SET SESSION sql_mode=CONCAT_WS(',',NULLIF(@@SESSION.sql_mode,''),'STRICT_ALL_TABLES')");return c;}catch(SQLException|RuntimeException e){c.close();throw e;}
    }
    private void lock(Connection c,int kind)throws SQLException{
        String locks=KEYS+" WRITE,"+ACCOUNTS+" READ";
        if(kind==0)locks+=",`taiwan_billing`.`cash_cera` WRITE";
        if(kind==1)locks+=",`taiwan_cain`.`charac_info` READ,`taiwan_cain_2nd`.`postal` WRITE,`taiwan_cain_2nd`.`letter` WRITE";
        try{journal.lock(c,locks);schema.inspect(c);}catch(SQLException|RuntimeException e){try{postal.unlock(c);}catch(SQLException|RuntimeException cleanup){e.addSuppressed(cleanup);}throw e;}
    }
    private Map<String,Object> descriptor(Connection c,int kind)throws SQLException{
        Map<String,Object> out=new TreeMap<>();out.put("key",schema.inspect(c));out.put("native",kind==0?recharge.schema(c):postal.descriptor(c));
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT @@server_uuid")){if(!r.next())throw uncertain();out.put("databaseUuid",r.getString(1));}return out;
    }
    private Map<String,Object> key(Connection c,String content,Long id)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT id,content,type,face,status,use_uid,use_account,CAST(create_time AS CHAR),CAST(use_time AS CHAR) FROM "+KEYS+" WHERE "+(id==null?"content=?":"id=?"))){
            if(id==null)p.setString(1,content);else p.setLong(1,id);
            try(ResultSet r=p.executeQuery()){
                if(!r.next())throw new DofFailure(404,"卡密不存在");String stored=r.getString(2);if(stored==null||id==null&&!code(stored).equals(content))throw new DofFailure(404,"卡密不存在");
                Map<String,Object> out=new TreeMap<>();out.put("id",r.getLong(1));out.put("codeHash",journal.hash(Collections.singletonMap("code",code(stored))));out.put("type",r.getInt(3));out.put("face",r.getInt(4));out.put("status",r.getInt(5));out.put("useUid",r.getObject(6));out.put("useAccount",r.getString(7));out.put("createdAt",r.getString(8));out.put("usedAt",r.getString(9));
                if(n(out,"id")<1||out.get("createdAt")==null||!Arrays.asList(0,1).contains(out.get("type"))||n(out,"face")<1||!Arrays.asList(0,1).contains(out.get("status"))||r.next())throw new DofFailure(409,"卡密重复或定义不完整，停止兑换");return out;
            }
        }
    }
    private String resource(Map<String,Object> state){return n(state,"type")==0?"taiwan_billing.cash_cera:"+n(state,"uid"):"taiwan_cain_2nd.postal:"+n(state,"recipient");}
    private ManagedOperationJournal.Entry source(Connection c,String request)throws SQLException{
        ManagedOperationJournal.Entry e=journal.require(c,NS,request);Map<String,Object> before=decode(e.beforeJson);
        if(!PROTOCOL.equals(before.get("protocol"))||!e.operation.equals("redeem")||e.actor!=n(before,"uid")||!e.target.equals(Long.toString(n(map(before.get("key")),"id")))||!e.resource.equals(resource(before))||e.targetRequest!=null)throw new DofFailure(409,"CDK回执归属不匹配");return e;
    }
    private void unclaimed(Connection c,long key,String own)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT request_id FROM "+ManagedOperationJournal.TABLE+" WHERE instance_id=? AND namespace=? AND target_key=? AND state IN ('PREVIEWED','PREPARED','CONFLICT','PERSISTED','ACKNOWLEDGED')")){
            p.setString(1,journal.instance());p.setString(2,NS);p.setString(3,Long.toString(key));try(ResultSet r=p.executeQuery()){while(r.next())if(!r.getString(1).equals(own))throw new DofFailure(409,"该卡密已使用或已有保留、待核对的兑换；请保留原编号，不要重复兑换");}
        }
    }
    private Map<String,Object> snapshot(Connection c,Claim request,String own,int expectedType)throws SQLException{
        Map<String,Object> key=key(c,code(request.content),null);int type=(int)n(key,"type");if(type!=expectedType)throw new DofFailure(409,"卡密类型已改变，请重新核对原编号");
        if(n(key,"status")!=0||key.get("useUid")!=null&&n(key,"useUid")!=0||key.get("useAccount")!=null&&!key.get("useAccount").equals("")||key.get("usedAt")!=null)throw new DofFailure(409,"该卡密已使用或使用状态不一致");
        unclaimed(c,n(key,"id"),own);long uid;String account;
        try(PreparedStatement p=c.prepareStatement("SELECT UID,accountname FROM "+ACCOUNTS+" WHERE BINARY accountname=?")){p.setString(1,request.useAccount);try(ResultSet r=p.executeQuery()){if(!r.next())throw new DofFailure(404,"收取账号不存在");uid=r.getLong(1);account=r.getString(2);if(uid<1||uid>Integer.MAX_VALUE||account==null||r.next())throw new DofFailure(409,"收取账号身份不唯一");}}
        long recipient=recipient(request);if(type==0&&recipient!=0||type==1&&recipient<1)throw new DofFailure(400,"点券码不接受角色编号；物品码须提供所属账号的角色编号");
        Map<String,Object> out=new TreeMap<>();out.put("protocol",PROTOCOL);out.put("initiator","public-code-and-captcha");out.put("key",key);out.put("type",type);out.put("uid",uid);out.put("account",account);out.put("recipient",recipient);out.put("schema",descriptor(c,type));
        Map<String,Object> nativeBefore=type==0?recharge.read(c,uid):postal.before(c,recipient,postal.descriptor(c));
        if(type==1&&n(nativeBefore,"uid")!=uid)throw new DofFailure(403,"收件角色不属于指定账号，未兑换");out.put("nativeBefore",nativeBefore);
        Map<String,Object> item=new TreeMap<>();item.put("itemId",n(key,"face"));item.put("quantity",1);
        Map<String,Object> plan=type==0?recharge.creditPlan(nativeBefore,n(key,"face")):postal.frozenSendPlan(postal.mail(json.valueToTree(item)));
        out.put("plan",plan);out.put("atomicReceipt",type==0?"InnoDB".equals(nativeBefore.get("engine")):Boolean.TRUE.equals(nativeBefore.get("atomicReceipt")));journal.noPending(c,resource(out),own);return out;
    }
    private Map<String,Object> result(ManagedOperationJournal.Entry e,boolean admin){
        Map<String,Object> before=decode(e.beforeJson),out=journal.result(e);out.put("initiator",before.get("initiator"));out.put("keyId",e.target);out.put("uid",before.get("uid"));out.put("recipient",before.get("recipient"));out.put("type",before.get("type"));out.put("face",map(before.get("key")).get("face"));out.put("atomicReceipt",before.get("atomicReceipt"));out.put("actualPlayerReceiptVerified",false);out.put("receiptRevision",journal.hash(ManagedPostalReceiptService.snapshot(e)));
        if(admin){out.put("before",before);out.put("outcome",decode(e.afterJson));}else{Map<String,Object> outcome=decode(e.afterJson);out.put("postalId",outcome.get("postalId"));}
        return out;
    }
    public Object claim(Claim request){return use(request,false);}
    public Object reconcile(Claim request){return use(request,true);}
    private Object use(Claim request,boolean reconcile){
        String fingerprint=fingerprint(request);
        try(Connection c=connection()){boolean discarded=false;lock(c,-1);try{
            ManagedOperationJournal.Entry old=journal.find(c,request.requestId);
            if(old!=null){old=source(c,request.requestId);journal.same(old,NS,old.actor,fingerprint);if(!reconcile&&!old.state.equals("PREVIEWED"))return result(old,false);}
            if(reconcile){if(old==null)throw new DofFailure(404,"没有该兑换回执；不会新建兑换");lock(c,(int)n(decode(old.beforeJson),"type"));return result(reconcile(c,source(c,request.requestId),old.actor),false);}
            if(request.valiId==null||!request.valiId.matches("[a-zA-Z0-9-]{16,64}")||request.valicode==null||request.valicode.length()>16||!challenges.consume(request.valiId,request.valicode))throw new DofFailure(400,"CDK验证码无效或已过期，请在新版兑换网页刷新验证码");
            int type=old==null?(int)n(key(c,code(request.content),null),"type"):(int)n(decode(old.beforeJson),"type");lock(c,type);
            Map<String,Object> before=snapshot(c,request,old==null?null:old.requestId,type);
            if(old!=null&&!journal.hash(before).equals(old.beforeHash)){
                Map<String,Object> outcome=new TreeMap<>();outcome.put("plan",decode(old.afterJson));outcome.put("note","兑换预览已过期，确认尚未提交");return result(journal.rejectUnsubmittedPreview(c,old,outcome,old.actor),false);
            }
            long uid=n(before,"uid");ManagedOperationJournal.Entry e=old==null?journal.preview(c,request.requestId,uid,NS,resource(before),Long.toString(n(map(before.get("key")),"id")),"redeem",null,fingerprint,"玩家持码兑换（非管理员操作）",before,before.get("plan")):old;
            e=journal.prepare(c,e,e.target,before,before.get("plan"));
            c.setAutoCommit(false);lock(c,type);
            try{
                e=source(c,request.requestId);if(!e.state.equals("PREPARED")){c.rollback();return result(e,false);}
                Map<String,Object> current=snapshot(c,request,e.requestId,type);
                if(!journal.hash(current).equals(e.beforeHash)){e=journal.finish(c,e,"NOT_APPLIED",uid);c.commit();return result(e,false);}
                Map<String,Object> outcome=new TreeMap<>();outcome.put("plan",before.get("plan"));
                if(type==0)recharge.applyBalance(c,uid,map(before.get("nativeBefore")),map(before.get("plan")));
                else outcome.putAll(postal.insertMail(c,map(before.get("plan")),n(before,"recipient")));
                try(PreparedStatement p=c.prepareStatement("UPDATE "+KEYS+" SET status=1,use_uid=?,use_account=?,use_time=NOW() WHERE id=? AND status=0")){p.setLong(1,uid);p.setString(2,(String)before.get("account"));p.setLong(3,n(map(before.get("key")),"id"));if(p.executeUpdate()!=1)throw new SQLException("CDK consumption changed");}
                Map<String,Object> used=key(c,null,n(map(before.get("key")),"id")),expected=new TreeMap<>(map(before.get("key")));expected.put("status",1);expected.put("useUid",uid);expected.put("useAccount",before.get("account"));expected.put("usedAt",used.get("usedAt"));if(used.get("usedAt")==null||!journal.hash(used).equals(journal.hash(expected)))throw new SQLException("CDK readback differs");
                outcome.put("usedKey",used);e=journal.finishWithGeneratedOutcome(c,e,outcome,uid);c.commit();return result(e,false);
            }catch(SQLException|RuntimeException failure){try{c.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);discarded=true;postal.discard(c,failure);}throw uncertain();}
        }finally{if(!discarded&&!c.isClosed()){postal.unlock(c);if(!c.getAutoCommit())c.setAutoCommit(true);}}}catch(SQLException e){throw uncertain();}
    }
    private ManagedOperationJournal.Entry reconcile(Connection c,ManagedOperationJournal.Entry e,long actor)throws SQLException{
        if(!e.state.equals("PREPARED"))return e;Map<String,Object> before=decode(e.beforeJson);
        if(!journal.hash(before.get("schema")).equals(journal.hash(descriptor(c,(int)n(before,"type")))))throw new DofFailure(409,"数据库或结构已改变，不能推断旧CDK结果");
        return journal.finish(c,e,Boolean.TRUE.equals(before.get("atomicReceipt"))?"NOT_APPLIED":"CONFLICT",actor);
    }
    public Object history(){long actor=journal.admin();try(Connection c=journal.connection()){postal.actor(c,actor);List<Object> rows=new ArrayList<>();for(ManagedOperationJournal.Entry e:journal.history(c,NS))rows.add(result(source(c,e.requestId),true));return rows;}catch(SQLException e){throw uncertain();}}
    public Object adminRead(String request){long actor=journal.admin();try(Connection c=journal.connection()){journal.schema(c);postal.actor(c,actor);return result(source(c,request),true);}catch(SQLException e){throw uncertain();}}
    public Object adminReconcile(String request){long actor=journal.admin();try(Connection c=journal.connection()){lock(c,-1);try{postal.actor(c,actor);ManagedOperationJournal.Entry e=source(c,request);lock(c,(int)n(decode(e.beforeJson),"type"));postal.actor(c,actor);return result(reconcile(c,source(c,request),actor),true);}finally{postal.unlock(c);}}catch(SQLException e){throw uncertain();}}
    private String controlFingerprint(String source,Control edit){
        ManagedOperationJournal.id(source);if(edit==null)throw new DofFailure(400,"缺少CDK回执控制请求");ManagedOperationJournal.id(edit.requestId);
        if(edit.revision==null||!edit.revision.matches("[0-9a-f]{64}"))throw new DofFailure(400,"缺少原回执版本");
        if(!Arrays.asList("cancel-preview","ack-unknown").contains(edit.operation))throw new DofFailure(400,"未知CDK控制动作");text(edit.reason,100,"操作原因");
        Map<String,Object> v=new TreeMap<>();v.put("source",ManagedOperationJournal.id(source));v.put("revision",edit.revision);v.put("operation",edit.operation);v.put("reason",edit.reason);return journal.hash(v);
    }
    private ManagedOperationJournal.Entry control(Connection c,String source,String request)throws SQLException{
        ManagedOperationJournal.Entry e=journal.require(c,CTRL,request);if(!e.target.equals(ManagedOperationJournal.id(source))||!e.target.equals(e.targetRequest)||!e.resource.equals(CTRL+":"+e.target)||!Arrays.asList("cancel-preview","ack-unknown").contains(e.operation))throw new DofFailure(409,"CDK控制归属不匹配");return e;
    }
    private Map<String,Object> controlResult(ManagedOperationJournal.Entry e){Map<String,Object> out=journal.result(e);out.put("revision",e.beforeHash);out.put("nativeDataChanged",false);return out;}
    private ManagedOperationJournal.Entry eligible(Connection c,String request,Control edit)throws SQLException{
        ManagedOperationJournal.Entry e=source(c,request);String expected=edit.operation.equals("cancel-preview")?"PREVIEWED":"CONFLICT";
        if(!e.state.equals(expected)||!journal.hash(ManagedPostalReceiptService.snapshot(e)).equals(edit.revision))throw new DofFailure(409,"CDK原回执状态或版本已改变，请重新读取");
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT @@server_uuid")){if(!r.next()||!Objects.equals(r.getString(1),map(decode(e.beforeJson).get("schema")).get("databaseUuid")))throw new DofFailure(409,"原CDK回执属于其他数据库，停止控制");}return e;
    }
    public Object controlPreview(String request,Control edit){
        long actor=journal.admin();String fingerprint=controlFingerprint(request,edit);
        try(Connection c=journal.connection()){lock(c,-1);try{
            postal.actor(c,actor);ManagedOperationJournal.Entry old=journal.find(c,edit.requestId);if(old!=null){journal.same(old,CTRL,actor,fingerprint);return controlResult(control(c,request,edit.requestId));}
            ManagedOperationJournal.Entry original=eligible(c,request,edit);Map<String,Object> after=new TreeMap<>();after.put("operation",edit.operation);after.put("nativeDataChanged",false);after.put("originalOutcome",edit.operation.equals("ack-unknown")?"unknown-key-quarantined":"not-submitted");
            return controlResult(journal.preview(c,edit.requestId,actor,CTRL,CTRL+":"+original.requestId,original.requestId,edit.operation,original.requestId,fingerprint,edit.reason,ManagedPostalReceiptService.snapshot(original),after));
        }finally{postal.unlock(c);}}catch(SQLException e){throw uncertain();}
    }
    public Object controlSave(String request,Control edit){
        long actor=journal.admin();String fingerprint=controlFingerprint(request,edit);
        try(Connection c=journal.connection()){boolean discarded=false;lock(c,-1);try{
            postal.actor(c,actor);ManagedOperationJournal.Entry e=control(c,request,edit.requestId);journal.same(e,CTRL,actor,fingerprint);if(!e.state.equals("PREVIEWED"))return controlResult(e);
            ManagedOperationJournal.Entry original=eligible(c,request,edit);e=journal.prepare(c,e,e.target,ManagedPostalReceiptService.snapshot(original),decode(e.afterJson));
            if(edit.operation.equals("ack-unknown"))return controlResult(journal.acknowledge(c,original,e,actor));
            c.setAutoCommit(false);lock(c,-1);
            try{
                postal.actor(c,actor);e=control(c,request,edit.requestId);if(!e.state.equals("PREPARED")){c.rollback();return controlResult(e);}original=source(c,request);
                if(!journal.hash(ManagedPostalReceiptService.snapshot(original)).equals(e.beforeHash)){e=journal.finish(c,e,"NOT_APPLIED",actor);c.commit();return controlResult(e);}
                Map<String,Object> outcome=new TreeMap<>();outcome.put("plan",decode(original.afterJson));outcome.put("controlRequestId",e.requestId);outcome.put("note","管理员已取消尚未提交的兑换预览");
                journal.rejectPreviewInTransaction(c,original,outcome,actor);e=journal.finishMetadataTransaction(c,e,decode(e.afterJson),actor);c.commit();return controlResult(e);
            }catch(SQLException|RuntimeException failure){try{c.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);discarded=true;postal.discard(c,failure);}throw uncertain();}
        }finally{if(!discarded&&!c.isClosed()){postal.unlock(c);if(!c.getAutoCommit())c.setAutoCommit(true);}}}catch(SQLException e){throw uncertain();}
    }
    public Object controlReconcile(String request,String id){long actor=journal.admin();try(Connection c=journal.connection()){lock(c,-1);try{postal.actor(c,actor);ManagedOperationJournal.Entry e=control(c,request,id);if(e.state.equals("PREPARED"))e=journal.finish(c,e,"NOT_APPLIED",actor);return controlResult(e);}finally{postal.unlock(c);}}catch(SQLException e){throw uncertain();}}
    public Object controls(String request){
        long actor=journal.admin();ManagedOperationJournal.id(request);try(Connection c=journal.connection()){postal.actor(c,actor);source(c,request);List<Object> rows=new ArrayList<>();
            try(PreparedStatement p=c.prepareStatement("SELECT request_id FROM "+ManagedOperationJournal.TABLE+" WHERE instance_id=? AND namespace=? AND target_request=? ORDER BY (state='PREPARED') DESC,created_at DESC LIMIT 50")){p.setString(1,journal.instance());p.setString(2,CTRL);p.setString(3,ManagedOperationJournal.id(request));try(ResultSet r=p.executeQuery()){while(r.next())rows.add(controlResult(control(c,request,r.getString(1))));}}return rows;
        }catch(SQLException e){throw uncertain();}
    }
}
