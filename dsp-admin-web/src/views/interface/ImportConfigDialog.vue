<template>
  <el-dialog :model-value="modelValue" title="导入配置" width="90%" style="max-width:600px" destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)">
    <el-alert type="info" :closable="false" class="mb-md">
      请选择从测试环境导出的 JSON 文件。导入时接口信息、Schema和模板XML会创建新版本。
    </el-alert>
    <el-upload
      ref="uploadRef"
      :auto-upload="false"
      :limit="1"
      accept=".json"
      :on-change="handleFileChange"
      :on-remove="() => importFileData = null"
      drag
    >
      <el-icon class="upload-icon"><UploadFilled /></el-icon>
      <div>拖拽文件到此处，或<em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">仅支持 .json 文件</div>
      </template>
    </el-upload>
    <el-form class="mt-md" v-if="importPreview.length">
      <el-form-item label="变更说明">
        <el-input v-model="importChangeLog" placeholder="导入说明" />
      </el-form-item>
      <el-form-item label="待导入接口">
        <div v-for="item in importPreview" :key="item.transno" class="import-preview-item">
          <el-tag>{{ item.transno }}</el-tag>
          <span class="ml-8">{{ item.name }}</span>
          <el-tag v-if="item.exists" type="warning" size="small" class="ml-8">已存在(将创建新版本)</el-tag>
          <el-tag v-else type="success" size="small" class="ml-8">新建</el-tag>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="handleImport" :disabled="!importFileData" :loading="importing">
        确认导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { configApi } from '../../api'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  existingTransnos: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'imported'])

const importFileData = ref(null)
const importPreview = ref([])
const importChangeLog = ref('从测试环境导入')
const importing = ref(false)
const uploadRef = ref(null)

// 每次打开弹窗时重置内部状态，行为与原 openImportDialog() 一致
watch(() => props.modelValue, (val) => {
  if (val) {
    importFileData.value = null
    importPreview.value = []
    importChangeLog.value = '从测试环境导入'
    importing.value = false
    // el-dialog destroy-on-close 会销毁子组件，uploadRef 会在下次挂载时自动清空
  }
})

function handleFileChange(file) {
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const data = JSON.parse(e.target.result)
      const configs = data.configs || [data]
      importFileData.value = configs
      importPreview.value = configs.map(c => ({
        transno: c.interfaceInfo?.transno || '',
        name: c.interfaceInfo?.name || '',
        exists: props.existingTransnos.some(t => t === c.interfaceInfo?.transno)
      }))
    } catch {
      ElMessage.error('JSON文件解析失败')
      importFileData.value = null
    }
  }
  reader.readAsText(file.raw)
}

async function handleImport() {
  if (!importFileData.value) return
  importing.value = true
  let successCount = 0
  let failCount = 0
  for (const config of importFileData.value) {
    try {
      config.changeLog = importChangeLog.value
      await configApi.importConfig(config)
      successCount++
    } catch {
      failCount++
    }
  }
  importing.value = false
  emit('update:modelValue', false)
  ElMessage.success(`导入完成：成功 ${successCount} 个${failCount ? '，失败 ' + failCount + ' 个' : ''}`)
  emit('imported')
}
</script>

<style scoped>
.mb-md { margin-bottom: 16px; }
.mt-md { margin-top: 16px; }
.upload-icon { font-size: 40px; color: var(--text-placeholder); }
.ml-8 { margin-left: 8px; }
.import-preview-item { margin-bottom: 4px; display: flex; align-items: center; }
</style>
