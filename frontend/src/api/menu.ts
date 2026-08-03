import type { ApiResponse } from '../types/api'
import type { MenuForm, MenuPage, MenuQuery, MenuRecord } from '../types/menu'
import request from '../utils/request'

export async function getMenuTree() {
  const response = await request.get<ApiResponse<MenuRecord[]>>('/system/menu/tree')
  return response.data.data
}

export async function getMenuPage(params: MenuQuery) {
  const response = await request.get<ApiResponse<MenuPage>>('/system/menu/page', { params })
  return response.data.data
}

export async function createMenu(data: MenuForm) {
  const response = await request.post<ApiResponse<string>>('/system/menu', data)
  return response.data.data
}

export async function updateMenu(data: MenuForm) {
  await request.put<ApiResponse<null>>('/system/menu', data)
}

export async function deleteMenu(id: string) {
  await request.delete<ApiResponse<null>>(`/system/menu/${id}`)
}
