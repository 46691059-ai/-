<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElButton,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElProgress,
  ElSelect,
  ElSkeleton,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTag,
} from 'element-plus'
import {
  addProjectMember,
  createProjectTask,
  deleteProjectMember,
  deleteProjectTask,
  fetchProject,
  fetchProjectMembers,
  fetchProjectTasks,
  updateProjectStage,
  type MemberInput,
  type TaskInput,
} from '../../api/projects'
import type { ProjectDetail, ProjectMember, ProjectStage, ProjectTask } from '../../types/project'

const route = useRoute()
const router = useRouter()
const projectId = String(route.params.id)
const loading = ref(true)
const detail = ref<ProjectDetail>()
const stageDialog = ref(false)
const taskDialog = ref(false)
const memberDialog = ref(false)
const stageForm = ref<ProjectStage>()
const taskPage = ref(1)
const memberPage = ref(1)

const taskForm = reactive<TaskInput>({
  stageId: '',
  parentTaskId: undefined,
  taskCode: '',
  taskName: '',
  taskType: 'GENERAL',
  assigneeUserId: undefined,
  priority: 'MEDIUM',
  taskStatus: 'TODO',
  plannedStartDate: undefined,
  plannedEndDate: undefined,
  actualStartDate: undefined,
  actualEndDate: undefined,
  progress: 0,
  outputDesc: '',
  riskDesc: '',
  sortNo: 0,
  version: 0,
})

const memberForm = reactive<MemberInput>({
  userId: '',
  memberRole: 'PARTICIPANT',
  responsibilities: '',
  joinedDate: new Date().toISOString().slice(0, 10),
  leftDate: undefined,
  memberStatus: 'ACTIVE',
  version: 0,
})

const project = computed(() => detail.value?.project)
const currentStageName = computed(
  () =>
    detail.value?.stages.find((item) => item.stageCode === detail.value?.project.currentStageCode)
      ?.stageName ?? '-',
)
const stageStatusNames: Record<string, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  SKIPPED: '已跳过',
}

async function load() {
  loading.value = true
  try {
    detail.value = await fetchProject(projectId)
    taskPage.value = 1
    memberPage.value = 1
  } finally {
    loading.value = false
  }
}

async function loadTaskPage(page: number) {
  if (!detail.value) return
  const result = await fetchProjectTasks(projectId, page)
  detail.value.tasks = result.records
  detail.value.taskTotal = result.total
  taskPage.value = result.page
}

async function loadMemberPage(page: number) {
  if (!detail.value) return
  const result = await fetchProjectMembers(projectId, page)
  detail.value.members = result.records
  detail.value.memberTotal = result.total
  memberPage.value = result.page
}

function editStage(stage: ProjectStage) {
  stageForm.value = { ...stage }
  stageDialog.value = true
}

async function saveStage() {
  if (!stageForm.value) return
  await updateProjectStage(projectId, stageForm.value)
  ElMessage.success('阶段信息已更新，项目总进度已重新计算')
  stageDialog.value = false
  await load()
}

function openTask() {
  Object.assign(taskForm, {
    stageId: detail.value?.stages.find((stage) => stage.stageCode === project.value?.currentStageCode)?.id ?? '',
    parentTaskId: undefined,
    taskCode: '',
    taskName: '',
    taskType: 'GENERAL',
    assigneeUserId: undefined,
    priority: 'MEDIUM',
    taskStatus: 'TODO',
    plannedStartDate: undefined,
    plannedEndDate: undefined,
    actualStartDate: undefined,
    actualEndDate: undefined,
    progress: 0,
    outputDesc: '',
    riskDesc: '',
    sortNo: 0,
    version: 0,
  })
  taskDialog.value = true
}

async function saveTask() {
  if (!taskForm.stageId || !taskForm.taskCode || !taskForm.taskName) {
    ElMessage.warning('请完整填写任务阶段、编码和名称')
    return
  }
  await createProjectTask(projectId, { ...taskForm })
  ElMessage.success('任务已创建')
  taskDialog.value = false
  await load()
}

