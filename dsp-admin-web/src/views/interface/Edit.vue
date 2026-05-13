<template>
  <div>
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>{{ isEdit ? '编辑接口' : '新增接口' }}</span>
          <el-button @click="$router.back()">返回</el-button>
        </div>
      </template>

      <!-- 基础信息 -->
      <el-form :model="form" label-width="100px" class="mb-16">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="接口编码" required>
              <el-input v-model="form.transno" placeholder="如 GET_FUND_BALANCE" :disabled="isEdit" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="接口名称" required>
              <el-input v-model="form.name" placeholder="请输入接口名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所属系统">
              <el-select v-model="form.systemId" placeholder="请选择所属系统" clearable filterable style="width:100%"
                @change="onSystemChange">
                <el-option v-for="sys in systemOptions" :key="sys.id" :label="sys.name" :value="sys.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="接口描述">
              <el-input v-model="form.description" placeholder="可选" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 报文定义 -->
      <el-divider>报文定义</el-divider>
      <el-form label-width="100px">
        <el-form-item label="请求报文">
          <el-button type="primary" @click="openSchemaDialog('input')">
            {{ inputSchema ? '查看/编辑' : '点击定义' }}
          </el-button>
          <span v-if="inputSchema" style="margin-left:12px;color:#67c23a">已定义</span>
        </el-form-item>
        <el-form-item label="响应报文">
          <el-button type="primary" @click="openSchemaDialog('output')">
            {{ outputSchema ? '查看/编辑' : '点击定义' }}
          </el-button>
          <span v-if="outputSchema" style="margin-left:12px;color:#67c23a">已定义</span>
        </el-form-item>
        <el-form-item label="变更说明">
          <el-input v-model="changeLog" placeholder="本次修改说明" />
        </el-form-item>
      </el-form>

      <!-- 操作按钮 -->
      <div style="text-align:center;margin-top:16px">
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
        <el-button type="warning" @click="handleSubmitApproval" v-if="isEdit">提交审批</el-button>
      </div>
    </el-card>

    <!-- JSON Schema 编辑弹窗 -->
    <el-dialog v-model="schemaDialogVisible" :title="schemaDialogTitle" width="1100px" top="5vh" destroy-on-close>
      <div class="schema-editor-container" ref="editorContainer">
        <!-- 左侧：JSON 编辑 -->
        <div class="schema-left" :style="{ width: leftWidth + 'px' }">
          <div class="schema-panel-header">JSON 编辑</div>
          <el-input
            v-model="schemaJsonText"
            type="textarea"
            :rows="22"
            :disabled="!schemaEditable"
            placeholder="请输入 JSON Schema"
            style="font-family:monospace"
          />
        </div>
        <!-- 可拖拽分割线 -->
        <div class="schema-divider" @mousedown="startDrag" />
        <!-- 右侧：递归折叠树 -->
        <div class="schema-right" :style="{ width: rightWidth + 'px' }">
          <div class="schema-panel-header">
            <span>字段结构</span>
            <el-icon v-if="schemaEditable" class="schema-add-root" @click="addRootField"><Plus /></el-icon>
          </div>
          <div class="schema-tree">
            <SchemaTreeNode
              v-for="(_, idx) in schemaFields"
              :key="idx"
              :field="schemaFields[idx]"
              :editable="schemaEditable"
              @remove="removeRootField(idx)"
            />
            <div v-if="schemaFields.length === 0" class="schema-empty">暂无字段，点击上方图标添加</div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="schemaDialogVisible = false">关闭</el-button>
        <el-button type="success" @click="confirmSchema" :disabled="!schemaEditable">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: '接口编辑' })

