package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.*;

/** Targeted native preset edits. Does not invoke the tool's general Save, which
 * also changes robot and billing settings. Game-effect acceptance is separate.
 */
public final class NativeUiPresetApplication {
    public static final class Preset {
        public String id, layout, evidence;
        public int index;
        public boolean saveEventVerified;
        public Map<String,Integer> coordinates;
    }
    private final Map<String,Preset> presets = new LinkedHashMap<>();
    public boolean compatible(NativeUiState state) {
        try {apply("native-100",state);return true;}
        catch (DofFailure | IllegalArgumentException e) {return false;}
    }
    /** Old 100-field states require their own verified migration; never synthesize defaults. */
    public NativeUiState apply(String id,NativeUiState state) {
        try {
            if (NativeHotkeys.decode(state.text("Hotkeys")).size()<138)
                throw new DofFailure(409,"旧版界面配置需要已验证的迁移，不能自动补齐默认值");
        } catch (IllegalArgumentException e) {throw new DofFailure(409,"界面配置编码不兼容，停止预设应用");}
        return candidate(id,state);
    }
    public NativeUiPresetApplication() {
        try (InputStream in = getClass().getResourceAsStream("/dof/ui-presets/application.json")) {
            List<Preset> rows = new ObjectMapper().readValue(in,new TypeReference<List<Preset>>() {});
            if (rows.size()!=15) throw new IllegalArgumentException("Incomplete native application catalog");
            for (int i=0;i<rows.size();i++) {
                Preset p=rows.get(i);
                if (p.index!=i || p.id==null || presets.put(p.id,p)!=null || p.coordinates==null
                    || p.coordinates.isEmpty() || p.layout==null || p.layout.split("&",-1).length!=110
                    || !p.layout.matches("-?[0-9]+(?:&-?[0-9]+){109}"))
                    throw new IllegalArgumentException("Invalid native preset application");
            }
        } catch (Exception e) { throw new IllegalStateException("原生预设应用证据无法加载",e); }
    }
    public NativeUiState candidate(String id, NativeUiState state) {
        Preset p=presets.get(id);
        if (p==null) throw new DofFailure(404,"血槽预设不存在");
        try {
            Map<String,Integer> layoutChanges=new LinkedHashMap<>();
            String[] replacement=p.layout.split("&",-1);
            for (int i=0;i<replacement.length;i++) layoutChanges.put("blood."+i,Integer.valueOf(replacement[i]));
            NativeUiState edited=NativeUiLayout.candidate(state,layoutChanges);
            edited=NativeUiCoordinates.apply(edited,p.coordinates);
            edited=NativeUiSwitches.apply(edited,Collections.singletonMap("bloodLayout",true));
            return edited;
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new DofFailure(409,"当前原生布局缺失或无法无损修改，停止预设应用");
        }
    }
}
