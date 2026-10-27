package com.aiyi.game.dnfserver.controller;
import com.aiyi.game.dnfserver.service.extension.RegistrationCodesService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import javax.annotation.Resource;
@RestController
@RequestMapping("api/v1/admin/registration")
public class RegistrationCodesController {
 @Resource private RegistrationCodesService service;
 @Resource private com.aiyi.game.dnfserver.service.dof.ManagedRegistrationAdmin operations;
 @Resource private com.aiyi.game.dnfserver.service.dof.RegistrationReceipts receipts;
 @GetMapping("policy") public Object policy(){return service.status();}
 @PutMapping("policy") public Object update(@RequestBody RegistrationCodesService.Policy request){return service.update(request);}
 @PostMapping("codes") public ResponseEntity<Object> create(@RequestBody RegistrationCodesService.Create request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.create(request));}
 @GetMapping("codes") public Object codes(@RequestParam(defaultValue="1")int page){return service.list(page,false);}
 @DeleteMapping("codes/{id}") public Object revoke(@PathVariable long id,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedRegistrationAdmin.Edit edit){if(edit.codeId==null||edit.codeId!=id)throw new com.aiyi.game.dnfserver.service.dof.DofFailure(400,"撤销目标与预览不同");return operations.save(edit,"revoke");}
 @GetMapping("history") public Object history(@RequestParam(defaultValue="1")int page){return service.list(page,true);}
 @PostMapping("preview") public Object preview(@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedRegistrationAdmin.Edit edit){return operations.preview(edit);}
 @GetMapping("codes/{id}") public Object codeState(@PathVariable long id){return operations.codeState(id);}
 @GetMapping("operations/{id}/current") public Object conflictState(@PathVariable String id){return operations.conflictState(id);}
 @PostMapping("operations/{id}/accept-current") public Object accept(@PathVariable String id,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedRegistrationAdmin.Edit edit){if(!id.equals(edit.targetRequestId))throw new com.aiyi.game.dnfserver.service.dof.DofFailure(400,"冲突目标与预览不同");return operations.save(edit,"accept-current");}
 @GetMapping("operations") public Object operations(){return operations.operations();}
 @PostMapping("operations/{id}/reconcile") public ResponseEntity<Object> reconcile(@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(operations.reconcile(id));}
 @GetMapping("receipts") public Object receipts(@RequestParam(defaultValue="1") int page){return receipts.list(page);}
 @GetMapping("receipts/{id}") public Object receiptState(@PathVariable String id){return operations.receiptState(id);}
 @PostMapping("receipts/{id}/reconcile") public Object receiptCheck(@PathVariable String id,@RequestBody com.aiyi.game.dnfserver.service.dof.ManagedRegistrationAdmin.Edit edit){if(!id.equals(edit.targetRequestId))throw new com.aiyi.game.dnfserver.service.dof.DofFailure(400,"注册核对目标与预览不同");return operations.save(edit,"check-receipt");}
 @ExceptionHandler(com.aiyi.game.dnfserver.service.dof.DofFailure.class) public ResponseEntity<java.util.Map<String,String>> journalFailure(com.aiyi.game.dnfserver.service.dof.DofFailure e){return ResponseEntity.status(e.status).body(java.util.Collections.singletonMap("message",e.getMessage()));}
 @ExceptionHandler(com.aiyi.game.dnfserver.service.extension.ActivityConfigService.Failure.class) public ResponseEntity<java.util.Map<String,String>> failure(com.aiyi.game.dnfserver.service.extension.ActivityConfigService.Failure e){return ResponseEntity.status(e.status).body(java.util.Collections.singletonMap("message",e.getMessage()));}
}
