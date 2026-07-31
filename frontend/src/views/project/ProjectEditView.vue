<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElSkeleton } from 'element-plus'
import ProjectFormPanel, { type ProjectFormModel } from '../../components/project/ProjectFormPanel.vue'
import { fetchProject, updateProject } from '../../api/projects'

const route = useRoute()
const router = useRouter()
const projectId = String(route.params.id)
const loading = ref(true)
const submitting = ref(false)
const initial = ref<Partial<ProjectFormModel>>()

onMounted(async () => {
  try {
    const { project } = await fetchProject(projectId)
    initial.value = { ...project }
  } finally {
    loading.value = false
  }
})

async function submit(value: ProjectFormModel) {
  submitting.value = true
  try {
    await updateProject(projectId, {
      projectName: value.projectName,
      projectType: value.projectType,
      projectMode: value.projectMode,
      departmentId: value.departmentId,
      leaderId: value.leaderId,
      startDate: value.startDate,
      endDate: value.endDate,
      actualStartDate: value.actualStartDate,
      actualEndDate: value.actualEndDate,
      budgetAmount: value.budgetAmount,
      expectedIncome: value.expectedIncome,
      expectedProfit: value.expectedProfit,
      riskLevel: value.riskLevel,
      remark: value.remark,
      version: value.version,
    })
    ElMessage.success('项目已更新')
    await router.push(`/projects/${projectId}`)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-title">
    <div><span class="page-eyebrow">PROJECT EDITING</span><h1>编辑项目</h1><p>维护项目计划、经营指标和执行状态。</p></div>
  </div>
  <ElSkeleton v-if="loading" :rows="10" animated />
  <ProjectFormPanel
    v-else
    mode="edit"
    :initial="initial"
    :submitting="submitting"
    @submit="submit"
    @cancel="$router.push(`/projects/${projectId}`)"
  />
</template>
