package com.aiyi.game.dnfserver.service.dof;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Paired 2.8 tool writer 0x7f1400: byte XOR, uppercase hex, context MD5 slice.
 * The suffix identifies the context; it does NOT authenticate the payload.
 * Script uses the version-bound NativeScriptContext; publication still requires
 * independent verification of the native update metadata and consumer flow.
 */
public final class NativeTextEnvelope {
    private static final int MAX_BYTES = 1024;
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private NativeTextEnvelope() { }

    public static String encode(byte[] text, byte[] context) {
        checkText(text); checkText(context);
        StringBuilder out = new StringBuilder(text.length * 2 + 9);
        for (byte b : text) {
            int v = (b & 255) ^ 0x10;
            out.append(HEX[v >>> 4]).append(HEX[v & 15]);
        }
        return out.append(contextTag(context)).toString();
    }

    /** Reject a context mismatch, but do not imply payload tamper detection. */
    public static byte[] decode(String envelope, byte[] context) {
        checkText(context);
        Inspection parsed = inspect(envelope);
        if (!MessageDigest.isEqual(parsed.contextTag.getBytes(StandardCharsets.US_ASCII),
                contextTag(context).getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("原生文本参数上下文不匹配");
        return parsed.payload();
    }

    /** Structural research read only. The returned context has NOT been verified. */
    public static Inspection inspect(String envelope) {
        if (envelope == null || envelope.length() < 9 || envelope.length() > MAX_BYTES * 2 + 9
                || ((envelope.length() - 9) & 1) != 0)
            throw new IllegalArgumentException("原生文本参数长度无效");
        for (int i = 0; i < envelope.length(); i++) {
            char c = envelope.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')))
                throw new IllegalArgumentException("原生文本参数必须为大写十六进制");
        }
        byte[] payload = new byte[(envelope.length() - 9) / 2];
        for (int i = 0; i < payload.length; i++)
            payload[i] = (byte)(Integer.parseInt(envelope.substring(i * 2, i * 2 + 2), 16) ^ 0x10);
        checkText(payload);
        return new Inspection(payload, envelope.substring(envelope.length() - 9));
    }

    private static void checkText(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("原生文本参数超出长度限制");
        for (byte b : bytes) if (b == 0)
            throw new IllegalArgumentException("原生文本参数不能含 NUL");
    }

    private static String contextTag(byte[] context) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(context);
            StringBuilder hex = new StringBuilder(32);
            for (byte b : digest) hex.append(HEX[(b & 255) >>> 4]).append(HEX[b & 15]);
            return hex.substring(2, 11).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("MD5 不可用", impossible);
        }
    }

    public static final class Inspection {
        private final byte[] payload;
        private final String contextTag;
        private Inspection(byte[] payload, String contextTag) {
            this.payload = payload.clone(); this.contextTag = contextTag;
        }
        public byte[] payload() { return payload.clone(); }
        public boolean contextVerified() { return false; }
        // Do not expose content parameters through logs or automatic serialization.
        @Override public String toString() { return "NativeTextEnvelope.Inspection[unverified]"; }
    }
}
