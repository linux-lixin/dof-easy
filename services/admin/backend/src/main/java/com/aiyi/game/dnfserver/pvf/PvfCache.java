package com.aiyi.game.dnfserver.pvf;

import com.aiyi.game.dnfserver.entity.equipment.Equipment;
import com.aiyi.game.dnfserver.entity.stackable.Stackable;

import java.util.List;

/**
 * Pvf缓存
 * @author gsk
 */
public class PvfCache {

    private static int size = 0;

    private static List<Equipment> equipmentList;

    private static List<Stackable> stackableList;


    public static List<Equipment> getEquipmentList() {
        return equipmentList;
    }

    public static void setEquipmentList(List<Equipment> equipmentList) {
        PvfCache.equipmentList = equipmentList;
    }

    public static List<Stackable> getStackableList() {
        return stackableList;
    }
    public static void setStackableList(List<Stackable> stackableList) {
        PvfCache.stackableList = stackableList;
    }

    public static int getSize() {
        return size;
    }

    public static void setPvfSize(int size) {
        PvfCache.size = size;
    }
}
