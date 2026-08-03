import type { RouteRecordRaw } from 'vue-router'
import type { BackendMenu } from '../types/auth'

const routeRegistry: RouteRecordRaw[] = [
  {
    path: 'profile',
    name: 'Profile',
    component: () => import('../views/profile/index.vue'),
    meta: { title: '个人中心', menuTitle: '个人中心', icon: 'UserFilled', permission: 'profile:view', order: 10 },
  },
  {
    path: 'system',
    name: 'SystemEntry',
    component: () => import('../views/system/index.vue'),
    meta: {
      title: '系统管理',
      menuTitle: '系统管理',
      icon: 'Setting',
      permission: 'system:view',
      order: 90,
    },
  },
  {
    path: 'system/user',
    name: 'SystemUser',
    component: () => import('../views/system/user/index.vue'),
    meta: {
      title: '用户管理',
      menuTitle: '用户管理',
      icon: 'User',
      permission: 'system:user:view',
      order: 91,
    },
  },
  {
    path: 'system/org',
    name: 'SystemOrg',
    component: () => import('../views/system/org/index.vue'),
    meta: {
      title: '组织管理',
      menuTitle: '组织管理',
      icon: 'OfficeBuilding',
      permission: 'system:org:view',
      order: 92,
    },
  },
  {
    path: 'system/role',
    name: 'SystemRole',
    component: () => import('../views/system/role/index.vue'),
    meta: { title: '角色权限', menuTitle: '角色权限', icon: 'Key', permission: 'system:role:view', order: 93 },
  },
  {
    path: 'system/menu',
    name: 'SystemMenu',
    component: () => import('../views/system/menu/index.vue'),
    meta: {
      title: '菜单管理',
      menuTitle: '菜单管理',
      icon: 'Menu',
      permission: 'system:menu:view',
      order: 94,
    },
  },
  {
    path: 'system/log',
    name: 'SystemLog',
    component: () => import('../views/system/log/index.vue'),
    meta: {
      title: '操作日志',
      menuTitle: '操作日志',
      icon: 'Document',
      permission: 'system:log:view',
      order: 95,
    },
  },
  {
    path: 'projects',
    name: 'ProjectList',
    component: () => import('../views/project/ProjectListView.vue'),
    meta: {
      title: '项目全生命周期',
      menuTitle: '项目管理',
      icon: 'Management',
      permission: 'project:view',
      order: 40,
    },
  },
  {
    path: 'projects/create',
    name: 'ProjectCreate',
    component: () => import('../views/project/ProjectCreateView.vue'),
    meta: { title: '新增项目', hidden: true, permission: 'project:add' },
  },
  {
    path: 'projects/:id/edit',
    name: 'ProjectEdit',
    component: () => import('../views/project/ProjectEditView.vue'),
    meta: { title: '编辑项目', hidden: true, permission: 'project:edit' },
  },
  {
    path: 'projects/:id',
    name: 'ProjectDetail',
    component: () => import('../views/project/ProjectDetailView.vue'),
    meta: { title: '项目详情', hidden: true, permission: 'project:lifecycle:list' },
  },
]

function flattenMenus(menus: BackendMenu[]): BackendMenu[] {
  return menus.flatMap((menu) => [menu, ...flattenMenus(menu.children ?? [])])
}

function normalizePath(path?: string) {
  return `/${path}`.replace(/\/+/g, '/')
}

export function buildAuthorizedRoutes(
  permissions: string[],
  backendMenus: BackendMenu[],
): RouteRecordRaw[] {
  const flattenedMenus = flattenMenus(backendMenus)
  return routeRegistry.filter((route) => {
    const permission = route.meta?.permission
    if (permission && !permissions.includes(permission)) {
      return false
    }
    if (!route.meta?.menuTitle || flattenedMenus.length === 0) {
      return true
    }
    const routePath = normalizePath(String(route.path))
    return flattenedMenus.some((menu) => {
      const menuVisible = menu.visible !== false && menu.visible !== 0
      const pathMatches = normalizePath(menu.path) === routePath
      const permissionMatches = !menu.permission || permissions.includes(menu.permission)
      return menuVisible && pathMatches && permissionMatches
    })
  })
}
