import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '../utils/request'
import { createMenu, deleteMenu, getMenuPage, getMenuTree, updateMenu } from './menu'

vi.mock('../utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

describe('menu api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('uses the menu center REST endpoints', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { data: [] } })
    vi.mocked(request.post).mockResolvedValue({ data: { data: '940' } })
    vi.mocked(request.put).mockResolvedValue({ data: { data: null } })
    vi.mocked(request.delete).mockResolvedValue({ data: { data: null } })

    await getMenuTree()
    await getMenuPage({ page: 1, size: 20 })
    await createMenu({ menuName: '菜单', menuType: 'C', path: '/menu', sort: 1, status: 1 })
    await updateMenu({ id: '940', version: 0, menuName: '菜单', menuType: 'C', path: '/menu', sort: 1, status: 1 })
    await deleteMenu('940')

    expect(request.get).toHaveBeenCalledWith('/system/menu/tree')
    expect(request.get).toHaveBeenCalledWith('/system/menu/page', { params: { page: 1, size: 20 } })
    expect(request.post).toHaveBeenCalledWith('/system/menu', expect.any(Object))
    expect(request.put).toHaveBeenCalledWith('/system/menu', expect.any(Object))
    expect(request.delete).toHaveBeenCalledWith('/system/menu/940')
  })
})
