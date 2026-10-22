package com.aiyi.game.dnfserver.entity;

import com.aiyi.core.annotation.po.FieldName;
import com.aiyi.core.annotation.po.ID;
import com.aiyi.core.annotation.po.TableName;
import com.aiyi.core.beans.PO;

import java.util.Date;

/**
 * 用户惩罚信息表
 */
@TableName(name = "d_taiwan.`member_punish_info`")
public class MemberPunishInfo extends PO {

    @FieldName(name = "m_id")
    private long mid;

    /**
     * 惩罚类型（网上没有具体解释， 猜测可能是封号， 限制交易， 限制发言， 限制收益一类的， 其中永久禁封的话是1）
     */
    @FieldName(name = "punish_type")
    private int punishType;

    /**
     * 违规发生时间
     */
    @FieldName(name = "occ_time")
    private Date occTime;

    /**
     * 惩罚具体值（不清楚是干嘛的， 可能是指的惩罚力度？其中，永久禁封的值为101）
     */
    @FieldName(name = "punish_value")
    private int punishValue;

    /**
     * 生效标记？不清楚， 其中永久禁封1和2都可以
     */
    @FieldName(name = "apply_flag")
    private int applyFlag;

    /**
     * 惩罚开始时间
     */
    @FieldName(name = "start_time")
    private Date startTime;

    /**
     * 惩罚结束时间（可以断定和惩罚结束有关系， 但是又不是绝对值， 比如永久禁封的话传入9999-12-31 23:59:59有效， 传入D+1可以登录游戏，但是没有以前的角色，不过还可以新建角色， 搞不懂）
     */
    @FieldName(name = "end_time")
    private Date endTime;

    /**
     * 操作人(20字符， 好像没影响)
     */
    @FieldName(name = "admin_id")
    private String adminId;

    /**
     * 操作备注？不影响游戏，100字符(我把它当成了UUID主键， 因为这个CRM框架必须要有一个自动生成的主键)
     */
    @ID
    private String reason;

}
