/**
 * SSE 流解析器（纯函数，无框架依赖）。
 *
 * 按标准 Server-Sent Events 协议解析 fetch + ReadableStream 读到的文本流：
 * - 空行（\n\n 或 \r\n\r\n）分隔事件
 * - event: / data: 字段（冒号后单个空格需剥离）
 * - 单事件可有多行 data:，用 \n 拼接
 * - chunk 可能半包：未完成事件缓冲到下个 chunk
 * - 以 ":" 开头的行是注释，忽略
 *
 * 用法：
 *   const parser = createSseParser({
 *     onEvent(event, data) { ... },
 *     onDone() { ... } // 可选，流结束时调用
 *   })
 *   reader.read() 循环里：parser.feed(textDecoder.decode(chunk))
 *   流结束：parser.end()
 */

/**
 * 创建一个 SSE 解析器实例。
 * @param {{onEvent?: (event: string, data: string) => void, onDone?: () => void}} handlers
 * @returns {{feed: (chunk: string) => void, end: () => void}}
 */
export function createSseParser(handlers = {}) {
  let buffer = ''

  function processBuffer() {
    // 按事件边界（空行）切分。事件之间可能是 \n\n 或 \r\n\r\n。
    // 用 /\r?\n\r?\n/ 统一匹配，保留剩余未完成部分。
    let boundary
    // eslint-disable-next-line no-cond-assign
    while ((boundary = findEventBoundary(buffer)) && boundary.index >= 0) {
      const rawEvent = buffer.slice(0, boundary.index)
      buffer = buffer.slice(boundary.index + boundary.length)
      dispatch(rawEvent)
    }
  }

  function dispatch(rawEvent) {
    if (rawEvent.length === 0) {
      return
    }
    const lines = rawEvent.split(/\r?\n/)
    let event = 'message' // SSE 默认事件名
    const dataLines = []
    for (const line of lines) {
      if (line.length === 0) {
        continue
      }
      // 注释行
      if (line.charAt(0) === ':') {
        continue
      }
      const colonIdx = line.indexOf(':')
      let field, value
      if (colonIdx === -1) {
        field = line
        value = ''
      } else {
        field = line.slice(0, colonIdx)
        value = line.slice(colonIdx + 1)
        // 字段值开头的单个空格要剥离（标准：冒号后若紧跟空格，去掉一个空格）
        if (value.charAt(0) === ' ') {
          value = value.slice(1)
        }
      }
      if (field === 'event') {
        event = value
      } else if (field === 'data') {
        dataLines.push(value)
      }
      // id:/retry: 字段一期不处理
    }
    const data = dataLines.join('\n')
    if (handlers.onEvent) {
      handlers.onEvent(event, data)
    }
  }

  return {
    feed(chunk) {
      if (!chunk) {
        return
      }
      buffer += chunk
      processBuffer()
    },
    end() {
      // 流结束：处理缓冲区中剩余的最后一个事件（即使末尾没有空行）
      if (buffer.length > 0) {
        const remaining = buffer
        buffer = ''
        if (remaining.trim().length > 0) {
          dispatch(remaining)
        }
      }
      if (handlers.onDone) {
        handlers.onDone()
      }
    }
  }
}

/** 在 buffer 中查找事件边界（空行），返回 {index, length} 或 -1。 */
function findEventBoundary(buf) {
  // 优先匹配 \n\n（最常见）
  const lf = buf.indexOf('\n\n')
  // 匹配 \r\n\r\n
  const crlf = buf.indexOf('\r\n\r\n')
  if (lf === -1 && crlf === -1) {
    return -1
  }
  if (crlf === -1 || (lf !== -1 && lf < crlf)) {
    return { index: lf, length: 2 }
  }
  return { index: crlf, length: 4 }
}
