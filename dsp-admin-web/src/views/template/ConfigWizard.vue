<template>
  <el-dialog :model-value="modelValue" title="配置向导 — 单表查询" width="90%" style="max-width:800px" destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)">
    <el-alert type="info" :closable="false" class="mb-md">
      填写以下表单，自动生成 XML 配置。生成后可切回高级模式微调。
    </el-alert>

    <el-form :model="wizard" label-width="100px">
      <!-- 数据源 -->
      <el-form-item label="数据源" required>
        <el-select v-model="wizard.datasourceName" placeholder="请选择数据源" filterable class="select-full">
          <el-option v-for="ds in datasourceList" :key="ds.id" :label="ds.dsName" :value="ds.dsName" />
        </el-select>
      </el-form-item>

      <!-- 数据源类型 -->
      <el-form-item label="数据源类型">
        <el-select v-model="wizard.datasourceType" class="select-full">
          <el-option label="MySQL" value="mysql" />
          <el-option label="Doris" value="doris" />
          <el-option label="MongoDB" value="mongo" />
        </el-select>
      </el-form-item>

      <!-- SQL 查询 -->
      <el-form-item label="查询SQL" required>
        <el-input v-model="wizard.sql" type="textarea" :rows="5" placeholder="SELECT col1, col2 FROM table WHERE id = #{$requestData['id']}" style="font-family:var(--font-mono)" />
        <div class="form-tip">使用 #{$requestData['参数名']} 引用请求参数</div>
      </el-form-item>

      <!-- 入参 -->
      <el-divider>入参定义</el-divider>
      <div v-for="(p, idx) in wizard.params" :key="idx" class="param-row">
        <el-input v-model="p.name" placeholder="参数名" class="param-input" />
        <el-select v-model="p.type" class="param-type">
          <el-option label="String" value="String" />
          <el-option label="Integer" value="Integer" />
          <el-option label="Long" value="Long" />
          <el-option label="Double" value="Double" />
          <el-option label="Date" value="Date" />
          <el-option label="List" value="List" />
        </el-select>
        <el-checkbox v-model="p.required">必填</el-checkbox>
        <el-input v-model="p.description" placeholder="说明" class="param-desc" />
        <el-button type="danger" size="small" @click="wizard.params.splice(idx, 1)" :icon="Minus" circle />
      </div>
      <el-button size="small" @click="addParam"><el-icon><Plus /></el-icon> 添加入参</el-button>

      <!-- 出参映射 -->
      <el-divider>出参映射</el-divider>
      <div v-for="(f, idx) in wizard.fields" :key="idx" class="param-row">
        <el-input v-model="f.column" placeholder="数据库列名" class="param-input" />
        <el-icon class="arrow-icon"><Right /></el-icon>
        <el-input v-model="f.alias" placeholder="输出字段名(可空,默认同列名)" class="param-input" />
        <el-select v-model="f.function" class="param-func" clearable placeholder="函数">
          <el-option label="无" value="" />
          <el-option label="DATE_FORMAT" value="fn:DATE_FORMAT,yyyy-MM-dd HH:mm:ss" />
          <el-option label="DATE_FORMAT(日期)" value="fn:DATE_FORMAT,yyyy-MM-dd" />
          <el-option label="UPPER" value="UPPER" />
          <el-option label="LOWER" value="LOWER" />
        </el-select>
        <el-button type="danger" size="small" @click="wizard.fields.splice(idx, 1)" :icon="Minus" circle />
      </div>
      <el-button size="small" @click="addField"><el-icon><Plus /></el-icon> 添加映射</el-button>

      <!-- 分页 -->
      <el-divider>分页配置</el-divider>
      <el-form-item label="启用分页">
        <el-switch v-model="wizard.pagination.enabled" />
      </el-form-item>
      <template v-if="wizard.pagination.enabled">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="分页模式">
              <el-select v-model="wizard.pagination.mode" class="select-full">
                <el-option label="游标分页" value="cursor" />
                <el-option label="优化分页" value="optimized" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="排序字段">
              <el-input v-model="wizard.pagination.orderBy" placeholder="如 id" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="每页上限">
              <el-input-number v-model="wizard.pagination.maxPageSize" :min="1" :max="10000" />
            </el-form-item>
          </el-col>
        </el-row>
      </template>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="handleGenerate" :disabled="!canGenerate">生成 XML</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Plus, Minus, Right } from '@element-plus/icons-vue'
