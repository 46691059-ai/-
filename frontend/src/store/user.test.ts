import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { getRoutesApi, loginApi, logoutApi } from '../api/auth'
import { useUserStore } from './user'

vi.mock('../api/auth', () => ({
  loginApi: vi.fn(),
  logoutApi: vi.fn(),
  getRoutesApi: vi.fn(),
}))

function createSessionStorage() {
  const values = new Map<string, string>()
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
    clear: () => values.clear(),
    key: (index: number) => [...values.keys()][index] ?? null,
    get length() {
      return values.size
    },
  }
}

describe('user store', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('sessionStorage', createSessionStorage())
    setActivePinia(createPinia())
    vi.mocked(getRoutesApi).mockResolvedValue([])
  })

  it('persists token, user and permissions after login', async () => {
    vi.mocked(loginApi).mockResolvedValue({
      token: 'signed-token',
      userInfo: { id: 1, username: 'admin', realName: '平台管理员' },
      roles: ['SUPER_ADMIN'],
      permissions: ['project:view', 'system:view'],
    })
    const store = useUserStore()

    await store.login({ username: 'admin', password: 'password' })

    expect(store.token).toBe('signed-token')
    expect(store.userInfo?.realName).toBe('平台管理员')
    expect(store.hasPermission('project:view')).toBe(true)
    expect(sessionStorage.getItem('enterprise_access_token')).toBe('signed-token')
  })

  it('clears local identity even when remote logout fails', async () => {
    vi.mocked(loginApi).mockResolvedValue({
      token: 'signed-token',
      userInfo: { id: 1, username: 'admin' },
    })
    vi.mocked(logoutApi).mockRejectedValue(new Error('network error'))
    const store = useUserStore()
    await store.login({ username: 'admin', password: 'password' })

    await expect(store.logout()).rejects.toThrow('network error')

    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
  })
})
