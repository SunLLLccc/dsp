<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside width="220px">
      <div class="logo">DSP 数据服务平台</div>
      <el-menu :default-active="activeMenu" router background-color="#304156" text-color="#bfcbd9" active-text-color="#409EFF">
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
        <el-sub-menu index="system">
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
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <el-header class="layout-header">
        <span class="header-title">DSP 管理后台</span>
        <div class="header-right">
          <span class="header-user">{{ authStore.realName || authStore.username }}</span>
          <el-button text @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { Document, Coin, Key, Download, Monitor, Notebook, Stamp, Files, Setting, User, OfficeBuilding } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const activeMenu = computed(() => route.path)

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout-container { height: 100vh; }
.layout-header { background: #fff; border-bottom: 1px solid #e6e6e6; display: flex; align-items: center; justify-content: space-between; }
.header-title { font-size: 16px; font-weight: 600; color: #333; }
.header-right { display: flex; align-items: center; gap: 12px; }
.header-user { font-size: 14px; color: #666; }
.logo { color: #fff; text-align: center; padding: 20px 0; font-size: 16px; font-weight: 700; letter-spacing: 2px; }
.el-aside { background: #304156; }
.el-menu { border-right: none; }
</style>
