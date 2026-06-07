<template>
  <div>
    <!-- 搜索栏 -->
    <el-card shadow="never" class="card-search">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="接口编码/名称/描述" clearable />
        </el-form-item>
        <el-form-item label="所属系统">
          <el-select v-model="searchForm.systemId" placeholder="全部" clearable filterable class="filter-select">
            <el-option v-for="sys in systemOptions" :key="sys.id" :label="sys.name" :value="sys.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable class="filter-select">
            <el-option v-for="(label, code) in INTERFACE_STATUS" :key="code" :label="label" :value="Number(code)" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="searchForm.tag" placeholder="输入标签筛选" clearable class="filter-select" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 接口市场列表 -->
    <el-card>
      <el-table :data="tableData" border stripe v-loading="loading" empty-text="暂无接口数据">
        <el-table-column prop="transno" label="接口编码" width="200" />
        <el-table-column prop="name" label="接口名称" width="180" />
        <el-table-column prop="systemName" label="所属系统" width="140" />
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column label="标签" width="140">
          <template #default="{ row }">
            <template v-if="row.tags && row.tags.length">
              <el-tag v-for="t in row.tags" :key="t" size="small" class="tag-gap">{{ t }}</el-tag>
            </template>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="INTERFACE_STATUS_TYPE[row.status] || 'info'">{{ INTERFACE_STATUS[row.status] || '未知' }}</el-tag>
          </template>
        </el-table-column>

        <!-- 健康指标 -->
        <el-table-column label="调用量(7天)" width="110" align="center">
          <template #default="{ row }">
            <span :class="{ 'text-success': row.callCount > 0, 'text-muted': row.callCount === 0 }">
              {{ row.callCount > 0 ? row.callCount : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="成功率" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.callCount > 0" :class="successRateClass(row.successRate)">
              {{ row.successRate }}%
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="平均耗时" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.callCount > 0" :class="costClass(row.avgCostMs)">{{ row.avgCostMs }}ms</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="最近错误" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="row.lastErrorTime">
              <el-text type="danger" size="small">{{ row.lastErrorMessage }}</el-text>
              <div class="error-time">{{ fmtTime(row.lastErrorTime) }}</div>
            </template>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>

        <el-table-column prop="updatedTime" label="更新时间" width="170" :formatter="fmtTimeCol" />
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="searchForm.pageNum" v-model:page-size="searchForm.pageSize"
          :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
defineOptions({ name: '接口市场' })

import { ref, onMounted } from 'vue'
import { marketplaceApi, systemApi } from '../../api'
import { fmtTime } from '../../utils/format'
import { INTERFACE_STATUS, INTERFACE_STATUS_TYPE } from '../../constants/status'

const tableData = ref([])
const total = ref(0)
const loading = ref(false)
const systemOptions = ref([])

const searchForm = ref({ pageNum: 1, pageSize: 10, keyword: '', systemId: null, status: null, tag: '' })

function fmtTimeCol(_row, _col, val) { return fmtTime(val) }

function successRateClass(rate) {
  if (rate >= 99) return 'text-success'
  if (rate >= 95) return 'text-warning'
  return 'text-danger'
}

function costClass(ms) {
  if (ms <= 100) return 'text-success'
  if (ms <= 500) return 'text-warning'
  return 'text-danger'
}

async function loadData() {
  loading.value = true
  try {
    const params = {
      pageNum: searchForm.value.pageNum,
      pageSize: searchForm.value.pageSize,
    }
    if (searchForm.value.keyword) params.keyword = searchForm.value.keyword
    if (searchForm.value.systemId) params.systemId = searchForm.value.systemId
    if (searchForm.value.status !== null && searchForm.value.status !== '') params.status = searchForm.value.status
    if (searchForm.value.tag) params.tag = searchForm.value.tag
    const res = await marketplaceApi.list(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  searchForm.value = { pageNum: 1, pageSize: 10, keyword: '', systemId: null, status: null, tag: '' }
  loadData()
}

async function loadSystemOptions() {
  try {
    const res = await systemApi.list()
    systemOptions.value = res.data?.records || res.data || []
  } catch {
    systemOptions.value = []
  }
}

onMounted(() => {
  loadSystemOptions()
  loadData()
})
</script>

<style scoped>
.filter-select { width: 150px; }
.text-success { color: var(--el-color-success); font-weight: 600; }
.text-warning { color: var(--el-color-warning); font-weight: 600; }
.text-danger { color: var(--el-color-danger); font-weight: 600; }
.text-muted { color: var(--text-placeholder); }
.error-time { font-size: 11px; color: var(--text-secondary); }
.tag-gap { margin-right: 4px; margin-bottom: 2px; }
</style>
