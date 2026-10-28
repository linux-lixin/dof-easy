package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.entity.CharacInfo;
import com.aiyi.game.dnfserver.service.CharacService;
import com.aiyi.game.dnfserver.utils.ChinaseUtil;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/13 14:33
 */
@RestController
@RequestMapping("api/v1/charac")
public class CharacInfoController {

    @Resource
    private CharacService characService;

    @GetMapping
    public ResultPage<CharacInfo> list(Integer minLev, Integer maxLevel, Integer job,
                                       String name, String account, Integer page, Integer pageSize){
        if (null == page) {
            page = 1;
        }
        if (null == pageSize) {
            pageSize = 10;
        }
        ResultPage<CharacInfo> list = characService.list(minLev, maxLevel, job, name, account, page, pageSize);
        for (CharacInfo info: list.getList()){
            ChinaseUtil.toSimple(info);
        }
        return list;
    }

    /**
     * 列出某个账号的角色列表
     * @param account
     *      账号
     * @return
     */
    @GetMapping("{account}")
    @NoLogin
    public List<CharacInfo> listByAccount(@PathVariable String account){
        return characService.list(account);
    }

    @Resource
    private com.aiyi.game.dnfserver.service.dof.ManagedRoleService managedRoles;

    @GetMapping("{characNo}/edit")
    public Object edit(@PathVariable long characNo){return managedRoles.read(characNo);}

    @PostMapping("{characNo}/preview")
    public Object preview(@PathVariable long characNo,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedRoleService.Edit edit){return managedRoles.preview(characNo,edit);}

    @PutMapping
    public Object update(@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedRoleService.Edit edit){return managedRoles.save(edit);}

    @GetMapping("{characNo}/history")
    public Object history(@PathVariable long characNo){return managedRoles.history(characNo);}

    @PostMapping("{characNo}/history/{requestId}/reconcile")
    public Object reconcile(@PathVariable long characNo,@PathVariable String requestId){return managedRoles.reconcile(characNo,requestId);}

    @ExceptionHandler(com.aiyi.game.dnfserver.service.dof.DofFailure.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String,String>> managementFailure(com.aiyi.game.dnfserver.service.dof.DofFailure e){
        return org.springframework.http.ResponseEntity.status(e.status).body(java.util.Collections.singletonMap("message",e.getMessage()));
    }

    /**
     * 角色完成已接受的所有任务
     * @param characId
     *      角色ID
     */
    @PostMapping("{characId}/overTasks")
    public void overTasks(@PathVariable Long characId){
        characService.overTasks(characId);
    }

}
