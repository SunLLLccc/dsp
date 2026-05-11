/**
 * 格式化时间：去掉 T，截取到秒
 */
export function fmtTime(val) {
  if (!val) return ''
  return String(val).replace('T', ' ').substring(0, 19)
}

/**
 * 格式化 JSON 字符串
 */
export function formatJson(str) {
  if (!str) return ''
  try { return JSON.stringify(JSON.parse(str), null, 2) } catch { return str }
}

/**
 * 简单行级 diff：返回 { left: [{text, type}], right: [{text, type}] }
 * type: 'common' | 'removed' | 'added'
 */
export function diffLines(oldStr, newStr) {
  const oldLines = (oldStr || '').split('\n')
  const newLines = (newStr || '').split('\n')
  const m = oldLines.length
  const n = newLines.length

  // LCS 动态规划
  const dp = Array.from({ length: m + 1 }, () => new Uint16Array(n + 1))
  for (let i = m - 1; i >= 0; i--) {
    for (let j = n - 1; j >= 0; j--) {
      dp[i][j] = oldLines[i] === newLines[j]
        ? dp[i + 1][j + 1] + 1
        : Math.max(dp[i + 1][j], dp[i][j + 1])
    }
  }

  // 回溯
  const left = []
  const right = []
  let i = 0, j = 0
  while (i < m && j < n) {
    if (oldLines[i] === newLines[j]) {
      left.push({ text: oldLines[i], type: 'common' })
      right.push({ text: newLines[j], type: 'common' })
      i++; j++
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      left.push({ text: oldLines[i], type: 'removed' })
      i++
    } else {
      right.push({ text: newLines[j], type: 'added' })
      j++
    }
  }
  while (i < m) { left.push({ text: oldLines[i++], type: 'removed' }) }
  while (j < n) { right.push({ text: newLines[j++], type: 'added' }) }

  // 对齐：保证左右行数一致
  const maxLen = Math.max(left.length, right.length)
  while (left.length < maxLen) left.push({ text: '', type: 'placeholder' })
  while (right.length < maxLen) right.push({ text: '', type: 'placeholder' })

  return { left, right }
}
