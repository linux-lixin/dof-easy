package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.service.impl.AssistConfigServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 后台：内辅管理
 *
 * @author Copilot
 */
@RestController
@RequestMapping("api/v1/admin/assist")
public class AssistAdminController {

    @Resource
    private AssistConfigServiceImpl assistConfigService;

    @GetMapping("config")
    public Map<String, Object> getConfig() {
        return assistConfigService.getConfig();
    }

    @PutMapping("config")
    public Map<String, Object> saveConfig(@RequestBody Map<String, Object> config) {
        return assistConfigService.saveConfig(config);
    }

    /**
     * 获取文本格式配置（用于直接落盘/对照配置文件）
     */
    @GetMapping(value = "config-text", produces = "text/plain; charset=utf-8")
    @NoLogin
    public String getConfigText() {
        return assistConfigService.getConfigText();
    }
}
