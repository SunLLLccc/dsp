<template>
  <div>
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>{{ isEdit ? '编辑接口' : '新增接口' }}</span>
          <el-button @click="$router.back()">返回</el-button>
        </div>
      </template>

      <!-- 基础信息 -->
      <el-form :model="form" label-width="100px" class="mb-16">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="接口编码" required>
              <el-input v-model="form.transno" placeholder="如 GET_FUND_BALANCE" :disabled="isEdit" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="接口名称" required>
              <el-input v-model="form.name" placeholder="请输入接口名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="协议类型">
              <el-select v-model="form.protocolType">
                <el-option label="HTTP" value="HTTP" />
                <el-option label="DUBBO" value="DUBBO" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="接口描述">
              <el-input v-model="form.description" placeholder="可选" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-divider>XML 配置</el-divider>

      <!-- XML编辑区 -->
      <el-form label-width="100px">
        <el-form-item label="XML配置">
          <el-input v-model="xmlConfig" type="textarea" :rows="18"
            placeholder="请输入XML配置，参考 GET_FUND_BALANCE.xml 格式" style="font-family:monospace" />
        </el-form-item>
        <el-form-item label="变更说明">
          <el-input v-model="changeLog" placeholder="本次修改说明" />
        </el-form-item>
      </el-form>

      <!-- 数据源关联（编辑模式） -->
      <template v-if="isEdit && form.transno">
        <el-divider>数据源关联</el-divider>
        <div style="margin-bottom:12px">
          <el-select v-model="selectedDs" placeholder="选择数据源" style="width:300px;margin-right:10px">
            <el-option v-for="ds in availableDatasources" :key="ds.dsName" :label="`${ds.dsName} (${ds.dsType})`" :value="ds.dsName" />
          </el-select>
          <el-button type="primary" @click="handleAddDs" :disabled="!selectedDs">关联</el-button>
        </div>
        <el-table :data="boundDatasources" border stripe size="small" style="max-width:600px">
          <el-table-column prop="dsName" label="数据源名称" />
          <el-table-column prop="dsType" label="类型" width="100" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button size="small" type="danger" text @click="handleRemoveDs(row.dsName)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 操作按钮 -->
      <div style="text-align:center;margin-top:16px">
        <el-button type="primary" @click="handleSave">保存</el-button>
        <el-button type="success" @click="handleSaveAndPublish" v-if="isEdit">保存并提交发布</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { interfaceApi, datasourceApi, interfaceDatasourceApi } from '../../api'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)

const form = ref({ transno: '', name: '', protocolType: 'HTTP', description: '' })
const xmlConfig = ref('')
const changeLog = ref('')
const selectedDs = ref('')
const availableDatasources = ref([])
const boundDatasources = ref([])

// 默认XML模板
const defaultXml = `<interface transno="" name="">
  <requestData>
    <param name="param1" type="String" required="true"/>
  </requestData>
  <query id="q1" type="mysql" datasource="mysql_main" ref="$requestData">
    SELECT * FROM table_name WHERE column = #{$requestData.param1}
    <if test="param2 != null">AND column2 = #{$requestData.param2}</if>
  </query>
  <resultMap id="result" query="q1">
    <field name="field1" column="column1"/>
  </resultMap>
  <responseData resultMap="result">
    <field name="field1" mapTo="field1"/>
  </responseData>
</interface>`

async function loadDetail() {
  if (!isEdit.value) {
    xmlConfig.value = defaultXml
    return
  }
  const res = await interfaceApi.detail(route.params.id)
  if (res.data) {
    form.value = res.data
    // 加载当前版本的XML配置
    try {
      const verRes = await interfaceApi.getVersion(res.data.transno, res.data.currentVersion)
      xmlConfig.value = verRes.data?.xmlConfig || defaultXml
    } catch {
      xmlConfig.value = defaultXml
    }
    // 加载数据源关联
    loadDatasources(res.data.transno)
  }
}

async function loadDatasources(transno) {
  try {
    const [allRes, boundRes] = await Promise.all([
      datasourceApi.list({ pageNum: 1, pageSize: 100 }),
      interfaceDatasourceApi.list(transno)
    ])
    availableDatasources.value = allRes.data?.records || []
    boundDatasources.value = boundRes.data || []
  } catch (e) {
    // ignore
  }
}

async function handleAddDs() {
  if (!selectedDs.value) return
  try {
    await interfaceDatasourceApi.add(form.value.transno, selectedDs.value)
    ElMessage.success('关联成功')
    selectedDs.value = ''
    loadDatasources(form.value.transno)
  } catch (e) {
    ElMessage.error('关联失败')
  }
}

async function handleRemoveDs(dsName) {
  try {
    await interfaceDatasourceApi.remove(form.value.transno, dsName)
    ElMessage.success('已移除')
    loadDatasources(form.value.transno)
  } catch (e) {
    ElMessage.error('移除失败')
  }
}

async function handleSave() {
  // 保存基础信息
  if (isEdit.value) {
    await interfaceApi.update(route.params.id, form.value)
  } else {
    const res = await interfaceApi.create(form.value)
  }
  // 保存XML配置为新版本
  if (form.value.transno) {
    await interfaceApi.saveXml(form.value.transno, {
      xmlConfig: xmlConfig.value,
      changeLog: changeLog.value,
      operator: 'admin'
    })
  }
  ElMessage.success('保存成功')
}

async function handleSaveAndPublish() {
  await handleSave()
  // 提交发布
  const info = await interfaceApi.detail(route.params.id)
  if (info.data) {
    await interfaceApi.approve(info.data.transno, info.data.currentVersion + 1, { approver: 'admin' })
    ElMessage.success('已发布')
  }
  router.back()
}

onMounted(() => loadDetail())
</script>

<style scoped>
.mb-16 { margin-bottom: 16px; }
</style>
