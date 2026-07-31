<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElButton, ElDatePicker, ElDescriptions, ElDescriptionsItem, ElDialog, ElForm,
  ElFormItem, ElInput, ElInputNumber, ElMessage, ElMessageBox, ElOption,
  ElProgress, ElSelect, ElSkeleton, ElTabPane, ElTable, ElTableColumn, ElTabs, ElTag,
} from 'element-plus'
import {
  addProjectMember, createProjectTask, deleteProjectMember, deleteProjectTask,
  fetchProject, updateProjectStage, type MemberInput, type TaskInput,
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

const taskForm = reactive<TaskInput>({
  stageId: '',
  parentTaskId: undefined,
  taskNo: '',
  taskName: '',
  responsiblePerson: undefined,
  planDate: undefined,
  actualDate: undefined,
  status: 'TODO',
  priority: 'MEDIUM',
  progress: 0,
  sortNo: 0,
  remark: '',
  version: 0,
})

const memberForm = reactive<MemberInput>({
  employeeId: '',
  role: 'PARTICIPANT',
  responsibilities: '',
  joinedDate: new Date().toISOString().slice(0, 10),
  leftDate: undefined,
  status: 'ACTIVE',
  remark: '',
  version: 0,
})

const project = computed(() => detail.value?.project)
const currentStageName = computed(
  () => detail.value?.stages.find(
    (item) => item.stageCode === detail.value?.project.currentStageCode,
  )?.stageName ?? '-',
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
  } finally {
    loading.value = false
  }
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
    stageId: detail.value?.stages.find(
      (stage) => stage.stageCode === project.value?.currentStageCode,
    )?.id ?? '',
    parentTaskId: undefined,
    taskNo: '',
    taskName: '',
    responsiblePerson: undefined,
    planDate: undefined,
    actualDate: undefined,
    status: 'TODO',
    priority: 'MEDIUM',
    progress: 0,
    sortNo: 0,
    remark: '',
    version: 0,
  })
  taskDialog.value = true
}

async function saveTask() {
  if (!taskForm.stageId || !taskForm.taskNo || !taskForm.taskName) {
    ElMessage.warning('请完整填写任务阶段、编号和名称')
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
    employeeId: '',
    role: 'PARTICIPANT',
    responsibilities: '',
    joinedDate: new Date().toISOString().slice(0, 10),
    leftDate: undefined,
    status: 'ACTIVE',
    remark: '',
    version: 0,
  })
  memberDialog.value = true
}

async function saveMember() {
  if (!memberForm.employeeId || !memberForm.joinedDate) {
    ElMessage.warning('请输入员工ID和加入日期')
    return
  }
  await addProjectMember(projectId, { ...memberForm })
  ElMessage.success('项目成员已添加')
  memberDialog.value = false
  await load()
}

