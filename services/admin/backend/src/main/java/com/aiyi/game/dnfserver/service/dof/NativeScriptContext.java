package com.aiyi.game.dnfserver.service.dof;

/**
 * Text parameter context from the pinned Dof 2.8 client DLL at 0x104d419c.
 * This is a public format constant, not an instance identity or content key.
 * The opaque payload is preserved: its first field is not a compression flag.
 * This codec alone does not establish native update metadata or publishability.
 */
public final class NativeScriptContext {
    public static final String CLIENT_DLL_SHA256 =
            "dd5b3a9be419b910fb82f01d432fdf9a12d374d491c554e10103ba345942f66b";
    private static final byte[] CONTEXT = {
        'J', 'K', 'G', 'E', 'F', '*', '^', '^', '&', '%', '^', '&', '%', '^', '^',
        (byte)0xb0, (byte)0xa1, (byte)0xce, (byte)0xf7, (byte)0xb0, (byte)0xd1,
        'm', 'd', 'd', 'd', 'n', 'f', 'r', 'h'
    };

    private NativeScriptContext() { }

    public static String encode(byte[] payload, String clientDllSha256) {
        requireVersion(clientDllSha256);
        return NativeTextEnvelope.encode(payload, CONTEXT);
    }

    public static byte[] decode(String envelope, String clientDllSha256) {
        requireVersion(clientDllSha256);
        return NativeTextEnvelope.decode(envelope, CONTEXT);
    }

    private static void requireVersion(String sha256) {
        if (!CLIENT_DLL_SHA256.equals(sha256))
            throw new IllegalArgumentException("Script 参数适配器与客户端版本不匹配");
    }
}
