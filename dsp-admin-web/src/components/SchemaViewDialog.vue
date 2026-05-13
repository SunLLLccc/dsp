<template>
  <el-dialog v-model="visible" :title="title" width="1100px" top="5vh" destroy-on-close @close="$emit('update:modelValue', false)">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="输入报文" name="input">
        <div class="schema-editor-container" ref="editorContainer">
          <div class="schema-left" :style="{ width: leftWidth + 'px' }">
            <div class="schema-panel-header">JSON</div>
            <div class="code-view">
              <div class="code-lines"><span v-for="n in inputLineCount" :key="n" class="code-ln">{{ n }}</span></div>
              <pre class="code-text">{{ formatJson(inputSchema) }}</pre>
            </div>
          </div>
          <div class="schema-divider" @mousedown="startDrag" />
          <div class="schema-right" :style="{ width: rightWidth + 'px' }">
            <div class="schema-panel-header">字段结构</div>
            <div class="schema-tree">
              <SchemaTreeNode
                v-for="(_, idx) in inputFields"
                :key="idx"
                :field="inputFields[idx]"
                :editable="false"
              />
              <div v-if="inputFields.length === 0" class="schema-empty">暂无字段定义</div>
            </div>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane label="输出报文" name="output">
        <div class="schema-editor-container">
          <div class="schema-left" :style="{ width: leftWidth + 'px' }">
            <div class="schema-panel-header">JSON</div>
            <div class="code-view">
              <div class="code-lines"><span v-for="n in outputLineCount" :key="n" class="code-ln">{{ n }}</span></div>
              <pre class="code-text">{{ formatJson(outputSchema) }}</pre>
            </div>
          </div>
          <div class="schema-divider" @mousedown="startDrag" />
          <div class="schema-right" :style="{ width: rightWidth + 'px' }">
            <div class="schema-panel-header">字段结构</div>
            <div class="schema-tree">
              <SchemaTreeNode
                v-for="(_, idx) in outputFields"
                :key="idx"
                :field="outputFields[idx]"
                :editable="false"
              />
              <div v-if="outputFields.length === 0" class="schema-empty">暂无字段定义</div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed, onBeforeUnmount } from 'vue'
import SchemaTreeNode from './SchemaTreeNode.vue'

const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: '报文定义' },
  inputSchema: { type: String, default: '' },
  outputSchema: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => { if (!v) emit('update:modelValue', false) }
})

const activeTab = ref('input')

// 拖拽分割线
const editorContainer = ref(null)
const leftWidth = ref(450)
const rightWidth = ref(600)
let dragging = false
let dragStartX = 0
let dragStartLeft = 0

function startDrag(e) {
  dragging = true
  dragStartX = e.clientX
  dragStartLeft = leftWidth.value
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}
function onDrag(e) {
  if (!dragging) return
  const delta = e.clientX - dragStartX
  const containerWidth = editorContainer.value?.offsetWidth || 1060
  const newLeft = Math.max(200, Math.min(containerWidth - 250, dragStartLeft + delta))
  leftWidth.value = newLeft
  rightWidth.value = containerWidth - newLeft - 12
}
function stopDrag() {
  dragging = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}
onBeforeUnmount(stopDrag)

// JSON → 字段结构
const inputFields = computed(() => parseFields(props.inputSchema))
const outputFields = computed(() => parseFields(props.outputSchema))
const inputLineCount = computed(() => (formatJson(props.inputSchema) || '').split('\n').length)
const outputLineCount = computed(() => (formatJson(props.outputSchema) || '').split('\n').length)

function parseFields(schemaStr) {
  if (!schemaStr) return []
  try {
    const schema = JSON.parse(schemaStr)
    const props = schema.properties || {}
    const required = schema.required || []
    const fields = []
    for (const [name, def] of Object.entries(props)) {
      fields.push(parseFieldNode(name, def, required))
    }
    return fields
  } catch {
    return []
  }
}

function parseFieldNode(name, def, parentRequired) {
  const field = {
    name,
    title: def.title || def.description || '',
    type: def.type || 'string',
    required: Array.isArray(parentRequired) && parentRequired.includes(name),
    expanded: true,
    children: []
  }
  if (def.type === 'object' && def.properties) {
    const subRequired = def.required || []
    for (const [subName, subDef] of Object.entries(def.properties)) {
      field.children.push(parseFieldNode(subName, subDef, subRequired))
    }
  }
  if (def.type === 'array' && def.items && def.items.type === 'object' && def.items.properties) {
    const subRequired = def.items.required || []
    for (const [subName, subDef] of Object.entries(def.items.properties)) {
      field.children.push(parseFieldNode(subName, subDef, subRequired))
    }
  }
  return field
}

function formatJson(str) {
  if (!str) return ''
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

// 切换时重置 tab
watch(() => props.modelValue, (v) => {
  if (v) activeTab.value = 'input'
})
</script>

<style scoped>
.schema-editor-container {
  display: flex;
  gap: 0;
  min-height: 430px;
}
.schema-left {
  flex: none;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.schema-right {
  flex: none;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.schema-divider {
  width: 6px;
  cursor: col-resize;
  background: #e4e7ed;
  margin: 0 3px;
  border-radius: 3px;
  flex-shrink: 0;
  transition: background 0.2s;
}
.schema-divider:hover { background: #409eff; }
.schema-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 8px;
}
.schema-tree {
  flex: 1;
  overflow-y: auto;
}
.code-view {
  display: flex;
  max-height: 430px;
  overflow: auto;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
}
.code-lines {
  display: flex;
  flex-direction: column;
  padding: 8px 0;
  background: #f5f5f5;
  border-right: 1px solid #ebeef5;
  text-align: right;
  user-select: none;
  flex-shrink: 0;
}
.code-ln {
  display: block;
  padding: 0 8px;
  line-height: 1.5;
  font-size: 13px;
  color: #b0b0b0;
  font-family: Consolas, Monaco, monospace;
}
.code-text {
  margin: 0;
  padding: 8px 12px;
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre;
  flex: 1;
  min-width: 0;
}
.schema-empty {
  text-align: center;
  color: #909399;
  padding: 40px 0;
  font-size: 14px;
}
</style>
