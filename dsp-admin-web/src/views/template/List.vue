<template>
  <div>
    <!-- 搜索栏 -->
    <el-card shadow="never" class="card-search">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="接口编码">
          <el-input v-model="searchForm.transno" placeholder="请输入接口编码" clearable />
        </el-form-item>
        <el-form-item label="所属系统">
          <el-input v-model="searchForm.systemName" placeholder="请输入所属系统" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable class="select-fixed">
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
      <div class="mb-md">
        <el-button type="primary" @click="openCreateDialog" v-role="'USER'">新增XML模板</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" border stripe v-loading="loading" empty-text="暂无模板数据">
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
            <el-button size="small" type="primary" @click="openEditDialog(row)" v-role="'USER'">修改</el-button>
            <el-button size="small" @click="showHistory(row)">历史</el-button>
            <el-button size="small" type="success" @click="handlePublish(row)" v-if="row.status === 0" v-role="'DEPT_MANAGER'">发布</el-button>
            <el-button size="small" type="warning" @click="handleOffline(row)" v-if="row.status === 3" v-role="'DEPT_MANAGER'">下线</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="searchForm.pageNum" v-model:page-size="searchForm.pageSize"
          :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 新增/编辑模板弹窗 -->
    <el-dialog v-model="editDialogVisible" :title="editDialogTitle" width="90%" style="max-width:900px" top="5vh" destroy-on-close>
      <el-form label-width="100px" class="mb-md">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="选择接口" required>
              <el-select
                v-model="editForm.transno"
                placeholder="请选择已发布接口"
                filterable
                :disabled="isEditMode"
                @change="handleInterfaceSelect"
                class="select-full"
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

      <div class="xml-section-header">
        <span class="section-title">XML 配置</span>
        <div>
          <el-button size="small" type="warning" @click="wizardVisible = true">
            配置向导
          </el-button>
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
        style="font-family:var(--font-mono)"
      />

      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTemplate">保存</el-button>
        <el-button type="success" @click="handleSaveAndPublish" v-if="isEditMode">保存并发布</el-button>
      </template>
    </el-dialog>

    <!-- XML 查看弹窗 -->
    <el-dialog v-model="viewDialogVisible" :title="`XML配置 - ${viewTransno}`" width="90%" style="max-width:800px">
      <div class="code-view">
        <div class="code-lines"><span v-for="n in viewXmlLineCount" :key="n" class="code-ln">{{ n }}</span></div>
        <pre class="code-text">{{ viewXmlContent }}</pre>
      </div>
    </el-dialog>

    <!-- 历史版本弹窗 -->
    <el-dialog v-model="historyDialogVisible" :title="`历史版本 - ${historyTransno}`" width="90%" style="max-width:900px">
      <el-table :data="historyData" border stripe>
        <el-table-column prop="versionNo" label="版本号" width="80" />
        <el-table-column prop="changeLog" label="变更说明" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="创建人" width="100" />
        <el-table-column prop="createdTime" label="创建时间" width="170" :formatter="fmtTimeCol" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="viewHistoryXml(row)">查看</el-button>
            <el-button size="small" type="primary" @click="compareWithCurrent(row)">对比当前</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- XML 版本对比弹窗 -->
    <XmlCompareDialog
      v-model="xmlCompareVisible"
      :title="`XML 对比 - V${compareVersionNo} vs 当前版本`"
      :left-label="`V${compareVersionNo}`"
      right-label="当前版本"
      :left-xml="compareLeftXml"
      :right-xml="compareRightXml"
    />

    <!-- 配置向导弹窗 -->
    <ConfigWizard
      v-model="wizardVisible"
      :transno="editForm.transno"
      @generated="handleWizardGenerated"
    />
  </div>
</template>

<script setup>
defineOptions({ name: 'XML模板管理' })

import { ref, computed, onMounted } from 'vue'
import { templateApi, interfaceApi } from '../../api'
import { INTERFACE_STATUS, INTERFACE_STATUS_TYPE } from '../../constants/status'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime } from '../../utils/format'
import XmlCompareDialog from '../../components/XmlCompareDialog.vue'
import ConfigWizard from './ConfigWizard.vue'
import { hasAnyRole } from '../../directives/role'

// 列表
const tableData = ref([])
const total = ref(0)
const loading = ref(false)
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
const viewXmlLineCount = computed(() => (viewXmlContent.value || '').split('\n').length)

// 历史弹窗
const historyDialogVisible = ref(false)
const historyTransno = ref('')
const historyData = ref([])

// XML 对比
const xmlCompareVisible = ref(false)
const compareVersionNo = ref('')
const compareLeftXml = ref('')
const compareRightXml = ref('')
const currentTemplateId = ref(null)

// 配置向导
const wizardVisible = ref(false)

function handleWizardGenerated(xml) {
  editForm.value.xmlContent = xml
  hasUndo.value = true
}

async function loadData() {
  loading.value = true
  try {
    const res = await templateApi.list(searchForm.value)
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
    // 全局拦截器已弹出错误
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
  currentTemplateId.value = row.id
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

async function compareWithCurrent(row) {
  compareVersionNo.value = row.versionNo
  compareLeftXml.value = row.xmlContent || ''
  try {
    const res = await templateApi.detail(currentTemplateId.value)
    compareRightXml.value = res.data?.xmlContent || ''
  } catch {
    compareRightXml.value = ''
  }
  xmlCompareVisible.value = true
}

onMounted(() => loadData())
</script>

<style scoped>
.select-fixed { width: 160px; }
.select-full { width: 100%; }

.xml-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-sm);
}

.section-title {
  font-weight: 600;
}

.code-view {
  display: flex;
  max-height: 520px;
  overflow: auto;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-page);
}
.code-lines {
  display: flex;
  flex-direction: column;
  padding: var(--space-sm) 0;
  background: var(--bg-sidebar-hover);
  border-right: 1px solid var(--border-color);
  text-align: right;
  user-select: none;
  flex-shrink: 0;
}
.code-ln {
  display: block;
  padding: 0 var(--space-sm);
  line-height: 1.5;
  font-size: 13px;
  color: var(--text-secondary);
  font-family: var(--font-mono);
}
.code-text {
  margin: 0;
  padding: var(--space-sm) 12px;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.5;
  white-space: pre;
  flex: 1;
  min-width: 0;
}
</style>
