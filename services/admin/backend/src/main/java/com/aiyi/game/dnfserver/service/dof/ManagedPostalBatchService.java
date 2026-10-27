package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Persisted finite task. Each bounded step delegates to the single native issuer. */
@Service
public class ManagedPostalBatchService {
    @Resource private ManagedOperationJournal journal;
    @Resource private ManagedPostalService postal;
    static final String NAMESPACE="postal.batch",PROTOCOL="postal-batch-v1";
    private static final ObjectMapper JSON=new ObjectMapper();
    public static class Draft {public JsonNode recipients,mail;public String reason,retryOf;}
    public static class Edit extends Draft {public String requestId,revision;}
    public static class Link {public String requestId,revision,childRequestId;}
    public static class Control {public String requestId,revision,operation,reason;}
    private static final String CONTROLS="postal.batch.control";
    private static DofFailure corrupt(){return new DofFailure(409,"批量邮件任务结构或子回执不一致，停止执行，请保留原编号核对");}
    private DofFailure unavailable(){return new DofFailure(503,"批量任务结果尚未确认，请读取原任务；不要新建任务重复发放");}
    private static Map<String,Object> map(Object v){if(!(v instanceof Map))throw corrupt();return (Map<String,Object>)v;}
    private static List<Object> list(Object v){if(!(v instanceof List)||((List<?>)v).isEmpty()||((List<?>)v).size()>100)throw corrupt();return (List<Object>)v;}
    private static long number(Object v){if(!(v instanceof Integer||v instanceof Long)||((Number)v).longValue()<1||((Number)v).longValue()>Integer.MAX_VALUE)throw corrupt();return ((Number)v).longValue();}
    private static Map<String,Object> decode(String text){try{return JSON.readValue(text,new TypeReference<Map<String,Object>>(){});}catch(java.io.IOException e){throw corrupt();}}
    private static Map<String,Object> unwrap(Map<String,Object> v){return v.containsKey("plan")?map(v.get("plan")):v;}
    static String childId(String parent,long recipient){return UUID.nameUUIDFromBytes((PROTOCOL+":"+ManagedOperationJournal.id(parent)+":"+recipient).getBytes(StandardCharsets.UTF_8)).toString();}
    private static String resource(String id){return NAMESPACE+":"+ManagedOperationJournal.id(id);}
    private Map<String,Object> draft(Draft d){
        if(d==null||d.recipients==null||!d.recipients.isArray()||d.recipients.size()<1||d.recipients.size()>100)throw new DofFailure(400,"请选择1～100个固定收件角色");
        SortedSet<Long> ids=new TreeSet<>();for(JsonNode node:d.recipients){
            if(!node.isIntegralNumber()||!node.canConvertToLong()||node.longValue()<1||node.longValue()>Integer.MAX_VALUE||!ids.add(node.longValue()))throw new DofFailure(400,"收件角色须为不重复的正整数");
        }
        if(d.reason==null||d.reason.trim().isEmpty()||d.reason.length()>100||d.reason.codePoints().anyMatch(x->x<32||x>65535||Character.isSurrogate((char)x)))throw new DofFailure(400,"请填写1～100字批量操作原因");
        Map<String,Object> out=new TreeMap<>();out.put("recipients",new ArrayList<>(ids));out.put("mail",postal.mail(d.mail));out.put("reason",d.reason);out.put("retryOf",d.retryOf==null?null:ManagedOperationJournal.id(d.retryOf));return out;
    }
    private void revision(String value){if(value==null||!value.matches("[0-9a-f]{64}"))throw new DofFailure(400,"缺少批量版本，请重新核对固定名单与内容");}
    private String fingerprint(Edit e,Map<String,Object> d){Map<String,Object> v=new TreeMap<>();v.put("draft",d);v.put("revision",e.revision);return journal.hash(v);}
    private ManagedOperationJournal.Entry parent(Connection c,String id)throws SQLException{
        ManagedOperationJournal.Entry e=journal.require(c,NAMESPACE,id);Map<String,Object> plan=unwrap(decode(e.afterJson));
        if(!resource(id).equals(e.resource)||!PROTOCOL.equals(decode(e.beforeJson).get("protocol"))||!PROTOCOL.equals(plan.get("protocol"))||!Arrays.asList("send","retry").contains(e.operation))throw corrupt();
        return e;
    }
    private Map<String,Object> child(ManagedOperationJournal.Entry parent,long id){
        Map<String,Object> plan=unwrap(decode(parent.afterJson));for(Object v:list(plan.get("children"))){Map<String,Object> x=map(v);if(number(x.get("recipient"))==id)return x;}throw corrupt();
    }
    private ManagedOperationJournal.Entry leaf(Connection c,ManagedOperationJournal.Entry parent,Map<String,Object> child)throws SQLException{
        long id=number(child.get("recipient"));String request=String.valueOf(child.get("requestId"));
        if(!childId(parent.requestId,id).equals(request))throw corrupt();
        ManagedOperationJournal.Entry e=journal.require(c,"postal."+id,request);
        if(e.actor!=parent.actor||!"send".equals(e.operation)||!parent.requestId.equals(e.targetRequest)||!Long.toString(id).equals(e.target)||!("taiwan_cain_2nd.postal:"+id).equals(e.resource)
            ||!Objects.equals(child.get("revision"),e.beforeHash)||!Objects.equals(child.get("fingerprint"),e.requestHash)
            ||!journal.hash(unwrap(decode(e.afterJson))).equals(journal.hash(unwrap(decode(parent.afterJson)).get("mailPlan"))))throw corrupt();
        return e;
    }
    /** Also called under native locks by the only issuer, before and after durable preparation. */
    static void authorizeChild(Connection c,ManagedOperationJournal journal,long actor,long id,ManagedPostalService.Edit edit,String fingerprint)throws SQLException{
        ManagedOperationJournal.Entry parent=journal.require(c,NAMESPACE,edit.batchRequestId);
        if(parent.actor!=actor||!"PREPARED".equals(parent.state)||!resource(parent.requestId).equals(parent.resource)||!Arrays.asList("send","retry").contains(parent.operation)||!PROTOCOL.equals(decode(parent.beforeJson).get("protocol")))throw new DofFailure(409,"批量父任务尚未开始或已结束，不能单独执行子邮件");
        Map<String,Object> plan=unwrap(decode(parent.afterJson));if(!PROTOCOL.equals(plan.get("protocol")))throw corrupt();
        for(Object v:list(plan.get("children"))){Map<String,Object> child=map(v);if(number(child.get("recipient"))==id){
            if(!childId(parent.requestId,id).equals(ManagedOperationJournal.id(edit.requestId))||!Objects.equals(child.get("fingerprint"),fingerprint))throw corrupt();return;
        }}throw corrupt();
    }
    private String origin(Connection c,ManagedOperationJournal.Entry root,long recipient)throws SQLException{
        Set<String> visited=new HashSet<>();
        while(true){
            if(!visited.add(root.requestId)||visited.size()>64)throw corrupt();
            child(root,recipient);
            if(root.targetRequest==null)return childId(root.requestId,recipient);
            root=parent(c,root.targetRequest);
        }
    }
    private void unclaimedRetry(Connection c,ManagedOperationJournal.Entry source,long recipient,String ownRoot)throws SQLException{
        String origin=origin(c,source,recipient);List<String> candidates=new ArrayList<>();
        // Check the entire lineage, including sibling retries and reservations.
        // A consumed native row or a new generation cannot release a successful claim.
        try(PreparedStatement p=c.prepareStatement("SELECT request_id FROM "+ManagedOperationJournal.TABLE+" WHERE instance_id=? AND resource_key=? AND operation_name='send' AND target_request IS NOT NULL AND state<>'NOT_APPLIED'")){
            p.setString(1,journal.instance());p.setString(2,"taiwan_cain_2nd.postal:"+recipient);try(ResultSet r=p.executeQuery()){while(r.next())candidates.add(r.getString(1));}
        }
        for(String request:candidates){ManagedOperationJournal.Entry candidate=journal.require(c,"postal."+recipient,request);
            if(Objects.equals(candidate.targetRequest,ownRoot))continue;
            ManagedOperationJournal.Entry root=parent(c,candidate.targetRequest);leaf(c,root,child(root,recipient));
            if(origin.equals(origin(c,root,recipient)))throw new DofFailure(409,"该原目标已有保留、成功或未知的关联任务，请读取原任务，不能新建重复发放");
        }
    }
    private Map<String,Object> snapshot(Connection c,Map<String,Object> draft)throws SQLException{return snapshot(c,draft,null);}
    private Map<String,Object> snapshot(Connection c,Map<String,Object> draft,String ownRoot)throws SQLException{
        Map<String,Object> descriptor=postal.descriptor(c),out=new TreeMap<>(),states=new TreeMap<>();
        for(Object x:list(draft.get("recipients"))){long id=number(x);journal.noPending(c,"taiwan_cain_2nd.postal:"+id,null);states.put(Long.toString(id),postal.before(c,id,descriptor));}
        Map<String,Object> mailPlan=postal.frozenSendPlan(map(draft.get("mail")));String retry=(String)draft.get("retryOf");Map<String,Object> retries=new TreeMap<>();
        if(retry!=null){
            ManagedOperationJournal.Entry source=parent(c,retry);Map<String,Object> oldPlan=unwrap(decode(source.afterJson));
            if(!journal.hash(oldPlan.get("mailPlan")).equals(journal.hash(mailPlan)))throw new DofFailure(409,"重试内容或PVF批次与原任务不一致，请勿以重试切换配套");
            for(Object x:list(draft.get("recipients"))){long id=number(x);Map<String,Object> item=child(source,id);ManagedOperationJournal.Entry old=leaf(c,source,item);
                if(!old.state.equals("NOT_APPLIED"))throw new DofFailure(409,"只能重试已确认未提交的目标，不能重发成功或结果未知的邮件");
                unclaimedRetry(c,source,id,ownRoot);
                Map<String,Object> a=new TreeMap<>(decode(old.beforeJson)),b=new TreeMap<>(map(states.get(Long.toString(id))));a.remove("generation");b.remove("generation");
                if(!journal.hash(a).equals(journal.hash(b)))throw new DofFailure(409,"原失败目标的角色身份或结构已改变，停止关联重试");retries.put(Long.toString(id),old.requestId);
            }
        }
        out.put("protocol",PROTOCOL);out.put("targets",states);out.put("mailPlan",mailPlan);out.put("retryOf",retry);out.put("retrySources",retries);return out;
    }
    private ManagedPostalService.Edit edit(ManagedOperationJournal.Entry root,Map<String,Object> child){
        Map<String,Object> d=map(unwrap(decode(root.afterJson)).get("draft"));ManagedPostalService.Edit e=new ManagedPostalService.Edit();
        e.receiveCharacNo=JSON.valueToTree(number(child.get("recipient")));e.mail=JSON.valueToTree(d.get("mail"));e.reason=(String)d.get("reason");e.operation="send";e.requestId=(String)child.get("requestId");e.revision=(String)child.get("revision");e.batchRequestId=root.requestId;return e;
    }
    private Map<String,Object> plan(String id,Map<String,Object> d,Map<String,Object> state){
        List<Object> children=new ArrayList<>();for(Object x:list(d.get("recipients"))){long target=number(x);Map<String,Object> before=map(map(state.get("targets")).get(Long.toString(target))),row=new TreeMap<>();
            ManagedPostalService.Edit e=new ManagedPostalService.Edit();e.receiveCharacNo=JSON.valueToTree(target);e.mail=JSON.valueToTree(d.get("mail"));e.operation="send";e.reason=(String)d.get("reason");e.batchRequestId=id;e.requestId=childId(id,target);e.revision=journal.hash(before);
            row.put("recipient",target);row.put("requestId",e.requestId);row.put("revision",e.revision);row.put("fingerprint",postal.fingerprint(target,e,map(d.get("mail"))));children.add(row);
        }
        Map<String,Object> out=new TreeMap<>();out.put("protocol",PROTOCOL);out.put("draft",d);out.put("mailPlan",state.get("mailPlan"));out.put("children",children);return out;
    }
    public Object validate(Draft draft){
        long actor=journal.admin();Map<String,Object> d=draft(draft);
        try(Connection c=journal.connection()){postal.lock(c);try{postal.actor(c,actor);Map<String,Object> state=snapshot(c,d),out=new TreeMap<>();out.put("revision",journal.hash(state));out.put("snapshot",state);out.put("draft",d);out.put("actor",actor);return out;}finally{postal.unlock(c);}}catch(SQLException e){throw unavailable();}
    }
    public Object preview(Edit edit){
        long actor=journal.admin();Map<String,Object> d=draft(edit);String id=ManagedOperationJournal.id(edit.requestId);revision(edit.revision);String fingerprint=fingerprint(edit,d);
        try(Connection c=journal.connection()){boolean discarded=false;
            try{
                c.setAutoCommit(false);postal.lock(c);postal.actor(c,actor);ManagedOperationJournal.Entry old=journal.find(c,id);
                if(old!=null){journal.same(old,NAMESPACE,actor,fingerprint);Map<String,Object> result=detail(c,parent(c,id));c.rollback();return result;}
                Map<String,Object> state=snapshot(c,d);if(!journal.hash(state).equals(edit.revision))throw new DofFailure(409,"名单、角色身份、发送版本或PVF已改变，请重新核对");
                Map<String,Object> plan=plan(id,d,state);ManagedOperationJournal.Entry root=journal.preview(c,id,actor,NAMESPACE,resource(id),id,d.get("retryOf")==null?"send":"retry",(String)d.get("retryOf"),fingerprint,(String)d.get("reason"),state,plan);
                for(Object v:list(plan.get("children"))){Map<String,Object> child=map(v);long target=number(child.get("recipient"));String childId=(String)child.get("requestId");
                    if(journal.find(c,childId)!=null)throw new DofFailure(409,"子操作编号已有记录，批量预览未创建，请核对原任务");
                    journal.preview(c,childId,actor,"postal."+target,"taiwan_cain_2nd.postal:"+target,Long.toString(target),"send",id,(String)child.get("fingerprint"),(String)d.get("reason"),map(state.get("targets")).get(Long.toString(target)),state.get("mailPlan"));
                }
                c.commit();return detail(c,root);
            }catch(SQLException|RuntimeException failure){
                try{c.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);discarded=true;postal.discard(c,failure);}
                if(failure instanceof DofFailure)throw (DofFailure)failure;throw unavailable();
            }finally{if(!discarded&&!c.isClosed()){postal.unlock(c);if(!c.getAutoCommit())c.setAutoCommit(true);}}
        }catch(SQLException e){throw unavailable();}
    }
    private ManagedOperationJournal.Entry owned(Connection c,long actor,String id,Link link)throws SQLException{
        if(link==null||!ManagedOperationJournal.id(id).equals(ManagedOperationJournal.id(link.requestId)))throw new DofFailure(400,"任务编号与路径不同");revision(link.revision);ManagedOperationJournal.Entry root=parent(c,id);
        if(root.actor!=actor||!root.beforeHash.equals(link.revision))throw new DofFailure(409,"批量操作者或原预览版本不匹配，请读取历史");return root;
    }
    public Object start(Edit edit){
        long actor=journal.admin();Map<String,Object> d=draft(edit);String id=ManagedOperationJournal.id(edit.requestId);revision(edit.revision);
        try(Connection c=journal.connection()){postal.lock(c);try{
            postal.actor(c,actor);ManagedOperationJournal.Entry root=parent(c,id);journal.same(root,NAMESPACE,actor,fingerprint(edit,d));if(!root.state.equals("PREVIEWED"))return detail(c,root);
            Map<String,Object> state=snapshot(c,d,id);if(!journal.hash(state).equals(root.beforeHash)||!journal.hash(state).equals(edit.revision))throw new DofFailure(409,"批量预览已经过期，尚未开始发放");
            for(Object v:list(unwrap(decode(root.afterJson)).get("children")))if(!leaf(c,root,map(v)).state.equals("PREVIEWED"))throw corrupt();
            root=journal.prepare(c,root,id,decode(root.beforeJson),decode(root.afterJson));return detail(c,root);
        }finally{postal.unlock(c);}}catch(SQLException e){throw unavailable();}
    }
    private Map<String,Object> summary(List<Object> rows){
        int sent=0,notApplied=0,unknown=0,pending=0,ready=0;for(Object value:rows){String state=(String)map(value).get("state");
            switch(state){case "PERSISTED":sent++;break;case "NOT_APPLIED":notApplied++;break;case "ACKNOWLEDGED":unknown++;break;case "PREPARED":case "CONFLICT":pending++;break;case "PREVIEWED":ready++;break;default:throw corrupt();}
        }
        Map<String,Object> out=new TreeMap<>();out.put("total",rows.size());out.put("sent",sent);out.put("notApplied",notApplied);out.put("unknown",unknown);out.put("pending",pending);out.put("ready",ready);out.put("terminal",sent+notApplied+unknown);out.put("allSent",sent==rows.size());return out;
    }
    private Map<String,Object> detail(Connection c,ManagedOperationJournal.Entry root)throws SQLException{
        Map<String,Object> plan=unwrap(decode(root.afterJson));List<Object> rows=new ArrayList<>();Set<Long> ids=new HashSet<>();
        for(Object v:list(plan.get("children"))){Map<String,Object> child=map(v);long id=number(child.get("recipient"));if(!ids.add(id))throw corrupt();ManagedOperationJournal.Entry e=leaf(c,root,child);
            Map<String,Object> out=new TreeMap<>(),result=decode(e.afterJson);out.put("recipient",id);out.put("requestId",e.requestId);out.put("state",e.state);out.put("postalId",result.get("postalId"));out.put("error",result.get("error"));out.put("actualReceiptVerified",false);rows.add(out);
        }
        Map<String,Object> counts=summary(rows),out=journal.result(root);String phase;
        if(root.state.equals("PREVIEWED")){if((int)counts.get("ready")!=rows.size())throw corrupt();phase="PREVIEWED";}
        else if(root.state.equals("PREPARED"))phase=(int)counts.get("pending")>0?"NEEDS_RECONCILIATION":(int)counts.get("ready")>0?"READY_FOR_NEXT":"ALL_RESULTS_RECORDED";
        else if(root.state.equals("PERSISTED")){if((int)counts.get("terminal")!=rows.size())throw corrupt();phase=(boolean)counts.get("allSent")?"COMPLETED":(int)counts.get("unknown")>0?"COMPLETED_WITH_UNCERTAINTY":"COMPLETED_WITH_FAILURES";}
        else throw corrupt();
        out.put("revision",root.beforeHash);out.put("controlRevision",journal.hash(controlSnapshot(c,root)));out.put("phase",phase);out.put("progress",counts);out.put("targets",rows);out.put("draft",plan.get("draft"));out.put("mailPlan",plan.get("mailPlan"));out.put("retryOf",root.targetRequest);out.put("actualPlayerReceiptVerified",false);return out;
    }
    private Map<String,Object> finishIfTerminal(Connection c,ManagedOperationJournal.Entry root)throws SQLException{
        Map<String,Object> result=detail(c,root),counts=map(result.get("progress"));if(root.state.equals("PREPARED")&&(int)counts.get("terminal")==((List<?>)result.get("targets")).size()){
            // owned() and actor() already verified this exact actor under locks;
            // do not borrow another pool connection for a redundant DAO lookup.
            Map<String,Object> outcome=new TreeMap<>();outcome.put("plan",unwrap(decode(root.afterJson)));outcome.put("summary",counts);root=journal.finishMetadataTask(c,root,outcome,root.actor);return detail(c,root);
        }return result;
    }
    public Object step(String id,Link link){
        long actor=journal.admin();Map<String,Object> next=null;ManagedPostalService.Edit request=null;
        try(Connection c=journal.connection()){postal.lock(c);try{
            postal.actor(c,actor);ManagedOperationJournal.Entry root=owned(c,actor,id,link);Map<String,Object> current=detail(c,root);
            if(root.state.equals("PREVIEWED"))throw new DofFailure(409,"请先明确开始已预览任务");if(root.state.equals("PERSISTED"))return current;
            if(link.childRequestId==null){
                if((int)map(current.get("progress")).get("ready")==0&&(int)map(current.get("progress")).get("pending")==0)return finishIfTerminal(c,root);
                throw new DofFailure(400,"执行步骤缺少固定子操作编号，请读取原任务");
            }
            String selected=ManagedOperationJournal.id(link.childRequestId);
            for(Object v:list(unwrap(decode(root.afterJson)).get("children")))if(selected.equals(map(v).get("requestId"))){next=map(v);break;}
            if(next==null)throw new DofFailure(409,"子操作不属于当前批量任务");
            if(!leaf(c,root,next).state.equals("PREVIEWED"))return finishIfTerminal(c,root);
            if((int)map(current.get("progress")).get("pending")>0)throw new DofFailure(409,"存在结果不明的子操作，请先核对原任务，不能继续发放");
            request=edit(root,next);
        }finally{postal.unlock(c);}}catch(SQLException e){throw unavailable();}
        DofFailure failure=null;try{postal.save(request);}catch(DofFailure e){if(e.status==403)throw e;failure=e;}
        try(Connection c=journal.connection()){postal.lock(c);try{
            postal.actor(c,actor);ManagedOperationJournal.Entry root=owned(c,actor,id,link),child=leaf(c,root,next);
            if(child.state.equals("PREVIEWED")){
                if(failure==null)throw new DofFailure(409,"子操作尚未推进，请读取原任务核对，不自动重复提交");
                Map<String,Object> outcome=new TreeMap<>(),error=new TreeMap<>();outcome.put("plan",decode(child.afterJson));error.put("status",failure.status);String message=failure.getMessage();error.put("message",message.length()>500?message.substring(0,500):message);error.put("phase","issuer_returned_preview_still_unsubmitted");outcome.put("error",error);
                journal.rejectUnsubmittedPreview(c,child,outcome,actor);
            }
            return finishIfTerminal(c,parent(c,id));
        }finally{postal.unlock(c);}}catch(SQLException e){throw unavailable();}
    }
    public Object reconcile(String id,Link link){
        long actor=journal.admin();List<Map<String,Object>> pending=new ArrayList<>();
        try(Connection c=journal.connection()){postal.lock(c);try{
            postal.actor(c,actor);ManagedOperationJournal.Entry root=owned(c,actor,id,link);
            for(Object v:list(unwrap(decode(root.afterJson)).get("children"))){Map<String,Object> child=map(v);if(leaf(c,root,child).state.equals("PREPARED"))pending.add(child);}
        }finally{postal.unlock(c);}}catch(SQLException e){throw unavailable();}
        for(Map<String,Object> child:pending)postal.reconcile(number(child.get("recipient")),(String)child.get("requestId"));
        try(Connection c=journal.connection()){postal.lock(c);try{postal.actor(c,actor);return finishIfTerminal(c,owned(c,actor,id,link));}finally{postal.unlock(c);}}catch(SQLException e){throw unavailable();}
    }
    private Map<String,Object> controlSnapshot(Connection c,ManagedOperationJournal.Entry root)throws SQLException{
        Map<String,Object> out=new TreeMap<>();out.put("protocol","postal-batch-control-v1");out.put("root",ManagedPostalReceiptService.snapshot(root));List<Object> children=new ArrayList<>();
        for(Object value:list(unwrap(decode(root.afterJson)).get("children")))children.add(ManagedPostalReceiptService.snapshot(leaf(c,root,map(value))));out.put("children",children);return out;
    }
    private String controlFingerprint(String source,Control edit){
        ManagedOperationJournal.id(source);if(edit==null)throw new DofFailure(400,"缺少任务控制请求");ManagedOperationJournal.id(edit.requestId);revision(edit.revision);
        if(!Arrays.asList("cancel-pending","finalize").contains(edit.operation))throw new DofFailure(400,"未知任务控制动作");
        if(edit.reason==null||edit.reason.trim().isEmpty()||edit.reason.length()>100||edit.reason.chars().anyMatch(x->x<32||Character.isSurrogate((char)x)))throw new DofFailure(400,"请填写1～100字任务控制原因");
        Map<String,Object> out=new TreeMap<>();out.put("source",ManagedOperationJournal.id(source));out.put("revision",edit.revision);out.put("operation",edit.operation);out.put("reason",edit.reason);return journal.hash(out);
    }
    private ManagedOperationJournal.Entry control(Connection c,String source,String request)throws SQLException{
        ManagedOperationJournal.Entry e=journal.require(c,CONTROLS,request);
        if(!ManagedOperationJournal.id(source).equals(e.targetRequest)||!e.targetRequest.equals(e.target)||!(CONTROLS+":"+e.targetRequest).equals(e.resource)||!Arrays.asList("cancel-pending","finalize").contains(e.operation))throw corrupt();return e;
    }
    private Map<String,Object> controlResult(ManagedOperationJournal.Entry e){Map<String,Object> out=journal.result(e);out.put("revision",e.beforeHash);out.put("outcome",decode(e.afterJson));out.put("nativeDataChanged",false);out.put("actualPlayerReceiptVerified",false);return out;}
    private Map<String,Object> controlPlan(Connection c,ManagedOperationJournal.Entry root,Control edit)throws SQLException{
        Map<String,Object> current=detail(c,root),counts=map(current.get("progress"));
        if(!edit.revision.equals(current.get("controlRevision")))throw new DofFailure(409,"任务进度已改变，请重新读取并预览控制动作");
        if(edit.operation.equals("cancel-pending")){
            if(!Arrays.asList("PREVIEWED","PREPARED").contains(root.state)||(int)counts.get("ready")==0)throw new DofFailure(409,"没有可永久终止的未提交目标");
        }else if(!root.state.equals("PREPARED")||(int)counts.get("ready")!=0||(int)counts.get("pending")!=0)throw new DofFailure(409,"只有全部目标已有确定回执后才能保存汇总");
        List<Object> targets=new ArrayList<>();for(Object v:list(current.get("targets")))if(map(v).get("state").equals("PREVIEWED")){Map<String,Object> target=new TreeMap<>();target.put("recipient",map(v).get("recipient"));target.put("requestId",map(v).get("requestId"));targets.add(target);}
        Map<String,Object> out=new TreeMap<>();out.put("protocol","postal-batch-control-v1");out.put("operation",edit.operation);out.put("cancelTargets",targets);out.put("nativeDataChanged",false);return out;
    }
    public Object controlPreview(String source,Control edit){
        long actor=journal.admin();String fingerprint=controlFingerprint(source,edit);
        try(Connection c=journal.connection()){postal.lock(c);try{
            postal.actor(c,actor);ManagedOperationJournal.Entry old=journal.find(c,edit.requestId);
            if(old!=null){journal.same(old,CONTROLS,actor,fingerprint);return controlResult(control(c,source,edit.requestId));}
            ManagedOperationJournal.Entry root=parent(c,source);Map<String,Object> plan=controlPlan(c,root,edit);
            return controlResult(journal.preview(c,edit.requestId,actor,CONTROLS,CONTROLS+":"+root.requestId,root.requestId,edit.operation,root.requestId,fingerprint,edit.reason,controlSnapshot(c,root),plan));
        }finally{postal.unlock(c);}}catch(SQLException e){throw unavailable();}
    }
    public Object controlSave(String source,Control edit){
        long actor=journal.admin();String fingerprint=controlFingerprint(source,edit);
        try(Connection c=journal.connection()){boolean discarded=false;postal.lock(c);try{
            postal.actor(c,actor);ManagedOperationJournal.Entry e=control(c,source,edit.requestId);journal.same(e,CONTROLS,actor,fingerprint);
            if(!e.state.equals("PREVIEWED"))return controlResult(e);
            ManagedOperationJournal.Entry root=parent(c,source);Map<String,Object> plan=controlPlan(c,root,edit),before=controlSnapshot(c,root);
            if(!journal.hash(before).equals(e.beforeHash)||!journal.hash(plan).equals(e.afterHash))throw new DofFailure(409,"控制预览内容已改变");
            e=journal.prepare(c,e,e.target,before,plan);
            c.setAutoCommit(false);postal.lock(c);
            try{
                postal.actor(c,actor);e=control(c,source,edit.requestId);if(!e.state.equals("PREPARED")){c.rollback();return controlResult(e);}
                root=parent(c,source);
                if(!journal.hash(controlSnapshot(c,root)).equals(e.beforeHash)){e=journal.finish(c,e,"NOT_APPLIED",actor);c.commit();return controlResult(e);}
                if(root.state.equals("PREVIEWED"))root=journal.prepare(c,root,root.target,decode(root.beforeJson),decode(root.afterJson));
                if(edit.operation.equals("cancel-pending"))for(Object value:list(unwrap(decode(root.afterJson)).get("children"))){
                    ManagedOperationJournal.Entry child=leaf(c,root,map(value));if(!child.state.equals("PREVIEWED"))continue;
                    Map<String,Object> outcome=new TreeMap<>(),error=new TreeMap<>();outcome.put("plan",decode(child.afterJson));error.put("phase","cancelled_before_issue");error.put("message","管理员已永久终止本任务中尚未提交的目标");error.put("controlRequestId",e.requestId);outcome.put("error",error);
                    journal.rejectPreviewInTransaction(c,child,outcome,actor);
                }
                Map<String,Object> result=detail(c,root),counts=map(result.get("progress"));
                if((int)counts.get("terminal")==((List<?>)result.get("targets")).size()){
                    Map<String,Object> outcome=new TreeMap<>();outcome.put("plan",unwrap(decode(root.afterJson)));outcome.put("summary",counts);root=journal.finishMetadataTransaction(c,root,outcome,actor);
                }
                Map<String,Object> outcome=new TreeMap<>();outcome.put("plan",plan);outcome.put("parentState",root.state);outcome.put("progress",counts);
                e=journal.finishMetadataTransaction(c,e,outcome,actor);c.commit();return controlResult(e);
            }catch(SQLException|RuntimeException failure){
                try{c.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);discarded=true;postal.discard(c,failure);}throw unavailable();
            }
        }finally{if(!discarded&&!c.isClosed()){postal.unlock(c);if(!c.getAutoCommit())c.setAutoCommit(true);}}}catch(SQLException e){throw unavailable();}
    }
    public Object controlReconcile(String source,String request){
        long actor=journal.admin();try(Connection c=journal.connection()){postal.lock(c);try{
            postal.actor(c,actor);ManagedOperationJournal.Entry e=control(c,source,request);
            // All source/child mutations commit with this InnoDB receipt. Under
            // their shared locks, PREPARED proves the metadata transaction did not commit.
            if(e.state.equals("PREPARED"))e=journal.finish(c,e,"NOT_APPLIED",actor);return controlResult(e);
        }finally{postal.unlock(c);}}catch(SQLException e){throw unavailable();}
    }
    public Object controlHistory(String source){
        long actor=journal.admin();try(Connection c=consistentRead()){try{
            postal.actor(c,actor);parent(c,source);List<Object> result=new ArrayList<>();
            try(PreparedStatement p=c.prepareStatement("SELECT request_id FROM "+ManagedOperationJournal.TABLE+" WHERE instance_id=? AND namespace=? AND target_request=? ORDER BY created_at DESC,request_id DESC LIMIT 100")){
                p.setString(1,journal.instance());p.setString(2,CONTROLS);p.setString(3,ManagedOperationJournal.id(source));try(ResultSet r=p.executeQuery()){while(r.next())result.add(controlResult(control(c,source,r.getString(1))));}
            }return result;
        }finally{c.rollback();}}catch(SQLException e){throw unavailable();}
    }
    private Connection consistentRead()throws SQLException{
        Connection c=journal.connection();try{c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);return c;}catch(SQLException e){c.close();throw e;}
    }
    public Object read(String id){long actor=journal.admin();try(Connection c=consistentRead()){try{postal.actor(c,actor);return detail(c,parent(c,id));}finally{c.rollback();}}catch(SQLException e){throw unavailable();}}
    public Object history(){
        long actor=journal.admin();try(Connection c=consistentRead()){try{
            postal.actor(c,actor);List<Object> out=new ArrayList<>();for(ManagedOperationJournal.Entry e:journal.history(c,NAMESPACE)){Map<String,Object> plan=unwrap(decode(parent(c,e.requestId).afterJson)),row=journal.result(e);row.put("revision",e.beforeHash);row.put("total",list(plan.get("children")).size());row.put("retryOf",e.targetRequest);out.add(row);}return out;
        }finally{c.rollback();}
        }catch(SQLException e){throw unavailable();}
    }
}