import { datasourceApi } from '../../api'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  transno: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'generated'])

const datasourceList = ref([])

const wizard = reactive({
  datasourceName: '',
  datasourceType: 'mysql',
  sql: '',
  params: [],
  fields: [],
  pagination: { enabled: false, mode: 'cursor', orderBy: 'id', maxPageSize: 1000 }
})

// 每次打开时加载数据源列表
watch(() => props.modelValue, async (val) => {
  if (val) {
    try {
      const res = await datasourceApi.list({ pageNum: 1, pageSize: 200 })
      datasourceList.value = res.data?.records || []
    } catch {
      datasourceList.value = []
    }
  }
})

const canGenerate = computed(() => wizard.datasourceName && wizard.sql.trim())

function addParam() {
  wizard.params.push({ name: '', type: 'String', required: false, description: '' })
}

function addField() {
  wizard.fields.push({ column: '', alias: '', function: '' })
}

function handleGenerate() {
  // 校验
  if (!wizard.datasourceName) { ElMessage.warning('请选择数据源'); return }
  if (!wizard.sql.trim()) { ElMessage.warning('请填写查询SQL'); return }
  for (const p of wizard.params) {
    if (!p.name.trim()) { ElMessage.warning('参数名不能为空'); return }
  }
  for (const f of wizard.fields) {
    if (!f.column.trim()) { ElMessage.warning('数据库列名不能为空'); return }
  }

  const xml = buildXml()
  emit('generated', xml)
  emit('update:modelValue', false)
  ElMessage.success('XML 已生成，可在高级模式中继续编辑')
}

function escapeXml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

function buildXml() {
  const transno = props.transno || 'INTERFACE'
  const lines = []

  lines.push(`<interface transno="${escapeXml(transno)}">`)

  // 入参
  if (wizard.params.length > 0) {
    lines.push('  <requestData>')
    for (const p of wizard.params) {
      lines.push(`    <param name="${escapeXml(p.name)}" type="${p.type}" required="${p.required}" description="${escapeXml(p.description)}" />`)
    }
    lines.push('  </requestData>')
  }

  // 数据源
  lines.push(`  <datasource name="${escapeXml(wizard.datasourceName)}" />`)

  // 查询
  const queryAttrs = [`id="q1"`, `type="${wizard.datasourceType}"`, `datasource="${escapeXml(wizard.datasourceName)}"`]
  if (wizard.pagination.enabled) {
    queryAttrs.push(`pagination="${wizard.pagination.mode}"`)
    queryAttrs.push(`order-by="${escapeXml(wizard.pagination.orderBy)}"`)
    queryAttrs.push(`page-size-param="pageSize"`)
    queryAttrs.push(`last-id-param="lastId"`)
    queryAttrs.push(`max-page-size="${wizard.pagination.maxPageSize}"`)
  }
  lines.push(`  <query ${queryAttrs.join(' ')}>`)
  lines.push(`    ${escapeXml(wizard.sql.trim())}`)
  lines.push('  </query>')

  // 结果映射 — name=输出字段名, column=数据库列名（符合 XmlConfigParser 契约）
  if (wizard.fields.length > 0) {
    lines.push('  <resultMap id="resultMap" query="q1">')
    for (const f of wizard.fields) {
      const outputName = f.alias.trim() || f.column.trim()
      const func = f.function ? ` function="${f.function}"` : ''
      lines.push(`    <field name="${escapeXml(outputName)}" column="${escapeXml(f.column.trim())}"${func} />`)
    }
    lines.push('  </resultMap>')
    lines.push('  <responseData resultMap="resultMap">')
    lines.push('    <field name="list" as="list" />')
    lines.push('  </responseData>')
  }

  lines.push('</interface>')
  return lines.join('\n')
}
</script>

<style scoped>
.mb-md { margin-bottom: 16px; }
.select-full { width: 100%; }
.form-tip { font-size: 12px; color: var(--text-secondary); margin-top: 4px; }
.param-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.param-input { width: 160px; flex-shrink: 0; }
.param-type { width: 110px; flex-shrink: 0; }
.param-desc { width: 140px; flex-shrink: 0; }
.param-func { width: 160px; flex-shrink: 0; }
.arrow-icon { color: var(--text-placeholder); flex-shrink: 0; }
</style>
