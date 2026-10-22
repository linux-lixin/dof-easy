package com.aiyi.game.dnfserver.entity.equipment;

/**
 * equipment type
 * 装备类型枚举
 * @author xiatian
 */
public enum EquipmentType {
    weapon("[weapon]", "武器", false),
    titleName("[title name]", "称号", false),
    coat("[coat]", "上衣", false),
    shoulder("[shoulder]", "护肩", false),
    pants("[pants]", "裤子", false),
    shoes("[shoes]", "鞋子", false),
    waist("[waist]", "腰带", false),
    amulet("[amulet]", "项链", false),
    wrist("[wrist]", "手镯", false),
    ring("[ring]", "戒指", false),

    creature("[creature]", "宠物", false),
    aartifactRed("[artifact red]", "宠物装备-红色", false),
    aartifactGreen("[artifact green]", "宠物装备-绿色", false),
    aartifactBlue("[artifact blue]", "宠物装备-蓝色", false),

    support("[support]", "辅助装备", false),
    magicStone("[magic stone]", "魔法石", false),

    weaponAvatar("[weapon avatar]", "武器(装扮)", true),
    auroraAvatar("[aurora avatar]", "光环(装扮)", true),
    hatAvatar("[hat avatar]", "帽子(装扮)", true),
    hairAvatar("[hair avatar]", "头发(装扮)", true),
    breastAvatar("[breast avatar]", "胸部(装扮)", true),
    faceAvatar("[face avatar]", "脸部(装扮)", true),
    waistAvatar("[waist avatar]", "腰部(装扮)", true),
    coatAvatar("[coat avatar]", "上衣(装扮)", true),
    pantsAvatar("[pants avatar]", "裤子(装扮)", true),
    shoesAvatar("[shoes avatar]", "鞋子(装扮)", true),
    skinAvatar("[skin avatar]", "皮肤(装扮)", true)
    ;


    /**
     * 装备类型
     */
    private final String type;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 是否是时装(非avatar则为普通装备)
     */
    private final boolean avatar;



    EquipmentType(String type, String desc, boolean avatar) {
        this.type = type;
        this.desc = desc;
        this.avatar = avatar;
    }



    public static EquipmentType forType(String type){
        for (EquipmentType t : EquipmentType.values()){
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

    public boolean isAvatar() {
        return avatar;
    }
}
