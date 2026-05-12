<template>
  <div>
    <el-card class="mb-16">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="searchForm.realName" placeholder="请输入姓名" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <div class="mb-16">
        <el-button type="primary" @click="openCreateDialog">新增用户</el-button>
      </div>

      <el-table :data="tableData" border stripe>
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="deptId" label="所属部门" width="150">
          <template #default="{ row }">{{ deptNameMap[row.deptId] || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="r in userRoleMap[row.id] || []" :key="r" size="small" class="mr-4">{{ r }}</el-tag>
            <span v-if="!userRoleMap[row.id]?.length">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="170" :formatter="fmtTimeCol" />
        <el-table-column label="操作" fixed="right" width="280">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" @click="openRoleDialog(row)">角色</el-button>
            <el-button size="small" @click="handleResetPwd(row)">重置密码</el-button>
            <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="handleToggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="mt-16" style="display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="searchForm.pageNum" v-model:page-size="searchForm.pageSize"
          :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="editDialogVisible" :title="editForm.id ? '编辑用户' : '新增用户'" width="500px" destroy-on-close>
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="用户名" required>
          <el-input v-model="editForm.username" :disabled="!!editForm.id" />
        </el-form-item>
        <el-form-item label="密码" required v-if="!editForm.id">
          <el-input v-model="editForm.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="editForm.realName" />
        </el-form-item>
        <el-form-item label="所属部门">
          <el-select v-model="editForm.deptId" placeholder="请选择部门" clearable style="width:100%">
            <el-option v-for="d in deptList" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 角色分配弹窗 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="400px" destroy-on-close>
      <el-checkbox-group v-model="selectedRoleIds">
        <el-checkbox v-for="r in allRoles" :key="r.id" :value="r.id">{{ r.roleName }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { userApi, deptApi, roleApi } from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime } from '../../utils/format'

const tableData = ref([])
const total = ref(0)
const searchForm = ref({ pageNum: 1, pageSize: 10, username: '', realName: '' })
const deptList = ref([])
const deptNameMap = ref({})
const userRoleMap = ref({})
const allRoles = ref([])

const editDialogVisible = ref(false)
const editForm = ref({ username: '', password: '', realName: '', deptId: null, status: 1 })

const roleDialogVisible = ref(false)
const currentUserId = ref(null)
const selectedRoleIds = ref([])

function fmtTimeCol(_row, _col, val) { return fmtTime(val) }

async function loadData() {
  const params = { ...searchForm.value }
  if (!params.username) delete params.username
  if (!params.realName) delete params.realName
  const res = await userApi.list(params)
  tableData.value = res.data?.records || []
  total.value = res.data?.total || 0
  // 加载每个用户的角色
  for (const u of tableData.value) {
    if (u.id) {
      const detail = await userApi.detail(u.id).catch(() => null)
      if (detail?.data) {
        // 从detail中获取角色（暂时只显示ids，后续可优化接口）
      }
    }
  }
}

async function loadRoles() {
  const res = await roleApi.list()
  allRoles.value = res.data || []
}

async function loadDepts() {
  const res = await deptApi.tree()
  deptList.value = res.data || []
  const map = {}
  function walk(list) {
    for (const d of list) {
      map[d.id] = d.name
      if (d.children) walk(d.children)
    }
  }
  walk(deptList.value)
  deptNameMap.value = map
}

function resetSearch() {
  searchForm.value = { pageNum: 1, pageSize: 10, username: '', realName: '' }
  loadData()
}

function openCreateDialog() {
  editForm.value = { username: '', password: '', realName: '', deptId: null, status: 1 }
  editDialogVisible.value = true
}

function openEditDialog(row) {
  editForm.value = { id: row.id, username: row.username, realName: row.realName, deptId: row.deptId, status: row.status }
  editDialogVisible.value = true
}

async function handleSave() {
  if (!editForm.value.username) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!editForm.value.id && !editForm.value.password) {
    ElMessage.warning('请输入密码')
    return
  }
  try {
    if (editForm.value.id) {
      await userApi.update(editForm.value.id, editForm.value)
    } else {
      await userApi.create(editForm.value)
    }
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    loadData()
  } catch {
    ElMessage.error('保存失败')
  }
}

async function handleResetPwd(row) {
  await ElMessageBox.prompt('请输入新密码', '重置密码', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /.{6,}/,
    inputErrorMessage: '密码至少6位'
  }).then(async ({ value }) => {
    await userApi.resetPassword(row.id, value)
    ElMessage.success('密码已重置')
  }).catch(() => {})
}

async function handleToggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  await userApi.updateStatus(row.id, newStatus)
  ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
  loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除用户 ${row.username}？`, '删除确认')
  await userApi.delete(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function openRoleDialog(row) {
  currentUserId.value = row.id
  // 获取当前用户的角色（从detail接口或单独获取）
  // 暂时先加载已有角色
  selectedRoleIds.value = []
  roleDialogVisible.value = true
}

async function handleSaveRoles() {
  await userApi.assignRoles(currentUserId.value, selectedRoleIds.value)
  ElMessage.success('角色分配成功')
  roleDialogVisible.value = false
  loadData()
}

onMounted(() => {
  loadData()
  loadDepts()
  loadRoles()
})
</script>

<style scoped>
.mb-16 { margin-bottom: 16px; }
.mt-16 { margin-top: 16px; }
.mr-4 { margin-right: 4px; }
</style>
