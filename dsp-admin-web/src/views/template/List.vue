<template>
  <div>
    <!-- 搜索栏 -->
    <el-card class="mb-16">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="接口编码">
          <el-input v-model="searchForm.transno" placeholder="请输入接口编码" clearable />
        </el-form-item>
        <el-form-item label="所属系统">
          <el-input v-model="searchForm.systemName" placeholder="请输入所属系统" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width:160px">
            <el-option label="草稿" :value="0" />
            <el-option label="待审批" :value="1" />
            <el-option label="已驳回" :value="2" />
            <el-option label="已发布" :value="3" />
            <el-option label="已下线" :value="4" />
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
        <el-button type="primary" @click="openCreateDialog">新增XML模板</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" border stripe>
        <el-table-column prop="transno" label="接口编码" width="200" />
        <el-table-column prop="interfaceName" label="接口名称" width="180" />
        <el-table-column prop="systemName" label="所属系统" width="150" />
        <el-table-column prop="versionNo" label="版本号" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedTime" label="更新时间" width="180" :formatter="fmtTimeCol" />
        <el-table-column label="操作" fixed="right" width="320">
          <template #default="{ row }">
            <el-button size="small" @click="viewXml(row)">查看</el-button>
            <el-button size="small" type="primary" @click="openEditDialog(row)">修改</el-button>
            <el-button size="small" @click="showHistory(row)">历史</el-button>
            <el-button size="small" type="success" @click="handlePublish(row)" v-if="row.status === 0">发布</el-button>
            <el-button size="small" type="warning" @click="handleOffline(row)" v-if="row.status === 3">下线</el-button>
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

    <!-- 新增/编辑模板弹窗 -->
    <el-dialog v-model="editDialogVisible" :title="editDialogTitle" width="900px" top="5vh" destroy-on-close>
      <el-form label-width="100px" class="mb-16">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="选择接口" required>
              <el-select
                v-model="editForm.transno"
                placeholder="请选择已发布接口"
                filterable
                :disabled="isEditMode"
                @change="handleInterfaceSelect"
                style="width:100%"
              >
                <el-option
                  v-for="item in publishedInterfaces"
                  :key="item.transno"
                  :label="`${item.transno} - ${item.name}`"
                  :value="item.transno"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="接口名称">
              <el-input :model-value="editForm.interfaceName" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所属系统">
              <el-input :model-value="editForm.systemName" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="变更说明">
              <el-input v-model="editForm.changeLog" placeholder="本次修改说明" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
        <span style="font-weight:600">XML 配置</span>
        <div>
          <el-button size="small" type="primary" @click="handleGenerateXml" :disabled="!editForm.transno">
            根据Schema生成
          </el-button>
          <el-button v-if="isEditMode" size="small" @click="handleUndoXml" :disabled="!hasUndo">
            撤销修改
          </el-button>
        </div>
      </div>
      <el-input
        v-model="editForm.xmlContent"
        type="textarea"
        :rows="20"
        placeholder="请输入XML配置"
        style="font-family:monospace"
      />

      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTemplate">保存</el-button>
        <el-button type="success" @click="handleSaveAndPublish" v-if="isEditMode">保存并发布</el-button>
      </template>
    </el-dialog>

    <!-- XML 查看弹窗 -->
    <el-dialog v-model="viewDialogVisible" :title="`XML配置 - ${viewTransno}`" width="800px">
      <el-input type="textarea" :rows="22" :model-value="viewXmlContent" readonly style="font-family:monospace" />
    </el-dialog>

    <!-- 历史版本弹窗 -->
    <el-dialog v-model="historyDialogVisible" :title="`历史版本 - ${historyTransno}`" width="900px">
      <el-table :data="historyData" border stripe>
        <el-table-column prop="versionNo" label="版本号" width="80" />
        <el-table-column prop="changeLog" label="变更说明" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="创建人" width="100" />
        <el-table-column prop="createdTime" label="创建时间" width="170" :formatter="fmtTimeCol" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" @click="viewHistoryXml(row)">查看XML</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { templateApi, interfaceApi } from '../../api'
import { INTERFACE_STATUS, INTERFACE_STATUS_TYPE } from '../../constants/status'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime } from '../../utils/format'

// 列表
const tableData = ref([])
const total = ref(0)
const searchForm = ref({ pageNum: 1, pageSize: 10, transno: '', systemName: '', status: null })

const statusText = (s) => INTERFACE_STATUS[s] || '未知'
const statusType = (s) => INTERFACE_STATUS_TYPE[s] || 'info'

function fmtTimeCol(_row, _col, val) { return fmtTime(val) }

// 编辑弹窗
const editDialogVisible = ref(false)
const isEditMode = ref(false)
const editForm = ref({ transno: '', interfaceName: '', systemName: '', xmlContent: '', changeLog: '' })
const publishedInterfaces = ref([])
const originalXml = ref('')
const hasUndo = ref(false)

const editDialogTitle = computed(() => isEditMode.value ? '修改XML模板' : '新增XML模板')

