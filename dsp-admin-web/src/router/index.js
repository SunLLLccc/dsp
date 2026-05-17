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
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/interface',
    children: [
      {
        path: 'interface',
        name: '接口管理',
        component: () => import('../views/interface/List.vue')
      },
      {
        path: 'interface/edit/:id?',
        name: '接口编辑',
        component: () => import('../views/interface/Edit.vue')
      },
      {
        path: 'interface/debug',
        name: '接口调试',
        component: () => import('../views/interface/Debug.vue')
      },
      {
        path: 'template',
        name: 'XML模板管理',
        component: () => import('../views/template/List.vue')
      },
      {
        path: 'datasource',
        name: '数据源管理',
        component: () => import('../views/datasource/List.vue')
      },
      {
        path: 'appauth',
        name: '应用授权',
        component: () => import('../views/appauth/List.vue')
      },
      {
        path: 'export',
        name: '导出管理',
        component: () => import('../views/export/List.vue')
      },
      {
        path: 'audit',
        name: '审计日志',
        component: () => import('../views/audit/List.vue')
      },
      {
        path: 'approval',
        name: '审批管理',
        component: () => import('../views/approval/List.vue')
      },
      {
        path: 'system/user',
        name: '用户管理',
        component: () => import('../views/system/UserList.vue')
      },
      {
        path: 'system/dept',
        name: '部门管理',
        component: () => import('../views/system/DeptList.vue')
      },
      {
        path: 'system/system',
        name: '系统管理',
        component: () => import('../views/system/SystemList.vue')
      },
    ]
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
