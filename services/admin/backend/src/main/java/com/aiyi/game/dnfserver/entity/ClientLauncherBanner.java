package com.aiyi.game.dnfserver.entity;

import com.aiyi.core.annotation.po.FieldName;
import com.aiyi.core.annotation.po.ID;
import com.aiyi.core.annotation.po.TableName;
import com.aiyi.core.beans.PO;
import com.aiyi.game.dnfserver.utils.Simple;

import java.util.Date;

/**
 * 登录器大图(轮播)
 */
@TableName(name = "`dnf_service`.`CLIENT_LAUNCHER_BANNER`")
public class ClientLauncherBanner extends PO {

    @ID
    private int id;

    @Simple
    @FieldName(name = "TITLE")
    private String title;

    @Simple
    @FieldName(name = "IMAGE_URL")
    private String imageUrl;

    @FieldName(name = "SORT_NO")
    private Integer sortNo;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
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
