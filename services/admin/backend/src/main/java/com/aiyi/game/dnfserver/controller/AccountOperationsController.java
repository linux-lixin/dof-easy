package com.aiyi.game.dnfserver.controller;
import com.aiyi.game.dnfserver.service.extension.*;
import com.aiyi.game.dnfserver.conf.NoLogin;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import javax.annotation.Resource;
import java.util.*;
@RestController
public class AccountOperationsController {
 @Resource private AccountOperationsService service;
 @GetMapping("api/v1/admin/accounts/{uid}/history") public Object history(@PathVariable long uid,@RequestParam(defaultValue="1") int page){return service.history(uid,page);}
 @Resource private com.aiyi.game.dnfserver.service.dof.ManagedBanService bans;
 @GetMapping("api/v1/admin/accounts/{uid}/ban") public Object status(@PathVariable long uid){return bans.status(uid);}
 @PostMapping("api/v1/admin/accounts/{uid}/ban/preview") public Object preview(@PathVariable long uid,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedBanService.Edit edit){return bans.preview(uid,edit);}
 @PutMapping("api/v1/admin/accounts/{uid}/ban") public Object ban(@PathVariable long uid,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedBanService.Edit edit){return bans.save(uid,edit,"ban");}
 @DeleteMapping("api/v1/admin/accounts/{uid}/ban") public Object unban(@PathVariable long uid,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedBanService.Edit edit){return bans.save(uid,edit,"unban");}
 @PostMapping("api/v1/admin/accounts/{uid}/ban/restore") public Object restore(@PathVariable long uid,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedBanService.Edit edit){return bans.save(uid,edit,"restore");}
 @PostMapping("api/v1/admin/accounts/{uid}/ban/accept-current") public Object accept(@PathVariable long uid,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedBanService.Edit edit){return bans.save(uid,edit,"accept-current");}
 @GetMapping("api/v1/admin/accounts/{uid}/ban/history") public Object banHistory(@PathVariable long uid){return bans.history(uid);}
 @PostMapping("api/v1/admin/accounts/{uid}/ban/history/{requestId}/reconcile") public Object reconcile(@PathVariable long uid,@PathVariable String requestId){return bans.reconcile(uid,requestId);}
 @ExceptionHandler(com.aiyi.game.dnfserver.service.dof.DofFailure.class) public ResponseEntity<Map<String,String>> journalFailure(com.aiyi.game.dnfserver.service.dof.DofFailure e){return ResponseEntity.status(e.status).body(Collections.singletonMap("message",e.getMessage()));}
 @Resource private com.aiyi.game.dnfserver.service.dof.ManagedPasswordService passwords;
 @GetMapping("api/v1/admin/accounts/{uid}/password-state") public Object passwordState(@PathVariable long uid){return passwords.status(uid);}
 @PostMapping("api/v1/admin/accounts/{uid}/password/preview") public Object passwordPreview(@PathVariable long uid,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedPasswordService.Edit edit){return passwords.preview(uid,edit);}
 @PutMapping("api/v1/admin/accounts/{uid}/password") public Object passwordReset(@PathVariable long uid,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedPasswordService.Edit edit){return passwords.save(uid,edit);}
 @GetMapping("api/v1/admin/accounts/{uid}/password/history") public Object passwordHistory(@PathVariable long uid){return passwords.history(uid);}
 @PostMapping("api/v1/admin/accounts/{uid}/password/history/{requestId}/reconcile") public Object passwordReconcile(@PathVariable long uid,@PathVariable String requestId){return passwords.reconcile(uid,requestId);}
 @NoLogin @GetMapping("api/v1/client/password-captcha/{index}") public void captcha(@PathVariable String index,javax.servlet.http.HttpServletResponse response)throws java.io.IOException {
  if(!index.matches("[a-zA-Z0-9-]{16,64}"))throw new ActivityConfigService.Failure(400,"验证码编号无效");
  com.wf.captcha.SpecCaptcha captcha=new com.wf.captcha.SpecCaptcha(130,48,5);
  captcha.setCharType(com.wf.captcha.base.Captcha.TYPE_NUM_AND_UPPER);
  service.issuePasswordChallenge(index,captcha.text());
  response.setContentType("image/png");response.setHeader("Cache-Control","no-store");try(java.io.OutputStream stream=response.getOutputStream()){captcha.out(stream);}
 }
 @NoLogin @PostMapping("api/v1/client/password") public Object password(@RequestBody AccountOperationsService.Password request){service.password(request);return Collections.singletonMap("message","密码已修改，请重新登录");}
 @ExceptionHandler(ActivityConfigService.Failure.class) public ResponseEntity<Map<String,String>> failure(ActivityConfigService.Failure e){return ResponseEntity.status(e.status).body(Collections.singletonMap("message",e.getMessage()));}
}
