<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createOrg, deleteOrg, getLeaderOptions, getOrgDetail, getOrgPage, getOrgTree, updateOrg } from '../../../api/org'
import type { LeaderOption, OrgForm, OrgQuery, OrgRecord, OrgTreeNode, OrgType } from '../../../types/org'

const typeOptions: Array<{ label: string; value: OrgType }> = [
  { label: '公司', value: 'COMPANY' },
  { label: '部门', value: 'DEPARTMENT' },
  { label: '团队/项目组织', value: 'PROJECT_TEAM' },
]
const typeLabel = (type?: string) => typeOptions.find((item) => item.value === type)?.label || type || '-'

const treeLoading = ref(false)
const tableLoading = ref(false)
const saving = ref(false)
const tree = ref<OrgTreeNode[]>([])
const selectedNode = ref<OrgTreeNode | null>(null)
const organizations = ref<OrgRecord[]>([])
const leaders = ref<LeaderOption[]>([])
const total = ref(0)
const query = reactive<OrgQuery>({ page: 1, size: 20 })

const dialogVisible = ref(false)
const mode = ref<'create' | 'edit'>('create')
const formRef = ref<FormInstance>()
const emptyForm = (): OrgForm => ({ orgCode: '', orgName: '', orgType: 'DEPARTMENT', status: 1, sortNo: 0, remark: '' })
const form = reactive<OrgForm>(emptyForm())
const rules: FormRules<OrgForm> = {
  orgCode: [{ required: true, message: '请输入组织编码', trigger: 'blur' }],
  orgName: [{ required: true, message: '请输入组织名称', trigger: 'blur' }],
  orgType: [{ required: true, message: '请选择组织类型', trigger: 'change' }],
}
const canChooseNoParent = computed(() => form.orgType === 'COMPANY')

async function loadTree() {
  treeLoading.value = true
  try {
    tree.value = await getOrgTree()
    if (selectedNode.value) selectedNode.value = findTreeNode(tree.value, selectedNode.value.id)
  } finally {
    treeLoading.value = false
  }
}

async function loadPage() {
  tableLoading.value = true
  try {
    const page = await getOrgPage(query)
    organizations.value = page.records
    total.value = page.total
  } finally {
    tableLoading.value = false
  }
}

function findTreeNode(nodes: OrgTreeNode[], id: string): OrgTreeNode | null {
  for (const node of nodes) {
    if (node.id === id) return node
    const found = findTreeNode(node.children || [], id)
    if (found) return found
  }
  return null
}

function selectNode(node: OrgTreeNode) {
  selectedNode.value = node
  query.parentId = node.id
  query.page = 1
  void loadPage()
}

function showAll() {
  selectedNode.value = null
  query.parentId = undefined
  query.page = 1
  void loadPage()
}

function resetQuery() {
  query.orgName = undefined
  query.orgType = undefined
  query.status = undefined
  query.page = 1
  void loadPage()
}

function openCreate(parent?: OrgTreeNode | null) {
  Object.assign(form, emptyForm(), {
    parentId: parent?.id,
    orgType: parent ? 'DEPARTMENT' : 'COMPANY',
  })
  mode.value = 'create'
  dialogVisible.value = true
}

async function openEdit(target: OrgTreeNode | OrgRecord) {
  const detail = await getOrgDetail(target.id)
  Object.assign(form, {
    id: detail.id,
    version: detail.version,
    orgCode: detail.orgCode,
    orgName: detail.orgName,
    orgType: detail.orgType,
    parentId: detail.parentId,
    leaderId: detail.leaderId,
    status: detail.status,
    sortNo: detail.sortNo,
    remark: detail.remark || '',
  })
  mode.value = 'edit'
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  if (!canChooseNoParent.value && !form.parentId) {
    ElMessage.warning('部门或项目组织必须选择上级组织')
    return
  }
  saving.value = true
  try {
    if (mode.value === 'create') {
      await createOrg(form)
      ElMessage.success('组织新增成功')
    } else {
      await updateOrg(form)
      ElMessage.success('组织修改成功')
    }
    dialogVisible.value = false
    await Promise.all([loadTree(), loadPage()])
  } finally {
    saving.value = false
  }
}

