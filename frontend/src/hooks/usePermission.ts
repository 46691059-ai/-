import { useUserStore } from '../store/user'

export function usePermission() {
  const userStore = useUserStore()
  return {
    hasPermission: userStore.hasPermission,
  }
}
