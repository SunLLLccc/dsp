import request from './request'
import { createSseParser } from '../utils/sseParser'
import { useAuthStore } from '../stores/auth'
import router from '../router'
import { ElMessage } from 'element-plus'

/**
 * 智能助手 chat API。
 * 普通 REST 端点复用全局 axios 实例（自动注入 Admin-Token + 统一错误处理）；
 * SSE 端点（ask）用 fetch + ReadableStream，手动注入 Admin-Token，
 * 并手动对齐 request.js 的 401/403 处理（fetch 不走 axios 拦截器）。
 */
export const assistantApi = {
  createSession: (data) => request.post('/assistant/chat/sessions', data),
  listSessions: (params) => request.get('/assistant/chat/sessions', { params }),
  listMessages: (sessionId) => request.get(`/assistant/chat/sessions/${sessionId}/messages`),
  deleteSession: (sessionId) => request.delete(`/assistant/chat/sessions/${sessionId}`),

  /**
   * 发起提问（SSE 流式回答）。
   *
   * @param {Object} param
   * @param {string} param.sessionId
   * @param {string} param.question
   * @param {Object} handlers 事件回调：{ onStart, onDelta, onCitations, onComplete, onError }
   * @returns {{abort: () => void}} 取消控制器
   */
  askStream({ sessionId, question }, handlers = {}) {
    const controller = new AbortController()
    const authStore = useAuthStore()
    // 是否已收到 terminal 事件（complete/error）。用于判断流提前断开是否异常。
    let terminalReceived = false

    const parser = createSseParser({
      onEvent(event, data) {
        switch (event) {
          case 'start':
            handlers.onStart && handlers.onStart()
            break
          case 'delta':
            handlers.onDelta && handlers.onDelta(data)
            break
          case 'citations':
            handlers.onCitations && handlers.onCitations(data)
            break
          case 'complete':
            terminalReceived = true
            handlers.onComplete && handlers.onComplete()
            break
          case 'error':
            terminalReceived = true
            handlers.onError && handlers.onError(data)
            break
          default:
            break
        }
      }
    })

    // 异步执行，不 return promise（调用方用回调接收）
    ;(async () => {
      try {
        const resp = await fetch('/dsp/admin/assistant/chat/ask', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Admin-Token': authStore.token || ''
          },
          body: JSON.stringify({ sessionId, question }),
          signal: controller.signal
        })

        if (!resp.ok) {
          // 必改：非 2xx 必须通知 store 结束生成，否则页面卡在 generating
          const error = await handleHttpError(resp.status, resp)
          handlers.onError && handlers.onError(error.message, { alreadyNotified: error.alreadyNotified })
          return
        }

        const reader = resp.body.getReader()
        const decoder = new TextDecoder('utf-8')
        // 循环读取 chunk，喂给解析器
        // eslint-disable-next-line no-constant-condition
        while (true) {
          const { done, value } = await reader.read()
          if (done) {
            // 建议改1：done 时 flush decoder 尾部（防止 UTF-8 边界丢字符）
            const tail = decoder.decode()
            if (tail) {
              parser.feed(tail)
            }
            parser.end()
            // 必改1：流结束但没收到 terminal event，视为异常断流
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
 * 调用方（askStream → handlers.onError）可据此让 store 静默，避免重复刷屏。
 */
async function handleHttpError(status, resp) {
  if (status === 401) {
    const authStore = useAuthStore()
    authStore.logout()
    router.push('/login')
    ElMessage.error('登录已过期，请重新登录')
    return { message: '登录已过期，请重新登录', alreadyNotified: true }
  }
  let msg = '请求失败'
  try {
    const errBody = await resp.json()
    msg = errBody.message || msg
  } catch (_) {}
  if (status === 403) {
    ElMessage.error(msg || '无操作权限')
  } else {
    ElMessage.error(msg)
  }
  return { message: msg, alreadyNotified: true }
}
