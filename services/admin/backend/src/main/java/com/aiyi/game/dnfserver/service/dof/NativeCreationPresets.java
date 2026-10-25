package com.aiyi.game.dnfserver.service.dof;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.*;

/** Native creation-layout buttons; changes 22 coordinates without enabling gameplay. */
public final class NativeCreationPresets {
    private static final String ROOT="/dof/creation-presets/";
    private final Map<String,Map<String,Object>> entries=new LinkedHashMap<>();
    public NativeCreationPresets() {
        try(InputStream in=getClass().getResourceAsStream(ROOT+"catalog.json")) {
            List<Map<String,Object>> rows=new ObjectMapper().readValue(in,new TypeReference<List<Map<String,Object>>>(){});
            if(rows.size()!=3)throw new IOException("Incomplete creation catalog");
            for(Map<String,Object> row:rows) {
                String id=(String)row.get("id");
                if(!Arrays.asList("creation-60","creation-85","creation-100").contains(id) || entries.put(id,row)!=null
                    || !(id+".png").equals(row.get("image")) || !String.valueOf(row.get("sha256")).matches("[0-9a-f]{64}"))throw new IOException("Invalid creation catalog");
                Map<String,Integer> layout=layout(row);
                if(layout.size()!=22)throw new IOException("Incomplete creation coordinates");
                for(int i=0;i<22;i++)if(!layout.containsKey("create."+i) || layout.get("create."+i)==null)throw new IOException("Invalid creation coordinates");
            }
        }catch(IOException | RuntimeException e) {throw new IllegalStateException("创建角色预设无法加载",e);}
    }
    private Map<String,Integer> layout(Map<String,Object> row) {
        return new ObjectMapper().convertValue(row.get("layout"),new TypeReference<Map<String,Integer>>(){});
    }
    private Map<String,Object> entry(String id) {
        Map<String,Object> row=entries.get(id);if(row==null)throw new DofFailure(404,"创建角色预设不存在");return row;
    }
    public List<Map<String,Object>> list() {
        List<Map<String,Object>> result=new ArrayList<>();
        for(Map<String,Object> row:entries.values()) {
            Map<String,Object> item=new LinkedHashMap<>();
            for(String key:Arrays.asList("id","label","width","height","sha256"))item.put(key,row.get(key));
            result.add(item);
        }
        return result;
    }
    public NativeUiState apply(String id,NativeUiState state) {return NativeUiLayout.candidate(state,layout(entry(id)));}
    public byte[] image(String id) {
        Map<String,Object> row=entry(id);
        try(InputStream in=getClass().getResourceAsStream(ROOT+row.get("image"))) {
            if(in==null)throw new IOException("Missing creation image");ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1) {if(out.size()+n>2*1024*1024)throw new IOException("Image exceeds bound");out.write(buffer,0,n);}
            byte[] bytes=out.toByteArray();if(!NativeUiState.sha256(bytes).equals(row.get("sha256")))throw new IOException("Image hash mismatch");return bytes;
        }catch(IOException e) {throw new DofFailure(503,"创建角色预览缺失或校验失败");}
    }
}
