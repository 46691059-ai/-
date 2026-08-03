import 'vue-router'

export {}

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    public?: boolean
    hidden?: boolean
    icon?: string
    permission?: string
    menuTitle?: string
    order?: number
  }
}
