<template>
  <div>
    <!-- 搜索栏 -->
    <el-card class="mb-16">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="接口编码">
          <el-input v-model="searchForm.transno" placeholder="请输入接口编码" clearable />
        </el-form-item>
        <el-form-item label="接口名称">
          <el-input v-model="searchForm.name" placeholder="请输入接口名称" clearable />
        </el-form-item>
        <el-form-item label="所属系统">
          <el-select v-model="searchForm.systemName" placeholder="全部" clearable filterable style="width:160px">
            <el-option v-for="sys in systemOptions" :key="sys.id" :label="sys.name" :value="sys.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width:160px">
            <el-option label="草稿" :value="0" />
            <el-option label="待审批" :value="1" />
            <el-option label="已驳回" :value="2" />
            <el-option label="已发布" :value="3" />
            <el-option label="已下线" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <el-card>
      <div class="mb-16">
        <el-button type="primary" @click="handleCreate" v-role="'USER'">新增接口</el-button>
        <el-button type="success" @click="handleExportSelected" :disabled="!selectedRows.length">导出选中</el-button>
        <el-button type="warning" @click="openImportDialog" v-role="'IMPORTER'">导入配置</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" border stripe @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="transno" label="接口编码" width="200" />
        <el-table-column prop="name" label="接口名称" width="180" />
        <el-table-column prop="systemName" label="所属系统" width="150" />
        <el-table-column prop="currentVersion" label="当前版本" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedTime" label="更新时间" width="180" :formatter="fmtTimeCol" />
        <el-table-column label="操作" fixed="right" width="360">
          <template #default="{ row }">
            <el-button size="small" @click="handleViewSchema(row)">查看</el-button>
            <el-button size="small" @click="handleEdit(row)" :disabled="row.status === 1" v-role="'USER'">编辑</el-button>
            <el-button size="small" @click="showVersionHistory(row)">版本</el-button>
            <el-button size="small" type="warning" @click="handleWithdraw(row)" v-if="row.status === 1" v-role="'USER'">撤销审批</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)" :disabled="row.status === 1" v-role="'USER'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="mt-16" style="display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="searchForm.pageNum" v-model:page-size="searchForm.pageSize"
          :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 查看输入输出弹窗 -->
    <SchemaViewDialog
      v-model="viewSchemaVisible"
      :title="`报文定义 - ${viewSchemaTransno}`"
      :input-schema="viewInputSchema"
      :output-schema="viewOutputSchema"
    />

    <!-- 版本历史弹窗 -->
    <el-dialog v-model="versionDialogVisible" :title="`版本历史 - ${versionTransno}`" width="900px">
      <el-table :data="versionData" border stripe>
        <el-table-column prop="versionNo" label="版本号" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="verStatusType(row.status)">{{ verStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="changeLog" label="变更说明" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="创建人" width="100" />
        <el-table-column prop="createdTime" label="创建时间" width="170" :formatter="fmtTimeCol" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="viewSchema(row)">查看</el-button>
            <el-button size="small" type="primary" @click="compareWithCurrent(row)">对比当前</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="mt-16" style="display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="versionPage.pageNum" v-model:page-size="versionPage.pageSize"
          :total="versionTotal" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
          @size-change="loadVersions" @current-change="loadVersions" />
      </div>
    </el-dialog>

    <!-- Schema 查看弹窗（版本历史） -->
    <SchemaViewDialog
      v-model="schemaDialogVisible"
      :title="`Schema - V${schemaVersionNo}`"
      :input-schema="inputSchema"
      :output-schema="outputSchema"
    />

    <!-- Schema 对比弹窗 -->
    <SchemaCompareDialog
      v-model="compareVisible"
      :title="`Schema 对比 - V${compareVersionNo} vs 当前版本`"
      :left-label="`V${compareVersionNo}`"
      right-label="当前版本"
      :left-input="compareLeftInput"
      :left-output="compareLeftOutput"
      :right-input="compareRightInput"
      :right-output="compareRightOutput"
    />

    <!-- 导入配置弹窗 -->
    <el-dialog v-model="importDialogVisible" title="导入配置" width="600px" destroy-on-close>
      <el-alert type="info" :closable="false" class="mb-16">
        请选择从测试环境导出的 JSON 文件。导入时接口信息、Schema和模板XML会创建新版本。
      </el-alert>
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".json"
        :on-change="handleFileChange"
        :on-remove="() => importFileData = null"
        drag
      >
        <el-icon style="font-size:40px;color:#c0c4cc"><UploadFilled /></el-icon>
        <div>拖拽文件到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .json 文件</div>
        </template>
      </el-upload>
      <el-form class="mt-16" v-if="importPreview.length">
        <el-form-item label="变更说明">
          <el-input v-model="importChangeLog" placeholder="导入说明" />
        </el-form-item>
        <el-form-item label="待导入接口">
          <div v-for="item in importPreview" :key="item.transno" class="import-preview-item">
            <el-tag>{{ item.transno }}</el-tag>
            <span class="ml-8">{{ item.name }}</span>
            <el-tag v-if="item.exists" type="warning" size="small" class="ml-8">已存在(将创建新版本)</el-tag>
            <el-tag v-else type="success" size="small" class="ml-8">新建</el-tag>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleImport" :disabled="!importFileData" :loading="importing">
          确认导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { interfaceApi, configApi, systemApi } from '../../api'
import { INTERFACE_STATUS, INTERFACE_STATUS_TYPE, VERSION_STATUS, VERSION_STATUS_TYPE } from '../../constants/status'
import { useAuthStore } from '../../stores/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime, formatJson } from '../../utils/format'
import SchemaViewDialog from '../../components/SchemaViewDialog.vue'
import SchemaCompareDialog from '../../components/SchemaCompareDialog.vue'
import { UploadFilled } from '@element-plus/icons-vue'
import { hasAnyRole } from '../../directives/role'

const router = useRouter()
const authStore = useAuthStore()
const tableData = ref([])
const total = ref(0)
const searchForm = ref({ pageNum: 1, pageSize: 10, transno: '', name: '', systemName: '', status: null })
const systemOptions = ref([])

const statusText = (s) => INTERFACE_STATUS[s] || '未知'
const statusType = (s) => INTERFACE_STATUS_TYPE[s] || 'info'

const verStatusText = (s) => VERSION_STATUS[s] || '未知'
const verStatusType = (s) => VERSION_STATUS_TYPE[s] || 'info'

// 查看输入输出
const viewSchemaVisible = ref(false)
const viewSchemaTransno = ref('')
const viewInputSchema = ref('')
const viewOutputSchema = ref('')

const versionDialogVisible = ref(false)
const versionTransno = ref('')
const versionData = ref([])
const versionTotal = ref(0)
const versionPage = ref({ pageNum: 1, pageSize: 10 })

const schemaDialogVisible = ref(false)
const inputSchema = ref('')
const outputSchema = ref('')
const schemaVersionNo = ref('')

// 对比
const compareVisible = ref(false)
const compareVersionNo = ref('')
const compareLeftInput = ref('')
const compareLeftOutput = ref('')
const compareRightInput = ref('')
const compareRightOutput = ref('')

async function loadData() {
  const res = await interfaceApi.list(searchForm.value)
  tableData.value = res.data?.records || []
  total.value = res.data?.total || 0
}

function resetSearch() {
  searchForm.value = { pageNum: 1, pageSize: 10, transno: '', name: '', systemName: '', status: null }
  loadData()
}

function handleCreate() {
  router.push('/interface/edit')
}

function handleEdit(row) {
  router.push(`/interface/edit/${row.id}`)
}

async function handleViewSchema(row) {
  viewSchemaTransno.value = row.transno
  viewInputSchema.value = ''
  viewOutputSchema.value = ''
  try {
    const res = await interfaceApi.getLatestVersion(row.transno)
    if (res.data) {
      viewInputSchema.value = formatJson(res.data.inputSchema)
      viewOutputSchema.value = formatJson(res.data.outputSchema)
    }
  } catch { /* 无版本 */ }
  viewSchemaVisible.value = true
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除接口 ${row.transno}？删除将提交审批`, '删除确认', { type: 'warning' })
  try {
    const schemaRes = await interfaceApi.saveSchema(row.transno, {
      inputSchema: '',
      outputSchema: '',
      changeLog: '申请删除接口',
      operator: authStore.username
    })
    const versionNo = schemaRes.data?.versionNo
    if (versionNo) {
      await interfaceApi.submitApproval(row.transno, versionNo, { operator: authStore.username })
      ElMessage.success('删除申请已提交审批')
      loadData()
    }
  } catch {
    ElMessage.error('操作失败')
  }
}

async function handleWithdraw(row) {
  await ElMessageBox.confirm(`确认撤销接口 ${row.transno} 的审批？`, '撤销确认')
  try {
    await interfaceApi.withdraw(row.transno)
    ElMessage.success('已撤销审批')
    loadData()
  } catch {
    ElMessage.error('撤销失败')
  }
}

async function showVersionHistory(row) {
  versionTransno.value = row.transno
  versionPage.value = { pageNum: 1, pageSize: 10 }
  versionDialogVisible.value = true
  loadVersions()
}

async function loadVersions() {
  const res = await interfaceApi.versions(versionTransno.value, versionPage.value)
  versionData.value = res.data?.records || []
  versionTotal.value = res.data?.total || 0
}

async function viewSchema(row) {
  const res = await interfaceApi.getVersion(versionTransno.value, row.versionNo)
  inputSchema.value = formatJson(res.data?.inputSchema)
  outputSchema.value = formatJson(res.data?.outputSchema)
  schemaVersionNo.value = row.versionNo
  schemaDialogVisible.value = true
}

async function compareWithCurrent(row) {
  // 左侧：历史版本
  const histRes = await interfaceApi.getVersion(versionTransno.value, row.versionNo)
  compareLeftInput.value = histRes.data?.inputSchema || ''
  compareLeftOutput.value = histRes.data?.outputSchema || ''
  // 右侧：当前最新版本
  try {
    const curRes = await interfaceApi.getLatestVersion(versionTransno.value)
    compareRightInput.value = curRes.data?.inputSchema || ''
    compareRightOutput.value = curRes.data?.outputSchema || ''
  } catch {
    compareRightInput.value = ''
    compareRightOutput.value = ''
  }
  compareVersionNo.value = row.versionNo
  compareVisible.value = true
}

function fmtTimeCol(_row, _col, val) {
  return fmtTime(val)
}

// 选择行
const selectedRows = ref([])
function handleSelectionChange(rows) {
  selectedRows.value = rows
}

// 导出选中
async function handleExportSelected() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先选择要导出的接口')
    return
  }
  const transnos = selectedRows.value.map(r => r.transno)
  try {
    const res = await configApi.exportBatch(transnos)
    const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `dsp-config-export-${new Date().toISOString().slice(0, 10)}.json`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${transnos.length} 个接口配置`)
  } catch {
    ElMessage.error('导出失败')
  }
}

