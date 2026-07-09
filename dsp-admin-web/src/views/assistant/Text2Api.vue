<template>
  <div class="text2api-page">
    <el-row :gutter="16" class="page-row">
      <!-- 左：草稿列表 -->
      <el-col :xs="24" :md="7" :lg="6">
        <el-card shadow="never" class="draft-panel">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">草稿列表</span>
              <el-button type="primary" size="small" :icon="Plus" @click="handleCreate">新建</el-button>
            </div>
          </template>
          <div v-loading="store.draftsLoading">
            <div v-if="!store.drafts.length" class="empty-tip">暂无草稿</div>
            <div
              v-for="d in store.drafts"
              :key="d.draftId"
              class="draft-item"
              :class="{ active: d.draftId === store.selectedDraftId }"
              @click="store.selectDraft(d.draftId)"
            >
              <div class="draft-item-top">
                <span class="draft-title">{{ stageName(d.stage) }} · {{ d.userName || '-' }}</span>
                <el-tag size="small" :type="stageTagType(d.stage)">{{ stageName(d.stage) }}</el-tag>
              </div>
              <div class="draft-item-meta">
                <span>{{ fmtTime(d.updatedTime) }}</span>
                <span v-if="d.publishError" class="err-dot">有发布错误</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右：当前草稿工作区 -->
      <el-col :xs="24" :md="17" :lg="18">
        <el-card shadow="never" class="workspace-panel">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">Text2API 工作区</span>
              <div v-if="store.currentDraft" class="header-actions">
                <el-button
                  v-if="store.isGenerating"
                  type="warning"
                  size="small"
                  @click="store.cancel"
                >取消生成</el-button>
                <el-button type="danger" size="small" plain :icon="Delete"
                  @click="handleDelete(store.currentDraft.draftId)">删除草稿</el-button>
              </div>
            </div>
          </template>

          <div v-if="!store.currentDraft" class="empty-tip" v-loading="store.draftLoading">
            {{ store.draftLoading ? '' : '请选择或创建草稿' }}
          </div>

          <div v-else v-loading="store.draftLoading">
            <!-- 状态条 -->
            <div class="status-bar">
              <el-tag :type="stageTagType(draft.stage)">阶段 {{ draft.stage }} · {{ stageName(draft.stage) }}</el-tag>
              <el-tag type="info">已确认 {{ stageName(draft.confirmedStage) }}</el-tag>
              <el-tag v-if="draft.invalidatedFromStage" type="warning">
                已回退（自 {{ stageName(draft.invalidatedFromStage) }})
              </el-tag>
              <el-tag v-if="draft.publishError" type="danger">发布失败</el-tag>
            </div>

            <!-- 6 阶段步骤条 -->
            <el-steps :active="draft.stage - 1" finish-status="success" align-center class="stage-steps">
              <el-step title="需求" />
              <el-step title="接口定义" />
              <el-step title="Text2SQL" />
              <el-step title="模板选择" />
              <el-step title="XML/JSON" />
              <el-step title="发布" />
            </el-steps>

            <!-- 生成中状态提示 -->
            <el-alert
              v-if="store.isGenerating"
              :title="`正在生成「${stageName(store.generatingStage)}」...`"
              type="info"
              :closable="false"
              show-icon
            />
            <el-alert
              v-if="store.streamStatus === 'needs_more_info'"
              :title="store.stageMessage || '需要补充更多信息'"
              type="warning"
              :closable="false"
              show-icon
            />
            <el-alert
              v-if="store.streamStatus === 'failed'"
              :title="store.stageMessage || store.error || '生成失败'"
              type="error"
              :closable="false"
              show-icon
            />
            <el-alert
              v-if="draft.publishError"
              :title="'发布错误：' + draft.publishError"
              type="error"
              :closable="false"
              show-icon
            />

            <!-- 阶段产物区 -->
            <el-tabs v-model="activeTab" class="stage-tabs">
              <!-- 阶段 1：需求 -->
              <el-tab-pane label="需求" name="1">
                <el-input
                  v-model="requirementText"
                  type="textarea"
                  :rows="8"
                  placeholder="描述你要生成的接口需求，例如：查询用户列表，支持按姓名模糊搜索、分页"
                  maxlength="30000"
                  show-word-limit
                />
                <div class="stage-actions">
                  <el-upload
                    :show-file-list="false"
                    :auto-upload="false"
                    accept=".md,.html"
                    :on-change="handleFileRead"
                  >
                    <el-button size="small" :icon="Upload">从本地文档读取(.md/.html，&lt;30KB)</el-button>
                  </el-upload>
                  <el-button type="primary" @click="handleSaveRequirement">保存需求</el-button>
                </div>
              </el-tab-pane>

              <!-- 阶段 2：接口定义 -->
              <el-tab-pane label="接口定义" name="2">
                <div class="stage-actions">
                  <el-button type="primary" :loading="isGenStage(2)" :disabled="store.isGenerating"
                    @click="store.generate(2)">生成接口定义</el-button>
                  <el-button :disabled="store.isGenerating" @click="handleConfirm(2)">确认接口定义</el-button>
                </div>
                <div class="artifact-viewer">
                  <div class="artifact-label">接口定义 JSON</div>
                  <pre v-if="draft.interfaceDraft" class="artifact-content">{{ draft.interfaceDraft }}</pre>
                  <div v-else class="artifact-empty">（暂无）</div>
                </div>
              </el-tab-pane>

              <!-- 阶段 3：Text2SQL -->
              <el-tab-pane label="Text2SQL" name="3">
                <el-alert
                  title="硬约束：没有表结构依据时无法生成 SQL，请先填写下方表结构"
                  type="warning"
                  :closable="false"
                  show-icon
                />
                <!-- SchemaEvidence 表单 -->
                <div class="evidence-form">
                  <div class="evidence-form-header">
                    <span class="evidence-label">表结构依据</span>
                    <el-button size="small" :icon="Plus" @click="store.addEvidenceTable">添加表</el-button>
                  </div>
                  <div v-for="(t, idx) in store.schemaEvidence.tables" :key="idx" class="evidence-table">
                    <el-row :gutter="8">
                      <el-col :span="7">
                        <el-input v-model="t.tableName" placeholder="表名，如 users" size="small" />
                      </el-col>
                      <el-col :span="12">
                        <el-input v-model="t.columnsText" placeholder="字段，逗号分隔：id,name,created_time" size="small" />
                      </el-col>
                      <el-col :span="4">
                        <el-input v-model="t.description" placeholder="说明（可选）" size="small" />
                      </el-col>
                      <el-col :span="1">
                        <el-button :icon="Delete" circle size="small" @click="store.removeEvidenceTable(idx)" />
                      </el-col>
                    </el-row>
                  </div>
                  <div v-if="!store.schemaEvidence.tables.length" class="empty-tip small">
                    暂无表结构，点击「添加表」
                  </div>
                  <div v-if="store.schemaEvidence.tables.length && !store.evidenceValid" class="warn-tip">
                    请补全每张表的表名和至少一个字段
                  </div>
                </div>
                <div class="stage-actions">
                  <el-button
                    type="primary"
                    :loading="isGenStage(3)"
                    :disabled="store.isGenerating || !store.evidenceValid"
                    @click="handleGenerateSql"
                  >生成 SQL</el-button>
                  <el-button :disabled="store.isGenerating" @click="handleConfirm(3)">确认 SQL</el-button>
                </div>
                <div class="artifact-viewer">
                  <div class="artifact-label">SQL 草稿 JSON</div>
                  <pre v-if="draft.sqlDraft" class="artifact-content">{{ draft.sqlDraft }}</pre>
                  <div v-else class="artifact-empty">（暂无）</div>
                </div>
              </el-tab-pane>

              <!-- 阶段 4：模板选择 -->
              <el-tab-pane label="模板选择" name="4">
                <div class="stage-actions">
                  <el-button type="primary" :loading="isGenStage(4)" :disabled="store.isGenerating"
                    @click="store.generate(4)">选择模板</el-button>
                  <el-button :disabled="store.isGenerating" @click="handleConfirm(4)">确认模板</el-button>
                </div>
                <div class="artifact-viewer">
                  <div class="artifact-label">模板选择</div>
                  <pre v-if="draft.templateSelection" class="artifact-content">{{ draft.templateSelection }}</pre>
                  <div v-else class="artifact-empty">（暂无）</div>
                </div>
              </el-tab-pane>

              <!-- 阶段 5：XML/JSON -->
              <el-tab-pane label="XML/JSON" name="5">
                <div class="stage-actions">
                  <el-button type="primary" :loading="isGenStage(5)" :disabled="store.isGenerating"
                    @click="store.generate(5)">生成 XML/JSON</el-button>
                  <el-button :disabled="store.isGenerating" @click="handleConfirm(5)">确认 XML/JSON</el-button>
                </div>
                <div class="artifact-viewer">
                  <div class="artifact-label">XML 草稿 <span class="artifact-lang">xml</span></div>
                  <pre v-if="draft.xmlDraft" class="artifact-content">{{ draft.xmlDraft }}</pre>
                  <div v-else class="artifact-empty">（暂无）</div>
                </div>
                <div class="artifact-viewer">
                  <div class="artifact-label">导入 JSON 草稿 <span class="artifact-lang">json</span></div>
                  <pre v-if="draft.importJsonDraft" class="artifact-content">{{ draft.importJsonDraft }}</pre>
                  <div v-else class="artifact-empty">（暂无）</div>
                </div>
              </el-tab-pane>

              <!-- 阶段 6：发布 -->
              <el-tab-pane label="发布" name="6">
                <el-alert
                  v-if="draft.stage === 6"
                  title="该草稿已发布"
                  type="success"
                  :closable="false"
                  show-icon
                />
                <el-alert
                  v-if="draft.publishError"
                  :title="'上次发布失败：' + draft.publishError"
                  type="error"
                  :closable="false"
                  show-icon
                />
                <div class="stage-actions">
                  <el-button
                    type="success"
                    :loading="publishing"
                    @click="handlePublish"
                  >{{ draft.stage === 6 ? '重新发布' : '发布接口' }}</el-button>
                </div>
                <div class="publish-hint">
                  发布将调用导入服务，把接口定义/Schema/XML 写入并发布为正式接口。失败后可重试。
                </div>
              </el-tab-pane>
            </el-tabs>

            <!-- 回退入口 -->
            <div class="rollback-bar">
              <span class="rollback-label">回退到：</span>
              <el-button
                v-for="s in rollbackStages"
                :key="s"
                size="small"
                plain
                :disabled="store.isGenerating || s >= draft.stage"
                @click="handleRollback(s)"
              >{{ stageName(s) }}</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useText2ApiStore } from '../../stores/text2api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Upload } from '@element-plus/icons-vue'
