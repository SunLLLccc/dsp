<template>
  <el-container class="layout-container">
    <!-- 桌面/平板侧边栏 -->
    <el-aside :width="sidebarWidth" class="sidebar-desktop">
      <div class="sidebar-logo">
        <div class="logo-icon-wrap">
          <svg class="logo-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="2" y="2" width="20" height="20" rx="4" fill="var(--el-color-primary)"/>
            <path d="M7 8h4v8H7V8zm6 0h4v8h-4V8z" fill="white"/>
          </svg>
        </div>
        <span v-show="!collapsed" class="logo-text">DSP</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        :collapse="collapsed"
        :collapse-transition="true"
        background-color="transparent"
        text-color="var(--sidebar-text)"
        active-text-color="var(--sidebar-text-active)"
        class="sidebar-menu"
      >
        <el-menu-item index="/interface">
          <el-icon><Document /></el-icon>
          <span>接口管理</span>
        </el-menu-item>
        <el-menu-item index="/marketplace">
          <el-icon><Promotion /></el-icon>
          <span>接口市场</span>
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
        <el-menu-item index="/assistant">
          <el-icon><ChatLineRound /></el-icon>
          <span>智能助手</span>
        </el-menu-item>
        <el-menu-item index="/assistant/text2api">
          <el-icon><MagicStick /></el-icon>
          <span>Text2API</span>
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
        <div class="logo-icon-wrap">
          <svg class="logo-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="2" y="2" width="20" height="20" rx="4" fill="var(--el-color-primary)"/>
            <path d="M7 8h4v8H7V8zm6 0h4v8h-4V8z" fill="white"/>
          </svg>
        </div>
        <span class="logo-text">DSP</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        :collapse="false"
        background-color="transparent"
        text-color="var(--sidebar-text)"
        active-text-color="var(--sidebar-text-active)"
        class="sidebar-menu"
        @select="onMenuSelect"
      >
        <el-menu-item index="/interface">
          <el-icon><Document /></el-icon>
          <span>接口管理</span>
        </el-menu-item>
        <el-menu-item index="/marketplace">
          <el-icon><Promotion /></el-icon>
          <span>接口市场</span>
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
        <el-menu-item index="/assistant">
          <el-icon><ChatLineRound /></el-icon>
          <span>智能助手</span>
        </el-menu-item>
        <el-menu-item index="/assistant/text2api">
          <el-icon><MagicStick /></el-icon>
          <span>Text2API</span>
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
      </el-menu>
    </el-drawer>

    <!-- 主内容区 -->
    <el-container class="main-container">
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="hamburger-btn" @click="drawerVisible = true"><Expand /></el-icon>
          <span class="header-brand">数据服务平台</span>
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
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>
                  个人信息
                </el-dropdown-item>
                <el-dropdown-item command="password">
                  <el-icon><Lock /></el-icon>
                  修改密码
                </el-dropdown-item>
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
    <el-dialog v-model="profileDialogVisible" title="个人信息" width="500px" destroy-on-close>
      <el-descriptions :column="1" border v-if="profileData">
        <el-descriptions-item label="用户名">{{ profileData.username }}</el-descriptions-item>
        <el-descriptions-item label="所属部门">{{ profileData.deptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag v-for="rn in profileData.roleNames" :key="rn" size="small" style="margin-right:4px">{{ rn }}</el-tag>
          <span v-if="!profileData.roleNames || !profileData.roleNames.length">-</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="profileData.status === 1 ? 'success' : 'danger'">
            {{ profileData.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmtTime(profileData.createdTime) }}</el-descriptions-item>
      </el-descriptions>
      <el-form style="margin-top:16px" v-if="profileData">
        <el-form-item label="真实姓名">
          <el-input v-model="profileRealName" placeholder="请输入真实姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveProfile" :loading="profileSaving">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdDialogVisible" title="修改密码" width="450px" destroy-on-close>
      <el-form :model="pwdForm" label-width="90px">
        <el-form-item label="旧密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少6位" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleChangePwd" :loading="pwdSaving">确认修改</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useTabStore } from '../stores/tabs'
import {
  Document, Coin, Key, Download, Monitor, Notebook, Stamp, Files,
  Setting, User, OfficeBuilding, Promotion, Expand, ArrowDown, SwitchButton, Lock, ChatLineRound, MagicStick
} from '@element-plus/icons-vue'
import TabBar from './TabBar.vue'
import { userApi } from '../api'
import { fmtTime } from '../utils/format'
import { ElMessage } from 'element-plus'
import { loadStatusEnums } from '../constants/status'

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
  } else if (command === 'profile') {
    openProfileDialog()
  } else if (command === 'password') {
    pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    pwdDialogVisible.value = true
  }
}

