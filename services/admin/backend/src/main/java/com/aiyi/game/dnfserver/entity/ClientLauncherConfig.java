package com.aiyi.game.dnfserver.entity;

import com.aiyi.core.annotation.po.FieldName;
import com.aiyi.core.annotation.po.ID;
import com.aiyi.core.annotation.po.TableName;
import com.aiyi.core.beans.PO;

import java.util.Date;

/**
 * 登录器配置(客户端整包信息等)
 */
@TableName(name = "`dnf_service`.`CLIENT_LAUNCHER_CONFIG`")
public class ClientLauncherConfig extends PO {

    @ID
    private int id;

    @FieldName(name = "VERSION")
    private String version;

    @FieldName(name = "DOWNLOAD_URL")
    private String downloadUrl;

    @FieldName(name = "MD5")
    private String md5;

    @FieldName(name = "REMARK")
    private String remark;

    @FieldName(name = "UPDATE_TIME")
    private Date updateTime;

    @FieldName(name = "TITLE")
    private String title;

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

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
