package com.aiyi.game.dnfserver.entity.stackable;

import cn.hutool.json.JSONObject;
import com.aiyi.game.dnfserver.entity.common.Item;

/**
 * 道具实体
 * @author xiatian
 */
public class Stackable extends Item {

    /**
     * 道具类型
     */
    private StackableType stackableType;


    public static Stackable forScript(JSONObject script) {
        Stackable stackable = new Stackable();
        stackable.parseForScript(script);
        stackable.stackableType = StackableType.forType(script
                .getJSONArray("[stackable type]").getStr(0));
        return stackable;
    }

    public StackableType getStackableType() {
        return stackableType;
    }

    public void setStackableType(StackableType stackableType) {
        this.stackableType = stackableType;
    }
}
