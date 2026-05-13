<template>
  <div>
    <el-card>
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 我的申请 -->
        <el-tab-pane label="我的申请" name="mine">
          <div class="mb-16">
            <el-button type="primary" @click="openApplyDialog">新增申请</el-button>
          </div>

          <el-table :data="myList" border stripe>
            <el-table-column prop="applicantSystemName" label="请求方系统" width="160" />
            <el-table-column prop="providerSystemName" label="服务方系统" width="160" />
            <el-table-column prop="interfaceName" label="接口" width="200" show-overflow-tooltip />
            <el-table-column prop="requirementNo" label="需求编号" width="150" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="appStatusType(row.status)">{{ appStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="申请时间" width="170" :formatter="fmtTimeCol" />
            <el-table-column prop="rejectReason" label="驳回原因" min-width="150" show-overflow-tooltip />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="viewDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="mt-16" style="display:flex;justify-content:flex-end">
            <el-pagination
              v-model:current-page="minePage.pageNum"
              v-model:page-size="minePage.pageSize"
              :total="mineTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadMyList"
              @current-change="loadMyList"
            />
          </div>
        </el-tab-pane>

        <!-- 待我审批 -->
        <el-tab-pane label="待我审批" name="pending">
          <el-table :data="pendingList" border stripe>
            <el-table-column prop="applicant" label="申请人" width="100" />
            <el-table-column prop="applicantSystemName" label="请求方系统" width="150" />
            <el-table-column prop="providerSystemName" label="服务方系统" width="150" />
            <el-table-column prop="interfaceName" label="接口" width="200" show-overflow-tooltip />
            <el-table-column prop="requirementNo" label="需求编号" width="150" />
            <el-table-column prop="requirementDesc" label="需求描述" min-width="180" show-overflow-tooltip />
            <el-table-column prop="createdTime" label="申请时间" width="170" :formatter="fmtTimeCol" />
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="viewDetail(row)">详情</el-button>
                <template v-if="hasAnyRole('DEPT_MANAGER')">
                  <el-button size="small" type="success" @click="handleApprove(row)">通过</el-button>
                  <el-button size="small" type="danger" @click="showRejectDialog(row)">驳回</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>

          <div class="mt-16" style="display:flex;justify-content:flex-end">
            <el-pagination
              v-model:current-page="pendingPage.pageNum"
              v-model:page-size="pendingPage.pageSize"
              :total="pendingTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadPendingList"
              @current-change="loadPendingList"
            />
          </div>
        </el-tab-pane>

        <!-- 已审批 -->
        <el-tab-pane label="已审批" name="done">
          <el-table :data="doneList" border stripe>
            <el-table-column prop="applicant" label="申请人" width="100" />
            <el-table-column prop="applicantSystemName" label="请求方系统" width="150" />
            <el-table-column prop="providerSystemName" label="服务方系统" width="150" />
            <el-table-column prop="interfaceName" label="接口" width="200" show-overflow-tooltip />
            <el-table-column prop="requirementNo" label="需求编号" width="150" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="appStatusType(row.status)">{{ appStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="approver" label="审批人" width="100" />
            <el-table-column prop="approveTime" label="审批时间" width="170" :formatter="fmtTimeCol" />
            <el-table-column prop="rejectReason" label="驳回原因" min-width="150" show-overflow-tooltip />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="viewDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="mt-16" style="display:flex;justify-content:flex-end">
            <el-pagination
              v-model:current-page="donePage.pageNum"
              v-model:page-size="donePage.pageSize"
              :total="doneTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadDoneList"
              @current-change="loadDoneList"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 新增申请弹窗 -->
    <el-dialog v-model="applyDialogVisible" title="新增接口申请" width="650px" destroy-on-close>
      <el-form :model="applyForm" label-width="110px">
        <el-form-item label="请求方系统" required>
          <el-select v-model="applyForm.reqSystemId" placeholder="请选择请求方系统" style="width:100%">
            <el-option v-for="s in reqSystemList" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务方系统" required>
          <el-select v-model="applyForm.providerSystemId" placeholder="请选择服务方系统" style="width:100%" @change="handleProviderSystemChange">
            <el-option v-for="s in allSystemList" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="选择接口" required>
          <el-select v-model="applyForm.interfaceId" placeholder="请先选择服务方系统" style="width:100%" :disabled="!applyForm.providerSystemId" filterable @change="onInterfaceChange">
            <el-option v-for="inf in interfaceOptions" :key="inf.id" :label="`${inf.transno} - ${inf.name}`" :value="inf.id" />
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
        <el-form-item label="下游接口/页面">
          <el-input v-model="applyForm.downstreamInfo" type="textarea" :rows="2" placeholder="请描述下游接口或页面信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitApply" :loading="submitting">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectDialogVisible" title="驳回申请" width="450px">
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
    <el-dialog v-model="detailDialogVisible" title="申请详情" width="600px">
      <el-descriptions :column="2" border v-if="currentDetail">
        <el-descriptions-item label="请求方系统">{{ currentDetail.applicantSystemName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="服务方系统">{{ currentDetail.providerSystemName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="接口">{{ currentDetail.interfaceName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="需求编号">{{ currentDetail.requirementNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="appStatusType(currentDetail.status)">{{ appStatusText(currentDetail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="申请人">{{ currentDetail.applicant || '-' }}</el-descriptions-item>
        <el-descriptions-item label="需求描述" :span="2">{{ currentDetail.requirementDesc || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请原因" :span="2">{{ currentDetail.applyReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下游接口/页面" :span="2">{{ currentDetail.downstreamInfo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ fmtTime(currentDetail.createdTime) }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ currentDetail.approver || currentDetail.approver2 || '-' }}</el-descriptions-item>
        <el-descriptions-item label="驳回原因" :span="2" v-if="currentDetail.rejectReason">{{ currentDetail.rejectReason }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: '接口申请' })

import { ref, onMounted } from 'vue'
import { interfaceApi, systemApi, applicationApi } from '../../api'
import { useAuthStore } from '../../stores/auth'
import { hasAnyRole } from '../../directives/role'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime } from '../../utils/format'

const authStore = useAuthStore()

// 申请状态映射
const APP_STATUS = { 0: '待审批', 1: '已通过', 2: '已驳回' }
const APP_STATUS_TYPE = { 0: 'warning', 1: 'success', 2: 'danger' }
const appStatusText = (s) => APP_STATUS[s] || '未知'
const appStatusType = (s) => APP_STATUS_TYPE[s] || 'info'

const activeTab = ref('mine')

// 我的申请
const myList = ref([])
const mineTotal = ref(0)
const minePage = ref({ pageNum: 1, pageSize: 10 })

// 待我审批
const pendingList = ref([])
const pendingTotal = ref(0)
const pendingPage = ref({ pageNum: 1, pageSize: 10 })

// 已审批
const doneList = ref([])
const doneTotal = ref(0)
const donePage = ref({ pageNum: 1, pageSize: 10 })

// 新增申请
const applyDialogVisible = ref(false)
const submitting = ref(false)
const reqSystemList = ref([])
const allSystemList = ref([])
const interfaceOptions = ref([])
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

// 驳回
const rejectDialogVisible = ref(false)
const rejectForm = ref({ reason: '' })
const currentRejectRow = ref(null)

// 详情
const detailDialogVisible = ref(false)
const currentDetail = ref(null)

function fmtTimeCol(_row, _col, val) { return fmtTime(val) }

// 加载我的申请列表
async function loadMyList() {
  try {
    const res = await applicationApi.myList({ pageNum: minePage.value.pageNum, pageSize: minePage.value.pageSize })
    myList.value = res.data?.records || []
    mineTotal.value = res.data?.total || 0
  } catch {
    myList.value = []
    mineTotal.value = 0
  }
}

// 加载待我审批列表
async function loadPendingList() {
  try {
    const res = await applicationApi.pendingApproval({ pageNum: pendingPage.value.pageNum, pageSize: pendingPage.value.pageSize })
    pendingList.value = res.data?.records || []
    pendingTotal.value = res.data?.total || 0
  } catch {
    pendingList.value = []
    pendingTotal.value = 0
  }
}

// 加载已审批列表
async function loadDoneList() {
  try {
    const res = await applicationApi.approvedList({ pageNum: donePage.value.pageNum, pageSize: donePage.value.pageSize })
    doneList.value = res.data?.records || []
    doneTotal.value = res.data?.total || 0
  } catch {
    doneList.value = []
    doneTotal.value = 0
  }
}

function handleTabChange(tab) {
  if (tab === 'mine') loadMyList()
  else if (tab === 'pending') loadPendingList()
  else if (tab === 'done') loadDoneList()
}

// 打开新增申请弹窗
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
  interfaceOptions.value = []
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

// 服务方系统变更时加载对应接口
async function handleProviderSystemChange(systemId) {
  applyForm.value.interfaceId = null
  applyForm.value.transno = ''
  interfaceOptions.value = []
  if (!systemId) return

  try {
    const res = await interfaceApi.list({ systemId, status: 3, pageNum: 1, pageSize: 200 })
    interfaceOptions.value = res.data?.records || []
  } catch {
    interfaceOptions.value = []
  }
}

function onInterfaceChange(val) {
  const inf = interfaceOptions.value.find(i => i.id === val)
  applyForm.value.transno = inf ? inf.transno : ''
}

// 提交申请
async function handleSubmitApply() {
  const form = applyForm.value
  if (!form.reqSystemId) {
    ElMessage.warning('请选择请求方系统')
    return
  }
  if (!form.providerSystemId) {
    ElMessage.warning('请选择服务方系统')
    return
  }
  if (!form.interfaceId) {
    ElMessage.warning('请选择接口')
    return
  }
  if (!form.reqNo) {
    ElMessage.warning('请输入需求编号')
    return
  }
  if (!form.reqDesc) {
    ElMessage.warning('请输入需求描述')
    return
  }
  if (!form.applyReason) {
    ElMessage.warning('请输入申请原因')
    return
  }

  submitting.value = true
  try {
    await applicationApi.submit(form)
    ElMessage.success('申请已提交')
    applyDialogVisible.value = false
    loadMyList()
  } catch {
    ElMessage.error('提交失败')
  } finally {
    submitting.value = false
  }
}

// 审批通过
async function handleApprove(row) {
  await ElMessageBox.confirm(`确认通过该接口申请？`, '审批确认')
  try {
    await applicationApi.approve(row.id)
    ElMessage.success('审批通过')
    loadPendingList()
  } catch {
    ElMessage.error('审批失败')
  }
}

// 显示驳回弹窗
function showRejectDialog(row) {
  currentRejectRow.value = row
  rejectForm.value = { reason: '' }
  rejectDialogVisible.value = true
}

// 确认驳回
async function handleReject() {
  if (!rejectForm.value.reason) {
    ElMessage.warning('请输入驳回原因')
    return
  }
  const row = currentRejectRow.value
  try {
    await applicationApi.reject(row.id, { reason: rejectForm.value.reason })
    ElMessage.success('已驳回')
    rejectDialogVisible.value = false
    loadPendingList()
  } catch {
    ElMessage.error('驳回失败')
  }
}

// 查看详情
function viewDetail(row) {
  currentDetail.value = row
  detailDialogVisible.value = true
}

onMounted(() => {
  loadMyList()
})
</script>

<style scoped>
.mb-16 { margin-bottom: 16px; }
.mt-16 { margin-top: 16px; }
</style>
