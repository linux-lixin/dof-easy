export interface AccountSearchForm {
    /**
     * 账号
     */
    account?: string;
    /**
     * 登录状态
     */
    loginStatus?: 'true' | 'false' | '';
    /**
     * 邮箱
     */
    lastLoginTime?: string[];

    pageNum: number;

    pageSize: number;

}
