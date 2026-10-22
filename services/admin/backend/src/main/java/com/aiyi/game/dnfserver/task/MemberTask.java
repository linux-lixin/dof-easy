package com.aiyi.game.dnfserver.task;

import com.aiyi.game.dnfserver.dao.AccountDao;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class MemberTask {

    @Resource
    private AccountVODao accountVODao;

    /**
     * 角色创建限制, 每3秒重置一次
     */
    @Scheduled(cron = "0/3 * * * * ?")
    public void roleCreateLimitReset(){
        // 每天凌晨重置角色创建限制
        /**
         * 18000000,1,2025-10-21 20:23:18
         * 18000002,0,2025-09-24 22:53:28
         * 18000008,0,2025-09-29 21:24:44
         * 18000010,0,2025-09-29 21:29:00
         * 18000011,0,2025-10-06 14:52:46
         * 18000009,1,2025-10-21 20:34:25
         */
        // update limit_create_character set count='0'
        accountVODao.execute("TRUNCATE TABLE `d_taiwan`.`limit_create_character`");
    }
}
