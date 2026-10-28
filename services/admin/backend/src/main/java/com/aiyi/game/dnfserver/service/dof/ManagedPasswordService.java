package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.utils.MD5;
import com.aiyi.game.dnfserver.utils.cache.UserTokenCacheUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Admin reset only. Durable evidence is keyed and contains no recoverable password/hash. */
@Service
public class ManagedPasswordService {
    @Resource private ManagedOperationJournal journal;
    @Value("${DOF_PASSWORD_JOURNAL_KEY_FILE:./data/private.key}") private String keyFile;
    private static final String TABLE="`d_taiwan`.`accounts`";
    private final ObjectMapper json=new ObjectMapper();
    public static class Edit {
        public String requestId,revision,operation,reason,targetRequestId;
        @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) public String newPassword;
    }
    private static class Account {int uid;boolean admin;String name,password;}
    private String ns(long uid){return "account-password."+uid;}
    private String resource(long uid){return "d_taiwan.accounts.password:"+uid;}
    private void target(long uid){if(uid<1||uid>Integer.MAX_VALUE)throw new DofFailure(400,"账号编号无效");}
    private byte[] key() {
        try{Path p=Paths.get(keyFile);if(Files.isSymbolicLink(p)||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)<32||Files.size(p)>65536)throw new Exception();
            MessageDigest d=MessageDigest.getInstance("SHA-256");d.update("dof-admin-password-journal-v1\0".getBytes(StandardCharsets.UTF_8));return d.digest(Files.readAllBytes(p));
        }catch(Exception e){throw new DofFailure(503,"改密核对所需的持久化私密材料不可用；未生成替代密钥或写入密码");}
    }
    private String tag(byte[] key,String domain,Object value) {
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));byte[] b=mac.doFinal((domain+"\0"+journal.encode(value)).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte n:b)out.append(String.format("%02x",n&255));return out.toString();}
        catch(java.security.GeneralSecurityException e){throw new DofFailure(503,"密码核对材料不可用");}
    }
    private void schema(Connection c)throws SQLException {
        Map<String,String> types=new HashMap<>();Set<String> pk=new HashSet<>();Map<String,Long> lengths=new HashMap<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COLUMN_NAME,DATA_TYPE,COLUMN_KEY,CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='d_taiwan' AND TABLE_NAME='accounts'")){
            while(r.next()){types.put(r.getString(1),r.getString(2));lengths.put(r.getString(1),r.getLong(4));if("PRI".equals(r.getString(3)))pk.add(r.getString(1));}
        }
        if(!pk.equals(Collections.singleton("UID"))||!"int".equals(types.get("UID"))||!"int".equals(types.get("admin"))||!"varchar".equals(types.get("accountname"))||!"varchar".equals(types.get("password"))||lengths.get("password")<32||lengths.get("password")>255)throw incompatible();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='d_taiwan' AND TABLE_NAME='accounts'")){if(!r.next()||!Arrays.asList("MyISAM","InnoDB").contains(r.getString(1))||r.next())throw incompatible();}
    }
    private DofFailure incompatible(){return new DofFailure(409,"账号密码结构或格式未适配，停止管理员改密");}
    private void lock(Connection c)throws SQLException{journal.lock(c,TABLE+" WRITE");schema(c);}
    private Account read(Connection c,long uid)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT UID,accountname,password,admin FROM "+TABLE+" WHERE UID=?")){p.setLong(1,uid);try(ResultSet r=p.executeQuery()){
            if(!r.next())throw new DofFailure(404,"账号不存在");Account a=new Account();a.uid=r.getInt(1);a.name=r.getString(2);a.password=r.getString(3);a.admin=r.getInt(4)!=0;
            if(a.name==null||a.password==null||!a.password.matches("[0-9a-fA-F]{32}")||r.next())throw incompatible();return a;
        }}
    }
    private Map<String,Object> state(Account a,byte[] key,String password) {
        Map<String,Object> out=new LinkedHashMap<>();out.put("uid",a.uid);out.put("identityTag",tag(key,"account",a.name));out.put("keyId",tag(key,"key-id",journal.instance()));out.put("passwordTag",tag(key,"password",Arrays.asList(journal.instance(),a.uid,password)));return out;
    }
    private String action(Edit e) {
        if(e==null||e.revision==null||!e.revision.matches("[0-9a-f]{64}"))throw new DofFailure(400,"缺少有效密码状态版本");ManagedOperationJournal.id(e.requestId);
        if(!Arrays.asList("reset","accept-current").contains(e.operation))throw new DofFailure(400,"未知管理员改密操作；不支持恢复旧密码");
        if(e.reason==null||e.reason.trim().isEmpty()||e.reason.length()>100||e.reason.codePoints().anyMatch(x->x<32||x>65535||Character.isSurrogate((char)x)))throw new DofFailure(400,"请填写 1–100 字操作原因");
        if(e.operation.equals("reset")){
            if(e.targetRequestId!=null||e.newPassword==null||e.newPassword.length()<8||e.newPassword.length()>64||e.newPassword.codePoints().anyMatch(x->x<32||x>65535||Character.isSurrogate((char)x)))throw new DofFailure(400,"新密码须为 8–64 位，且不能提供历史恢复目标");
            if(e.reason.contains(e.newPassword)||e.reason.toLowerCase(Locale.ROOT).contains(MD5.getMd5(e.newPassword).toLowerCase(Locale.ROOT)))throw new DofFailure(400,"操作原因不能包含新密码或密码摘要");
        }else{ManagedOperationJournal.id(e.targetRequestId);if(e.newPassword!=null)throw new DofFailure(400,"保留当前密码状态不接受新密码");}return e.operation;
    }
    private String fingerprint(long uid,Edit e,byte[] key) {
        Map<String,Object> data=new LinkedHashMap<>();data.put("uid",uid);data.put("revision",e.revision);data.put("operation",e.operation);data.put("reason",e.reason);data.put("targetRequestId",e.targetRequestId==null?null:ManagedOperationJournal.id(e.targetRequestId));data.put("newPassword",e.newPassword);return tag(key,"request",data);
    }
    private Map<String,Object> snapshot(long uid,String text) {
        try{Map<String,Object> out=json.readValue(text,new TypeReference<Map<String,Object>>(){});
            if(out==null||!out.keySet().equals(new HashSet<>(Arrays.asList("uid","identityTag","keyId","passwordTag")))||!json.valueToTree(out.get("uid")).isIntegralNumber()||((Number)out.get("uid")).longValue()!=uid)throw new Exception();
            for(String k:Arrays.asList("identityTag","keyId","passwordTag"))if(!(out.get(k) instanceof String)||!out.get(k).toString().matches("[0-9a-f]{64}"))throw new Exception();return out;
        }catch(Exception e){throw new DofFailure(409,"改密核对记录损坏，停止判断结果");}
    }
    private ManagedOperationJournal.Entry source(Connection c,long uid,String id)throws SQLException {
        ManagedOperationJournal.Entry e=journal.require(c,ns(uid),id);if(!resource(uid).equals(e.resource)||!Long.toString(uid).equals(e.target))throw new DofFailure(409,"改密记录目标不匹配");snapshot(uid,e.beforeJson);snapshot(uid,e.afterJson);return e;
    }
    private void sameKey(long uid,ManagedOperationJournal.Entry e,Map<String,Object> current) {
        if(!current.get("keyId").equals(snapshot(uid,e.beforeJson).get("keyId"))||!current.get("keyId").equals(snapshot(uid,e.afterJson).get("keyId")))throw new DofFailure(409,"核对密钥已改变，不能判断旧改密结果；请恢复原核对材料，不要重复执行");
    }
    private void protectedAccount(Account a,long actor){if(a.admin||a.uid==actor)throw new DofFailure(409,"不能通过玩家密码重置修改管理员或自己的密码");}
    private DofFailure unavailable(){return new DofFailure(503,"改密结果未确认，请保留操作编号核对；没有自动重试或恢复旧密码");}
    public Object status(long uid){long actor=journal.admin();target(uid);byte[] key=key();try(Connection c=journal.connection()){schema(c);Account a=read(c,uid);Map<String,Object> out=new LinkedHashMap<>();out.put("uid",a.uid);out.put("revision",journal.hash(state(a,key,a.password)));out.put("resetAllowed",!a.admin&&a.uid!=actor);return out;}catch(SQLException e){throw unavailable();}}
    private Map<String,Object> next(Connection c,long uid,Edit e,Account a,Map<String,Object> before,byte[] key,long actor)throws SQLException {
        if(!journal.hash(before).equals(e.revision))throw new DofFailure(409,"密码状态或账号身份改变，请重新读取并预览");
        if(e.operation.equals("reset")){protectedAccount(a,actor);String password=MD5.getMd5(e.newPassword);if(password.equalsIgnoreCase(a.password))throw new DofFailure(400,"新密码不能与当前密码相同");return state(a,key,password);}
        ManagedOperationJournal.Entry old=source(c,uid,e.targetRequestId);sameKey(uid,old,before);if(!old.state.equals("CONFLICT"))throw new DofFailure(409,"只能确认冲突改密记录的当前状态");return before;
    }
    public Object preview(long uid,Edit edit) {
        long actor=journal.admin();target(uid);action(edit);byte[] key=key();try(Connection c=journal.connection()){lock(c);try{
            ManagedOperationJournal.Entry old=journal.find(c,edit.requestId);if(old!=null){journal.same(old,ns(uid),actor,fingerprint(uid,edit,key));source(c,uid,edit.requestId);return journal.result(old);}
            journal.noPending(c,resource(uid),edit.operation.equals("accept-current")?ManagedOperationJournal.id(edit.targetRequestId):null);Account a=read(c,uid);Map<String,Object> before=state(a,key,a.password),after=next(c,uid,edit,a,before,key,actor);
            return journal.result(journal.preview(c,edit.requestId,actor,ns(uid),resource(uid),Long.toString(uid),edit.operation,edit.targetRequestId,fingerprint(uid,edit,key),edit.reason,before,after));
        }finally{journal.unlock(c);}}catch(SQLException e){throw unavailable();}
    }
    public Object save(long uid,Edit edit) {
        long actor=journal.admin();target(uid);String op=action(edit);byte[] key=key();boolean prepared=false;
        try(Connection c=journal.connection()){lock(c);try{
            ManagedOperationJournal.Entry e=source(c,uid,edit.requestId);journal.same(e,ns(uid),actor,fingerprint(uid,edit,key));if(!e.state.equals("PREVIEWED"))return journal.result(e);
            journal.noPending(c,resource(uid),op.equals("accept-current")?ManagedOperationJournal.id(edit.targetRequestId):null);Account a=read(c,uid);Map<String,Object> before=state(a,key,a.password),after=next(c,uid,edit,a,before,key,actor);
            if(!journal.hash(before).equals(e.beforeHash)||!journal.hash(after).equals(e.afterHash))throw new DofFailure(409,"密码预览版本已改变");e=journal.prepare(c,e,Long.toString(uid),before,after);
            if(op.equals("accept-current"))return journal.result(journal.acknowledge(c,source(c,uid,edit.targetRequestId),e,actor));
            prepared=true;
            try(PreparedStatement p=c.prepareStatement("UPDATE "+TABLE+" SET password=? WHERE UID=? AND BINARY password=? AND (admin=0 OR admin IS NULL)")){p.setString(1,MD5.getMd5(edit.newPassword));p.setLong(2,uid);p.setString(3,a.password);if(p.executeUpdate()!=1)throw new SQLException("Password state changed");}
            Account written=read(c,uid);if(!journal.hash(state(written,key,written.password)).equals(e.afterHash))throw new SQLException("Password readback differs");
            return journal.result(journal.finish(c,e,"PERSISTED",actor));
        }finally{journal.unlock(c);}}catch(SQLException e){throw unavailable();}finally{if(prepared)UserTokenCacheUtil.clear(uid);}
    }
    public Object reconcile(long uid,String requestId){long actor=journal.admin();target(uid);byte[] key=key();try(Connection c=journal.connection()){lock(c);try{
        ManagedOperationJournal.Entry e=source(c,uid,requestId);if(!e.state.equals("PREPARED"))return journal.result(e);Account a=read(c,uid);Map<String,Object> current=state(a,key,a.password);sameKey(uid,e,current);String h=journal.hash(current);
        if(e.operation.equals("accept-current")){ManagedOperationJournal.Entry old=source(c,uid,e.targetRequest);if(!old.state.equals("CONFLICT"))throw new DofFailure(409,"原改密冲突状态改变");return journal.result(h.equals(e.afterHash)?journal.acknowledge(c,old,e,actor):journal.finish(c,e,"NOT_APPLIED",actor));}
        ManagedOperationJournal.Entry result=journal.finish(c,e,h.equals(e.afterHash)?"PERSISTED":h.equals(e.beforeHash)?"NOT_APPLIED":"CONFLICT",actor);UserTokenCacheUtil.clear(uid);return journal.result(result);
    }finally{journal.unlock(c);}}catch(SQLException e){throw unavailable();}}
    public Object history(long uid){journal.admin();target(uid);try(Connection c=journal.connection()){List<Object> out=new ArrayList<>();for(ManagedOperationJournal.Entry e:journal.history(c,ns(uid))){source(c,uid,e.requestId);out.add(journal.result(e));}return out;}catch(SQLException e){throw unavailable();}}
}
