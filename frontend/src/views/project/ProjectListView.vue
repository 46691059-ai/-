<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElButton,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElProgress,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { deleteProject, fetchProjects } from '../../api/projects'
import type { Project } from '../../types/project'

const router = useRouter()
const loading = ref(false)
const rows = ref<Project[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  status: '',
  stageCode: '',
})

const stageNames: Record<string, string> = {
  RESERVE: '项目储备',
  INITIATION: '立项审批',
  IMPLEMENTATION: '建设实施',
  OPERATION: '运营管理',
  ACCEPTANCE: '验收评价',
}

const statusNames: Record<string, string> = {
  DRAFT: '草稿',
  RESERVED: '储备中',
  IN_PROGRESS: '进行中',
  SUSPENDED: '已暂停',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

async function load() {
  loading.value = true
  try {
    const result = await fetchProjects(query)
    rows.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, { page: 1, size: 20, keyword: '', status: '', stageCode: '' })
  load()
}

async function remove(project: Project) {
  await ElMessageBox.confirm(
    `确定删除项目“${project.projectName}”吗？项目阶段、任务和成员将同步逻辑删除。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  await deleteProject(project.id)
  ElMessage.success('项目已删除')
  await load()
}

function removeRow(row: unknown) {
  return remove(row as Project)
}

function money(value: number) {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(value)
}

onMounted(load)
</script>

<template>
  <div class="page-title page-title--actions">
    <div>
      <span class="page-eyebrow">PROJECT PORTFOLIO</span>
      <h1>项目全生命周期</h1>
      <p>统一管理项目储备、立项、实施、运营与验收评价。</p>
    </div>
    <ElButton type="primary" :icon="Plus" @click="router.push('/projects/create')">新增项目</ElButton>
  </div>

  <section class="summary-band">
    <div><span>项目总数</span><strong>{{ total }}</strong></div>
    <div><span>本页进行中</span><strong>{{ rows.filter((item) => item.projectStatus === 'IN_PROGRESS').length }}</strong></div>
    <div><span>本页重大风险</span><strong>{{ rows.filter((item) => item.riskLevel === 'CRITICAL').length }}</strong></div>
    <p>项目数据贯穿投资、合同、资金、风险与经营分析。</p>
  </section>

  <section class="data-panel">
    <div class="filter-bar">
      <ElInput
        v-model="query.keyword"
        :prefix-icon="Search"
        placeholder="项目编码 / 项目名称"
        clearable
        @keyup.enter="query.page = 1; load()"
      />
      <ElSelect v-model="query.status" placeholder="全部状态" clearable>
        <ElOption v-for="(label, value) in statusNames" :key="value" :label="label" :value="value" />
      </ElSelect>
      <ElSelect v-model="query.stageCode" placeholder="全部阶段" clearable>
        <ElOption v-for="(label, value) in stageNames" :key="value" :label="label" :value="value" />
      </ElSelect>
      <ElButton type="primary" plain @click="query.page = 1; load()">查询</ElButton>
      <ElButton @click="reset">重置</ElButton>
    </div>

    <ElTable v-loading="loading" :data="rows" stripe @row-click="(row: Project) => router.push(`/projects/${row.id}`)">
      <ElTableColumn prop="projectCode" label="项目编码" width="150" />
      <ElTableColumn prop="projectName" label="项目名称" min-width="220" show-overflow-tooltip />
      <ElTableColumn label="当前阶段" width="120">
        <template #default="{ row }">{{ stageNames[row.currentStageCode] ?? row.currentStageCode }}</template>
      </ElTableColumn>
      <ElTableColumn label="投资金额" width="150" align="right">
        <template #default="{ row }">{{ money(row.investmentAmount) }}</template>
      </ElTableColumn>
      <ElTableColumn label="进度" width="150">
        <template #default="{ row }"><ElProgress :percentage="Number(row.progress)" :stroke-width="7" /></template>
      </ElTableColumn>
      <ElTableColumn label="风险" width="90" align="center">
        <template #default="{ row }">
          <ElTag :type="row.riskLevel === 'LOW' ? 'success' : row.riskLevel === 'MEDIUM' ? 'warning' : 'danger'" effect="plain">
            {{ { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '重大' }[row.riskLevel as string] }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="状态" width="100">
        <template #default="{ row }">{{ statusNames[row.projectStatus] }}</template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <ElButton link type="primary" @click.stop="router.push(`/projects/${row.id}`)">详情</ElButton>
          <ElButton link @click.stop="router.push(`/projects/${row.id}/edit`)">编辑</ElButton>
          <ElButton link type="danger" @click.stop="removeRow(row)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElPagination
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      layout="total, sizes, prev, pager, next"
      :page-sizes="[10, 20, 50, 100]"
      @change="load"
    />
  </section>
</template>
