<template>
  <div class="citations-view" v-if="parsed.length > 0">
    <div class="citations-title">
      <el-icon><Document /></el-icon>
      <span>引用来源</span>
    </div>
    <div v-for="(group, type) in grouped" :key="type" class="citation-group">
      <div class="group-label">{{ typeLabel(type) }}</div>
      <div v-for="(c, idx) in group" :key="idx" class="citation-item">
        <div class="citation-header">
          <span class="citation-path">{{ c.path }}</span>
          <span v-if="c.lineStart" class="citation-lines">
            :{{ c.lineStart }}<template v-if="c.lineEnd">-{{ c.lineEnd }}</template>
          </span>
        </div>
        <div v-if="c.title" class="citation-title">{{ c.title }}</div>
        <pre class="citation-snippet">{{ c.snippet }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Document } from '@element-plus/icons-vue'

const props = defineProps({
  // citations 可以是 JSON 字符串或数组
  citations: { type: [String, Array], default: () => [] }
})

const parsed = computed(() => {
  if (!props.citations) return []
  if (Array.isArray(props.citations)) return props.citations
  try {
    return JSON.parse(props.citations)
  } catch (_) {
    return []
  }
})

// 按 type 分组：doc / source
const grouped = computed(() => {
  const g = {}
  for (const c of parsed.value) {
    const t = c.type || 'doc'
    if (!g[t]) g[t] = []
    g[t].push(c)
  }
  return g
})

function typeLabel(type) {
  return type === 'source' ? '源码依据' : '文档依据'
}
</script>

<style scoped>
.citations-view {
  margin-top: 8px;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  font-size: 12px;
}
.citations-title {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}
.citation-group {
  margin-bottom: 8px;
}
.group-label {
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-bottom: 4px;
}
.citation-item {
  margin-bottom: 6px;
  padding: 6px 8px;
  background: var(--el-bg-color);
  border-radius: 4px;
}
.citation-header {
  display: flex;
  gap: 4px;
  align-items: baseline;
}
.citation-path {
  color: var(--el-color-primary);
  font-family: monospace;
  word-break: break-all;
}
.citation-lines {
  color: var(--el-text-color-secondary);
  font-family: monospace;
}
.citation-title {
  color: var(--el-text-color-regular);
  margin: 2px 0;
}
.citation-snippet {
  margin: 4px 0 0;
  padding: 6px;
  background: var(--el-fill-color);
  border-radius: 3px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 120px;
  overflow-y: auto;
  font-family: monospace;
  font-size: 11px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
}
</style>
