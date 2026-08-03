import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get, post, put, del } = vi.hoisted(() => ({
  get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn(),
}))

vi.mock('../utils/request', () => ({ default: { get, post, put, delete: del } }))

import { createOrg, deleteOrg, getOrgTree } from './org'

describe('organization management api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('loads the organization tree', async () => {
    const tree = [{ id: '100', orgName: '集团总部', children: [] }]
    get.mockResolvedValue({ data: { code: 200, data: tree } })
    await expect(getOrgTree()).resolves.toEqual(tree)
    expect(get).toHaveBeenCalledWith('/system/org/tree')
  })

  it('creates an administrative organization', async () => {
    post.mockResolvedValue({ data: { code: 200, data: '190000000000000001' } })
    const form = { orgCode: 'DEPT009', orgName: '审计部', orgType: 'DEPARTMENT' as const,
      parentId: '100', status: 1, sortNo: 9 }
    await expect(createOrg(form)).resolves.toBe('190000000000000001')
    expect(post).toHaveBeenCalledWith('/system/org', form)
  })

  it('keeps snowflake ids as strings when deleting', async () => {
    del.mockResolvedValue({ data: { code: 200, data: null } })
    await deleteOrg('190000000000000001')
    expect(del).toHaveBeenCalledWith('/system/org/190000000000000001')
  })
})
