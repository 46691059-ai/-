import type { ApiResponse } from '../types/api'
import type { OrgOption, RoleOption, UserForm, UserPage, UserQuery, UserRecord } from '../types/user'
import request from '../utils/request'

export async function getUserPage(params: UserQuery) {
  const response = await request.get<ApiResponse<UserPage>>('/system/user/page', { params })
  return response.data.data
}

export async function getUserDetail(id: string) {
  const response = await request.get<ApiResponse<UserRecord>>(`/system/user/${id}`)
  return response.data.data
}

export async function createUser(data: UserForm) {
  const response = await request.post<ApiResponse<string>>('/system/user', data)
  return response.data.data
}

export async function updateUser(data: UserForm) {
  await request.put<ApiResponse<null>>('/system/user', data)
}

export async function deleteUser(id: string) {
  await request.delete<ApiResponse<null>>(`/system/user/${id}`)
}

export async function changeUserStatus(data: { id: string; status: number; version: number }) {
  await request.put<ApiResponse<null>>('/system/user/status', data)
}

export async function resetUserPassword(data: { id: string; newPassword: string; version: number }) {
  await request.put<ApiResponse<null>>('/system/user/resetPassword', data)
}

export async function assignUserRoles(data: { userId: string; roleIds: string[] }) {
  await request.put<ApiResponse<null>>('/system/user/roles', data)
}

export async function getRoleOptions() {
  const response = await request.get<ApiResponse<RoleOption[]>>('/system/user/roleOptions')
  return response.data.data
}

export async function getOrgOptions() {
  const response = await request.get<ApiResponse<OrgOption[]>>('/system/user/orgOptions')
  return response.data.data
}
