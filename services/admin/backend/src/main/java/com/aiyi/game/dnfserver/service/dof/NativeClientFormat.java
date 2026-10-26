package com.aiyi.game.dnfserver.service.dof;


/** Publication metadata version; no mandatory version field on the native wire. */
public final class NativeClientFormat {
    public static final int VERSION = 2;
    public static final String VERSION_FIELD = "token_dof_version";
    public static final String CONFIG_FILE = "dof-client.dll";
    public static final String LAUNCHER_FILE = "dof-launcher.exe";
    private NativeClientFormat() {}

}