import { fmtTime } from '../../utils/format'

const store = useText2ApiStore()

const requirementText = ref('')
const activeTab = ref('1')
const publishing = ref(false)

const draft = computed(() => store.currentDraft || {})

// 同步需求文本（切换草稿时回填）
watch(() => store.currentDraft, (d) => {
  requirementText.value = d?.requirementText || ''
  activeTab.value = String(d?.stage || 1)
}, { immediate: true })

// ===== 阶段辅助 =====

const STAGE_NAMES = { 1: '需求', 2: '接口定义', 3: 'Text2SQL', 4: '模板选择', 5: 'XML/JSON', 6: '已发布' }
function stageName(s) { return STAGE_NAMES[s] || '-' }
function stageTagType(s) {
  if (s === 6) return 'success'
  if (s >= 5) return 'primary'
  return 'info'
}
const rollbackStages = [2, 3, 4, 5]

function isGenStage(stage) {
  return store.isGenerating && store.generatingStage === stage
}

// ===== 操作 =====

async function handleCreate() {
  await store.createDraft('新草稿')
}

async function handleDelete(draftId) {
  try {
    await ElMessageBox.confirm('确定删除该草稿？删除后不可恢复。', '提示', { type: 'warning' })
  } catch { return }
  await store.deleteDraft(draftId)
  ElMessage.success('已删除')
}

