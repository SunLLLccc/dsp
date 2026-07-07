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

/**
 * Blob 下载：携带 Admin-Token，解析 Content-Disposition 文件名，处理 401/403 错误
 * @param {string} url - 请求路径（不含 baseURL 前缀）
 * @param {string} defaultFilename - 后端未返回文件名时的默认值
 */
export async function downloadFile(url, defaultFilename = 'export.xlsx') {
  const authStore = useAuthStore()

  try {
    const response = await axios.get(url, {
      baseURL: '/dsp/admin',
      responseType: 'blob',
      headers: { 'Admin-Token': authStore.token || '' }
    })

    // 防御：后端返回 200 但 content-type 为 JSON（业务错误）
    const contentType = response.headers['content-type'] || ''
    if (contentType.includes('application/json')) {
      const text = await response.data.text()
      try {
        const error = JSON.parse(text)
        ElMessage.error(error.message || '下载失败')
      } catch (_) {
        ElMessage.error('下载失败')
      }
      return
    }

    // 从 Content-Disposition 提取文件名
    let filename = defaultFilename
    const disposition = response.headers['content-disposition']
    if (disposition) {
      // RFC 5987: filename*=UTF-8''xxx
      let match = disposition.match(/filename\*=UTF-8''(.+)/i)
      if (match) {
        filename = decodeURIComponent(match[1])
      } else {
        // 标准: filename="xxx" 或 filename=xxx
        match = disposition.match(/filename[^;=\n]*=["']?([^"';\n]+)/)
        if (match) {
          filename = match[1]
          try { filename = decodeURIComponent(filename) } catch (_) {}
        }
      }
    }

    // 触发浏览器下载
    const blobUrl = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(blobUrl)

  } catch (error) {
    if (error.response) {
      const status = error.response.status
      // 非 2xx 时 data 也是 Blob，需要解析 JSON 错误信息
      let errorMessage = '下载失败'
      try {
        const text = await error.response.data.text()
        const errorData = JSON.parse(text)
        errorMessage = errorData.message || errorMessage
      } catch (_) {}

      if (status === 401) {
        authStore.logout()
        router.push('/login')
        ElMessage.error('登录已过期，请重新登录')
      } else if (status === 403) {
        ElMessage.error(errorMessage || '无操作权限')
      } else {
        ElMessage.error(errorMessage)
      }
    } else {
      ElMessage.error('网络错误，下载失败')
    }
  }
}

export default request
