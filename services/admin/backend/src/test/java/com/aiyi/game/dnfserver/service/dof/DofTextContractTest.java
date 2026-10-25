package com.aiyi.game.dnfserver.service.dof;

import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;

/** Shares the explicitly opted-in disposable MyISAM fixture, never a player database. */
public class DofTextContractTest {
    private DofTextService service;
    private DataSource ds;
    private String hotkeys;
    @Before public void setup() throws Exception {
        DofMyisamContractTest fixture = new DofMyisamContractTest(); fixture.setup();
        ds = (DataSource) ReflectionTestUtils.getField(fixture, "ds");
        service = new DofTextService(); ReflectionTestUtils.setField(service, "access", ReflectionTestUtils.getField(fixture, "service"));
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM dnf_service.QINGKONG_TEXT_AUDIT");
            for (String field : DofTextService.FIELDS) s.execute("ALTER TABLE qk.qingkong_config ADD `" + field + "` TEXT NULL");
            s.execute("UPDATE qk.qingkong_config SET name='隔离测试',Notice='旧公告',Official='https://example.test',webreg='' ");
            try (ResultSet r = s.executeQuery("SELECT MD5(Hotkeys) FROM qk.qingkong_config")) { r.next(); hotkeys = r.getString(1); }
        }
    }
    @SuppressWarnings("unchecked") private Map<String,Object> read() { return (Map<String,Object>) service.read(); }
    @SuppressWarnings("unchecked") private DofTextService.Edit edit() {
        Map<String,Object> current = read(); DofTextService.Edit e = new DofTextService.Edit();
        e.revision = current.get("revision").toString(); e.requestId = UUID.randomUUID().toString(); e.values = new LinkedHashMap<>((Map<String,String>)current.get("values")); return e;
    }
    private void rejects(int code, Runnable task) { try { task.run(); fail("expected rejection"); } catch (DofFailure e) { assertEquals(e.getMessage(), code, e.status); } }
    @Test public void homepageSaveReplayRestorePreservesHudAndNulls() throws Exception {
        String before = read().get("revision").toString(); DofTextService.Edit e = edit(); e.values.put("Notice", "测试公告\r\n第二行");
        assertEquals("PERSISTED", ((Map<?,?>)service.save(e, null)).get("state"));
        assertEquals("PERSISTED", ((Map<?,?>)service.save(e, null)).get("state"));
        Map<?,?> values = (Map<?,?>)read().get("values"); assertEquals("测试公告\r\n第二行", values.get("Notice")); assertNull(values.get("GameNotice"));
        assertEquals("not_observed", read().get("effectState"));
        DofTextService.Edit restore = edit(); restore.values = null; service.save(restore, e.requestId);
        assertEquals(before, read().get("revision"));
        try (Connection c = ds.getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT MD5(Hotkeys),HEX(unknown_setting) FROM qk.qingkong_config")) {
            r.next(); assertEquals(hotkeys, r.getString(1)); assertEquals("00FF01", r.getString(2));
        }
    }
    @Test public void staleEditRequestReuseAndExternalChangesCannotOverwrite() {
        DofTextService.Edit e = edit(), stale = edit(); e.values.put("Notice", "公告一"); service.save(e, null);
        stale.values.put("Notice", "公告二"); rejects(409, () -> service.save(stale, null));
        e.values.put("Notice", "同编号不同内容"); rejects(409, () -> service.save(e, null));
        DofTextService.Edit later = edit(); later.values.put("name", "其他管理员修改"); service.save(later, null);
        rejects(409, () -> service.save(edit(), e.requestId));
    }
    @Test public void nativeDelimiterInvalidUrlAndUnknownFieldsAreRejected() {
        DofTextService.Edit e = edit(); e.values.put("Notice", "标题=+^%^%&伪字段"); rejects(400, () -> service.preview(e));
        e.values.put("Notice", "有效公告"); e.values.put("Official", "javascript:alert(1)"); rejects(400, () -> service.preview(e));
        e.values.put("Official", "https://user:pass@example.test"); rejects(400, () -> service.preview(e));
        e.values.put("Official", "https://example.test"); e.values.put("Hotkeys", ""); rejects(400, () -> service.preview(e));
    }
    @Test public void pendingRecordRequiresReconciliationAndCorruptRestoreIsRejected() throws Exception {
        DofTextService.Edit e = edit(); e.values.put("Notice", "可核对公告"); service.save(e, null);
        try (Connection c = ds.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE dnf_service.QINGKONG_TEXT_AUDIT SET state='PREPARED' WHERE request_id=?")) {
            p.setString(1, e.requestId); p.executeUpdate();
        }
        rejects(409, () -> service.save(edit(), null));
        assertEquals("PERSISTED", ((Map<?,?>)service.reconcile(e.requestId)).get("state"));
        try (Connection c = ds.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE dnf_service.QINGKONG_TEXT_AUDIT SET before_json='{}' WHERE request_id=?")) {
            p.setString(1, e.requestId); p.executeUpdate();
        }
        rejects(409, () -> service.save(edit(), e.requestId));
    }
}