// 查看弹窗
const viewDialogVisible = ref(false)
const viewXmlContent = ref('')
const viewTransno = ref('')

// 历史弹窗
const historyDialogVisible = ref(false)
const historyTransno = ref('')
const historyData = ref([])

async function loadData() {
  const res = await templateApi.list(searchForm.value)
  tableData.value = res.data?.records || []
  total.value = res.data?.total || 0
}

function resetSearch() {
  searchForm.value = { pageNum: 1, pageSize: 10, transno: '', systemName: '', status: null }
  loadData()
}

async function openCreateDialog() {
  isEditMode.value = false
  editForm.value = { transno: '', interfaceName: '', systemName: '', xmlContent: '', changeLog: '' }
  originalXml.value = ''
  hasUndo.value = false
  // 加载已发布接口列表
  const res = await interfaceApi.list({ pageNum: 1, pageSize: 200, status: 3 })
  publishedInterfaces.value = res.data?.records || []
  editDialogVisible.value = true
}

async function openEditDialog(row) {
  isEditMode.value = true
  const res = await templateApi.detail(row.id)
  if (res.data) {
    editForm.value = {
      id: res.data.id,
      transno: res.data.transno,
      interfaceName: res.data.interfaceName,
      systemName: res.data.systemName,
      xmlContent: res.data.xmlContent || '',
      changeLog: ''
    }
    originalXml.value = res.data.xmlContent || ''
    hasUndo.value = false
  }
  // 也加载已发布接口列表（用于参考）
  const intRes = await interfaceApi.list({ pageNum: 1, pageSize: 200 })
  publishedInterfaces.value = intRes.data?.records || []
  editDialogVisible.value = true
}

async function handleInterfaceSelect(transno) {
  const item = publishedInterfaces.value.find(i => i.transno === transno)
  if (item) {
    editForm.value.interfaceName = item.name
    editForm.value.systemName = item.systemName || ''
  }
  // 自动根据 Schema 生成 XML 框架
  try {
    const res = await templateApi.generate(transno)
    editForm.value.xmlContent = res.data || ''
  } catch {
    // 生成失败，用户可手动填写
  }
}

async function handleGenerateXml() {
  if (!editForm.value.transno) return
  try {
    const res = await templateApi.generate(editForm.value.transno)
    editForm.value.xmlContent = res.data || ''
    ElMessage.success('已根据Schema生成XML框架')
  } catch {
    ElMessage.error('生成失败，请检查接口是否已定义报文')
  }
}

function handleUndoXml() {
  editForm.value.xmlContent = originalXml.value
  hasUndo.value = false
  ElMessage.info('已撤销修改')
}

async function handleSaveTemplate() {
  if (!editForm.value.transno) {
    ElMessage.warning('请选择接口')
    return
  }
  try {
    if (isEditMode.value) {
      await templateApi.update(editForm.value.id, {
        xmlContent: editForm.value.xmlContent,
        changeLog: editForm.value.changeLog,
        operator: 'admin'
      })
    } else {
      await templateApi.create({
        transno: editForm.value.transno,
        xmlContent: editForm.value.xmlContent,
        changeLog: editForm.value.changeLog,
        operator: 'admin'
      })
    }
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    loadData()
  } catch {
    ElMessage.error('保存失败')
  }
}

async function handleSaveAndPublish() {
  await handleSaveTemplate()
  // 找到刚保存的模板
  const res = await templateApi.list({ pageNum: 1, pageSize: 1, transno: editForm.value.transno })
  const template = res.data?.records?.[0]
  if (template) {
    await templateApi.publish(template.id, { operator: 'admin' })
    ElMessage.success('已发布')
    loadData()
  }
}

async function viewXml(row) {
  const res = await templateApi.detail(row.id)
  viewXmlContent.value = res.data?.xmlContent || ''
  viewTransno.value = row.transno
  viewDialogVisible.value = true
}

async function handlePublish(row) {
  await ElMessageBox.confirm(`确认发布模板 ${row.transno}？`, '发布确认')
  await templateApi.publish(row.id, { operator: 'admin' })
  ElMessage.success('发布成功')
  loadData()
}

async function handleOffline(row) {
  await ElMessageBox.confirm(`确认下线模板 ${row.transno}？`, '下线确认')
  await templateApi.offline(row.id)
  ElMessage.success('已下线')
  loadData()
}

async function showHistory(row) {
  historyTransno.value = row.transno
  try {
    const res = await templateApi.historyByTransno(row.transno)
    historyData.value = res.data || []
  } catch {
    historyData.value = []
  }
  historyDialogVisible.value = true
}

function viewHistoryXml(row) {
  viewXmlContent.value = row.xmlContent || ''
  viewTransno.value = `${historyTransno.value} V${row.versionNo}`
  viewDialogVisible.value = true
}

onMounted(() => loadData())
</script>

<style scoped>
.mb-16 { margin-bottom: 16px; }
.mt-16 { margin-top: 16px; }
</style>
