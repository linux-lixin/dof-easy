package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.beans.Method;
import com.aiyi.core.beans.ResultPage;
import com.aiyi.core.beans.WherePrams;
import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.sql.where.C;
import com.aiyi.game.dnfserver.dao.RechargeKeyDao;
import com.aiyi.game.dnfserver.entity.RechargeKey;
import com.aiyi.game.dnfserver.service.RechargeKeyService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class RechargeKeyServiceImpl implements RechargeKeyService {
    @Resource private com.aiyi.game.dnfserver.service.dof.ManagedCdkService managedCdk;

    @Resource
    private RechargeKeyDao rechargeKeyDao;




    @Override
    public ResultPage<RechargeKey> list(Integer type, Integer status,
                                        int page, int pageSize,
                                        String content) {
        managedCdk.requireAdmin();
        if(page<1||page>10000||pageSize<1||pageSize>100)throw new ValidationException("页码或每页数量无效");
        WherePrams where = Method.createDefault();
        if (null != type){
            where.and(RechargeKey::getType, C.EQ, type);
        }
        if (null != status){
            where.and(RechargeKey::getStatus, C.EQ, status);
        }
        if (!StringUtils.isEmpty(content)){
            where.and(RechargeKey::getContent, C.LIKE, content);
        }
        ResultPage<RechargeKey> list = rechargeKeyDao.list(where,
                page, pageSize);
        for (RechargeKey key: list.getList()){
            if (!StringUtils.isEmpty(key.getFaceName())){
                key.setFaceName(new String(
                    Base64.getDecoder().decode(key.getFaceName()),
                    StandardCharsets.UTF_8
                ));
            }
        }
        return list;
    }

    @Override
    public void create(RechargeKey rechargeKey) {
        managedCdk.requireAdmin();
        if(rechargeKey==null||rechargeKey.getType()<0||rechargeKey.getType()>1||rechargeKey.getCount()<1||rechargeKey.getCount()>1000||rechargeKey.getFace()<1||rechargeKey.getType()==0&&rechargeKey.getFace()>9999999||rechargeKey.getFaceName()==null||rechargeKey.getFaceName().length()>150)throw new ValidationException("卡密类型、面值、名称或数量无效（最多1000张）");
        if (rechargeKey.getType() == 0){
            if (rechargeKey.getFace() < 1){
                throw new ValidationException("面值无效");
            }
        }else{
            if (rechargeKey.getFace() < 0){
                throw new ValidationException("选择的物品无效");
            }
        }

        if (rechargeKey.getCount() < 1){
            throw new ValidationException("卡密数量不能小于1");
        }

        for (int i = 0; i < rechargeKey.getCount(); i ++){
            RechargeKey key = new RechargeKey();
            key.setContent(UUID.randomUUID().toString().toUpperCase());
            key.setCreateTime(new Date());
            key.setFace(rechargeKey.getFace());
            key.setFaceName(Base64.getEncoder().encodeToString(
                    rechargeKey.getFaceName().
                            getBytes(StandardCharsets.UTF_8)));
            key.setType(rechargeKey.getType());
            rechargeKeyDao.add(key);
        }
    }

    @Override
    public void use(RechargeKey key) {
        com.aiyi.game.dnfserver.service.dof.ManagedCdkService.Claim request=new com.aiyi.game.dnfserver.service.dof.ManagedCdkService.Claim();
        request.requestId=key.getRequestId();request.content=key.getContent();request.useAccount=key.getUseAccount();request.charach=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(key.getCharach());request.valiId=key.getValiId();request.valicode=key.getValicode();
        managedCdk.claim(request);
    }
}
