<template>
  <div>
    <el-card>
      <div class="mb-md">
        <el-button type="primary" @click="openCreateDialog(null)">新增顶级部门</el-button>
      </div>

      <el-table :data="deptTree" border row-key="id" :tree-props="{ children: 'children', hasChildren: 'hasChildren' }">
        <el-table-column prop="name" label="部门名称" min-width="200" />
        <el-table-column prop="code" label="部门编码" width="150" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="170" :formatter="fmtTimeCol" />
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openCreateDialog(row)">新增子部门</el-button>
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="editDialogVisible" :title="editForm.id ? '编辑部门' : '新增部门'" width="450px" destroy-on-close>
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="部门名称" required>
          <el-input v-model="editForm.name" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="部门编码" required>
          <el-input v-model="editForm.code" placeholder="请输入部门编码" :disabled="!!editForm.id" />
        </el-form-item>
        <el-form-item label="上级部门" v-if="editForm.parentName">
          <el-input :model-value="editForm.parentName" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
defineOptions({ name: '部门管理' })

import { ref, onMounted } from 'vue'
import { deptApi } from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime } from '../../utils/format'

const deptTree = ref([])
const editDialogVisible = ref(false)
const editForm = ref({ name: '', code: '', parentId: 0, parentName: '' })

function fmtTimeCol(_row, _col, val) { return fmtTime(val) }

async function loadData() {
  const res = await deptApi.tree()
  // 后端返回扁平列表，前端构建树
  const list = res.data || []
  deptTree.value = buildTree(list, 0)
}

function buildTree(list, parentId) {
  return list
    .filter(d => d.parentId === parentId)
    .map(d => {
      const children = buildTree(list, d.id)
      return children.length > 0 ? { ...d, children } : d
    })
    .sort((a, b) => a.name.localeCompare(b.name, 'zh'))
}

function openCreateDialog(parent) {
  editForm.value = {
    name: '',
    code: '',
    parentId: parent ? parent.id : 0,
    parentName: parent ? parent.name : ''
  }
  editDialogVisible.value = true
}

function openEditDialog(row) {
  editForm.value = {
    id: row.id,
    name: row.name,
    code: row.code || '',
    parentId: row.parentId,
    parentName: ''
  }
  editDialogVisible.value = true
}

async function handleSave() {
  if (!editForm.value.name) {
    ElMessage.warning('请输入部门名称')
    return
  }
  try {
    if (editForm.value.id) {
      await deptApi.update(editForm.value.id, { name: editForm.value.name, code: editForm.value.code })
    } else {
      await deptApi.create({ name: editForm.value.name, code: editForm.value.code, parentId: editForm.value.parentId })
    }
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    loadData()
  } catch {
    ElMessage.error('保存失败')
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除部门 ${row.name}？`, '删除确认')
  await deptApi.delete(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => loadData())
</script>
