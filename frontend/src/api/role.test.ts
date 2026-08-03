import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get, post, put, del } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() }))
vi.mock('../utils/request', () => ({ default: { get, post, put, delete: del } }))
import { assignRoleMenus, createRole, getMenuTree } from './role'

describe('role permission api', () => {
  beforeEach(() => vi.clearAllMocks())
  it('loads the menu permission tree', async () => {
    const tree = [{ id: '900', menuName: '系统管理', children: [] }]
    get.mockResolvedValue({ data: { code: 200, data: tree } })
    await expect(getMenuTree()).resolves.toEqual(tree)
    expect(get).toHaveBeenCalledWith('/system/role/menuTree')
  })
  it('creates a role with ORG_AND_CHILDREN scope', async () => {
    post.mockResolvedValue({ data: { code: 200, data: '8' } })
    const role = { roleName: '部门负责人', roleCode: 'DEPT_MANAGER_2', description: '',
      dataScopeType: 'ORG_AND_CHILDREN' as const, status: 1 }
    await expect(createRole(role)).resolves.toBe('8')
    expect(post).toHaveBeenCalledWith('/system/role', role)
  })
  it('saves checked menu ids as strings', async () => {
    put.mockResolvedValue({ data: { code: 200, data: null } })
    await assignRoleMenus({ roleId: '8', menuIds: ['900', '930', '931'] })
    expect(put).toHaveBeenCalledWith('/system/role/menu', { roleId: '8', menuIds: ['900', '930', '931'] })
  })
})
