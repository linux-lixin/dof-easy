package com.aiyi.game.dnfserver.entity.stackable;

/**
 * 可堆叠物品类型枚举
 * @author xiatian
 */
public enum StackableType {

    /**
     * 消耗品
     */
    waste("[waste]", "消耗品"),

    /**
     * 材料
     */
    material("[material]", "材料"),

    /**
     * 设计图
     */
    recipe("[recipe]", "设计图"),

    /**
     * 副职业材料
     */
    material_expert_job("[material expert job]", "副职业"),

    /**
     * 任务道具
     */
    quest("[quest]", "任务道具"),

    /**
     * 礼盒
     */
    booster("[booster]", "礼盒"),

    /**
     * 宠物饲料
     */
    feed("[feed]", "饲料"),

    /**
     * 宠物
     */
    creature("[creature]", "宠物"),

    /**
     * 其他杂物
     */
    etc("[etc]", "杂物"),


    /**
     * 投掷物
     */
    throwItem("[throw]", "投掷物"),

    /**
     * 遗物
     */
    legacy("[legacy]", "罐子"),

    ;

    /**
     * 装备类型
     */
    private final String type;

    /**
     * 描述
     */
    private final String desc;

    StackableType(String type, String desc) {
        this.type = type;
        this.desc = desc;
    }



    public static StackableType forType(String type){
        for (StackableType t : StackableType.values()){
            if (t.type.equals(type)){
                return t;
            }
        }
        if (type.contains("legacy")){
            return legacy;
        }
        if (type.contains("expert_town_potion") || type.contains("contract") ||
                type.contains("global effect") || type.contains("disguise") ||
                type.contains("waste") || type.contains("expert town potion") ||
                type.contains("unlimited")){
            return waste;
        }
        if (type.contains("quest")){
            return quest;
        }
        if (type.contains("avatar_emblem") ||
                /*制作书*/ type.contains("recipe") || type.contains("avatar emblem")){
            return material;
        }
        if (type.contains("booster")){
            return booster;
        }
        return etc;
    }

    public String getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }
}
