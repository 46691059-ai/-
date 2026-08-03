import type { ApiResponse } from '../types/api'
import type { LogPage, LogQuery, LogRecord } from '../types/log'
import request from '../utils/request'

export async function getLogPage(params: LogQuery) {
  const response = await request.get<ApiResponse<LogPage>>('/system/log/page', { params })
  return response.data.data
}

export async function getLogDetail(id: string) {
  const response = await request.get<ApiResponse<LogRecord>>(`/system/log/${id}`)
  return response.data.data
}

export async function deleteLog(id: string) {
  await request.delete<ApiResponse<null>>(`/system/log/${id}`)
}

export async function cleanLogs(before: string) {
  const response = await request.delete<ApiResponse<number>>('/system/log/clean', { params: { before } })
  return response.data.data
}
