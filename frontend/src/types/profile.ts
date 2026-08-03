export interface Profile {
  id: string
  username: string
  realName: string
  phone?: string
  orgId: string
  orgName?: string
  roleCodes: string[]
  roleNames: string[]
}
