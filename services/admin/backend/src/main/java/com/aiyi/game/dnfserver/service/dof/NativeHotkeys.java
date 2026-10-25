package com.aiyi.game.dnfserver.service.dof;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.*;

/** Dof 2.8 Hotkeys storage codec; field meaning is deliberately separate.
 * Evidence: pinned tool UI reader 0x80079c -> 0x7d1700, key at 0xaaa640.
 * No defaults, trimming, type normalization or migration of unknown fields.
 */
public final class NativeHotkeys {
    private static final Charset GBK = Charset.forName("GBK");
    private static final int MAX_BYTES = 1024 * 1024, MAX_FIELDS = 1024;
    // Fixed format parameter embedded in the native tool, not an instance secret.
    private static final byte[] KEY = unhex("B7A2C8F6CAC7B7F1B0A2C8F8B8C16767646167616568656168616561686261686162");
    private final List<byte[]> fields;

    private NativeHotkeys(List<byte[]> fields) {
        if (fields.size() < 66 || fields.size() > MAX_FIELDS) throw invalid();
        this.fields = new ArrayList<>();
        int total = fields.size() - 1;
        for (byte[] field : fields) {
            total += field.length;
            if (total > MAX_BYTES) throw invalid();
            this.fields.add(field.clone());
        }
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("晴空界面配置编码或长度不兼容，未修改配置");
    }
    private static byte[] unhex(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_BYTES * 2 || value.length() % 2 != 0) throw invalid();
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            char a = value.charAt(i * 2), b = value.charAt(i * 2 + 1);
            if (!isHex(a) || !isHex(b)) throw invalid();
            result[i] = (byte) ((Character.digit(a, 16) << 4) | Character.digit(b, 16));
        }
        return result;
    }
    private static boolean isHex(char c) { return c >= '0' && c <= '9' || c >= 'A' && c <= 'F'; }
    public static NativeHotkeys decode(String wire) {
        byte[] plain = NativeConfigContainer.cipher(unhex(wire), KEY);
        List<byte[]> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < plain.length; i++) if (plain[i] == '*') {
            if (parts.size() >= MAX_FIELDS - 1) throw invalid();
            parts.add(Arrays.copyOfRange(plain, start, i)); start = i + 1;
        }
        parts.add(Arrays.copyOfRange(plain, start, plain.length));
        return new NativeHotkeys(parts);
    }
    public int size() { return fields.size(); }
    public byte[] raw(int index) { return fields.get(index).clone(); }
    public String text(int index) {
        try {
            return GBK.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(fields.get(index))).toString();
        } catch (CharacterCodingException e) { throw invalid(); }
    }
    /** Internal adapter operation; never expose arbitrary indices as admin controls. */
    public NativeHotkeys withText(Map<Integer,String> edits) {
        if (edits == null) throw invalid();
        List<byte[]> changed = new ArrayList<>(fields);
        for (Map.Entry<Integer,String> edit : edits.entrySet()) {
            Integer index = edit.getKey(); String value = edit.getValue();
            if (index == null || index < 0 || index >= fields.size() || value == null
                || value.length() > MAX_BYTES || value.indexOf('*') >= 0 || value.indexOf('\0') >= 0) throw invalid();
            try {
                ByteBuffer encoded = GBK.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
                byte[] bytes = new byte[encoded.remaining()]; encoded.get(bytes); changed.set(index, bytes);
            } catch (CharacterCodingException e) { throw invalid(); }
        }
        return new NativeHotkeys(changed);
    }
    public String encode() {
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        for (int i = 0; i < fields.size(); i++) {
            if (i != 0) plain.write('*');
            byte[] field = fields.get(i); plain.write(field, 0, field.length);
        }
        byte[] cipher = NativeConfigContainer.cipher(plain.toByteArray(), KEY);
        char[] digits = "0123456789ABCDEF".toCharArray(), result = new char[cipher.length * 2];
        for (int i = 0; i < cipher.length; i++) {
            result[2 * i] = digits[(cipher[i] & 255) >>> 4]; result[2 * i + 1] = digits[cipher[i] & 15];
        }
        return new String(result);
    }
}
