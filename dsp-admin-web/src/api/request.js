import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import router from '../router'

const request = axios.create({
  baseURL: '/dsp/admin',
  timeout: 30000
})

// 请求拦截：附加 Admin-Token
request.interceptors.request.use(config => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers['Admin-Token'] = authStore.token
  }
  return config
})

// 响应拦截
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code && res.code !== '0000') {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    if (error.response) {
      const status = error.response.status
      const data = error.response.data
      if (status === 401) {
        const authStore = useAuthStore()
        authStore.logout()
        router.push('/login')
        ElMessage.error('登录已过期，请重新登录')
      } else if (status === 403) {
        ElMessage.error(data?.message || '无操作权限')
      } else {
        ElMessage.error(data?.message || error.message || '网络错误')
      }
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
