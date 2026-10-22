package com.aiyi.game.dnfserver.entity;

import com.aiyi.core.annotation.po.FieldName;
import com.aiyi.core.annotation.po.ID;
import com.aiyi.core.annotation.po.TableName;
import com.aiyi.core.beans.PO;

import java.util.Date;

/**
 * 登录器版本(更新包)
 */
@TableName(name = "`dnf_service`.`CLIENT_LAUNCHER_VERSION`")
public class ClientLauncherVersion extends PO {

    @ID
    private int id;

    @FieldName(name = "VERSION")
    private String version;

    @FieldName(name = "DOWNLOAD_URL")
    private String downloadUrl;

    @FieldName(name = "DESCRIPTION")
    private String description;

    @FieldName(name = "FORCE_UPDATE")
    private Integer forceUpdate;

    @FieldName(name = "ENABLED")
    private Integer enabled;

    @FieldName(name = "CREATE_TIME")
    private Date createTime;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getForceUpdate() {
        return forceUpdate;
    }

    public void setForceUpdate(Integer forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
