<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>导出任务</span>
          <el-button type="primary" size="small" @click="loadData">刷新</el-button>
        </div>
      </template>

      <el-table :data="tableData" border stripe v-loading="loading" empty-text="暂无导出任务">
        <el-table-column prop="id" label="任务ID" width="80" />
        <el-table-column prop="transno" label="接口编码" width="180" />
        <el-table-column prop="exportType" label="导出类型" width="100">
          <template #default="{ row }">{{ row.exportType === 1 ? '在线' : '离线' }}</template>
        </el-table-column>
        <el-table-column prop="fileFormat" label="格式" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="taskStatusType(row.status)">{{ taskStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalRows" label="总行数" width="100" />
        <el-table-column prop="progress" label="进度" width="120">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" :status="row.status === 3 ? 'exception' : row.status === 2 ? 'success' : ''" />
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误信息" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createdTime" label="创建时间" width="180" :formatter="fmtTimeCol" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleDownload(row)" v-if="row.status === 2">下载</el-button>
            <el-button size="small" @click="handleRefresh(row)" v-if="row.status === 0 || row.status === 1">刷新</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
defineOptions({ name: '导出管理' })

import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { exportApi } from '../../api'
import { fmtTime } from '../../utils/format'
import { EXPORT_TASK_STATUS, EXPORT_TASK_STATUS_TYPE } from '../../constants/status'

const tableData = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const taskStatusText = (s) => EXPORT_TASK_STATUS[s] || '未知'
const taskStatusType = (s) => EXPORT_TASK_STATUS_TYPE[s] || 'info'

function fmtTimeCol(_row, _col, val) { return fmtTime(val) }

async function loadData() {
  loading.value = true
  try {
    const res = await exportApi.list({ pageNum: pageNum.value, pageSize: pageSize.value })
    if (res.data && res.data.records) {
      tableData.value = res.data.records
      total.value = res.data.total
    }
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleDownload(row) {
  const ext = (row.fileFormat || 'xlsx').toLowerCase()
  exportApi.download(row.id, `export_${row.transno}_${row.id}.${ext}`)
}

async function handleRefresh(row) {
  try {
    const res = await exportApi.detail(row.id)
    if (res.data) {
      const idx = tableData.value.findIndex(r => r.id === row.id)
      if (idx !== -1) tableData.value[idx] = res.data
      ElMessage.success('已刷新')
    }
  } catch {
    // 全局拦截器已弹出错误
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
