import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  {
    path: '/login',
    name: '登录',
    component: () => import('../views/login/Login.vue')
  },
  {
    path: '/403',
    name: '无权限',
    component: () => import('../views/error/Forbidden.vue')
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
        path: 'marketplace',
        name: '接口市场',
        component: () => import('../views/marketplace/List.vue')
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
        path: 'assistant',
        name: '智能助手',
        component: () => import('../views/assistant/Chat.vue')
      },
      {
        path: 'assistant/text2api',
        name: 'Text2API',
        component: () => import('../views/assistant/Text2Api.vue')
      },
      {
        path: 'approval',
        name: '审批管理',
        component: () => import('../views/approval/List.vue')
      },
      {
        path: 'system/user',
        name: '用户管理',
        component: () => import('../views/system/UserList.vue'),
        meta: { roles: ['ADMIN'] }
      },
      {
        path: 'system/dept',
        name: '部门管理',
        component: () => import('../views/system/DeptList.vue'),
        meta: { roles: ['ADMIN'] }
      },
      {
        path: 'system/system',
        name: '系统管理',
        component: () => import('../views/system/SystemList.vue'),
        meta: { roles: ['ADMIN'] }
      },
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: '未找到',
    component: () => import('../views/error/Forbidden.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 角色校验：ADMIN 绕过所有检查，否则需匹配 meta.roles 中的任一角色
 */
function hasRequiredRole(userRoles, requiredRoles) {
  if (!requiredRoles || requiredRoles.length === 0) return true
  if (userRoles.includes('ADMIN')) return true
  return requiredRoles.some(r => userRoles.includes(r))
}

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  // 未登录：只允许访问 /login
  if (!authStore.isAuthenticated && to.path !== '/login') {
    next('/login')
    return
  }

  // 已登录访问 /login：跳转首页
  if (to.path === '/login' && authStore.isAuthenticated) {
    next('/')
    return
  }

  // 角色校验：meta.roles 指定所需角色，ADMIN 全部绕过
  if (to.meta && to.meta.roles && !hasRequiredRole(authStore.roles, to.meta.roles)) {
    next('/403')
    return
  }

  next()
})

export default router
