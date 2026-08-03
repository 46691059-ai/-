import type { ApiResponse } from '../types/api'
import type { BackendMenu, LoginRequest, LoginResponse } from '../types/auth'
import { request } from '../utils/request'

export async function loginApi(input: LoginRequest): Promise<LoginResponse> {
  const response = await request.post<ApiResponse<LoginResponse>>('/auth/login', input)
  return response.data.data
}

export async function logoutApi(): Promise<void> {
  await request.post<ApiResponse<null>>('/auth/logout')
}

export async function getRoutesApi(): Promise<BackendMenu[]> {
  const response = await request.get<ApiResponse<BackendMenu[]>>('/auth/routes')
  return response.data.data
}
