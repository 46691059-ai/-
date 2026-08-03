export interface UserRecord {
  id: string
  username: string
  realName: string
  phone?: string
  email?: string
  orgId: string
  orgName?: string
  status: number
  roleIds: string[]
  roleNames: string[]
  createTime: string
  updateTime: string
  version: number
}

export interface UserPage {
  records: UserRecord[]
  total: number
  page: number
  size: number
}

export interface UserQuery {
  username?: string
  realName?: string
  orgId?: string
  status?: number
  page: number
  size: number
}

export interface UserForm {
  id?: string
  version?: number
  username: string
  password?: string
  realName: string
  phone?: string
  email?: string
  orgId?: string
  status: number
}

export interface RoleOption { id: string; roleName: string; roleCode: string }
export interface OrgOption { id: string; orgName: string; orgCode: string }