async function handleSaveRequirement() {
  if (!requirementText.value.trim()) {
    ElMessage.warning('需求不能为空')
    return
  }
  await store.updateRequirement(requirementText.value.trim())
  ElMessage.success('已保存')
}

// 阶段 3：直接用 store 的 schemaEvidence（store 内 parseColumns 统一转 columns）
function handleGenerateSql() {
  store.generate(3)
}

async function handleConfirm(stage) {
  try {
    await store.confirmStage(stage)
    ElMessage.success('已确认')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '确认失败')
  }
}

async function handleRollback(stage) {
  try {
    await ElMessageBox.confirm(`确定回退到「${stageName(stage)}」？后续产物将标记失效，需重新确认。`, '回退', { type: 'warning' })
  } catch { return }
  await store.rollbackStage(stage)
  ElMessage.success('已回退')
}

async function handlePublish() {
  try {
    await ElMessageBox.confirm('确定发布该接口？将写入并发布为正式接口。', '发布确认', { type: 'warning' })
  } catch { return }
  publishing.value = true
  try {
    await store.publish()
  } finally {
    publishing.value = false
  }
}

// 阶段 1：本地 FileReader 读取 md/html 填入 textarea（不传后端）
async function handleFileRead(file) {
  const raw = file?.raw
  if (!raw) return
  // 一期只允许 md/html，按后缀校验（accept 不能作为唯一校验）
  const name = (raw.name || '').toLowerCase()
  if (!name.endsWith('.md') && !name.endsWith('.html')) {
    ElMessage.warning('仅支持 .md / .html 格式')
    return
  }
  if (raw.size > 30 * 1024) {
    ElMessage.warning('文件过大，限制 30KB')
    return
  }
  const text = await raw.text()
  requirementText.value = text
  ElMessage.success('已读取文档内容，请保存需求')
}

