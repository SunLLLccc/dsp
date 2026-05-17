<template>
  <div>
    <el-tabs v-model="activeTab" type="border-card" @tab-change="handleTabChange">
      <!-- 页签一：我的提交 -->
      <el-tab-pane label="我的提交" name="mySubmissions">
        <!-- 筛选栏 -->
        <el-card shadow="never" class="card-search">
          <el-form :inline="true" :model="submitSearchForm">
            <el-form-item label="审批类型">
              <el-select v-model="submitSearchForm.type" placeholder="全部" clearable class="filter-select">
                <el-option label="新增接口" :value="1" />
                <el-option label="修改接口" :value="2" />
                <el-option label="申请接口" :value="3" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="submitSearchForm.status" placeholder="全部" clearable class="filter-select">
                <el-option v-for="(label, val) in APPROVAL_STATUS_MAP" :key="val" :label="label" :value="Number(val)" />
              </el-select>
            </el-form-item>
            <el-form-item label="日期范围">
              <el-date-picker v-model="submitSearchForm.dateRange" type="daterange" range-separator="至"
                start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadSubmissions">查询</el-button>
              <el-button @click="resetSubmitSearch">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card>
          <el-table :data="submissionList" border stripe>
            <el-table-column prop="approvalNo" label="审批单号" width="180" />
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="type" label="审批类型" width="120">
              <template #default="{ row }">
                <el-tag :type="APPROVAL_TYPE_TAG[row.type] || ''">{{ APPROVAL_TYPE_MAP[row.type] || '未知' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="申请时间" width="170" :formatter="fmtTimeCol" />
            <el-table-column prop="currentStep" label="当前步骤" width="100">
              <template #default="{ row }">
                {{ row.currentStep || '-' }}/{{ row.totalStep || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="APPROVAL_STATUS_TAG[row.status] || 'info'">{{ APPROVAL_STATUS_MAP[row.status] || '未知' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="warning" @click="handleWithdraw(row)" v-if="row.status === 0 && (!row.currentStep || row.currentStep === 0)">撤回</el-button>
                <el-button size="small" @click="showFlowDetail(row)">审批记录</el-button>
                <el-button size="small" type="primary" @click="showApprovalDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination v-model:current-page="submitSearchForm.pageNum" v-model:page-size="submitSearchForm.pageSize"
              :total="submissionTotal" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
              @size-change="loadSubmissions" @current-change="loadSubmissions" />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 页签二：待我审批 -->
      <el-tab-pane label="待我审批" name="pending">
        <el-card>
          <el-table :data="pendingList" border stripe>
            <el-table-column prop="approvalNo" label="审批单号" width="180" />
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="type" label="审批类型" width="120">
              <template #default="{ row }">
                <el-tag :type="APPROVAL_TYPE_TAG[row.type] || ''">{{ APPROVAL_TYPE_MAP[row.type] || '未知' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="applicant" label="申请人" width="120" />
            <el-table-column prop="createdTime" label="申请时间" width="170" :formatter="fmtTimeCol" />
            <el-table-column prop="currentStep" label="当前步骤" width="100">
              <template #default="{ row }">
                {{ row.currentStep || '-' }}/{{ row.totalStep || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="success" @click="handleApprove(row)" v-role="'DEPT_MANAGER'">通过</el-button>
                <el-button size="small" type="danger" @click="showRejectDialog(row)" v-role="'DEPT_MANAGER'">驳回</el-button>
                <el-button size="small" type="primary" @click="showApprovalDetail(row)">详情</el-button>
                <el-button size="small" @click="showFlowDetail(row)">审批记录</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination v-model:current-page="pendingPage.pageNum" v-model:page-size="pendingPage.pageSize"
              :total="pendingTotal" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
              @size-change="loadPendingList" @current-change="loadPendingList" />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 页签三：已审批 -->
      <el-tab-pane label="已审批" name="history">
        <!-- 筛选栏 -->
        <el-card shadow="never" class="card-search">
          <el-form :inline="true" :model="historySearchForm">
            <el-form-item label="审批类型">
              <el-select v-model="historySearchForm.type" placeholder="全部" clearable class="filter-select">
                <el-option label="新增接口" :value="1" />
                <el-option label="修改接口" :value="2" />
                <el-option label="申请接口" :value="3" />
              </el-select>
            </el-form-item>
            <el-form-item label="日期范围">
              <el-date-picker v-model="historySearchForm.dateRange" type="daterange" range-separator="至"
                start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadHistoryList">查询</el-button>
              <el-button @click="resetHistorySearch">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card>
          <el-table :data="historyList" border stripe>
            <el-table-column prop="approvalNo" label="审批单号" width="180" />
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="type" label="审批类型" width="120">
              <template #default="{ row }">
                <el-tag :type="APPROVAL_TYPE_TAG[row.type] || ''">{{ APPROVAL_TYPE_MAP[row.type] || '未知' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="applicant" label="申请人" width="120" />
            <el-table-column prop="createdTime" label="申请时间" width="170" :formatter="fmtTimeCol" />
            <el-table-column prop="approver" label="审批人" width="120" />
            <el-table-column prop="approveTime" label="审批时间" width="170" :formatter="fmtTimeCol" />
            <el-table-column prop="status" label="结果" width="100">
              <template #default="{ row }">
                <el-tag :type="APPROVAL_STATUS_TAG[row.status] || 'info'">{{ APPROVAL_STATUS_MAP[row.status] || '未知' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="showFlowDetail(row)">审批记录</el-button>
                <el-button size="small" type="primary" @click="showApprovalDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination v-model:current-page="historySearchForm.pageNum" v-model:page-size="historySearchForm.pageSize"
              :total="historyTotal" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
              @size-change="loadHistoryList" @current-change="loadHistoryList" />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 驳回原因弹窗 -->
    <el-dialog v-model="rejectDialogVisible" title="驳回审批" width="450px">
      <el-form :model="rejectForm" label-width="80px">
        <el-form-item label="驳回原因" required>
          <el-input v-model="rejectForm.reason" type="textarea" :rows="3" placeholder="请输入驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="handleReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="审批详情" width="700px">
      <template v-if="currentDetail">
        <!-- type=1/2：新增/修改接口 -->
        <template v-if="currentDetail.type === 1 || currentDetail.type === 2">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="审批类型">
              <el-tag :type="APPROVAL_TYPE_TAG[currentDetail.type]">{{ APPROVAL_TYPE_MAP[currentDetail.type] }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="APPROVAL_STATUS_TAG[currentDetail.status]">{{ APPROVAL_STATUS_MAP[currentDetail.status] }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="接口编码">{{ currentDetail.transno || '-' }}</el-descriptions-item>
            <el-descriptions-item label="版本号">{{ currentDetail.versionNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="变更说明" :span="2">{{ currentDetail.changeLog || '-' }}</el-descriptions-item>
          </el-descriptions>
          <!-- Schema 对比（如有） -->
          <div v-if="currentDetail.inputSchema || currentDetail.outputSchema" class="mt-md">
            <h4 class="mb-sm">Schema 定义</h4>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="输入 Schema">
                <pre class="schema-pre">{{ currentDetail.inputSchema || '-' }}</pre>
              </el-descriptions-item>
              <el-descriptions-item label="输出 Schema">
                <pre class="schema-pre">{{ currentDetail.outputSchema || '-' }}</pre>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </template>

        <!-- type=3：申请接口 -->
        <template v-else-if="currentDetail.type === 3">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="需求编号">{{ currentDetail.requirementNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="APPROVAL_STATUS_TAG[currentDetail.status]">{{ APPROVAL_STATUS_MAP[currentDetail.status] }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="申请方系统">{{ currentDetail.applicantSystemName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="服务方系统">{{ currentDetail.providerSystemName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="接口编码">{{ currentDetail.transno || '-' }}</el-descriptions-item>
            <el-descriptions-item label="需求描述">{{ currentDetail.requirementDesc || '-' }}</el-descriptions-item>
            <el-descriptions-item label="申请原因" :span="2">{{ currentDetail.applyReason || '-' }}</el-descriptions-item>
            <el-descriptions-item label="下游接口" :span="2">{{ currentDetail.downstreamInfo || '-' }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </template>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 审批记录弹窗 -->
    <el-dialog v-model="flowDialogVisible" title="审批记录" width="600px">
      <el-timeline v-if="flowSteps.length">
        <el-timeline-item
          v-for="(step, idx) in flowSteps"
          :key="idx"
          :timestamp="step.approveTime || '-'"
          placement="top"
          :type="step.status === 1 ? 'success' : step.status === 2 ? 'danger' : 'warning'"
        >
          <el-card shadow="never" class="flow-step-card">
            <div class="flow-step-header">
              <span class="flow-step-name">{{ step.stepName || `步骤 ${idx + 1}` }}</span>
              <el-tag v-if="step.status === 1" type="success" size="small">通过</el-tag>
              <el-tag v-else-if="step.status === 2" type="danger" size="small">驳回</el-tag>
              <el-tag v-else type="warning" size="small">待审批</el-tag>
            </div>
            <div class="flow-step-info">
              <span>审批人：{{ step.approver || '-' }}</span>
            </div>
            <div v-if="step.rejectReason" class="flow-step-reason">
              驳回原因：{{ step.rejectReason }}
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无审批记录" />
      <template #footer>
        <el-button @click="flowDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: '审批管理' })

import { ref, onMounted } from 'vue'
import { approvalApi, interfaceApi } from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime, formatJson } from '../../utils/format'
import SchemaCompareDialog from '../../components/SchemaCompareDialog.vue'
import { hasAnyRole } from '../../directives/role'

// ==================== 审批类型/状态映射 ====================
const APPROVAL_TYPE_MAP = { 1: '新增接口', 2: '修改接口', 3: '申请接口' }
const APPROVAL_TYPE_TAG = { 1: '', 2: 'warning', 3: 'success' }
const APPROVAL_STATUS_MAP = { 0: '待审批', 1: '已通过', 2: '已驳回', 3: '已撤回' }
const APPROVAL_STATUS_TAG = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' }

function fmtTimeCol(_row, _col, val) { return fmtTime(val) }

// 格式化日期为本地时间字符串
function formatLocalDateTime(date) {
  const y = date.getFullYear()
  const M = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const m = String(date.getMinutes()).padStart(2, '0')
  const s = String(date.getSeconds()).padStart(2, '0')
  return `${y}-${M}-${d} ${h}:${m}:${s}`
}

// 默认日期范围：近7天（返回本地时间字符串）
function defaultDateRange() {
  const end = new Date()
  const start = new Date(Date.now() - 7 * 24 * 3600 * 1000)
  return [formatLocalDateTime(start), formatLocalDateTime(end)]
}

// ==================== 页签控制 ====================
const activeTab = ref('mySubmissions')

function handleTabChange(tab) {
  if (tab === 'mySubmissions') loadSubmissions()
  else if (tab === 'pending') loadPendingList()
  else if (tab === 'history') loadHistoryList()
}

// ==================== 页签一：我的提交 ====================
const submitSearchForm = ref({
  pageNum: 1,
  pageSize: 10,
  type: null,
  status: null,
  dateRange: defaultDateRange()
})
const submissionList = ref([])
const submissionTotal = ref(0)

async function loadSubmissions() {
  try {
    const params = {
      pageNum: submitSearchForm.value.pageNum,
      pageSize: submitSearchForm.value.pageSize,
    }
    if (submitSearchForm.value.type !== null && submitSearchForm.value.type !== '') {
      params.type = submitSearchForm.value.type
    }
    if (submitSearchForm.value.status !== null && submitSearchForm.value.status !== '') {
      params.status = submitSearchForm.value.status
    }
    if (submitSearchForm.value.dateRange && submitSearchForm.value.dateRange.length === 2) {
      const dr = submitSearchForm.value.dateRange
      params.startDate = dr[0] instanceof Date ? formatLocalDateTime(dr[0]) : dr[0]
      params.endDate = dr[1] instanceof Date ? formatLocalDateTime(dr[1]) : dr[1]
    }
    const res = await approvalApi.mySubmissions(params)
    submissionList.value = res.data?.records || []
    submissionTotal.value = res.data?.total || 0
  } catch {
    submissionList.value = []
    submissionTotal.value = 0
  }
}

function resetSubmitSearch() {
  submitSearchForm.value = { pageNum: 1, pageSize: 10, type: null, status: null, dateRange: defaultDateRange() }
  loadSubmissions()
}

async function handleWithdraw(row) {
  await ElMessageBox.confirm(`确认撤回审批单 ${row.approvalNo}？`, '撤回确认')
  try {
    await approvalApi.withdraw(row.id)
    ElMessage.success('已撤回')
    loadSubmissions()
  } catch {
    ElMessage.error('撤回失败')
  }
}

// ==================== 页签二：待我审批 ====================
const pendingList = ref([])
const pendingTotal = ref(0)
const pendingPage = ref({ pageNum: 1, pageSize: 10 })

async function loadPendingList() {
  try {
    const res = await approvalApi.pending(pendingPage.value)
    pendingList.value = res.data?.records || []
    pendingTotal.value = res.data?.total || 0
  } catch {
    pendingList.value = []
    pendingTotal.value = 0
  }
}

async function handleApprove(row) {
  await ElMessageBox.confirm(`确认通过审批单 ${row.approvalNo}？`, '审批确认')
  try {
    await approvalApi.approve(row.id)
    ElMessage.success('审批通过')
    loadPendingList()
  } catch {
    ElMessage.error('审批失败')
  }
}

// 驳回
const rejectDialogVisible = ref(false)
const rejectForm = ref({ reason: '' })
const currentRejectRow = ref(null)

function showRejectDialog(row) {
  currentRejectRow.value = row
  rejectForm.value = { reason: '' }
  rejectDialogVisible.value = true
}

async function handleReject() {
  if (!rejectForm.value.reason) {
    ElMessage.warning('请输入驳回原因')
    return
  }
  const row = currentRejectRow.value
  try {
    await approvalApi.reject(row.id, { reason: rejectForm.value.reason })
    ElMessage.success('已驳回')
    rejectDialogVisible.value = false
    loadPendingList()
  } catch {
    ElMessage.error('驳回失败')
  }
}

// ==================== 页签三：已审批 ====================
const historySearchForm = ref({
  pageNum: 1,
  pageSize: 10,
  type: null,
  dateRange: defaultDateRange()
})
const historyList = ref([])
const historyTotal = ref(0)

async function loadHistoryList() {
  try {
    const params = {
      pageNum: historySearchForm.value.pageNum,
      pageSize: historySearchForm.value.pageSize,
    }
    if (historySearchForm.value.type !== null && historySearchForm.value.type !== '') {
      params.type = historySearchForm.value.type
    }
    if (historySearchForm.value.dateRange && historySearchForm.value.dateRange.length === 2) {
      const dr = historySearchForm.value.dateRange
      params.startDate = dr[0] instanceof Date ? formatLocalDateTime(dr[0]) : dr[0]
      params.endDate = dr[1] instanceof Date ? formatLocalDateTime(dr[1]) : dr[1]
    }
    const res = await approvalApi.history(params)
    historyList.value = res.data?.records || []
    historyTotal.value = res.data?.total || 0
  } catch {
    historyList.value = []
    historyTotal.value = 0
  }
}

function resetHistorySearch() {
  historySearchForm.value = { pageNum: 1, pageSize: 10, type: null, dateRange: defaultDateRange() }
  loadHistoryList()
}

// ==================== 详情弹窗 ====================
const detailDialogVisible = ref(false)
const currentDetail = ref(null)

async function showApprovalDetail(row) {
  try {
    const res = await approvalApi.detail(row.id)
    currentDetail.value = res.data || row
  } catch {
    currentDetail.value = row
  }
  detailDialogVisible.value = true
}

// ==================== 审批记录弹窗 ====================
const flowDialogVisible = ref(false)
const flowSteps = ref([])

async function showFlowDetail(row) {
  try {
    const res = await approvalApi.flowDetail(row.id)
    flowSteps.value = res.data || []
  } catch {
    flowSteps.value = []
  }
  flowDialogVisible.value = true
}

// ==================== 初始化 ====================
onMounted(() => {
  loadSubmissions()
})
</script>

<style scoped>
.filter-select { width: 160px; }

.flow-step-card {
  padding: 0;
}
.flow-step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.flow-step-name {
  font-weight: 600;
  font-size: 14px;
}
.flow-step-info {
  color: var(--text-secondary);
  font-size: 13px;
}
.flow-step-reason {
  color: var(--el-color-danger);
  font-size: 13px;
  margin-top: 4px;
}
.schema-pre {
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
  margin: 0;
  font-size: 12px;
  background: var(--bg-page);
  padding: 8px;
  border-radius: 4px;
}
.mt-md { margin-top: 16px; }
.mb-sm { margin-bottom: 8px; }
</style>