async function removeTask(task: ProjectTask) {
  await ElMessageBox.confirm(`确定删除任务“${task.taskName}”吗？`, '删除确认', { type: 'warning' })
  await deleteProjectTask(projectId, task.id)
  ElMessage.success('任务已删除')
  await load()
}

function removeTaskRow(row: unknown) {
  return removeTask(row as ProjectTask)
}

function openMember() {
  Object.assign(memberForm, {
    userId: '',
    memberRole: 'PARTICIPANT',
    responsibilities: '',
    joinedDate: new Date().toISOString().slice(0, 10),
    leftDate: undefined,
    memberStatus: 'ACTIVE',
    version: 0,
  })
  memberDialog.value = true
}

async function saveMember() {
  if (!memberForm.userId || !memberForm.joinedDate) {
    ElMessage.warning('请输入用户ID和加入日期')
    return
  }
  await addProjectMember(projectId, { ...memberForm })
  ElMessage.success('项目成员已添加')
  memberDialog.value = false
  await load()
}

async function removeMember(member: ProjectMember) {
  await ElMessageBox.confirm(`确定移除用户 ${member.userId} 吗？`, '移除确认', { type: 'warning' })
  await deleteProjectMember(projectId, member.id)
  ElMessage.success('成员已移除')
  await load()
}

function removeMemberRow(row: unknown) {
  return removeMember(row as ProjectMember)
}

function money(value?: number) {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(value ?? 0)
}

onMounted(load)
</script>

