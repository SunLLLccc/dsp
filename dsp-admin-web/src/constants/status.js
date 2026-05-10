/**
 * 接口/模板状态（interface_info、interface_template）
 * 0=草稿 1=待审批 2=已驳回 3=已发布 4=已下线
 */
export const INTERFACE_STATUS = { 0: '草稿', 1: '待审批', 2: '已驳回', 3: '已发布', 4: '已下线' }
export const INTERFACE_STATUS_TYPE = { 0: 'info', 1: 'warning', 2: 'danger', 3: 'success', 4: 'info' }

/**
 * 版本状态（interface_version）
 * 0=草稿 1=待审批 2=已驳回 3=已发布
 */
export const VERSION_STATUS = { 0: '草稿', 1: '待审批', 2: '已驳回', 3: '已发布' }
export const VERSION_STATUS_TYPE = { 0: 'info', 1: 'warning', 2: 'danger', 3: 'success' }

/**
 * 审批记录状态（approval_record）
 * 0=待审批 1=已通过 2=已驳回
 */
export const APPROVAL_STATUS = { 0: '待审批', 1: '已通过', 2: '已驳回' }
export const APPROVAL_STATUS_TYPE = { 0: 'warning', 1: 'success', 2: 'danger' }
