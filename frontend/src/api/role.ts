import type { ApiResponse } from '../types/api'
import type { MenuTreeNode, RoleForm, RolePage, RolePermission, RoleQuery } from '../types/role'
import request from '../utils/request'

export async function getRolePage(params: RoleQuery) {
  const response = await request.get<ApiResponse<RolePage>>('/system/role/page', { params })
  return response.data.data
}
export async function getRoleDetail(id: string) {
  const response = await request.get<ApiResponse<RoleForm>>(`/system/role/${id}`)
  return response.data.data
}
export async function createRole(data: RoleForm) {
  const response = await request.post<ApiResponse<string>>('/system/role', data)
  return response.data.data
}
export async function updateRole(data: RoleForm) { await request.put<ApiResponse<null>>('/system/role', data) }
export async function deleteRole(id: string) { await request.delete<ApiResponse<null>>(`/system/role/${id}`) }
export async function getMenuTree() {
  const response = await request.get<ApiResponse<MenuTreeNode[]>>('/system/role/menuTree')
  return response.data.data
}
export async function getRolePermissions(id: string) {
  const response = await request.get<ApiResponse<RolePermission>>(`/system/role/${id}/menus`)
  return response.data.data
}
export async function assignRoleMenus(data: { roleId: string; menuIds: string[] }) {
  await request.put<ApiResponse<null>>('/system/role/menu', data)
}
