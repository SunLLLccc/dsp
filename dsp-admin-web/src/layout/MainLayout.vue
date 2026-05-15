<template>
  <el-container class="layout-container">
    <!-- 桌面/平板侧边栏 -->
    <el-aside :width="sidebarWidth" class="sidebar-desktop">
      <div class="sidebar-logo">
        <svg class="logo-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="2" y="2" width="20" height="20" rx="4" fill="var(--el-color-primary)"/>
          <path d="M7 8h4v8H7V8zm6 0h4v8h-4V8z" fill="white"/>
        </svg>
        <transition name="logo-fade">
          <span v-show="!collapsed" class="logo-text">DSP</span>
        </transition>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        :collapse="collapsed"
        :collapse-transition="true"
        class="sidebar-menu"
      >
        <el-menu-item index="/interface">
          <el-icon><Document /></el-icon>
          <span>接口管理</span>
        </el-menu-item>
        <el-menu-item index="/template">
          <el-icon><Files /></el-icon>
          <span>XML模板管理</span>
        </el-menu-item>
        <el-menu-item index="/datasource">
          <el-icon><Coin /></el-icon>
          <span>数据源管理</span>
        </el-menu-item>
        <el-menu-item index="/approval">
          <el-icon><Stamp /></el-icon>
          <span>审批管理</span>
        </el-menu-item>
        <el-menu-item index="/appauth">
          <el-icon><Key /></el-icon>
          <span>应用授权</span>
        </el-menu-item>
        <el-menu-item index="/export">
          <el-icon><Download /></el-icon>
          <span>导出管理</span>
        </el-menu-item>
        <el-menu-item index="/audit">
          <el-icon><Notebook /></el-icon>
          <span>审计日志</span>
        </el-menu-item>
        <el-menu-item index="/interface/debug">
          <el-icon><Monitor /></el-icon>
          <span>接口调试</span>
        </el-menu-item>
        <el-sub-menu index="system" v-role="'ADMIN'">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/system/user">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/system/dept">
            <el-icon><OfficeBuilding /></el-icon>
            <span>部门管理</span>
          </el-menu-item>
          <el-menu-item index="/system/system">
            <el-icon><Monitor /></el-icon>
            <span>系统管理</span>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/application">
          <el-icon><Promotion /></el-icon>
          <span>接口申请</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 手机端抽屉侧边栏 -->
    <el-drawer
      v-model="drawerVisible"
      direction="ltr"
      :with-header="false"
      size="220px"
      class="sidebar-drawer"
    >
      <div class="sidebar-logo">
        <svg class="logo-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="2" y="2" width="20" height="20" rx="4" fill="var(--el-color-primary)"/>
          <path d="M7 8h4v8H7V8zm6 0h4v8h-4V8z" fill="white"/>
        </svg>
        <span class="logo-text">DSP</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        :collapse="false"
        class="sidebar-menu"
        @select="onMenuSelect"
      >
        <el-menu-item index="/interface">
          <el-icon><Document /></el-icon>
          <span>接口管理</span>
        </el-menu-item>
        <el-menu-item index="/template">
          <el-icon><Files /></el-icon>
          <span>XML模板管理</span>
        </el-menu-item>
        <el-menu-item index="/datasource">
          <el-icon><Coin /></el-icon>
          <span>数据源管理</span>
        </el-menu-item>
        <el-menu-item index="/approval">
          <el-icon><Stamp /></el-icon>
          <span>审批管理</span>
        </el-menu-item>
        <el-menu-item index="/appauth">
          <el-icon><Key /></el-icon>
          <span>应用授权</span>
        </el-menu-item>
        <el-menu-item index="/export">
          <el-icon><Download /></el-icon>
          <span>导出管理</span>
        </el-menu-item>
        <el-menu-item index="/audit">
          <el-icon><Notebook /></el-icon>
          <span>审计日志</span>
        </el-menu-item>
        <el-menu-item index="/interface/debug">
          <el-icon><Monitor /></el-icon>
          <span>接口调试</span>
        </el-menu-item>
        <el-sub-menu index="system" v-role="'ADMIN'">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/system/user">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/system/dept">
            <el-icon><OfficeBuilding /></el-icon>
            <span>部门管理</span>
          </el-menu-item>
          <el-menu-item index="/system/system">
            <el-icon><Monitor /></el-icon>
            <span>系统管理</span>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/application">
          <el-icon><Promotion /></el-icon>
          <span>接口申请</span>
        </el-menu-item>
      </el-menu>
    </el-drawer>

    <!-- 主内容区 -->
    <el-container class="main-container">
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="hamburger-btn" @click="drawerVisible = true"><Expand /></el-icon>
          <span class="header-title">{{ currentTitle }}</span>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="handleUserCommand">
            <span class="user-info">
              <el-avatar :size="28" class="user-avatar">
                {{ (authStore.realName || authStore.username || '?').charAt(0) }}
              </el-avatar>
              <span class="user-name">{{ authStore.realName || authStore.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <TabBar />
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <keep-alive :include="tabStore.cachedNames" :max="15">
              <component :is="Component" />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useTabStore } from '../stores/tabs'
import {
  Document, Coin, Key, Download, Monitor, Notebook, Stamp, Files,
  Setting, User, OfficeBuilding, Promotion, Expand, ArrowDown, SwitchButton
} from '@element-plus/icons-vue'
import TabBar from './TabBar.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const tabStore = useTabStore()

const collapsed = ref(false)
const drawerVisible = ref(false)

const activeMenu = computed(() => {
  if (route.path.startsWith('/interface/edit')) return '/interface'
  if (route.path.startsWith('/system/')) return route.path
  return route.path
})

const currentTitle = computed(() => route.name || 'DSP 管理后台')

const sidebarWidth = computed(() =>
  collapsed.value ? 'var(--layout-sidebar-collapsed-width)' : 'var(--layout-sidebar-width)'
)

watch(() => route.fullPath, () => {
  if (route.name && route.path !== '/login') {
    tabStore.addTab(route)
  }
}, { immediate: true })

function onMenuSelect() {
  drawerVisible.value = false
}

function handleUserCommand(command) {
  if (command === 'logout') {
    authStore.logout()
    router.push('/login')
  }
}

function handleResize() {
  const width = window.innerWidth
  if (width < 768) {
    collapsed.value = false
  } else if (width < 1200) {
    collapsed.value = true
  } else {
    collapsed.value = false
  }
}

onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
  overflow: hidden;
}

