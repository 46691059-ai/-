import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchProjects, updateProjectStage } from './projects'
import { http } from '../utils/http'
import type { ProjectStage } from '../types/project'

vi.mock('../utils/http', () => ({
  http: {
    get: vi.fn(),
    put: vi.fn(),
  },
}))

describe('project lifecycle api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('passes pagination and lifecycle filters to the list endpoint', async () => {
    vi.mocked(http.get).mockResolvedValue({
      data: { data: { records: [], total: 0, page: 1, size: 20 } },
    })

    await fetchProjects({ page: 1, size: 20, stageCode: 'IMPLEMENTATION' })

    expect(http.get).toHaveBeenCalledWith('/projects', {
      params: { page: 1, size: 20, stageCode: 'IMPLEMENTATION' },
    })
  })

  it('sends optimistic-lock version when updating a stage', async () => {
    const stage: ProjectStage = {
      id: '3001',
      projectId: '2001',
      stageCode: 'INITIATION',
      stageName: '立项审批',
      stageOrder: 2,
      status: 'IN_PROGRESS',
      approvalStatus: 'PENDING',
      completionPercent: 35,
      version: 4,
    }
    vi.mocked(http.put).mockResolvedValue({ data: { data: stage } })

    await updateProjectStage('2001', stage)

    expect(http.put).toHaveBeenCalledWith(
      '/projects/2001/stages/3001',
      expect.objectContaining({ version: 4, completionPercent: 35 }),
    )
  })
})
