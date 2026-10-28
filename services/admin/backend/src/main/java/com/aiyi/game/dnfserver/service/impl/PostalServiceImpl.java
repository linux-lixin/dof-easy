package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.dao.PostalQueryDao;
import com.aiyi.game.dnfserver.dao.PostalDao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.entity.Postal;
import com.aiyi.game.dnfserver.service.PostalService;
import com.aiyi.game.dnfserver.service.dof.DofFailure;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/13 16:37
 */
@Service
public class PostalServiceImpl implements PostalService {

    @Resource
    private PostalDao postalDao;

    @Resource
    private AccountVODao accountVODao;

    @Resource
    private PostalQueryDao postalQueryDao;

    @Override
    public ResultPage<Postal> list(String account, Date start,
                                   Date end, Integer page,
                                   Integer pageSize) {
        long actor = ThreadUtil.getUserId();
        if (actor <= 0) throw new DofFailure(403,"需要有效的管理登录");
        AccountVO identity = accountVODao.get(actor);
        if (identity == null) throw new DofFailure(403,"管理账号不可用");
        if (page == null) page = 1;
        if (pageSize == null) pageSize = 10;
        if (page < 1 || pageSize < 1 || pageSize > 100 || (long)(page - 1) * pageSize > Integer.MAX_VALUE)
            throw new DofFailure(400,"邮件页码必须为正数，每页须为 1～100 条");
        if (start != null && end != null && start.after(end)) throw new DofFailure(400,"开始时间不能晚于结束时间");
        if (account != null && account.length() > 128) throw new DofFailure(400,"账号筛选过长");
        String filter = account == null || account.isEmpty() ? null : account;
        // EXISTS retains the account filter when it has no matches, includes all
        // of its characters, and cannot multiply mail rows if a join is ambiguous.
        int count = postalQueryDao.count(filter,identity.isAdmin(),actor,start,end);
        return new ResultPage<>(count,page,pageSize,
            postalQueryDao.list(filter,identity.isAdmin(),actor,start,end,(page - 1) * pageSize,pageSize));
    }

    @Override
    public void sendMail(Postal postal) {
        postal.setSendCharacName("DNF Manager");
        postalDao.add(postal);
    }
}
