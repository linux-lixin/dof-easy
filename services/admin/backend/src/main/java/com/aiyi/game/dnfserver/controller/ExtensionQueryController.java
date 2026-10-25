package com.aiyi.game.dnfserver.controller;
import com.aiyi.game.dnfserver.service.extension.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import javax.annotation.Resource;
import java.util.*;
@RestController
@RequestMapping("api/v1/admin/extension")
public class ExtensionQueryController {
 @Resource private ExtensionQueryService service;
 @GetMapping("records") public Object catalog(){return service.catalog();}
 @GetMapping("records/{key}") public Object records(@PathVariable String key,@RequestParam(required=false)Long owner,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="50")int size){return service.records(key,owner,page,size);}
 @GetMapping("companion-preview") public Object companion(){return service.companion();}
 @GetMapping("features") public Object features(){return service.features();}
 @GetMapping("clock") public Object clock(){return service.clock();}
 @ExceptionHandler(ActivityConfigService.Failure.class) public ResponseEntity<Map<String,String>> failure(ActivityConfigService.Failure e){return ResponseEntity.status(e.status).body(Collections.singletonMap("message",e.getMessage()));}
}
