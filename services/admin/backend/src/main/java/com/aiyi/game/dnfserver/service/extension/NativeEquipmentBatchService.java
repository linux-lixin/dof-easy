package com.aiyi.game.dnfserver.service.extension;

import com.aiyi.game.dnfserver.service.dof.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Finite, persisted configuration task. Native plugins remain the only item/currency executors. */
@Service
public class NativeEquipmentBatchService {
    @Resource private ManagedOperationJournal journal;
    @Resource private ActivityConfigService activity;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String PROTOCOL="equipment-batch-v1";
    public static class Edit {public String requestId,revision,reason,sourceTaskId;}
    public static class Control extends Edit {public String operation,childRequestId,currentRevision;}
    private interface Work<T>{T run(Connection c,ActivityConfigService.Section s,long actor)throws Exception;}
    private static ActivityConfigService.Failure fail(int status,String message){return new ActivityConfigService.Failure(status,message);}
    private static ActivityConfigService.Failure corrupt(){return fail(409,"批量任务的计划、归属或子回执不一致，停止执行并保留原编号");}
    private static String ns(String key){return "activity.batch."+key;}
    private static String items(String key){return "activity.batch.item."+key;}
    private static String controls(String key){return "activity.batch.control."+key;}
    private static String childId(String parent,long code){return UUID.nameUUIDFromBytes((PROTOCOL+":"+ManagedOperationJournal.id(parent)+":"+code).getBytes(StandardCharsets.UTF_8)).toString();}
    private static Map<String,Object> decode(String json){try{return JSON.readValue(json,new TypeReference<Map<String,Object>>(){});}catch(IOException e){throw corrupt();}}
    private static Map<String,Object> map(Object x){if(!(x instanceof Map))throw corrupt();return (Map<String,Object>)x;}
    private static Map<String,Object> plan(ManagedOperationJournal.Entry e){Map<String,Object> p=decode(e.afterJson);return p.containsKey("plan")?map(p.get("plan")):p;}
    private static void revision(String text){if(text==null||!text.matches("[0-9a-f]{64}"))throw fail(400,"缺少批量版本，请先读取当前状态");}
    private static void reason(String text){if(text==null||text.trim().isEmpty()||text.length()>128||text.codePoints().anyMatch(x->x<32||x>65535||Character.isSurrogate((char)x)))throw fail(400,"请填写1～128字批量操作原因");}
    /** Ordinary edits cannot bypass a task that has reserved a module, including an unsubmitted preview. */
    static void guardOrdinary(Connection c,ManagedOperationJournal journal,String key)throws SQLException{guardActive(c,journal,key,null);}
    private static void guardActive(Connection c,ManagedOperationJournal journal,String key,String own)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT request_id FROM "+ManagedOperationJournal.TABLE+" WHERE instance_id=? AND namespace=? AND state IN ('PREVIEWED','PREPARED','CONFLICT')")){
            p.setString(1,journal.instance());p.setString(2,ns(key));try(ResultSet r=p.executeQuery()){while(r.next())if(!Objects.equals(own,r.getString(1)))throw fail(409,"当前规则已有批量任务，请读取原任务，完成核对或取消后再修改");}
        }
    }
    private <T>T run(String key,boolean transaction,Work<T> work){
        long actor=journal.admin();ActivityConfigService.Section s=activity.equipment(key);
        try(Connection c=journal.connection()){
            boolean locked=false,discarded=false;Throwable primary=null;
            try{
                if(transaction)c.setAutoCommit(false);activity.lock(c,s);locked=true;activity.guard(c,s);T result=work.run(c,s,actor);if(transaction)c.commit();return result;
            }catch(Exception|Error error){primary=error;if(transaction)try{c.rollback();}catch(SQLException cleanup){error.addSuppressed(cleanup);activity.discard(c,error);discarded=true;}
                if(error instanceof RuntimeException)throw (RuntimeException)error;if(error instanceof Error)throw (Error)error;throw fail(503,"批量操作结果尚未确认，请读取原任务，不要更换编号重做");
            }finally{
                if(!discarded&&!c.isClosed()){
                    try{if(locked)activity.unlock(c);if(transaction)c.setAutoCommit(true);}catch(SQLException|RuntimeException cleanup){activity.discard(c,cleanup);if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}
                }
            }
        }catch(SQLException e){throw fail(503,"批量数据库不可用，请保留原任务编号核对");}
    }
    private ManagedOperationJournal.Entry parent(Connection c,ActivityConfigService.Section s,String id)throws SQLException{
        ManagedOperationJournal.Entry e=journal.require(c,ns(s.key),id);Map<String,Object> p=plan(e);
        if(!PROTOCOL.equals(p.get("protocol"))||!s.key.equals(p.get("section"))||!e.resource.equals(activity.resource(s)+":batch")||!e.target.equals(e.requestId)||!Arrays.asList("import-upsert","clear","restore").contains(e.operation)||!Objects.equals(p.get("operation"),e.operation))throw corrupt();
        Map<String,Object> request=map(p.get("request"));
        if(!Objects.equals(p.get("databaseUuid"),activity.batchSpec(c,s).database)||!journal.hash(request).equals(e.requestHash)||!Objects.equals(request.get("operation"),e.operation)||!Objects.equals(request.get("reason"),e.reason)||!Objects.equals(request.get("sourceTaskId"),e.targetRequest)||!Objects.equals(decode(e.beforeJson).get("revision"),request.get("revision")))throw corrupt();
        return e;
    }
    private long code(Map<String,Object> row){Object v=row==null?null:row.get("code");if(!(v instanceof Integer||v instanceof Long)||((Number)v).longValue()<0||((Number)v).longValue()>Integer.MAX_VALUE)throw fail(409,"批量写入暂不支持超出已验证范围的规则代码；未修改原生数据");return ((Number)v).longValue();}
    private ManagedOperationJournal.Entry child(Connection c,ActivityConfigService.Section s,ManagedOperationJournal.Entry root,String id)throws SQLException{
        ManagedOperationJournal.Entry e=journal.require(c,items(s.key),id);
        if(!e.target.matches("(?:0|[1-9][0-9]{0,9})"))throw corrupt();long code=Long.parseLong(e.target);
        if(code>Integer.MAX_VALUE||!childId(root.requestId,code).equals(e.requestId)||!root.requestId.equals(e.targetRequest)||e.actor!=root.actor||!activity.resource(s).equals(e.resource)||!e.operation.equals(root.operation))throw corrupt();
        for(String text:Arrays.asList(e.beforeJson,e.afterJson)){Map<String,Object> row=activity.snapshot(s,text);if(row!=null&&code(row)!=code)throw corrupt();}return e;
    }
    private List<ManagedOperationJournal.Entry> children(Connection c,ActivityConfigService.Section s,ManagedOperationJournal.Entry root,long previous,int limit)throws SQLException{
        List<String> ids=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT request_id FROM "+ManagedOperationJournal.TABLE+" WHERE instance_id=? AND namespace=? AND target_request=? AND CAST(target_key AS UNSIGNED)>=? ORDER BY CAST(target_key AS UNSIGNED) LIMIT ?")){
            p.setString(1,journal.instance());p.setString(2,items(s.key));p.setString(3,root.requestId);p.setLong(4,previous+1);p.setInt(5,limit);try(ResultSet r=p.executeQuery()){while(r.next())ids.add(r.getString(1));}
        }List<ManagedOperationJournal.Entry> out=new ArrayList<>();for(String id:ids)out.add(child(c,s,root,id));return out;
    }
    private void digest(MessageDigest digest,ManagedOperationJournal.Entry e){Map<String,Object> p=new TreeMap<>();p.put("id",e.requestId);p.put("code",e.target);p.put("before",e.beforeHash);p.put("after",e.afterHash);p.put("request",e.requestHash);digest.update(journal.hash(p).getBytes(StandardCharsets.US_ASCII));digest.update((byte)'\n');}
    private Map<String,Object> summary(Connection c,ActivityConfigService.Section s,ManagedOperationJournal.Entry root)throws SQLException{
        MessageDigest digest=NativeEquipmentDataset.digest(),states=NativeEquipmentDataset.digest();Map<String,Long> counts=new TreeMap<>();long total=0,previous=-1;
        while(true){List<ManagedOperationJournal.Entry> page=children(c,s,root,previous,100);if(page.isEmpty())break;for(ManagedOperationJournal.Entry e:page){digest(digest,e);states.update((e.requestId+":"+e.state+"\n").getBytes(StandardCharsets.US_ASCII));counts.put(e.state,counts.getOrDefault(e.state,0L)+1);total++;previous=Long.parseLong(e.target);}}
        Map<String,Object> p=plan(root);if(!(p.get("rowCount") instanceof Number)||((Number)p.get("rowCount")).longValue()!=total||!Objects.equals(p.get("planHash"),NativeEquipmentDataset.hex(digest.digest())))throw corrupt();
        Map<String,Object> result=new TreeMap<>();result.put("counts",counts);result.put("total",total);result.put("statesHash",NativeEquipmentDataset.hex(states.digest()));return result;
    }
    private String version(ManagedOperationJournal.Entry root,Map<String,Object> summary){Map<String,Object> p=new TreeMap<>();p.put("requestId",root.requestId);p.put("requestHash",root.requestHash);p.put("state",root.state);p.put("afterHash",root.afterHash);p.put("children",summary);return journal.hash(p);}
    private Map<String,Object> detail(Connection c,ActivityConfigService.Section s,ManagedOperationJournal.Entry root,int page,int size)throws SQLException{
        if(page<1||size<1||size>100)throw fail(400,"任务页码或每页数量无效");Map<String,Object> sum=summary(c,s,root),out=journal.result(root);out.put("revision",version(root,sum));out.put("summary",sum);out.put("plan",plan(root));out.put("outcome",decode(root.afterJson).get("outcome"));out.put("configurationEffectVerified",false);out.put("moduleSnapshotAtFinish",decode(root.afterJson).get("moduleSnapshotAtFinish"));
        List<Object> rows=new ArrayList<>();long skip=(long)(page-1)*size,previous=-1,seen=0;while(seen<skip+size){List<ManagedOperationJournal.Entry> entries=children(c,s,root,previous,100);if(entries.isEmpty())break;for(ManagedOperationJournal.Entry e:entries){if(seen>=skip&&seen<skip+size){Map<String,Object> item=journal.result(e);item.put("before",activity.snapshot(s,e.beforeJson));item.put("after",activity.snapshot(s,e.afterJson));rows.add(item);}seen++;previous=Long.parseLong(e.target);}if(seen>=skip+size)break;}
        out.put("items",rows);out.put("page",page);out.put("pageSize",size);out.put("notice","任务持久化不代表组件或游戏效果通过；未决子项只能核对原编号，不能重新执行。");return out;
    }
    public Object state(String key){return run(key,false,(c,s,a)->activity.batchState(c,s));}
    public Object history(String key){return run(key,false,(c,s,a)->{List<Object> out=new ArrayList<>();for(ManagedOperationJournal.Entry e:journal.history(c,ns(key)))out.add(journal.result(e));return out;});}
    public Object detail(String key,String id,int page,int size){return run(key,false,(c,s,a)->detail(c,s,parent(c,s,id),page,size));}
    private Map<String,Object> request(Edit e,String operation,String sourceRevision){if(e==null)throw fail(400,"缺少任务预览");ManagedOperationJournal.id(e.requestId);revision(e.revision);reason(e.reason);Map<String,Object> p=new TreeMap<>();p.put("operation",operation);p.put("revision",e.revision);p.put("reason",e.reason);p.put("sourceRevision",sourceRevision);p.put("sourceTaskId",e.sourceTaskId==null?null:ManagedOperationJournal.id(e.sourceTaskId));if(operation.equals("restore")!=(e.sourceTaskId!=null))throw fail(400,"恢复来源与操作不匹配");return p;}
    private ManagedOperationJournal.Entry add(Connection c,ActivityConfigService.Section s,String root,long actor,String operation,String reason,Map<String,Object> before,Map<String,Object> after)throws SQLException{
        long code=code(before==null?after:before);Map<String,Object> p=new TreeMap<>();p.put("parent",root);p.put("operation",operation);p.put("before",before);p.put("after",after);return journal.preview(c,childId(root,code),actor,items(s.key),activity.resource(s),Long.toString(code),operation,root,journal.hash(p),reason,before,after);
    }
    private interface Planner{void run(Connection c,ActivityConfigService.Section s,long actor,String root,MessageDigest digest,long[] count)throws Exception;}
    private Object preview(String key,Edit edit,String operation,String sourceRevision,Planner planner){Map<String,Object> req=request(edit,operation,sourceRevision);String fingerprint=journal.hash(req),id=ManagedOperationJournal.id(edit.requestId);
        return run(key,true,(c,s,actor)->{
            ManagedOperationJournal.Entry old=journal.find(c,id);if(old!=null){journal.same(old,ns(key),actor,fingerprint);return detail(c,s,parent(c,s,id),1,20);}
            guardActive(c,journal,key,null);activity.noLegacyPending(c,s);journal.noPending(c,activity.resource(s),null);
            Map<String,Object> state=activity.batchState(c,s);if(!Objects.equals(state.get("revision"),edit.revision))throw fail(409,"全表版本已变化，请重新读取并预览，未创建任务");
            MessageDigest digest=NativeEquipmentDataset.digest();long[] count={0};planner.run(c,s,actor,id,digest,count);
            Map<String,Object> p=new TreeMap<>();p.put("protocol",PROTOCOL);p.put("section",key);p.put("operation",operation);p.put("rowCount",count[0]);p.put("planHash",NativeEquipmentDataset.hex(digest.digest()));p.put("request",req);p.put("databaseUuid",activity.batchSpec(c,s).database);
            ManagedOperationJournal.Entry root=journal.preview(c,id,actor,ns(key),activity.resource(s)+":batch",id,operation,edit.sourceTaskId,fingerprint,edit.reason,state,p);return detail(c,s,root,1,20);
        });
    }
    public Object previewClear(String key,Edit edit){return preview(key,edit,"clear",null,(c,s,actor,root,digest,count)->{long previous=-1;while(true){List<Map<String,Object>> page=activity.batchRows(c,s,previous);if(page.isEmpty())break;for(Map<String,Object> before:page){code(before);activity.validate(s,before,before,false);ManagedOperationJournal.Entry e=add(c,s,root,actor,"clear",edit.reason,before,null);digest(digest,e);count[0]++;previous=code(before);}}});}
    public Object previewImport(String key,Edit edit,InputStream input)throws IOException{
        // Upload completes outside native locks. Only immutable per-row journal plans survive preview.
        journal.admin();activity.equipment(key);request(edit,"import-upsert",null);Path dir=Files.createTempDirectory("dof-equipment-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));Path file=dir.resolve("rules.json");
        try{
            if(Files.getFileStore(dir).getUsableSpace()<2L*1024*1024*1024+NativeEquipmentDataset.MAX_UPLOAD)throw fail(507,"临时空间不足，保留2 GiB基础余量及完整上传峰值，未创建任务");
            try(OutputStream out=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));byte[] buffer=new byte[65536];long size=0;int n;while((n=input.read(buffer))!=-1){size+=n;if(size>NativeEquipmentDataset.MAX_UPLOAD)throw fail(413,"规则文件超过256 MiB");if(Files.getFileStore(dir).getUsableSpace()<2L*1024*1024*1024+buffer.length)throw fail(507,"临时空间不足，上传已停止");out.write(buffer,0,n);}}
            Map<String,Object> checked;try(InputStream in=Files.newInputStream(file)){checked=activity.checkDataset(key,in);}
            return preview(key,edit,"import-upsert",(String)checked.get("revision"),(c,s,actor,root,digest,count)->{try(InputStream in=Files.newInputStream(file)){NativeEquipmentDataset.inspect(activity.batchSpec(c,s),in,row->{try{
                long id=code(row);Map<String,Object> before=activity.current(c,s,id),after=activity.validate(s,row,before,before==null);ManagedOperationJournal.Entry e=add(c,s,root,actor,"import-upsert",edit.reason,before,after);digest(digest,e);count[0]++;
            }catch(SQLException e){throw new IOException("规则计划尚未持久化",e);}});}});
        }finally{try{Files.deleteIfExists(file);}finally{Files.deleteIfExists(dir);}}
    }
    public Object previewRestore(String key,Edit edit){return preview(key,edit,"restore",null,(c,s,actor,id,digest,count)->{
        ManagedOperationJournal.Entry source=parent(c,s,edit.sourceTaskId);if(!Arrays.asList("PERSISTED","NOT_APPLIED").contains(source.state))throw fail(409,"原批量任务未结束，请先核对或取消未提交项");summary(c,s,source);long previous=-1;
        while(true){List<ManagedOperationJournal.Entry> page=children(c,s,source,previous,100);if(page.isEmpty())break;for(ManagedOperationJournal.Entry e:page){previous=Long.parseLong(e.target);if(Arrays.asList("PREVIEWED","PREPARED","CONFLICT").contains(e.state))throw fail(409,"原任务仍有未决子项，先核对后再恢复");if(!Arrays.asList("PERSISTED","NO_CHANGE").contains(e.state))continue;ActivityConfigService.Change change=activity.batchRestore(c,s,e);ManagedOperationJournal.Entry saved=add(c,s,id,actor,"restore",edit.reason,change.before,change.after);digest(digest,saved);count[0]++;}}
    });}
    private void expected(ManagedOperationJournal.Entry root,Map<String,Object> sum,String revision){revision(revision);if(!version(root,sum).equals(revision))throw fail(409,"任务版本已改变，请读取原任务，不要更换编号重做");}
    private boolean unfinished(Map<String,Object> sum){Map<String,Object> counts=map(sum.get("counts"));return counts.containsKey("PREVIEWED")||counts.containsKey("PREPARED")||counts.containsKey("CONFLICT");}
    private ManagedOperationJournal.Entry finish(Connection c,ActivityConfigService.Section s,ManagedOperationJournal.Entry root,String outcome)throws Exception{
        Map<String,Object> sum=summary(c,s,root),p=new TreeMap<>();p.put("plan",plan(root));p.put("summary",sum);p.put("outcome",map(sum.get("counts")).containsKey("NOT_APPLIED")||map(sum.get("counts")).containsKey("ACKNOWLEDGED")?"PARTIAL_RESULTS_RECORDED":outcome);p.put("nativeConsumerVerified",false);p.put("moduleSnapshotAtFinish",activity.batchState(c,s));return journal.finishMetadataTask(c,root,p,journal.admin());
    }
    public Object step(String key,String id,String revision){return run(key,false,(c,s,actor)->{
        ManagedOperationJournal.Entry root=parent(c,s,id);Map<String,Object> sum=summary(c,s,root);expected(root,sum,revision);if(root.actor!=actor)throw fail(403,"只能由原操作者继续批量任务；其他管理员可明确取消后建立恢复任务");
        if(!Arrays.asList("PREVIEWED","PREPARED").contains(root.state))return detail(c,s,root,1,20);guardActive(c,journal,key,root.requestId);
        if(root.state.equals("PREVIEWED")){if(!Objects.equals(activity.batchState(c,s).get("revision"),map(plan(root).get("request")).get("revision")))throw fail(409,"原生全表在预览后变化，请取消原预览并重新核对");root=journal.prepare(c,root,root.target,decode(root.beforeJson),plan(root));}
        long previous=-1;int performed=0;
        outer:while(performed<20){List<ManagedOperationJournal.Entry> page=children(c,s,root,previous,100);if(page.isEmpty())break;for(ManagedOperationJournal.Entry e:page){previous=Long.parseLong(e.target);if(e.state.equals("PREPARED")||e.state.equals("CONFLICT"))break outer;if(!e.state.equals("PREVIEWED"))continue;
            Map<String,Object> before=activity.current(c,s,Long.valueOf(e.target)),after=activity.snapshot(s,e.afterJson);if(!journal.hash(before).equals(e.beforeHash))throw fail(409,"任务子项当前配置与预览不同；已完成项保持，请读取任务并取消剩余项后核对");
            e=journal.prepare(c,e,e.target,before,after);if(e.beforeHash.equals(e.afterHash))journal.finish(c,e,"NO_CHANGE",actor);else{ActivityConfigService.Change change=new ActivityConfigService.Change();change.id=Long.valueOf(e.target);change.before=before;change.after=after;activity.write(c,s,change);journal.finish(c,e,"PERSISTED",actor);}if(++performed==20)break outer;
        }}
        sum=summary(c,s,root);if(!unfinished(sum))root=finish(c,s,root,"COMPLETED_CONFIG_WRITES");return detail(c,s,root,1,20);
    });}
    public Object reconcile(String key,String id,String child,String revision){return run(key,false,(c,s,actor)->{
        ManagedOperationJournal.Entry root=parent(c,s,id);expected(root,summary(c,s,root),revision);ManagedOperationJournal.Entry e=child(c,s,root,child);
        if(e.state.equals("PREPARED")){Map<String,Object> current=activity.current(c,s,Long.valueOf(e.target));String hash=journal.hash(current);journal.finish(c,e,hash.equals(e.afterHash)?(e.beforeHash.equals(e.afterHash)?"NO_CHANGE":"PERSISTED"):hash.equals(e.beforeHash)?"NOT_APPLIED":"CONFLICT",actor);}
        if(root.state.equals("PREPARED")&&!unfinished(summary(c,s,root)))root=finish(c,s,root,"RECONCILED_CONFIG_WRITES");return detail(c,s,root,1,20);
    });}
    public Object control(String key,String id,Control edit,boolean commit){if(edit==null||edit.sourceTaskId!=null)throw fail(400,"缺少任务控制内容或包含不适用的来源字段");if(edit.childRequestId!=null)edit.childRequestId=ManagedOperationJournal.id(edit.childRequestId);ManagedOperationJournal.id(edit.requestId);revision(edit.revision);reason(edit.reason);if(!Arrays.asList("cancel","accept-current").contains(edit.operation))throw fail(400,"未知任务控制");
        Map<String,Object> req=new TreeMap<>();req.put("parent",ManagedOperationJournal.id(id));req.put("operation",edit.operation);req.put("revision",edit.revision);req.put("reason",edit.reason);req.put("child",edit.childRequestId);req.put("currentRevision",edit.currentRevision);String fingerprint=journal.hash(req);
        return run(key,true,(c,s,actor)->{
            ManagedOperationJournal.Entry root=parent(c,s,id),control=journal.find(c,edit.requestId);if(control!=null){journal.same(control,controls(key),actor,fingerprint);if(!control.state.equals("PREVIEWED")||!commit)return journal.result(control);}
            expected(root,summary(c,s,root),edit.revision);ManagedOperationJournal.Entry leaf=null;Map<String,Object> current=null;
            if(edit.operation.equals("cancel")){if(edit.childRequestId!=null||edit.currentRevision!=null||!Arrays.asList("PREVIEWED","PREPARED").contains(root.state))throw fail(409,"只能取消未结束任务的未提交项");}
            else{leaf=child(c,s,root,edit.childRequestId);if(!leaf.state.equals("CONFLICT"))throw fail(409,"只能明确保留冲突子项的当前值");current=activity.current(c,s,Long.valueOf(leaf.target));if(!journal.hash(current).equals(edit.currentRevision))throw fail(409,"冲突子项的当前值已变化，请重读后确认");}
            Map<String,Object> proof=new TreeMap<>();proof.put("taskRevision",edit.revision);proof.put("current",current);
            if(!commit)return journal.result(journal.preview(c,edit.requestId,actor,controls(key),activity.resource(s)+":control",id,edit.operation,edit.childRequestId==null?id:edit.childRequestId,fingerprint,edit.reason,proof,proof));
            if(control==null)throw fail(404,"请先预览任务控制");control=journal.prepare(c,control,id,proof,proof);
            if(edit.operation.equals("cancel")){
                long previous=-1;while(true){List<ManagedOperationJournal.Entry> page=children(c,s,root,previous,100);if(page.isEmpty())break;for(ManagedOperationJournal.Entry e:page){previous=Long.parseLong(e.target);if(e.state.equals("PREVIEWED"))journal.cancelPreviewInTransaction(c,e,actor);}}
                if(root.state.equals("PREVIEWED"))journal.cancelPreviewInTransaction(c,root,actor);else{Map<String,Object> out=new TreeMap<>();out.put("plan",plan(root));out.put("summary",summary(c,s,root));out.put("outcome","STOPPED_UNSUBMITTED_ITEMS_ONLY");journal.finishMetadataTransaction(c,root,out,actor);}return journal.result(journal.finish(c,control,"NO_CHANGE",actor));
            }
            return journal.result(journal.acknowledge(c,leaf,control,actor));
        });
    }
    public Object controlHistory(String key,String id,int page,int size){return run(key,false,(c,s,actor)->{
        if(page<1||size<1||size>100)throw fail(400,"控制记录分页参数无效");ManagedOperationJournal.Entry root=parent(c,s,id);long total;
        String where=" FROM "+ManagedOperationJournal.TABLE+" WHERE instance_id=? AND namespace=? AND target_key=?";
        try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*)"+where)){p.setString(1,journal.instance());p.setString(2,controls(key));p.setString(3,root.requestId);try(ResultSet r=p.executeQuery()){if(!r.next())throw corrupt();total=r.getLong(1);}}
        List<String> ids=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT request_id"+where+" ORDER BY created_at DESC,request_id DESC LIMIT ? OFFSET ?")){p.setString(1,journal.instance());p.setString(2,controls(key));p.setString(3,root.requestId);p.setInt(4,size);p.setLong(5,(long)(page-1)*size);try(ResultSet r=p.executeQuery()){while(r.next())ids.add(r.getString(1));}}
        List<Object> rows=new ArrayList<>();for(String child:ids)rows.add(journal.result(journal.require(c,controls(key),child)));Map<String,Object> out=new TreeMap<>();out.put("rows",rows);out.put("total",total);out.put("page",page);out.put("pageSize",size);return out;
    });}
    public Object controlDetail(String key,String id,String controlId){return run(key,false,(c,s,actor)->{
        parent(c,s,id);ManagedOperationJournal.Entry e=journal.require(c,controls(key),controlId);if(!e.target.equals(ManagedOperationJournal.id(id)))throw corrupt();Map<String,Object> out=journal.result(e),proof=decode(e.beforeJson);Control request=new Control();request.requestId=e.requestId;request.revision=(String)proof.get("taskRevision");request.reason=e.reason;request.operation=e.operation;if(!ManagedOperationJournal.id(id).equals(e.targetRequest)){request.childRequestId=e.targetRequest;request.currentRevision=journal.hash(proof.get("current"));}Map<String,Object> fp=new TreeMap<>();fp.put("parent",ManagedOperationJournal.id(id));fp.put("operation",request.operation);fp.put("revision",request.revision);fp.put("reason",request.reason);fp.put("child",request.childRequestId);fp.put("currentRevision",request.currentRevision);if(!journal.hash(fp).equals(e.requestHash))throw corrupt();if(e.actor==actor&&e.state.equals("PREVIEWED"))out.put("request",request);return out;
    });}
    public Object item(String key,String id,String child){return run(key,false,(c,s,actor)->{ManagedOperationJournal.Entry root=parent(c,s,id);ManagedOperationJournal.Entry e=child(c,s,root,child);Map<String,Object> row=journal.result(e),current=activity.current(c,s,Long.valueOf(e.target));row.put("current",current);row.put("currentRevision",journal.hash(current));return row;});}
}
