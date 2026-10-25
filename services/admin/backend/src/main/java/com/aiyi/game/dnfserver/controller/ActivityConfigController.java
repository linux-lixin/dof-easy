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
    @GetMapping("{section}/history") public Object history(@PathVariable String section) {return service.history(section);}
    @ExceptionHandler(ActivityConfigService.Failure.class)
    public ResponseEntity<Map<String,String>> failure(ActivityConfigService.Failure e) {
        return ResponseEntity.status(e.status).body(Collections.singletonMap("message",e.getMessage()));
    }
}