import { ref, onMounted, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { interfaceApi, systemApi } from '../../api'
import { useAuthStore } from '../../stores/auth'
import { hasAnyRole } from '../../directives/role'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import SchemaTreeNode from '../../components/SchemaTreeNode.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isEdit = computed(() => !!route.params.id)

const form = ref({ transno: '', name: '', systemId: null, systemName: '', description: '' })
const inputSchema = ref('')
const outputSchema = ref('')
const changeLog = ref('')
const systemOptions = ref([])

// Schema 编辑弹窗
const schemaDialogVisible = ref(false)
const schemaType = ref('input')
const schemaJsonText = ref('')
const schemaFields = ref([])
const schemaEditable = ref(true)

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

// 自动同步：JSON → 字段结构
let jsonSyncTimer = null
watch(schemaJsonText, () => {
  if (!schemaDialogVisible.value || !schemaEditable.value) return
  clearTimeout(jsonSyncTimer)
  jsonSyncTimer = setTimeout(() => { parseFieldsFromJson() }, 400)
})

// 自动同步：字段结构 → JSON（深度监听）
let fieldSyncTimer = null
watch(schemaFields, () => {
  if (!schemaDialogVisible.value || !schemaEditable.value) return
  clearTimeout(fieldSyncTimer)
  fieldSyncTimer = setTimeout(() => {
    schemaJsonText.value = buildJsonFromFields()
  }, 400)
}, { deep: true })

const schemaDialogTitle = computed(() => {
  const label = schemaType.value === 'input' ? '请求报文' : '响应报文'
  const mode = schemaEditable.value ? '编辑' : '查看'
  return `${label} - ${mode}`
})

function openSchemaDialog(type) {
  schemaType.value = type
  const schemaStr = type === 'input' ? inputSchema.value : outputSchema.value
  schemaJsonText.value = schemaStr ? formatJson(schemaStr) : defaultSchema()
  parseFieldsFromJson()
  schemaDialogVisible.value = true
}

function defaultSchema() {
  return JSON.stringify({
    type: 'object',
    properties: {},
    required: []
  }, null, 2)
}

// JSON → 字段结构（递归）
function parseFieldsFromJson() {
  schemaFields.value = []
  try {
    const schema = JSON.parse(schemaJsonText.value)
    const props = schema.properties || {}
    const required = schema.required || []
    for (const [name, def] of Object.entries(props)) {
      schemaFields.value.push(parseFieldNode(name, def, required))
    }
  } catch {
    // JSON 解析失败，字段结构留空
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
  // object 类型：递归解析 properties
  if (def.type === 'object' && def.properties) {
    const subRequired = def.required || []
    for (const [subName, subDef] of Object.entries(def.properties)) {
      field.children.push(parseFieldNode(subName, subDef, subRequired))
    }
  }
  // array 类型：如果 items 是 object，递归解析 items.properties
  if (def.type === 'array' && def.items) {
    if (def.items.type === 'object' && def.items.properties) {
      const subRequired = def.items.required || []
      for (const [subName, subDef] of Object.entries(def.items.properties)) {
        field.children.push(parseFieldNode(subName, subDef, subRequired))
      }
    }
  }
  return field
}

// 字段结构 → JSON（递归）
function buildJsonFromFields() {
  const schema = { type: 'object', properties: {}, required: [] }
  for (const field of schemaFields.value) {
    schema.properties[field.name] = buildFieldNode(field)
    if (field.required) schema.required.push(field.name)
  }
  return JSON.stringify(schema, null, 2)
}

function buildFieldNode(field) {
  const prop = { type: field.type }
  if (field.title) prop.title = field.title

  if (field.type === 'object' && field.children && field.children.length > 0) {
    prop.properties = {}
    prop.required = []
    for (const child of field.children) {
      prop.properties[child.name] = buildFieldNode(child)
      if (child.required) prop.required.push(child.name)
    }
  }

  if (field.type === 'array' && field.children && field.children.length > 0) {
    prop.items = { type: 'object', properties: {}, required: [] }
    for (const child of field.children) {
      prop.items.properties[child.name] = buildFieldNode(child)
      if (child.required) prop.items.required.push(child.name)
    }
  }

  return prop
}

function addRootField() {
  schemaFields.value.push({ name: '', title: '', type: 'string', required: false, expanded: true, children: [] })
}

function removeRootField(idx) {
  schemaFields.value.splice(idx, 1)
}

function confirmSchema() {
  const finalJson = buildJsonFromFields()
  if (schemaType.value === 'input') {
    inputSchema.value = finalJson
  } else {
    outputSchema.value = finalJson
  }
  schemaDialogVisible.value = false
  ElMessage.success('报文定义已保存')
}

function formatJson(str) {
  if (!str) return ''
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

async function loadDetail() {
  if (!isEdit.value) return
  const res = await interfaceApi.detail(route.params.id)
  if (res.data) {
    form.value = res.data
    // 通过新接口获取当前用户可见的最新版本
    try {
      const verRes = await interfaceApi.getLatestVersion(res.data.transno)
      if (verRes.data) {
        inputSchema.value = verRes.data.inputSchema || ''
        outputSchema.value = verRes.data.outputSchema || ''
      }
    } catch {
      // 无版本信息
    }
  }
}

async function loadSystems() {
  const params = {}
  if (!hasAnyRole(['ADMIN', 'IMPORTER']) && authStore.deptId) {
    params.deptId = authStore.deptId
  }
  const res = await systemApi.list(params)
  systemOptions.value = res.data || []
}

function onSystemChange(val) {
  if (val) {
    const sys = systemOptions.value.find(s => s.id === val)
    form.value.systemName = sys ? sys.name : ''
  } else {
    form.value.systemName = ''
  }
}

onMounted(() => { loadSystems(); loadDetail() })

async function handleSave() {
  if (!form.value.transno || !form.value.name) {
    ElMessage.warning('请填写接口编码和名称')
    return
  }
  // 保存基础信息
  if (isEdit.value) {
    await interfaceApi.update(route.params.id, form.value)
  } else {
    await interfaceApi.create(form.value)
  }
  // 保存 Schema 版本
  if (form.value.transno) {
    await interfaceApi.saveSchema(form.value.transno, {
      inputSchema: inputSchema.value,
      outputSchema: outputSchema.value,
      changeLog: changeLog.value,
      operator: authStore.username
    })
  }
  ElMessage.success('保存成功')
  router.back()
}

async function handleSubmitApproval() {
  if (!form.value.transno) {
    ElMessage.warning('请先保存接口')
    return
  }
  try {
    // 先保存基础信息
    await interfaceApi.update(route.params.id, form.value)
    // 保存 Schema 版本（返回完整版本对象）
    const schemaRes = await interfaceApi.saveSchema(form.value.transno, {
      inputSchema: inputSchema.value,
      outputSchema: outputSchema.value,
      changeLog: changeLog.value || '提交审批',
      operator: authStore.username
    })
    // 用返回的版本号提交审批
    const versionNo = schemaRes.data?.versionNo
    if (versionNo) {
      await interfaceApi.submitApproval(form.value.transno, versionNo, { operator: authStore.username })
      ElMessage.success('已提交审批')
      router.back()
    }
  } catch {
    ElMessage.error('提交审批失败')
  }
}
</script>

<style scoped>
.mb-16 { margin-bottom: 16px; }

.schema-editor-container {
  display: flex;
  gap: 0;
  min-height: 500px;
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
  padding: 8px 0;
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
.schema-empty {
  text-align: center;
  color: #909399;
  padding: 40px 0;
  font-size: 14px;
}
.schema-add-root {
  cursor: pointer;
  font-size: 18px;
  color: #409eff;
  padding: 4px;
  border-radius: 4px;
}
.schema-add-root:hover { background: #ecf5ff; }
</style>
