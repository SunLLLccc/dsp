<template>
  <div>
    <el-card>
      <div class="mb-16">
        <el-button type="primary" @click="showDialog(null)">新增应用</el-button>
      </div>

      <el-table :data="tableData" border stripe>
        <el-table-column prop="appId" label="应用ID" width="180" />
        <el-table-column prop="appName" label="应用名称" width="180" />
        <el-table-column prop="appSecret" label="密钥" width="200" show-overflow-tooltip />
        <el-table-column prop="allowedTransnos" label="授权接口" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showDialog(row)">编辑</el-button>
            <el-button size="small" type="warning" @click="handleGenToken(row)">签发Token</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑应用' : '新增应用'" width="600px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="应用ID" required>
          <el-input v-model="editForm.appId" placeholder="如 biz-system-01" :disabled="!!editForm.id" />
        </el-form-item>
        <el-form-item label="应用名称" required>
          <el-input v-model="editForm.appName" />
        </el-form-item>
        <el-form-item label="密钥">
          <el-input v-model="editForm.appSecret" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="授权接口">
          <el-input v-model="editForm.allowedTransnos" placeholder="逗号分隔，* 表示全部" />
          <div class="form-tip">多个接口用逗号分隔，如：GET_FUND_BALANCE,GET_ACCOUNT_INFO；输入 * 授权全部接口</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- Token展示对话框 -->
    <el-dialog v-model="tokenDialogVisible" title="Token已签发" width="500px">
      <el-alert type="warning" :closable="false" class="mb-16" description="请妥善保管Token，关闭后不会再次展示" />
      <el-input v-model="tokenValue" type="textarea" :rows="6" readonly style="font-family:monospace" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { appAuthApi } from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const tableData = ref([])
const dialogVisible = ref(false)
const tokenDialogVisible = ref(false)
const tokenValue = ref('')
const editForm = ref({ appId: '', appName: '', appSecret: '', allowedTransnos: '' })

async function loadData() {
  const res = await appAuthApi.list()
  tableData.value = res.data || []
}

function showDialog(row) {
  if (row) {
    editForm.value = { ...row }
  } else {
    editForm.value = { appId: '', appName: '', appSecret: '', allowedTransnos: '' }
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (editForm.value.id) {
    await appAuthApi.update(editForm.value.id, editForm.value)
  } else {
    await appAuthApi.create(editForm.value)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function handleGenToken(row) {
  const res = await appAuthApi.generateToken(row.appId)
  tokenValue.value = JSON.stringify(res.data, null, 2)
  tokenDialogVisible.value = true
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除应用 ${row.appName}？`, '删除确认', { type: 'warning' })
  await appAuthApi.delete(row.id)
  ElMessage.success('已删除')
  loadData()
}

onMounted(() => loadData())
</script>

<style scoped>
.mb-16 { margin-bottom: 16px; }
.form-tip { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
