package com.aiyi.game.dnfserver.entity;

import com.aiyi.game.dnfserver.entity.task.TaskType;

import java.util.List;

/**
 * 任务信息
 * @author xiatian
 */
public class TaskVO {

    private int id;

    private int no;

    private String name;

    private List<TaskItemVO> items;

    private TaskType type;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<TaskItemVO> getItems() {
        return items;
    }

    public void setItems(List<TaskItemVO> items) {
        this.items = items;
    }

    public TaskType getType() {
        return type;
    }

    public void setType(TaskType type) {
        this.type = type;
    }

    public int getNo() {
        return no;
    }

    public void setNo(int no) {
        this.no = no;
    }
}
