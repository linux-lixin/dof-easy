package com.aiyi.game.dnfserver.entity.common;

/**
 * usable job
 * 可使用职业
 * [swordman] // 鬼剑士
 * [fighter] // 格斗家(女)
 * [at fighter] // 格斗家(男)
 * [demonic swordman] // 体操王子
 * [creatormage] // 创造者/魔法师
 * [gunner] // 枪手
 * [at gunner] // 女枪手
 * [mage] // 魔法师(女)
 * [at mage] // 魔法师(男)
 * [priest] // 圣职者
 * [all] // 全职业
 * @author xiatian
 */
public enum UsableJob {

    all("[all]", "所有职业"),
    swordman("[swordman]", "鬼剑士"),
    fighter("[fighter]", "格斗家(女)"),
    atFighter("[at fighter]", "格斗家(男)"),
    demonicSwordman("[demonic swordman]", "体操王子"),
    creatorMage("[creator mage]", "缔造者/魔法师"),
    gunner("[gunner]", "神枪手(男)"),
    atGunner("[at gunner]", "神枪手(女)"),
    mage("[mage]", "魔法师(女)"),
    atMage("[at mage]", "魔法师(男)"),
    priest("[priest]", "圣职者"),
    thief("[thief]", "盗贼"),
    free("[free]", "自由职业")


    ;

    private String jobKey;

    private String jobDesc;

    UsableJob(String jobKey, String jobDesc) {
        this.jobKey = jobKey;
        this.jobDesc = jobDesc;
    }

    public static UsableJob forJobKey(String jobKey){
        for (UsableJob job : UsableJob.values()){
            if (job.jobKey.equals(jobKey)){
                return job;
            }
        }
        throw new RuntimeException("no job found for jobKey: " + jobKey);
    }

    public String getJobDesc() {
        return jobDesc;
    }
}
