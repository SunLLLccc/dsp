<template>
  <div class="tab-bar" v-if="tabStore.tabs.length > 1">
    <div
      v-for="tab in tabStore.tabs"
      :key="tab.fullPath"
      :class="['tab-item', { active: tab.fullPath === tabStore.activeTab }]"
      @click="switchTab(tab)"
    >
      <span class="tab-label">{{ tab.name }}</span>
      <el-icon
        v-if="tab.fullPath !== '/interface'"
        class="tab-close"
        @click.stop="closeTab(tab)"
      >
        <Close />
      </el-icon>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useTabStore } from '../stores/tabs'
import { Close } from '@element-plus/icons-vue'

const router = useRouter()
const tabStore = useTabStore()

function switchTab(tab) {
  router.push(tab.fullPath)
}

function closeTab(tab) {
  const isActive = tab.fullPath === tabStore.activeTab
  tabStore.removeTab(tab.fullPath)
  if (isActive) {
    router.push(tabStore.activeTab)
  }
}
</script>

<style scoped>
.tab-bar {
  display: flex;
  align-items: center;
  background: var(--bg-header);
  border-bottom: 1px solid var(--border-color);
  padding: 0 20px;
  height: var(--layout-tabbar-height);
  flex-shrink: 0;
  overflow-x: auto;
  white-space: nowrap;
  gap: 6px;
}

.tab-bar::-webkit-scrollbar {
  height: 0;
}

.tab-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 14px;
  height: 28px;
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--border-light);
  border-radius: 14px;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s ease;
  user-select: none;
}

.tab-item:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.tab-item.active {
  color: white;
  background: var(--el-color-primary);
  font-weight: 500;
  box-shadow: 0 2px 6px rgba(76, 110, 245, 0.3);
}

.tab-label {
  line-height: 1;
}

.tab-close {
  font-size: 10px;
  border-radius: 50%;
  width: 14px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}

.tab-item:not(.active) .tab-close:hover {
  background: rgba(0, 0, 0, 0.1);
}

.tab-item.active .tab-close:hover {
  background: rgba(255, 255, 255, 0.3);
  color: white;
}

@media (max-width: 767px) {
  .tab-bar {
    padding: 0 12px;
  }
  .tab-item {
    padding: 0 10px;
    font-size: 11px;
  }
}
</style>
