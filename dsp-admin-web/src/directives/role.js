import { useAuthStore } from '../stores/auth'

/**
 * v-role 指令：根据角色控制元素可见性
 * 用法：v-role="'ADMIN'" 或 v-role="['USER', 'DEPT_MANAGER']"
 * 逻辑：ADMIN 角色始终可见，否则需要匹配任一指定角色
 */
export const roleDirective = {
  mounted(el, binding) {
    const authStore = useAuthStore()
    const required = Array.isArray(binding.value) ? binding.value : [binding.value]
    if (!checkRole(authStore.roles, required)) {
      el.parentNode?.removeChild(el)
    }
  }
}

function checkRole(userRoles, required) {
  if (userRoles.includes('ADMIN')) return true
  return required.some(r => userRoles.includes(r))
}

export function hasAnyRole(required) {
  const authStore = useAuthStore()
  return checkRole(authStore.roles, Array.isArray(required) ? required : [required])
}

export function setupRoleDirective(app) {
  app.directive('role', roleDirective)
}
