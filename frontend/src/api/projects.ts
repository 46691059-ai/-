import { http } from '../utils/http'
import type { ApiResponse } from '../types/api'
import type {
  Project,
  ProjectCreateInput,
  ProjectDetail,
  ProjectMember,
  ProjectStage,
  ProjectTask,
  ProjectUpdateInput,
} from '../types/project'

export interface PageResponse<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export async function fetchProjects(params: Record<string, unknown>) {
  const response = await http.get<ApiResponse<PageResponse<Project>>>('/projects', { params })
  return response.data.data
}

export async function fetchProject(id: string) {
  const response = await http.get<ApiResponse<ProjectDetail>>(`/projects/${id}`)
  return response.data.data
}

export async function createProject(input: ProjectCreateInput) {
  const response = await http.post<ApiResponse<ProjectDetail>>('/projects', input)
  return response.data.data
}

export async function updateProject(id: string, input: ProjectUpdateInput) {
  const response = await http.put<ApiResponse<Project>>(`/projects/${id}`, input)
  return response.data.data
}

export async function deleteProject(id: string) {
  await http.delete(`/projects/${id}`)
}

export async function updateProjectStage(projectId: string, stage: ProjectStage) {
  const response = await http.put<ApiResponse<ProjectStage>>(
    `/projects/${projectId}/stages/${stage.id}`,
    {
      ownerUserId: stage.ownerUserId || null,
      plannedStartDate: stage.plannedStartDate || null,
      plannedEndDate: stage.plannedEndDate || null,
      actualStartDate: stage.actualStartDate || null,
      actualEndDate: stage.actualEndDate || null,
      stageStatus: stage.stageStatus,
      approvalStatus: stage.approvalStatus,
      completionPercent: stage.completionPercent,
      milestoneDesc: stage.milestoneDesc,
      riskSummary: stage.riskSummary,
      version: stage.version,
    },
  )
  return response.data.data
}

export type TaskInput = Omit<ProjectTask, 'id' | 'projectId'>

export async function createProjectTask(projectId: string, input: TaskInput) {
  const response = await http.post<ApiResponse<ProjectTask>>(`/projects/${projectId}/tasks`, input)
  return response.data.data
}

export async function updateProjectTask(projectId: string, taskId: string, input: TaskInput) {
  const response = await http.put<ApiResponse<ProjectTask>>(
    `/projects/${projectId}/tasks/${taskId}`,
    input,
  )
  return response.data.data
}

export async function deleteProjectTask(projectId: string, taskId: string) {
  await http.delete(`/projects/${projectId}/tasks/${taskId}`)
}

export async function fetchProjectTasks(projectId: string, page = 1, size = 20) {
  const response = await http.get<ApiResponse<PageResponse<ProjectTask>>>(
    `/projects/${projectId}/tasks`,
    { params: { page, size } },
  )
  return response.data.data
}

export type MemberInput = Omit<ProjectMember, 'id' | 'projectId'>

export async function addProjectMember(projectId: string, input: MemberInput) {
  const response = await http.post<ApiResponse<ProjectMember>>(
    `/projects/${projectId}/members`,
    input,
  )
  return response.data.data
}

export async function updateProjectMember(projectId: string, memberId: string, input: MemberInput) {
  const response = await http.put<ApiResponse<ProjectMember>>(
    `/projects/${projectId}/members/${memberId}`,
    input,
  )
  return response.data.data
}

export async function deleteProjectMember(projectId: string, memberId: string) {
  await http.delete(`/projects/${projectId}/members/${memberId}`)
}

export async function fetchProjectMembers(projectId: string, page = 1, size = 20) {
  const response = await http.get<ApiResponse<PageResponse<ProjectMember>>>(
    `/projects/${projectId}/members`,
    { params: { page, size } },
  )
  return response.data.data
}
