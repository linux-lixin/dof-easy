package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.beans.Method;
import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.sql.where.C;
import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.dao.AdminTempPasswordDao;
import com.aiyi.game.dnfserver.dao.GameManagerAuthKeyDao;
import com.aiyi.game.dnfserver.entity.AdminTempPassword;
import com.aiyi.game.dnfserver.entity.GameManagerAuthKey;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

/**
 * GM注册授权码控制器
 * @author gsk
 */
@RestController
@RequestMapping("api/v1/authcode")
public class AuthCodeController {

    @Resource
    private GameManagerAuthKeyDao gameManagerAuthKeyDao;

    @Resource
    private AdminTempPasswordDao adminTempPasswordDao;

    @PostMapping
    @NoLogin
    public GameManagerAuthKey generateAuthCode(@RequestBody Map<String, String> params) {
        if (!params.containsKey("password")){
            throw new ValidationException("临时密码不正确");
        }

        AdminTempPassword password = adminTempPasswordDao.get(Method
                .where(AdminTempPassword::getPassword, C.EQ, params.get("password")).limit(0, 1));
        if (password == null){
            throw new ValidationException("临时密码不正确");
        }

        GameManagerAuthKey authKey = new GameManagerAuthKey();
        authKey.setMaxCount(1);
        authKey.setAuthCode(Long.toHexString(System.currentTimeMillis()));
        gameManagerAuthKeyDao.add(authKey);
        return authKey;
    }
}
