<template>
  <el-dialog :model-value="modelValue" :title="`请求方列表 - ${transno}`" width="700px"
    @update:model-value="$emit('update:modelValue', $event)">
    <el-table :data="applicantsList" border stripe v-loading="applicantsLoading">
      <el-table-column prop="applicantSystemName" label="请求方系统" width="160" />
      <el-table-column prop="requirementNo" label="需求编号" width="140" />
      <el-table-column prop="applyTime" label="申请时间" width="170" :formatter="fmtTimeCol" />
      <el-table-column prop="applyReason" label="申请事由" show-overflow-tooltip />
    </el-table>
    <el-empty v-if="!applicantsLoading && !applicantsList.length" description="暂无请求方" />
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { relationApi } from '../../api'
import { fmtTime } from '../../utils/format'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  transno: { type: String, default: '' }
})

defineEmits(['update:modelValue'])

const applicantsList = ref([])
const applicantsLoading = ref(false)

function fmtTimeCol(_row, _col, val) {
  return fmtTime(val)
}

async function loadApplicants() {
  applicantsLoading.value = true
  try {
    const res = await relationApi.applicantsByTransno(props.transno)
    applicantsList.value = res.data || []
  } catch {
    applicantsList.value = []
  } finally {
    applicantsLoading.value = false
  }
}

watch(() => props.modelValue, (val) => {
  if (val && props.transno) {
    applicantsList.value = []
    loadApplicants()
  }
})
</script>