<template>
  <ElSkeleton v-if="loading" :rows="12" animated />
  <template v-else-if="detail && project">
    <div class="page-title page-title--actions">
      <div>
        <span class="page-eyebrow">{{ project.projectCode }}</span>
        <h1>{{ project.projectName }}</h1>
        <p>{{ project.description || '暂无项目说明' }}</p>
      </div>
      <div class="page-actions">
        <ElButton @click="router.push('/projects')">返回列表</ElButton>
        <ElButton type="primary" @click="router.push(`/projects/${projectId}/edit`)">编辑项目</ElButton>
      </div>
    </div>

    <section class="project-overview">
      <div class="project-overview__progress">
        <ElProgress type="dashboard" :percentage="Number(project.progress)" :width="120" />
        <div><span>当前阶段</span><strong>{{ currentStageName }}</strong></div>
      </div>
      <div class="project-overview__metric"><span>投资金额</span><strong>{{ money(project.investmentAmount) }}</strong></div>
      <div class="project-overview__metric"><span>预计收益</span><strong>{{ money(project.expectedIncome) }}</strong></div>
      <div class="project-overview__metric"><span>实际收益</span><strong>{{ money(project.actualIncome) }}</strong></div>
      <div class="project-overview__metric"><span>风险等级</span><ElTag :type="project.riskLevel === 'LOW' ? 'success' : project.riskLevel === 'MEDIUM' ? 'warning' : 'danger'">{{ project.riskLevel }}</ElTag></div>
    </section>

    <section class="lifecycle-track">
      <article
        v-for="stage in detail.stages"
        :key="stage.id"
        :class="['stage-card', `stage-card--${stage.stageStatus.toLowerCase()}`]"
        @click="editStage(stage)"
      >
        <div class="stage-card__number">{{ String(stage.stageOrder).padStart(2, '0') }}</div>
        <div>
          <span>{{ stageStatusNames[stage.stageStatus] }}</span>
          <h3>{{ stage.stageName }}</h3>
          <ElProgress :percentage="Number(stage.completionPercent)" :show-text="false" :stroke-width="5" />
        </div>
      </article>
    </section>

    <section class="data-panel">
      <ElTabs>
        <ElTabPane label="项目概览">
          <ElDescriptions :column="3" border>
            <ElDescriptionsItem label="项目类型">{{ project.projectType }}</ElDescriptionsItem>
            <ElDescriptionsItem label="所属组织">{{ project.orgId }}</ElDescriptionsItem>
            <ElDescriptionsItem label="项目负责人">{{ project.managerUserId }}</ElDescriptionsItem>
            <ElDescriptionsItem label="计划开始">{{ project.plannedStartDate || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="计划结束">{{ project.plannedEndDate || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="项目状态">{{ project.projectStatus }}</ElDescriptionsItem>
          </ElDescriptions>
        </ElTabPane>
        <ElTabPane :label="`任务 (${detail.taskTotal})`">
          <div class="tab-actions"><ElButton type="primary" @click="openTask">新增任务</ElButton></div>
          <ElTable :data="detail.tasks" stripe>
            <ElTableColumn prop="taskCode" label="任务编码" width="130" />
            <ElTableColumn prop="taskName" label="任务名称" min-width="200" />
            <ElTableColumn label="所属阶段" width="120">
              <template #default="{ row }">{{ detail.stages.find((stage) => stage.id === row.stageId)?.stageName }}</template>
            </ElTableColumn>
            <ElTableColumn prop="assigneeUserId" label="负责人ID" width="120" />
            <ElTableColumn prop="priority" label="优先级" width="100" />
            <ElTableColumn prop="taskStatus" label="状态" width="120" />
            <ElTableColumn label="进度" width="150">
              <template #default="{ row }"><ElProgress :percentage="Number(row.progress)" :stroke-width="6" /></template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="90">
              <template #default="{ row }"><ElButton link type="danger" @click="removeTaskRow(row)">删除</ElButton></template>
            </ElTableColumn>
          </ElTable>
          <ElPagination
            v-if="detail.taskTotal > 20"
            :current-page="taskPage"
            :page-size="20"
            :total="detail.taskTotal"
            layout="prev, pager, next, total"
            @current-change="loadTaskPage"
          />
        </ElTabPane>
        <ElTabPane :label="`成员 (${detail.memberTotal})`">
          <div class="tab-actions"><ElButton type="primary" @click="openMember">添加成员</ElButton></div>
          <ElTable :data="detail.members" stripe>
            <ElTableColumn prop="userId" label="用户ID" width="160" />
            <ElTableColumn prop="memberRole" label="项目角色" width="140" />
            <ElTableColumn prop="responsibilities" label="职责" min-width="240" />
            <ElTableColumn prop="joinedDate" label="加入日期" width="120" />
            <ElTableColumn prop="memberStatus" label="状态" width="100" />
            <ElTableColumn label="操作" width="90">
              <template #default="{ row }"><ElButton link type="danger" @click="removeMemberRow(row)">移除</ElButton></template>
            </ElTableColumn>
          </ElTable>
          <ElPagination
            v-if="detail.memberTotal > 20"
            :current-page="memberPage"
            :page-size="20"
            :total="detail.memberTotal"
            layout="prev, pager, next, total"
            @current-change="loadMemberPage"
          />
        </ElTabPane>
      </ElTabs>
    </section>

    <ElDialog v-model="stageDialog" title="更新项目阶段" width="620px">
      <ElForm v-if="stageForm" :model="stageForm" label-position="top">
        <div class="form-grid">
          <ElFormItem label="阶段状态">
            <ElSelect v-model="stageForm.stageStatus"><ElOption label="未开始" value="NOT_STARTED" /><ElOption label="进行中" value="IN_PROGRESS" /><ElOption label="已完成" value="COMPLETED" /><ElOption label="已跳过" value="SKIPPED" /></ElSelect>
          </ElFormItem>
          <ElFormItem label="审批状态">
            <ElSelect v-model="stageForm.approvalStatus"><ElOption label="未提交" value="NOT_SUBMITTED" /><ElOption label="审批中" value="PENDING" /><ElOption label="已通过" value="APPROVED" /><ElOption label="已驳回" value="REJECTED" /></ElSelect>
          </ElFormItem>
          <ElFormItem label="阶段负责人ID"><ElInput v-model="stageForm.ownerUserId" /></ElFormItem>
          <ElFormItem label="完成比例"><ElInputNumber v-model="stageForm.completionPercent" :min="0" :max="100" /></ElFormItem>
          <ElFormItem label="计划开始"><ElDatePicker v-model="stageForm.plannedStartDate" value-format="YYYY-MM-DD" /></ElFormItem>
          <ElFormItem label="计划结束"><ElDatePicker v-model="stageForm.plannedEndDate" value-format="YYYY-MM-DD" /></ElFormItem>
          <ElFormItem label="里程碑说明" class="form-grid__wide"><ElInput v-model="stageForm.milestoneDesc" type="textarea" /></ElFormItem>
          <ElFormItem label="风险摘要" class="form-grid__wide"><ElInput v-model="stageForm.riskSummary" type="textarea" /></ElFormItem>
        </div>
      </ElForm>
      <template #footer><ElButton @click="stageDialog = false">取消</ElButton><ElButton type="primary" @click="saveStage">保存</ElButton></template>
    </ElDialog>

    <ElDialog v-model="taskDialog" title="新增项目任务" width="680px">
      <ElForm :model="taskForm" label-position="top">
        <div class="form-grid">
          <ElFormItem label="所属阶段"><ElSelect v-model="taskForm.stageId"><ElOption v-for="stage in detail.stages" :key="stage.id" :label="stage.stageName" :value="stage.id" /></ElSelect></ElFormItem>
          <ElFormItem label="任务编码"><ElInput v-model="taskForm.taskCode" /></ElFormItem>
          <ElFormItem label="任务名称"><ElInput v-model="taskForm.taskName" /></ElFormItem>
          <ElFormItem label="负责人用户ID"><ElInput v-model="taskForm.assigneeUserId" /></ElFormItem>
          <ElFormItem label="优先级"><ElSelect v-model="taskForm.priority"><ElOption label="低" value="LOW" /><ElOption label="中" value="MEDIUM" /><ElOption label="高" value="HIGH" /><ElOption label="紧急" value="URGENT" /></ElSelect></ElFormItem>
          <ElFormItem label="任务状态"><ElSelect v-model="taskForm.taskStatus"><ElOption label="待办" value="TODO" /><ElOption label="进行中" value="IN_PROGRESS" /><ElOption label="阻塞" value="BLOCKED" /><ElOption label="已完成" value="COMPLETED" /></ElSelect></ElFormItem>
          <ElFormItem label="计划开始"><ElDatePicker v-model="taskForm.plannedStartDate" value-format="YYYY-MM-DD" /></ElFormItem>
          <ElFormItem label="计划结束"><ElDatePicker v-model="taskForm.plannedEndDate" value-format="YYYY-MM-DD" /></ElFormItem>
          <ElFormItem label="任务说明" class="form-grid__wide"><ElInput v-model="taskForm.outputDesc" type="textarea" /></ElFormItem>
        </div>
      </ElForm>
      <template #footer><ElButton @click="taskDialog = false">取消</ElButton><ElButton type="primary" @click="saveTask">创建任务</ElButton></template>
    </ElDialog>

    <ElDialog v-model="memberDialog" title="添加项目成员" width="580px">
      <ElForm :model="memberForm" label-position="top">
        <div class="form-grid">
          <ElFormItem label="用户ID"><ElInput v-model="memberForm.userId" /></ElFormItem>
          <ElFormItem label="项目角色"><ElSelect v-model="memberForm.memberRole"><ElOption label="项目负责人" value="MANAGER" /><ElOption label="核心成员" value="CORE" /><ElOption label="参与成员" value="PARTICIPANT" /><ElOption label="专家" value="EXPERT" /></ElSelect></ElFormItem>
          <ElFormItem label="加入日期"><ElDatePicker v-model="memberForm.joinedDate" value-format="YYYY-MM-DD" /></ElFormItem>
          <ElFormItem label="成员状态"><ElSelect v-model="memberForm.memberStatus"><ElOption label="有效" value="ACTIVE" /><ElOption label="无效" value="INACTIVE" /></ElSelect></ElFormItem>
          <ElFormItem label="职责说明" class="form-grid__wide"><ElInput v-model="memberForm.responsibilities" type="textarea" /></ElFormItem>
        </div>
      </ElForm>
      <template #footer><ElButton @click="memberDialog = false">取消</ElButton><ElButton type="primary" @click="saveMember">添加成员</ElButton></template>
    </ElDialog>
  </template>
</template>
