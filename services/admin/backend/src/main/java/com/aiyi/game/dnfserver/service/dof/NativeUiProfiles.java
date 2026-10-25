package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.*;

/** A captured native preset, not a general Hotkeys encoder. */
public final class NativeUiProfiles {
    private NativeUiState before, after;
    public NativeUiProfiles() {
        String profile=System.getProperty("qingkong.legacy.ui.profile",System.getenv("QINGKONG_LEGACY_UI_PROFILE"));
        if(profile==null||profile.isEmpty())return;
        java.nio.file.Path path=java.nio.file.Paths.get(profile).toAbsolutePath().normalize();
        try {
            if(!path.equals(path.toRealPath())||!java.nio.file.Files.isRegularFile(path)||java.nio.file.Files.size(path)>256*1024)
                throw new IllegalArgumentException("旧版 UI 私有迁移配置不兼容");
        } catch(java.io.IOException e){throw new IllegalStateException("无法读取旧版 UI 私有迁移配置",e);}
        try (InputStream in = java.nio.file.Files.newInputStream(path)) {
            Map<String,Object> document = new ObjectMapper().readValue(in, new TypeReference<Map<String,Object>>() {});
            ObjectMapper mapper = new ObjectMapper();
            before = NativeUiState.fromText(mapper.convertValue(document.get("before"), new TypeReference<Map<String,String>>() {}));
            after = NativeUiState.fromText(mapper.convertValue(document.get("after"), new TypeReference<Map<String,String>>() {}));
        } catch (Exception e) { throw new IllegalStateException("原生界面样本无法加载", e); }
    }
    public boolean compatible(NativeUiState state) {
        return before!=null && (state.revision().equals(before.revision()) || state.revision().equals(after.revision()));
    }
    public boolean is100(NativeUiState state) { return after!=null && state.revision().equals(after.revision()); }
    public NativeUiState apply(String preset, NativeUiState current) {
        if (!"native-100".equals(preset)) throw new DofFailure(409, "此预设的编码尚未验证，不能生成或保存");
        if (!compatible(current)) throw new DofFailure(409, "当前界面配置与原生对照样本不同，停止覆盖；需要先完成编码对照");
        return after;
    }
}