onMounted(() => {
  store.loadDrafts()
})

onBeforeUnmount(() => {
  // 页面卸载 abort，避免泄漏
  store.abortIfGenerating()
})
</script>

<style scoped>
.text2api-page {
  height: 100%;
}
.page-row {
  height: 100%;
}
.draft-panel, .workspace-panel {
  height: calc(100vh - var(--layout-header-height, 56px) - 80px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.draft-panel :deep(.el-card__body),
.workspace-panel :deep(.el-card__body) {
  overflow-y: auto;
  flex: 1;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.panel-title {
  font-weight: 600;
  font-size: 15px;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.empty-tip {
  color: var(--el-text-color-secondary);
  text-align: center;
  padding: 40px 0;
}
.empty-tip.small {
  padding: 12px 0;
}
.warn-tip {
  color: var(--el-color-warning);
  font-size: 12px;
  margin-top: 6px;
}

/* 草稿项 */
.draft-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid transparent;
  margin-bottom: 6px;
  transition: background 0.15s;
}
.draft-item:hover {
  background: var(--el-fill-color-light);
}
.draft-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}
.draft-item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
}
.draft-title {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.draft-item-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  display: flex;
  justify-content: space-between;
}
.err-dot {
  color: var(--el-color-danger);
}

/* 状态条 + 步骤条 */
.status-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.stage-steps {
  margin-bottom: 16px;
}
.stage-tabs {
  margin-top: 12px;
}
.stage-actions {
  display: flex;
  gap: 8px;
  margin: 12px 0;
  flex-wrap: wrap;
}

/* SchemaEvidence 表单 */
.evidence-form {
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  padding: 12px;
  margin: 12px 0;
}
.evidence-form-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.evidence-label {
  font-weight: 600;
  font-size: 13px;
}
.evidence-table {
  margin-bottom: 8px;
}

/* 回退 */
.rollback-bar {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color);
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.rollback-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* 发布 */
.publish-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 8px;
}

/* 产物展示 */
.artifact-viewer {
  margin: 8px 0 16px;
}
.artifact-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
  color: var(--el-text-color-primary);
}
.artifact-lang {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-left: 6px;
  padding: 1px 5px;
  background: var(--el-fill-color);
  border-radius: 3px;
}
.artifact-content {
  background: var(--el-fill-color-darker);
  color: var(--el-text-color-primary);
  padding: 10px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}
.artifact-empty {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  padding: 8px 0;
}
</style>
