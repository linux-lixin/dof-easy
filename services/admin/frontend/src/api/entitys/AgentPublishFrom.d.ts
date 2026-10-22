/**
 * 智能体发布表单数据结构
 */
export interface AgentPublishFrom {
    id: number;
    publishStatus: 'UNPUBLISHED' | 'PUBLISHED' | 'internalPublic';
    agentTagList: Array<{
        agentTag: string;
    }>;
    agentMenuList: Array<AgentMenuItem>;
    roleList: Array<{
        roleId: number;
    }>;
    deptList: Array<{
        deptId: number;
    }>;
}

export interface AgentMenuItem {
    id: number;
    menuName: string;
    menuIcon: string;
    guideText: string;
    promptText: string | '';
    submitContent: string;
    inputContent: string | '';
    deepThinkSwitch: boolean;
    searchAllSwitch: boolean;
    promptTemplate: string;
    sort: number | null;
    agentId: number;
}
