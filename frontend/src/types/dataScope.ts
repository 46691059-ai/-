import type { DataScopeType } from './role'

export interface RoleDataScopeConfig {
  roleId: string
  dataScope: DataScopeType
  orgIds: string[]
}

export interface RoleDataScopeSaveRequest {
  roleId: string
  dataScope: DataScopeType
  orgIds: string[]
}
