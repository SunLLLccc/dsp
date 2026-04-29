import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/dsp/admin',
  timeout: 30000
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
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
