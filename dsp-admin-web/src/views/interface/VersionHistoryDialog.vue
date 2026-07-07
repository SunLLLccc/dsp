<template>
  <el-dialog :model-value="modelValue" :title="`版本历史 - ${transno}`" width="90%" style="max-width:900px"
    @update:model-value="$emit('update:modelValue', $event)">
    <el-table :data="versionData" border stripe>
      <el-table-column prop="versionNo" label="版本号" width="80" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="verStatusType(row.status)">{{ verStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="changeLog" label="变更说明" show-overflow-tooltip />
      <el-table-column prop="createdBy" label="创建人" width="100" />
      <el-table-column prop="createdTime" label="创建时间" width="170" :formatter="fmtTimeCol" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" @click="viewSchema(row)">查看</el-button>
          <el-button size="small" type="primary" @click="compareWithCurrent(row)">对比当前</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination-wrap">
      <el-pagination v-model:current-page="versionPage.pageNum" v-model:page-size="versionPage.pageSize"
        :total="versionTotal" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next"
        @size-change="loadVersions" @current-change="loadVersions" />
    </div>
  </el-dialog>

  <!-- Schema 查看弹窗 -->
  <SchemaViewDialog
    v-model="schemaDialogVisible"
    :title="`Schema - V${schemaVersionNo}`"
    :input-schema="inputSchema"
    :output-schema="outputSchema"
  />

  <!-- Schema 对比弹窗 -->
  <SchemaCompareDialog
    v-model="compareVisible"
    :title="`Schema 对比 - V${compareVersionNo} vs 当前版本`"
    :left-label="`V${compareVersionNo}`"
    right-label="当前版本"
    :left-input="compareLeftInput"
    :left-output="compareLeftOutput"
    :right-input="compareRightInput"
    :right-output="compareRightOutput"
  />
</template>

<script setup>
import { ref, watch } from 'vue'
import { interfaceApi } from '../../api'
import { VERSION_STATUS, VERSION_STATUS_TYPE } from '../../constants/status'
import { fmtTime, formatJson } from '../../utils/format'
import SchemaViewDialog from '../../components/SchemaViewDialog.vue'
import SchemaCompareDialog from '../../components/SchemaCompareDialog.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  transno: { type: String, default: '' }
})

defineEmits(['update:modelValue'])

const verStatusText = (s) => VERSION_STATUS[s] || '未知'
const verStatusType = (s) => VERSION_STATUS_TYPE[s] || 'info'

const versionData = ref([])
const versionTotal = ref(0)
const versionPage = ref({ pageNum: 1, pageSize: 10 })

const schemaDialogVisible = ref(false)
const inputSchema = ref('')
const outputSchema = ref('')
const schemaVersionNo = ref('')

const compareVisible = ref(false)
const compareVersionNo = ref('')
const compareLeftInput = ref('')
const compareLeftOutput = ref('')
const compareRightInput = ref('')
const compareRightOutput = ref('')

function fmtTimeCol(_row, _col, val) {
  return fmtTime(val)
}

async function loadVersions() {
  const res = await interfaceApi.versions(props.transno, versionPage.value)
  versionData.value = res.data?.records || []
  versionTotal.value = res.data?.total || 0
}

async function viewSchema(row) {
  const res = await interfaceApi.getVersion(props.transno, row.versionNo)
  inputSchema.value = formatJson(res.data?.inputSchema)
  outputSchema.value = formatJson(res.data?.outputSchema)
  schemaVersionNo.value = row.versionNo
  schemaDialogVisible.value = true
}

async function compareWithCurrent(row) {
  const histRes = await interfaceApi.getVersion(props.transno, row.versionNo)
  compareLeftInput.value = histRes.data?.inputSchema || ''
  compareLeftOutput.value = histRes.data?.outputSchema || ''
  try {
    const curRes = await interfaceApi.getLatestVersion(props.transno)
    compareRightInput.value = curRes.data?.inputSchema || ''
    compareRightOutput.value = curRes.data?.outputSchema || ''
  } catch {
    compareRightInput.value = ''
    compareRightOutput.value = ''
  }
  compareVersionNo.value = row.versionNo
  compareVisible.value = true
}

watch(() => props.modelValue, (val) => {
  if (val && props.transno) {
    versionPage.value = { pageNum: 1, pageSize: 10 }
    loadVersions()
  }
})
</script>
