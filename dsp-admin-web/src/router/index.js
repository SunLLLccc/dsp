import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  {
    path: '/login',
    name: '登录',
    component: () => import('../views/login/Login.vue')
  },
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
  },
  {
    path: '/audit',
    name: '审计日志',
    component: () => import('../views/audit/List.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  if (to.path !== '/login' && !authStore.isAuthenticated) {
    next('/login')
  } else if (to.path === '/login' && authStore.isAuthenticated) {
    next('/')
  } else {
    next()
  }
})

export default router
