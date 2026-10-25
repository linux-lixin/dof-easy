package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.*;

public final class NativeUiLayoutControls {
    private NativeUiLayoutControls() { }
    public static final class Control {
        public String key,label,group,nativeControl,labelEvidence;
        public boolean decimal;
    }
    private static final List<Control> CONTROLS=load();
    private static List<Control> load() {
        try(InputStream in=NativeUiLayoutControls.class.getResourceAsStream("/dof/layout-controls.json")) {
            List<Control> result=new ObjectMapper().readValue(in,new TypeReference<List<Control>>(){});
            Set<String> keys=new HashSet<>();
            for(Control c:result) {
                if(c.key==null || c.label==null || c.label.isEmpty() || c.group==null || !keys.add(c.key))throw new IllegalArgumentException();
                if(c.decimal!=c.key.equals("blood.40"))throw new IllegalArgumentException();
            }
            for(int i=0;i<110;i++)if(!keys.remove("blood."+i))throw new IllegalArgumentException();
            for(int i=0;i<22;i++)if(!keys.remove("create."+i))throw new IllegalArgumentException();
            if(!keys.isEmpty())throw new IllegalArgumentException();return Collections.unmodifiableList(result);
        } catch(Exception e) {throw new IllegalStateException("原生布局控件映射无法加载",e);}
    }
    public static List<Map<String,Object>> inspect(NativeUiState state) {
        Map<String,Number> values;
        try {values=NativeUiLayout.read(state);}catch(DofFailure e) {values=Collections.emptyMap();}
        List<Map<String,Object>> result=new ArrayList<>();
        for(Control c:CONTROLS) {
            Map<String,Object> row=new LinkedHashMap<>();row.put("key",c.key);row.put("label",c.label);row.put("group",c.group);
            row.put("decimal",c.decimal);row.put("value",values.get(c.key));row.put("available",values.containsKey(c.key));
            row.put("min",Integer.MIN_VALUE);row.put("max",Integer.MAX_VALUE);result.add(row);
        }
        return result;
    }
}