async function handleDelete(target: OrgTreeNode | OrgRecord) {
  await ElMessageBox.confirm(`确认删除组织“${target.orgName}”吗？`, '删除确认', { type: 'warning' })
  await deleteOrg(target.id)
  if (selectedNode.value?.id === target.id) showAll()
  ElMessage.success('组织已删除')
  await Promise.all([loadTree(), loadPage()])
}

onMounted(async () => {
  await Promise.all([loadTree(), loadPage(), getLeaderOptions().then((data) => { leaders.value = data })])
})
</script>

<template>
  <section class="org-page">
    <header class="page-heading">
      <div><p>系统管理中心</p><h1>组织管理</h1><span>维护公司、部门及项目组织的行政隶属关系</span></div>
      <el-button v-permission="'org:add'" type="primary" @click="openCreate(selectedNode)">新增组织</el-button>
    </header>

    <div class="org-workspace">
      <el-card shadow="never" class="tree-card" v-loading="treeLoading">
        <template #header><div class="card-title"><strong>行政组织树</strong><el-button link type="primary" @click="showAll">显示全部</el-button></div></template>
        <el-tree
          :data="tree"
          node-key="id"
          default-expand-all
          highlight-current
          :expand-on-click-node="false"
          :props="{ label: 'orgName', children: 'children' }"
          @node-click="selectNode"
        >
          <template #default="{ data }">
            <div class="tree-node"><span class="tree-node__icon">{{ data.orgType === 'COMPANY' ? '企' : data.orgType === 'DEPARTMENT' ? '部' : '组' }}</span><span>{{ data.orgName }}</span><i :class="{ off: data.status === 0 }"></i></div>
          </template>
        </el-tree>
        <div v-if="!tree.length && !treeLoading" class="empty-tree">暂无行政组织</div>
      </el-card>

      <main class="org-content">
        <el-card v-if="selectedNode" shadow="never" class="selected-card">
          <div class="selected-info">
            <div><small>当前节点</small><strong>{{ selectedNode.orgName }}</strong><span>{{ selectedNode.orgCode }} · {{ typeLabel(selectedNode.orgType) }} · {{ selectedNode.leaderName || '未设置负责人' }}</span></div>
            <div class="selected-actions"><el-button v-permission="'org:add'" @click="openCreate(selectedNode)">新增下级</el-button><el-button v-permission="'org:edit'" type="primary" plain @click="openEdit(selectedNode)">编辑</el-button><el-button v-permission="'org:delete'" type="danger" plain @click="handleDelete(selectedNode)">删除</el-button></div>
          </div>
        </el-card>

        <el-card shadow="never" class="query-card">
          <el-form :inline="true" :model="query">
            <el-form-item label="组织名称"><el-input v-model="query.orgName" clearable placeholder="请输入名称" /></el-form-item>
            <el-form-item label="组织类型"><el-select v-model="query.orgType" clearable placeholder="全部类型"><el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
            <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部状态"><el-option label="正常" :value="1" /><el-option label="停用" :value="0" /></el-select></el-form-item>
            <el-form-item><el-button type="primary" @click="query.page = 1; loadPage()">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <div class="list-caption">{{ selectedNode ? `“${selectedNode.orgName}”的直属下级` : '全部行政组织' }}</div>
          <el-table v-loading="tableLoading" :data="organizations" row-key="id">
            <el-table-column prop="orgCode" label="组织编码" min-width="120" />
            <el-table-column prop="orgName" label="组织名称" min-width="150" />
            <el-table-column label="类型" width="130"><template #default="scope">{{ typeLabel(scope.row.orgType) }}</template></el-table-column>
            <el-table-column prop="parentName" label="上级组织" min-width="140"><template #default="scope">{{ scope.row.parentName || '根组织' }}</template></el-table-column>
            <el-table-column prop="leaderName" label="负责人" min-width="110"><template #default="scope">{{ scope.row.leaderName || '-' }}</template></el-table-column>
            <el-table-column prop="treeLevel" label="层级" width="75" />
            <el-table-column label="状态" width="85"><template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '正常' : '停用' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="170" fixed="right"><template #default="scope"><el-button v-permission="'org:edit'" link type="primary" @click="openEdit(scope.row)">编辑</el-button><el-button v-permission="'org:add'" link type="primary" @click="openCreate(findTreeNode(tree, scope.row.id))">新增下级</el-button><el-button v-permission="'org:delete'" link type="danger" @click="handleDelete(scope.row)">删除</el-button></template></el-table-column>
          </el-table>
          <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @change="loadPage" />
        </el-card>
      </main>
    </div>

    <el-dialog v-model="dialogVisible" :title="mode === 'create' ? '新增组织' : '编辑组织'" width="650px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="18"><el-col :span="12"><el-form-item label="组织编码" prop="orgCode"><el-input v-model="form.orgCode" maxlength="50" /></el-form-item></el-col><el-col :span="12"><el-form-item label="组织名称" prop="orgName"><el-input v-model="form.orgName" maxlength="100" /></el-form-item></el-col></el-row>
        <el-row :gutter="18"><el-col :span="12"><el-form-item label="组织类型" prop="orgType"><el-select v-model="form.orgType"><el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="上级组织"><el-tree-select v-model="form.parentId" :data="tree" node-key="id" :props="{ label: 'orgName', children: 'children' }" check-strictly clearable filterable placeholder="公司可不选择" /></el-form-item></el-col></el-row>
        <el-row :gutter="18"><el-col :span="12"><el-form-item label="负责人"><el-select v-model="form.leaderId" clearable filterable placeholder="请选择有效用户"><el-option v-for="item in leaders" :key="item.employeeId" :label="`${item.realName}（${item.username}）`" :value="item.employeeId" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="排序号"><el-input-number v-model="form.sortNo" :min="0" :max="9999" /></el-form-item></el-col></el-row>
        <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio :value="1">正常</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitForm">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.org-page { display: grid; gap: 18px; }
