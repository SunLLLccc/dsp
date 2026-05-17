<template>
  <div>
    <el-tabs v-model="activeTab" type="border-card" @tab-change="handleTabChange">
      <!-- 页签一：接口信息（原有功能） -->
      <el-tab-pane label="接口信息" name="info">
        <!-- 搜索栏 -->
        <el-card shadow="never" class="card-search">
          <el-form :inline="true" :model="searchForm">
            <el-form-item label="接口编码">
              <el-input v-model="searchForm.transno" placeholder="请输入接口编码" clearable />
            </el-form-item>
            <el-form-item label="接口名称">
              <el-input v-model="searchForm.name" placeholder="请输入接口名称" clearable />
            </el-form-item>
            <el-form-item label="所属系统">
              <el-select v-model="searchForm.systemId" placeholder="全部" clearable filterable class="filter-select">
                <el-option v-for="sys in systemOptions" :key="sys.id" :label="sys.name" :value="sys.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="searchForm.status" placeholder="全部" clearable class="filter-select">
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
          <div class="mb-md">
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
                <el-button size="small" type="warning" @click="handleWithdraw(row)" v-if="row.status === 1 && row.canWithdraw" v-role="'USER'">撤销审批</el-button>
                <el-button size="small" type="danger" @click="handleDelete(row)" :disabled="row.status === 1" v-role="'USER'">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页 -->
          <div class="pagination-wrap">
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
        <el-dialog v-model="versionDialogVisible" :title="`版本历史 - ${versionTransno}`" width="90%" style="max-width:900px">
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
          <div class="pagination-wrap">
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
        <el-dialog v-model="importDialogVisible" title="导入配置" width="90%" style="max-width:600px" destroy-on-close>
          <el-alert type="info" :closable="false" class="mb-md">
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
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div>拖拽文件到此处，或<em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">仅支持 .json 文件</div>
            </template>
          </el-upload>
          <el-form class="mt-md" v-if="importPreview.length">
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
      </el-tab-pane>

      <!-- 页签二：接口申请 -->
      <el-tab-pane label="接口申请" name="application">
        <!-- 搜索栏 -->
        <el-card shadow="never" class="card-search">
          <el-form :inline="true" :model="applySearchForm">
            <el-form-item label="申请状态">
              <el-select v-model="applySearchForm.status" placeholder="全部" clearable class="filter-select">
                <el-option label="待审批" :value="0" />
                <el-option label="已通过" :value="1" />
                <el-option label="已驳回" :value="2" />
              </el-select>
            </el-form-item>
            <el-form-item label="日期范围">
              <el-date-picker v-model="applySearchForm.dateRange" type="daterange" range-separator="至"
                start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadApplyList">查询</el-button>
              <el-button @click="resetApplySearch">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card>
          <div class="mb-md">
            <el-button type="primary" @click="openApplyDialog">新建申请</el-button>
          </div>
          <el-table :data="applyList" border stripe>
            <el-table-column prop="requirementNo" label="需求编号" width="150" />
            <el-table-column prop="transno" label="接口编码" width="180" />
            <el-table-column prop="providerSystemName" label="服务方系统" width="150" />
            <el-table-column prop="applicantSystemName" label="申请方系统" width="150" />
            <el-table-column prop="createdTime" label="申请时间" width="170" :formatter="fmtTimeCol" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="applyAppStatusType(row.status)">{{ applyAppStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="warning" @click="handleApplyWithdraw(row)" v-if="row.status === 0">撤回</el-button>
                <el-button size="small" type="primary" @click="viewApplyDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination v-model:current-page="applySearchForm.pageNum" v-model:page-size="applySearchForm.pageSize"
              :total="applyTotal" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
              @size-change="loadApplyList" @current-change="loadApplyList" />
          </div>
        </el-card>

        <!-- 新建申请弹窗 -->
        <el-dialog v-model="applyDialogVisible" title="新建接口申请" width="650px" destroy-on-close>
          <el-form :model="applyForm" label-width="110px">
            <el-form-item label="请求方系统" required>
              <el-select v-model="applyForm.reqSystemId" placeholder="请选择请求方系统" class="full-width">
                <el-option v-for="s in reqSystemList" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="服务方系统" required>
              <el-select v-model="applyForm.providerSystemId" placeholder="请选择服务方系统" class="full-width" @change="handleProviderSystemChange">
                <el-option v-for="s in allSystemList" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="选择接口" required>
              <el-select v-model="applyForm.interfaceId" placeholder="请先选择服务方系统" class="full-width" :disabled="!applyForm.providerSystemId" filterable @change="onApplyInterfaceChange">
                <el-option v-for="inf in applyInterfaceOptions" :key="inf.id" :label="`${inf.transno} - ${inf.name}`" :value="inf.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="需求编号" required>
              <el-input v-model="applyForm.reqNo" placeholder="请输入需求编号" />
            </el-form-item>
            <el-form-item label="需求描述" required>
              <el-input v-model="applyForm.reqDesc" type="textarea" :rows="3" placeholder="请输入需求描述" />
            </el-form-item>
            <el-form-item label="申请原因" required>
              <el-input v-model="applyForm.applyReason" type="textarea" :rows="3" placeholder="请输入申请原因" />
            </el-form-item>
            <el-form-item label="下游接口">
              <el-input v-model="applyForm.downstreamInfo" type="textarea" :rows="2" placeholder="请描述下游接口或页面信息" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="applyDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleSubmitApply" :loading="submitting">提交申请</el-button>
          </template>
        </el-dialog>

        <!-- 申请详情弹窗 -->
        <el-dialog v-model="applyDetailVisible" title="申请详情" width="600px">
          <el-descriptions :column="2" border v-if="currentApplyDetail">
            <el-descriptions-item label="需求编号">{{ currentApplyDetail.requirementNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="接口编码">{{ currentApplyDetail.transno || '-' }}</el-descriptions-item>
            <el-descriptions-item label="请求方系统">{{ currentApplyDetail.applicantSystemName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="服务方系统">{{ currentApplyDetail.providerSystemName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="applyAppStatusType(currentApplyDetail.status)">{{ applyAppStatusText(currentApplyDetail.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="申请时间">{{ fmtTime(currentApplyDetail.createdTime) }}</el-descriptions-item>
            <el-descriptions-item label="需求描述" :span="2">{{ currentApplyDetail.requirementDesc || '-' }}</el-descriptions-item>
            <el-descriptions-item label="申请原因" :span="2">{{ currentApplyDetail.applyReason || '-' }}</el-descriptions-item>
            <el-descriptions-item label="下游接口" :span="2">{{ currentApplyDetail.downstreamInfo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="驳回原因" :span="2" v-if="currentApplyDetail.rejectReason">{{ currentApplyDetail.rejectReason }}</el-descriptions-item>
          </el-descriptions>
          <template #footer>
            <el-button @click="applyDetailVisible = false">关闭</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- 页签三：请求关系 -->
      <el-tab-pane label="请求关系" name="relation">
        <el-card>
          <el-tabs v-model="relationSubTab" @tab-change="handleRelationTabChange">
            <!-- 作为服务方 -->
            <el-tab-pane label="作为服务方" name="provider">
              <el-table :data="providerList" border stripe>
                <el-table-column prop="transno" label="接口编码" width="200" />
                <el-table-column prop="interfaceName" label="接口名称" width="180" />
                <el-table-column prop="applicantSystemName" label="请求方系统" width="150" />
                <el-table-column prop="requirementNo" label="需求编号" width="150" />
                <el-table-column prop="createdTime" label="申请时间" width="170" :formatter="fmtTimeCol" />
                <el-table-column prop="applyReason" label="申请事由" show-overflow-tooltip />
                <el-table-column prop="status" label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="relationStatusType(row.status)">{{ relationStatusText(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120" fixed="right">
                  <template #default="{ row }">
                    <el-button size="small" type="danger" @click="handleOfflineRelation(row)" v-if="row.status === 1">下线</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="pagination-wrap">
                <el-pagination v-model:current-page="providerPage.pageNum" v-model:page-size="providerPage.pageSize"
                  :total="providerTotal" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
                  @size-change="loadProviderList" @current-change="loadProviderList" />
              </div>
            </el-tab-pane>

            <!-- 作为请求方 -->
            <el-tab-pane label="作为请求方" name="applicant">
              <el-table :data="applicantList" border stripe>
                <el-table-column prop="transno" label="接口编码" width="200" />
                <el-table-column prop="interfaceName" label="接口名称" width="180" />
                <el-table-column prop="providerSystemName" label="服务方系统" width="150" />
                <el-table-column prop="requirementNo" label="需求编号" width="150" />
                <el-table-column prop="createdTime" label="申请时间" width="170" :formatter="fmtTimeCol" />
                <el-table-column prop="applyReason" label="申请事由" show-overflow-tooltip />
                <el-table-column prop="status" label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="relationStatusType(row.status)">{{ relationStatusText(row.status) }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
              <div class="pagination-wrap">
                <el-pagination v-model:current-page="applicantPage.pageNum" v-model:page-size="applicantPage.pageSize"
                  :total="applicantTotal" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
                  @size-change="loadApplicantList" @current-change="loadApplicantList" />
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
defineOptions({ name: '接口管理' })

import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { interfaceApi, configApi, systemApi, approvalApi, relationApi } from '../../api'
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

// ==================== 页签控制 ====================
const activeTab = ref('info')

function handleTabChange(tab) {
  if (tab === 'application') loadApplyList()
  else if (tab === 'relation') {
    if (relationSubTab.value === 'provider') loadProviderList()
    else loadApplicantList()
  }
}

// ==================== 页签一：接口信息（原有逻辑完全保留） ====================
const tableData = ref([])
const total = ref(0)
const searchForm = ref({ pageNum: 1, pageSize: 10, transno: '', name: '', systemId: null, status: null })
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
  searchForm.value = { pageNum: 1, pageSize: 10, transno: '', name: '', systemId: null, status: null }
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

// ==================== 页签二：接口申请 ====================
const applySearchForm = ref({ pageNum: 1, pageSize: 10, status: null, dateRange: null })
const applyList = ref([])
const applyTotal = ref(0)

const APPLY_APP_STATUS = { 0: '待审批', 1: '已通过', 2: '已驳回', 3: '已撤回' }
const APPLY_APP_STATUS_TYPE = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' }
const applyAppStatusText = (s) => APPLY_APP_STATUS[s] || '未知'
const applyAppStatusType = (s) => APPLY_APP_STATUS_TYPE[s] || 'info'

async function loadApplyList() {
  try {
    const params = {
      type: 3,
      pageNum: applySearchForm.value.pageNum,
      pageSize: applySearchForm.value.pageSize,
    }
    if (applySearchForm.value.status !== null && applySearchForm.value.status !== '') {
      params.status = applySearchForm.value.status
    }
    if (applySearchForm.value.dateRange && applySearchForm.value.dateRange.length === 2) {
      params.startDate = applySearchForm.value.dateRange[0]
      params.endDate = applySearchForm.value.dateRange[1]
    }
    const res = await approvalApi.mySubmissions(params)
    applyList.value = res.data?.records || []
    applyTotal.value = res.data?.total || 0
  } catch {
    applyList.value = []
    applyTotal.value = 0
  }
}

function resetApplySearch() {
  applySearchForm.value = { pageNum: 1, pageSize: 10, status: null, dateRange: null }
  loadApplyList()
}

// 新建申请
const applyDialogVisible = ref(false)
const submitting = ref(false)
const reqSystemList = ref([])
const allSystemList = ref([])
const applyInterfaceOptions = ref([])
const applyForm = ref({
  reqSystemId: null,
  providerSystemId: null,
  interfaceId: null,
  transno: '',
  reqNo: '',
  reqDesc: '',
  applyReason: '',
  downstreamInfo: ''
})

async function openApplyDialog() {
  applyForm.value = {
    reqSystemId: null,
    providerSystemId: null,
    interfaceId: null,
    transno: '',
    reqNo: '',
    reqDesc: '',
    applyReason: '',
    downstreamInfo: ''
  }
  applyInterfaceOptions.value = []
  applyDialogVisible.value = true

  // 加载请求方系统（当前用户部门下的系统）
  try {
    const res = await systemApi.list({ deptId: authStore.deptId })
    reqSystemList.value = res.data?.records || res.data || []
  } catch {
    reqSystemList.value = []
  }

  // 加载全部系统
  try {
    const res = await systemApi.list()
    allSystemList.value = res.data?.records || res.data || []
  } catch {
    allSystemList.value = []
  }
}

async function handleProviderSystemChange(systemId) {
  applyForm.value.interfaceId = null
  applyForm.value.transno = ''
  applyInterfaceOptions.value = []
  if (!systemId) return
  try {
    const res = await interfaceApi.list({ systemId, status: 3, pageNum: 1, pageSize: 200 })
    applyInterfaceOptions.value = res.data?.records || []
  } catch {
    applyInterfaceOptions.value = []
  }
}

function onApplyInterfaceChange(val) {
  const inf = applyInterfaceOptions.value.find(i => i.id === val)
  applyForm.value.transno = inf ? inf.transno : ''
}

async function handleSubmitApply() {
  const form = applyForm.value
  if (!form.reqSystemId) { ElMessage.warning('请选择请求方系统'); return }
  if (!form.providerSystemId) { ElMessage.warning('请选择服务方系统'); return }
  if (!form.interfaceId) { ElMessage.warning('请选择接口'); return }
  if (!form.reqNo) { ElMessage.warning('请输入需求编号'); return }
  if (!form.reqDesc) { ElMessage.warning('请输入需求描述'); return }
  if (!form.applyReason) { ElMessage.warning('请输入申请原因'); return }

  submitting.value = true
  try {
    await approvalApi.submitApply({
      type: 3,
      applicantSystemId: form.reqSystemId,
      providerSystemId: form.providerSystemId,
      transno: form.transno,
      requirementNo: form.reqNo,
      requirementDesc: form.reqDesc,
      applyReason: form.applyReason,
      downstreamInfo: form.downstreamInfo
    })
    ElMessage.success('申请已提交')
    applyDialogVisible.value = false
    loadApplyList()
  } catch {
    ElMessage.error('提交失败')
  } finally {
    submitting.value = false
  }
}

// 撤回
async function handleApplyWithdraw(row) {
  await ElMessageBox.confirm('确认撤回该申请？', '撤回确认')
  try {
    await approvalApi.withdraw(row.id)
    ElMessage.success('已撤回')
    loadApplyList()
  } catch {
    ElMessage.error('撤回失败')
  }
}

// 申请详情
const applyDetailVisible = ref(false)
const currentApplyDetail = ref(null)

function viewApplyDetail(row) {
  currentApplyDetail.value = row
  applyDetailVisible.value = true
}

// ==================== 页签三：请求关系 ====================
const relationSubTab = ref('provider')

const RELATION_STATUS = { 0: '待审批', 1: '已通过', 2: '已驳回', 3: '已下线' }
const RELATION_STATUS_TYPE = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' }
const relationStatusText = (s) => RELATION_STATUS[s] || '未知'
const relationStatusType = (s) => RELATION_STATUS_TYPE[s] || 'info'

// 服务方
const providerList = ref([])
const providerTotal = ref(0)
const providerPage = ref({ pageNum: 1, pageSize: 10 })

// 请求方
const applicantList = ref([])
const applicantTotal = ref(0)
const applicantPage = ref({ pageNum: 1, pageSize: 10 })

function handleRelationTabChange(tab) {
  if (tab === 'provider') loadProviderList()
  else loadApplicantList()
}

async function loadProviderList() {
  try {
    const res = await relationApi.provider(providerPage.value)
    providerList.value = res.data?.records || []
    providerTotal.value = res.data?.total || 0
  } catch {
    providerList.value = []
    providerTotal.value = 0
  }
}

async function loadApplicantList() {
  try {
    const res = await relationApi.applicant(applicantPage.value)
    applicantList.value = res.data?.records || []
    applicantTotal.value = res.data?.total || 0
  } catch {
    applicantList.value = []
    applicantTotal.value = 0
  }
}

async function handleOfflineRelation(row) {
  await ElMessageBox.confirm(`确认下线与 ${row.applicantSystemName} 的接口关系？`, '下线确认', { type: 'warning' })
  try {
    await relationApi.offline(row.id, { operator: authStore.username })
    ElMessage.success('已下线')
    loadProviderList()
  } catch {
    ElMessage.error('下线失败')
  }
}

// ==================== 初始化 ====================
onMounted(() => {
  loadData()
  // ADMIN用户加载全部系统，其他用户只加载自己部门的系统
  const params = authStore.hasRole('ADMIN') ? {} : { deptId: authStore.deptId }
  systemApi.list(params).then(res => { systemOptions.value = res.data || [] })
})
</script>

<style scoped>
.filter-select { width: 160px; }
.upload-icon { font-size: 40px; color: var(--text-placeholder); }
.ml-8 { margin-left: 8px; }
.import-preview-item { margin-bottom: 4px; display: flex; align-items: center; }
.full-width { width: 100%; }
</style>
