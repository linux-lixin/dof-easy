package com.aiyi.game.dnfserver.service.extension;

import com.aiyi.game.dnfserver.entity.AccountVO;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** Fixed account insert adapter for the original and companion schemas. Never alters native tables. */
@Service
public class NativeAccountRegistration {
    @Resource private DataSource dataSource;
    @Resource private com.aiyi.game.dnfserver.service.dof.RegistrationProof registrationProof;
    @org.springframework.beans.factory.annotation.Value("${DOF_ADMIN_ENABLED:false}") private boolean managed;
    @org.springframework.beans.factory.annotation.Value("${DOF_INSTANCE_ID:}") private String instanceId;
    public static final String[][] TABLES={{"d_taiwan","accounts"},{"d_taiwan","member_info"},{"d_taiwan","member_white_account"},{"taiwan_login","member_login"},{"taiwan_billing","cash_cera"},{"taiwan_billing","cash_cera_point"},{"taiwan_cain_2nd","member_avatar_coin"}};
    public static void verifyTransactional(Connection c,boolean pin)throws SQLException {
        if(pin)for(String[] t:TABLES)try(Statement s=c.createStatement();ResultSet ignored=s.executeQuery("SELECT * FROM `"+t[0]+"`.`"+t[1]+"` WHERE 1=0 FOR UPDATE")){}
        try(PreparedStatement p=c.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")){
            for(String[] t:TABLES){p.setString(1,t[0]);p.setString(2,t[1]);try(ResultSet r=p.executeQuery()){if(!r.next()||!"InnoDB".equalsIgnoreCase(r.getString(1))||r.next())throw new ActivityConfigService.Failure(409,"原生注册表不支持已验证的完整事务，停止开户；不更改引擎或假称已回滚");}}
        }
        String[][] columns={{"m_id","user_id"},{"m_id"},{"m_id"},{"account","cera","mod_tran","mod_date","reg_date"},{"account","cera_point","mod_date","reg_date"},{"m_id"}};
        for(int i=1;i<TABLES.length;i++){
            Set<String> supplied=new HashSet<>(Arrays.asList(columns[i-1]));
            try(PreparedStatement p=c.prepareStatement("SELECT COLUMN_NAME,IS_NULLABLE,COLUMN_DEFAULT,EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")){
                p.setString(1,TABLES[i][0]);p.setString(2,TABLES[i][1]);try(ResultSet r=p.executeQuery()){while(r.next())if(!supplied.contains(r.getString(1).toLowerCase(Locale.ROOT))&&"NO".equals(r.getString(2))&&r.getObject(3)==null&&!r.getString(4).contains("auto_increment")&&!r.getString(4).contains("GENERATED"))throw new ActivityConfigService.Failure(409,"注册表存在未适配的必填字段："+TABLES[i][1]+"."+r.getString(1));}
            }
        }
    }
    public void verify(){Connection c=DataSourceUtils.getConnection(dataSource);try{verifyTransactional(c,true);}catch(SQLException e){throw new ActivityConfigService.Failure(409,"注册表或权限不兼容，停止开户");}finally{DataSourceUtils.releaseConnection(c,dataSource);}}
    private static int[] rules(Connection c)throws SQLException {
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT ID,switch_regCdkey FROM dof_native.config LIMIT 2")){
                if(!r.next()||r.getInt(1)!=1||!Arrays.asList("0","1").contains(r.getString(2)))throw new ActivityConfigService.Failure(409,"原生注册卡开关结构或值未适配，停止网页注册");
                boolean nativeGate="1".equals(r.getString(2));if(r.next())throw new ActivityConfigService.Failure(409,"原生配置存在未适配的多行记录");
                if(nativeGate)throw new ActivityConfigService.Failure(409,"原生注册激活码协议尚未适配，停止网页注册；未把自有码当作晴空原生码");
            }
            Map<String,String> fields=new HashMap<>();try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COLUMN_NAME,DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='billing'")){while(r.next())fields.put(r.getString(1),r.getString(2));}
            for(String k:Arrays.asList("ID","ip_reg_control","regnumber","regtype"))if(!"int".equals(fields.get(k)))throw new ActivityConfigService.Failure(409,"原生注册规则结构未适配，停止开户");
            int limit,amount,currency;try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT ID,ip_reg_control,regnumber,regtype FROM dof_native.billing LIMIT 2")){
                if(!r.next()||r.getInt(1)!=1||r.getObject(2)==null||r.getObject(3)==null||r.getObject(4)==null)throw new ActivityConfigService.Failure(409,"原生注册规则缺失");limit=r.getInt(2);amount=r.getInt(3);currency=r.getInt(4);if(r.wasNull()||amount<0||currency<0||currency>1||r.next())throw new ActivityConfigService.Failure(409,"原生注册规则无效");
            }
        return new int[]{limit,amount,currency};
    }
    public Map<String,Object> readiness(Connection c,String instance){
        Map<String,Object> out=new LinkedHashMap<>();out.put("executor","existing-web-registration");out.put("nativeGatewayEffect","not_observed");
        try{
            verifyTransactional(c,false);int[] rule=rules(c);out.put("ipLimit",rule[0]);out.put("giftAmount",rule[1]);out.put("giftCurrency",rule[2]);
            long unverified=0;try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM d_taiwan.accounts a WHERE (a.admin=0 OR a.admin IS NULL) AND (NOT EXISTS(SELECT 1 FROM dnf_service.DOF_REGISTRATION_RECEIPT r WHERE r.instance_id=? AND r.uid=a.UID AND r.state='PERSISTED' AND r.ip_tag IS NOT NULL AND r.key_id=?))")){p.setString(1,instance);p.setString(2,registrationProof.currentKeyId());try(ResultSet r=p.executeQuery()){r.next();unverified=r.getLong(1);}}
            out.put("unverifiedRegistrationIpAccounts",unverified);boolean ready=rule[0]!=0&&(rule[0]<0||unverified==0);out.put("ready",ready);
            if(!ready)out.put("reason",rule[0]==0?"当前配置关闭注册":"历史或其他注册链的账号缺少已验证注册 IP，不能核对正数配额；没有补写历史玩家数据");
        }catch(com.aiyi.game.dnfserver.service.dof.DofFailure e){out.put("ready",false);out.put("reason",e.getMessage());}catch(ActivityConfigService.Failure e){out.put("ready",false);out.put("reason",e.getMessage());}catch(SQLException e){out.put("ready",false);out.put("reason","原生注册规则、账号表或权限不兼容，停止网页开户");}return out;
    }
    public int[] gift(String clientIp){
        if(!managed)return new int[]{1000,1};Connection c=DataSourceUtils.getConnection(dataSource);
        try{
            int[] rule=rules(c);int limit=rule[0],amount=rule[1],currency=rule[2];
            if(limit==0)throw new ActivityConfigService.Failure(403,"注册当前已关闭");
            if(limit>0){
                if(clientIp==null||clientIp.isEmpty())throw new ActivityConfigService.Failure(409,"无法确认可信客户端地址，停止执行 IP 注册配额");
                String ipTag=registrationProof.ipTag(clientIp),keyId=registrationProof.currentKeyId();int count=0;try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT UID FROM d_taiwan.accounts WHERE admin=0 OR admin IS NULL FOR UPDATE")){
                    while(r.next()){
                        try(PreparedStatement p=c.prepareStatement("SELECT ip_tag,key_id FROM dnf_service.DOF_REGISTRATION_RECEIPT WHERE instance_id=? AND uid=? AND state='PERSISTED' LIMIT 1 FOR UPDATE")){p.setString(1,instanceId);p.setLong(2,r.getLong(1));try(ResultSet own=p.executeQuery()){if(!own.next()||!keyId.equals(own.getString(2))||own.getString(1)==null)throw new ActivityConfigService.Failure(409,"存在其他注册链创建的账号或原始 IP 材料变化，注册来源待核对；未将登录 IP 当作注册 IP");if(ipTag.equals(own.getString(1)))count++;}}
                    }
                }if(count>=limit)throw new ActivityConfigService.Failure(403,"该 IP 的注册数量已达到上限");
            }
            return new int[]{amount,currency};
        }catch(SQLException e){throw new ActivityConfigService.Failure(409,"原生注册规则不可用，未自动使用默认赠送");}finally{DataSourceUtils.releaseConnection(c,dataSource);}
    }

    public void insert(AccountVO account) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new ActivityConfigService.Failure(503, "注册事务未启动");
        Connection c = DataSourceUtils.getConnection(dataSource);
        try {
            verifyTransactional(c,true);
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("accountname", account.getAccountname());
            values.put("password", account.getPassword());
            values.put("qq", account.getQq());
            values.put("vip", account.getVip());
            values.put("admin", account.isAdmin());
            values.put("parent_uid", account.getParentUid());
            if(managed)values.put("ip",account.getRegistrationClientIp());
            Map<String, String> optional = new LinkedHashMap<>();
            for (String name : Arrays.asList("qcc", "login_ip", "login_mac")) optional.put(name, "varchar");
            for (String name : Arrays.asList("seal_ip", "seal_mac", "seal_accountname")) optional.put(name, "int");
            Set<String> seen = new HashSet<>();
            try (PreparedStatement p = c.prepareStatement("SELECT COLUMN_NAME,DATA_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='d_taiwan' AND TABLE_NAME='accounts' ORDER BY ORDINAL_POSITION"); ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    String name = r.getString(1).toLowerCase(Locale.ROOT);
                    seen.add(name);
                    if (optional.containsKey(name)) {
                        if (!optional.get(name).equalsIgnoreCase(r.getString(2)))
                            throw new ActivityConfigService.Failure(409, "原生账号扩展字段类型不兼容，拒绝注册");
                        values.put(name, "varchar".equals(optional.get(name)) ? "" : 0);
                    } else if (!values.containsKey(name) && !r.getString(5).contains("auto_increment")
                            && "NO".equals(r.getString(3)) && r.getObject(4) == null) {
                        throw new ActivityConfigService.Failure(409, "原生账号表存在未适配的必填字段，拒绝注册");
                    }
                }
            }
            if (!seen.contains("uid") || !seen.containsAll(values.keySet()))
                throw new ActivityConfigService.Failure(409, "原生账号表结构不兼容，拒绝注册");
            // The companion table has no unique accountname index. A current locking read
            // also closes the race after RegistrationCodesService locks the policy row.
            try (PreparedStatement p = c.prepareStatement("SELECT UID FROM d_taiwan.accounts WHERE accountname=? FOR UPDATE")) {
                p.setString(1, account.getAccountname());
                try (ResultSet r = p.executeQuery()) {
                    if (r.next()) throw new ActivityConfigService.Failure(400, "该用户名已存在，请更换其他用户名");
                }
            }
            String columns = "`" + String.join("`,`", values.keySet()) + "`";
            String placeholders = String.join(",", Collections.nCopies(values.size(), "?"));
            try (PreparedStatement p = c.prepareStatement("INSERT INTO d_taiwan.accounts (" + columns + ") VALUES (" + placeholders + ")", Statement.RETURN_GENERATED_KEYS)) {
                int index = 1;
                for (Object value : values.values()) p.setObject(index++, value);
                p.executeUpdate();
                try (ResultSet keys = p.getGeneratedKeys()) {
                    if (!keys.next() || keys.getLong(1) <= 0) throw new SQLException("Missing generated UID");
                    account.setUid(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new ActivityConfigService.Failure(409, "账号写入失败，请检查账号是否重复及原生表结构");
        } finally {
            DataSourceUtils.releaseConnection(c, dataSource);
        }
    }
}
