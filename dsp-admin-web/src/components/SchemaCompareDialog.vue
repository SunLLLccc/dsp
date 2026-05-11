<template>
  <el-dialog v-model="visible" :title="title" width="1100px" top="5vh" destroy-on-close @close="$emit('update:modelValue', false)">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="输入报文" name="input">
        <div class="compare-container">
          <div class="compare-side">
            <div class="compare-header">{{ leftLabel }}</div>
            <el-input :model-value="formatJson(leftInput)" type="textarea" :rows="20" readonly style="font-family:monospace" />
          </div>
          <div class="compare-divider" />
          <div class="compare-side">
            <div class="compare-header">{{ rightLabel }}</div>
            <el-input :model-value="formatJson(rightInput)" type="textarea" :rows="20" readonly style="font-family:monospace" />
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane label="输出报文" name="output">
        <div class="compare-container">
          <div class="compare-side">
            <div class="compare-header">{{ leftLabel }}</div>
            <el-input :model-value="formatJson(leftOutput)" type="textarea" :rows="20" readonly style="font-family:monospace" />
          </div>
          <div class="compare-divider" />
          <div class="compare-side">
            <div class="compare-header">{{ rightLabel }}</div>
            <el-input :model-value="formatJson(rightOutput)" type="textarea" :rows="20" readonly style="font-family:monospace" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: 'Schema 对比' },
  leftLabel: { type: String, default: '历史版本' },
  rightLabel: { type: String, default: '当前版本' },
  leftInput: { type: String, default: '' },
  leftOutput: { type: String, default: '' },
  rightInput: { type: String, default: '' },
  rightOutput: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => { if (!v) emit('update:modelValue', false) }
})
const activeTab = ref('input')

function formatJson(str) {
  if (!str) return ''
  try { return JSON.stringify(JSON.parse(str), null, 2) } catch { return str }
}

watch(() => props.modelValue, (v) => { if (v) activeTab.value = 'input' })
</script>

<style scoped>
.compare-container { display: flex; gap: 0; }
.compare-side { flex: 1; min-width: 0; }
.compare-header {
  padding: 6px 0;
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 8px;
}
.compare-divider { width: 6px; background: #e4e7ed; margin: 0 6px; border-radius: 3px; flex-shrink: 0; }
</style>
