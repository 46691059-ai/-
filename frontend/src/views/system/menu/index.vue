<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createMenu, deleteMenu, getMenuPage, getMenuTree, updateMenu } from '../../../api/menu'
import type { MenuForm, MenuQuery, MenuRecord, MenuType } from '../../../types/menu'

const typeOptions: Array<{ label: string; value: MenuType }> = [
  { label: '目录', value: 'M' }, { label: '菜单', value: 'C' }, { label: '按钮', value: 'B' },
]
const typeLabel = (value: MenuType) => typeOptions.find((item) => item.value === value)?.label || value
const tree = ref<MenuRecord[]>([])
const records = ref<MenuRecord[]>([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const query = reactive<MenuQuery>({ page: 1, size: 20 })
const dialogVisible = ref(false)
const mode = ref<'create' | 'edit'>('create')
const formRef = ref<FormInstance>()
const emptyForm = (): MenuForm => ({ menuName: '', menuType: 'C', sort: 0, status: 1 })
const form = reactive<MenuForm>(emptyForm())
const rules: FormRules<MenuForm> = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
}
const parentTree = computed(() => removeButtons(tree.value, form.id))

function removeButtons(nodes: MenuRecord[], excludedId?: string): MenuRecord[] {
  return nodes.filter((node) => node.menuType !== 'B' && node.id !== excludedId).map((node) => ({
    ...node, children: removeButtons(node.children || [], excludedId),
  }))
}

async function loadData() {
  loading.value = true
  try {
    const [treeData, page] = await Promise.all([getMenuTree(), getMenuPage(query)])
    tree.value = treeData
    records.value = page.records
    total.value = page.total
  } finally { loading.value = false }
}

function openCreate(parent?: MenuRecord) {
  Object.assign(form, emptyForm(), { parentId: parent?.id, menuType: parent ? 'C' : 'M' })
  mode.value = 'create'; dialogVisible.value = true
}

function openEdit(row: MenuRecord) {
  Object.assign(form, emptyForm(), { ...row, children: undefined })
  mode.value = 'edit'; dialogVisible.value = true
}

async function submit() {
  await formRef.value?.validate()
  if (form.menuType === 'C' && !form.path) return ElMessage.warning('菜单类型必须填写路由地址')
  if (form.menuType === 'B' && !form.permission) return ElMessage.warning('按钮类型必须填写权限标识')
  saving.value = true
  try {
    if (mode.value === 'create') await createMenu(form); else await updateMenu(form)
    ElMessage.success(mode.value === 'create' ? '菜单新增成功' : '菜单修改成功')
    dialogVisible.value = false
    await loadData()
  } finally { saving.value = false }
}

async function remove(row: MenuRecord) {
  await ElMessageBox.confirm(`确认删除菜单“${row.menuName}”吗？`, '删除确认', { type: 'warning' })
  await deleteMenu(row.id)
  ElMessage.success('菜单删除成功')
  await loadData()
}

function resetQuery() {
  Object.assign(query, { menuName: undefined, menuType: undefined, permission: undefined, status: undefined, page: 1 })
  void loadData()
}

onMounted(loadData)
</script>

<template>
  <section class="menu-page">
    <header class="page-heading">
      <div><p>系统管理中心</p><h1>菜单管理</h1><span>维护目录、页面、按钮三级资源及前端路由元数据</span></div>
      <el-button v-permission="'menu:add'" type="primary" @click="openCreate()">新增菜单</el-button>
    </header>
    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="菜单名称"><el-input v-model="query.menuName" clearable /></el-form-item>
        <el-form-item label="菜单类型"><el-select v-model="query.menuType" clearable><el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="权限标识"><el-input v-model="query.permission" clearable /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable><el-option label="正常" :value="1"/><el-option label="停用" :value="0"/></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="query.page=1; loadData()">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="records" row-key="id">
        <el-table-column prop="menuName" label="菜单名称" min-width="150"/>
        <el-table-column label="类型" width="90"><template #default="scope"><el-tag effect="plain">{{ typeLabel(scope.row.menuType) }}</el-tag></template></el-table-column>
        <el-table-column prop="path" label="路由" min-width="140"><template #default="scope">{{ scope.row.path || '-' }}</template></el-table-column>
        <el-table-column prop="component" label="组件" min-width="170"><template #default="scope">{{ scope.row.component || '-' }}</template></el-table-column>
        <el-table-column prop="permission" label="权限标识" min-width="170"><template #default="scope"><code>{{ scope.row.permission || '-' }}</code></template></el-table-column>
        <el-table-column prop="sort" label="排序" width="75"/>
        <el-table-column label="状态" width="80"><template #default="scope"><el-tag :type="scope.row.status===1?'success':'info'">{{ scope.row.status===1?'正常':'停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="210" fixed="right"><template #default="scope"><el-button v-permission="'menu:add'" link type="primary" @click="openCreate(scope.row)">新增下级</el-button><el-button v-permission="'menu:edit'" link type="primary" @click="openEdit(scope.row)">编辑</el-button><el-button v-permission="'menu:delete'" link type="danger" @click="remove(scope.row)">删除</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @change="loadData"/>
    </el-card>
    <el-dialog v-model="dialogVisible" :title="mode==='create'?'新增菜单':'编辑菜单'" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="95px">
        <el-row :gutter="18"><el-col :span="12"><el-form-item label="菜单名称" prop="menuName"><el-input v-model="form.menuName" maxlength="100"/></el-form-item></el-col><el-col :span="12"><el-form-item label="菜单类型" prop="menuType"><el-radio-group v-model="form.menuType"><el-radio-button v-for="item in typeOptions" :key="item.value" :value="item.value">{{ item.label }}</el-radio-button></el-radio-group></el-form-item></el-col></el-row>
        <el-form-item label="上级菜单"><el-tree-select v-model="form.parentId" :data="parentTree" node-key="id" :props="{label:'menuName',children:'children'}" check-strictly clearable default-expand-all /></el-form-item>
        <el-row :gutter="18"><el-col :span="12"><el-form-item label="路由地址"><el-input v-model="form.path" :disabled="form.menuType==='B'" placeholder="/system/menu"/></el-form-item></el-col><el-col :span="12"><el-form-item label="组件路径"><el-input v-model="form.component" :disabled="form.menuType==='B'" placeholder="system/menu/index"/></el-form-item></el-col></el-row>
        <el-row :gutter="18"><el-col :span="12"><el-form-item label="权限标识"><el-input v-model="form.permission" placeholder="system:menu:view"/></el-form-item></el-col><el-col :span="12"><el-form-item label="图标"><el-input v-model="form.icon"/></el-form-item></el-col></el-row>
        <el-row :gutter="18"><el-col :span="12"><el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" :max="9999"/></el-form-item></el-col><el-col :span="12"><el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio :value="1">正常</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item></el-col></el-row>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.menu-page{display:grid;gap:18px}.page-heading{display:flex;align-items:flex-end;justify-content:space-between;padding:4px 2px}.page-heading p{margin:0 0 5px;color:#b28a3c;font-size:12px;letter-spacing:.12em}.page-heading h1{margin:0;color:#173e34;font-size:26px}.page-heading span{display:block;margin-top:7px;color:#7f8c88;font-size:13px}.query-card,.table-card{border-color:var(--platform-border)}.el-pagination{justify-content:flex-end;margin-top:18px}code{color:#225b4b;background:#edf5f2;padding:2px 6px;border-radius:4px}
</style>
