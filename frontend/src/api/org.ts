import type { ApiResponse } from '../types/api'
import type { LeaderOption, OrgForm, OrgPage, OrgQuery, OrgRecord, OrgTreeNode } from '../types/org'
import request from '../utils/request'

export async function getOrgTree() {
  const response = await request.get<ApiResponse<OrgTreeNode[]>>('/system/org/tree')
  return response.data.data
}

export async function getOrgPage(params: OrgQuery) {
  const response = await request.get<ApiResponse<OrgPage>>('/system/org/page', { params })
  return response.data.data
}

export async function getOrgDetail(id: string) {
  const response = await request.get<ApiResponse<OrgRecord>>(`/system/org/${id}`)
  return response.data.data
}

export async function createOrg(data: OrgForm) {
  const response = await request.post<ApiResponse<string>>('/system/org', data)
  return response.data.data
}

export async function updateOrg(data: OrgForm) {
  await request.put<ApiResponse<null>>('/system/org', data)
}

export async function deleteOrg(id: string) {
  await request.delete<ApiResponse<null>>(`/system/org/${id}`)
}

export async function getLeaderOptions() {
  const response = await request.get<ApiResponse<LeaderOption[]>>('/system/org/leaderOptions')
  return response.data.data
}
