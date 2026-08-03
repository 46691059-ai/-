import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getRoutesApi, loginApi, logoutApi } from '../api/auth'
import type { BackendMenu, LoginRequest, UserInfo } from '../types/auth'
import { authStorage } from '../utils/auth-storage'

export const useUserStore = defineStore('user', () => {
  const token = ref(authStorage.getToken())
  const userInfo = ref<UserInfo | null>(authStorage.getUser())
  const roles = ref<string[]>(authStorage.getRoles())
  const permissions = ref<string[]>(authStorage.getPermissions())
  const menus = ref<BackendMenu[]>(authStorage.getMenus())
  const routesLoaded = ref(false)

  const isAuthenticated = computed(() => Boolean(token.value))

  async function login(input: LoginRequest) {
    const payload = await loginApi(input)
    const accessToken = payload.token || payload.accessToken
    const authenticatedUser = payload.userInfo || payload.user
    if (!accessToken || !authenticatedUser) {
      throw new Error('登录响应缺少Token或用户信息')
    }

    token.value = accessToken
    userInfo.value = authenticatedUser
    roles.value = payload.roles ?? []
    permissions.value = payload.permissions ?? []
    menus.value = []

    authStorage.setToken(accessToken)
    authStorage.setUser(authenticatedUser)
    authStorage.setRoles(roles.value)
    authStorage.setPermissions(permissions.value)
    try {
      menus.value = await getRoutesApi()
      routesLoaded.value = true
      authStorage.setMenus(menus.value)
    } catch (error) {
      reset()
      throw error
    }
    return payload
  }

  async function logout() {
    try {
      if (token.value) {
        await logoutApi()
      }
    } finally {
      reset()
    }
  }

  async function getUserInfo() {
    if (!token.value) {
      return null
    }
    userInfo.value = userInfo.value ?? authStorage.getUser()
    roles.value = roles.value.length ? roles.value : authStorage.getRoles()
    permissions.value = permissions.value.length
      ? permissions.value
      : authStorage.getPermissions()
    menus.value = menus.value.length ? menus.value : authStorage.getMenus()
    if (!routesLoaded.value) {
      menus.value = await getRoutesApi()
      authStorage.setMenus(menus.value)
      routesLoaded.value = true
    }
    return userInfo.value
  }

  function reset() {
    token.value = ''
    userInfo.value = null
    roles.value = []
    permissions.value = []
    menus.value = []
    routesLoaded.value = false
    authStorage.clear()
  }

  function hasPermission(permission: string | string[]) {
    const required = Array.isArray(permission) ? permission : [permission]
    return required.some((item) => permissions.value.includes(item))
  }

  return {
    token,
    userInfo,
    roles,
    permissions,
    menus,
    isAuthenticated,
    login,
    logout,
    getUserInfo,
    reset,
    hasPermission,
  }
})
