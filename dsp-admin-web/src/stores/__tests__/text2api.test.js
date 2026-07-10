import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useText2ApiStore } from '../text2api'

// mock 整个 api 模块（store 依赖 text2apiApi）
vi.mock('../../api/text2api', () => ({
  text2apiApi: {
    listDrafts: vi.fn(),
    createDraft: vi.fn(),
    getDraft: vi.fn(),
    deleteDraft: vi.fn(),
    updateRequirement: vi.fn(),
    confirmStage: vi.fn(),
    rollbackStage: vi.fn(),
    publishDraft: vi.fn(),
    generateStageStream: vi.fn()
  }
}))

import { text2apiApi } from '../../api/text2api'

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.setItem('admin_token', 'test-token')
  vi.clearAllMocks()
})

describe('useText2ApiStore 状态机', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('result 后 complete 刷新草稿', async () => {
    const store = useText2ApiStore()
    store.selectedDraftId = 'd1'
    store.currentDraft = { draftId: 'd1', stage: 2 }

    // 模拟 generateStageStream：立即触发 result + complete
    text2apiApi.generateStageStream.mockImplementation((params, handlers) => {
      // 异步触发回调（模拟流事件）
      setTimeout(() => {
        handlers.onResult && handlers.onResult('已生成')
        handlers.onComplete && handlers.onComplete()
      }, 0)
      return { abort: () => {} }
    })
    text2apiApi.getDraft.mockResolvedValue({ data: { draftId: 'd1', stage: 3 } })

    store.generate(2, null)
    // 等待回调 + loadDraft
    await flush()

    // onResult 将 status 设为 done（阶段已生成），complete 后刷新草稿
    expect(store.streamStatus).toBe('done')
    // complete 后刷新了草稿
    expect(text2apiApi.getDraft).toHaveBeenCalledWith('d1')
  })

  it('needs_more_info 停止 generating', async () => {
    const store = useText2ApiStore()
    store.selectedDraftId = 'd1'
    text2apiApi.generateStageStream.mockImplementation((params, handlers) => {
      setTimeout(() => {
        handlers.onNeedsMoreInfo && handlers.onNeedsMoreInfo('请补充字段')
        handlers.onComplete && handlers.onComplete()
      }, 0)
      return { abort: () => {} }
    })

    store.generate(3, { source: 'USER_INPUT', tables: [] })
    await flush()

    expect(store.streamStatus).toBe('needs_more_info')
    expect(store.stageMessage).toBe('请补充字段')
    expect(store.isGenerating).toBe(false)
  })

  it('failed 停止 generating', async () => {
    const store = useText2ApiStore()
    store.selectedDraftId = 'd1'
    text2apiApi.generateStageStream.mockImplementation((params, handlers) => {
      setTimeout(() => {
        handlers.onFailed && handlers.onFailed('解析失败')
        handlers.onComplete && handlers.onComplete()
      }, 0)
      return { abort: () => {} }
    })

    store.generate(2, null)
    await flush()

    expect(store.streamStatus).toBe('failed')
    expect(store.stageMessage).toBe('解析失败')
    expect(store.isGenerating).toBe(false)
  })

  it('publish 成功刷新草稿', async () => {
    const store = useText2ApiStore()
    store.selectedDraftId = 'd1'
    text2apiApi.publishDraft.mockResolvedValue({})
    text2apiApi.getDraft.mockResolvedValue({ data: { draftId: 'd1', stage: 6 } })

    await store.publish()

    expect(text2apiApi.publishDraft).toHaveBeenCalledWith('d1')
    expect(text2apiApi.getDraft).toHaveBeenCalledWith('d1')
    expect(store.currentDraft.stage).toBe(6)
  })

  it('publish 失败后刷新草稿并保留 publishError', async () => {
    const store = useText2ApiStore()
    store.selectedDraftId = 'd1'
    text2apiApi.publishDraft.mockRejectedValue(new Error('导入服务异常'))
    // 失败后 getDraft 返回带 publishError 的草稿
    text2apiApi.getDraft.mockResolvedValue({ data: { draftId: 'd1', stage: 5, publishError: '导入服务异常' } })

    await store.publish()

    expect(text2apiApi.publishDraft).toHaveBeenCalledWith('d1')
    expect(text2apiApi.getDraft).toHaveBeenCalledWith('d1')
    expect(store.currentDraft.publishError).toBe('导入服务异常')
  })

  it('selectDraft 切换时 abort 正在进行的生成', async () => {
    const store = useText2ApiStore()
    store.selectedDraftId = 'd1'
    let aborted = false
    text2apiApi.generateStageStream.mockImplementation((params, handlers) => {
      return { abort: () => { aborted = true } }
    })
    text2apiApi.getDraft.mockResolvedValue({ data: { draftId: 'd2' } })

    store.generate(2, null)
    expect(store.isGenerating).toBe(true)

    await store.selectDraft('d2')
    expect(aborted).toBe(true)
    expect(store.isGenerating).toBe(false)
  })

  it('evidenceValid：表名 + 至少一个字段都非空才为 true（硬约束）', () => {
    const store = useText2ApiStore()
    // 空表 → false
    expect(store.evidenceValid).toBe(false)
    // 只有表名、无字段 → false（硬约束：无字段依据不能生成 SQL）
    store.schemaEvidence.tables.push({ tableName: 'users', columnsText: '' })
    expect(store.evidenceValid).toBe(false)
    // 表名 + 字段 → true
    store.schemaEvidence.tables[0].columnsText = 'id,name'
    expect(store.evidenceValid).toBe(true)
    // 第二张表无字段 → 整体 false
    store.schemaEvidence.tables.push({ tableName: 'orders', columnsText: '' })
    expect(store.evidenceValid).toBe(false)
    // 补全第二张表字段 → true
    store.schemaEvidence.tables[1].columnsText = 'order_id'
    expect(store.evidenceValid).toBe(true)
    // columns 数组形式也支持
    store.schemaEvidence.tables[0] = { tableName: 'users', columns: ['id', 'name'], columnsText: '' }
    expect(store.evidenceValid).toBe(true)
  })
})

/** 等待 setTimeout(0) 触发的回调。 */
function flush() {
  return new Promise(resolve => setTimeout(resolve, 10))
}
