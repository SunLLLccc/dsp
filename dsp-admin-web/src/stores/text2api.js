import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { text2apiApi } from '../api/text2api'
import { ElMessage } from 'element-plus'

/**
 * Text2API 工作区状态。
 *
 * 管理：草稿列表、当前草稿、生成状态机、SchemaEvidence 表单、取消控制器。
 * 页面组件保持薄，复杂状态在这里管理。
 *
 * 生成状态机 streamStatus：
 *   idle → generating → (done | needs_more_info | failed | cancelled)
 *   - result 事件：阶段已生成 → complete 后刷新草稿
 *   - needs_more_info：停止 generating，展示追问
 *   - failed：停止 generating，展示原因
 *   - complete：终态，刷新草稿详情
 *   - error/cancelled：终态，停止 generating
 */
export const useText2ApiStore = defineStore('text2api', () => {
  // 草稿列表
  const drafts = ref([])
  const draftsLoading = ref(false)
  // 当前草稿
  const currentDraft = ref(null)
  const selectedDraftId = ref(null)
  const draftLoading = ref(false)
  // 生成状态
  const streamStatus = ref('idle') // idle / generating / done / needs_more_info / failed / cancelled
  const generatingStage = ref(null) // 当前正在生成的阶段
  const stageMessage = ref('') // 生成结果/失败/追问信息
  const error = ref('')
  // 取消控制器
  let abortController = null
  // SchemaEvidence 表单状态（阶段 3 用）
  const schemaEvidence = ref({ source: 'USER_INPUT', tables: [] })

  const isGenerating = computed(() => streamStatus.value === 'generating')

  // ===== 草稿管理 =====

  async function loadDrafts() {
    draftsLoading.value = true
    try {
      const res = await text2apiApi.listDrafts()
      drafts.value = res.data || []
    } finally {
      draftsLoading.value = false
    }
  }

  async function createDraft(title) {
    const res = await text2apiApi.createDraft({ title: title || null })
    const draft = res.data
    drafts.value.unshift(draft)
    await selectDraft(draft.draftId)
    return draft
  }

  async function selectDraft(draftId) {
    // 切换前先 abort 正在进行的生成
    abortIfGenerating()
    selectedDraftId.value = draftId
    streamStatus.value = 'idle'
    generatingStage.value = null
    stageMessage.value = ''
    error.value = ''
    await loadDraft(draftId)
  }

  async function loadDraft(draftId) {
    draftLoading.value = true
    try {
      const res = await text2apiApi.getDraft(draftId)
      currentDraft.value = res.data
    } finally {
      draftLoading.value = false
    }
  }

  async function deleteDraft(draftId) {
    await text2apiApi.deleteDraft(draftId)
    drafts.value = drafts.value.filter(d => d.draftId !== draftId)
    if (selectedDraftId.value === draftId) {
      selectedDraftId.value = null
      currentDraft.value = null
    }
  }

  async function updateRequirement(text) {
    const res = await text2apiApi.updateRequirement(selectedDraftId.value, text)
    currentDraft.value = res.data
    return res.data
  }

  async function confirmStage(stage) {
    const res = await text2apiApi.confirmStage(selectedDraftId.value, stage)
    currentDraft.value = res.data
    return res.data
  }

  async function rollbackStage(stage) {
    const res = await text2apiApi.rollbackStage(selectedDraftId.value, stage)
    currentDraft.value = res.data
    return res.data
  }

  // ===== 阶段生成 SSE =====

  /**
   * 发起阶段生成。
   * @param {number} stage 目标阶段 2-5
   * @param {Object} [evidence] 仅阶段 3
   */
  function generate(stage, evidence) {
    if (!selectedDraftId.value || isGenerating.value) {
      return
    }
    streamStatus.value = 'generating'
    generatingStage.value = stage
    stageMessage.value = ''
    error.value = ''

    abortController = text2apiApi.generateStageStream(
      {
        draftId: selectedDraftId.value,
        stage,
        evidence: stage === 3 ? schemaEvidenceToPayload(evidence) : null
      },
      {
        onStart() {},
        onResult() {
          // 阶段已生成，complete 后刷新草稿
          streamStatus.value = 'done'
        },
        onNeedsMoreInfo(data) {
          streamStatus.value = 'needs_more_info'
          stageMessage.value = data || '需要补充更多信息'
        },
        onFailed(data) {
          streamStatus.value = 'failed'
          stageMessage.value = data || '阶段生成失败'
          ElMessage.error(stageMessage.value)
        },
        onComplete() {
          finalizeGenerate()
        },
        onError(data, meta) {
          // meta.alreadyNotified=true 表示 api 层已弹提示（401/403），store 静默
          streamStatus.value = 'failed'
          error.value = data || '生成失败'
          if (!(meta && meta.alreadyNotified)) {
            ElMessage.error(error.value)
          }
          // 终态：清 abortController
          abortController = null
        },
        onCancelled() {
          streamStatus.value = 'cancelled'
          abortController = null
        }
      }
    )
  }

  /** complete 终态：刷新草稿详情（拿到最新产物），清 abortController。 */
  function finalizeGenerate() {
    abortController = null
    // 刷新当前草稿详情（result 后产物已落库）
    if (selectedDraftId.value) {
      loadDraft(selectedDraftId.value).catch(() => {})
    }
  }

  /** 用户主动取消。 */
  function cancel() {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    if (streamStatus.value === 'generating') {
      streamStatus.value = 'cancelled'
    }
  }

  /** 如果正在生成，先取消（用于切换草稿/组件卸载）。 */
  function abortIfGenerating() {
    if (isGenerating.value) {
      cancel()
    }
  }

  // ===== 发布 =====

  async function publish() {
    const draftId = selectedDraftId.value
    try {
      await text2apiApi.publishDraft(draftId)
      ElMessage.success('发布成功')
    } catch (e) {
      // 失败：刷新草稿拿到 publishError，保留重试
      ElMessage.error(e.response?.data?.message || e.message || '发布失败')
    } finally {
      // 无论成功失败都刷新草稿详情（成功展示已发布；失败展示 publishError）
      await loadDraft(draftId).catch(() => {})
    }
  }

  // ===== SchemaEvidence 表单 =====

  function addEvidenceTable() {
    // columnsText：页面用逗号分隔字符串输入，store 内统一保留该字段
    schemaEvidence.value.tables.push({ tableName: '', columnsText: '', description: '' })
  }

  function removeEvidenceTable(index) {
    schemaEvidence.value.tables.splice(index, 1)
  }

  /**
   * SchemaEvidence 是否有效（硬约束：无表结构依据不能生成 SQL）。
   * 至少一张表，且每张表 tableName 非空 + 至少一个非空字段。
   * 与后端 SchemaEvidence.isEmpty 收紧口径一致。
   */
  const evidenceValid = computed(() => {
    const tables = schemaEvidence.value.tables
    if (!tables.length) return false
    return tables.every(t => {
      const cols = parseColumns(t)
      return t.tableName && t.tableName.trim() && cols.length > 0
    })
  })

  /** 把表对象的字段来源（columns 数组或 columnsText 字符串）统一解析成去空后的字段数组。 */
  function parseColumns(t) {
    if (!t) return []
    if (Array.isArray(t.columns) && t.columns.length) {
      return t.columns.map(c => String(c).trim()).filter(Boolean)
    }
    if (typeof t.columnsText === 'string' && t.columnsText.trim()) {
      return t.columnsText.split(',').map(c => c.trim()).filter(Boolean)
    }
    return []
  }

  /** store 内部表单 → 后端 payload。columns 从逗号分隔字符串转数组。 */
  function schemaEvidenceToPayload(evidence) {
    const ev = evidence || schemaEvidence.value
    return {
      source: ev.source || 'USER_INPUT',
      tables: (ev.tables || []).map(t => ({
        tableName: t.tableName,
        columns: parseColumns(t),
        description: t.description || ''
      }))
    }
  }

  function reset() {
    abortIfGenerating()
    drafts.value = []
    currentDraft.value = null
    selectedDraftId.value = null
    streamStatus.value = 'idle'
    generatingStage.value = null
    stageMessage.value = ''
    error.value = ''
    schemaEvidence.value = { source: 'USER_INPUT', tables: [] }
  }

  return {
    drafts, draftsLoading, currentDraft, selectedDraftId, draftLoading,
    streamStatus, generatingStage, stageMessage, error, isGenerating,
    schemaEvidence, evidenceValid,
    loadDrafts, createDraft, selectDraft, loadDraft, deleteDraft,
    updateRequirement, confirmStage, rollbackStage,
    generate, cancel, abortIfGenerating, publish,
    addEvidenceTable, removeEvidenceTable, reset
  }
})
