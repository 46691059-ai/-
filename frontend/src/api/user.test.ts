import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get, post, put, del } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}))

vi.mock('../utils/request', () => ({ default: { get, post, put, delete: del } }))

import { assignUserRoles, getUserPage, resetUserPassword } from './user'

describe('user management api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('queries the REST pagination endpoint', async () => {
    const page = { records: [], total: 0, page: 1, size: 20 }
    get.mockResolvedValue({ data: { code: 200, data: page } })
    const query = { username: 'admin', page: 1, size: 20 }

    await expect(getUserPage(query)).resolves.toEqual(page)
    expect(get).toHaveBeenCalledWith('/system/user/page', { params: query })
  })

  it('uses the dedicated reset-password endpoint', async () => {
    put.mockResolvedValue({ data: { code: 200, data: null } })
    await resetUserPassword({ id: '1', newPassword: 'Secure123', version: 0 })
    expect(put).toHaveBeenCalledWith('/system/user/resetPassword', {
      id: '1', newPassword: 'Secure123', version: 0,
    })
  })

  it('submits role ids without unsafe numeric conversion', async () => {
    put.mockResolvedValue({ data: { code: 200, data: null } })
    await assignUserRoles({ userId: '190000000000000001', roleIds: ['190000000000000002'] })
    expect(put).toHaveBeenCalledWith('/system/user/roles', {
      userId: '190000000000000001', roleIds: ['190000000000000002'],
    })
  })
})