async function removeMember(member: ProjectMember) {
  await ElMessageBox.confirm(`确定移除员工 ${member.employeeId} 吗？`, '移除确认', { type: 'warning' })
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
        <span class="page-eyebrow">{{ project.projectNo }}</span>
        <h1>{{ project.projectName }}</h1>
        <p>{{ project.remark || '暂无项目备注' }}</p>
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
      <div class="project-overview__metric"><span>预算金额</span><strong>{{ money(project.budgetAmount) }}</strong></div>
      <div class="project-overview__metric"><span>预计收入</span><strong>{{ money(project.expectedIncome) }}</strong></div>
      <div class="project-overview__metric"><span>预计利润</span><strong>{{ money(project.expectedProfit) }}</strong></div>
      <div class="project-overview__metric">
        <span>风险等级</span>
        <ElTag :type="project.riskLevel === 'LOW' ? 'success' : project.riskLevel === 'MEDIUM' ? 'warning' : 'danger'">
          {{ project.riskLevel }}
        </ElTag>
      </div>
    </section>

    <section class="lifecycle-track">
      <article
        v-for="stage in detail.stages"
        :key="stage.id"
        :class="['stage-card', `stage-card--${stage.status.toLowerCase()}`]"
        @click="editStage(stage)"
      >
        <div class="stage-card__number">{{ String(stage.stageOrder).padStart(2, '0') }}</div>
        <div>
          <span>{{ stageStatusNames[stage.status] }}</span>
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
            <ElDescriptionsItem label="项目模式">{{ project.projectMode || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="责任部门">{{ project.departmentId }}</ElDescriptionsItem>
            <ElDescriptionsItem label="项目负责人">{{ project.leaderId }}</ElDescriptionsItem>
            <ElDescriptionsItem label="计划开始">{{ project.startDate || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="计划结束">{{ project.endDate || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="项目状态">{{ project.status }}</ElDescriptionsItem>
          </ElDescriptions>
        </ElTabPane>

        <ElTabPane :label="`任务 (${detail.taskTotal})`">
          <div class="tab-actions"><ElButton type="primary" @click="openTask">新增任务</ElButton></div>
          <ElTable :data="detail.tasks" stripe>
            <ElTableColumn prop="taskNo" label="任务编号" width="140" />
            <ElTableColumn prop="taskName" label="任务名称" min-width="200" />
            <ElTableColumn prop="responsiblePerson" label="负责人员工ID" width="150" />
            <ElTableColumn prop="planDate" label="计划日期" width="120" />
            <ElTableColumn prop="priority" label="优先级" width="100" />
            <ElTableColumn prop="status" label="状态" width="120" />
            <ElTableColumn label="进度" width="150">
              <template #default="{ row }"><ElProgress :percentage="Number(row.progress)" /></template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="90">
              <template #default="{ row }"><ElButton link type="danger" @click="removeTaskRow(row)">删除</ElButton></template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <ElTabPane :label="`成员 (${detail.memberTotal})`">
          <div class="tab-actions"><ElButton type="primary" @click="openMember">添加成员</ElButton></div>
          <ElTable :data="detail.members" stripe>
            <ElTableColumn prop="employeeId" label="员工ID" width="160" />
            <ElTableColumn prop="role" label="项目角色" width="140" />
            <ElTableColumn prop="responsibilities" label="职责" min-width="240" />
            <ElTableColumn prop="joinedDate" label="加入日期" width="120" />
            <ElTableColumn prop="status" label="状态" width="100" />
            <ElTableColumn label="操作" width="90">
              <template #default="{ row }"><ElButton link type="danger" @click="removeMemberRow(row)">移除</ElButton></template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>
      </ElTabs>
    </section>

    <ElDialog v-model="stageDialog" title="更新项目阶段" width="620px">
      <ElForm v-if="stageForm" :model="stageForm" label-position="top">
        <div class="form-grid">
          <ElFormItem label="阶段状态">
            <ElSelect v-model="stageForm.status">
              <ElOption label="未开始" value="NOT_STARTED" />
              <ElOption label="进行中" value="IN_PROGRESS" />
              <ElOption label="已完成" value="COMPLETED" />
              <ElOption label="已跳过" value="SKIPPED" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="审批状态">
            <ElSelect v-model="stageForm.approvalStatus">
              <ElOption label="未提交" value="NOT_SUBMITTED" />
              <ElOption label="审批中" value="PENDING" />
              <ElOption label="已通过" value="APPROVED" />
              <ElOption label="已驳回" value="REJECTED" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="负责人员工ID"><ElInput v-model="stageForm.responsiblePerson" /></ElFormItem>
          <ElFormItem label="完成比例"><ElInputNumber v-model="stageForm.completionPercent" :min="0" :max="100" /></ElFormItem>
          <ElFormItem label="计划开始"><ElDatePicker v-model="stageForm.startTime" value-format="YYYY-MM-DD" /></ElFormItem>
          <ElFormItem label="计划结束"><ElDatePicker v-model="stageForm.endTime" value-format="YYYY-MM-DD" /></ElFormItem>
          <ElFormItem label="阶段备注" class="form-grid__wide"><ElInput v-model="stageForm.remark" type="textarea" /></ElFormItem>
        </div>
      </ElForm>
      <template #footer>
        <ElButton @click="stageDialog = false">取消</ElButton>
        <ElButton type="primary" @click="saveStage">保存</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="taskDialog" title="新增项目任务" width="680px">
      <ElForm :model="taskForm" label-position="top">
        <div class="form-grid">
          <ElFormItem label="所属阶段">
            <ElSelect v-model="taskForm.stageId">
              <ElOption v-for="stage in detail.stages" :key="stage.id" :label="stage.stageName" :value="stage.id" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="任务编号"><ElInput v-model="taskForm.taskNo" /></ElFormItem>
          <ElFormItem label="任务名称"><ElInput v-model="taskForm.taskName" /></ElFormItem>
          <ElFormItem label="负责人员工ID"><ElInput v-model="taskForm.responsiblePerson" /></ElFormItem>
          <ElFormItem label="优先级">
            <ElSelect v-model="taskForm.priority">
              <ElOption label="低" value="LOW" /><ElOption label="中" value="MEDIUM" />
              <ElOption label="高" value="HIGH" /><ElOption label="紧急" value="URGENT" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="任务状态">
            <ElSelect v-model="taskForm.status">
              <ElOption label="待办" value="TODO" /><ElOption label="进行中" value="IN_PROGRESS" />
              <ElOption label="阻塞" value="BLOCKED" /><ElOption label="已完成" value="COMPLETED" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="计划日期"><ElDatePicker v-model="taskForm.planDate" value-format="YYYY-MM-DD" /></ElFormItem>
          <ElFormItem label="任务备注" class="form-grid__wide"><ElInput v-model="taskForm.remark" type="textarea" /></ElFormItem>
        </div>
      </ElForm>
      <template #footer>
        <ElButton @click="taskDialog = false">取消</ElButton>
        <ElButton type="primary" @click="saveTask">创建任务</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="memberDialog" title="添加项目成员" width="580px">
      <ElForm :model="memberForm" label-position="top">
        <div class="form-grid">
          <ElFormItem label="员工ID"><ElInput v-model="memberForm.employeeId" /></ElFormItem>
          <ElFormItem label="项目角色">
            <ElSelect v-model="memberForm.role">
              <ElOption label="项目负责人" value="MANAGER" /><ElOption label="核心成员" value="CORE" />
              <ElOption label="参与成员" value="PARTICIPANT" /><ElOption label="专家" value="EXPERT" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="加入日期"><ElDatePicker v-model="memberForm.joinedDate" value-format="YYYY-MM-DD" /></ElFormItem>
          <ElFormItem label="成员状态">
            <ElSelect v-model="memberForm.status">
              <ElOption label="有效" value="ACTIVE" /><ElOption label="无效" value="INACTIVE" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="职责说明" class="form-grid__wide"><ElInput v-model="memberForm.responsibilities" type="textarea" /></ElFormItem>
        </div>
      </ElForm>
      <template #footer>
        <ElButton @click="memberDialog = false">取消</ElButton>
        <ElButton type="primary" @click="saveMember">添加成员</ElButton>
      </template>
    </ElDialog>
  </template>
</template>
