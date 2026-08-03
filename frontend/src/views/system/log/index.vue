<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cleanLogs, deleteLog, getLogDetail, getLogPage } from '../../../api/log'
import type { LogQuery, LogRecord, LogStatus } from '../../../types/log'

const records = ref<LogRecord[]>([])
const total = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref<LogRecord>()
const range = ref<[string, string]>()
const query = reactive<LogQuery>({ page: 1, size: 20 })
const statusOptions: Array<{ label: string; value: LogStatus }> = [
  { label: '成功', value: 'SUCCESS' }, { label: '失败', value: 'FAIL' }, { label: '异常', value: 'ERROR' },
]

async function loadData() {
  loading.value = true
  try {
    query.startTime = range.value?.[0]
    query.endTime = range.value?.[1]
    const page = await getLogPage(query)
    records.value = page.records
    total.value = page.total
  } finally { loading.value = false }
}

async function showDetail(row: LogRecord) {
  detail.value = await getLogDetail(row.id)
  detailVisible.value = true
}

async function remove(row: LogRecord) {
  await ElMessageBox.confirm('确认删除该审计日志吗？', '删除确认', { type: 'warning' })
  await deleteLog(row.id)
  ElMessage.success('日志已删除')
  await loadData()
}

async function clean() {
  const before = range.value?.[1]
  if (!before) return ElMessage.warning('请先选择清理截止时间')
  await ElMessageBox.confirm(`确认清理 ${before.replace('T', ' ')} 之前的日志吗？`, '清理确认', { type: 'warning' })
  const count = await cleanLogs(before)
  ElMessage.success(`已清理 ${count} 条日志`)
  await loadData()
}

function reset() {
  Object.assign(query, { username: undefined, logType: undefined, moduleName: undefined, status: undefined, page: 1 })
  range.value = undefined
  void loadData()
}

function statusType(status: LogStatus) {
  return status === 'SUCCESS' ? 'success' : status === 'FAIL' ? 'warning' : 'danger'
}

onMounted(loadData)
</script>

<template>
  <section class="log-page">
    <header class="page-heading">
      <div><p>系统管理中心</p><h1>操作日志</h1><span>查询登录、操作和异常审计记录，支持 TraceId 链路定位</span></div>
      <el-button v-permission="'system:log:delete'" type="danger" plain @click="clean">按时间清理</el-button>
    </header>
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="用户"><el-input v-model="query.username" clearable placeholder="用户名" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="query.logType" clearable><el-option label="操作" value="OPERATION"/><el-option label="登录成功" value="LOGIN_SUCCESS"/><el-option label="登录失败" value="LOGIN_FAIL"/><el-option label="异常" value="ERROR"/></el-select></el-form-item>
        <el-form-item label="模块"><el-input v-model="query.moduleName" clearable placeholder="如 SYSTEM_USER" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value"/></el-select></el-form-item>
        <el-form-item label="时间"><el-date-picker v-model="range" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" start-placeholder="开始时间" end-placeholder="结束时间"/></el-form-item>
        <el-form-item><el-button v-permission="'system:log:query'" type="primary" @click="query.page=1; loadData()">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="records">
        <el-table-column prop="createTime" label="时间" width="180"/>
        <el-table-column prop="username" label="用户" width="120"/>
        <el-table-column prop="logType" label="类型" width="130"/>
        <el-table-column prop="moduleName" label="模块" width="150"/>
        <el-table-column prop="operation" label="操作" min-width="190" show-overflow-tooltip/>
        <el-table-column prop="requestUrl" label="请求地址" min-width="180" show-overflow-tooltip/>
        <el-table-column prop="durationMs" label="耗时(ms)" width="100"/>
        <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ scope.row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right"><template #default="scope"><el-button v-permission="'system:log:detail'" link type="primary" @click="showDetail(scope.row)">详情</el-button><el-button v-permission="'system:log:delete'" link type="danger" @click="remove(scope.row)">删除</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="loadData"/>
    </el-card>
    <el-drawer v-model="detailVisible" title="日志详情" size="620px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="TraceId">{{ detail.traceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ detail.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求">{{ detail.requestMethod }} {{ detail.requestUrl }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求参数"><pre>{{ detail.requestParams || '-' }}</pre></el-descriptions-item>
        <el-descriptions-item label="响应摘要"><pre>{{ detail.responseResult || '-' }}</pre></el-descriptions-item>
        <el-descriptions-item label="异常信息"><pre>{{ detail.errorMessage || '-' }}</pre></el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>

<style scoped>
.log-page { display: grid; gap: 16px; }
.page-heading { display: flex; align-items: flex-end; justify-content: space-between; }
.page-heading p { margin: 0; color: var(--el-color-primary); font-weight: 600; }
.page-heading h1 { margin: 4px 0; }
.page-heading span { color: var(--el-text-color-secondary); }
.query-card :deep(.el-form-item) { margin-bottom: 0; }
.table-card :deep(.el-pagination) { justify-content: flex-end; margin-top: 16px; }
pre { margin: 0; white-space: pre-wrap; word-break: break-all; font-family: inherit; }
</style>
