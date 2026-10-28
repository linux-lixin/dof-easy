package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.sql.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Management of the existing website code pool. Secrets stay in native rows and explicit exports. */
@Service
public class ManagedCdkInventoryService {
    @Resource private ManagedOperationJournal journal;
    @Resource private ManagedPostalService postal;
    private final ManagedCdkSchema schema=new ManagedCdkSchema();
    private final ObjectMapper json=new ObjectMapper();
    private static final String TABLE="`dnf_service`.`recharge_key`",ISSUE="cdk.issue",EXPORT="cdk.export",PROTOCOL="cdk-inventory-v1",RESOURCE="dnf_service.recharge_key:inventory";
    public static class Draft {public JsonNode type,face,count;public String faceName,reason;}
    public static class Edit extends Draft {public String requestId,revision;}
    public static class KeyEdit {public String requestId,revision,operation,targetRequestId,reason;}
    public static class ExportEdit {public String requestId,revision,reason;}
    public static class Query {
        public Integer type,status;public int page=1,pageSize=20;
        @com.alibaba.fastjson.annotation.JSONField(serialize=false) @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) public String content;
    }
    public static class Download { @com.alibaba.fastjson.annotation.JSONField(serialize=false) @com.fasterxml.jackson.annotation.JsonIgnore public byte[] bytes;public String requestId,sha256; }
    private static class SecretRow {Map<String,Object> meta;String code,name,nameError;}
    private DofFailure failed(){return new DofFailure(503,"CDK管理结果尚未确认，请保留原操作编号并核对；不要换编号重做");}
    private Map<String,Object> map(Object value){if(!(value instanceof Map))throw new DofFailure(409,"CDK管理回执结构不兼容");return (Map<String,Object>)value;}
    private List<Map<String,Object>> rows(Object value){if(!(value instanceof List)||((List<?>)value).size()>1000)throw new DofFailure(409,"CDK批次明细不完整");return (List<Map<String,Object>>)value;}
    private Map<String,Object> decode(String value){try{return json.readValue(value,new TypeReference<Map<String,Object>>(){});}catch(java.io.IOException e){throw new DofFailure(409,"CDK管理回执损坏");}}
    private long n(Map<String,Object> m,String key){Object v=m.get(key);if(!(v instanceof Integer||v instanceof Long))throw new DofFailure(409,"CDK管理回执数值不完整");return ((Number)v).longValue();}
    private long number(JsonNode value,long min,long max,String label){if(value==null||!value.isIntegralNumber()||!value.canConvertToLong()||value.longValue()<min||value.longValue()>max)throw new DofFailure(400,label+"必须是 "+min+"～"+max+" 的整数");return value.longValue();}
    private String text(String value,int max,String label){if(value==null||value.trim().isEmpty()||value.length()>max||value.chars().anyMatch(c->c<32||Character.isSurrogate((char)c)))throw new DofFailure(400,label+"格式无效");return value;}
    private void revision(String value){if(value==null||!value.matches("[0-9a-f]{64}"))throw new DofFailure(400,"缺少CDK版本，请重新读取并预览");}
    private String keyNs(long id){if(id<1||id>Integer.MAX_VALUE)throw new DofFailure(400,"卡密记录编号无效");return "cdk.key."+id;}
    private void lock(Connection c)throws SQLException{
        try{journal.lock(c,TABLE+" WRITE,`d_taiwan`.`accounts` READ");schema.inspect(c);}catch(SQLException|RuntimeException e){try{postal.unlock(c);}catch(SQLException|RuntimeException cleanup){e.addSuppressed(cleanup);}throw e;}
    }
    private Map<String,Object> descriptor(Connection c)throws SQLException{
        Map<String,Object> d=new TreeMap<>();d.put("protocol",PROTOCOL);d.put("schema",schema.inspect(c));
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT @@server_uuid")){if(!r.next())throw failed();d.put("databaseUuid",r.getString(1));}return d;
    }
    private Map<String,Object> draft(Draft d){
        if(d==null)throw new DofFailure(400,"缺少制码内容");long type=number(d.type,0,1,"卡密类型"),face=number(d.face,1,type==0?9999999:Integer.MAX_VALUE,"面值或物品编号");
        Map<String,Object> out=new TreeMap<>();out.put("type",type);out.put("face",face);out.put("count",number(d.count,1,1000,"制码数量"));out.put("faceName",d.faceName);out.put("reason",text(d.reason,100,"制码原因"));return out;
    }
    private Map<String,Object> issueSnapshot(Connection c,Map<String,Object> d)throws SQLException{
        Map<String,Object> reward=new TreeMap<>();String name;
        if(n(d,"type")==1){Map<String,Object> item=new TreeMap<>();item.put("itemId",d.get("face"));item.put("quantity",1);reward=postal.frozenSendPlan(postal.mail(json.valueToTree(item)));name=String.valueOf(map(reward.get("attachment")).get("name"));}
        else{name=d.get("face")+" 点券";reward.put("cera",d.get("face"));}
        if(d.get("faceName")!=null)name=text((String)d.get("faceName"),189,"显示名称");text(name,189,"显示名称");String encoded=Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8));if(encoded.length()>255)throw new DofFailure(400,"显示名称的UTF-8编码过长，Base64后不得超过255字节");
        Map<String,Object> out=new TreeMap<>(descriptor(c));out.put("draft",d);out.put("reward",reward);out.put("encodedName",encoded);out.put("displayName",name);return out;
    }
    private String issueFingerprint(Edit e,Map<String,Object> d){ManagedOperationJournal.id(e.requestId);revision(e.revision);Map<String,Object> v=new TreeMap<>();v.put("draft",d);v.put("revision",e.revision);return journal.hash(v);}
    private ManagedOperationJournal.Entry issue(Connection c,String id)throws SQLException{
        ManagedOperationJournal.Entry e=journal.require(c,ISSUE,id);if(!e.operation.equals("create")||!e.resource.equals(RESOURCE)||!e.target.equals(e.requestId)||!PROTOCOL.equals(decode(e.beforeJson).get("protocol")))throw new DofFailure(409,"制码回执归属不匹配");return e;
    }
    private Map<String,Object> issueResult(ManagedOperationJournal.Entry e){Map<String,Object> out=journal.result(e);out.put("revision",e.beforeHash);Map<String,Object> before=decode(e.beforeJson);out.put("draft",before.get("draft"));out.put("reward",before.get("reward"));out.put("displayName",before.get("displayName"));out.put("generated",decode(e.afterJson).get("generated"));out.put("secretsHidden",true);return out;}
    public Object validate(Draft edit){long actor=journal.admin();Map<String,Object> d=draft(edit);try(Connection c=journal.connection()){lock(c);try{postal.actor(c,actor);Map<String,Object> snapshot=issueSnapshot(c,d),out=new TreeMap<>();out.put("revision",journal.hash(snapshot));out.put("snapshot",snapshot);return out;}finally{postal.unlock(c);}}catch(SQLException e){throw failed();}}
    public Object preview(Edit edit){
        long actor=journal.admin();Map<String,Object> d=draft(edit);String fp=issueFingerprint(edit,d);
        try(Connection c=journal.connection()){lock(c);try{postal.actor(c,actor);ManagedOperationJournal.Entry old=journal.find(c,edit.requestId);if(old!=null){journal.same(old,ISSUE,actor,fp);return issueResult(issue(c,edit.requestId));}
            journal.noPending(c,RESOURCE,null);Map<String,Object> snapshot=issueSnapshot(c,d);if(!journal.hash(snapshot).equals(edit.revision))throw new DofFailure(409,"制码定义、PVF或结构已改变，请重新预览");
            return issueResult(journal.preview(c,edit.requestId,actor,ISSUE,RESOURCE,ManagedOperationJournal.id(edit.requestId),"create",null,fp,(String)d.get("reason"),snapshot,snapshot));
        }finally{postal.unlock(c);}}catch(SQLException e){throw failed();}
    }
    public Object save(Edit edit){
        long actor=journal.admin();Map<String,Object> d=draft(edit);String fp=issueFingerprint(edit,d);
        try(Connection c=journal.connection()){boolean discarded=false;lock(c);try{
            postal.actor(c,actor);ManagedOperationJournal.Entry e=issue(c,edit.requestId);journal.same(e,ISSUE,actor,fp);if(!e.state.equals("PREVIEWED"))return issueResult(e);
            journal.noPending(c,RESOURCE,null);Map<String,Object> before=issueSnapshot(c,d);if(!journal.hash(before).equals(e.beforeHash))throw new DofFailure(409,"制码预览已过期，未创建卡密");e=journal.prepare(c,e,e.target,before,before);
            c.setAutoCommit(false);lock(c);
            try{
                postal.actor(c,actor);e=issue(c,edit.requestId);if(!e.state.equals("PREPARED")){c.rollback();return issueResult(e);}
                if(!journal.hash(issueSnapshot(c,d)).equals(e.beforeHash)){e=journal.finish(c,e,"NOT_APPLIED",actor);c.commit();return issueResult(e);}
                List<Object> generated=new ArrayList<>();for(int index=0;index<n(d,"count");index++){
                    String code=UUID.randomUUID().toString().toUpperCase(Locale.ROOT);long id;
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO "+TABLE+" (content,type,face,face_name,status,use_uid,use_account,create_time,use_time) VALUES(?,?,?,?,0,NULL,NULL,NOW(),NULL)",Statement.RETURN_GENERATED_KEYS)){
                        p.setString(1,code);p.setLong(2,n(d,"type"));p.setLong(3,n(d,"face"));p.setString(4,(String)before.get("encodedName"));if(p.executeUpdate()!=1)throw new SQLException("CDK insert count");try(ResultSet r=p.getGeneratedKeys()){if(!r.next()||(id=r.getLong(1))<1||r.next())throw new SQLException("CDK identifier missing");}
                    }
                    SecretRow row=row(c,id);if(!row.code.equals(code)||n(row.meta,"type")!=n(d,"type")||n(row.meta,"face")!=n(d,"face")||!Objects.equals(row.name,before.get("displayName"))||!unused(row.meta)||claims(c,id).size()!=0)throw new SQLException("CDK inserted definition differs");generated.add(definition(row.meta));
                }
                if(!journal.hash(issueSnapshot(c,d)).equals(e.beforeHash))throw new SQLException("CDK issue source changed during batch");Map<String,Object> outcome=new TreeMap<>();outcome.put("plan",before);outcome.put("generated",generated);e=journal.finishWithGeneratedOutcome(c,e,outcome,actor);c.commit();return issueResult(e);
            }catch(SQLException|RuntimeException failure){try{c.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);discarded=true;postal.discard(c,failure);}throw failed();}
        }finally{if(!discarded&&!c.isClosed()){postal.unlock(c);if(!c.getAutoCommit())c.setAutoCommit(true);}}}catch(SQLException e){throw failed();}
    }
    private SecretRow row(Connection c,long id)throws SQLException{
        keyNs(id);try(PreparedStatement p=c.prepareStatement("SELECT id,content,type,face,face_name,status,use_uid,use_account,CAST(create_time AS CHAR),CAST(use_time AS CHAR) FROM "+TABLE+" WHERE id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new DofFailure(404,"卡密记录不存在");SecretRow x=readRow(r);if(r.next())throw new DofFailure(409,"卡密编号不唯一");return x;}}
    }
    private SecretRow readRow(ResultSet r)throws SQLException{
        SecretRow out=new SecretRow();out.code=r.getString(2);String encoded=r.getString(5);if(out.code==null||r.getString(9)==null)throw new DofFailure(409,"卡密身份字段不完整");
        Map<String,Object> m=new TreeMap<>();m.put("id",r.getLong(1));m.put("codeHash",journal.hash(Collections.singletonMap("code",out.code)));m.put("type",r.getInt(3));m.put("face",r.getInt(4));m.put("nameHash",journal.hash(Collections.singletonMap("name",encoded)));m.put("status",r.getInt(6));m.put("useUid",r.getObject(7));m.put("useAccount",r.getString(8));m.put("createdAt",r.getString(9));m.put("usedAt",r.getString(10));out.meta=m;
        try{if(encoded==null)throw new IllegalArgumentException();out.name=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Base64.getDecoder().decode(encoded))).toString();}catch(IllegalArgumentException|CharacterCodingException invalid){out.nameError="名称不是已知Base64/UTF-8格式，未修改原字段";}
        return out;
    }
    private Map<String,Object> definition(Map<String,Object> m){Map<String,Object> out=new TreeMap<>();for(String key:Arrays.asList("id","codeHash","type","face","nameHash","createdAt"))out.put(key,m.get(key));return out;}
    private boolean unused(Map<String,Object> m){return n(m,"status")==0&&(m.get("useUid")==null||n(m,"useUid")==0)&&(m.get("useAccount")==null||m.get("useAccount").equals(""))&&m.get("usedAt")==null;}
    private List<Object> claims(Connection c,long id)throws SQLException{
        List<Object> rows=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT request_id FROM "+ManagedOperationJournal.TABLE+" WHERE instance_id=? AND namespace='cdk.use' AND target_key=? AND state IN ('PREVIEWED','PREPARED','CONFLICT','PERSISTED','ACKNOWLEDGED') ORDER BY request_id")){p.setString(1,journal.instance());p.setString(2,Long.toString(id));try(ResultSet r=p.executeQuery()){while(r.next())rows.add(ManagedPostalReceiptService.snapshot(journal.require(c,"cdk.use",r.getString(1))));}}return rows;
    }
    private Map<String,Object> keySnapshot(Connection c,long id)throws SQLException{
        Map<String,Object> out=new TreeMap<>(descriptor(c));out.put("key",row(c,id).meta);out.put("claims",claims(c,id));
        try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM "+ManagedOperationJournal.TABLE+" WHERE instance_id=? AND namespace=? AND state='PERSISTED'")){p.setString(1,journal.instance());p.setString(2,keyNs(id));try(ResultSet r=p.executeQuery()){if(!r.next())throw failed();out.put("generation",r.getLong(1));}}return out;
    }
    private String pausedBy(Connection c,long id,Map<String,Object> snapshot)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT request_id FROM "+ManagedOperationJournal.TABLE+" WHERE instance_id=? AND namespace=? AND operation_name='pause' AND state='PERSISTED' AND after_hash=?")){p.setString(1,journal.instance());p.setString(2,keyNs(id));p.setString(3,journal.hash(snapshot));try(ResultSet r=p.executeQuery()){if(!r.next())return null;String source=r.getString(1);if(r.next())throw new DofFailure(409,"停用来源不唯一");return source;}}
    }
    private Map<String,Object> view(Connection c,long id)throws SQLException{
        Map<String,Object> snapshot=keySnapshot(c,id),m=map(snapshot.get("key"));SecretRow row=row(c,id);Map<String,Object> out=new TreeMap<>(m);out.remove("codeHash");out.remove("nameHash");out.put("faceName",row.name);out.put("nameError",row.nameError);out.put("revision",journal.hash(snapshot));String pause=pausedBy(c,id,snapshot);out.put("pauseRequestId",pause);
        String state=!((List<?>)snapshot.get("claims")).isEmpty()?"reserved_or_consumed":pause!=null?"paused":unused(m)?"available":n(m,"status")==1?"used_or_unavailable":"inconsistent";out.put("availability",state);out.put("secretsHidden",true);return out;
    }
    public Object query(Query query){
        long actor=journal.admin();if(query==null||query.page<1||query.page>10000||query.pageSize<1||query.pageSize>100||query.type!=null&&!Arrays.asList(0,1).contains(query.type)||query.status!=null&&!Arrays.asList(0,1).contains(query.status))throw new DofFailure(400,"CDK查询条件或页码无效");
        if(query.content!=null)text(query.content,255,"卡密查询");
        try(Connection c=journal.connection()){lock(c);try{
            postal.actor(c,actor);String where=" WHERE 1=1";List<Object> values=new ArrayList<>();if(query.type!=null){where+=" AND type=?";values.add(query.type);}if(query.status!=null){where+=" AND status=?";values.add(query.status);}if(query.content!=null){where+=" AND BINARY content=?";values.add(query.content);}
            long total;try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM "+TABLE+where)){bind(p,values);try(ResultSet r=p.executeQuery()){if(!r.next())throw failed();total=r.getLong(1);}}
            List<Long> ids=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT id FROM "+TABLE+where+" ORDER BY id DESC LIMIT ? OFFSET ?")){bind(p,values);p.setInt(values.size()+1,query.pageSize);p.setInt(values.size()+2,(query.page-1)*query.pageSize);try(ResultSet r=p.executeQuery()){while(r.next())ids.add(r.getLong(1));}}
            List<Object> list=new ArrayList<>();for(long id:ids)list.add(view(c,id));Map<String,Object> out=new TreeMap<>();out.put("page",query.page);out.put("pageSize",query.pageSize);out.put("totalSize",total);out.put("totalPageSize",(total+query.pageSize-1)/query.pageSize);out.put("list",list);out.put("secretsHidden",true);out.put("protocol",PROTOCOL);return out;
        }finally{postal.unlock(c);}}catch(SQLException e){throw failed();}
    }
    private void bind(PreparedStatement p,List<Object> values)throws SQLException{for(int index=0;index<values.size();index++)p.setObject(index+1,values.get(index));}
    public Object keyRead(long id){long actor=journal.admin();try(Connection c=journal.connection()){lock(c);try{postal.actor(c,actor);return view(c,id);}finally{postal.unlock(c);}}catch(SQLException e){throw failed();}}
    private ManagedOperationJournal.Entry keyEntry(Connection c,long id,String request)throws SQLException{
        ManagedOperationJournal.Entry e=journal.require(c,keyNs(id),request);if(!e.target.equals(Long.toString(id))||!e.resource.equals("dnf_service.recharge_key:"+id)||!Arrays.asList("pause","resume").contains(e.operation)||!PROTOCOL.equals(decode(e.beforeJson).get("protocol")))throw new DofFailure(409,"卡密管理回执归属不匹配");return e;
    }
    private String keyFingerprint(long id,KeyEdit edit){keyNs(id);if(edit==null)throw new DofFailure(400,"缺少卡密管理请求");ManagedOperationJournal.id(edit.requestId);revision(edit.revision);text(edit.reason,100,"操作原因");if(!Arrays.asList("pause","resume").contains(edit.operation))throw new DofFailure(400,"未知卡密管理动作");if(edit.operation.equals("resume"))ManagedOperationJournal.id(edit.targetRequestId);else if(edit.targetRequestId!=null)throw new DofFailure(400,"停用不能关联其他操作");Map<String,Object> v=new TreeMap<>();v.put("id",id);v.put("revision",edit.revision);v.put("operation",edit.operation);v.put("source",edit.targetRequestId);v.put("reason",edit.reason);return journal.hash(v);}
    private Map<String,Object> keyPlan(Connection c,long id,KeyEdit edit,Map<String,Object> before)throws SQLException{
        if(!journal.hash(before).equals(edit.revision))throw new DofFailure(409,"卡密定义、占用或版本已改变，请重新读取");Map<String,Object> key=map(before.get("key"));if(!((List<?>)before.get("claims")).isEmpty())throw new DofFailure(409,"该码已有保留、兑换或未知回执，不能停用或恢复");
        if(edit.operation.equals("pause")){if(!unused(key))throw new DofFailure(409,"只能停用未使用且未被占用的码");}
        else{ManagedOperationJournal.Entry source=keyEntry(c,id,edit.targetRequestId);if(!source.operation.equals("pause")||!source.state.equals("PERSISTED")||!source.afterHash.equals(journal.hash(before)))throw new DofFailure(409,"只能恢复本后台停用且当前状态完全匹配的码，不能复活已使用码");}
        Map<String,Object> out=new TreeMap<>(before),afterKey=new TreeMap<>(key);afterKey.put("status",edit.operation.equals("pause")?1:0);out.put("key",afterKey);out.put("generation",n(before,"generation")+1);return out;
    }
    private Map<String,Object> keyResult(ManagedOperationJournal.Entry e){Map<String,Object> out=journal.result(e);out.put("revision",e.beforeHash);out.put("before",decode(e.beforeJson));out.put("after",decode(e.afterJson));out.put("rewardDataChanged",false);return out;}
    public Object keyPreview(long id,KeyEdit edit){long actor=journal.admin();String fp=keyFingerprint(id,edit);try(Connection c=journal.connection()){lock(c);try{postal.actor(c,actor);ManagedOperationJournal.Entry old=journal.find(c,edit.requestId);if(old!=null){journal.same(old,keyNs(id),actor,fp);return keyResult(keyEntry(c,id,edit.requestId));}journal.noPending(c,"dnf_service.recharge_key:"+id,null);Map<String,Object> before=keySnapshot(c,id),after=keyPlan(c,id,edit,before);return keyResult(journal.preview(c,edit.requestId,actor,keyNs(id),"dnf_service.recharge_key:"+id,Long.toString(id),edit.operation,edit.targetRequestId,fp,edit.reason,before,after));}finally{postal.unlock(c);}}catch(SQLException e){throw failed();}}
    public Object keySave(long id,KeyEdit edit){
        long actor=journal.admin();String fp=keyFingerprint(id,edit);try(Connection c=journal.connection()){boolean discarded=false;lock(c);try{
            postal.actor(c,actor);ManagedOperationJournal.Entry e=keyEntry(c,id,edit.requestId);journal.same(e,keyNs(id),actor,fp);if(!e.state.equals("PREVIEWED"))return keyResult(e);journal.noPending(c,e.resource,null);Map<String,Object> before=keySnapshot(c,id),after=keyPlan(c,id,edit,before);if(!journal.hash(before).equals(e.beforeHash)||!journal.hash(after).equals(e.afterHash))throw new DofFailure(409,"卡密控制预览已改变");e=journal.prepare(c,e,e.target,before,after);c.setAutoCommit(false);lock(c);
            try{postal.actor(c,actor);e=keyEntry(c,id,edit.requestId);if(!e.state.equals("PREPARED")){c.rollback();return keyResult(e);}if(!journal.hash(keySnapshot(c,id)).equals(e.beforeHash)){e=journal.finish(c,e,"NOT_APPLIED",actor);c.commit();return keyResult(e);}try(PreparedStatement p=c.prepareStatement("UPDATE "+TABLE+" SET status=? WHERE id=? AND status=?")){p.setInt(1,(int)n(map(after.get("key")),"status"));p.setLong(2,id);p.setInt(3,(int)n(map(before.get("key")),"status"));if(p.executeUpdate()!=1)throw new SQLException("CDK availability changed");}e=journal.finish(c,e,"PERSISTED",actor);if(!journal.hash(keySnapshot(c,id)).equals(e.afterHash))throw new SQLException("CDK control readback differs");c.commit();return keyResult(e);
            }catch(SQLException|RuntimeException failure){try{c.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);discarded=true;postal.discard(c,failure);}throw failed();}
        }finally{if(!discarded&&!c.isClosed()){postal.unlock(c);if(!c.getAutoCommit())c.setAutoCommit(true);}}}catch(SQLException e){throw failed();}
    }
    private Map<String,Object> currentBatch(Connection c,ManagedOperationJournal.Entry batch)throws SQLException{
        if(!batch.state.equals("PERSISTED"))throw new DofFailure(409,"只有已确认生成的批次才能导出");List<Object> current=new ArrayList<>();for(Map<String,Object> definition:rows(decode(batch.afterJson).get("generated"))){long id=n(definition,"id");SecretRow row=row(c,id);if(!journal.hash(definition(row.meta)).equals(journal.hash(definition)))throw new DofFailure(409,"批次卡密缺失或定义已改变，停止导出");Map<String,Object> item=new TreeMap<>(row.meta);item.put("displayName",row.name);item.put("availability",view(c,id).get("availability"));current.add(item);}Map<String,Object> out=new TreeMap<>(descriptor(c));out.put("batchId",batch.requestId);out.put("batchAfterHash",batch.afterHash);out.put("rows",current);return out;
    }
    public Object read(String request){long actor=journal.admin();try(Connection c=journal.connection()){lock(c);try{postal.actor(c,actor);ManagedOperationJournal.Entry e=issue(c,request);Map<String,Object> out=issueResult(e);if(e.state.equals("PERSISTED")){try{Map<String,Object> current=currentBatch(c,e);out.put("currentKeys",current.get("rows"));out.put("exportRevision",journal.hash(current));}catch(DofFailure unavailable){out.put("exportUnavailableReason",unavailable.getMessage());}}return out;}finally{postal.unlock(c);}}catch(SQLException e){throw failed();}}
    public Object history(){long actor=journal.admin();try(Connection c=journal.connection()){postal.actor(c,actor);List<Object> result=new ArrayList<>();for(ManagedOperationJournal.Entry e:journal.history(c,ISSUE))result.add(issueResult(issue(c,e.requestId)));return result;}catch(SQLException e){throw failed();}}
    public Object keyHistory(long id){long actor=journal.admin();try(Connection c=journal.connection()){postal.actor(c,actor);List<Object> result=new ArrayList<>();for(ManagedOperationJournal.Entry e:journal.history(c,keyNs(id)))result.add(keyResult(keyEntry(c,id,e.requestId)));return result;}catch(SQLException e){throw failed();}}
    public Object reconcile(String request){return reconcile(null,request);}
    public Object reconcile(Long key,String request){long actor=journal.admin();try(Connection c=journal.connection()){lock(c);try{postal.actor(c,actor);ManagedOperationJournal.Entry e=key==null?issue(c,request):keyEntry(c,key,request);if(e.state.equals("PREPARED")){Map<String,Object> before=decode(e.beforeJson),current=descriptor(c);if(!Objects.equals(before.get("databaseUuid"),current.get("databaseUuid"))||!journal.hash(before.get("schema")).equals(journal.hash(current.get("schema"))))throw new DofFailure(409,"数据库或码池结构已改变，不能推断旧结果");e=journal.finish(c,e,"NOT_APPLIED",actor);}return key==null?issueResult(e):keyResult(e);}finally{postal.unlock(c);}}catch(SQLException e){throw failed();}}
    private ManagedOperationJournal.Entry exportEntry(Connection c,String batch,String request)throws SQLException{ManagedOperationJournal.Entry e=journal.require(c,EXPORT,request);if(!e.operation.equals("export")||!e.target.equals(ManagedOperationJournal.id(batch))||!e.target.equals(e.targetRequest)||!e.resource.equals("cdk.export:"+e.target))throw new DofFailure(409,"导出回执归属不匹配");return e;}
    private String csv(String value){if(value==null)value="";if(value.matches("^\\s*[=+@-].*"))value="'"+value;return "\""+value.replace("\"","\"\"")+"\"";}
    private byte[] csvBytes(Connection c,Map<String,Object> frozen)throws SQLException{
        StringBuilder out=new StringBuilder("\ufeff记录编号,卡密,类型,面值或物品编号,名称,导出时状态,批次\r\n");for(Map<String,Object> m:rows(frozen.get("rows"))){SecretRow nativeRow=row(c,n(m,"id"));if(!journal.hash(definition(nativeRow.meta)).equals(journal.hash(definition(m))))throw new DofFailure(409,"原批次卡密定义已改变，不能重建导出文件");out.append(csv(m.get("id").toString())).append(',').append(csv(nativeRow.code)).append(',').append(csv(m.get("type").toString())).append(',').append(csv(m.get("face").toString())).append(',').append(csv((String)m.get("displayName"))).append(',').append(csv((String)m.get("availability"))).append(',').append(csv((String)frozen.get("batchId"))).append("\r\n");}return out.toString().getBytes(StandardCharsets.UTF_8);
    }
    public Download export(String batch,ExportEdit edit){
        long actor=journal.admin();ManagedOperationJournal.id(batch);if(edit==null)throw new DofFailure(400,"缺少导出请求");ManagedOperationJournal.id(edit.requestId);revision(edit.revision);text(edit.reason,100,"导出原因");Map<String,Object> request=new TreeMap<>();request.put("batch",ManagedOperationJournal.id(batch));request.put("revision",edit.revision);request.put("reason",edit.reason);String fp=journal.hash(request);
        try(Connection c=journal.connection()){lock(c);try{postal.actor(c,actor);ManagedOperationJournal.Entry old=journal.find(c,edit.requestId);Map<String,Object> frozen;
            if(old!=null){journal.same(old,EXPORT,actor,fp);old=exportEntry(c,batch,edit.requestId);if(!Arrays.asList("PREVIEWED","PERSISTED").contains(old.state))throw new DofFailure(409,"导出回执待核对或未完成，请保留原编号");frozen=decode(old.beforeJson);}else{journal.noPending(c,"cdk.export:"+ManagedOperationJournal.id(batch),null);frozen=currentBatch(c,issue(c,batch));if(!journal.hash(frozen).equals(edit.revision))throw new DofFailure(409,"批次状态已改变，请重新读取后导出");old=journal.preview(c,edit.requestId,actor,EXPORT,"cdk.export:"+ManagedOperationJournal.id(batch),ManagedOperationJournal.id(batch),"export",batch,fp,edit.reason,frozen,frozen);}
            byte[] bytes=csvBytes(c,frozen);String sha=NativeUiState.sha256(bytes);if(old.state.equals("PREVIEWED")){old=journal.prepare(c,old,old.target,frozen,frozen);Map<String,Object> outcome=new TreeMap<>();outcome.put("sha256",sha);outcome.put("format","website-cdk-csv-v1");outcome.put("rows",rows(frozen.get("rows")).size());old=journal.finishMetadataTask(c,old,outcome,actor);}else if(!sha.equals(decode(old.afterJson).get("sha256")))throw new DofFailure(409,"导出内容与原回执不符，停止下载");Download result=new Download();result.bytes=bytes;result.requestId=old.requestId;result.sha256=sha;return result;
        }finally{postal.unlock(c);}}catch(SQLException e){throw failed();}
    }
    public Object exportHistory(){long actor=journal.admin();try(Connection c=journal.connection()){postal.actor(c,actor);List<Object> result=new ArrayList<>();for(ManagedOperationJournal.Entry e:journal.history(c,EXPORT)){Map<String,Object> out=journal.result(e);out.put("revision",e.beforeHash);out.put("artifact",e.state.equals("PERSISTED")?decode(e.afterJson):null);result.add(out);}return result;}catch(SQLException e){throw failed();}}
    public Object exportReconcile(String batch,String request){long actor=journal.admin();try(Connection c=journal.connection()){lock(c);try{postal.actor(c,actor);ManagedOperationJournal.Entry e=exportEntry(c,batch,request);if(e.state.equals("PREPARED"))e=journal.finish(c,e,"NOT_APPLIED",actor);return journal.result(e);}finally{postal.unlock(c);}}catch(SQLException e){throw failed();}}
}
