export interface LoginRequest {
  username: string
  password: string
}

export interface UserInfo {
  id: string | number
  username: string
  realName?: string
  avatar?: string
  orgId?: string | number
  orgName?: string
}

export interface LoginResponse {
  token?: string
  accessToken?: string
  userInfo?: UserInfo
  user?: UserInfo
  roles?: string[]
  permissions?: string[]
  buttonPermissions?: string[]
  menus?: BackendMenu[]
}

export interface BackendMenu {
  id?: string | number
  parentId?: string | number | null
  menuName: string
  menuType?: 'M' | 'C'
  path?: string
  component?: string
  permission?: string
  icon?: string
  visible?: number | boolean
  children?: BackendMenu[]
}
