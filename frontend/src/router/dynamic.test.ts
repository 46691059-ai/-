import { describe, expect, it } from 'vitest'
import { buildAuthorizedRoutes } from './dynamic'

describe('permission route builder', () => {
  it('only exposes modules granted by permissions', () => {
    const routes = buildAuthorizedRoutes(['project:view', 'project:lifecycle:list'], [])

    expect(routes.map((route) => route.name)).toContain('ProjectList')
    expect(routes.map((route) => route.name)).not.toContain('SystemEntry')
    expect(routes.map((route) => route.name)).not.toContain('ProjectCreate')
  })

  it('uses backend menus as a safe whitelist for visible module routes', () => {
    const routes = buildAuthorizedRoutes(
      ['project:view', 'system:view'],
      [{ menuName: '系统管理', path: '/system', permission: 'system:view' }],
    )

    expect(routes.map((route) => route.name)).toContain('SystemEntry')
    expect(routes.map((route) => route.name)).not.toContain('ProjectList')
  })

  it('shows project routes after a project manager receives view, create and update permissions', () => {
    const routes = buildAuthorizedRoutes(
      ['project:view', 'project:lifecycle:list', 'project:add', 'project:edit'],
      [
        {
          menuName: '项目管理',
          path: '/projects',
          permission: 'project:view',
          children: [
            { menuName: '项目库', path: '/projects', permission: 'project:lifecycle:list' },
          ],
        },
      ],
    )

    expect(routes.map((route) => route.name)).toEqual(
      expect.arrayContaining(['ProjectList', 'ProjectCreate', 'ProjectEdit']),
    )
    expect(routes.map((route) => route.name)).not.toContain('SystemEntry')
  })

  it('does not expose investment, data asset or system routes to a common employee', () => {
    const routes = buildAuthorizedRoutes(['profile:view'], [])
    const names = routes.map((route) => route.name)

    expect(names).toContain('Profile')
    expect(names).not.toContain('SystemEntry')
    expect(names).not.toContain('SystemUser')
    expect(names).not.toContain('SystemOrg')
    expect(names).not.toContain('SystemRole')
    expect(names).not.toContain('ProjectList')
    expect(routes.some((route) => String(route.path).includes('investment'))).toBe(false)
    expect(routes.some((route) => String(route.path).includes('data-asset'))).toBe(false)
  })

  it('adds the menu management route for an authorized administrator', () => {
    const routes = buildAuthorizedRoutes(
      ['system:view', 'system:menu:view'],
      [{
        menuName: '系统管理', path: '/system', permission: 'system:view', children: [
          { menuName: '菜单管理', path: '/system/menu', permission: 'system:menu:view' },
        ],
      }],
    )

    expect(routes.map((route) => route.name)).toEqual(
      expect.arrayContaining(['SystemEntry', 'SystemMenu']),
    )
  })

  it('adds the operation log route only when backend menu and view authority both exist', () => {
    const routes = buildAuthorizedRoutes(
      ['system:view', 'system:log:view', 'system:log:query'],
      [{
        menuName: '系统管理', path: '/system', permission: 'system:view', children: [
          { menuName: '操作日志', path: '/system/log', permission: 'system:log:view' },
        ],
      }],
    )

    expect(routes.map((route) => route.name)).toContain('SystemLog')
    expect(buildAuthorizedRoutes(['system:view'], []).map((route) => route.name))
      .not.toContain('SystemLog')
  })
})
