import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '../store/user'

type PermissionValue = string | string[]
const originalDisplay = new WeakMap<HTMLElement, string>()

function updatePermission(element: HTMLElement, binding: DirectiveBinding<PermissionValue>) {
  const userStore = useUserStore()
  const allowed = binding.value ? userStore.hasPermission(binding.value) : true
  if (!originalDisplay.has(element)) {
    originalDisplay.set(element, element.style.display)
  }
  element.style.display = allowed ? originalDisplay.get(element) || '' : 'none'
}

export const permissionDirective: Directive<HTMLElement, PermissionValue> = {
  mounted: updatePermission,
  updated: updatePermission,
}
