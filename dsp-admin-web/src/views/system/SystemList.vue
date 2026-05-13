<template>
  <div style="display:flex;gap:16px;height:calc(100vh - 140px)">
    <!-- 左侧部门树 -->
    <el-card style="width:260px;flex-shrink:0;overflow:auto">
      <template #header>
        <span>部门</span>
      </template>
      <el-tree
        ref="treeRef"
        :data="deptTree"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        highlight-current
        default-expand-all
        :expand-on-click-node="false"
        @node-click="handleDeptClick"
      >
        <template #default="{ node, data }">
          <span class="tree-node-label">{{ data.name }}</span>
        </template>
      </el-tree>
      <div class="mt-16">
        <el-button size="small" @click="handleClearDeptFilter">显示全部</el-button>
      </div>
    </el-card>

    <!-- 右侧系统列表 -->
    <div style="flex:1;overflow:auto">
      <el-card>
        <div class="mb-16" style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-size:16px;font-weight:600">
            {{ selectedDeptName ? `${selectedDeptName} - 系统列表` : '系统列表' }}
          </span>
          <el-button type="primary" @click="openCreateDialog">新增系统</el-button>
        </div>

        <el-table :data="tableData" border stripe>
          <el-table-column prop="name" label="系统名称" min-width="150" />
          <el-table-column prop="code" label="系统编码" width="160" />
          <el-table-column prop="deptName" label="所属部门" width="150">
            <template #default="{ row }">{{ deptNameMap[row.deptId] || '-' }}</template>
          </el-table-column>
          <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdTime" label="创建时间" width="170" :formatter="fmtTimeCol" />
          <el-table-column label="操作" fixed="right" width="160">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="openEditDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="mt-16" style="display:flex;justify-content:flex-end">
          <el-pagination
            v-model:current-page="searchForm.pageNum"
            v-model:page-size="searchForm.pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadData"
            @current-change="loadData"
          />
        </div>
      </el-card>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="editDialogVisible" :title="editForm.id ? '编辑系统' : '新增系统'" width="520px" destroy-on-close>
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="系统名称" required>
          <el-input v-model="editForm.name" placeholder="请输入系统名称" />
        </el-form-item>
        <el-form-item label="系统编码" required>
          <el-input v-model="editForm.code" placeholder="请输入系统编码" :disabled="!!editForm.id" />
        </el-form-item>
        <el-form-item label="所属部门" required>
          <el-select v-model="editForm.deptId" placeholder="请选择部门" clearable style="width:100%">
            <el-option v-for="d in flatDeptList" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="请输入系统说明" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="editForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="正常"
            inactive-text="禁用"
          />
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
import { ref, onMounted } from 'vue'
import { systemApi, deptApi } from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime } from '../../utils/format'
import { hasAnyRole } from '../../directives/role'

const tableData = ref([])
const total = ref(0)
const searchForm = ref({ pageNum: 1, pageSize: 10, deptId: null })

const deptTree = ref([])
const flatDeptList = ref([])
const deptNameMap = ref({})
const selectedDeptName = ref('')
const treeRef = ref(null)

const editDialogVisible = ref(false)
const editForm = ref({ name: '', code: '', deptId: null, description: '', status: 1 })

function fmtTimeCol(_row, _col, val) { return fmtTime(val) }

// 加载部门树
async function loadDepts() {
  const res = await deptApi.tree()
  const list = res.data || []
  deptTree.value = buildTree(list, 0)
  flatDeptList.value = list
  // 构建部门名称映射
  const map = {}
  for (const d of list) {
    map[d.id] = d.name
  }
  deptNameMap.value = map
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

// 点击部门节点
function handleDeptClick(data) {
  searchForm.value.deptId = data.id
  searchForm.value.pageNum = 1
  selectedDeptName.value = data.name
  loadData()
}

// 清除部门过滤
function handleClearDeptFilter() {
  searchForm.value.deptId = null
  searchForm.value.pageNum = 1
  selectedDeptName.value = ''
  if (treeRef.value) {
    treeRef.value.setCurrentKey(null)
  }
  loadData()
}

// 加载系统列表
async function loadData() {
  const params = {}
  if (searchForm.value.deptId) {
    params.deptId = searchForm.value.deptId
  }
  const res = await systemApi.list(params)
  const data = res.data
  // 后端返回 List（非分页对象）
  if (Array.isArray(data)) {
    tableData.value = data
    total.value = data.length
  } else {
    tableData.value = data?.records || []
    total.value = data?.total || 0
  }
}

// 新增弹窗
function openCreateDialog() {
  editForm.value = {
    name: '',
    code: '',
    deptId: searchForm.value.deptId || null,
    description: '',
    status: 1
  }
  editDialogVisible.value = true
}

// 编辑弹窗
function openEditDialog(row) {
  editForm.value = {
    id: row.id,
    name: row.name,
    code: row.code,
    deptId: row.deptId,
    description: row.description || '',
    status: row.status
  }
  editDialogVisible.value = true
}

// 保存
async function handleSave() {
  if (!editForm.value.name) {
    ElMessage.warning('请输入系统名称')
    return
  }
  if (!editForm.value.code) {
    ElMessage.warning('请输入系统编码')
    return
  }
  if (!editForm.value.deptId) {
    ElMessage.warning('请选择所属部门')
    return
  }
  try {
    if (editForm.value.id) {
      await systemApi.update(editForm.value.id, editForm.value)
    } else {
      await systemApi.create(editForm.value)
    }
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    loadData()
  } catch {
    ElMessage.error('保存失败')
  }
}

// 删除
async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除系统「${row.name}」？删除后不可恢复。`, '删除确认', { type: 'warning' })
  try {
    await systemApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadDepts()
  loadData()
})
</script>

<style scoped>
.mb-16 { margin-bottom: 16px; }
.mt-16 { margin-top: 16px; }
.tree-node-label { font-size: 14px; }
</style>
