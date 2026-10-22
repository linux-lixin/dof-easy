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

import javax.annotation.Resource;
import java.util.List;

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

    @GetMapping("config")
    public ClientLauncherConfig getConfig() {
        return clientLauncherService.getConfig();
    }

    @PutMapping("config")
    public ClientLauncherConfig saveConfig(@RequestBody ClientLauncherConfig config) {
        return clientLauncherService.saveConfig(config);
    }

    @GetMapping("versions")
    public ResultPage<ClientLauncherVersion> listVersions(String keyword, Integer enabled, Integer page, Integer pageSize) {
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
        return clientLauncherService.addVersion(version);
    }

    @PutMapping("versions")
    public ClientLauncherVersion updateVersion(@RequestBody ClientLauncherVersion version) {
        return clientLauncherService.updateVersion(version);
    }

    @DeleteMapping("versions/{id}")
    public ResultBean deleteVersion(@PathVariable int id) {
        clientLauncherService.deleteVersion(id);
        return ResultBean.success();
    }

    @GetMapping("banners")
    public List<ClientLauncherBanner> listBanners(Integer enabled) {
        return clientLauncherService.listBanners(enabled);
    }

    @PostMapping("banners")
    public ClientLauncherBanner addBanner(@RequestBody ClientLauncherBanner banner) {
        return clientLauncherService.addBanner(banner);
    }

    @PutMapping("banners")
    public ClientLauncherBanner updateBanner(@RequestBody ClientLauncherBanner banner) {
        return clientLauncherService.updateBanner(banner);
    }

    @DeleteMapping("banners/{id}")
    public ResultBean deleteBanner(@PathVariable int id) {
        clientLauncherService.deleteBanner(id);
        return ResultBean.success();
    }
}
