package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.beans.ResultBean;
import com.aiyi.core.beans.ResultPage;
import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.entity.ClientLauncherBanner;
import com.aiyi.game.dnfserver.entity.ClientLauncherConfig;
import com.aiyi.game.dnfserver.entity.ClientLauncherVersion;
import com.aiyi.game.dnfserver.service.ClientLauncherService;
import com.aiyi.game.dnfserver.utils.ChinaseUtil;
import com.alibaba.fastjson.JSON;
import com.github.houbb.opencc4j.util.ZhConverterUtil;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台：登录器管理接口
 *
 * @author gsk
 */
@RestController
@RequestMapping("api/v1/admin/launcher")
public class ClientLauncherAdminController {

    @Resource
    private ClientLauncherService clientLauncherService;

    @Resource
    private com.aiyi.game.dnfserver.service.extension.ExtensionQueryService security;

    @Value("${LAUNCHER_MODE:dof}")
    private String launcherMode;

    @Value("${DOF_ADMIN_ENABLED:false}") private boolean dofAdminEnabled;
    @Value("${DOF_ENVIRONMENT:}") private String dofEnvironment;
    @Value("${DOF_INSTANCE_ID:}") private String dofInstance;

    @GetMapping("runtime")
    public Map<String, Object> runtime() {
        security.requireAdmin();
        if (!"dof".equals(launcherMode) && !"native".equals(launcherMode)) {
            throw new IllegalStateException("Invalid LAUNCHER_MODE");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", launcherMode);
        result.put("configurationOwner", "web-admin");
        result.put("nativeDevelopment", dofAdminEnabled);
        if (dofAdminEnabled) {
            result.put("environment", dofEnvironment);
            result.put("instanceId", dofInstance);
        }
        return result;
    }

    @GetMapping("config")
    public ClientLauncherConfig getConfig() {
        security.requireAdmin();
        return clientLauncherService.getConfig();
    }

    @PutMapping("config")
    public ClientLauncherConfig saveConfig(@RequestBody ClientLauncherConfig config) {
        security.requireAdmin();
        return clientLauncherService.saveConfig(config);
    }

    @GetMapping("versions")
    public ResultPage<ClientLauncherVersion> listVersions(String keyword, Integer enabled, Integer page, Integer pageSize) {
        security.requireAdmin();
        if (page == null) {
            page = 1;
        }
        if (pageSize == null) {
            pageSize = 10;
        }
        return clientLauncherService.listVersions(keyword, enabled, page, pageSize);
    }

    @PostMapping("versions")
    public ClientLauncherVersion addVersion(@RequestBody ClientLauncherVersion version) {
        security.requireAdmin();
        return clientLauncherService.addVersion(version);
    }

    @PutMapping("versions")
    public ClientLauncherVersion updateVersion(@RequestBody ClientLauncherVersion version) {
        security.requireAdmin();
        return clientLauncherService.updateVersion(version);
    }

    @DeleteMapping("versions/{id}")
    public ResultBean deleteVersion(@PathVariable int id) {
        security.requireAdmin();
        clientLauncherService.deleteVersion(id);
        return ResultBean.success();
    }

    @GetMapping("banners")
    public List<ClientLauncherBanner> listBanners(Integer enabled) {
        security.requireAdmin();
        return clientLauncherService.listBanners(enabled);
    }

    @PostMapping("banners")
    public ClientLauncherBanner addBanner(@RequestBody ClientLauncherBanner banner) {
        security.requireAdmin();
        return clientLauncherService.addBanner(banner);
    }

    @PutMapping("banners")
    public ClientLauncherBanner updateBanner(@RequestBody ClientLauncherBanner banner) {
        security.requireAdmin();
        return clientLauncherService.updateBanner(banner);
    }

    @DeleteMapping("banners/{id}")
    public ResultBean deleteBanner(@PathVariable int id) {
        security.requireAdmin();
        clientLauncherService.deleteBanner(id);
        return ResultBean.success();
    }
}
