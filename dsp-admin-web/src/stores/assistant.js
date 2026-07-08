import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { assistantApi } from '../api/assistant'
import { ElMessage } from 'element-plus'

/**
 * 智能助手 chat 状态。
 * 管理会话列表、当前会话、消息、生成状态、citations。
 * 页面组件保持薄，复杂状态在这里管理。
 */
export const useAssistantStore = defineStore('assistant', () => {
  // 会话列表
  const sessions = ref([])
  const sessionsLoading = ref(false)
  // 当前会话
  const currentSessionId = ref(null)
  // 当前会话的消息列表
  const messages = ref([])
  // 生成状态：idle / generating / done / failed / cancelled
  const genStatus = ref('idle')
  // 当前正在生成的 assistant 消息内容（delta 累积）
  const generatingContent = ref('')
  // 当前回答的 citations（JSON 解析后的数组）
  const currentCitations = ref([])
  // 取消控制器
  let abortController = null

  const isGenerating = computed(() => genStatus.value === 'generating')

  // ===== 会话管理 =====

  async function loadSessions() {
    sessionsLoading.value = true
    try {
      const res = await assistantApi.listSessions({ pageNum: 1, pageSize: 50 })
      sessions.value = res.data?.records || []
    } finally {
      sessionsLoading.value = false
    }
  }

  async function createSession(title) {
    const res = await assistantApi.createSession({ title: title || '新会话' })
    const session = res.data
    sessions.value.unshift(session)
    await selectSession(session.sessionId)
    return session
  }

  async function selectSession(sessionId) {
    // 切换前先 abort 正在进行的生成
    abortIfGenerating()
    currentSessionId.value = sessionId
    genStatus.value = 'idle'
    generatingContent.value = ''
    currentCitations.value = []
    await loadMessages(sessionId)
  }

  async function loadMessages(sessionId) {
    const res = await assistantApi.listMessages(sessionId)
    messages.value = res.data || []
  }

  async function deleteSession(sessionId) {
    await assistantApi.deleteSession(sessionId)
    sessions.value = sessions.value.filter(s => s.sessionId !== sessionId)
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = null
      messages.value = []
    }
  }

  // ===== ask / SSE =====

  async function ask(question) {
    if (!currentSessionId.value || isGenerating.value) {
      return
    }
    // 立即把 user 消息加到列表（乐观更新，后端也会落库）
    messages.value.push({ role: 'user', content: question, status: 1 })

    genStatus.value = 'generating'
    generatingContent.value = ''
    currentCitations.value = []

    abortController = assistantApi.askStream(
      { sessionId: currentSessionId.value, question },
      {
        onStart() {
          // 开始生成，占位 assistant 消息
        },
        onDelta(text) {
          generatingContent.value += text
        },
        onCitations(citationsJson) {
          try {
            currentCitations.value = JSON.parse(citationsJson)
          } catch (_) {
            currentCitations.value = []
          }
        },
        onComplete() {
          finalizeMessage('done')
        },
        onError(data, meta) {
          if (data === 'cancelled') {
            finalizeMessage('cancelled')
          } else {
            // meta.alreadyNotified=true 表示 api 层已弹提示（如 401/403），store 静默
            finalizeMessage('failed', data, { silent: meta && meta.alreadyNotified })
          }
        }
      }
    )
  }

  /** 完成/失败/取消时，把累积内容落为正式消息。
   * @param {string} status done/cancelled/failed
   * @param {string} [errorMsg] 失败时的错误信息
   * @param {Object} [options] { silent: true 表示 api 层已弹提示，store 不重复刷屏 }
   */
  function finalizeMessage(status, errorMsg, options) {
    const assistantMsg = {
      role: 'assistant',
      content: generatingContent.value,
      citations: currentCitations.value.length > 0
        ? JSON.stringify(currentCitations.value)
        : null,
      status: status === 'done' ? 1 : (status === 'cancelled' ? 3 : 2)
    }
    messages.value.push(assistantMsg)
    generatingContent.value = ''
    currentCitations.value = []
    genStatus.value = status
    abortController = null

    if (status === 'failed' && !(options && options.silent)) {
      ElMessage.error(errorMsg || 'AI 回答失败')
    } else if (status === 'cancelled') {
      ElMessage.info('已取消生成')
    }
    // 刷新会话列表（updatedTime 变了）
    loadSessions().catch(() => {})
  }

  /** 用户主动取消。
   * 注意：前端先乐观展示「取消」状态，后端依赖连接断开触发 SseEmitter 生命周期再落库，
   * 存在短暂不一致；后续重新进入会话时 loadMessages 会用后端历史覆盖本地乐观状态。
   */
  function cancel() {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    if (genStatus.value === 'generating') {
      finalizeMessage('cancelled')
    }
  }

  /** 如果正在生成，先取消（用于切换会话/组件卸载）。 */
  function abortIfGenerating() {
    if (isGenerating.value) {
      cancel()
    }
  }

  function reset() {
    abortIfGenerating()
    sessions.value = []
    currentSessionId.value = null
    messages.value = []
    genStatus.value = 'idle'
    generatingContent.value = ''
    currentCitations.value = []
  }

  return {
    sessions, sessionsLoading, currentSessionId, messages,
    genStatus, generatingContent, currentCitations, isGenerating,
    loadSessions, createSession, selectSession, loadMessages, deleteSession,
    ask, cancel, abortIfGenerating, reset
  }
})
