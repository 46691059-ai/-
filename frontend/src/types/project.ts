export type ProjectType = 'INVESTMENT' | 'ENGINEERING' | 'DIGITAL' | 'OPERATION' | 'OTHER'
export type ProjectStatus = 'DRAFT' | 'RESERVED' | 'IN_PROGRESS' | 'SUSPENDED' | 'COMPLETED' | 'CANCELLED'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface Project {
  id: string
  projectCode: string
  projectName: string
  projectType: ProjectType
  orgId: string
  managerUserId: string
  description?: string
  plannedStartDate?: string
  plannedEndDate?: string
  actualStartDate?: string
  actualEndDate?: string
  investmentAmount: number
  expectedIncome: number
  actualIncome: number
  currentStageCode: string
  projectStatus: ProjectStatus
  riskLevel: RiskLevel
  progress: number
  createdTime: string
  updatedTime: string
  version: number
}

export interface ProjectCreateInput {
  projectCode: string
  projectName: string
  projectType: ProjectType
  orgId: string
  managerUserId: string
  description?: string
  plannedStartDate?: string
  plannedEndDate?: string
  investmentAmount: number
  expectedIncome: number
  riskLevel: RiskLevel
}

export interface ProjectUpdateInput extends Omit<ProjectCreateInput, 'projectCode'> {
  actualStartDate?: string
  actualEndDate?: string
  actualIncome: number
  version: number
}

export interface ProjectStage {
  id: string
  projectId: string
  stageCode: string
  stageName: string
  stageOrder: number
  stageStatus: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED'
  ownerUserId?: string
  plannedStartDate?: string
  plannedEndDate?: string
  actualStartDate?: string
  actualEndDate?: string
  approvalStatus: 'NOT_SUBMITTED' | 'PENDING' | 'APPROVED' | 'REJECTED'
  completionPercent: number
  milestoneDesc?: string
  riskSummary?: string
  version: number
}

export interface ProjectTask {
  id: string
  projectId: string
  stageId: string
  parentTaskId?: string
  taskCode: string
  taskName: string
  taskType: string
  assigneeUserId?: string
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
  taskStatus: 'TODO' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED' | 'CANCELLED'
  plannedStartDate?: string
  plannedEndDate?: string
  actualStartDate?: string
  actualEndDate?: string
  progress: number
  outputDesc?: string
  riskDesc?: string
  sortNo: number
  version: number
}

export interface ProjectMember {
  id: string
  projectId: string
  userId: string
  memberRole: 'MANAGER' | 'CORE' | 'PARTICIPANT' | 'EXPERT'
  responsibilities?: string
  joinedDate: string
  leftDate?: string
  memberStatus: 'ACTIVE' | 'INACTIVE'
  version: number
}

export interface ProjectDetail {
  project: Project
  stages: ProjectStage[]
  tasks: ProjectTask[]
  taskTotal: number
  members: ProjectMember[]
  memberTotal: number
}
