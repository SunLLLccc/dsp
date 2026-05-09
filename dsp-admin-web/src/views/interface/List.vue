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
          <el-input v-model="searchForm.systemName" placeholder="请输入所属系统" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable>
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
            <el-option label="已下线" :value="2" />
            <el-option label="待审批" :value="3" />
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
        <el-button type="primary" @click="handleCreate">新增接口</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" border stripe>
        <el-table-column prop="transno" label="接口编码" width="200" />
        <el-table-column prop="name" label="接口名称" width="180" />
        <el-table-column prop="systemName" label="所属系统" width="150" />
        <el-table-column prop="currentVersion" label="当前版本" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedTime" label="更新时间" width="180" />
        <el-table-column label="操作" fixed="right" width="360">
          <template #default="{ row }">
            <el-button size="small" @click="handleViewSchema(row)">查看</el-button>
            <el-button size="small" @click="handleEdit(row)" :disabled="row.status === 3">编辑</el-button>
            <el-button size="small" @click="showVersionHistory(row)">版本</el-button>
            <el-button size="small" type="warning" @click="handleWithdraw(row)" v-if="row.status === 3">撤销审批</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)" :disabled="row.status === 3">删除</el-button>
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
    <el-dialog v-model="viewSchemaVisible" :title="`报文定义 - ${viewSchemaTransno}`" width="800px">
      <el-tabs>
        <el-tab-pane label="输入报文">
          <el-input type="textarea" :rows="15" :model-value="viewInputSchema" readonly style="font-family:monospace" />
        </el-tab-pane>
        <el-tab-pane label="输出报文">
          <el-input type="textarea" :rows="15" :model-value="viewOutputSchema" readonly style="font-family:monospace" />
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

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
        <el-table-column prop="createdTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="viewSchema(row)">查看Schema</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="mt-16" style="display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="versionPage.pageNum" v-model:page-size="versionPage.pageSize"
          :total="versionTotal" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
          @size-change="loadVersions" @current-change="loadVersions" />
      </div>
    </el-dialog>

    <!-- Schema 查看弹窗 -->
    <el-dialog v-model="schemaDialogVisible" :title="`Schema - V${schemaVersionNo}`" width="800px">
      <el-tabs>
        <el-tab-pane label="输入报文">
          <el-input type="textarea" :rows="15" :model-value="inputSchema" readonly style="font-family:monospace" />
        </el-tab-pane>
        <el-tab-pane label="输出报文">
          <el-input type="textarea" :rows="15" :model-value="outputSchema" readonly style="font-family:monospace" />
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { interfaceApi } from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const tableData = ref([])
const total = ref(0)
const searchForm = ref({ pageNum: 1, pageSize: 10, transno: '', name: '', systemName: '', status: null })

const statusText = (s) => ({ 0: '草稿', 1: '已发布', 2: '已下线', 3: '待审批' }[s] || '未知')
const statusType = (s) => ({ 0: 'info', 1: 'success', 2: 'danger', 3: 'warning' }[s] || 'info')

const verStatusText = (s) => ({ 0: '草稿', 1: '待审批', 2: '已驳回', 3: '已发布' }[s] || '未知')
const verStatusType = (s) => ({ 0: 'info', 1: 'warning', 2: 'danger', 3: 'success' }[s] || 'info')

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
    let verData = null
    if (row.currentVersion && row.currentVersion > 0) {
      const res = await interfaceApi.getVersion(row.transno, row.currentVersion)
      verData = res.data
    } else {
      const res = await interfaceApi.versions(row.transno, { pageNum: 1, pageSize: 1 })
      verData = res.data?.records?.[0]
    }
    if (verData) {
      viewInputSchema.value = formatJson(verData.inputSchema)
      viewOutputSchema.value = formatJson(verData.outputSchema)
    }
  } catch { /* 无版本 */ }
  viewSchemaVisible.value = true
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除接口 ${row.transno}？删除将提交审批`, '删除确认', { type: 'warning' })
  // 删除走审批：先保存一个标记删除的版本，再提交审批
  try {
    await interfaceApi.saveSchema(row.transno, {
      inputSchema: '',
      outputSchema: '',
      changeLog: '申请删除接口',
      operator: 'admin'
    })
    const verListRes = await interfaceApi.versions(row.transno, { pageNum: 1, pageSize: 1 })
    const latestVer = verListRes.data?.records?.[0]
    if (latestVer) {
      await interfaceApi.submitApproval(row.transno, latestVer.versionNo, { operator: 'admin' })
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
    const verListRes = await interfaceApi.versions(row.transno, { pageNum: 1, pageSize: 1 })
    const latestVer = verListRes.data?.records?.[0]
    if (latestVer) {
      await interfaceApi.withdraw(row.transno, latestVer.versionNo)
      ElMessage.success('已撤销审批')
      loadData()
    }
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

function formatJson(str) {
  if (!str) return ''
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.mb-16 { margin-bottom: 16px; }
.mt-16 { margin-top: 16px; }
</style>
