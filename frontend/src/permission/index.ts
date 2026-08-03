import type { App } from 'vue'
import type { Router } from 'vue-router'
import { useUserStore } from '../store/user'
import { buildAuthorizedRoutes } from '../router/dynamic'
import { permissionDirective } from './directive'

let removeDynamicRoutes: Array<() => void> = []
let dynamicRoutesReady = false

function resetDynamicRoutes() {
  removeDynamicRoutes.forEach((remove) => remove())
  removeDynamicRoutes = []
  dynamicRoutesReady = false
}

function installDynamicRoutes(router: Router) {
  const userStore = useUserStore()
  resetDynamicRoutes()
  buildAuthorizedRoutes(userStore.permissions, userStore.menus).forEach((route) => {
    removeDynamicRoutes.push(router.addRoute('RootLayout', route))
  })
  dynamicRoutesReady = true
}

export function setupPermission(app: App, router: Router) {
  app.directive('permission', permissionDirective)

  router.beforeEach(async (to) => {
    const userStore = useUserStore()
    if (to.meta.public) {
      if (to.name === 'Login' && userStore.isAuthenticated) {
        return { path: '/home', replace: true }
      }
      return true
    }

    if (!userStore.isAuthenticated) {
      resetDynamicRoutes()
      return { path: '/login', query: { redirect: to.fullPath }, replace: true }
    }

    await userStore.getUserInfo()
    if (!dynamicRoutesReady) {
      installDynamicRoutes(router)
      if (to.name === 'NotFound') {
        return { path: to.fullPath, replace: true }
      }
    }

    const requiredPermission = to.meta.permission
    if (requiredPermission && !userStore.hasPermission(requiredPermission)) {
      return { path: '/home', replace: true }
    }
    return true
  })
}
