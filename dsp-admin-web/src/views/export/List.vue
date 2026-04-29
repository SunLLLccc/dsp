<template>
  <div>
    <el-card>
      <template #header><span>导出任务</span></template>

      <el-table :data="tableData" border stripe>
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
        <el-table-column prop="progress" label="进度" width="100">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" :status="row.status === 3 ? 'exception' : row.status === 2 ? 'success' : ''" />
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleDownload(row)" v-if="row.status === 2">下载</el-button>
            <el-button size="small" @click="handleRefresh(row)">刷新</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const tableData = ref([])

const taskStatusText = (s) => ({ 0: '待处理', 1: '处理中', 2: '已完成', 3: '失败' }[s] || '未知')
const taskStatusType = (s) => ({ 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }[s] || 'info')

function handleDownload(row) {
  window.open(`/ds/export/download/${row.id}`, '_blank')
}

function handleRefresh(row) {
  ElMessage.info('刷新功能需要后端接口支持')
}

onMounted(() => {
  // 导出任务列表需要后端查询接口，暂时为空
  tableData.value = []
})
</script>
