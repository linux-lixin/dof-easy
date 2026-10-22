/**
 * 智能体列表项
 */
export interface AgentListItem {
    /**
     * 主键ID
     */
    id?: number;
    /**
     * 租户ID
     */
    tenantId?: number;
    /**
     * 智能体名称
     */
    name: string;
    /**
     * 智能体类型: BASE-基础通用型, WORKFLOW-自定义工作流型, DATABASE-数据库查询型, DOCUMENT-文档编写型, APPLICATION-高级应用型, CUSTOM-完全定制型
     */
    agentType: 'BASE' | 'WORKFLOW' | 'DATABASE' | 'DOCUMENT' | 'APPLICATION' | 'CUSTOM';
    /**
     * 图标
     */
    icon: string;
    /**
     * 发布状态
     */
    publishStatus?: 'UNPUBLISHED' | 'PUBLISHED' | 'internalPublic';
    /**
     * 备注
     */
    remark: string;
    /**
     * 智能体编码
     */
    agentCode?: string;
    /**
     * 排序
     */
    sort?: number | null;
    /**
     * 删除标志: 0-未删除, 1-已删除
     */
    delFlag?: string;
    /**
     * 创建人
     */
    createBy?: number;
    createTime?: string;
    updateBy?: number;
    updateTime?: string;
}
