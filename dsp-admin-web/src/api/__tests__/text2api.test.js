import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { text2apiApi } from '../text2api'

beforeEach(() => {
  // generateStageStream 内部调用 useAuthStore()，需要 active pinia
  setActivePinia(createPinia())
  localStorage.setItem('admin_token', 'test-token')
})

describe('text2apiApi.generateStageStream', () => {
  const originalFetch = global.fetch

  afterEach(() => {
    global.fetch = originalFetch
    vi.restoreAllMocks()
  })

  it('正常 result + complete', async () => {
    const result = await runStream([
      'event:start\ndata:\n\n',
      'event:result\ndata:接口定义已生成\n\n',
      'event:complete\ndata:\n\n'
    ])
    expect(result.events).toContain('start')
    expect(result.events.some(e => e.type === 'result')).toBe(true)
    expect(result.events).toContain('complete')
    expect(result.events.some(e => e.type === 'error')).toBe(false)
  })

  it('needs_more_info 触发 onNeedsMoreInfo', async () => {
    const result = await runStream([
      'event:needs_more_info\ndata:请补充字段信息\n\n',
      'event:complete\ndata:\n\n'
    ])
    expect(result.events.some(e => e.type === 'needs_more_info' && e.data === '请补充字段信息')).toBe(true)
  })

  it('failed 触发 onFailed', async () => {
    const result = await runStream([
      'event:failed\ndata:解析失败\n\n',
      'event:complete\ndata:\n\n'
    ])
    expect(result.events.some(e => e.type === 'failed' && e.data === '解析失败')).toBe(true)
  })

  it('非 2xx 触发 onError 且不卡在 generating', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({ message: '服务异常' })
    })
    const events = await runWithHandlers()
    expect(events.length).toBe(1)
    expect(events[0].type).toBe('error')
    expect(events[0].msg).toBe('服务异常')
    expect(events[0].meta.alreadyNotified).toBe(true)
  })

  it('流结束无 terminal 触发 onError', async () => {
    // 只发 result，没有 complete/error 就断流
    const result = await runStream(['event:result\ndata:已生成\n\n'])
    expect(result.events.some(e => e.type === 'error' && /未收到完成事件/.test(e.msg))).toBe(true)
  })

  it('abort 不触发 onError（用户主动取消）', async () => {
    // 用一个永不 close 的流 + 手动 abort
    let streamController
    const stream = new ReadableStream({
      start(c) { streamController = c }
    })
    global.fetch = vi.fn().mockResolvedValue({ ok: true, body: stream })

    const events = []
    const ret = text2apiApi.generateStageStream(
      { draftId: 'd1', stage: 2 },
      {
        onComplete: () => events.push('complete'),
        onError: (msg) => events.push('error:' + msg)
      }
    )
    // 立即 abort
    ret.abort()
    // 等待微任务/宏任务
    await new Promise(r => setTimeout(r, 100))
    expect(events.some(e => e.startsWith('error'))).toBe(false)
  })
})

/**
 * 工具：喂入 chunks 流，返回收到的事件（complete/error 后 resolve）。
 * 自行构造 OK 流并 mock fetch。
 */
function runStream(chunks) {
  const encoder = new TextEncoder()
  const stream = new ReadableStream({
    start(controller) {
      for (const c of chunks) controller.enqueue(encoder.encode(c))
      controller.close()
    }
  })
  global.fetch = vi.fn().mockResolvedValue({ ok: true, body: stream })
  return runWithHandlers().then(events => ({ events }))
}

/**
 * 工具：不 mock fetch（调用方已 mock），仅注入 handlers 收集事件，resolve on terminal。
 * 返回事件数组。
 */
function runWithHandlers() {
  const events = []
  return new Promise((resolve) => {
    text2apiApi.generateStageStream(
      { draftId: 'd1', stage: 2 },
      {
        onStart: () => events.push('start'),
        onResult: (d) => events.push({ type: 'result', data: d }),
        onNeedsMoreInfo: (d) => events.push({ type: 'needs_more_info', data: d }),
        onFailed: (d) => events.push({ type: 'failed', data: d }),
        onComplete: () => { events.push('complete'); resolve(events) },
        onError: (msg, meta) => { events.push({ type: 'error', msg, meta }); resolve(events) },
        onCancelled: (d) => events.push({ type: 'cancelled', data: d })
      }
    )
    setTimeout(() => resolve(events), 300)
  })
}
