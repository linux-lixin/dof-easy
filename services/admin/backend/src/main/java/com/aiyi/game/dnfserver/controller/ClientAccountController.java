package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.entity.ClientLauncherBanner;
import com.aiyi.game.dnfserver.entity.ClientLauncherConfig;
import com.aiyi.game.dnfserver.entity.ClientLauncherVersion;
import com.aiyi.game.dnfserver.service.AccountService;
import com.aiyi.game.dnfserver.service.ClientLauncherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * 客户端账号操作Web接口
 * @author gsk
 */
@RestController
public class ClientAccountController {

    @Resource
    private AccountService accountService;

    @Resource
    private ClientLauncherService clientLauncherService;

    /**
     * 客户端用户登录接口
     * @param accountVO 要登录的用户
     * @return 该用户的客户端授权Key
     */
    @RequestMapping("client/login")
    @NoLogin
    public String loginClient(AccountVO accountVO){
        return accountService.loginClient(accountVO, false);
    }

    /**
     * 登录
     * @param accountVO 账号信息
     * @return token
     */
    @PostMapping("api/v1/client/login")
    @NoLogin
    public Map<String, String> loginClieng2(@RequestBody AccountVO accountVO){
        Map<String, String> map = new HashMap<>();
        map.put("token", accountService.loginClient(accountVO, true));
        return map;
    }

    /**
     * 注册
     * @param accountVO
     *          要注册的账号信息
     * @return 注册结果
     */
    @GetMapping("client/register")
    @NoLogin
    public String register(AccountVO accountVO,javax.servlet.http.HttpServletRequest request){
        accountVO.setRegistrationClientIp(com.aiyi.game.dnfserver.service.extension.RegistrationClientAddress.resolve(request));
        accountService.register(accountVO);
        return "注册成功";
    }

    /**
     * 注册
     * @param accountVO 账号信息
     * @return message
     */
    @PostMapping("api/v1/client/register")
    @NoLogin
    public Map<String, Object> register2(@RequestBody AccountVO accountVO,javax.servlet.http.HttpServletRequest request){
        accountVO.setRegistrationClientIp(com.aiyi.game.dnfserver.service.extension.RegistrationClientAddress.resolve(request));
        accountService.register(accountVO);
        Map<String, Object> map = new HashMap<>();
        map.put("message", "注册成功");
        map.put("requestId",accountVO.getRegistrationRequestId());
        return map;
    }


    /**
     * 获取完整客户端安装包信息
     */
    @GetMapping("api/v1/client/full-package")
    @NoLogin
    public Map<String, Object> getFullPackage(){
        ClientLauncherConfig cfg = clientLauncherService.getConfig();
        Map<String, Object> map = new HashMap<>();
        map.put("version", cfg.getVersion());
        map.put("downloadUrl", cfg.getDownloadUrl());
        map.put("sha256", cfg.getSha256());
        if (cfg.getMd5() != null && !cfg.getMd5().trim().isEmpty()) {
            map.put("md5", cfg.getMd5());
        }
        return map;
    }

    /**
     * 获取客户端更新信息（返回最新启用版本）
     */
    @GetMapping("api/v1/client/version")
    @NoLogin
    public Map<String, Object> getClientUpdateInfo(){
        ClientLauncherVersion v = clientLauncherService.getLatestEnabledVersion();
        Map<String, Object> map = new HashMap<>();
        if (v == null) {
            // 没有配置时保持兼容：返回空对象
            map.put("version", "");
            map.put("downloadUrl", "");
            map.put("description", "");
            return map;
        }
        map.put("version", v.getVersion());
        map.put("downloadUrl", v.getDownloadUrl());
        map.put("sha256", v.getSha256());
        map.put("description", v.getDescription() == null ? "" : v.getDescription());
        map.put("forceUpdate", v.getForceUpdate() != null && v.getForceUpdate() == 1);
        return map;
    }

    /**
     * 获取客户端更新列表（根据客户端版本获取更新列表）
     */
    @NoLogin
    @GetMapping("api/v1/client/version/{currentVersion}")
    public List<ClientLauncherVersion> getClientUpdateList(@PathVariable String currentVersion){
        return clientLauncherService.listUpdateVersions(currentVersion);
    }

    /**
     * 获取客户端大图列表
     */
    @GetMapping("api/v1/client/big-pic-list")
    @NoLogin
    public List<Map<String, Object>> getClientBigPictureList(){
        List<ClientLauncherBanner> banners = clientLauncherService.listEnabledBanners();
        List<Map<String, Object>> list = new ArrayList<>();
        for (ClientLauncherBanner b : banners) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", b.getId());
            m.put("title", b.getTitle());
            m.put("imageUrl", b.getImageUrl());
            list.add(m);
        }
        return list;
    }
    @ExceptionHandler(com.aiyi.game.dnfserver.service.dof.DofFailure.class)
    public org.springframework.http.ResponseEntity<Map<String,String>> journalFailure(com.aiyi.game.dnfserver.service.dof.DofFailure e){return org.springframework.http.ResponseEntity.status(e.status).body(Collections.singletonMap("message",e.getMessage()));}
    @ExceptionHandler(com.aiyi.game.dnfserver.service.extension.ActivityConfigService.Failure.class)
    public org.springframework.http.ResponseEntity<Map<String,String>> operationFailure(com.aiyi.game.dnfserver.service.extension.ActivityConfigService.Failure e){return org.springframework.http.ResponseEntity.status(e.status).body(Collections.singletonMap("message",e.getMessage()));}
    @ExceptionHandler(com.aiyi.core.exception.ValidationException.class)
    public org.springframework.http.ResponseEntity<Map<String,String>> validationFailure(com.aiyi.core.exception.ValidationException e){return org.springframework.http.ResponseEntity.badRequest().body(Collections.singletonMap("message",e.getMessage()));}
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public org.springframework.http.ResponseEntity<Map<String,String>> databaseFailure(){return org.springframework.http.ResponseEntity.status(503).body(Collections.singletonMap("message","账号操作结果待核对，请保留注册操作编号；未自动重复执行"));}
}
