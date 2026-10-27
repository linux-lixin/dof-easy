package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.game.dnfserver.entity.RechargeKey;
import com.aiyi.game.dnfserver.service.RechargeKeyService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;

@Service
public class RechargeKeyServiceImpl implements RechargeKeyService {
    @Resource private com.aiyi.game.dnfserver.service.dof.ManagedCdkService managedCdk;

    @Resource private com.aiyi.game.dnfserver.service.dof.ManagedCdkInventoryService inventory;
    @Override public Object list(Integer type,Integer status,int page,int pageSize,String content){
        managedCdk.requireAdmin();
        com.aiyi.game.dnfserver.service.dof.ManagedCdkInventoryService.Query query=new com.aiyi.game.dnfserver.service.dof.ManagedCdkInventoryService.Query();
        query.type=type;query.status=status;query.page=page;query.pageSize=pageSize;query.content=content;return inventory.query(query);
    }
    @Override public void create(RechargeKey key){
        managedCdk.requireAdmin();if(key==null)throw new com.aiyi.game.dnfserver.service.dof.DofFailure(400,"缺少制码内容");
        com.aiyi.game.dnfserver.service.dof.ManagedCdkInventoryService.Edit edit=new com.aiyi.game.dnfserver.service.dof.ManagedCdkInventoryService.Edit();
        com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();edit.type=json.valueToTree(key.getType());edit.face=json.valueToTree(key.getFace());edit.count=json.valueToTree(key.getCount());edit.faceName=key.getFaceName();edit.reason=key.getReason();edit.requestId=key.getRequestId();edit.revision=key.getRevision();inventory.save(edit);
    }

    @Override
    public void use(RechargeKey key) {
        com.aiyi.game.dnfserver.service.dof.ManagedCdkService.Claim request=new com.aiyi.game.dnfserver.service.dof.ManagedCdkService.Claim();
        request.requestId=key.getRequestId();request.content=key.getContent();request.useAccount=key.getUseAccount();request.charach=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(key.getCharach());request.valiId=key.getValiId();request.valicode=key.getValicode();
        managedCdk.claim(request);
    }
}
