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
 @GetMapping("api/v1/admin/accounts/{uid}/ban") public Object status(@PathVariable long uid){return service.status(uid);}
 @PutMapping("api/v1/admin/accounts/{uid}/ban") public Object ban(@PathVariable long uid,@RequestBody AccountOperationsService.Ban request){service.ban(uid,request,false);return Collections.singletonMap("message","已限制后续登录；不承诺立即踢出在线角色");}
 @DeleteMapping("api/v1/admin/accounts/{uid}/ban") public Object unban(@PathVariable long uid,@RequestBody AccountOperationsService.Ban request){service.ban(uid,request,true);return Collections.singletonMap("message","已解除本后台创建的处罚，其他处罚保持不变");}
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
