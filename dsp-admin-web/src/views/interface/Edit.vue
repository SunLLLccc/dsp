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
              <el-input v-model="form.systemName" placeholder="请输入所属系统名称" />
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

      <!-- 数据源关联（编辑模式） -->
      <template v-if="isEdit && form.transno">
        <el-divider>数据源关联</el-divider>
        <div style="margin-bottom:12px">
          <el-select v-model="selectedDs" placeholder="选择数据源" style="width:300px;margin-right:10px">
            <el-option v-for="ds in availableDatasources" :key="ds.dsName" :label="`${ds.dsName} (${ds.dsType})`" :value="ds.dsName" />
          </el-select>
          <el-button type="primary" @click="handleAddDs" :disabled="!selectedDs">关联</el-button>
        </div>
        <el-table :data="boundDatasources" border stripe size="small" style="max-width:600px">
          <el-table-column prop="dsName" label="数据源名称" />
          <el-table-column prop="dsType" label="类型" width="100" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button size="small" type="danger" text @click="handleRemoveDs(row.dsName)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 操作按钮 -->
      <div style="text-align:center;margin-top:16px">
        <el-button type="primary" @click="handleSave">保存</el-button>
      </div>
    </el-card>

    <!-- JSON Schema 编辑弹窗 -->
    <el-dialog v-model="schemaDialogVisible" :title="schemaDialogTitle" width="1100px" top="5vh" destroy-on-close>
      <div class="schema-editor-container">
        <!-- 左侧：JSON 编辑 -->
        <div class="schema-left">
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
        <!-- 分割线 -->
        <div class="schema-divider" />
        <!-- 右侧：递归折叠树 -->
        <div class="schema-right">
          <div class="schema-panel-header">
            <span>字段结构</span>
            <el-button v-if="schemaEditable" size="small" type="primary" @click="addRootField">添加根字段</el-button>
          </div>
          <div class="schema-tree">
            <SchemaTreeNode
              v-for="(_, idx) in schemaFields"
              :key="idx"
              :field="schemaFields[idx]"
              :editable="schemaEditable"
              @remove="removeRootField(idx)"
            />
            <div v-if="schemaFields.length === 0" class="schema-empty">暂无字段，点击上方按钮添加</div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="schemaDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="syncJsonToFields" :disabled="!schemaEditable">JSON → 字段结构</el-button>
        <el-button type="primary" @click="syncFieldsToJson" :disabled="!schemaEditable">字段结构 → JSON</el-button>
        <el-button type="success" @click="confirmSchema" :disabled="!schemaEditable">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { interfaceApi, datasourceApi, interfaceDatasourceApi } from '../../api'
import { ElMessage } from 'element-plus'
import SchemaTreeNode from '../../components/SchemaTreeNode.vue'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)

const form = ref({ transno: '', name: '', systemName: '', description: '' })
const inputSchema = ref('')
const outputSchema = ref('')
const changeLog = ref('')
const selectedDs = ref('')
const availableDatasources = ref([])
const boundDatasources = ref([])

// Schema 编辑弹窗
const schemaDialogVisible = ref(false)
const schemaType = ref('input') // input 或 output
const schemaJsonText = ref('')
const schemaFields = ref([])
const schemaEditable = ref(true)

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

function syncJsonToFields() {
  parseFieldsFromJson()
  ElMessage.success('已从 JSON 同步到字段结构')
}

function syncFieldsToJson() {
  schemaJsonText.value = buildJsonFromFields()
  ElMessage.success('已从字段结构同步到 JSON')
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
    // 加载最新版本的 Schema
    try {
      const verRes = await interfaceApi.getVersion(res.data.transno, res.data.currentVersion)
      inputSchema.value = verRes.data?.inputSchema || ''
      outputSchema.value = verRes.data?.outputSchema || ''
    } catch {
      // 无版本信息
    }
    loadDatasources(res.data.transno)
  }
}

async function loadDatasources(transno) {
  try {
    const [allRes, boundRes] = await Promise.all([
      datasourceApi.list({ pageNum: 1, pageSize: 100 }),
      interfaceDatasourceApi.list(transno)
    ])
    availableDatasources.value = allRes.data?.records || []
    boundDatasources.value = boundRes.data || []
  } catch {
    // ignore
  }
}

async function handleAddDs() {
  if (!selectedDs.value) return
  try {
    await interfaceDatasourceApi.add(form.value.transno, selectedDs.value)
    ElMessage.success('关联成功')
    selectedDs.value = ''
    loadDatasources(form.value.transno)
  } catch {
    ElMessage.error('关联失败')
  }
}

async function handleRemoveDs(dsName) {
  try {
    await interfaceDatasourceApi.remove(form.value.transno, dsName)
    ElMessage.success('已移除')
    loadDatasources(form.value.transno)
  } catch {
    ElMessage.error('移除失败')
  }
}

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
      operator: 'admin'
    })
  }
  ElMessage.success('保存成功')
  router.back()
}

onMounted(() => loadDetail())
</script>

<style scoped>
.mb-16 { margin-bottom: 16px; }

.schema-editor-container {
  display: flex;
  gap: 0;
  min-height: 500px;
}
.schema-left {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.schema-right {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.schema-divider {
  width: 1px;
  background: #dcdfe6;
  margin: 0 12px;
}
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
</style>
