import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('admin_token') || '')
  const username = ref(localStorage.getItem('admin_username') || '')

  const isAuthenticated = computed(() => !!token.value)

  function setLogin(tokenVal, user) {
    token.value = tokenVal
    username.value = user
    localStorage.setItem('admin_token', tokenVal)
    localStorage.setItem('admin_username', user)
  }

  function logout() {
    token.value = ''
    username.value = ''
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_username')
  }

  return { token, username, isAuthenticated, setLogin, logout }
})