// 导入
const importDialogVisible = ref(false)
const importFileData = ref(null)
const importPreview = ref([])
const importChangeLog = ref('从测试环境导入')
const importing = ref(false)
const uploadRef = ref(null)

function openImportDialog() {
  importFileData.value = null
  importPreview.value = []
  importChangeLog.value = '从测试环境导入'
  importDialogVisible.value = true
}

function handleFileChange(file) {
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const data = JSON.parse(e.target.result)
      // 支持单文件多配置
      const configs = data.configs || [data]
      importFileData.value = configs
      importPreview.value = configs.map(c => ({
        transno: c.interfaceInfo?.transno || '',
        name: c.interfaceInfo?.name || '',
        exists: tableData.value.some(t => t.transno === c.interfaceInfo?.transno)
      }))
    } catch {
      ElMessage.error('JSON文件解析失败')
      importFileData.value = null
    }
  }
  reader.readAsText(file.raw)
}

async function handleImport() {
  if (!importFileData.value) return
  importing.value = true
  let successCount = 0
  let failCount = 0
  for (const config of importFileData.value) {
    try {
      config.changeLog = importChangeLog.value
      await configApi.importConfig(config)
      successCount++
    } catch {
      failCount++
    }
  }
  importing.value = false
  importDialogVisible.value = false
  ElMessage.success(`导入完成：成功 ${successCount} 个${failCount ? '，失败 ' + failCount + ' 个' : ''}`)
  loadData()
}

onMounted(() => {
  loadData()
  systemApi.list().then(res => { systemOptions.value = res.data || [] })
})
</script>

<style scoped>
.mb-16 { margin-bottom: 16px; }
.mt-16 { margin-top: 16px; }
.ml-8 { margin-left: 8px; }
.import-preview-item { margin-bottom: 4px; display: flex; align-items: center; }
</style>
