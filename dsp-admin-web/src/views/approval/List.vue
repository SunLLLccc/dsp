<template>
  <div>
    <!-- 搜索栏 -->
    <el-card class="mb-16">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width:160px">
            <el-option label="待审批" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已驳回" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card>
      <el-table :data="tableData" border stripe>
        <el-table-column prop="transno" label="接口编码" width="200" />
        <el-table-column prop="versionNo" label="版本号" width="100" />
        <el-table-column prop="applicant" label="申请人" width="120" />
        <el-table-column prop="applyTime" label="申请时间" width="180" :formatter="fmtTimeCol" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approver" label="审批人" width="120" />
        <el-table-column prop="approveTime" label="审批时间" width="180" :formatter="fmtTimeCol" />
        <el-table-column prop="rejectReason" label="驳回原因" show-overflow-tooltip />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button size="small" type="success" @click="handleApprove(row)" v-role="'DEPT_MANAGER'">通过</el-button>
              <el-button size="small" type="danger" @click="showRejectDialog(row)" v-role="'DEPT_MANAGER'">驳回</el-button>
            </template>
            <el-button size="small" type="primary" @click="viewDiff(row)">查看差异</el-button>
            <el-button size="small" @click="viewRecords(row)">审批记录</el-button>
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

    <!-- 驳回对话框 -->
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

    <!-- 审批记录对话框 -->
    <el-dialog v-model="recordsDialogVisible" :title="`审批记录 - ${currentTransno}`" width="800px">
      <el-table :data="recordsData" border stripe>
        <el-table-column prop="versionNo" label="版本号" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applicant" label="申请人" width="100" />
        <el-table-column prop="applyTime" label="申请时间" width="170" :formatter="fmtTimeCol" />
        <el-table-column prop="approver" label="审批人" width="100" />
        <el-table-column prop="approveTime" label="审批时间" width="170" :formatter="fmtTimeCol" />
        <el-table-column prop="rejectReason" label="驳回原因" show-overflow-tooltip />
      </el-table>
      <div class="mt-16" style="display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="recordPage.pageNum" v-model:page-size="recordPage.pageSize"
          :total="recordTotal" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
          @size-change="loadRecords" @current-change="loadRecords" />
      </div>
    </el-dialog>

    <!-- 审批差异对比弹窗 -->
    <SchemaCompareDialog
      v-model="diffVisible"
      :title="`审批差异 - ${diffTransno} V${diffVersionNo}`"
      left-label="变更前（当前版本）"
      :left-input="diffBeforeInput"
      :left-output="diffBeforeOutput"
      right-label="变更后（待审批版本）"
      :right-input="diffAfterInput"
      :right-output="diffAfterOutput"
    />
  </div>
</template>

<script setup>
defineOptions({ name: '审批管理' })

import { ref, onMounted } from 'vue'
import { approvalApi, interfaceApi } from '../../api'
import { APPROVAL_STATUS, APPROVAL_STATUS_TYPE } from '../../constants/status'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime } from '../../utils/format'
import SchemaCompareDialog from '../../components/SchemaCompareDialog.vue'
import { hasAnyRole } from '../../directives/role'

const tableData = ref([])
const total = ref(0)
const searchForm = ref({ pageNum: 1, pageSize: 10, status: null })

const rejectDialogVisible = ref(false)
const rejectForm = ref({ reason: '' })
const currentRow = ref(null)

const recordsDialogVisible = ref(false)
const currentTransno = ref('')
const recordsData = ref([])
const recordTotal = ref(0)
const recordPage = ref({ pageNum: 1, pageSize: 10 })

const statusText = (s) => APPROVAL_STATUS[s] || '未知'
const statusType = (s) => APPROVAL_STATUS_TYPE[s] || 'info'

function fmtTimeCol(_row, _col, val) { return fmtTime(val) }

// 差异对比
const diffVisible = ref(false)
const diffTransno = ref('')
const diffVersionNo = ref('')
const diffBeforeInput = ref('')
const diffBeforeOutput = ref('')
const diffAfterInput = ref('')
const diffAfterOutput = ref('')

async function loadData() {
  // 待审批列表（status=0），或全部审批记录
  const params = { pageNum: searchForm.value.pageNum, pageSize: searchForm.value.pageSize }
  if (searchForm.value.status === 0) {
    const res = await approvalApi.pending(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } else {
    // 查全部需要后端支持，暂用待审批列表+前端过滤
    // 后续可扩展全量查询接口
    const res = await approvalApi.pending(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  }
}

function resetSearch() {
  searchForm.value = { pageNum: 1, pageSize: 10, status: null }
  loadData()
}

async function handleApprove(row) {
  await ElMessageBox.confirm(`确认通过接口 ${row.transno} V${row.versionNo} 的审批？`, '审批确认')
  await approvalApi.approve(row.transno)
  ElMessage.success('审批通过')
  loadData()
}

function showRejectDialog(row) {
  currentRow.value = row
  rejectForm.value = { reason: '' }
  rejectDialogVisible.value = true
}

async function handleReject() {
  if (!rejectForm.value.reason) {
    ElMessage.warning('请输入驳回原因')
    return
  }
  const row = currentRow.value
  await approvalApi.reject(row.transno, { reason: rejectForm.value.reason })
  ElMessage.success('已驳回')
  rejectDialogVisible.value = false
  loadData()
}

async function viewRecords(row) {
  currentTransno.value = row.transno
  recordPage.value = { pageNum: 1, pageSize: 10 }
  recordsDialogVisible.value = true
  loadRecords()
}

async function loadRecords() {
  const res = await approvalApi.records(currentTransno.value, recordPage.value)
  recordsData.value = res.data?.records || []
  recordTotal.value = res.data?.total || 0
}

async function viewDiff(row) {
  diffTransno.value = row.transno
  diffVersionNo.value = row.versionNo
  // 变更后：待审批版本的 schema
  try {
    const afterRes = await interfaceApi.getVersion(row.transno, row.versionNo)
    diffAfterInput.value = afterRes.data?.inputSchema || ''
    diffAfterOutput.value = afterRes.data?.outputSchema || ''
  } catch {
    diffAfterInput.value = ''
    diffAfterOutput.value = ''
  }
  // 变更前：最新已发布版本的 schema（versions 接口只返回已发布版本，取第一条即最新）
  try {
    const verRes = await interfaceApi.versions(row.transno, { pageNum: 1, pageSize: 1 })
    const latestPublished = verRes.data?.records?.[0]
    if (latestPublished) {
      const beforeRes = await interfaceApi.getVersion(row.transno, latestPublished.versionNo)
      diffBeforeInput.value = beforeRes.data?.inputSchema || ''
      diffBeforeOutput.value = beforeRes.data?.outputSchema || ''
    } else {
      diffBeforeInput.value = ''
      diffBeforeOutput.value = ''
    }
  } catch {
    diffBeforeInput.value = ''
    diffBeforeOutput.value = ''
  }
  diffVisible.value = true
}

onMounted(() => loadData())
</script>

<style scoped>
.mb-16 { margin-bottom: 16px; }
.mt-16 { margin-top: 16px; }
</style>
