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
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable>
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
            <el-option label="已下线" :value="2" />
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
        <el-table-column prop="name" label="接口名称" width="200" />
        <el-table-column prop="protocolType" label="协议类型" width="100" />
        <el-table-column prop="currentVersion" label="当前版本" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedTime" label="更新时间" width="180" />
        <el-table-column label="操作" fixed="right" width="320">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="success" @click="handlePublish(row)" v-if="row.status === 0">发布</el-button>
            <el-button size="small" type="warning" @click="handleOffline(row)" v-if="row.status === 1">下线</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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
const searchForm = ref({ pageNum: 1, pageSize: 10, transno: '', name: '', status: null })

const statusText = (s) => ({ 0: '草稿', 1: '已发布', 2: '已下线' }[s] || '未知')
const statusType = (s) => ({ 0: 'info', 1: 'success', 2: 'danger' }[s] || 'info')

async function loadData() {
  const res = await interfaceApi.list(searchForm.value)
  tableData.value = res.data?.records || []
  total.value = res.data?.total || 0
}

function resetSearch() {
  searchForm.value = { pageNum: 1, pageSize: 10, transno: '', name: '', status: null }
  loadData()
}

function handleCreate() {
  router.push('/interface/edit')
}

function handleEdit(row) {
  router.push(`/interface/edit/${row.id}`)
}

async function handlePublish(row) {
  await ElMessageBox.confirm(`确认发布接口 ${row.transno}？`, '发布确认')
  await interfaceApi.approve(row.transno, row.currentVersion || 1, { approver: 'admin' })
  ElMessage.success('发布成功')
  loadData()
}

async function handleOffline(row) {
  await ElMessageBox.confirm(`确认下线接口 ${row.transno}？`, '下线确认')
  await interfaceApi.offline(row.transno)
  ElMessage.success('已下线')
  loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除接口 ${row.transno}？此操作不可恢复`, '删除确认', { type: 'warning' })
  await interfaceApi.delete(row.id)
  ElMessage.success('已删除')
  loadData()
}

onMounted(() => loadData())
</script>

<style scoped>
.mb-16 { margin-bottom: 16px; }
.mt-16 { margin-top: 16px; }
</style>
