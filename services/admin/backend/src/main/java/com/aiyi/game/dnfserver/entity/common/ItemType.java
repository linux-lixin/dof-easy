package com.aiyi.game.dnfserver.entity.common;

/**
 * 物品类型枚举
 * @author xiatian
 */
public enum ItemType implements HasDesc {
    /**
     * 装备
     */
    equipment("equipment", "装备"),

    /**
     * 道具
     */
    stackable("stackable", "道具"),

    /**
     * 其他
     */
    other("other", "其他"),

    ;


    /**
     * 装备类型
     */
    private final String type;

    /**
     * 描述
     */
    private final String desc;



    ItemType(String type, String desc) {
        this.type = type;
        this.desc = desc;
    }



    public static ItemType forType(String type){
        for (ItemType t : ItemType.values()){
            if (t.type.equals(type)){
                return t;
            }
        }
        throw new RuntimeException("no t found TypeTag:" + type);
    }

    public String getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }
}
