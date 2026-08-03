export type MenuType = 'M' | 'C' | 'B'

export interface MenuRecord {
  id: string
  parentId?: string
  menuName: string
  menuType: MenuType
  path?: string
  component?: string
  permission?: string
  icon?: string
  sort: number
  visible: number
  status: number
  createTime: string
  updateTime: string
  version: number
  children: MenuRecord[]
}

export interface MenuPage {
  records: MenuRecord[]
  total: number
  page: number
  size: number
}

export interface MenuQuery {
  menuName?: string
  menuType?: MenuType
  permission?: string
  status?: number
  page: number
  size: number
}

export interface MenuForm {
  id?: string
  version?: number
  parentId?: string
  menuName: string
  menuType: MenuType
  path?: string
  component?: string
  permission?: string
  icon?: string
  sort: number
  status: number
}
