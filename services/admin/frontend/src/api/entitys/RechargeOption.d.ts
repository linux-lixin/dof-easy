export interface RechargeOption {
    /**
     * 预充值的ID
     */
    uid: number;
    /**
     * 预充值的账号
     */
    accountname: string;
    /**
     * 是否展示充值窗口
     */
    open: boolean;
    /**
     * 充值金额
     */
    cera: number;
    /**
     * 当前金额
     */
    thisCera: number;
}
