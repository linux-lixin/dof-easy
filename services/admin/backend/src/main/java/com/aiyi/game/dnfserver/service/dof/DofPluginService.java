package com.aiyi.game.dnfserver.service.dof;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** File editing is bound to the same verified instance as database management. */
@Service
public class DofPluginService {
    @Resource private DofAdminService access;
    @Value("${DOF_GAME_CONFIG_ROOT:}") private String configRoot;
    @Value("${DOF_PLUGIN_AUDIT_ROOT:/app/data/plugin-audit}") private String auditRoot;
    @Value("${PLUGIN_MODE:none}") private String pluginMode;
    @Value("${RAID_EXTENSION:off}") private String raidExtension;
    @Value("${LAUNCHER_MODE:dof}") private String launcherMode;

    private NativePluginFiles files() throws IOException {
        if(configRoot==null || configRoot.isEmpty())throw new DofFailure(409,"当前实例未挂载插件配置目录，请按部署说明启用插件管理");
        Path root=Paths.get(configRoot).toAbsolutePath().normalize(),marker=root.resolve(".admin-instance.json");
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS) || !root.equals(root.toRealPath()) || Files.isSymbolicLink(marker)
            || !Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS) || Files.size(marker)>4096)
            throw new DofFailure(409,"插件配置目录缺少可信的实例标记，请使用当前安装器核对并绑定目录");
        Map<?,?> binding;
        try(java.io.InputStream in=Files.newInputStream(marker,LinkOption.NOFOLLOW_LINKS)){binding=new ObjectMapper().readValue(in,Map.class);}
        if(binding.size()!=3 || !Objects.equals(binding.get("instanceId"),access.context().get("instanceId"))
            || !Objects.equals(binding.get("databaseUuid"),access.boundDatabaseUuid()) || !"isolated".equals(binding.get("environment")))
            throw new DofFailure(409,"插件文件目录与当前数据库实例不匹配，拒绝读写");
        return new NativePluginFiles(Paths.get(configRoot),Paths.get(auditRoot),access.context().get("instanceId").toString());
    }
    private interface Action {Object run()throws IOException;}
    private Object guarded(Action action) {
        access.admin();
        try(Connection ignored=access.boundConnection()){return action.run();}
        catch(SQLException e){throw new DofFailure(503,"实例数据库绑定暂不可核对，未确认操作结果");}
        catch(IOException e){throw new DofFailure(503,"插件配置文件或审计暂不可用；保存中断时请保留操作编号并核对结果");}
    }
    public Object catalog() {
        return guarded(()->{
            Map<String,Object> out=access.context();out.put("launcherMode",launcherMode);out.put("pluginMode",pluginMode);out.put("raidExtension",raidExtension);
            List<Map<String,Object>> modules=new ArrayList<>();
            for(NativePluginConfig.Module module:NativePluginConfig.modules()) {
                Map<String,Object> row=new LinkedHashMap<>();row.put("id",module.id);row.put("title",module.title);row.put("fieldCount",module.fields.size());
                row.put("executor",module.id.equals("plugin")?"libdof-plugin.so":"libdof-plugin.so + DP2");
                row.put("effectState","not_observed");
                row.put("configuredEnabled",module.id.equals("plugin")?"native".equals(launcherMode)||"on".equals(raidExtension):"on".equals(raidExtension));
                row.put("dependencyReady",module.id.equals("plugin")||Arrays.asList("dp2","dp2-frida").contains(pluginMode));
                try{files().read(module.id);row.put("available",true);}
                catch(DofFailure e){row.put("available",false);row.put("reason",e.getMessage());}
                modules.add(row);
            }
            out.put("modules",modules);return out;
        });
    }
    public Object read(String module){return guarded(()->{
        Map<String,Object> out=files().read(module);
        Map<String,Object> application=new LinkedHashMap<>();
        application.put("runtimeObserved",false);application.put("webExecutionSupported",false);
        application.put("configuredEnabled",module.equals("plugin")?"native".equals(launcherMode)||"on".equals(raidExtension):"on".equals(raidExtension));
        application.put("dependencyReady",module.equals("plugin")||Arrays.asList("dp2","dp2-frida").contains(pluginMode));
        if(module.equals("plugin")) {
            application.put("method","component_specific");
            application.put("instruction","基础插件各项读取时机仍需逐项对照；文件保存不会自动刷新或重启游戏。");
        }else {
            String key=module+"_reload",command=null;
            for(Object value:(List<?>)out.get("fields")) {
                Map<?,?> field=(Map<?,?>)value;
                if(key.equals(field.get("key")) && Boolean.TRUE.equals(field.get("available")))command=(String)field.get("value");
            }
            application.put("method",command==null || command.trim().isEmpty()?"unavailable":"gm_chat");
            application.put("command",command);
            application.put("instruction",command==null || command.trim().isEmpty()?"当前配置缺少刷新命令；请先核对匹配的插件版本。":"由具有插件所需权限的 GM 在游戏角色聊天中输入当前命令。网页不发送聊天，不确认刷新已执行；修改刷新命令本身的读取时机需单独验证。");
        }
        out.put("application",application);return out;
    });}
    public Object preview(String module,NativePluginFiles.Edit edit){return guarded(()->files().preview(module,edit));}
    public Object save(String module,NativePluginFiles.Edit edit,String restore){return guarded(()->files().save(module,edit,access.admin(),restore));}
    public Object history(String module){return guarded(()->files().history(module));}
    public Object reconcile(String module,String id){return guarded(()->files().reconcile(module,id));}
}
