package com.aiyi.game.dnfserver.service;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.game.dnfserver.entity.ClientLauncherBanner;
import com.aiyi.game.dnfserver.entity.ClientLauncherConfig;
import com.aiyi.game.dnfserver.entity.ClientLauncherVersion;

import java.util.List;

/**
 * 登录器管理业务
 */
public interface ClientLauncherService {

    ClientLauncherConfig getConfig();

    ClientLauncherConfig saveConfig(ClientLauncherConfig config);

    ResultPage<ClientLauncherVersion> listVersions(String keyword, Integer enabled, int page, int pageSize);

    ClientLauncherVersion addVersion(ClientLauncherVersion version);

    ClientLauncherVersion updateVersion(ClientLauncherVersion version);

    void deleteVersion(int id);

    /**
     * 客户端获取最新启用版本
     */
    ClientLauncherVersion getLatestEnabledVersion();

    List<ClientLauncherBanner> listEnabledBanners();

    List<ClientLauncherBanner> listBanners(Integer enabled);

    ClientLauncherBanner addBanner(ClientLauncherBanner banner);

    ClientLauncherBanner updateBanner(ClientLauncherBanner banner);

    void deleteBanner(int id);

    /**
     * 列出指定客户端版本可更新的版本列表
     */
    List<ClientLauncherVersion> listUpdateVersions(String clientVersion);
}
