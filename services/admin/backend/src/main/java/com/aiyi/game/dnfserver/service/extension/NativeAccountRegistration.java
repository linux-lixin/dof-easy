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

    public void insert(AccountVO account) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new ActivityConfigService.Failure(503, "注册事务未启动");
        Connection c = DataSourceUtils.getConnection(dataSource);
        try {
            String[][] tables = {{"d_taiwan","accounts"},{"d_taiwan","member_info"},{"d_taiwan","member_white_account"},{"taiwan_login","member_login"},{"taiwan_billing","cash_cera"},{"taiwan_billing","cash_cera_point"},{"taiwan_cain_2nd","member_avatar_coin"}};
            try (PreparedStatement p = c.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) {
                for (String[] table : tables) {
                    p.setString(1, table[0]); p.setString(2, table[1]);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next() || !"InnoDB".equalsIgnoreCase(r.getString(1)))
                            throw new ActivityConfigService.Failure(409, "原生注册表不支持完整事务，拒绝注册；未修改表引擎");
                    }
                }
            }
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("accountname", account.getAccountname());
            values.put("password", account.getPassword());
            values.put("qq", account.getQq());
            values.put("vip", account.getVip());
            values.put("admin", account.isAdmin());
            values.put("parent_uid", account.getParentUid());
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
