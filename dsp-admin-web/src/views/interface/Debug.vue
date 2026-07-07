<template>
  <div>
    <el-card>
      <template #header><span>接口调试</span></template>

      <el-form :model="debugForm" label-width="100px">
        <el-form-item label="接口编码" required>
          <el-input v-model="debugForm.transno" placeholder="如 GET_FUND_BALANCE" />
        </el-form-item>
        <el-form-item label="请求参数">
          <el-input v-model="debugForm.paramsJson" type="textarea" :rows="6"
            placeholder='请输入JSON格式参数，如：{"fundCode":"000001"}' style="font-family:var(--font-mono)" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleDebug" :loading="loading">执行调试</el-button>
        </el-form-item>
      </el-form>

      <template v-if="debugResult">
        <!-- 错误提示 -->
        <el-alert v-if="debugResult.error" type="error"
          :title="debugResult.error" show-icon :closable="false" class="mb-md" />

        <!-- 执行阶段 -->
        <template v-if="debugResult.trace && debugResult.trace.steps && debugResult.trace.steps.length">
          <el-divider content-position="left">
            执行阶段 · 总耗时 {{ debugResult.trace.totalTimeMs }}ms
          </el-divider>

          <el-table :data="debugResult.trace.steps" border stripe size="small" class="mb-md">
            <el-table-column prop="name" label="阶段" width="160">
              <template #default="{ row }">
                {{ stepLabel(row.name) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="elapsedTimeMs" label="耗时(ms)" width="100" />
            <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
          </el-table>
        </template>

        <!-- 查询跟踪 -->
        <template v-if="debugResult.trace && debugResult.trace.queries && debugResult.trace.queries.length">
          <el-divider content-position="left">
            查询详情 · {{ debugResult.trace.queries.length }} 个查询
          </el-divider>

          <el-table :data="debugResult.trace.queries" border stripe size="small" class="mb-md">
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="trace-expand">
                  <div v-if="row.sql">
                    <strong>SQL:</strong>
                    <pre class="trace-sql">{{ row.sql }}</pre>
                  </div>
                  <div v-if="row.params && row.params.length">
                    <strong>参数:</strong> {{ JSON.stringify(row.params) }}
                  </div>
                  <div v-if="row.paginationMode">
                    <strong>分页模式:</strong> {{ row.paginationMode }}
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="queryId" label="查询ID" width="100" />
            <el-table-column prop="type" label="类型" width="80" />
            <el-table-column prop="datasource" label="数据源" width="120" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="rowCount" label="行数" width="80" />
            <el-table-column prop="elapsedTimeMs" label="耗时(ms)" width="100" />
            <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
          </el-table>
        </template>

        <!-- 调试结果 -->
        <el-divider content-position="left">调试结果</el-divider>
        <el-input v-if="debugResult.data != null"
          :model-value="JSON.stringify(debugResult.data, null, 2)"
          type="textarea" :rows="12" readonly style="font-family:var(--font-mono)" />
      </template>

      <el-empty v-else description="请输入参数后点击执行调试" />
    </el-card>
  </div>
</template>

<script setup>
defineOptions({ name: '接口调试' })

import { ref } from 'vue'
import { interfaceApi } from '../../api'
import { ElMessage } from 'element-plus'

const STEP_LABELS = {
  PARAM_VALIDATE: '参数校验',
  QUERY_EXECUTE: '查询执行',
  RESULT_MAP: '结果映射',
  RESPONSE_BUILD: '响应构建'
}

const debugForm = ref({ transno: '', paramsJson: '{}' })
const debugResult = ref(null)
const loading = ref(false)

function stepLabel(name) {
  return STEP_LABELS[name] || name
}

async function handleDebug() {
  if (!debugForm.value.transno) {
    ElMessage.warning('请输入接口编码')
    return
  }
  loading.value = true
  debugResult.value = null
  try {
    let params = {}
    try {
      params = JSON.parse(debugForm.value.paramsJson || '{}')
    } catch {
      ElMessage.error('参数JSON格式错误')
      return
    }
    const res = await interfaceApi.debug({ transno: debugForm.value.transno, params })
    debugResult.value = res.data
  } catch (e) {
    debugResult.value = { success: false, error: e.message || '网络请求失败', trace: null, data: null }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.mb-md { margin-bottom: 16px; }
.trace-expand { padding: 8px 16px; }
.trace-sql {
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 4px 0;
}
</style>
