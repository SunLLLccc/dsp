<template>
  <div class="tab-bar">
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
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 16px;
  height: 36px;
  flex-shrink: 0;
  overflow-x: auto;
  white-space: nowrap;
}
.tab-bar::-webkit-scrollbar { height: 0; }
.tab-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 12px;
  height: 28px;
  font-size: 12px;
  color: #666;
  background: #f5f7fa;
  border: 1px solid #e6e6e6;
  border-bottom: none;
  border-radius: 4px 4px 0 0;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s;
  margin-right: 4px;
}
.tab-item:hover { color: #409eff; }
.tab-item.active {
  color: #409eff;
  background: #fff;
  border-color: #e6e6e6;
  border-bottom: 1px solid #fff;
  margin-bottom: -1px;
  font-weight: 500;
}
.tab-label { line-height: 1; }
.tab-close {
  font-size: 12px;
  border-radius: 50%;
  padding: 1px;
  transition: background 0.15s;
}
.tab-close:hover { background: #c0c4cc; color: #fff; }
</style>
