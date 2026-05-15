<template>
  <div>
    <el-card>
      <template #header><span>接口调试</span></template>

      <el-form :model="debugForm" label-width="100px">
        <el-form-item label="接口编码" required>
          <el-input v-model="debugForm.transno" placeholder="如 GET_FUND_BALANCE" />
        </el-form-item>
        <el-form-item label="请求参数">
          <el-input v-model="debugForm.paramsJson" type="textarea" :rows="8"
            placeholder='请输入JSON格式参数，如：{"fundCode":"000001"}' style="font-family:var(--font-mono)" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleDebug" :loading="loading">执行调试</el-button>
        </el-form-item>
      </el-form>

      <!-- 调试结果 -->
      <el-divider>调试结果</el-divider>
      <el-input v-model="resultJson" type="textarea" :rows="15" readonly style="font-family:var(--font-mono)"
        v-if="resultJson" />
      <el-empty v-else description="请输入参数后点击执行调试" />
    </el-card>
  </div>
</template>

<script setup>
defineOptions({ name: '接口调试' })

import { ref } from 'vue'
import { interfaceApi } from '../../api'
import { ElMessage } from 'element-plus'

const debugForm = ref({ transno: '', paramsJson: '{}' })
const resultJson = ref('')
const loading = ref(false)

async function handleDebug() {
  if (!debugForm.value.transno) {
    ElMessage.warning('请输入接口编码')
    return
  }
  loading.value = true
  resultJson.value = ''
  try {
    let params = {}
    try {
      params = JSON.parse(debugForm.value.paramsJson || '{}')
    } catch {
      ElMessage.error('参数JSON格式错误')
      return
    }
    const res = await interfaceApi.debug({ transno: debugForm.value.transno, params })
    resultJson.value = JSON.stringify(res, null, 2)
  } catch (e) {
    resultJson.value = JSON.stringify({ error: e.message }, null, 2)
  } finally {
    loading.value = false
  }
}
</script>
