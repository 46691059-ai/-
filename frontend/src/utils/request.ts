import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '../types/api'
import { authStorage } from './auth-storage'

export const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

request.interceptors.request.use((config) => {
  const token = authStorage.getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data as Partial<ApiResponse<unknown>> | undefined
    if (body && typeof body.code === 'number' && body.code !== 200) {
      ElMessage.error(body.message || '请求处理失败')
      return Promise.reject(new Error(body.message || '请求处理失败'))
    }
    return response
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status
    const serverMessage = error.response?.data?.message

    if (status === 401) {
      authStorage.clear()
      ElMessage.warning('登录状态已失效，请重新登录')
      if (window.location.pathname !== '/login') {
        const redirect = encodeURIComponent(`${window.location.pathname}${window.location.search}`)
        window.location.replace(`/login?redirect=${redirect}`)
      }
    } else if (status === 403) {
      ElMessage.error('无权访问当前资源')
    } else if (status && status >= 500) {
      ElMessage.error(serverMessage || '系统服务异常，请稍后重试')
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络连接')
    } else {
      ElMessage.error(serverMessage || error.message || '网络请求失败')
    }
    return Promise.reject(error)
  },
)

export default request
