import { describe, it, expect } from 'vitest'
import { createSseParser } from '../sseParser'

describe('sseParser', () => {
  function collect() {
    const events = []
    const parser = createSseParser({
      onEvent: (event, data) => events.push({ event, data })
    })
    return { parser, events }
  }

  it('单 chunk 单事件', () => {
    const { parser, events } = collect()
    parser.feed('event:delta\ndata:你好\n\n')
    expect(events).toEqual([{ event: 'delta', data: '你好' }])
  })

  it('默认 event 为 message', () => {
    const { parser, events } = collect()
    parser.feed('data:hello\n\n')
    expect(events).toEqual([{ event: 'message', data: 'hello' }])
  })

  it('跨 chunk 分片：event 行被拆开', () => {
    const { parser, events } = collect()
    parser.feed('event:del')
    parser.feed('ta\ndata:hi\n\n')
    expect(events).toEqual([{ event: 'delta', data: 'hi' }])
  })

  it('跨 chunk 分片：data 被拆开', () => {
    const { parser, events } = collect()
    parser.feed('event:delta\ndata:hel')
    parser.feed('lo\n\n')
    expect(events).toEqual([{ event: 'delta', data: 'hello' }])
  })

  it('跨 chunk 分片：边界 \\n\\n 正好在拆分点', () => {
    const { parser, events } = collect()
    parser.feed('event:start\ndata:\n')
    parser.feed('\nevent:complete\ndata:\n\n')
    expect(events).toEqual([
      { event: 'start', data: '' },
      { event: 'complete', data: '' }
    ])
  })

  it('多行 data 用换行拼接', () => {
    const { parser, events } = collect()
    parser.feed('event:delta\ndata:line1\ndata:line2\ndata:line3\n\n')
    expect(events).toEqual([{ event: 'delta', data: 'line1\nline2\nline3' }])
  })

  it('多事件连续', () => {
    const { parser, events } = collect()
    parser.feed('event:start\ndata:\n\nevent:delta\ndata:A\ndata:B\n\nevent:complete\ndata:\n\n')
    expect(events).toEqual([
      { event: 'start', data: '' },
      { event: 'delta', data: 'A\nB' },
      { event: 'complete', data: '' }
    ])
  })

  it('冒号后单个空格被剥离', () => {
    const { parser, events } = collect()
    parser.feed('event: error\ndata: failed reason\n\n')
    expect(events).toEqual([{ event: 'error', data: 'failed reason' }])
  })

  it('冒号后无空格时不剥离', () => {
    const { parser, events } = collect()
    parser.feed('data:nospace\n\n')
    expect(events).toEqual([{ event: 'message', data: 'nospace' }])
  })

  it('注释行（以冒号开头）被忽略', () => {
    const { parser, events } = collect()
    parser.feed(': this is a comment\nevent:delta\ndata:ok\n\n')
    expect(events).toEqual([{ event: 'delta', data: 'ok' }])
  })

  it('不完整事件缓冲到下个 chunk', () => {
    const { parser, events } = collect()
    parser.feed('event:delta\ndata:not-finished-yet') // 无空行结尾
    expect(events).toEqual([])
    parser.feed(' continued\n\n')
    expect(events).toEqual([{ event: 'delta', data: 'not-finished-yet continued' }])
  })

  it('CRLF 行尾（\\r\\n\\r\\n 边界）', () => {
    const { parser, events } = collect()
    parser.feed('event:delta\ndata:hi\r\n\r\n')
    expect(events).toEqual([{ event: 'delta', data: 'hi' }])
  })

  it('end() 处理缓冲区剩余的最后一个事件（无空行结尾）', () => {
    const { parser, events } = collect()
    parser.feed('event:delta\ndata:tail')
    expect(events).toEqual([])
    parser.end()
    expect(events).toEqual([{ event: 'delta', data: 'tail' }])
  })

  it('end() 调用 onDone', () => {
    let done = false
    const parser = createSseParser({ onDone: () => { done = true } })
    parser.end()
    expect(done).toBe(true)
  })

  it('空 chunk 与空 feed 不影响', () => {
    const { parser, events } = collect()
    parser.feed('')
    parser.feed(null)
    parser.feed('event:delta\ndata:x\n\n')
    expect(events).toEqual([{ event: 'delta', data: 'x' }])
  })
})
