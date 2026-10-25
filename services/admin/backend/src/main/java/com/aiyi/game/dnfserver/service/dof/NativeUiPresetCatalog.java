package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.*;

/** Original tool previews, selected by the original handler in the offline oracle.
 * A preview proves neither the preset write set nor its in-game effect.
 */
public final class NativeUiPresetCatalog {
    private static final String ROOT = "/dof/ui-presets/";
    private final List<Map<String,Object>> entries;
    public NativeUiPresetCatalog() {
        try (InputStream in = getClass().getResourceAsStream(ROOT + "catalog.json")) {
            List<Map<String,Object>> rows = new ObjectMapper().readValue(in, new TypeReference<List<Map<String,Object>>>() {});
            if (rows.size() != 15) throw new IOException("Incomplete native preset catalog");
            Set<String> ids = new HashSet<>();
            List<Map<String,Object>> checked = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                Map<String,Object> row = rows.get(i);
                if (!Integer.valueOf(i).equals(row.get("index")) || !ids.add((String)row.get("id"))
                    || !String.valueOf(row.get("image")).matches("[0-9a-f]+\\.png")
                    || !String.valueOf(row.get("sha256")).matches("[0-9a-f]{64}")) throw new IOException("Invalid native preset catalog");
                checked.add(Collections.unmodifiableMap(new LinkedHashMap<>(row)));
            }
            entries = Collections.unmodifiableList(checked);
        } catch (IOException | RuntimeException e) { throw new IllegalStateException("原生血槽预览目录无法加载", e); }
    }
    public List<Map<String,Object>> list() { return entries; }
    public byte[] image(String id) {
        Map<String,Object> entry = entries.stream().filter(e -> e.get("id").equals(id)).findFirst()
            .orElseThrow(() -> new DofFailure(404, "血槽预设不存在"));
        try (InputStream in = getClass().getResourceAsStream(ROOT + entry.get("image"))) {
            if (in == null) throw new IOException("Missing native preview");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > 2 * 1024 * 1024) throw new IOException("Preview exceeds bound");
                out.write(buffer, 0, n);
            }
            byte[] bytes = out.toByteArray();
            if (!NativeUiState.sha256(bytes).equals(entry.get("sha256"))) throw new IOException("Preview checksum mismatch");
            return bytes;
        } catch (IOException e) { throw new DofFailure(503, "原生血槽预览缺失或校验失败"); }
    }
}
