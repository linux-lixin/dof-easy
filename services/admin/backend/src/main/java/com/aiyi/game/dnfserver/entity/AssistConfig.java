package com.aiyi.game.dnfserver.entity;

import com.aiyi.core.annotation.po.FieldName;
import com.aiyi.core.annotation.po.ID;
import com.aiyi.core.annotation.po.TableName;
import com.aiyi.core.beans.PO;

import java.util.Date;

/**
 * 内辅配置
 * @author gsk
 */
@TableName(name = "`dnf_service`.`ASSIST_CONFIG`")
public class AssistConfig extends PO {

    @ID
    private int id;

    /**
     * JSON 配置内容
     */
    @FieldName(name = "CONFIG_JSON")
    private String configJson;

    @FieldName(name = "UPDATE_TIME")
    private Date updateTime;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public java.util.Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(java.util.Date updateTime) {
        this.updateTime = updateTime;
    }
}
