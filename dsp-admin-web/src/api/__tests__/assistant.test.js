import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { assistantApi } from '../assistant'

beforeEach(() => {
  // askStream 内部调用 useAuthStore()，需要 active pinia
  setActivePinia(createPinia())
})

/**
 * askStream 的流消费逻辑单测，重点覆盖：
 * - 流正常结束但未收到 terminal event → onError（必改1）
 * - 正常 complete → onComplete
 */
describe('assistantApi.askStream', () => {
  const originalFetch = global.fetch

  afterEach(() => {
    global.fetch = originalFetch
    vi.restoreAllMocks()
  })

  function mockFetchStream(chunks) {
    const encoder = new TextEncoder()
    const stream = new ReadableStream({
      start(controller) {
        for (const c of chunks) {
          controller.enqueue(encoder.encode(c))
        }
        controller.close()
      }
    })
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      body: stream
    })
  }

  function mockAuth() {
    // 注入 token 到 localStorage（auth store 读 localStorage）
    localStorage.setItem('admin_token', 'test-token')
  }

  it('流正常结束但未收到 terminal event → onError', async () => {
    mockAuth()
    // 只发 delta，没有 complete/error 就断流
    mockFetchStream(['event:delta\ndata:部分回答\n\n'])

    const events = []
    await new Promise((resolve) => {
      assistantApi.askStream(
        { sessionId: 's1', question: 'q' },
        {
          onDelta: (t) => events.push({ type: 'delta', text: t }),
          onComplete: () => events.push({ type: 'complete' }),
          onError: (msg) => { events.push({ type: 'error', msg }); resolve() }
        }
      )
      // 兜底 resolve（若未触发 onError）
      setTimeout(resolve, 200)
    })

    expect(events.some(e => e.type === 'delta' && e.text === '部分回答')).toBe(true)
    expect(events.some(e => e.type === 'error' && /未收到完成事件/.test(e.msg))).toBe(true)
  })

  it('正常 complete → onComplete，不触发 onError', async () => {
    mockAuth()
    mockFetchStream([
      'event:start\ndata:\n\n',
      'event:delta\ndata:你好\n\n',
      'event:complete\ndata:\n\n'
    ])

    const events = []
    await new Promise((resolve) => {
      assistantApi.askStream(
        { sessionId: 's1', question: 'q' },
        {
          onStart: () => events.push('start'),
          onDelta: (t) => events.push('delta:' + t),
          onComplete: () => { events.push('complete'); resolve() },
          onError: (msg) => events.push('error:' + msg)
        }
      )
      setTimeout(resolve, 200)
    })

    expect(events).toContain('start')
    expect(events).toContain('delta:你好')
    expect(events).toContain('complete')
    expect(events.some(e => typeof e === 'string' && e.startsWith('error'))).toBe(false)
  })

  it('跨 chunk 分片 delta 能正确拼接', async () => {
    mockAuth()
    // delta 内容跨 chunk 拆开，最后有 complete
    mockFetchStream([
      'event:delta\nda',
      'ta:hel',
      'lo\n\nevent:complete\ndata:\n\n'
    ])

    const deltas = []
    await new Promise((resolve) => {
      assistantApi.askStream(
        { sessionId: 's1', question: 'q' },
        {
          onDelta: (t) => deltas.push(t),
          onComplete: () => resolve(),
          onError: () => resolve()
        }
      )
      setTimeout(resolve, 200)
    })

    expect(deltas.join('')).toBe('hello')
  })

  // ===== 必改：非 2xx 必须触发 onError，不能卡在 generating =====

  it('500 返回时调用 onError 并带 message', async () => {
    mockAuth()
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({ message: '服务异常' })
    })

    const errors = []
    await new Promise((resolve) => {
      assistantApi.askStream(
        { sessionId: 's1', question: 'q' },
        {
          onError: (msg, meta) => { errors.push({ msg, meta }); resolve() },
          onComplete: () => resolve()
        }
      )
      setTimeout(resolve, 300)
    })

    expect(errors.length).toBe(1)
    expect(errors[0].msg).toBe('服务异常')
    expect(errors[0].meta && errors[0].meta.alreadyNotified).toBe(true)
  })

  it('403 返回时调用 onError', async () => {
    mockAuth()
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 403,
      json: async () => ({ message: '无操作权限' })
    })

    const errors = []
    await new Promise((resolve) => {
      assistantApi.askStream(
        { sessionId: 's1', question: 'q' },
        {
          onError: (msg, meta) => { errors.push({ msg, meta }); resolve() },
          onComplete: () => resolve()
        }
      )
      setTimeout(resolve, 300)
    })

    expect(errors.length).toBe(1)
    expect(errors[0].msg).toBe('无操作权限')
    expect(errors[0].meta.alreadyNotified).toBe(true)
  })
})
