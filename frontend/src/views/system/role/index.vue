<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createRole, deleteRole, getRoleDetail, getRolePage, updateRole } from '../../../api/role'
import type { DataScopeType, RoleForm, RoleQuery, RoleRecord } from '../../../types/role'
import RoleDataScope from './dataScope.vue'
import RolePermission from './RolePermission.vue'

const scopeOptions: Array<{ value: DataScopeType; label: string }> = [
  { value: 'ALL', label: '全部数据' }, { value: 'ORG', label: '本组织' },
  { value: 'ORG_AND_CHILDREN', label: '本组织及下级组织' }, { value: 'SELF', label: '本人数据' },
  { value: 'CUSTOM', label: '自定义组织' },
]
const scopeLabel = (value: string) => scopeOptions.find((item) => item.value === value)?.label || value
const loading = ref(false), saving = ref(false), total = ref(0)
const roles = ref<RoleRecord[]>([])
const query = reactive<RoleQuery>({ page: 1, size: 20 })
const dialogVisible = ref(false), permissionVisible = ref(false), dataScopeVisible = ref(false)
const mode = ref<'create' | 'edit'>('create')
const selectedRole = ref<RoleRecord | null>(null)
const formRef = ref<FormInstance>()
const emptyForm = (): RoleForm => ({ roleName: '', roleCode: '', description: '', dataScopeType: 'SELF', status: 1 })
const form = reactive<RoleForm>(emptyForm())
const rules: FormRules<RoleForm> = { roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }], roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }], dataScopeType: [{ required: true, message: '请选择数据范围', trigger: 'change' }] }

async function loadRoles() { loading.value = true; try { const page = await getRolePage(query); roles.value = page.records; total.value = page.total } finally { loading.value = false } }
function resetQuery() { Object.assign(query, { roleName: undefined, roleCode: undefined, status: undefined, page: 1, size: 20 }); void loadRoles() }
function openCreate() { Object.assign(form, emptyForm()); mode.value = 'create'; dialogVisible.value = true }
async function openEdit(row: RoleRecord) { Object.assign(form, await getRoleDetail(row.id)); mode.value = 'edit'; dialogVisible.value = true }
async function submit() { await formRef.value?.validate(); saving.value = true; try { if (mode.value === 'create') { await createRole(form); ElMessage.success('角色新增成功') } else { await updateRole(form); ElMessage.success('角色修改成功') } dialogVisible.value = false; await loadRoles() } finally { saving.value = false } }
async function remove(row: RoleRecord) { await ElMessageBox.confirm(`确认删除角色“${row.roleName}”吗？`, '删除确认', { type: 'warning' }); await deleteRole(row.id); ElMessage.success('角色已删除'); await loadRoles() }
function configure(row: RoleRecord) { selectedRole.value = row; permissionVisible.value = true }
function configureDataScope(row: RoleRecord) { selectedRole.value = row; dataScopeVisible.value = true }
onMounted(loadRoles)
</script>

<template>
  <section class="role-page">
    <header class="page-heading"><div><p>系统管理中心</p><h1>角色权限</h1><span>统一配置角色、菜单、按钮及数据访问范围</span></div><el-button v-permission="'role:add'" type="primary" @click="openCreate">新增角色</el-button></header>
    <el-card shadow="never" class="query-card"><el-form :inline="true" :model="query"><el-form-item label="角色名称"><el-input v-model="query.roleName" clearable /></el-form-item><el-form-item label="角色编码"><el-input v-model="query.roleCode" clearable /></el-form-item><el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部"><el-option label="正常" :value="1"/><el-option label="停用" :value="0"/></el-select></el-form-item><el-form-item><el-button type="primary" @click="query.page=1;loadRoles()">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item></el-form></el-card>
    <el-card shadow="never" class="table-card"><el-table v-loading="loading" :data="roles" row-key="id"><el-table-column prop="roleName" label="角色名称" min-width="130"/><el-table-column prop="roleCode" label="角色编码" min-width="160"><template #default="scope"><code>{{ scope.row.roleCode }}</code></template></el-table-column><el-table-column prop="description" label="角色说明" min-width="200" show-overflow-tooltip/><el-table-column label="数据范围" min-width="150"><template #default="scope"><el-tag effect="plain">{{ scopeLabel(scope.row.dataScopeType) }}</el-tag></template></el-table-column><el-table-column prop="userCount" label="用户数" width="85"/><el-table-column label="状态" width="85"><template #default="scope"><el-tag :type="scope.row.status===1?'success':'info'">{{ scope.row.status===1?'正常':'停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="300" fixed="right"><template #default="scope"><template v-if="scope.row.roleCode!=='SUPER_ADMIN'"><el-button v-permission="'role:edit'" link type="primary" @click="openEdit(scope.row)">编辑</el-button><el-button v-permission="'role:edit'" link type="primary" @click="configureDataScope(scope.row)">数据权限</el-button><el-button v-permission="'role:permission'" link type="primary" @click="configure(scope.row)">菜单权限</el-button><el-button v-permission="'role:delete'" link type="danger" @click="remove(scope.row)">删除</el-button></template><el-tag v-else type="warning" effect="plain">系统保护</el-tag></template></el-table-column></el-table><el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @change="loadRoles"/></el-card>
    <el-dialog v-model="dialogVisible" :title="mode==='create'?'新增角色':'编辑角色'" width="600px" destroy-on-close><el-form ref="formRef" :model="form" :rules="rules" label-width="100px"><el-row :gutter="18"><el-col :span="12"><el-form-item label="角色名称" prop="roleName"><el-input v-model="form.roleName" maxlength="50"/></el-form-item></el-col><el-col :span="12"><el-form-item label="角色编码" prop="roleCode"><el-input v-model="form.roleCode" maxlength="50" placeholder="例如 FINANCE_MANAGER"/></el-form-item></el-col></el-row><el-form-item label="数据范围" prop="dataScopeType"><el-select v-model="form.dataScopeType" disabled><el-option v-for="item in scopeOptions" :key="item.value" :label="item.label" :value="item.value"/></el-select><span class="scope-hint">请在角色列表的“数据权限”中单独配置</span></el-form-item><el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio :value="1">正常</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item><el-form-item label="角色说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="200" show-word-limit/></el-form-item></el-form><template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template></el-dialog>
    <RolePermission v-model="permissionVisible" :role="selectedRole" @saved="loadRoles"/>
    <RoleDataScope v-model="dataScopeVisible" :role="selectedRole" @saved="loadRoles"/>
  </section>
</template>

<style scoped>
.role-page{display:grid;gap:18px}.page-heading{display:flex;align-items:flex-end;justify-content:space-between;padding:4px 2px}.page-heading p{margin:0 0 5px;color:#b28a3c;font-size:12px;letter-spacing:.12em}.page-heading h1{margin:0;color:#173e34;font-size:26px}.page-heading span{display:block;margin-top:7px;color:#7f8c88;font-size:13px}.query-card,.table-card{border-color:var(--platform-border)}.query-card :deep(.el-card__body){padding-bottom:2px}.query-card :deep(.el-select){width:150px}.table-card :deep(.el-card__body){padding:0}.table-card :deep(.el-pagination){justify-content:flex-end;padding:18px;border-top:1px solid #edf0ef}.table-card code{color:#2b5c4f;font-size:11px}.scope-hint{margin-left:12px;color:#8a9793;font-size:12px}
</style>
