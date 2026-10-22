package com.aiyi.game.dnfserver.entity;

import com.aiyi.core.annotation.po.FieldName;
import com.aiyi.core.annotation.po.ID;
import com.aiyi.core.annotation.po.TableName;
import com.aiyi.core.beans.PO;

/**
 * GM注册授权码实体
 * @author gsk
 */
@TableName(name = "`dnf_service`.`GM_REGIST_AUTH_CODE`")
public class GameManagerAuthKey extends PO {

    @ID
    private int id;

    /**
     * 授权码
     */
    @FieldName(name = "AUTH_CODE")
    private String authCode;

    /**
     * 使用次数
     */
    @FieldName(name = "USE_COUNT")
    private int useCount;

    /**
     * 最大使用次数
     */
    @FieldName(name = "MAX_COUNT")
    private int maxCount;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public int getUseCount() {
        return useCount;
    }

    public void setUseCount(int useCount) {
        this.useCount = useCount;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }
}
