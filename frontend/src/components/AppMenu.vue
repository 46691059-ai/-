<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Document, HomeFilled, Key, Management, Menu, OfficeBuilding, Setting, User, UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import type { BackendMenu } from '../types/auth'

defineProps<{ collapsed: boolean }>()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const iconMap = { Document, HomeFilled, Key, Management, Menu, OfficeBuilding, Setting, User, UserFilled }
function resolveIcon(icon?: string) {
  return iconMap[icon as keyof typeof iconMap] || HomeFilled
}
interface DisplayMenu { key: string; path: string; title: string; icon?: string; disabled: boolean }
function flattenMenus(menus: BackendMenu[]): BackendMenu[] {
  return menus.flatMap((menu) => [menu, ...flattenMenus(menu.children ?? [])])
}
const menuRoutes = computed<DisplayMenu[]>(() => {
  const registeredPaths = new Set(router.getRoutes().map((item) => item.path))
  const items = new Map<string, DisplayMenu>()
  items.set('/home', { key: 'home', path: '/home', title: '首页', icon: 'HomeFilled', disabled: false })
  flattenMenus(userStore.menus).forEach((menu, index) => {
    if (!menu.path || menu.visible === false || menu.visible === 0 || items.has(menu.path)) return
    items.set(menu.path, {
      key: String(menu.id ?? `${menu.path}-${index}`), path: menu.path, title: menu.menuName,
      icon: menu.icon, disabled: !registeredPaths.has(menu.path),
    })
  })
  return [...items.values()]
})
</script>

<template>
  <el-menu
    :default-active="route.path"
    :collapse="collapsed"
    :collapse-transition="false"
    router
    class="platform-menu"
  >
    <el-menu-item v-for="item in menuRoutes" :key="item.key" :index="item.path" :disabled="item.disabled">
      <el-icon>
        <component :is="resolveIcon(item.icon)" />
      </el-icon>
      <template #title>{{ item.title }}</template>
    </el-menu-item>
  </el-menu>
</template>
