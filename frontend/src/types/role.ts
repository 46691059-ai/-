export type DataScopeType = 'ALL' | 'ORG' | 'ORG_AND_CHILDREN' | 'SELF' | 'CUSTOM'

export interface RoleRecord {
  id: string
  roleName: string
  roleCode: string
  description?: string
  dataScopeType: DataScopeType
  status: number
  userCount: number
  createTime: string
  updateTime: string
  version: number
}

export interface RolePage { records: RoleRecord[]; total: number; page: number; size: number }
export interface RoleQuery { roleName?: string; roleCode?: string; status?: number; page: number; size: number }
export interface RoleForm {
  id?: string
  version?: number
  roleName: string
  roleCode: string
  description?: string
  dataScopeType: DataScopeType
  status: number
}

export interface MenuTreeNode {
  id: string
  menuName: string
  menuType: 'M' | 'C' | 'B'
  parentId?: string
  permission?: string
  path?: string
  status: number
  children: MenuTreeNode[]
}

export interface RolePermission {
  roleId: string
  menuIds: string[]
  permissionCodes: string[]
}
