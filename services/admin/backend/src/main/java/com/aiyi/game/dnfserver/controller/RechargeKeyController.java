package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.entity.RechargeKey;
import com.aiyi.game.dnfserver.service.RechargeKeyService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("api/v1/keys")
public class RechargeKeyController {

    @Resource
    private RechargeKeyService rechargeKeyService;

    /**
     * 制作卡密
     * @param key
     *      卡密信息
     */
    @PostMapping
    public void createKey(@RequestBody RechargeKey key){
        rechargeKeyService.create(key);
    }


    /**
     * 列出卡密列表
     * @param type
     *      卡密类型
     * @param status
     *      卡密状态
     * @param page
     *      页码
     * @param pageSize
     *      每页条数
     * @return
     */
    @GetMapping
    public ResultPage<RechargeKey> list(Integer type, Integer status,
                                        int page, int pageSize,
                                        String content){
        return rechargeKeyService.list(type, status, page, pageSize,
                content);
    }

    /**
     * 使用卡密
     * @param key
     */
    @PostMapping("use")
    @NoLogin
    public Object use(@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedCdkService.Claim key){return managed.claim(key);}
    @Resource private com.aiyi.game.dnfserver.service.dof.ManagedCdkService managed;
    @NoLogin @PostMapping("use/reconcile") public Object reconcile(@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedCdkService.Claim key){return managed.reconcile(key);}
    @GetMapping("claims") public Object history(){return managed.history();}
    @GetMapping("claims/{requestId}") public Object read(@PathVariable String requestId){return managed.adminRead(requestId);}
    @PostMapping("claims/{requestId}/reconcile") public Object adminReconcile(@PathVariable String requestId){return managed.adminReconcile(requestId);}
    @GetMapping("claims/{source}/controls") public Object controls(@PathVariable String source){return managed.controls(source);}
    @PostMapping("claims/{source}/controls/preview") public Object controlPreview(@PathVariable String source,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedCdkService.Control edit){return managed.controlPreview(source,edit);}
    @PostMapping("claims/{source}/controls") public Object controlSave(@PathVariable String source,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedCdkService.Control edit){return managed.controlSave(source,edit);}
    @PostMapping("claims/{source}/controls/{requestId}/reconcile") public Object controlReconcile(@PathVariable String source,@PathVariable String requestId){return managed.controlReconcile(source,requestId);}
    @NoLogin @GetMapping("captcha/{index}") public void captcha(@PathVariable String index,javax.servlet.http.HttpServletResponse response)throws java.io.IOException {
        com.wf.captcha.SpecCaptcha captcha=new com.wf.captcha.SpecCaptcha(130,48,5);captcha.setCharType(com.wf.captcha.base.Captcha.TYPE_NUM_AND_UPPER);managed.issueChallenge(index,captcha.text());
        response.setContentType("image/png");response.setHeader("Cache-Control","no-store");try(java.io.OutputStream out=response.getOutputStream()){captcha.out(out);}
    }
    @ExceptionHandler(com.aiyi.game.dnfserver.service.dof.DofFailure.class) public org.springframework.http.ResponseEntity<java.util.Map<String,String>> failure(com.aiyi.game.dnfserver.service.dof.DofFailure e){return org.springframework.http.ResponseEntity.status(e.status).body(java.util.Collections.singletonMap("message",e.getMessage()));}
    @ExceptionHandler(com.aiyi.game.dnfserver.service.extension.ActivityConfigService.Failure.class) public org.springframework.http.ResponseEntity<java.util.Map<String,String>> challengeFailure(com.aiyi.game.dnfserver.service.extension.ActivityConfigService.Failure e){return org.springframework.http.ResponseEntity.status(e.status).body(java.util.Collections.singletonMap("message",e.getMessage()));}
}
