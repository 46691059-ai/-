export type OrgType = 'COMPANY' | 'DEPARTMENT' | 'PROJECT_TEAM'

export interface OrgTreeNode {
  id: string
  orgCode: string
  orgName: string
  orgType: OrgType
  parentId?: string
  leaderId?: string
  leaderName?: string
  status: number
  treeLevel: number
  sortNo: number
  children: OrgTreeNode[]
}

export interface OrgRecord {
  id: string
  orgCode: string
  orgName: string
  orgType: OrgType
  parentId?: string
  parentName?: string
  leaderId?: string
  leaderUserId?: string
  leaderName?: string
  treePath: string
  treeLevel: number
  sortNo: number
  status: number
  remark?: string
  createTime: string
  updateTime: string
  version: number
}

export interface OrgPage {
  records: OrgRecord[]
  total: number
  page: number
  size: number
}

export interface OrgQuery {
  orgName?: string
  orgType?: OrgType
  status?: number
  parentId?: string
  page: number
  size: number
}

export interface OrgForm {
  id?: string
  version?: number
  orgCode: string
  orgName: string
  orgType: OrgType
  parentId?: string
  leaderId?: string
  status: number
  sortNo: number
  remark?: string
}

export interface LeaderOption {
  employeeId: string
  userId: string
  username: string
  realName: string
}