.sidebar-desktop {
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width var(--transition-slow);
}

.sidebar-logo {
  height: var(--layout-header-height);
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 10px;
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
  overflow: hidden;
}

.logo-icon {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-color-primary);
  letter-spacing: 2px;
  white-space: nowrap;
}

.logo-fade-enter-active,
.logo-fade-leave-active {
  transition: opacity 0.2s ease;
}

.logo-fade-enter-from,
.logo-fade-leave-to {
  opacity: 0;
}

.sidebar-menu {
  border-right: none;
  padding: 8px;
  flex: 1;
  overflow-y: auto;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: calc(var(--layout-sidebar-width) - 16px);
}

.sidebar-menu :deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  border-radius: var(--radius-sm);
  margin-bottom: 2px;
  color: var(--text-regular);
  transition: all var(--transition-fast);
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: var(--bg-sidebar-hover);
  color: var(--el-color-primary);
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: var(--bg-sidebar-active);
  color: var(--el-color-primary);
  font-weight: 600;
  position: relative;
}

.sidebar-menu :deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  background: var(--el-color-primary);
  border-radius: 0 2px 2px 0;
}

.sidebar-menu :deep(.el-sub-menu .el-sub-menu__title) {
  height: 40px;
  line-height: 40px;
  border-radius: var(--radius-sm);
  color: var(--text-regular);
}

.sidebar-menu :deep(.el-sub-menu .el-sub-menu__title:hover) {
  background: var(--bg-sidebar-hover);
  color: var(--el-color-primary);
}

:deep(.sidebar-drawer .el-drawer__body) {
  padding: 0;
  background: var(--bg-sidebar);
}

.main-container {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.layout-header {
  height: var(--layout-header-height);
  background: var(--bg-header);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.hamburger-btn {
  font-size: 20px;
  cursor: pointer;
  color: var(--text-regular);
  padding: 4px;
  border-radius: var(--radius-sm);
  transition: all var(--transition-fast);
}

.hamburger-btn:hover {
  background: var(--bg-hover);
  color: var(--el-color-primary);
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast);
}

.user-info:hover {
  background: var(--bg-hover);
}

.user-avatar {
  background: var(--el-color-primary-light-5);
  color: white;
  font-size: 13px;
  font-weight: 600;
}

.user-name {
  font-size: 14px;
  color: var(--text-regular);
}

.layout-main {
  background: var(--bg-page);
  padding: 20px;
  overflow-y: auto;
}

@media (min-width: 1200px) {
  .hamburger-btn {
    display: none;
  }
}

@media (min-width: 768px) and (max-width: 1199px) {
  .hamburger-btn {
    display: none;
  }
  .sidebar-desktop {
    width: var(--layout-sidebar-collapsed-width) !important;
  }
  .sidebar-logo {
    justify-content: center;
    padding: 0;
  }
}

@media (max-width: 767px) {
  .sidebar-desktop {
    display: none;
  }
  .layout-header {
    padding: 0 12px;
  }
  .user-name {
    display: none;
  }
  .layout-main {
    padding: 12px;
  }
}
</style>
