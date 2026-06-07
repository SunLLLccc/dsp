import { reactive } from 'vue'
import request from '../api/request'

// ==================== 本地 fallback（API 不可用时仍可正常展示） ====================

const LOCAL_INTERFACE_STATUS = { 0: '草稿', 1: '待审批', 2: '已驳回', 3: '已发布', 4: '已下线' }
const LOCAL_INTERFACE_STATUS_TYPE = { 0: 'info', 1: 'warning', 2: 'danger', 3: 'success', 4: 'info' }

const LOCAL_VERSION_STATUS = { 0: '草稿', 1: '待审批', 2: '已驳回', 3: '已发布' }
const LOCAL_VERSION_STATUS_TYPE = { 0: 'info', 1: 'warning', 2: 'danger', 3: 'success' }

const LOCAL_APPROVAL_STATUS = { 0: '待审批', 1: '已通过', 2: '已驳回', 3: '已撤回' }
const LOCAL_APPROVAL_STATUS_TYPE = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' }

const LOCAL_COMMON_STATUS = { 0: '禁用', 1: '启用' }
const LOCAL_COMMON_STATUS_TYPE = { 0: 'danger', 1: 'success' }

const LOCAL_RELATION_STATUS = { 1: '生效', 2: '已下线' }
const LOCAL_RELATION_STATUS_TYPE = { 1: 'success', 2: 'info' }

const LOCAL_EXPORT_TASK_STATUS = { 0: '待处理', 1: '处理中', 2: '已完成', 3: '失败' }
const LOCAL_EXPORT_TASK_STATUS_TYPE = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }

// ==================== Reactive 导出（页面使用方式不变） ====================

export const INTERFACE_STATUS = reactive({ ...LOCAL_INTERFACE_STATUS })
export const INTERFACE_STATUS_TYPE = reactive({ ...LOCAL_INTERFACE_STATUS_TYPE })

export const VERSION_STATUS = reactive({ ...LOCAL_VERSION_STATUS })
export const VERSION_STATUS_TYPE = reactive({ ...LOCAL_VERSION_STATUS_TYPE })

export const APPROVAL_STATUS = reactive({ ...LOCAL_APPROVAL_STATUS })
export const APPROVAL_STATUS_TYPE = reactive({ ...LOCAL_APPROVAL_STATUS_TYPE })

export const COMMON_STATUS = reactive({ ...LOCAL_COMMON_STATUS })
export const COMMON_STATUS_TYPE = reactive({ ...LOCAL_COMMON_STATUS_TYPE })

export const RELATION_STATUS = reactive({ ...LOCAL_RELATION_STATUS })
export const RELATION_STATUS_TYPE = reactive({ ...LOCAL_RELATION_STATUS_TYPE })

export const EXPORT_TASK_STATUS = reactive({ ...LOCAL_EXPORT_TASK_STATUS })
export const EXPORT_TASK_STATUS_TYPE = reactive({ ...LOCAL_EXPORT_TASK_STATUS_TYPE })

// ==================== 从后端加载枚举 ====================

let loaded = false

/**
 * 从后端 /dsp/admin/enums/status 加载枚举标签。
 * 加载成功后更新 reactive 对象，页面自动使用最新值。
 * 失败时保持本地 fallback 不变。
 * 同一 session 只加载一次。
 */
export async function loadStatusEnums() {
  if (loaded) return
  loaded = true
  try {
    const res = await request.get('/enums/status')
    if (!res.data) return
    const d = res.data
    applyEnum(d.interfaceStatus, INTERFACE_STATUS, INTERFACE_STATUS_TYPE)
    applyEnum(d.versionStatus, VERSION_STATUS, VERSION_STATUS_TYPE)
    applyEnum(d.approvalStatus, APPROVAL_STATUS, APPROVAL_STATUS_TYPE)
    applyEnum(d.commonStatus, COMMON_STATUS, COMMON_STATUS_TYPE)
    applyEnum(d.relationStatus, RELATION_STATUS, RELATION_STATUS_TYPE)
    applyEnum(d.exportTaskStatus, EXPORT_TASK_STATUS, EXPORT_TASK_STATUS_TYPE)
  } catch {
    // API 不可用，保持本地 fallback
  }
}

/**
 * 将后端枚举数据合并到 reactive label/type 对象。
 * API 返回的值覆盖本地 fallback；API 未覆盖的本地 key（如 3:'已撤回'）保留。
 */
function applyEnum(enumData, labelMap, typeMap) {
  if (!enumData) return
  for (const [code, info] of Object.entries(enumData)) {
    labelMap[code] = info.label
    typeMap[code] = deriveTagType(info.name)
  }
}

/**
 * 根据枚举名推导 Element Plus tag 类型
 */
function deriveTagType(name) {
  if (!name) return 'info'
  const n = name.toUpperCase()
  if (n.includes('PENDING') || n.includes('PROCESSING')) return 'warning'
  if (n.includes('REJECTED') || n.includes('FAILED')) return 'danger'
  if (n.includes('PUBLISHED') || n.includes('APPROVED') || n.includes('ENABLED') || n.includes('COMPLETED') || n.includes('ONLINE')) return 'success'
  if (n.includes('OFFLINE') || n.includes('DISABLED')) return 'info'
  if (n.includes('DRAFT')) return 'info'
  return 'info'
}
