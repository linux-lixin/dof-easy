package com.aiyi.game.dnfserver.service.dof;

import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.sql.*;
import java.util.*;

/** Metadata-only acknowledgment. A missing/deleted recipient is never recreated. */
@Service
public class ManagedPostalReceiptService {
    @Resource private ManagedOperationJournal journal;
    @Resource private ManagedPostalService postal;
    public static class Edit {public String requestId,revision,reason;}
    private String namespace(long recipient){return "postal.receipt-control."+recipient;}
    private void target(long recipient){if(recipient<1||recipient>Integer.MAX_VALUE)throw new DofFailure(400,"收件角色编号无效");}
    static Map<String,Object> snapshot(ManagedOperationJournal.Entry e){
        Map<String,Object> out=new TreeMap<>();out.put("requestId",e.requestId);out.put("namespace",e.namespace);out.put("resource",e.resource);out.put("target",e.target);out.put("operation",e.operation);out.put("targetRequestId",e.targetRequest);out.put("actor",e.actor);out.put("requestHash",e.requestHash);out.put("beforeHash",e.beforeHash);out.put("afterHash",e.afterHash);out.put("state",e.state);return out;
    }
    private String fingerprint(long recipient,String source,Edit edit){
        target(recipient);ManagedOperationJournal.id(source);
        if(edit==null)throw new DofFailure(400,"缺少人工确认请求");ManagedOperationJournal.id(edit.requestId);
        if(edit.revision==null||!edit.revision.matches("[0-9a-f]{64}"))throw new DofFailure(400,"缺少原回执版本，请重新读取历史");
        if(edit.reason==null||edit.reason.trim().isEmpty()||edit.reason.length()>100||edit.reason.chars().anyMatch(x->x<32||Character.isSurrogate((char)x)))throw new DofFailure(400,"人工确认原因须为1～100字，不能包含控制字符或代理码");
        Map<String,Object> out=new TreeMap<>();out.put("recipient",recipient);out.put("source",ManagedOperationJournal.id(source));out.put("revision",edit.revision);out.put("reason",edit.reason);out.put("operation","ack-unknown");return journal.hash(out);
    }
    private ManagedOperationJournal.Entry control(Connection c,long recipient,String source,String request)throws SQLException{
        ManagedOperationJournal.Entry e=journal.require(c,namespace(recipient),request);
        if(!ManagedOperationJournal.id(source).equals(e.targetRequest)||!Long.toString(recipient).equals(e.target)||!"ack-unknown".equals(e.operation)||!e.resource.equals("postal.receipt:"+recipient+":"+e.targetRequest))throw new DofFailure(409,"人工确认回执归属不匹配");return e;
    }
    private Map<String,Object> outcome(ManagedOperationJournal.Entry e){
        Map<String,Object> out=journal.result(e);out.put("revision",e.beforeHash);out.put("nativeDataChanged",false);out.put("originalOutcome","unknown");out.put("actualReceiptVerified",false);return out;
    }
    private Map<String,Object> eligible(Connection c,long recipient,String source,String revision)throws SQLException{
        ManagedOperationJournal.Entry original=postal.source(c,recipient,source);
        if(!"send".equals(original.operation)||!"CONFLICT".equals(original.state))throw new DofFailure(409,"只能人工确认结果不明的发送回执；不会重发邮件");
        Map<String,Object> state=snapshot(original);if(!journal.hash(state).equals(revision))throw new DofFailure(409,"原邮件回执已改变，请重新读取和预览");return state;
    }
    private DofFailure uncertain(){return new DofFailure(503,"人工确认结果尚未核实，请保留确认操作编号并核对历史；不要重发邮件");}
    public Object preview(long recipient,String source,Edit edit){
        long who=journal.admin();String fingerprint=fingerprint(recipient,source,edit);
        try(Connection c=journal.connection()){postal.lock(c);try{
            postal.actor(c,who);ManagedOperationJournal.Entry old=journal.find(c,edit.requestId);
            if(old!=null){journal.same(old,namespace(recipient),who,fingerprint);return outcome(control(c,recipient,source,edit.requestId));}
            Map<String,Object> before=eligible(c,recipient,source,edit.revision),after=new TreeMap<>();after.put("sourceRequestId",ManagedOperationJournal.id(source));after.put("originalOutcome","unknown");after.put("nativeDataChanged",false);
            return outcome(journal.preview(c,edit.requestId,who,namespace(recipient),"postal.receipt:"+recipient+":"+ManagedOperationJournal.id(source),Long.toString(recipient),"ack-unknown",source,fingerprint,edit.reason,before,after));
        }finally{postal.unlock(c);}}catch(SQLException e){throw uncertain();}
    }
    public Object save(long recipient,String source,Edit edit){
        long who=journal.admin();String fingerprint=fingerprint(recipient,source,edit);
        try(Connection c=journal.connection()){postal.lock(c);try{
            postal.actor(c,who);ManagedOperationJournal.Entry e=control(c,recipient,source,edit.requestId);journal.same(e,namespace(recipient),who,fingerprint);
            if(!"PREVIEWED".equals(e.state))return outcome(e);
            Map<String,Object> before=eligible(c,recipient,source,edit.revision);
            if(!journal.hash(before).equals(e.beforeHash))throw new DofFailure(409,"人工确认预览已改变");
            // Persist PREPARED first. The subsequent single InnoDB UPDATE changes
            // source+control atomically; neither statement writes native mail data.
            e=journal.prepare(c,e,e.target,before,new com.fasterxml.jackson.databind.ObjectMapper().readValue(e.afterJson,Map.class));
            return outcome(journal.acknowledge(c,postal.source(c,recipient,source),e,who));
        }finally{postal.unlock(c);}}catch(SQLException|java.io.IOException e){throw uncertain();}
    }
    public Object reconcile(long recipient,String source,String request){
        long who=journal.admin();target(recipient);
        try(Connection c=journal.connection()){postal.lock(c);try{
            postal.actor(c,who);ManagedOperationJournal.Entry e=control(c,recipient,source,request);
            // A committed acknowledgment always records NO_CHANGE atomically with
            // ACKNOWLEDGED on the original. Under the journal lock, PREPARED proves
            // this metadata action did not commit; it says nothing about delivery.
            if("PREPARED".equals(e.state))e=journal.finish(c,e,"NOT_APPLIED",who);return outcome(e);
        }finally{postal.unlock(c);}}catch(SQLException e){throw uncertain();}
    }
    public Object history(long recipient){
        long who=journal.admin();target(recipient);
        try(Connection c=journal.connection()){postal.actor(c,who);List<Object> out=new ArrayList<>();for(ManagedOperationJournal.Entry e:journal.history(c,namespace(recipient)))out.add(outcome(control(c,recipient,e.targetRequest,e.requestId)));return out;}catch(SQLException e){throw uncertain();}
    }
}
