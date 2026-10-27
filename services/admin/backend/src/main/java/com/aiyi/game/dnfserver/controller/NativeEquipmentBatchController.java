package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.service.extension.*;
import com.aiyi.game.dnfserver.service.dof.DofFailure;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/activities/{section}/batches")
public class NativeEquipmentBatchController {
    @Resource private NativeEquipmentBatchService service;
    public static class Version {public String revision;}
    @GetMapping("state") public Object state(@PathVariable String section){return service.state(section);}
    @GetMapping public Object history(@PathVariable String section){return service.history(section);}
    @GetMapping("{id}") public Object detail(@PathVariable String section,@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.detail(section,id,page,pageSize);}
    @PostMapping("clear/preview") public Object clear(@PathVariable String section,@RequestBody NativeEquipmentBatchService.Edit edit){return service.previewClear(section,edit);}
    @PostMapping("restore/preview") public Object restore(@PathVariable String section,@RequestBody NativeEquipmentBatchService.Edit edit){return service.previewRestore(section,edit);}
    @PostMapping(value="import/preview",consumes="application/json") public Object upload(@PathVariable String section,@RequestParam String requestId,@RequestParam String revision,@RequestParam String reason,HttpServletRequest request)throws IOException{NativeEquipmentBatchService.Edit edit=new NativeEquipmentBatchService.Edit();edit.requestId=requestId;edit.revision=revision;edit.reason=reason;return service.previewImport(section,edit,request.getInputStream());}
    @PostMapping("{id}/step") public Object step(@PathVariable String section,@PathVariable String id,@RequestBody Version v){return service.step(section,id,v.revision);}
    @GetMapping("{id}/items/{child}") public Object item(@PathVariable String section,@PathVariable String id,@PathVariable String child){return service.item(section,id,child);}
    @PostMapping("{id}/items/{child}/reconcile") public Object reconcile(@PathVariable String section,@PathVariable String id,@PathVariable String child,@RequestBody Version v){return service.reconcile(section,id,child,v.revision);}
    @GetMapping("{id}/controls") public Object controlHistory(@PathVariable String section,@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.controlHistory(section,id,page,pageSize);}
    @GetMapping("{id}/controls/{control}") public Object controlDetail(@PathVariable String section,@PathVariable String id,@PathVariable String control){return service.controlDetail(section,id,control);}
    @PostMapping("{id}/controls/preview") public Object previewControl(@PathVariable String section,@PathVariable String id,@RequestBody NativeEquipmentBatchService.Control edit){return service.control(section,id,edit,false);}
    @PostMapping("{id}/controls") public Object control(@PathVariable String section,@PathVariable String id,@RequestBody NativeEquipmentBatchService.Control edit){return service.control(section,id,edit,true);}
    @ExceptionHandler(ActivityConfigService.Failure.class) public ResponseEntity<Map<String,String>> failure(ActivityConfigService.Failure e){return ResponseEntity.status(e.status).body(Collections.singletonMap("message",e.getMessage()));}
    @ExceptionHandler(DofFailure.class) public ResponseEntity<Map<String,String>> failure(DofFailure e){return ResponseEntity.status(e.status).body(Collections.singletonMap("message",e.getMessage()));}
}
