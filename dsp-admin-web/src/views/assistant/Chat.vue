<template>
  <div class="chat-page">
    <!-- 左侧会话列表 -->
    <div class="session-panel">
      <div class="session-header">
        <el-button type="primary" :icon="Plus" @click="onNewSession" :loading="creating">
          新建会话
        </el-button>
      </div>
      <div class="session-list" v-loading="store.sessionsLoading">
        <div
          v-for="s in store.sessions"
          :key="s.sessionId"
          class="session-item"
          :class="{ active: s.sessionId === store.currentSessionId }"
          @click="store.selectSession(s.sessionId)"
        >
          <div class="session-title">{{ s.title || '新会话' }}</div>
          <div class="session-time">{{ formatTime(s.updatedTime) }}</div>
          <el-icon class="session-delete" @click.stop="onDelete(s)"><Delete /></el-icon>
        </div>
        <el-empty v-if="store.sessions.length === 0" description="暂无会话" :image-size="60" />
      </div>
    </div>

    <!-- 中间聊天区 -->
    <div class="chat-main">
      <template v-if="store.currentSessionId">
        <!-- 消息列表 -->
        <div class="message-list" ref="messageListRef">
          <div
            v-for="(msg, idx) in displayMessages"
            :key="idx"
            class="message-row"
            :class="msg.role"
          >
            <div class="message-avatar">
              <el-icon v-if="msg.role === 'user'"><User /></el-icon>
              <el-icon v-else><ChatDotRound /></el-icon>
            </div>
            <div class="message-body">
              <div class="message-role">{{ msg.role === 'user' ? '我' : '助手' }}</div>
              <div class="message-content">
                <span v-if="msg.content">{{ msg.content }}</span>
                <span v-else-if="msg.generating" class="typing">正在输入...</span>
              </div>
              <!-- 状态标签 -->
              <el-tag v-if="msg.status === 2" type="danger" size="small" class="status-tag">失败</el-tag>
              <el-tag v-else-if="msg.status === 3" type="warning" size="small" class="status-tag">已取消</el-tag>
              <!-- citations -->
              <CitationsView v-if="msg.citations" :citations="msg.citations" />
            </div>
          </div>
        </div>

        <!-- citations（当前生成中） -->
        <CitationsView
          v-if="store.isGenerating && store.currentCitations.length > 0"
          :citations="store.currentCitations"
        />

        <!-- 输入区 -->
        <div class="input-area">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            placeholder="输入问题，Enter 发送，Shift+Enter 换行"
            resize="none"
            :disabled="store.isGenerating"
            @keydown.enter.exact.prevent="onSend"
          />
          <div class="input-actions">
            <el-button v-if="!store.isGenerating" type="primary" :icon="Promotion" @click="onSend">
              发送
            </el-button>
            <el-button v-else type="danger" :icon="VideoPause" @click="store.cancel">
              取消生成
            </el-button>
          </div>
        </div>
      </template>
      <el-empty v-else description="请选择或新建会话" class="empty-center" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { Plus, Delete, User, ChatDotRound, Promotion, VideoPause } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useAssistantStore } from '../../stores/assistant'
import CitationsView from './CitationsView.vue'

const store = useAssistantStore()
const inputText = ref('')
const creating = ref(false)
const messageListRef = ref(null)

// 展示消息：正式消息 + 生成中的占位 assistant 消息
const displayMessages = computed(() => {
  const list = [...store.messages]
  if (store.isGenerating) {
    list.push({
      role: 'assistant',
      content: store.generatingContent,
      generating: true,
      status: 0
    })
  }
  return list
})

onMounted(() => {
  store.loadSessions()
})

onUnmounted(() => {
  // 组件卸载时取消正在进行的生成，避免悬挂请求
  store.abortIfGenerating()
})

// 新消息时滚动到底部
watch(() => displayMessages.value.length, async () => {
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
})
// 生成中内容变化也滚动
watch(() => store.generatingContent, async () => {
  if (store.isGenerating) {
    await nextTick()
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  }
})

async function onNewSession() {
  creating.value = true
  try {
    await store.createSession('新会话')
  } finally {
    creating.value = false
  }
}

async function onDelete(session) {
  try {
    await ElMessageBox.confirm('确定删除该会话？删除后不可恢复。', '提示', { type: 'warning' })
  } catch (_) {
    return
  }
  await store.deleteSession(session.sessionId)
}

function onSend() {
  const q = inputText.value.trim()
  if (!q || store.isGenerating) {
    return
  }
  inputText.value = ''
  store.ask(q)
}

function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}
</script>

<style scoped>
.chat-page {
  display: flex;
  height: calc(100vh - 60px);
  background: var(--el-bg-color);
}
.session-panel {
  width: 240px;
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  background: var(--el-fill-color-light);
}
.session-header {
  padding: 12px;
}
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
}
.session-item {
  position: relative;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.2s;
}
.session-item:hover {
  background: var(--el-fill-color);
}
.session-item.active {
  background: var(--el-color-primary-light-8);
}
.session-title {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 20px;
}
.session-time {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.session-delete {
  position: absolute;
  right: 8px;
  top: 12px;
  opacity: 0;
  transition: opacity 0.2s;
  color: var(--el-text-color-secondary);
}
.session-item:hover .session-delete {
  opacity: 1;
}
.session-delete:hover {
  color: var(--el-color-danger);
}
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
}
.message-row {
  display: flex;
  margin-bottom: 20px;
  gap: 12px;
}
.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color);
  flex-shrink: 0;
  font-size: 16px;
}
.message-row.user .message-avatar {
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
}
.message-body {
  flex: 1;
  min-width: 0;
}
.message-role {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.message-content {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.typing {
  color: var(--el-text-color-secondary);
}
.status-tag {
  margin-left: 8px;
}
.input-area {
  border-top: 1px solid var(--el-border-color-light);
  padding: 12px 24px;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}
.input-area :deep(.el-textarea__inner) {
  flex: 1;
}
.input-actions {
  flex-shrink: 0;
}
.empty-center {
  margin: auto;
}
</style>
