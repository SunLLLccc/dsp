import request from './request'
import { createSseParser } from '../utils/sseParser'
import { useAuthStore } from '../stores/auth'
import router from '../router'
import { ElMessage } from 'element-plus'

/**
 * Text2API API。
 * 普通 REST 端点复用全局 axios 实例（自动注入 Admin-Token + 统一错误处理）；
 * SSE 端点（generate）用 fetch + ReadableStream，手动注入 Admin-Token，
 * 并手动对齐 request.js 的 401/403 处理（fetch 不走 axios 拦截器）。
 *
 * 后端端点（T4/T5）：
 *   POST   /dsp/admin/assistant/text2api/drafts
 *   GET    /dsp/admin/assistant/text2api/drafts
 *   GET    /dsp/admin/assistant/text2api/drafts/{draftId}
 *   DELETE /dsp/admin/assistant/text2api/drafts/{draftId}
 *   PUT    /dsp/admin/assistant/text2api/drafts/{draftId}/requirement
 *   POST   /dsp/admin/assistant/text2api/drafts/{draftId}/confirm
 *   POST   /dsp/admin/assistant/text2api/drafts/{draftId}/rollback
 *   POST   /dsp/admin/assistant/text2api/drafts/{draftId}/generate   (SSE)
 *   POST   /dsp/admin/assistant/text2api/drafts/{draftId}/publish
 */
export const text2apiApi = {
  createDraft: (data) => request.post('/assistant/text2api/drafts', data || {}),
  listDrafts: () => request.get('/assistant/text2api/drafts'),
  getDraft: (draftId) => request.get(`/assistant/text2api/drafts/${draftId}`),
  deleteDraft: (draftId) => request.delete(`/assistant/text2api/drafts/${draftId}`),
  updateRequirement: (draftId, requirementText) =>
    request.put(`/assistant/text2api/drafts/${draftId}/requirement`, { requirementText }),
  confirmStage: (draftId, stage) =>
    request.post(`/assistant/text2api/drafts/${draftId}/confirm`, { stage }),
  rollbackStage: (draftId, stage) =>
    request.post(`/assistant/text2api/drafts/${draftId}/rollback`, { stage }),
  publishDraft: (draftId) =>
    request.post(`/assistant/text2api/drafts/${draftId}/publish`),

  /**
   * 阶段生成（SSE 流式响应）。
   *
   * @param {Object} param
   * @param {string} param.draftId
   * @param {number} param.stage        目标阶段 2-5
   * @param {Object} [param.evidence]   Text2SQL 依据（仅阶段 3）
   * @param {Object} handlers 事件回调：
   *   { onStart, onResult, onNeedsMoreInfo, onFailed, onComplete, onError, onCancelled }
   * @returns {{abort: () => void}} 取消控制器
   */
  generateStageStream({ draftId, stage, evidence }, handlers = {}) {
    const controller = new AbortController()
    const authStore = useAuthStore()
    // 是否已收到 terminal 事件（complete/error/cancelled）。用于判断流提前断开是否异常。
    let terminalReceived = false

    const parser = createSseParser({
      onEvent(event, data) {
        switch (event) {
          case 'start':
            handlers.onStart && handlers.onStart()
            break
          case 'delta':
            // 一期同步聚合无 delta，预留
            handlers.onDelta && handlers.onDelta(data)
            break
          case 'result':
            handlers.onResult && handlers.onResult(data)
            break
          case 'needs_more_info':
            handlers.onNeedsMoreInfo && handlers.onNeedsMoreInfo(data)
            break
          case 'failed':
            handlers.onFailed && handlers.onFailed(data)
            break
          case 'complete':
            terminalReceived = true
            handlers.onComplete && handlers.onComplete()
            break
          case 'error':
            terminalReceived = true
            handlers.onError && handlers.onError(data)
            break
          case 'cancelled':
            terminalReceived = true
            handlers.onCancelled && handlers.onCancelled(data)
            break
          default:
            break
        }
      }
    })

    ;(async () => {
      try {
        const resp = await fetch(`/dsp/admin/assistant/text2api/drafts/${draftId}/generate`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Admin-Token': authStore.token || ''
          },
          body: JSON.stringify({ stage, evidence: evidence || null }),
          signal: controller.signal
        })

        if (!resp.ok) {
          // 非 2xx 必须通知调用方结束，否则页面卡在 generating
          const error = await handleHttpError(resp.status, resp)
          handlers.onError && handlers.onError(error.message, { alreadyNotified: error.alreadyNotified })
          return
        }

        const reader = resp.body.getReader()
        const decoder = new TextDecoder('utf-8')
        // eslint-disable-next-line no-constant-condition
        while (true) {
          const { done, value } = await reader.read()
          if (done) {
            // flush decoder 尾部（防止 UTF-8 边界丢字符）
            const tail = decoder.decode()
            if (tail) {
              parser.feed(tail)
            }
            parser.end()
            // 流结束但没收到 terminal event，视为异常断流
            if (!terminalReceived) {
              handlers.onError && handlers.onError('连接已结束但未收到完成事件')
            }
            break
          }
          parser.feed(decoder.decode(value, { stream: true }))
        }
      } catch (err) {
        if (err.name === 'AbortError') {
          // 用户主动取消，不当作错误
          return
        }
        handlers.onError && handlers.onError(err.message || '网络错误')
      }
    })()

    return { abort: () => controller.abort() }
  }
}

/**
 * fetch 非 2xx 时的错误处理，对齐 request.js 的 401/403 处理。
 * 返回 {message, alreadyNotified}：alreadyNotified=true 表示 api 层已弹提示，
 * 调用方可据此让 store 静默，避免重复刷屏。
 */
async function handleHttpError(status, resp) {
  if (status === 401) {
    const authStore = useAuthStore()
    authStore.logout()
    router.push('/login')
    ElMessage.error('登录已过期，请重新登录')
    return { message: '登录已过期，请重新登录', alreadyNotified: true }
  }
  let msg = ''
  try {
    const errBody = await resp.json()
    msg = errBody.message || ''
  } catch (_) {}
  // 403 无 message 时 fallback「无操作权限」，贴近 request.js 行为
  if (!msg && status === 403) {
    msg = '无操作权限'
  }
  if (!msg) {
    msg = '请求失败'
  }
  ElMessage.error(msg)
  return { message: msg, alreadyNotified: true }
}
