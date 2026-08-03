import { authStorage } from './auth-storage'
import { request } from './request'

/** 兼容已有业务模块的HTTP客户端导出。 */
export const http = request

/** @deprecated 新代码由用户Store统一维护Token。 */
export function setAccessToken(token: string | null) {
  if (token) {
    authStorage.setToken(token)
  } else {
    authStorage.clear()
  }
}
