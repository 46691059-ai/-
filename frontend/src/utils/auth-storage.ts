import type { BackendMenu, UserInfo } from '../types/auth'

const TOKEN_KEY = 'enterprise_access_token'
const USER_KEY = 'enterprise_user_info'
const ROLE_KEY = 'enterprise_roles'
const PERMISSION_KEY = 'enterprise_permissions'
const MENU_KEY = 'enterprise_menus'

function readJson<T>(key: string, fallback: T): T {
  try {
    const value = sessionStorage.getItem(key)
    return value ? (JSON.parse(value) as T) : fallback
  } catch {
    return fallback
  }
}

export const authStorage = {
  getToken: () => sessionStorage.getItem(TOKEN_KEY) ?? '',
  setToken: (token: string) => sessionStorage.setItem(TOKEN_KEY, token),
  getUser: () => readJson<UserInfo | null>(USER_KEY, null),
  setUser: (user: UserInfo) => sessionStorage.setItem(USER_KEY, JSON.stringify(user)),
  getRoles: () => readJson<string[]>(ROLE_KEY, []),
  setRoles: (roles: string[]) => sessionStorage.setItem(ROLE_KEY, JSON.stringify(roles)),
  getPermissions: () => readJson<string[]>(PERMISSION_KEY, []),
  setPermissions: (permissions: string[]) =>
    sessionStorage.setItem(PERMISSION_KEY, JSON.stringify(permissions)),
  getMenus: () => readJson<BackendMenu[]>(MENU_KEY, []),
  setMenus: (menus: BackendMenu[]) => sessionStorage.setItem(MENU_KEY, JSON.stringify(menus)),
  clear: () => {
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(USER_KEY)
    sessionStorage.removeItem(ROLE_KEY)
    sessionStorage.removeItem(PERMISSION_KEY)
    sessionStorage.removeItem(MENU_KEY)
  },
}
