<template>
  <div>
    <!-- 搜索栏 -->
    <el-card class="mb-16">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="数据源名称">
          <el-input v-model="searchForm.dsName" placeholder="请输入数据源名称" clearable />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="searchForm.dsType" placeholder="全部" clearable>
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="Doris" value="DORIS" />
            <el-option label="MongoDB" value="MONGODB" />
            <el-option label="HTTP" value="HTTP" />
            <el-option label="Dubbo" value="DUBBO" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 + 表格 -->
    <el-card>
      <div class="mb-16">
        <el-button type="primary" @click="showDialog(null)" v-role="'USER'">新增数据源</el-button>
      </div>

      <el-table :data="tableData" border stripe>
        <el-table-column prop="dsName" label="数据源名称" width="160" />
        <el-table-column prop="dsType" label="类型" width="100" />
        <el-table-column prop="jdbcUrl" label="连接地址" show-overflow-tooltip />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showDialog(row)" v-role="'USER'">编辑</el-button>
            <el-button size="small" type="success" @click="handleTest(row)">测试连接</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)" v-role="'DEPT_MANAGER'">删除</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑数据源' : '新增数据源'" width="600px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="数据源名称" required>
          <el-input v-model="editForm.dsName" placeholder="如 mysql_main" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="editForm.dsType">
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="Doris" value="DORIS" />
            <el-option label="MongoDB" value="MONGODB" />
            <el-option label="HTTP" value="HTTP" />
            <el-option label="Dubbo" value="DUBBO" />
          </el-select>
        </el-form-item>
        <el-form-item label="连接地址" v-if="showJdbc">
          <el-input v-model="editForm.jdbcUrl" placeholder="jdbc:mysql://host:3306/db" />
        </el-form-item>
        <el-form-item label="用户名" v-if="showJdbc">
          <el-input v-model="editForm.username" />
        </el-form-item>
        <el-form-item label="密码" v-if="showJdbc">
          <el-input v-model="editForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="扩展配置" v-if="!showJdbc">
          <el-input v-model="editForm.extraConfig" type="textarea" :rows="4"
            placeholder='HTTP: {"url":"http://..."}\nDubbo: {"registry":"zookeeper://..."}' style="font-family:monospace" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { datasourceApi } from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hasAnyRole } from '../../directives/role'

const tableData = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const searchForm = ref({ pageNum: 1, pageSize: 10, dsName: '', dsType: '' })
const editForm = ref({ dsName: '', dsType: 'MYSQL', jdbcUrl: '', username: '', password: '', extraConfig: '' })

const showJdbc = computed(() => ['MYSQL', 'DORIS', 'MONGODB'].includes(editForm.value.dsType))

async function loadData() {
  const res = await datasourceApi.list(searchForm.value)
  tableData.value = res.data?.records || []
  total.value = res.data?.total || 0
}

function resetSearch() {
  searchForm.value = { pageNum: 1, pageSize: 10, dsName: '', dsType: '' }
  loadData()
}

function showDialog(row) {
  if (row) {
    editForm.value = { ...row }
  } else {
    editForm.value = { dsName: '', dsType: 'MYSQL', jdbcUrl: '', username: '', password: '', extraConfig: '' }
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (editForm.value.id) {
    await datasourceApi.update(editForm.value.id, editForm.value)
  } else {
    await datasourceApi.create(editForm.value)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function handleTest(row) {
  const res = await datasourceApi.test(row)
  ElMessage.success(res.data || '连接成功')
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除数据源 ${row.dsName}？`, '删除确认', { type: 'warning' })
  await datasourceApi.delete(row.id)
  ElMessage.success('已删除')
  loadData()
}

onMounted(() => loadData())
</script>

<style scoped>
.mb-16 { margin-bottom: 16px; }
.mt-16 { margin-top: 16px; }
</style>
