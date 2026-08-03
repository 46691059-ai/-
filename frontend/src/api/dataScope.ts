import type { ApiResponse } from '../types/api'
import type { RoleDataScopeConfig, RoleDataScopeSaveRequest } from '../types/dataScope'
import type { OrgTreeNode } from '../types/org'
import request from '../utils/request'

export async function getRoleDataScope(roleId: string) {
  const response = await request.get<ApiResponse<RoleDataScopeConfig>>(`/system/datascope/role/${roleId}`)
  return response.data.data
}

export async function saveRoleDataScope(data: RoleDataScopeSaveRequest) {
  await request.put<ApiResponse<null>>('/system/datascope/role', data)
}

export async function getDataScopeOrganizationTree() {
  const response = await request.get<ApiResponse<OrgTreeNode[]>>('/system/datascope/org/tree')
  return response.data.data
}
