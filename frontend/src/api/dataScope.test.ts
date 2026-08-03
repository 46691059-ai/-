import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '../utils/request'
import {
  getDataScopeOrganizationTree,
  getRoleDataScope,
  saveRoleDataScope,
} from './dataScope'

vi.mock('../utils/request', () => ({
  default: { get: vi.fn(), put: vi.fn() },
}))

describe('data scope api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('loads role data scope', async () => {
    vi.mocked(request.get).mockResolvedValueOnce({
      data: { data: { roleId: '6', dataScope: 'CUSTOM', orgIds: ['10303'] } },
    })

    await expect(getRoleDataScope('6')).resolves.toEqual({
      roleId: '6', dataScope: 'CUSTOM', orgIds: ['10303'],
    })
    expect(request.get).toHaveBeenCalledWith('/system/datascope/role/6')
  })

  it('saves custom organizations', async () => {
    vi.mocked(request.put).mockResolvedValueOnce({ data: { data: null } })
    const payload = { roleId: '6', dataScope: 'CUSTOM' as const, orgIds: ['10303'] }

    await saveRoleDataScope(payload)

    expect(request.put).toHaveBeenCalledWith('/system/datascope/role', payload)
  })

  it('loads selectable organization tree', async () => {
    vi.mocked(request.get).mockResolvedValueOnce({ data: { data: [] } })
    await expect(getDataScopeOrganizationTree()).resolves.toEqual([])
    expect(request.get).toHaveBeenCalledWith('/system/datascope/org/tree')
  })
})
