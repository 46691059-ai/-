export type LogStatus = 'SUCCESS' | 'FAIL' | 'ERROR'

export interface LogQuery {
  username?: string
  logType?: string
  moduleName?: string
  status?: LogStatus
  startTime?: string
  endTime?: string
  page: number
  size: number
}

export interface LogRecord {
  id: string
  userId?: string
  username?: string
  logType: string
  moduleName: string
  operation: string
  requestUrl: string
  requestMethod: string
  requestParams?: string
  responseResult?: string
  ip?: string
  status: LogStatus
  errorMessage?: string
  durationMs?: number
  traceId?: string
  createTime: string
}

export interface LogPage {
  records: LogRecord[]
  total: number
  page: number
  size: number
}
