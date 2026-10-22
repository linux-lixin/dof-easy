package com.aiyi.game.dnfserver.service;

import java.util.Map;

/**
 * 内辅/客户端配置 Service
 *
 * @author Copilot
 */
public interface AssistConfigService {

    /**
     * 获取内辅配置（返回结构化 Map，便于前端直接渲染表单）
     */
    Map<String, Object> getConfig();

    /**
     * 保存内辅配置（前端原样提交）
     */
    Map<String, Object> saveConfig(Map<String, Object> config);

    /**
     * 获取文本格式配置（与配置文件展示一致）
     */
    String getConfigText();
}
