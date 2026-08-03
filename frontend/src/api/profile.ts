import type { ApiResponse } from '../types/api'
import type { Profile } from '../types/profile'
import request from '../utils/request'

export async function getCurrentProfile() {
  const response = await request.get<ApiResponse<Profile>>('/profile')
  return response.data.data
}
