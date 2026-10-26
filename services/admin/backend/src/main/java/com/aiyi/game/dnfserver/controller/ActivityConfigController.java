package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.service.extension.ActivityConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.*;

@RestController
@RequestMapping("api/v1/admin/activities")
public class ActivityConfigController {
    @Resource private ActivityConfigService service;
    @GetMapping public Object catalog() {return service.catalog();}
    @GetMapping("{section}") public Object list(@PathVariable String section) {return service.list(section);}
    @PostMapping("{section}/validate") public Object preview(@PathVariable String section,@RequestBody ActivityConfigService.Edit edit){return service.preview(section,edit);}
    @PutMapping("{section}") public Object save(@PathVariable String section, @RequestBody ActivityConfigService.Edit edit) {return service.save(section,edit,false);}
    @DeleteMapping("{section}") public Object remove(@PathVariable String section, @RequestBody ActivityConfigService.Edit edit) {return service.save(section,edit,true);}
    @GetMapping("{section}/history") public Object history(@PathVariable String section,@RequestParam(defaultValue="1") int legacyPage) {return service.history(section,legacyPage);}
    @GetMapping("{section}/history/{requestId}/current") public Object current(@PathVariable String section,@PathVariable String requestId){return service.currentForHistory(section,requestId);}
    @PostMapping("{section}/history/{requestId}/reconcile") public Object reconcile(@PathVariable String section,@PathVariable String requestId){return service.reconcile(section,requestId);}
    @PostMapping("{section}/history/{requestId}/restore") public Object restore(@PathVariable String section,@PathVariable String requestId,@RequestBody ActivityConfigService.Edit edit){return service.restore(section,requestId,edit);}
    @PostMapping("{section}/history/{requestId}/accept-current") public Object acceptCurrent(@PathVariable String section,@PathVariable String requestId,@RequestBody ActivityConfigService.Edit edit){return service.acceptCurrent(section,requestId,edit);}
    @GetMapping("{section}/legacy/{id}") public Object legacyCurrent(@PathVariable String section,@PathVariable long id){return service.legacyCurrent(section,id);}
    @PostMapping("{section}/legacy/{id}/preview") public Object legacyPreview(@PathVariable String section,@PathVariable long id,@RequestBody ActivityConfigService.LegacyEdit edit){return service.legacyReview(section,id,edit,false);}
    @PostMapping("{section}/legacy/{id}/confirm") public Object legacyConfirm(@PathVariable String section,@PathVariable long id,@RequestBody ActivityConfigService.LegacyEdit edit){return service.legacyReview(section,id,edit,true);}
    @PostMapping("{section}/legacy/{id}/operations/{requestId}/reconcile") public Object legacyReconcile(@PathVariable String section,@PathVariable long id,@PathVariable String requestId){return service.legacyReconcile(section,id,requestId);}
    @ExceptionHandler(com.aiyi.game.dnfserver.service.dof.DofFailure.class)
    public ResponseEntity<Map<String,String>> journalFailure(com.aiyi.game.dnfserver.service.dof.DofFailure e){return ResponseEntity.status(e.status).body(Collections.singletonMap("message",e.getMessage()));}
    @ExceptionHandler(ActivityConfigService.Failure.class)
    public ResponseEntity<Map<String,String>> failure(ActivityConfigService.Failure e) {
        return ResponseEntity.status(e.status).body(Collections.singletonMap("message",e.getMessage()));
    }
}