.page-heading { display: flex; align-items: flex-end; justify-content: space-between; padding: 4px 2px; }
.page-heading p { margin: 0 0 5px; color: #b28a3c; font-size: 12px; letter-spacing: .12em; }
.page-heading h1 { margin: 0; color: #173e34; font-size: 26px; }
.page-heading span { display: block; margin-top: 7px; color: #7f8c88; font-size: 13px; }
.org-workspace { display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: 18px; align-items: start; }
.tree-card, .query-card, .table-card, .selected-card { border-color: var(--platform-border); }
.tree-card { position: sticky; top: 82px; min-height: 520px; }
.card-title { display: flex; align-items: center; justify-content: space-between; color: #29483f; }
.tree-node { width: 100%; display: flex; align-items: center; gap: 8px; padding-right: 8px; }
.tree-node__icon { width: 24px; height: 24px; display: grid; place-items: center; color: #21634f; background: #e8f2ee; border-radius: 5px; font-size: 11px; }
.tree-node i { width: 6px; height: 6px; margin-left: auto; background: #4da47d; border-radius: 50%; }
.tree-node i.off { background: #b7c0bd; }
.empty-tree { padding: 60px 0; color: #9aa5a1; text-align: center; }
.org-content { min-width: 0; display: grid; gap: 14px; }
.selected-info { display: flex; align-items: center; justify-content: space-between; }
.selected-info small, .selected-info strong, .selected-info span { display: block; }
.selected-info small { color: #ad873d; font-size: 11px; }
.selected-info strong { margin: 5px 0; color: #23483e; font-size: 19px; }
.selected-info span { color: #87938f; font-size: 12px; }
.query-card :deep(.el-card__body) { padding-bottom: 2px; }
.query-card :deep(.el-select) { width: 165px; }
.table-card :deep(.el-card__body) { padding: 0; }
.list-caption { padding: 16px 18px; color: #405d55; border-bottom: 1px solid #edf0ef; font-weight: 600; }
.table-card :deep(.el-pagination) { justify-content: flex-end; padding: 18px; border-top: 1px solid #edf0ef; }
:deep(.el-tree-node__content) { height: 40px; border-radius: 6px; }
:deep(.el-tree-node__content:hover), :deep(.el-tree--highlight-current .el-tree-node.is-current > .el-tree-node__content) { background: #edf5f2; }
@media (max-width: 980px) { .org-workspace { grid-template-columns: 1fr; } .tree-card { position: static; min-height: auto; } }
</style>
