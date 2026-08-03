import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '../utils/request'
import { cleanLogs, deleteLog, getLogDetail, getLogPage } from './log'

vi.mock('../utils/request', () => ({
  default: { get: vi.fn(), delete: vi.fn() },
}))

describe('log api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('uses operation log REST endpoints', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { data: { records: [], total: 0, page: 1, size: 20 } } })
    vi.mocked(request.delete).mockResolvedValue({ data: { data: 2 } })
    const query = { page: 1, size: 20, username: 'admin' }

    await getLogPage(query)
    await getLogDetail('1')
    await deleteLog('1')
    await cleanLogs('2026-07-01T00:00:00')

    expect(request.get).toHaveBeenCalledWith('/system/log/page', { params: query })
    expect(request.get).toHaveBeenCalledWith('/system/log/1')
    expect(request.delete).toHaveBeenCalledWith('/system/log/1')
    expect(request.delete).toHaveBeenCalledWith('/system/log/clean', {
      params: { before: '2026-07-01T00:00:00' },
    })
  })
})
