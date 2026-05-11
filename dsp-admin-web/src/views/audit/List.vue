<template>
  <div>
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>审计日志</span>
          <el-button type="primary" size="small" @click="loadData">刷新</el-button>
        </div>
      </template>

      <!-- 筛选条件 -->
      <el-form :inline="true" style="margin-bottom:16px">
        <el-form-item label="接口编码">
          <el-input v-model="filters.transno" placeholder="模糊搜索" clearable />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="filters.operation" clearable placeholder="全部">
            <el-option label="数据查询" value="QUERY" />
            <el-option label="在线导出" value="EXPORT" />
            <el-option label="接口发布" value="PUBLISH" />
            <el-option label="接口下线" value="OFFLINE" />
            <el-option label="接口管理" value="INTERFACE_MGR" />
            <el-option label="数据源管理" value="DATASOURCE_MGR" />
            <el-option label="应用授权管理" value="APP_AUTH_MGR" />
            <el-option label="审批" value="APPROVAL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="pageNum = 1; loadData()">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="appId" label="应用ID" width="120" show-overflow-tooltip />
        <el-table-column prop="transno" label="接口编码" width="160" show-overflow-tooltip />
        <el-table-column prop="operation" label="操作类型" width="130" />
        <el-table-column prop="responseCode" label="状态码" width="80">
          <template #default="{ row }">
            <el-tag :type="row.responseCode === '0000' ? 'success' : 'danger'" size="small">
              {{ row.responseCode }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costTime" label="耗时(ms)" width="100" />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="ip" label="IP" width="130" show-overflow-tooltip />
        <el-table-column prop="createdTime" label="时间" width="170" :formatter="fmtTimeCol" />
        <el-table-column prop="requestData" label="请求参数" min-width="200" show-overflow-tooltip />
      </el-table>

      <el-pagination
        style="margin-top:16px;justify-content:flex-end"
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
import { auditApi } from '../../api'
import { fmtTime } from '../../utils/format'

const tableData = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const filters = ref({ transno: '', operation: '' })

function fmtTimeCol(_row, _col, val) { return fmtTime(val) }

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (filters.value.transno) params.transno = filters.value.transno
    if (filters.value.operation) params.operation = filters.value.operation
    const res = await auditApi.list(params)
    if (res.data && res.data.records) {
      tableData.value = res.data.records
      total.value = res.data.total
    }
  } catch (e) {
    ElMessage.error('加载审计日志失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => loadData())
</script>
