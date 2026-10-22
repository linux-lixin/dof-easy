package com.aiyi.game.dnfserver.entity.task;


/**
 * 任务类型
 * @author xiatian
 */
public enum TaskType {
    custom("[custom quest]", "普通操作任务", true),
    condition("[condition under clear]", "过图任务", true),
    npc("[meet npc]", "寻人任务", true),
    seeking("[seeking]", "收集任务", false),
    pvpRank("[pvp rank]", "竞技场任务", true),
    huntEnemy("[hunt enemy]", "击杀任务", true),
    clearMap("[clear map]", "过图任务", true),
    amplifyItem("[amplify item]", "强化任务", true),
    hunt("[hunt monster]", "击杀任务", true);


    /**
     * 任务类型
     */
    private final String type;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 是否可直接完成
     */
    private final boolean catFinish;



    TaskType(String type, String desc, boolean catFinish) {
        this.type = type;
        this.desc = desc;
        this.catFinish = catFinish;
    }



    public static TaskType forType(String type){
        for (TaskType t : TaskType.values()){
            if (t.type.equals(type)){
                return t;
            }
        }
        throw new RuntimeException("no t found TypeTag");
    }

    public String getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }

    public boolean isCatFinish() {
        return catFinish;
    }
}
