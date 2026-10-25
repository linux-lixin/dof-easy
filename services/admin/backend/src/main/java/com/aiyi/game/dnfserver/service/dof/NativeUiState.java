package com.aiyi.game.dnfserver.service.dof;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Lossless native storage envelope; preserves nulls and all unmodified raw columns. */
public final class NativeUiState {
    public static final List<String> COLUMNS = Collections.unmodifiableList(Arrays.asList("Hotkeys", "temp_2", "temp_3"));
    private final Map<String,byte[]> fields = new LinkedHashMap<>();

    public NativeUiState(Map<String,byte[]> input) {
        if (input == null || !input.keySet().equals(new HashSet<>(COLUMNS)))
            throw new IllegalArgumentException("界面配置字段不完整");
        for (String key : COLUMNS) {
            byte[] value = input.get(key);
            if (value != null && value.length > 1024 * 1024) throw new IllegalArgumentException("界面配置超出读取上限");
            fields.put(key, value == null ? null : value.clone());
        }
    }
    public byte[] get(String key) {
        if (!fields.containsKey(key)) throw new IllegalArgumentException("未知界面字段");
        byte[] value = fields.get(key);
        return value == null ? null : value.clone();
    }
    public String text(String key) {
        byte[] value = get(key);
        if (value == null) return null;
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString();
        } catch (CharacterCodingException e) { throw new IllegalArgumentException("原生界面编码无法无损读取，停止修改"); }
    }
    public String revision() {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(buffer);
            for (String key : COLUMNS) {
                stream.writeUTF(key);
                byte[] value = fields.get(key);
                stream.writeInt(value == null ? -1 : value.length);
                if (value != null) stream.write(value);
            }
            return sha256(buffer.toByteArray());
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    public static String sha256(byte[] bytes) {
        try {
            StringBuilder out = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format("%02x", b & 255));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public Map<String,String> encode() {
        Map<String,String> out = new LinkedHashMap<>();
        for (String key : COLUMNS) out.put(key, fields.get(key) == null ? null : Base64.getEncoder().encodeToString(fields.get(key)));
        return out;
    }
    public static NativeUiState decode(Map<String,String> input) {
        if (input == null || !input.keySet().equals(new HashSet<>(COLUMNS))) throw new IllegalArgumentException("恢复记录字段不完整");
        Map<String,byte[]> out = new LinkedHashMap<>();
        for (String key : COLUMNS) out.put(key, input.get(key) == null ? null : Base64.getDecoder().decode(input.get(key)));
        return new NativeUiState(out);
    }
    public static NativeUiState fromText(Map<String,String> input) {
        if (input == null || !input.keySet().equals(new HashSet<>(COLUMNS))) throw new IllegalArgumentException("原生样本字段不完整");
        Map<String,byte[]> out = new LinkedHashMap<>();
        for (String key : COLUMNS) out.put(key, input.get(key) == null ? null : input.get(key).getBytes(StandardCharsets.UTF_8));
        return new NativeUiState(out);
    }
    public List<String> changedFields(NativeUiState other) {
        List<String> changed = new ArrayList<>();
        for (String key : COLUMNS) if (!Arrays.equals(fields.get(key), other.fields.get(key))) changed.add(key);
        return changed;
    }
    public Map<String,Object> inspect() {
        Map<String,Object> out = new LinkedHashMap<>();
        String switches = text("temp_2"), coordinates = text("temp_3");
        String[] flags = switches == null ? new String[0] : switches.split("##", -1);
        out.put("bloodLayoutEnabled", flags.length > 0 && "1".equals(flags[0]));
        out.put("createRoleLayoutEnabled", flags.length > 1 && "1".equals(flags[1]));
        out.put("switchFormatRecognized", flags.length == 2 && Arrays.stream(flags).allMatch(v -> v.equals("0") || v.equals("1")));
        out.put("coordinateSections", coordinates == null ? Collections.emptyList() : Arrays.asList(coordinates.split("##", -1)));
        out.put("revision", revision());
        Map<String,Object> sizes = new LinkedHashMap<>();
        for (String key : COLUMNS) sizes.put(key, fields.get(key) == null ? null : fields.get(key).length);
        out.put("nativeBytes", sizes);
        try {
            NativeHotkeys hotkeys = NativeHotkeys.decode(text("Hotkeys"));
            out.put("hotkeysCodec", "qingkong-2.8-gbk-rc4-hex");
            out.put("hotkeysFields", hotkeys.size());
        } catch (IllegalArgumentException e) { out.put("hotkeysCodec", "unrecognized"); }
        out.put("coordinates", NativeUiCoordinates.inspect(this));
        out.put("switches", NativeUiSwitches.inspect(this));
        out.put("featureSwitches", NativeFeatureSwitches.inspect(this));
        out.put("featureValues", NativeFeatureValues.inspect(this));
        out.put("layoutFields", NativeUiLayoutControls.inspect(this));
        out.put("effectState", "not_observed");
        out.put("activation", "完整退出并重新启动晴空登录器和游戏；保存成功不等于已经生效");
        return out;
    }
}
