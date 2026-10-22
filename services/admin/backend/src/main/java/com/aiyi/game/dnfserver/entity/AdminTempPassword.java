package com.aiyi.game.dnfserver.entity;


import com.aiyi.core.annotation.po.FieldName;
import com.aiyi.core.annotation.po.ID;
import com.aiyi.core.annotation.po.TableName;
import com.aiyi.core.beans.PO;

/**
 * 管理员临时密码实体, 用于在不方便登录的时候快速操作一些事务的授权密码
 * @author gsk
 */
@TableName(name = "`dnf_service`.`ADMIN_TEMP_PASSWORD`")
public class AdminTempPassword extends PO {

    @ID
    private int id;

    @FieldName("PASSWORD")
    private String password;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
