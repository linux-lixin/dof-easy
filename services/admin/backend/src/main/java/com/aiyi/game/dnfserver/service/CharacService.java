package com.aiyi.game.dnfserver.service;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.game.dnfserver.entity.CharacInfo;
import com.aiyi.game.dnfserver.entity.TaskVO;

import java.util.List;

/**
 * 角色相关业务
 */
public interface CharacService {

    /**
     * 查询角色列表
     * @param levMin
     *      等级区间-开始
     * @param levMax
     *      等级区间-技术
     * @param job
     *      职业
     * @param name
     *      名称模糊匹配
     * @return
     */
    ResultPage<CharacInfo> list(Integer levMin, Integer levMax, Integer job,
                                String name, String account, Integer page, Integer pageSize);

    /**
     * 列出某个账号的角色列表
     * @param account
     *      账号
     * @return
     */
    List<CharacInfo> list(String account);

    /**
     * 更新角色信息
     * @param info
     *      角色信息
     */
    void update(CharacInfo info);

    /**
     * 角色完成已接受的所有任务
     * @param characId
     *      角色ID
     */
    void overTasks(Long characId);

    /**
     * 列出角色未完成的任务列表
     * @param characId
     *      角色ID
     * @return
     */
    List<TaskVO> listNoOverTasks(Long characId);
}
