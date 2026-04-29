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
        <el-menu-item index="/datasource">
          <el-icon><Coin /></el-icon>
          <span>数据源管理</span>
        </el-menu-item>
        <el-menu-item index="/appauth">
          <el-icon><Key /></el-icon>
          <span>应用授权</span>
        </el-menu-item>
        <el-menu-item index="/export">
          <el-icon><Download /></el-icon>
          <span>导出管理</span>
        </el-menu-item>
        <el-menu-item index="/interface/debug">
          <el-icon><Monitor /></el-icon>
          <span>接口调试</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <el-header class="layout-header">
        <span class="header-title">DSP 管理后台</span>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Document, Coin, Key, Download, Monitor } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.layout-container { height: 100vh; }
.layout-header { background: #fff; border-bottom: 1px solid #e6e6e6; display: flex; align-items: center; }
.header-title { font-size: 16px; font-weight: 600; color: #333; }
.logo { color: #fff; text-align: center; padding: 20px 0; font-size: 16px; font-weight: 700; letter-spacing: 2px; }
.el-aside { background: #304156; }
.el-menu { border-right: none; }
</style>
