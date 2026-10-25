package com.aiyi.game.dnfserver.service.dof;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.*;
import java.util.*;

/** This development adapter is opt-in and bound to an isolated database identity. */
@Service
public class DofAdminService {
    @Resource private DataSource dataSource;
    @Resource private AccountVODao accountVODao;
    @Value("${DOF_ADMIN_ENABLED:false}") private boolean enabled;
    @Value("${DOF_INSTANCE_ID:}") private String instanceId;
    @Value("${DOF_ENVIRONMENT:}") private String environment;
    @Value("${DOF_DATABASE_UUID:}") private String databaseUuid;
    @Value("${DOF_ADAPTER_VERSION:}") private String adapterVersion;
    static final String VERSION = "dof-native-1";
    private static final String TABLE = "`dof_native`.`config`";
    private static final String AUDIT = "`dnf_service`.`DOF_CHANGE_AUDIT`";
    private final ObjectMapper json = new ObjectMapper();
    private final NativeUiProfiles profiles = new NativeUiProfiles();
    private final NativeUiPresetCatalog presetCatalog = new NativeUiPresetCatalog();
    private final NativeUiPresetApplication presetApplication = new NativeUiPresetApplication();
    private final NativeCreationPresets creationPresets = new NativeCreationPresets();

    public static class Edit {
        public String revision, preset, requestId;
        public Map<String,JsonNode> coordinates;
        public Map<String,JsonNode> switches;
        public Map<String,JsonNode> features;
        public Map<String,JsonNode> layout;
        public Map<String,JsonNode> values;
    }
    /** Fail application startup before any legacy admin endpoint can use a misbound datasource. */
    @PostConstruct public void verifyStartupBinding() {
        if (!enabled) return;
        try (Connection ignored = connection()) {
            // connection() verifies both the server UUID and the dedicated binding row.
        } catch (SQLException e) { throw unavailable(); }
    }
    long admin() {
        long actor = ThreadUtil.getUserId();
        AccountVO account = accountVODao.get(actor);
        if (account == null || !account.isAdmin()) throw new DofFailure(403, "仅管理员可操作晴空管理");
        return actor;
    }
    private void configured() {
        if (!enabled) throw new DofFailure(409, "此后台尚未启用晴空替代开发区");
        if (!"isolated".equals(environment) || instanceId == null || !instanceId.matches("[a-z0-9][a-z0-9-]{2,63}")
            || databaseUuid == null || !databaseUuid.matches("[0-9a-fA-F-]{36}") || !VERSION.equals(adapterVersion))
            throw new DofFailure(409, "隔离实例身份或适配版本不完整，停止访问");
    }
    private void guard(Connection c) throws SQLException {
        configured();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT @@server_uuid")) {
            if (!r.next() || !databaseUuid.equalsIgnoreCase(r.getString(1))) throw new DofFailure(409, "数据库身份与绑定的隔离实例不符，停止访问");
        }
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
            "SELECT adapter_version,database_uuid FROM dnf_service.DOF_INSTANCE_BINDING WHERE instance_id='" + instanceId + "' AND environment='isolated'")) {
            if (!r.next() || !VERSION.equals(r.getString(1)) || !databaseUuid.equalsIgnoreCase(r.getString(2)) || r.next())
                throw new DofFailure(409, "隔离实例绑定未完成或版本不匹配");
        }
    }
    private void schema(Connection c) throws SQLException {
        Map<String,String> types = new HashMap<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COLUMN_NAME,DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dof_native' AND TABLE_NAME='config'")) {
            while (r.next()) types.put(r.getString(1), r.getString(2));
        }
        if (!"int".equals(types.get("ID"))) throw new DofFailure(409, "晴空配置结构不兼容；不会初始化玩家库");
        for (String key : NativeUiState.COLUMNS) if (!Arrays.asList("text", "mediumtext", "longtext", "varchar").contains(types.get(key)))
            throw new DofFailure(409, "晴空界面字段结构不兼容");
    }
    private NativeUiState read(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT ID,BINARY Hotkeys,BINARY temp_2,BINARY temp_3 FROM " + TABLE + " LIMIT 2")) {
            if (!r.next() || r.getInt(1) != 1) throw new DofFailure(409, "原生界面配置缺失，停止访问");
            Map<String,byte[]> fields = new LinkedHashMap<>();
            for (int i = 0; i < NativeUiState.COLUMNS.size(); i++) fields.put(NativeUiState.COLUMNS.get(i), r.getBytes(i + 2));
            if (r.next()) throw new DofFailure(409, "存在多条界面配置，停止覆盖");
            return new NativeUiState(fields);
        }
    }
    Connection connection() throws SQLException {
        configured();
        Connection c = dataSource.getConnection();
        try {
            c.setAutoCommit(true);
            guard(c); schema(c);
            try (Statement s = c.createStatement()) { s.execute("SET SESSION lock_wait_timeout=5"); s.execute("SET SESSION innodb_lock_wait_timeout=5"); }
            return c;
        } catch (SQLException | RuntimeException e) { c.close(); throw e; }
    }
    private DofFailure unavailable() { return new DofFailure(503, "隔离数据库或管理迁移不可用；未初始化或重建原生表"); }
    Map<String,Object> context() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("instanceId", instanceId); out.put("environment", environment); out.put("adapterVersion", VERSION);
        out.put("completeReplacement", false); return out;
    }
    public Object catalog() {
        admin(); configured();
        try (Connection c = connection(); InputStream in = getClass().getResourceAsStream("/dof/scope.json")) {
            Map<String,Object> out = context();
            out.put("scope", json.readValue(in, new TypeReference<Map<String,Object>>() {}));
            try (InputStream plan = getClass().getResourceAsStream("/dof/execution-plan.json")) {
                out.put("executionPlan",json.readValue(plan,new TypeReference<Map<String,Object>>() {}));
            }
            out.put("blockers", Arrays.asList("通用 UI 编码已验证；其余预设与游戏效果待对照", "客户端配置编解码与过滤已验证，完整发布链路待验", "Script 加密及更新协议待验证"));
            return out;
        } catch (SQLException e) { throw unavailable(); }
        catch (java.io.IOException e) { throw new IllegalStateException("功能清单无法加载", e); }
    }
    public Object ui() {
        admin();
        try (Connection c = connection()) {
            NativeUiState state = read(c);
            Map<String,Object> out = context(); out.putAll(state.inspect());
            out.put("preset", profiles.is100(state) ? "native-100" : "unrecognized");
            out.put("native100Compatible", profiles.compatible(state));
            out.put("nativePresetsCompatible", presetApplication.compatible(state));
            out.put("presets", presetCatalog.list());
            out.put("creationPresets", creationPresets.list());
            out.put("capability", "15种血槽预设、3种创建角色预设、132项布局数值、12项独立坐标、界面开关及69项功能开关；旧版配置限制迁移，保存后游戏效果待验证");
            return out;
        } catch (SQLException e) { throw unavailable(); }
    }
    public byte[] presetImage(String id) {
        admin();
        try (Connection c = connection()) { return id!=null && id.startsWith("creation-") ? creationPresets.image(id) : presetCatalog.image(id); }
        catch (SQLException e) { throw unavailable(); }
    }
    private void validate(Edit edit) {
        if (edit == null || edit.revision == null || !edit.revision.matches("[0-9a-f]{64}")) throw new DofFailure(400, "缺少有效配置版本");
        if (edit.requestId == null || !edit.requestId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new DofFailure(400, "缺少有效操作编号");
    }
    private void revision(NativeUiState state, String expected) {
        if (!state.revision().equals(expected)) throw new DofFailure(409, "界面配置已被修改，请重新读取并预览");
    }
    private NativeUiState apply(Edit edit, NativeUiState state) {
        if ((edit.coordinates != null ? 1 : 0) + (edit.switches != null ? 1 : 0) + (edit.preset != null ? 1 : 0) + (edit.features != null ? 1 : 0) + (edit.layout != null ? 1 : 0) + (edit.values != null ? 1 : 0) != 1)
            throw new DofFailure(400, "坐标、界面开关、功能开关与预设必须分别提交并预览");
        if(edit.values!=null) {
            Map<String,String> values=new LinkedHashMap<>();
            for(Map.Entry<String,JsonNode> entry:edit.values.entrySet()) {
                if(entry.getValue()==null || !entry.getValue().isTextual())throw new DofFailure(400,"原生文本设置必须提交文本，不能自动转换数值");
                values.put(entry.getKey(),entry.getValue().textValue());
            }
            return NativeFeatureValues.apply(state,values);
        }
        if(edit.layout!=null) {
            Map<String,java.math.BigDecimal> values=new LinkedHashMap<>();
            for(Map.Entry<String,JsonNode> entry:edit.layout.entrySet()) {
                JsonNode value=entry.getValue();
                if(value==null || !value.isNumber() || !Double.isFinite(value.doubleValue()))throw new DofFailure(400,"布局数值必须是有限数字");
                java.math.BigDecimal number=value.decimalValue();
                if(number.scale()>8 || number.precision()>20)throw new DofFailure(400,"布局数值精度超出范围");
                values.put(entry.getKey(),number);
            }
            return NativeUiLayout.edit(state,values);
        }
        if (edit.switches != null || edit.features != null) {
            Map<String,Boolean> values = new LinkedHashMap<>();
            for (Map.Entry<String,JsonNode> entry : (edit.switches != null ? edit.switches : edit.features).entrySet()) {
                JsonNode value = entry.getValue();
                if (value == null || !value.isBoolean()) throw new DofFailure(400, "界面开关必须为布尔值");
                values.put(entry.getKey(),value.booleanValue());
            }
            return edit.switches != null ? NativeUiSwitches.apply(state, values) : NativeFeatureSwitches.apply(state, values);
        }
        if (edit.coordinates != null) {
            if (edit.preset != null) throw new DofFailure(400, "坐标修改与预设应用须分别预览");
            Map<String,Integer> values = new LinkedHashMap<>();
            for (Map.Entry<String,JsonNode> entry : edit.coordinates.entrySet()) {
                JsonNode value = entry.getValue();
                if (value == null || !value.isIntegralNumber() || !value.canConvertToInt())
                    throw new DofFailure(400, "界面坐标必须为范围内的整数");
                values.put(entry.getKey(), value.intValue());
            }
            return NativeUiCoordinates.apply(state, values);
        }
        if(edit.preset.startsWith("creation-"))return creationPresets.apply(edit.preset,state);
        if ("native-100".equals(edit.preset) && profiles.compatible(state)) return profiles.apply(edit.preset,state);
        return presetApplication.apply(edit.preset,state);
    }
    private String encode(NativeUiState state) {
        try { return json.writeValueAsString(state.encode()); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private NativeUiState decode(String value) {
        try { return NativeUiState.decode(json.readValue(value, new TypeReference<Map<String,String>>() {})); }
        catch (Exception e) { throw new DofFailure(409, "历史快照损坏，停止恢复"); }
    }
    public Object preview(Edit edit) {
        admin(); validate(edit);
        try (Connection c = connection()) {
            NativeUiState before = read(c); revision(before, edit.revision);
            NativeUiState after = apply(edit, before);
            Map<String,Object> out = context(); out.put("revision", before.revision());
            out.put("changedFields", before.changedFields(after)); out.put("after", after.inspect()); out.put("persisted", false);
            return out;
        } catch (SQLException e) { throw unavailable(); }
    }
    private void lock(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) { s.execute("LOCK TABLES " + TABLE + " WRITE," + AUDIT + " WRITE"); }
    }
    private void unlock(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) { s.execute("UNLOCK TABLES"); }
    }
    private Map<String,Object> audit(Connection c, String requestId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT actor,operation_name,state,before_revision,after_revision,created_at,target_request,preset FROM " + AUDIT + " WHERE instance_id=? AND request_id=?")) {
            p.setString(1, instanceId); p.setString(2, requestId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return null;
                Map<String,Object> out = context(); out.put("requestId", requestId); out.put("actor", r.getLong(1));
                out.put("operation", r.getString(2)); out.put("state", r.getString(3)); out.put("beforeRevision", r.getString(4));
                out.put("afterRevision", r.getString(5)); out.put("createdAt", r.getString(6)); out.put("targetRequest", r.getString(7)); out.put("preset", r.getString(8));
                out.put("persisted", Arrays.asList("PERSISTED", "NO_CHANGE").contains(r.getString(3)));
                out.put("effectState", "not_observed");
                out.put("activation", "完整重启登录器和游戏后核对效果；历史操作不代表当前配置");
                return out;
            }
        }
    }
    private void finish(Connection c, String requestId, String state) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE " + AUDIT + " SET state=?,finished_at=CURRENT_TIMESTAMP WHERE instance_id=? AND request_id=?")) {
            p.setString(1, state); p.setString(2, instanceId); p.setString(3, requestId); p.executeUpdate();
        }
    }
    private void pending(Connection c) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT request_id FROM " + AUDIT + " WHERE instance_id=? AND state='PREPARED' LIMIT 1")) {
            p.setString(1, instanceId); try (ResultSet r = p.executeQuery()) {
                if (r.next()) throw new DofFailure(409, "上次操作结果不明，请先核对操作记录：" + r.getString(1));
            }
        }
    }
    private NativeUiState restoreTarget(Connection c, String target, NativeUiState current) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT before_json,after_revision,state,before_revision,adapter_version FROM " + AUDIT + " WHERE instance_id=? AND request_id=?")) {
            p.setString(1, instanceId); p.setString(2, target);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next() || !"PERSISTED".equals(r.getString(3))) throw new DofFailure(409, "只能恢复已确认持久化的操作");
                revision(current, r.getString(2));
                NativeUiState targetState = decode(r.getString(1));
                if (!targetState.revision().equals(r.getString(4)) || !VERSION.equals(r.getString(5)))
                    throw new DofFailure(409, "恢复快照校验或适配版本不匹配，停止恢复");
                return targetState;
            }
        }
    }
    public Object save(Edit edit, String target) {
        long actor = admin(); validate(edit);
        if (target != null && (edit.coordinates != null || edit.switches != null || edit.preset != null || edit.features != null || edit.layout != null || edit.values != null))
            throw new DofFailure(400, "恢复请求不能同时修改坐标、开关或预设");
        if (target != null && !target.matches("[0-9a-fA-F-]{36}")) throw new DofFailure(400, "恢复操作编号无效");
        boolean prepared = false;
        try (Connection c = connection()) {
            lock(c);
            try {
                String operation = target != null ? "restore" : edit.coordinates != null ? "coordinates" : edit.switches != null ? "switches" : edit.features != null ? "features" : edit.layout != null ? "layout" : edit.values != null ? "values" : "preset";
                Map<String,Object> previous = audit(c, edit.requestId);
                if (previous != null) {
                    if (!Objects.equals(previous.get("operation"), operation) || !Objects.equals(previous.get("targetRequest"), target)
                        || !Objects.equals(previous.get("beforeRevision"), edit.revision) || !Objects.equals(previous.get("preset"), target == null ? edit.preset : null)
                        || ((Number)previous.get("actor")).longValue() != actor)
                        throw new DofFailure(409, "操作编号已用于其他请求，不能重用");
                    if ("coordinates".equals(operation) || "switches".equals(operation) || "preset".equals(operation) || "features".equals(operation) || "layout".equals(operation) || "values".equals(operation)) {
                        try (PreparedStatement p = c.prepareStatement("SELECT before_json FROM " + AUDIT + " WHERE instance_id=? AND request_id=?")) {
                            p.setString(1, instanceId); p.setString(2, edit.requestId);
                            try (ResultSet r = p.executeQuery()) {
                                if (!r.next() || !apply(edit, decode(r.getString(1))).revision().equals(previous.get("afterRevision")))
                                    throw new DofFailure(409, "操作编号已用于不同界面修改，不能重用");
                            }
                        }
                    }
                    return previous;
                }
                pending(c);
                NativeUiState before = read(c); revision(before, edit.revision);
                NativeUiState after = target == null ? apply(edit, before) : restoreTarget(c, target, before);
                try (PreparedStatement p = c.prepareStatement("INSERT INTO " + AUDIT + " (instance_id,request_id,actor,adapter_version,operation_name,target_request,preset,before_revision,after_revision,before_json,after_json,state) VALUES (?,?,?,?,?,?,?,?,?,?,?,'PREPARED')")) {
                    p.setString(1, instanceId); p.setString(2, edit.requestId); p.setLong(3, actor); p.setString(4, VERSION);
                    p.setString(5, operation); p.setString(6, target); p.setString(7, target == null ? edit.preset : null);
                    p.setString(8, before.revision()); p.setString(9, after.revision()); p.setString(10, encode(before)); p.setString(11, encode(after));
                    p.executeUpdate(); prepared = true;
                }
                List<String> changed = before.changedFields(after);
                if (!changed.isEmpty()) {
                    List<String> clauses = new ArrayList<>(); for (String field : changed) clauses.add("`" + field + "`=?");
                    try (PreparedStatement p = c.prepareStatement("UPDATE " + TABLE + " SET " + String.join(",", clauses) + " WHERE ID=1")) {
                        for (int i = 0; i < changed.size(); i++) p.setBytes(i + 1, after.get(changed.get(i)));
                        if (p.executeUpdate() != 1) throw new SQLException("unexpected native row count");
                    }
                    if (!after.revision().equals(read(c).revision())) throw new SQLException("native write verification failed");
                }
                finish(c, edit.requestId, changed.isEmpty() ? "NO_CHANGE" : "PERSISTED");
                return audit(c, edit.requestId);
            } finally { unlock(c); }
        } catch (SQLException e) {
            if (prepared) throw new DofFailure(503, "操作结果尚未确认；请按操作编号核对历史，不要新建请求重试：" + edit.requestId);
            throw unavailable();
        }
    }
    public Object reconcile(String requestId) {
        admin();
        if (requestId == null || !requestId.matches("[0-9a-fA-F-]{36}")) throw new DofFailure(400, "操作编号无效");
        try (Connection c = connection()) {
            lock(c);
            try {
                Map<String,Object> operation = audit(c, requestId);
                if (operation == null) throw new DofFailure(404, "操作记录不存在");
                if ("PREPARED".equals(operation.get("state"))) {
                    String revision = read(c).revision();
                    if (revision.equals(operation.get("afterRevision"))) finish(c, requestId, "PERSISTED");
                    else if (revision.equals(operation.get("beforeRevision"))) finish(c, requestId, "NOT_APPLIED");
                    else throw new DofFailure(409, "实际配置与修改前后都不一致，保留现场并停止写入，需核对其他管理操作");
                }
                return audit(c, requestId);
            } finally { unlock(c); }
        } catch (SQLException e) { throw unavailable(); }
    }
    public Object history() {
        admin();
        try (Connection c = connection()) {
            List<String> ids = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement("SELECT request_id FROM " + AUDIT + " WHERE instance_id=? ORDER BY id DESC LIMIT 100")) {
                p.setString(1, instanceId); try (ResultSet r = p.executeQuery()) { while (r.next()) ids.add(r.getString(1)); }
            }
            List<Object> result = new ArrayList<>(); for (String id : ids) result.add(audit(c, id));
            return result;
        } catch (SQLException e) { throw unavailable(); }
    }
}
