/**
 * 分页结果
 */
export interface PageResult<T = any> {

    /**
     * 当页记录数
     */
    length?: number;
    /**
     * 当前页码
     */
    page: number;
    /**
     * 总页数
     */
    totalPageSize: number;
    /**
     * 总记录数
     */
    totalSize: number;

    /**
     * 列表数据
     */
    list: T[];
}
