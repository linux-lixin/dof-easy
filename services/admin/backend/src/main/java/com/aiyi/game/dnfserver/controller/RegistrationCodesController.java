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
 @GetMapping("policy") public Object policy(){return service.status();}
 @PutMapping("policy") public Object update(@RequestBody RegistrationCodesService.Policy request){return service.update(request);}
 @PostMapping("codes") public ResponseEntity<Object> create(@RequestBody RegistrationCodesService.Create request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.create(request));}
 @GetMapping("codes") public Object codes(@RequestParam(defaultValue="1")int page){return service.list(page,false);}
 @DeleteMapping("codes/{id}") public void revoke(@PathVariable long id){service.revoke(id);}
 @GetMapping("history") public Object history(@RequestParam(defaultValue="1")int page){return service.list(page,true);}
}
