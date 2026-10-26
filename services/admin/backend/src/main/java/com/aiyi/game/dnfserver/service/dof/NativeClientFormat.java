package com.aiyi.game.dnfserver.service.dof;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Client wire version is independent of the existing database adapter version. */
public final class NativeClientFormat {
    public static final int VERSION = 2;
    public static final String VERSION_FIELD = "token_dof_version";
    public static final String CONFIG_FILE = "dof-client.dll";
    public static final String LAUNCHER_FILE = "dof-launcher.exe";
    private NativeClientFormat() {}

    static void require(NativeConfigContainer.Document document) {
        int count = 0;
        for (NativeConfigContainer.Entry entry : document.entries) {
            if (Arrays.equals(entry.name(), VERSION_FIELD.getBytes(StandardCharsets.US_ASCII))) {
                if (!Arrays.equals(entry.kind(), "TXT".getBytes(StandardCharsets.US_ASCII))
                    || !Arrays.equals(entry.value(), "2".getBytes(StandardCharsets.US_ASCII)))
                    throw new IllegalArgumentException("客户端配置格式版本不匹配");
                count++;
            }
        }
        if (count != 1) throw new IllegalArgumentException("客户端配置格式版本缺失或重复");
    }
}
