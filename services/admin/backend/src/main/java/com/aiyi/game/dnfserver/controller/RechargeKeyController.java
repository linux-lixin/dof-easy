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

    @Resource private com.aiyi.game.dnfserver.service.dof.ManagedCdkInventoryService inventory;
    @PostMapping public Object createKey(@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedCdkInventoryService.Edit edit){return inventory.save(edit);}
    @PostMapping("validate") public Object validate(@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedCdkInventoryService.Draft edit){return inventory.validate(edit);}
    @PostMapping("preview") public Object preview(@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedCdkInventoryService.Edit edit){return inventory.preview(edit);}
    @GetMapping public Object list(@RequestParam(required=false) Integer type,@RequestParam(required=false) Integer status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String content){
        if(content!=null)throw new com.aiyi.game.dnfserver.service.dof.DofFailure(400,"卡密原文不能放入URL，请使用POST查询");
        return rechargeKeyService.list(type,status,page,pageSize,null);
    }
    @PostMapping("query") public Object query(@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedCdkInventoryService.Query query){return inventory.query(query);}
    @GetMapping("batches/history") public Object batches(){return inventory.history();}
    @GetMapping("batches/{id}") public Object batch(@PathVariable String id){return inventory.read(id);}
    @PostMapping("batches/{id}/reconcile") public Object batchReconcile(@PathVariable String id){return inventory.reconcile(id);}
    @GetMapping("exports/history") public Object exports(){return inventory.exportHistory();}
    @PostMapping("batches/{id}/export/{requestId}/reconcile") public Object exportReconcile(@PathVariable String id,@PathVariable String requestId){return inventory.exportReconcile(id,requestId);}
    @PostMapping("batches/{id}/export") public org.springframework.http.ResponseEntity<byte[]> export(@PathVariable String id,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedCdkInventoryService.ExportEdit edit){
        com.aiyi.game.dnfserver.service.dof.ManagedCdkInventoryService.Download file=inventory.export(id,edit);
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control","no-store").header("Content-Disposition","attachment; filename=website-cdk-"+file.requestId+".csv").header("X-Operation-Id",file.requestId).header("X-Artifact-SHA256",file.sha256).contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8")).body(file.bytes);
    }
    @GetMapping("inventory/{id}") public Object key(@PathVariable long id){return inventory.keyRead(id);}
    @GetMapping("inventory/{id}/history") public Object keyHistory(@PathVariable long id){return inventory.keyHistory(id);}
    @PostMapping("inventory/{id}/preview") public Object keyPreview(@PathVariable long id,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedCdkInventoryService.KeyEdit edit){return inventory.keyPreview(id,edit);}
    @PostMapping("inventory/{id}") public Object keySave(@PathVariable long id,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedCdkInventoryService.KeyEdit edit){return inventory.keySave(id,edit);}
    @PostMapping("inventory/{id}/{requestId}/reconcile") public Object keyReconcile(@PathVariable long id,@PathVariable String requestId){return inventory.reconcile(id,requestId);}

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
