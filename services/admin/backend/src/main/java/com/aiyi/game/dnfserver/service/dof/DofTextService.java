package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.sql.*;
import java.util.*;

/** Native homepage text only. Opaque/encrypted columns and gameplay switches are never written. */
@Service
public class DofTextService {
    @Resource private DofAdminService access;
    private static final String TABLE = "`dof_native`.`config`";
    private static final String AUDIT = "`dnf_service`.`DOF_TEXT_AUDIT`";
    static final List<String> FIELDS = Collections.unmodifiableList(Arrays.asList("name", "Notice", "GameNotice", "Rechar", "crowd", "Official", "webreg"));
    private static final List<String> LABELS = Arrays.asList("服务器名称", "登录公告", "游戏公告", "充值入口", "交流群入口", "官方网站", "网页注册入口");
    private final ObjectMapper json = new ObjectMapper();
    public static class Edit { public String revision, requestId; public Map<String,String> values; }

    private String instance() { return access.context().get("instanceId").toString(); }
    private String encode(Map<String,String> state) {
        try { return json.writeValueAsString(state); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private String revision(Map<String,String> state) { return NativeUiState.sha256(encode(state).getBytes(StandardCharsets.UTF_8)); }
    private Map<String,String> decode(String text) {
        try {
            Map<String,String> map = json.readValue(text, new TypeReference<Map<String,String>>() {});
            if (!map.keySet().equals(new HashSet<>(FIELDS))) throw new IllegalArgumentException();
            Map<String,String> canonical = new LinkedHashMap<>();
            for (String key : FIELDS) {
                String value = map.get(key);
                if (value != null) Base64.getDecoder().decode(value);
                canonical.put(key, value);
            }
            return canonical;
        } catch (Exception e) { throw new DofFailure(409, "历史快照格式损坏，停止恢复"); }
    }
    private Map<String,String> read(Connection c) throws SQLException {
        StringBuilder columns = new StringBuilder("ID");
        for (String field : FIELDS) columns.append(",BINARY `").append(field).append("`");
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT " + columns + " FROM " + TABLE + " LIMIT 2")) {
            if (!r.next() || r.getInt(1) != 1) throw new DofFailure(409, "原生首页配置缺失，停止访问");
            Map<String,String> state = new LinkedHashMap<>();
            for (int i = 0; i < FIELDS.size(); i++) {
                byte[] value = r.getBytes(i + 2);
                if (value != null && value.length > 1024 * 1024) throw new DofFailure(409, "原生文本超出读取上限");
                state.put(FIELDS.get(i), value == null ? null : Base64.getEncoder().encodeToString(value));
            }
            if (r.next()) throw new DofFailure(409, "原生首页配置存在多行，停止覆盖");
            return state;
        }
    }
    private Map<String,String> texts(Map<String,String> state) {
        Map<String,String> out = new LinkedHashMap<>();
        for (String key : FIELDS) {
            String value = state.get(key);
            try {
                out.put(key, value == null ? null : StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Base64.getDecoder().decode(value))).toString());
            } catch (Exception e) { throw new DofFailure(409, "原生文本编码无法无损转换，停止修改"); }
        }
        return out;
    }
    static Map<String,String> validate(Map<String,String> values, Map<String,String> current) {
        if (values == null || !values.keySet().equals(new HashSet<>(FIELDS))) throw new DofFailure(400, "首页字段不完整或包含未知字段");
        Map<String,String> out = new LinkedHashMap<>();
        for (int i = 0; i < FIELDS.size(); i++) {
            String key = FIELDS.get(i), value = values.get(key);
            // Preserve untouched legacy values byte-for-byte, including NULL and old URL formats.
            if (Objects.equals(value, current.get(key))) { out.put(key, value); continue; }
            if (value == null) throw new DofFailure(400, LABELS.get(i) + "不能新增空值");
            int limit = i == 0 ? 120 : (i <= 2 ? 16000 : 2048);
            if (value.length() > limit || value.contains("=+^%^%&") || value.codePoints().anyMatch(n -> n == 0 || n > 65535 || (n < 32 && n != 9 && n != 10 && n != 13)))
                throw new DofFailure(400, LABELS.get(i) + "超长、包含控制字符或原生协议分隔符");
            if (i == 0 && value.trim().isEmpty()) throw new DofFailure(400, "服务器名称不能为空");
            if (i > 2 && !value.isEmpty()) {
                try {
                    URI uri = new URI(value);
                    if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null)
                        throw new IllegalArgumentException();
                } catch (Exception e) { throw new DofFailure(400, LABELS.get(i) + "必须为不含账号密码的 HTTP 或 HTTPS 地址"); }
            }
            out.put(key, value);
        }
        return out;
    }
    private Map<String,String> target(Edit edit, Map<String,String> before) {
        Map<String,String> values = validate(edit.values, texts(before));
        Map<String,String> out = new LinkedHashMap<>();
        for (String key : FIELDS) out.put(key, values.get(key) == null ? null : Base64.getEncoder().encodeToString(values.get(key).getBytes(StandardCharsets.UTF_8)));
        return out;
    }
    private void request(Edit edit) {
        if (edit == null || edit.revision == null || !edit.revision.matches("[0-9a-f]{64}") || edit.requestId == null || !edit.requestId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new DofFailure(400, "缺少有效配置版本或操作编号");
    }
    private void sameRevision(Map<String,String> state, String expected) {
        if (!revision(state).equals(expected)) throw new DofFailure(409, "首页配置已变化，请重新读取并预览");
    }
    private Map<String,Object> envelope(Map<String,String> state) {
        Map<String,Object> out = access.context();
        out.put("revision", revision(state)); out.put("values", texts(state));
        List<Map<String,Object>> fields = new ArrayList<>();
        for (int i = 0; i < FIELDS.size(); i++) {
            Map<String,Object> f = new LinkedHashMap<>(); f.put("key", FIELDS.get(i)); f.put("label", LABELS.get(i)); f.put("multiline", i == 1 || i == 2); fields.add(f);
        }
        out.put("fields", fields); out.put("effectState", "not_observed");
        out.put("activation", "QK 读取原生配置后下发；客户端实际刷新时机仍待对照，保存不代表已显示");
        return out;
    }
    public Object read() {
        access.admin(); try (Connection c = access.connection()) { return envelope(read(c)); }
        catch (SQLException e) { throw new DofFailure(503, "隔离数据库或首页配置不可用"); }
    }
    private List<String> changed(Map<String,String> a, Map<String,String> b) {
        List<String> out = new ArrayList<>(); for (String key : FIELDS) if (!Objects.equals(a.get(key), b.get(key))) out.add(key); return out;
    }
    public Object preview(Edit edit) {
        access.admin(); request(edit);
        try (Connection c = access.connection()) {
            Map<String,String> before = read(c); sameRevision(before, edit.revision); Map<String,String> after = target(edit, before);
            Map<String,Object> out = envelope(after); out.put("changedFields", changed(before, after)); out.put("persisted", false); return out;
        } catch (SQLException e) { throw new DofFailure(503, "隔离数据库不可用，尚未保存"); }
    }
    private void lock(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) { s.execute("LOCK TABLES " + TABLE + " WRITE," + AUDIT + " WRITE"); }
    }
    private void unlock(Connection c) throws SQLException { try (Statement s = c.createStatement()) { s.execute("UNLOCK TABLES"); } }
    private Map<String,Object> audit(Connection c, String id, boolean snapshots) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT actor,operation_name,state,before_revision,after_revision,target_request,before_json,after_json,created_at,adapter_version FROM " + AUDIT + " WHERE instance_id=? AND request_id=?")) {
            p.setString(1, instance()); p.setString(2, id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return null;
                Map<String,Object> out = access.context(); out.put("requestId", id); out.put("actor", r.getLong(1)); out.put("operation", r.getString(2)); out.put("state", r.getString(3));
                out.put("beforeRevision", r.getString(4)); out.put("afterRevision", r.getString(5)); out.put("targetRequest", r.getString(6)); out.put("createdAt", r.getString(9));
                out.put("persisted", Arrays.asList("PERSISTED", "NO_CHANGE").contains(r.getString(3))); out.put("effectState", "not_observed");
                if (snapshots) { out.put("beforeJson", r.getString(7)); out.put("afterJson", r.getString(8)); out.put("storedAdapter", r.getString(10)); }
                return out;
            }
        }
    }
    private void finish(Connection c, String id, String state) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE " + AUDIT + " SET state=?,finished_at=CURRENT_TIMESTAMP WHERE instance_id=? AND request_id=?")) {
            p.setString(1, state); p.setString(2, instance()); p.setString(3, id); p.executeUpdate();
        }
    }
    public Object save(Edit edit, String restoreId) {
        long actor = access.admin(); request(edit);
        if (restoreId != null && !restoreId.matches("[0-9a-fA-F-]{36}")) throw new DofFailure(400, "恢复编号无效");
        boolean prepared = false;
        try (Connection c = access.connection()) {
            lock(c);
            try {
                String operation = restoreId == null ? "edit" : "restore";
                Map<String,Object> previous = audit(c, edit.requestId, true);
                if (previous != null) {
                    if (((Number)previous.get("actor")).longValue() != actor || !Objects.equals(operation, previous.get("operation")) || !Objects.equals(restoreId, previous.get("targetRequest")) || !Objects.equals(edit.revision, previous.get("beforeRevision")))
                        throw new DofFailure(409, "操作编号已用于其他请求");
                    if (restoreId == null && !Objects.equals(revision(target(edit, decode(previous.get("beforeJson").toString()))), previous.get("afterRevision")))
                        throw new DofFailure(409, "操作编号对应的内容不同");
                    return audit(c, edit.requestId, false);
                }
                try (PreparedStatement p = c.prepareStatement("SELECT request_id FROM " + AUDIT + " WHERE instance_id=? AND state='PREPARED' LIMIT 1")) {
                    p.setString(1, instance()); try (ResultSet r = p.executeQuery()) { if (r.next()) throw new DofFailure(409, "上次保存结果待核对：" + r.getString(1)); }
                }
                Map<String,String> before = read(c); sameRevision(before, edit.revision); Map<String,String> after;
                if (restoreId == null) after = target(edit, before);
                else {
                    Map<String,Object> row = audit(c, restoreId, true);
                    if (row == null || !"PERSISTED".equals(row.get("state")) || !DofAdminService.VERSION.equals(row.get("storedAdapter"))) throw new DofFailure(409, "恢复记录不存在、未完成或版本不兼容");
                    sameRevision(before, row.get("afterRevision").toString()); after = decode(row.get("beforeJson").toString());
                    sameRevision(after, row.get("beforeRevision").toString());
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO " + AUDIT + " (instance_id,request_id,actor,adapter_version,operation_name,target_request,before_revision,after_revision,before_json,after_json,state) VALUES (?,?,?,?,?,?,?,?,?,?,'PREPARED')")) {
                    p.setString(1, instance()); p.setString(2, edit.requestId); p.setLong(3, actor); p.setString(4, DofAdminService.VERSION); p.setString(5, operation); p.setString(6, restoreId);
                    p.setString(7, revision(before)); p.setString(8, revision(after)); p.setString(9, encode(before)); p.setString(10, encode(after)); p.executeUpdate(); prepared = true;
                }
                List<String> changes = changed(before, after);
                if (!changes.isEmpty()) {
                    List<String> assignments = new ArrayList<>(); for (String field : changes) assignments.add("`" + field + "`=?");
                    try (PreparedStatement p = c.prepareStatement("UPDATE " + TABLE + " SET " + String.join(",", assignments) + " WHERE ID=1")) {
                        for (int i = 0; i < changes.size(); i++) { String value = after.get(changes.get(i)); p.setBytes(i + 1, value == null ? null : Base64.getDecoder().decode(value)); }
                        if (p.executeUpdate() != 1) throw new SQLException("Unexpected native row count");
                    }
                    if (!revision(read(c)).equals(revision(after))) throw new SQLException("Native text write verification failed");
                }
                finish(c, edit.requestId, changes.isEmpty() ? "NO_CHANGE" : "PERSISTED"); return audit(c, edit.requestId, false);
            } finally { unlock(c); }
        } catch (SQLException e) { throw new DofFailure(503, prepared ? "保存结果不明，请核对操作记录，不要创建新请求重试：" + edit.requestId : "数据库或审计表不可用，尚未确认保存"); }
    }
    public Object reconcile(String id) {
        access.admin(); if (id == null || !id.matches("[0-9a-fA-F-]{36}")) throw new DofFailure(400, "操作编号无效");
        try (Connection c = access.connection()) {
            lock(c); try {
                Map<String,Object> row = audit(c, id, false); if (row == null) throw new DofFailure(404, "操作记录不存在");
                if ("PREPARED".equals(row.get("state"))) {
                    String current = revision(read(c));
                    if (current.equals(row.get("afterRevision"))) finish(c, id, "PERSISTED");
                    else if (current.equals(row.get("beforeRevision"))) finish(c, id, "NOT_APPLIED");
                    else throw new DofFailure(409, "当前首页配置与修改前后均不同，保留现场并停止覆盖");
                }
                return audit(c, id, false);
            } finally { unlock(c); }
        } catch (SQLException e) { throw new DofFailure(503, "无法核对保存结果，请保留操作编号"); }
    }
    public Object history() {
        access.admin(); try (Connection c = access.connection()) {
            List<String> ids = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement("SELECT request_id FROM " + AUDIT + " WHERE instance_id=? ORDER BY id DESC LIMIT 100")) {
                p.setString(1, instance()); try (ResultSet r = p.executeQuery()) { while (r.next()) ids.add(r.getString(1)); }
            }
            List<Object> out = new ArrayList<>(); for (String id : ids) out.add(audit(c, id, false)); return out;
        } catch (SQLException e) { throw new DofFailure(503, "无法读取首页操作记录"); }
    }
}
