import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/interface'
  },
  {
    path: '/interface',
    name: '接口管理',
    component: () => import('../views/interface/List.vue')
  },
  {
    path: '/interface/edit/:id?',
    name: '接口编辑',
    component: () => import('../views/interface/Edit.vue')
  },
  {
    path: '/interface/debug',
    name: '接口调试',
    component: () => import('../views/interface/Debug.vue')
  },
  {
    path: '/datasource',
    name: '数据源管理',
    component: () => import('../views/datasource/List.vue')
  },
  {
    path: '/appauth',
    name: '应用授权',
    component: () => import('../views/appauth/List.vue')
  },
  {
    path: '/export',
    name: '导出管理',
    component: () => import('../views/export/List.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
