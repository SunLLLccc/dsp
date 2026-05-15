<template>
  <div class="tree-node">
    <div class="tree-node-row">
      <!-- 展开/折叠 -->
      <span
        v-if="hasChildren"
        class="tree-toggle"
        @click="field.expanded = !field.expanded"
      >
        <el-icon :size="12"><ArrowRight v-if="!field.expanded" /><ArrowDown v-else /></el-icon>
      </span>
      <span v-else class="tree-toggle-placeholder" />

      <!-- 字段编辑 -->
      <el-input v-model="field.name" placeholder="字段名" size="small" :disabled="!editable" class="tree-input-name" />
      <el-input v-model="field.title" placeholder="中文名" size="small" :disabled="!editable" class="tree-input-title" />
      <el-select v-model="field.type" size="small" :disabled="!editable" class="tree-select-type" @change="onTypeChange">
        <el-option label="string" value="string" />
        <el-option label="integer" value="integer" />
        <el-option label="number" value="number" />
        <el-option label="boolean" value="boolean" />
        <el-option label="object" value="object" />
        <el-option label="array" value="array" />
      </el-select>
      <el-checkbox v-model="field.required" :disabled="!editable" size="small">必填</el-checkbox>

      <!-- 操作图标 -->
      <el-icon v-if="editable && canHaveChildren" class="tree-action" @click="addChild"><Plus /></el-icon>
      <el-icon v-if="editable" class="tree-action tree-action-danger" @click="$emit('remove')"><Delete /></el-icon>
    </div>

    <!-- 子节点（递归） -->
    <div v-if="hasChildren && field.expanded" class="tree-children">
      <SchemaTreeNode
        v-for="(_, idx) in field.children"
        :key="idx"
        :field="field.children[idx]"
        :editable="editable"
        @remove="removeChild(idx)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowRight, ArrowDown, Plus, Delete } from '@element-plus/icons-vue'

const props = defineProps({
  field: { type: Object, required: true },
  editable: { type: Boolean, default: true }
})

defineEmits(['remove'])

const canHaveChildren = computed(() =>
  props.field.type === 'object' || props.field.type === 'array'
)

const hasChildren = computed(() =>
  props.field.children && props.field.children.length > 0
)

function onTypeChange(type) {
  if ((type === 'object' || type === 'array') && !props.field.children) {
    props.field.children = []
  }
  if (type !== 'object' && type !== 'array') {
    props.field.children = []
  }
}

function addChild() {
  if (!props.field.children) props.field.children = []
  props.field.children.push({
    name: '',
    title: '',
    type: 'string',
    required: false,
    expanded: true,
    children: []
  })
  props.field.expanded = true
}

function removeChild(idx) {
  props.field.children.splice(idx, 1)
}
</script>

<style scoped>
.tree-node { margin-bottom: 2px; }
.tree-node-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 0;
}
.tree-toggle {
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  color: var(--text-secondary);
  border-radius: 2px;
}
.tree-toggle:hover { color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.tree-toggle-placeholder { width: 16px; flex-shrink: 0; }
.tree-input-name { width: 110px; }
.tree-input-title { width: 90px; }
.tree-select-type { width: 88px; }

.tree-action {
  cursor: pointer;
  font-size: 15px;
  color: var(--el-color-primary);
  flex-shrink: 0;
  border-radius: 4px;
  padding: 2px;
}
.tree-action:hover { background: var(--el-color-primary-light-9); }
.tree-action-danger { color: var(--el-color-danger); }
.tree-action-danger:hover { background: var(--el-color-danger-light-9); }

.tree-children {
  margin-left: 20px;
  border-left: 1.5px dashed var(--el-color-primary-light-7);
  padding-left: 6px;
}
</style>
