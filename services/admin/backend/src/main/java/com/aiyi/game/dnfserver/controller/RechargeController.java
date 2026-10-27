package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.service.dof.ManagedRechargeService;
import com.aiyi.game.dnfserver.service.dof.DofFailure;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * chong zhi
 */
@RestController
@RequestMapping("api/v1/recharge")
public class RechargeController {

    @Resource
    private ManagedRechargeService recharge;

    /**
     * recharge in the user
     * @param edit
     *      the recharge info
     */
    @PostMapping
    public Object recharge(@RequestBody ManagedRechargeService.Edit edit){
        return recharge.save(edit);
    }

    @GetMapping("{uid}") public Object status(@PathVariable long uid){return recharge.status(uid);}
    @PostMapping("{uid}/preview") public Object preview(@PathVariable long uid,@RequestBody ManagedRechargeService.Edit edit){return recharge.preview(uid,edit);}
    @GetMapping("{uid}/history") public Object history(@PathVariable long uid){return recharge.history(uid);}
    @PostMapping("{uid}/history/{requestId}/reconcile") public Object reconcile(@PathVariable long uid,@PathVariable String requestId){return recharge.reconcile(uid,requestId);}
    @ExceptionHandler(DofFailure.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String,String>> failure(DofFailure e){
        return org.springframework.http.ResponseEntity.status(e.status).body(java.util.Collections.singletonMap("message",e.getMessage()));
    }


}
