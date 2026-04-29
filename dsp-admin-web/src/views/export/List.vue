<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>导出任务</span>
          <el-button type="primary" size="small" @click="loadData">刷新</el-button>
        </div>
      </template>

      <el-table :data="tableData" border stripe v-loading="loading">
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
        <el-table-column prop="createdTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleDownload(row)" v-if="row.status === 2">下载</el-button>
            <el-button size="small" @click="handleRefresh(row)" v-if="row.status === 0 || row.status === 1">刷新</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top: 16px; justify-content: flex-end;"
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { exportApi } from '@/api/index'

const tableData = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const taskStatusText = (s) => ({ 0: '待处理', 1: '处理中', 2: '已完成', 3: '失败' }[s] || '未知')
const taskStatusType = (s) => ({ 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }[s] || 'info')

async function loadData() {
  loading.value = true
  try {
    const res = await exportApi.list({ pageNum: pageNum.value, pageSize: pageSize.value })
    if (res.data && res.data.records) {
      tableData.value = res.data.records
      total.value = res.data.total
    }
  } catch (e) {
    ElMessage.error('加载导出任务失败')
  } finally {
    loading.value = false
  }
}

function handleDownload(row) {
  window.open(`/dsp/offline/export/${row.id}/download`, '_blank')
}

async function handleRefresh(row) {
  try {
    const res = await exportApi.detail(row.id)
    if (res.data) {
      const idx = tableData.value.findIndex(r => r.id === row.id)
      if (idx !== -1) tableData.value[idx] = res.data
      ElMessage.success('已刷新')
    }
  } catch (e) {
    ElMessage.error('刷新失败')
  }
}

onMounted(() => {
  loadData()
})
</script>
