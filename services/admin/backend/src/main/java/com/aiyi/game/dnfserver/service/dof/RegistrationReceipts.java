package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.game.dnfserver.entity.AccountVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Durable receipt surrounding the SAME InnoDB transaction as all account/code/gift writes. */
@Service
public class RegistrationReceipts {
    @Resource private DofAdminService access;
    @Resource private ManagedOperationJournal journal;
    @Resource private RegistrationProof proof;
    @Resource private DataSource dataSource;
    @Value("${DOF_ADMIN_ENABLED:false}") private boolean enabled;
    static final String TABLE="`dnf_service`.`DOF_REGISTRATION_RECEIPT`",GATE="`dnf_service`.`DOF_REGISTRATION_GATE`";
    public static class Receipt {public String requestId,nameTag,requestTag,keyId;public Long uid;public boolean replay;}
    public boolean enabled(){return enabled;}
    private String instance(){return access.context().get("instanceId").toString();}
    private Connection connection()throws SQLException {Connection c=access.boundConnection();if(!c.getAutoCommit()){c.close();throw new DofFailure(409,"注册回执写前连接必须独立提交");}return c;}
    private String nameTag(Connection c,String name)throws SQLException {
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT DATA_TYPE,CHARACTER_SET_NAME,COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='d_taiwan' AND TABLE_NAME='accounts' AND COLUMN_NAME='accountname'")){
            if(!r.next()||!"varchar".equals(r.getString(1))||!Arrays.asList("utf8","utf8mb3").contains(r.getString(2))||!Arrays.asList("utf8_general_ci","utf8mb3_general_ci").contains(r.getString(3))||r.next())throw new DofFailure(409,"账号名称排序规则未适配，停止注册以避免重复账号");
        }
        try(PreparedStatement p=c.prepareStatement("SELECT HEX(WEIGHT_STRING(CONVERT(? USING utf8) COLLATE utf8_general_ci))")){p.setString(1,name);try(ResultSet r=p.executeQuery()){if(!r.next()||r.getString(1)==null)throw new SQLException("No name weight");return NativeUiState.sha256(r.getString(1).getBytes(StandardCharsets.US_ASCII));}}
    }
    private String requestTag(AccountVO a,String name,byte[] key){Map<String,Object> m=new LinkedHashMap<>();m.put("instance",instance());m.put("name",name);m.put("password",a.getPassword());m.put("qq",a.getQq()==null?"":a.getQq());m.put("recommender",a.getRecommender()==null||a.getRecommender().isEmpty()?"admin":a.getRecommender().toLowerCase(Locale.ROOT));m.put("activationCode",a.getActivationCode());return proof.tag(key,"registration-request",m);}
    private Receipt find(Connection c,String id)throws SQLException {try(PreparedStatement p=c.prepareStatement("SELECT request_id,name_tag,request_tag,key_id,state,uid FROM "+TABLE+" WHERE instance_id=? AND request_id=?")){p.setString(1,instance());p.setString(2,ManagedOperationJournal.id(id));try(ResultSet r=p.executeQuery()){
        if(!r.next())return null;Receipt receipt=new Receipt();receipt.requestId=r.getString(1);receipt.nameTag=r.getString(2);receipt.requestTag=r.getString(3);receipt.keyId=r.getString(4);String state=r.getString(5);receipt.uid=r.getObject(6)==null?null:r.getLong(6);
        if(!Arrays.asList("PREPARED","PERSISTED","NOT_APPLIED").contains(state)||state.equals("PERSISTED")!=(receipt.uid!=null)||receipt.uid!=null&&receipt.uid<1||r.next())throw RegistrationSchema.bad();receipt.replay=state.equals("PERSISTED");return receipt;
    }}}
    private Receipt check(Connection c,Receipt old,AccountVO a,String name,String tag,String keyId,boolean explicit)throws SQLException {
        if(!old.keyId.equals(keyId))throw new DofFailure(409,"注册核对私密材料已变化，请恢复原材料；不要更换编号重复注册");
        if(!old.nameTag.equals(name)||!old.requestTag.equals(tag))throw new DofFailure(explicit?409:400,explicit?"注册编号已被不同内容使用":"账号已存在或已有不同内容的注册请求");
        a.setRegistrationRequestId(old.requestId);
        if(old.replay){a.setUid(old.uid);return old;}
        try(PreparedStatement p=c.prepareStatement("SELECT state FROM "+TABLE+" WHERE instance_id=? AND request_id=?")){p.setString(1,instance());p.setString(2,old.requestId);try(ResultSet r=p.executeQuery()){if(!r.next())throw RegistrationSchema.bad();if("NOT_APPLIED".equals(r.getString(1)))throw new DofFailure(409,"注册未写入，请使用新的操作编号和验证码；原编号 "+old.requestId);}}
        throw new DofFailure(503,"注册正在执行或结果待核对，请保留原编号 "+old.requestId+"，不要重复扣码或赠送");
    }
    /** A completed replay needs no second captcha and never overwrites later password/progress. */
    public Receipt replay(AccountVO a){if(!enabled)return null;validate(a);byte[] key=proof.key();try(Connection c=connection()){RegistrationSchema.check(c);String name=nameTag(c,a.getAccountname()),tag=requestTag(a,name,key),keyId=proof.keyId(key);boolean explicit=a.getRegistrationRequestId()!=null;
        if(explicit){Receipt old=find(c,a.getRegistrationRequestId());return old==null?null:check(c,old,a,name,tag,keyId,true);}
        try(PreparedStatement p=c.prepareStatement("SELECT request_id,state FROM "+TABLE+" WHERE instance_id=? AND name_tag=? AND state IN ('PREPARED','PERSISTED') ORDER BY (state='PREPARED') DESC,created_at DESC LIMIT 1")){p.setString(1,instance());p.setString(2,name);try(ResultSet r=p.executeQuery()){return r.next()?check(c,find(c,r.getString(1)),a,name,tag,keyId,false):null;}}
    }catch(SQLException e){throw unavailable(a.getRegistrationRequestId());}}
    public static void validate(AccountVO a){
        if(a==null||a.getAccountname()==null||a.getAccountname().isEmpty()||!a.getAccountname().equals(a.getAccountname().trim())||a.getAccountname().length()>64||a.getAccountname().codePoints().anyMatch(x->x<33||x>65535||Character.isSurrogate((char)x)))throw new DofFailure(400,"账号须为1–64字，不能包含空白、控制字符或不支持的文字");
        if(a.getPassword()==null||a.getPassword().isEmpty()||a.getPassword().length()>128||a.getPassword().codePoints().anyMatch(x->x<32||x>65535||Character.isSurrogate((char)x))||a.getQq()!=null&&a.getQq().length()>64||a.getRecommender()!=null&&a.getRecommender().length()>64||a.getActivationCode()!=null&&a.getActivationCode().length()>512)throw new DofFailure(400,"注册密码或附加字段格式无效");
        if(a.getRegistrationRequestId()!=null)ManagedOperationJournal.id(a.getRegistrationRequestId());
    }
    public Receipt begin(AccountVO a){if(!enabled)return null;validate(a);byte[] key=proof.key();try(Connection c=connection()){
        c.setAutoCommit(false);for(String table:Arrays.asList(TABLE,GATE))try(Statement s=c.createStatement();ResultSet ignored=s.executeQuery("SELECT * FROM "+table+" WHERE 1=0 FOR UPDATE")){}RegistrationSchema.check(c);
        String name=nameTag(c,a.getAccountname()),tag=requestTag(a,name,key),keyId=proof.keyId(key);boolean explicit=a.getRegistrationRequestId()!=null;
        try{
            try(PreparedStatement p=c.prepareStatement("INSERT IGNORE INTO "+GATE+"(instance_id,name_tag) VALUES(?,?)")){p.setString(1,instance());p.setString(2,name);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("SELECT name_tag FROM "+GATE+" WHERE instance_id=? AND name_tag=? FOR UPDATE")){p.setString(1,instance());p.setString(2,name);try(ResultSet r=p.executeQuery()){if(!r.next())throw RegistrationSchema.bad();}}
            if(explicit){Receipt old=find(c,a.getRegistrationRequestId());if(old!=null){Receipt result=check(c,old,a,name,tag,keyId,true);c.commit();return result;}}
            String parent=null;
            try(PreparedStatement p=c.prepareStatement("SELECT request_id,state FROM "+TABLE+" WHERE instance_id=? AND name_tag=? ORDER BY (state IN ('PREPARED','PERSISTED')) DESC,created_at DESC,request_id DESC LIMIT 1")){p.setString(1,instance());p.setString(2,name);try(ResultSet r=p.executeQuery()){if(r.next()){if(!"NOT_APPLIED".equals(r.getString(2))){Receipt result=check(c,find(c,r.getString(1)),a,name,tag,keyId,explicit);c.commit();return result;}parent=r.getString(1);}}}
            if(a.getRegistrationClientIp()==null||a.getRegistrationClientIp().isEmpty())throw new DofFailure(409,"注册来源地址未确认，停止开户");
            Receipt out=new Receipt();out.requestId=explicit?ManagedOperationJournal.id(a.getRegistrationRequestId()):UUID.randomUUID().toString();out.nameTag=name;out.requestTag=tag;out.keyId=keyId;a.setRegistrationRequestId(out.requestId);
            try(PreparedStatement p=c.prepareStatement("INSERT INTO "+TABLE+"(instance_id,request_id,name_tag,request_tag,key_id,state,parent_request,ip_tag) VALUES(?,?,?,?,?,'PREPARED',?,?)")){p.setString(1,instance());p.setString(2,out.requestId);p.setString(3,name);p.setString(4,tag);p.setString(5,keyId);p.setString(6,parent);p.setString(7,proof.ipTag(a.getRegistrationClientIp()));p.executeUpdate();}
            c.commit();return out;
        }catch(RuntimeException|SQLException e){c.rollback();throw e;}
    }catch(SQLException e){throw unavailable(a.getRegistrationRequestId());}}
    public void lockForExecution(Receipt receipt,AccountVO a){if(receipt==null)return;if(!TransactionSynchronizationManager.isActualTransactionActive())throw new DofFailure(503,"注册事务未启动");Connection c=DataSourceUtils.getConnection(dataSource);
        try{try(PreparedStatement p=c.prepareStatement("SELECT state,request_tag FROM "+TABLE+" WHERE instance_id=? AND request_id=? FOR UPDATE")){p.setString(1,instance());p.setString(2,receipt.requestId);try(ResultSet r=p.executeQuery()){if(!r.next()||!"PREPARED".equals(r.getString(1))||!receipt.requestTag.equals(r.getString(2)))throw new DofFailure(409,"注册回执状态改变，停止开户");}}
            RegistrationSchema.check(c);
            if(!receipt.nameTag.equals(nameTag(c,a.getAccountname())))throw new DofFailure(409,"账号排序规则变化，停止开户");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter(){@Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK)recordRollback(receipt);}});
        }catch(SQLException e){throw unavailable(receipt.requestId);}finally{DataSourceUtils.releaseConnection(c,dataSource);}}
    private void recordRollback(Receipt receipt){try(Connection c=connection();PreparedStatement p=c.prepareStatement("UPDATE "+TABLE+" SET state='NOT_APPLIED',finished_at=NOW() WHERE instance_id=? AND request_id=? AND request_tag=? AND state='PREPARED' AND uid IS NULL")){p.setString(1,instance());p.setString(2,receipt.requestId);p.setString(3,receipt.requestTag);p.executeUpdate();}catch(Exception ignored){/* Receipt remains pending; no uncertain result is retried. */}}
    public void complete(Receipt receipt,long uid,int amount,int currency){if(receipt==null)return;if(!TransactionSynchronizationManager.isActualTransactionActive())throw new DofFailure(503,"注册事务未启动");Connection c=DataSourceUtils.getConnection(dataSource);try(PreparedStatement p=c.prepareStatement("UPDATE "+TABLE+" SET state='PERSISTED',uid=?,gift_amount=?,gift_currency=?,finished_at=NOW() WHERE instance_id=? AND request_id=? AND request_tag=? AND state='PREPARED'")){p.setLong(1,uid);p.setInt(2,amount);p.setInt(3,currency);p.setString(4,instance());p.setString(5,receipt.requestId);p.setString(6,receipt.requestTag);if(p.executeUpdate()!=1)throw new SQLException("Registration receipt changed");}catch(SQLException e){throw unavailable(receipt.requestId);}finally{DataSourceUtils.releaseConnection(c,dataSource);}}
    private DofFailure unavailable(String id){return new DofFailure(503,"注册结果待核对，操作编号 "+(id==null?"尚未分配":id)+"；不要更换编号重复扣码或赠送");}
    public Object list(int page){journal.admin();if(page<1||page>10000)throw new DofFailure(400,"页码无效");try(Connection c=journal.connection()){RegistrationSchema.check(c);List<Object> rows=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT request_id,state,uid,gift_amount,gift_currency,parent_request,created_at,finished_at FROM "+TABLE+" WHERE instance_id=? ORDER BY (state='PREPARED') DESC,created_at DESC,request_id DESC LIMIT 50 OFFSET ?")){p.setString(1,instance());p.setInt(2,(page-1)*50);try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> m=new LinkedHashMap<>();for(String k:Arrays.asList("request_id","state","uid","gift_amount","gift_currency","parent_request","created_at","finished_at"))m.put(k,r.getObject(k));rows.add(m);}}}return rows;}catch(SQLException e){throw unavailable(null);}}
}
