<template>
  <el-dialog v-model="visible" :title="title" width="1150px" top="5vh" destroy-on-close @close="$emit('update:modelValue', false)">
    <div class="compare-tabs-wrap">
      <el-switch v-model="diffOnly" active-text="仅看差异" inactive-text="全部" class="compare-switch" />
    </div>
    <div class="compare-container">
      <div class="compare-side">
        <div class="compare-header">{{ leftLabel }}</div>
        <div class="compare-code">
          <template v-for="(line, idx) in diffResult.left" :key="idx">
            <div v-if="!diffOnly || line.type !== 'common'" :class="['diff-line', line.type]">
              <span class="line-no">{{ line.lineNo || '' }}</span>
              <span class="line-prefix">{{ line.type === 'removed' ? '-' : line.type === 'common' ? ' ' : '' }}</span>
              <span>{{ line.text }}</span>
            </div>
          </template>
        </div>
      </div>
      <div class="compare-divider" />
      <div class="compare-side">
        <div class="compare-header">{{ rightLabel }}</div>
        <div class="compare-code">
          <template v-for="(line, idx) in diffResult.right" :key="idx">
            <div v-if="!diffOnly || line.type !== 'common'" :class="['diff-line', line.type]">
              <span class="line-no">{{ line.lineNo || '' }}</span>
              <span class="line-prefix">{{ line.type === 'added' ? '+' : line.type === 'common' ? ' ' : '' }}</span>
              <span>{{ line.text }}</span>
            </div>
          </template>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { diffLines } from '../utils/format'

const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: 'XML 对比' },
  leftLabel: { type: String, default: '历史版本' },
  rightLabel: { type: String, default: '当前版本' },
  leftXml: { type: String, default: '' },
  rightXml: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => { if (!v) emit('update:modelValue', false) }
})
const diffOnly = ref(false)

const diffResult = computed(() => diffLines(props.leftXml || '', props.rightXml || ''))

watch(() => props.modelValue, (v) => { if (v) diffOnly.value = false })
</script>

<style scoped>
.compare-tabs-wrap { position: relative; margin-bottom: 10px; }
.compare-switch { position: absolute; top: 0; right: 0; z-index: 1; }
.compare-container { display: flex; gap: 0; }
.compare-side { flex: 1; min-width: 0; }
.compare-header {
  padding: 6px 0;
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 4px;
}
.compare-divider { width: 6px; background: #e4e7ed; margin: 0 6px; border-radius: 3px; flex-shrink: 0; }
.compare-code {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
  max-height: 480px;
  overflow-y: auto;
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
.diff-line {
  padding: 0 8px 0 0;
  white-space: pre;
  min-height: 20px;
  display: flex;
}
.diff-line.common { background: transparent; }
.diff-line.removed { background: #fde2e2; color: #c45656; }
.diff-line.added { background: #e1f3d8; color: #4a8f3f; }
.diff-line.placeholder { background: #f5f5f5; }
.line-no {
  display: inline-block;
  width: 32px;
  text-align: right;
  color: #b0b0b0;
  user-select: none;
  padding-right: 6px;
  font-size: 12px;
  flex-shrink: 0;
}
.line-prefix {
  display: inline-block;
  width: 20px;
  text-align: center;
  color: #999;
  user-select: none;
  font-weight: 600;
}
.diff-line.removed .line-prefix { color: #c45656; }
.diff-line.added .line-prefix { color: #4a8f3f; }
</style>
