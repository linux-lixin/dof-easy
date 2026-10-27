package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.entity.common.*;
import com.aiyi.game.dnfserver.entity.equipment.Equipment;
import com.aiyi.game.dnfserver.entity.equipment.EquipmentType;
import com.aiyi.game.dnfserver.pvf.PvfManager;
import com.aiyi.game.dnfserver.pvf.ResourceIndex;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Administrative mail issuer only. Internal CDK/task issuers retain their own service. */
@Service
public class ManagedPostalService {
    @Resource private ManagedOperationJournal journal;
    @Resource private PvfManager pvf;
    private final ManagedPostalSchema schema=new ManagedPostalSchema();
    private final ObjectMapper json=new ObjectMapper();
    private static final String POSTAL="`taiwan_cain_2nd`.`postal`",LETTER="`taiwan_cain_2nd`.`letter`",ROLES="`taiwan_cain`.`charac_info`",ACCOUNTS="`d_taiwan`.`accounts`";
    private static final String PROTOCOL="postal-generated-id-v1";
    public static class Edit {public JsonNode receiveCharacNo,mail;public String requestId,revision,operation,targetRequestId,reason;}
    private String namespace(long id){return "postal."+id;}
    private String resource(long id){return "taiwan_cain_2nd.postal:"+id;}
    private void target(long id){if(id<1||id>Integer.MAX_VALUE)throw new DofFailure(400,"收件角色编号无效");}
    public long recipient(Edit e){if(e==null)throw new DofFailure(400,"缺少邮件请求");long id=number(e.receiveCharacNo,1,Integer.MAX_VALUE,"收件角色",false,0);target(id);return id;}
    private long number(JsonNode v,long min,long max,String label,boolean optional,long fallback){
        if(v==null&&optional)return fallback;
        if(v==null||!v.isIntegralNumber()||!v.canConvertToLong()||v.longValue()<min||v.longValue()>max)throw new DofFailure(400,label+"必须为 "+min+"～"+max+" 的整数");return v.longValue();
    }
    private String text(String value,int max,String label,boolean allowLines){
        if(value==null||value.codePointCount(0,value.length())>max||value.codePoints().anyMatch(x->x>65535||Character.isSurrogate((char)x)||(x<32&&!(allowLines&&(x==9||x==10||x==13)))))
            throw new DofFailure(400,label+"最多 "+max+" 字，不能包含无效字符或表情代理码");return value;
    }
    Map<String,Object> mail(JsonNode node){
        if(node==null||!node.isObject())throw new DofFailure(400,"缺少固定邮件内容");
        Set<String> allowed=new HashSet<>(Arrays.asList("itemId","quantity","upgrade","seperateUpgrade","amplifyOption","amplifyValue","sealFlag","gold","message"));
        node.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw new DofFailure(400,"不支持的邮件字段："+k);});
        Map<String,Object> out=new TreeMap<>();
        long item=number(node.get("itemId"),0,Integer.MAX_VALUE,"物品编号",true,0);
        out.put("itemId",item);out.put("quantity",number(node.get("quantity"),item==0?0:1,Integer.MAX_VALUE,"物品数量",true,item==0?0:1));
        out.put("upgrade",number(node.get("upgrade"),0,31,"强化等级",true,0));
        out.put("seperateUpgrade",number(node.get("seperateUpgrade"),0,31,"锻造等级",true,0));
        out.put("amplifyOption",number(node.get("amplifyOption"),0,4,"增幅属性",true,0));
        out.put("amplifyValue",number(node.get("amplifyValue"),0,65535,"增幅数值",true,0));
        out.put("gold",number(node.get("gold"),0,Integer.MAX_VALUE,"邮件金币",true,0));
        JsonNode seal=node.get("sealFlag");if(seal!=null&&!seal.isBoolean())throw new DofFailure(400,"封装开关必须为布尔值");out.put("sealFlag",seal!=null&&seal.booleanValue());
        JsonNode body=node.get("message");if(body!=null&&!body.isTextual())throw new DofFailure(400,"邮件正文必须为文本");out.put("message",text(body==null?"":body.textValue(),255,"邮件正文",true));
        if(item==0&&(n(out,"quantity")!=0||equipmentOptions(out)))throw new DofFailure(400,"无物品邮件不能携带数量或装备参数");
        if(n(out,"amplifyOption")==0&&n(out,"amplifyValue")!=0)throw new DofFailure(400,"未选增幅属性时增幅值必须为零");
        if(item==0&&n(out,"gold")==0&&out.get("message").equals(""))throw new DofFailure(400,"邮件须包含物品、金币或正文");return out;
    }
    private boolean equipmentOptions(Map<String,Object> m){return n(m,"upgrade")!=0||n(m,"seperateUpgrade")!=0||n(m,"amplifyOption")!=0||n(m,"amplifyValue")!=0||Boolean.TRUE.equals(m.get("sealFlag"));}
    private long n(Map<String,Object> m,String key){return ((Number)m.get(key)).longValue();}
    private Map<String,Object> validate(long id,Edit e){
        if(recipient(e)!=id)throw new DofFailure(400,"请求收件角色与路径不一致");ManagedOperationJournal.id(e.requestId);
        if(e.revision==null||!e.revision.matches("[0-9a-f]{64}"))throw new DofFailure(400,"缺少收件身份版本，请重新读取");
        if(!Arrays.asList("send","accept-current").contains(e.operation))throw new DofFailure(400,"未知邮件操作");
        if(e.reason==null||e.reason.trim().isEmpty())throw new DofFailure(400,"请填写操作原因");text(e.reason,100,"操作原因",false);
        if(e.operation.equals("send")){if(e.targetRequestId!=null)throw new DofFailure(400,"普通发送不能关联历史操作");return mail(e.mail);}
        ManagedOperationJournal.id(e.targetRequestId);if(e.mail!=null&&!e.mail.isNull())throw new DofFailure(400,"结果不明确认不能携带新邮件");return null;
    }
    private String fingerprint(long id,Edit e,Map<String,Object> mail){
        Map<String,Object> out=new TreeMap<>();out.put("recipient",id);out.put("revision",e.revision);out.put("operation",e.operation);out.put("reason",e.reason);out.put("mail",mail);out.put("targetRequestId",e.targetRequestId==null?null:ManagedOperationJournal.id(e.targetRequestId));return journal.hash(out);
    }
    private DofFailure uncertain(){return new DofFailure(503,"邮件结果尚未确认，请保留原操作编号并核对历史，不要换编号重发");}
    private void discard(Connection c,Throwable failure){try{Connection physical=c.unwrap(Connection.class);if(physical==null)throw new SQLException("No physical connection");physical.close();}catch(SQLException|RuntimeException ex){failure.addSuppressed(ex);try{c.abort(Runnable::run);}catch(SQLException|RuntimeException abort){failure.addSuppressed(abort);}}}
    private void unlock(Connection c)throws SQLException{try{journal.unlock(c);}catch(SQLException|RuntimeException e){discard(c,e);throw e;}}
    private void lock(Connection c)throws SQLException{
        try{journal.lock(c,POSTAL+" WRITE,"+LETTER+" WRITE,"+ROLES+" READ,"+ACCOUNTS+" READ");schema.inspect(c,journal);}
        catch(SQLException|RuntimeException e){try{unlock(c);}catch(SQLException|RuntimeException cleanup){e.addSuppressed(cleanup);}throw e;}
    }
    private void actor(Connection c,long who)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT admin FROM "+ACCOUNTS+" WHERE UID=?")){p.setLong(1,who);try(ResultSet r=p.executeQuery()){if(!r.next()||r.getInt(1)==0||r.wasNull()||r.next())throw new DofFailure(403,"管理员权限已改变，停止发送");}}
    }
    private Map<String,Object> before(Connection c,long id)throws SQLException{
        Map<String,Object> descriptor=schema.inspect(c,journal),out=new TreeMap<>();out.put("protocol",PROTOCOL);out.put("recipient",id);out.put("schemaRevision",journal.hash(descriptor));out.put("atomicReceipt",descriptor.get("atomicReceipt"));
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT @@server_uuid")){if(!r.next())throw uncertain();out.put("databaseUuid",r.getString(1));}
        long uid;
        try(PreparedStatement p=c.prepareStatement("SELECT m_id,HEX(charac_name),delete_flag,CAST(create_time AS CHAR) FROM "+ROLES+" WHERE charac_no=?")){
            p.setLong(1,id);try(ResultSet r=p.executeQuery()){
                if(!r.next())throw new DofFailure(404,"收件角色不存在");uid=r.getLong(1);if(r.wasNull()||uid<=0)throw new DofFailure(409,"收件角色账号身份不完整");
                if(r.getString(2)==null||r.getString(2).isEmpty()||r.getString(4)==null)throw new DofFailure(409,"收件角色身份字段缺失");
                out.put("uid",uid);out.put("nameHex",r.getString(2));out.put("createdAt",r.getString(4));
                if(r.getObject(3)==null||r.getInt(3)!=0||r.next())throw new DofFailure(409,"收件角色已删除或身份不唯一");
            }
        }
        try(PreparedStatement p=c.prepareStatement("SELECT HEX(accountname) FROM "+ACCOUNTS+" WHERE UID=?")){p.setLong(1,uid);try(ResultSet r=p.executeQuery()){if(!r.next()||r.getString(1)==null)throw new DofFailure(409,"收件角色所属账号不存在");out.put("accountHex",r.getString(1));if(r.next())throw uncertain();}}
        try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM "+ManagedOperationJournal.TABLE+" WHERE instance_id=? AND resource_key=? AND state IN ('PERSISTED','NO_CHANGE','ACKNOWLEDGED')")){
            p.setString(1,journal.instance());p.setString(2,resource(id));try(ResultSet r=p.executeQuery()){if(!r.next())throw uncertain();out.put("generation",r.getLong(1));}
        }return out;
    }
    private Map<String,Object> attachment(Map<String,Object> mail){
        int id=(int)n(mail,"itemId");if(id==0)return null;
        try{
            PvfManager.VerifiedMailItem checked=pvf.verifiedMailItem(id);String hash=checked.sha256,kind=checked.kind;Item found=checked.item;
            if(kind.equals("equipment")){
                EquipmentType type=found instanceof Equipment?((Equipment)found).getEquipmentType():null;
                if(type==null||type.isAvatar()||Arrays.asList(EquipmentType.creature,EquipmentType.aartifactRed,EquipmentType.aartifactGreen,EquipmentType.aartifactBlue).contains(type))throw new DofFailure(409,"时装、宠物及宠物装备的原生邮件标记尚待核实，当前不按普通装备发送");
                if(n(mail,"quantity")!=1)throw new DofFailure(400,"每封装备邮件数量必须为1");
                if(Boolean.TRUE.equals(mail.get("sealFlag"))&&(found.getRarity()>=5||!(found.getAttachType()==AttachType.sealing||found.getAttachType()==AttachType.sealingTrade)))throw new DofFailure(400,"绑定PVF不允许此装备封装");
            }else if(equipmentOptions(mail)||found.getStackLimit()<1||n(mail,"quantity")>found.getStackLimit())throw new DofFailure(400,"道具不能携带装备参数，数量须在绑定PVF堆叠上限内");
            if(!hash.equals(ResourceIndex.hash(pvf.sourceFile())))throw new DofFailure(409,"PVF在检查期间改变，请重新预览");
            Map<String,Object> out=new TreeMap<>();out.put("pvfSha256",hash);out.put("kind",kind);out.put("name",found.getName());out.put("rarity",found.getRarity());out.put("stackLimit",found.getStackLimit());return out;
        }catch(DofFailure e){throw e;}catch(Exception e){throw new DofFailure(409,"绑定PVF或索引不可用，未发送物品；请先核对资源");}
    }
    private Map<String,Object> decode(String value){try{return json.readValue(value,new TypeReference<Map<String,Object>>(){});}catch(java.io.IOException e){throw new DofFailure(409,"邮件回执损坏，停止核对");}}
    private ManagedOperationJournal.Entry source(Connection c,long id,String request)throws SQLException{
        ManagedOperationJournal.Entry e=journal.require(c,namespace(id),request);
        if(!resource(id).equals(e.resource)||!Long.toString(id).equals(e.target)||!PROTOCOL.equals(decode(e.beforeJson).get("protocol"))||!Arrays.asList("send","accept-current").contains(e.operation))throw new DofFailure(409,"邮件回执不属于当前收件角色或协议");return e;
    }
    private Map<String,Object> plan(Connection c,long id,Edit e,Map<String,Object> mail,Map<String,Object> state)throws SQLException{
        if(!journal.hash(state).equals(e.revision))throw new DofFailure(409,"收件身份、结构或发送版本已改变，请重新读取和预览");
        if(e.operation.equals("accept-current")){
            if(!source(c,id,e.targetRequestId).state.equals("CONFLICT"))throw new DofFailure(409,"只能确认结果不明的邮件操作");return state;
        }
        Map<String,Object> out=new TreeMap<>();out.put("protocol",PROTOCOL);out.put("mail",mail);out.put("attachment",attachment(mail));out.put("sender","DNF Manager");out.put("bodyEncoding","database-utf8-consumer-unverified");return out;
    }
    private Map<String,Object> result(ManagedOperationJournal.Entry e){
        Map<String,Object> out=journal.result(e);out.put("revision",e.beforeHash);out.put("recipient",e.target);out.put("before",decode(e.beforeJson));out.put("outcome",decode(e.afterJson));out.put("actualReceiptVerified",false);return out;
    }
    public Object status(long id){
        long who=journal.admin();target(id);try(Connection c=journal.connection()){lock(c);try{actor(c,who);Map<String,Object> state=before(c,id),out=new TreeMap<>(state);out.put("revision",journal.hash(state));out.put("instanceId",journal.instance());out.put("actor",who);return out;}finally{unlock(c);}}catch(SQLException e){throw uncertain();}
    }
    public Object preview(long id,Edit edit){
        long who=journal.admin();target(id);Map<String,Object> mail=validate(id,edit);String fingerprint=fingerprint(id,edit,mail);
        try(Connection c=journal.connection()){lock(c);try{
            actor(c,who);ManagedOperationJournal.Entry old=journal.find(c,edit.requestId);
            if(old!=null){journal.same(old,namespace(id),who,fingerprint);return result(source(c,id,edit.requestId));}
            journal.noPending(c,resource(id),edit.operation.equals("accept-current")?ManagedOperationJournal.id(edit.targetRequestId):null);
            Map<String,Object> state=before(c,id),after=plan(c,id,edit,mail,state);
            return result(journal.preview(c,edit.requestId,who,namespace(id),resource(id),Long.toString(id),edit.operation,edit.targetRequestId,fingerprint,edit.reason,state,after));
        }finally{unlock(c);}}catch(SQLException e){throw uncertain();}
    }
    private long insert(Connection c,String sql,Object... args)throws SQLException{
        try(PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);if(p.executeUpdate()!=1)throw new SQLException("Mail insert count");
            try(ResultSet r=p.getGeneratedKeys()){if(!r.next())throw new SQLException("Missing mail identifier");long id=r.getLong(1);if(id<=0||r.next())throw new SQLException("Invalid mail identifier");return id;}
        }
    }
    private Map<String,Object> insertMail(Connection c,Map<String,Object> plan,long id)throws SQLException{
        Map<String,Object> m=(Map<String,Object>)plan.get("mail");String body=(String)m.get("message");long letter=0;
        if(!body.isEmpty())letter=insert(c,"INSERT INTO "+LETTER+" (charac_no,send_charac_name,letter_text,reg_date) VALUES(?,'DNF Manager',?,NOW())",id,body);
        long postal=insert(c,"INSERT INTO "+POSTAL+" (occ_time,send_charac_name,receive_charac_no,item_id,add_info,`upgrade`,amplify_option,amplify_value,gold,seal_flag,letter_id,seperate_upgrade) VALUES(NOW(),'DNF Manager',?,?,?,?,?,?,?,?,?,?)",id,m.get("itemId"),m.get("quantity"),m.get("upgrade"),m.get("amplifyOption"),m.get("amplifyValue"),m.get("gold"),Boolean.TRUE.equals(m.get("sealFlag"))?1:0,letter,m.get("seperateUpgrade"));
        try(PreparedStatement p=c.prepareStatement("SELECT receive_charac_no,item_id,add_info,`upgrade`,amplify_option,amplify_value,gold,seal_flag,letter_id,seperate_upgrade FROM "+POSTAL+" WHERE postal_id=?")){
            p.setLong(1,postal);try(ResultSet r=p.executeQuery()){
                if(!r.next()||r.getLong(1)!=id||r.getLong(2)!=n(m,"itemId")||r.getLong(3)!=n(m,"quantity")||r.getLong(4)!=n(m,"upgrade")||r.getLong(5)!=n(m,"amplifyOption")||r.getLong(6)!=n(m,"amplifyValue")||r.getLong(7)!=n(m,"gold")||r.getBoolean(8)!=(Boolean.TRUE.equals(m.get("sealFlag")))||r.getLong(9)!=letter||r.getLong(10)!=n(m,"seperateUpgrade")||r.next())throw new SQLException("Mail readback differs");
            }
        }
        if(letter!=0)try(PreparedStatement p=c.prepareStatement("SELECT charac_no,HEX(letter_text) FROM "+LETTER+" WHERE letter_id=?")){
            p.setLong(1,letter);try(ResultSet r=p.executeQuery()){if(!r.next()||r.getLong(1)!=id||!r.getString(2).equalsIgnoreCase(hex(body))||r.next())throw new SQLException("Letter readback differs");}
        }
        Map<String,Object> out=new TreeMap<>();out.put("plan",plan);out.put("postalId",postal);out.put("letterId",letter);return out;
    }
    private String hex(String text){StringBuilder b=new StringBuilder();for(byte v:text.getBytes(StandardCharsets.UTF_8))b.append(String.format("%02x",v&255));return b.toString();}
    public Object save(Edit edit){
        long who=journal.admin(),id=recipient(edit);Map<String,Object> mail=validate(id,edit);String fingerprint=fingerprint(id,edit,mail);
        try(Connection c=journal.connection()){boolean discarded=false;lock(c);try{
            actor(c,who);ManagedOperationJournal.Entry e=source(c,id,edit.requestId);journal.same(e,namespace(id),who,fingerprint);
            if(!e.state.equals("PREVIEWED"))return result(e);
            journal.noPending(c,resource(id),edit.operation.equals("accept-current")?ManagedOperationJournal.id(edit.targetRequestId):null);
            Map<String,Object> state=before(c,id),after=plan(c,id,edit,mail,state);
            if(!journal.hash(state).equals(e.beforeHash)||!journal.hash(after).equals(e.afterHash))throw new DofFailure(409,"邮件预览已改变，未提交新内容");
            e=journal.prepare(c,e,Long.toString(id),state,after);
            if(edit.operation.equals("accept-current"))return result(journal.acknowledge(c,source(c,id,edit.targetRequestId),e,who));
            c.setAutoCommit(false);lock(c);
            try{
                e=source(c,id,edit.requestId);if(!e.state.equals("PREPARED")){c.rollback();return result(e);}
                actor(c,who);Map<String,Object> current=before(c,id);
                if(!journal.hash(current).equals(e.beforeHash)){e=journal.finish(c,e,"NOT_APPLIED",who);c.commit();return result(e);}
                // Revalidate the frozen PVF plan after the table-lock reacquisition.
                if(!journal.hash(plan(c,id,edit,mail,current)).equals(e.afterHash))throw new SQLException("Mail plan changed before insert");
                Map<String,Object> outcome=insertMail(c,after,id);
                if(after.get("attachment")!=null){
                    try{if(!Objects.equals(((Map<?,?>)after.get("attachment")).get("pvfSha256"),ResourceIndex.hash(pvf.sourceFile())))throw new SQLException("PVF changed during mail insert");}
                    catch(java.io.IOException failure){throw new SQLException("PVF unavailable after mail insert",failure);}
                    catch(SQLException failure){throw failure;}
                    catch(Exception failure){throw new SQLException("PVF validation unavailable after mail insert",failure);}
                }
                e=journal.finishWithGeneratedOutcome(c,e,outcome,who);c.commit();return result(e);
            }catch(SQLException|RuntimeException failure){
                try{c.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);discarded=true;discard(c,failure);}throw uncertain();
            }
        }finally{if(!discarded&&!c.isClosed()){unlock(c);if(!c.getAutoCommit())c.setAutoCommit(true);}}}catch(SQLException e){throw uncertain();}
    }
    public Object reconcile(long id,String request){
        long who=journal.admin();target(id);try(Connection c=journal.connection()){lock(c);try{
            actor(c,who);ManagedOperationJournal.Entry e=source(c,id,request);if(!e.state.equals("PREPARED"))return result(e);
            Map<String,Object> old=decode(e.beforeJson),current=before(c,id);
            if(!Objects.equals(old.get("schemaRevision"),current.get("schemaRevision"))||!Objects.equals(old.get("databaseUuid"),current.get("databaseUuid")))throw new DofFailure(409,"数据库或结构已改变，不能推断旧邮件结果");
            // InnoDB receipt commits with both native rows; PREPARED after taking
            // their locks proves no atomic commit. MyISAM rows may already exist.
            return result(journal.finish(c,e,Boolean.TRUE.equals(old.get("atomicReceipt"))?"NOT_APPLIED":"CONFLICT",who));
        }finally{unlock(c);}}catch(SQLException e){throw uncertain();}
    }
    public Object history(long id){
        long who=journal.admin();target(id);try(Connection c=journal.connection()){actor(c,who);List<Object> out=new ArrayList<>();for(ManagedOperationJournal.Entry e:journal.history(c,namespace(id)))out.add(result(source(c,id,e.requestId)));return out;}catch(SQLException e){throw uncertain();}
    }
}