const profileDialogVisible = ref(false)
const profileData = ref(null)
const profileRealName = ref('')
const profileSaving = ref(false)

async function openProfileDialog() {
  profileDialogVisible.value = true
  try {
    const res = await userApi.profile()
    profileData.value = res.data
    profileRealName.value = res.data?.realName || ''
  } catch {
    profileData.value = null
  }
}

async function handleSaveProfile() {
  if (!profileRealName.value.trim()) {
    ElMessage.warning('真实姓名不能为空')
    return
  }
  profileSaving.value = true
  try {
    await userApi.updateProfile({ realName: profileRealName.value.trim() })
    authStore.updateRealName(profileRealName.value.trim())
    ElMessage.success('保存成功')
    profileDialogVisible.value = false
  } catch {
    ElMessage.error('保存失败')
  } finally {
    profileSaving.value = false
  }
}

const pwdDialogVisible = ref(false)
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdSaving = ref(false)

async function handleChangePwd() {
  const form = pwdForm.value
  if (!form.oldPassword) { ElMessage.warning('请输入旧密码'); return }
  if (!form.newPassword || form.newPassword.length < 6) { ElMessage.warning('新密码至少6位'); return }
  if (form.newPassword !== form.confirmPassword) { ElMessage.warning('两次输入的密码不一致'); return }
  pwdSaving.value = true
  try {
    await userApi.changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    pwdDialogVisible.value = false
    authStore.logout()
    router.push('/login')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '修改失败')
  } finally {
    pwdSaving.value = false
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
  loadStatusEnums()
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

/* 深色侧边栏 */
.sidebar-desktop {
  background: var(--bg-sidebar);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width var(--transition-slow);
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
}

.sidebar-logo {
  height: 80px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 12px;
  background: var(--sidebar-logo-bg);
  flex-shrink: 0;
  overflow: hidden;
  border-bottom: 1px solid var(--sidebar-border);
}

.logo-icon-wrap {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-icon {
  width: 32px;
  height: 32px;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: #1D2939;
  letter-spacing: 3px;
  white-space: nowrap;
}

/* 菜单 */
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
  height: 42px;
  line-height: 42px;
  border-radius: var(--radius-sm);
  margin-bottom: 2px;
  transition: all var(--transition-fast);
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: var(--bg-sidebar-hover) !important;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: var(--bg-sidebar-active) !important;
  color: var(--sidebar-text-active) !important;
  font-weight: 600;
}

.sidebar-menu :deep(.el-sub-menu .el-sub-menu__title) {
  height: 42px;
  line-height: 42px;
  border-radius: var(--radius-sm);
}

.sidebar-menu :deep(.el-sub-menu .el-sub-menu__title:hover) {
  background: var(--bg-sidebar-hover) !important;
}

/* 手机端抽屉 */
:deep(.sidebar-drawer .el-drawer__body) {
  padding: 0;
  background: var(--bg-sidebar);
}

/* 主容器 */
.main-container {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 顶栏 — 三栏绝对居中 */
.layout-header {
  height: var(--layout-header-height);
  background: var(--bg-header);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  position: relative;
  padding: 0 20px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
  z-index: 1;
}

.header-brand {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 1px;
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
  margin-left: auto;
  position: relative;
  z-index: 1;
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
  background: var(--el-color-primary);
  color: white;
  font-size: 13px;
  font-weight: 600;
}

.user-name {
  font-size: 14px;
  color: var(--text-regular);
}

/* 内容区 */
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
    height: 64px;
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
  .header-center {
    display: none;
  }
  .layout-main {
    padding: 12px;
  }
}
</style>
