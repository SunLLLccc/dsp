import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('admin_token') || '')
  const username = ref(localStorage.getItem('admin_username') || '')
  const realName = ref(localStorage.getItem('admin_realname') || '')
  const roles = ref(JSON.parse(localStorage.getItem('admin_roles') || '[]'))
  const deptId = ref(localStorage.getItem('admin_deptid') || '')

  const isAuthenticated = computed(() => !!token.value)

  function setLogin({ token: tokenVal, username: user, realName: name, roles: roleList, deptId: dept }) {
    token.value = tokenVal
    username.value = user
    realName.value = name || ''
    roles.value = roleList || []
    deptId.value = dept || ''
    localStorage.setItem('admin_token', tokenVal)
    localStorage.setItem('admin_username', user)
    localStorage.setItem('admin_realname', name || '')
    localStorage.setItem('admin_roles', JSON.stringify(roleList || []))
    localStorage.setItem('admin_deptid', dept || '')
  }

  function logout() {
    token.value = ''
    username.value = ''
    realName.value = ''
    roles.value = []
    deptId.value = ''
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_username')
    localStorage.removeItem('admin_realname')
    localStorage.removeItem('admin_roles')
    localStorage.removeItem('admin_deptid')
  }

  function hasRole(roleCode) {
    return roles.value.includes(roleCode)
  }

  function updateRealName(name) {
    realName.value = name
    localStorage.setItem('admin_realname', name || '')
  }

  return { token, username, realName, roles, deptId, isAuthenticated, setLogin, logout, hasRole, updateRealName }
})
