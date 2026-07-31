<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ProjectFormPanel, { type ProjectFormModel } from '../../components/project/ProjectFormPanel.vue'
import { createProject } from '../../api/projects'

const router = useRouter()
const submitting = ref(false)

async function submit(value: ProjectFormModel) {
  submitting.value = true
  try {
    const result = await createProject({
      projectCode: value.projectCode,
      projectName: value.projectName,
      projectType: value.projectType,
      orgId: value.orgId,
      managerUserId: value.managerUserId,
      description: value.description,
      plannedStartDate: value.plannedStartDate,
      plannedEndDate: value.plannedEndDate,
      investmentAmount: value.investmentAmount,
      expectedIncome: value.expectedIncome,
      riskLevel: value.riskLevel,
    })
    ElMessage.success('项目已创建，五个生命周期阶段已初始化')
    await router.push(`/projects/${result.project.id}`)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-title">
    <div><span class="page-eyebrow">PROJECT CREATION</span><h1>新增项目</h1><p>建立项目主档并启动全生命周期管理。</p></div>
  </div>
  <ProjectFormPanel mode="create" :submitting="submitting" @submit="submit" @cancel="$router.push('/projects')" />
</template>
