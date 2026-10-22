package com.aiyi.game.dnfserver.entity.common;

/**
 * attach type
 * 绑定/交易类型
 * `[trade]` //不可交易
 * `[free]` //自由交易
 * `[sealing]` //封装
 * `[account]` //账号绑定
 * `[trade delete]` //无法删除
 * `[sealing trade]` //封装且不可交易
 * @author xiatian
 */
public enum AttachType implements HasDesc {

    trade("[trade]", "不可交易"),
    tradeLimit("[trade limit]", "有限制交易"),
    free("[free]", "自由交易"),
    sealing("[sealing]", "封装"),
    account("[account]", "账号绑定"),
    tradeDelete("[trade delete]", "无法删除"),
    sealingTrade("[sealing trade]", "封装且不可交易"),
    ;

    /**
     * 交易类型
     */
    private String type;

    /**
     * 类型描述
     */
    private String desc;

    AttachType(String type, String desc){
        this.type = type;
        this.desc = desc;
    }

    public static AttachType forType(String type){
        if (type.endsWith("]]")){
            type = type.substring(0, type.length() -1);
        }
        for (AttachType t : AttachType.values()){
            if (t.type.equals(type)){
                return t;
            }
        }
        throw new RuntimeException("no t found TypeTag:" + type);
    }

    public String getType() {
        return type;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
