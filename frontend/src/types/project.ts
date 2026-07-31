export type ProjectType = 'INVESTMENT' | 'OPERATION' | 'ENGINEERING' | 'DIGITAL' | 'RD' | 'OTHER'
export type ProjectStatus = 'RESERVED' | 'IN_PROGRESS' | 'SUSPENDED' | 'COMPLETED' | 'CANCELLED'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface Project {
  id: string
  projectNo: string
  projectName: string
  projectType: ProjectType
  projectMode?: string
  leaderId: string
  departmentId: string
  status: ProjectStatus
  startDate?: string
  endDate?: string
  actualStartDate?: string
  actualEndDate?: string
  budgetAmount: number
  expectedIncome: number
  expectedProfit: number
  currentStageCode: string
  riskLevel: RiskLevel
  progress: number
  remark?: string
  createTime: string
  updateTime: string
  version: number
}

export interface ProjectCreateInput {
  projectNo: string
  projectName: string
  projectType: ProjectType
  projectMode?: string
  leaderId: string
  departmentId: string
  startDate?: string
  endDate?: string
  budgetAmount: number
  expectedIncome: number
  expectedProfit: number
  riskLevel: RiskLevel
  remark?: string
}

export interface ProjectUpdateInput extends Omit<ProjectCreateInput, 'projectNo'> {
  actualStartDate?: string
  actualEndDate?: string
  version: number
}

export interface ProjectStage {
  id: string
  projectId: string
  stageCode: string
  stageName: string
  stageOrder: number
  startTime?: string
  endTime?: string
  actualStartTime?: string
  actualEndTime?: string
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED'
  responsiblePerson?: string
  approvalStatus: 'NOT_SUBMITTED' | 'PENDING' | 'APPROVED' | 'REJECTED'
  completionPercent: number
  remark?: string
  version: number
}

export interface ProjectTask {
  id: string
  projectId: string
  stageId: string
  parentTaskId?: string
  taskNo: string
  taskName: string
  responsiblePerson?: string
  planDate?: string
  actualDate?: string
  status: 'TODO' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED' | 'CANCELLED'
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
  progress: number
  sortNo: number
  remark?: string
  version: number
}

export interface ProjectMember {
  id: string
  projectId: string
  employeeId: string
  role: 'MANAGER' | 'CORE' | 'PARTICIPANT' | 'EXPERT'
  responsibilities?: string
  joinedDate: string
  leftDate?: string
  status: 'ACTIVE' | 'INACTIVE'
  remark?: string
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
