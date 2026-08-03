<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  assignUserRoles,
  changeUserStatus,
  createUser,
  deleteUser,
  getOrgOptions,
  getRoleOptions,
  getUserDetail,
  getUserPage,
  resetUserPassword,
  updateUser,
} from '../../../api/user'
import type { OrgOption, RoleOption, UserForm, UserQuery, UserRecord } from '../../../types/user'

const loading = ref(false)
const saving = ref(false)
const users = ref<UserRecord[]>([])
const total = ref(0)
const orgOptions = ref<OrgOption[]>([])
const roleOptions = ref<RoleOption[]>([])
const query = reactive<UserQuery>({ page: 1, size: 20 })

const formDialogVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formRef = ref<FormInstance>()
const emptyForm = (): UserForm => ({ username: '', password: '', realName: '', phone: '', email: '', status: 1 })
const form = reactive<UserForm>(emptyForm())
const formRules: FormRules<UserForm> = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  password: [{ validator: (_rule, value, callback) => {
    if (formMode.value === 'create' && (!value || value.length < 8)) callback(new Error('密码至少8位'))
    else callback()
  }, trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  orgId: [{ required: true, message: '请选择所属组织', trigger: 'change' }],
}

const roleDialogVisible = ref(false)
const roleUser = ref<UserRecord | null>(null)
const selectedRoleIds = ref<string[]>([])

async function loadUsers() {
  loading.value = true
  try {
    const page = await getUserPage(query)
    users.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [orgs, roles] = await Promise.all([getOrgOptions(), getRoleOptions()])
  orgOptions.value = orgs
  roleOptions.value = roles
}

function resetQuery() {
  Object.assign(query, { username: undefined, realName: undefined, orgId: undefined, status: undefined, page: 1, size: 20 })
  void loadUsers()
}

function openCreate() {
  Object.assign(form, emptyForm())
  formMode.value = 'create'
  formDialogVisible.value = true
}

async function openEdit(row: UserRecord) {
  const detail = await getUserDetail(row.id)
  Object.assign(form, {
    id: detail.id,
    version: detail.version,
    username: detail.username,
    password: '',
    realName: detail.realName,
    phone: detail.phone || '',
    email: detail.email || '',
    orgId: detail.orgId,
    status: detail.status,
  })
  formMode.value = 'edit'
  formDialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (formMode.value === 'create') {
      await createUser(form)
      ElMessage.success('用户新增成功')
    } else {
      const { password: _password, ...payload } = form
      await updateUser(payload)
      ElMessage.success('用户修改成功')
    }
    formDialogVisible.value = false
    await loadUsers()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: UserRecord) {
  await ElMessageBox.confirm(`确认删除用户“${row.realName || row.username}”吗？`, '删除确认', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('用户已删除')
  await loadUsers()
}

async function handleStatus(row: UserRecord) {
  const nextStatus = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(`确认${nextStatus === 1 ? '启用' : '停用'}该账号吗？`, '状态确认', { type: 'warning' })
  await changeUserStatus({ id: row.id, status: nextStatus, version: row.version })
  ElMessage.success('账号状态已更新')
  await loadUsers()
}

async function handleResetPassword(row: UserRecord) {
  const result = await ElMessageBox.prompt('请输入新密码（至少8位）', `重置 ${row.username} 的密码`, {
    inputType: 'password',
    inputValidator: (value) => value.length >= 8 || '密码至少8位',
    confirmButtonText: '确认重置',
  })
  await resetUserPassword({ id: row.id, newPassword: result.value, version: row.version })
  ElMessage.success('密码重置成功，用户原登录凭证已失效')
  await loadUsers()
}

function openRoleDialog(row: UserRecord) {
  roleUser.value = row
  selectedRoleIds.value = [...row.roleIds]
  roleDialogVisible.value = true
}

async function submitRoles() {
  if (!roleUser.value) return
  saving.value = true
  try {
    await assignUserRoles({ userId: roleUser.value.id, roleIds: selectedRoleIds.value })
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
    await loadUsers()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadUsers(), loadOptions()])
})
</script>

<template>
  <section class="user-page">
    <header class="page-heading">
      <div><p>系统管理中心</p><h1>用户管理</h1><span>维护登录账号、状态和角色授权</span></div>
      <el-button v-permission="'user:add'" type="primary" @click="openCreate">新增用户</el-button>
    </header>

    <el-card shadow="never" class="query-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="登录账号"><el-input v-model="query.username" clearable placeholder="请输入账号" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="query.realName" clearable placeholder="请输入姓名" /></el-form-item>
        <el-form-item label="组织"><el-select v-model="query.orgId" clearable filterable placeholder="全部组织"><el-option v-for="item in orgOptions" :key="item.id" :label="item.orgName" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部状态"><el-option label="正常" :value="1" /><el-option label="停用" :value="0" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="query.page = 1; loadUsers()">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="users" row-key="id">
        <el-table-column prop="username" label="登录账号" min-width="130" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column prop="orgName" label="所属组织" min-width="140" show-overflow-tooltip />
        <el-table-column label="角色" min-width="180"><template #default="scope"><el-tag v-for="role in scope.row.roleNames" :key="role" class="role-tag" effect="plain">{{ role }}</el-tag><span v-if="!scope.row.roleNames.length" class="muted">未分配</span></template></el-table-column>
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '正常' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="380" fixed="right">
          <template #default="scope">
            <el-button v-permission="'user:edit'" link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            <el-button v-permission="'user:edit'" link type="primary" @click="openRoleDialog(scope.row)">分配角色</el-button>
            <el-button v-permission="'user:edit'" link :type="scope.row.status === 1 ? 'warning' : 'success'" @click="handleStatus(scope.row)">{{ scope.row.status === 1 ? '停用' : '启用' }}</el-button>
            <el-button v-permission="'user:resetPassword'" link type="warning" @click="handleResetPassword(scope.row)">重置密码</el-button>
            <el-button v-permission="'user:delete'" link type="danger" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @change="loadUsers" />
    </el-card>

    <el-dialog v-model="formDialogVisible" :title="formMode === 'create' ? '新增用户' : '编辑用户'" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="92px">
        <el-row :gutter="18"><el-col :span="12"><el-form-item label="登录账号" prop="username"><el-input v-model="form.username" maxlength="50" /></el-form-item></el-col><el-col v-if="formMode === 'create'" :span="12"><el-form-item label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password maxlength="72" /></el-form-item></el-col></el-row>
        <el-row :gutter="18"><el-col :span="12"><el-form-item label="姓名" prop="realName"><el-input v-model="form.realName" maxlength="50" /></el-form-item></el-col><el-col :span="12"><el-form-item label="所属组织" prop="orgId"><el-select v-model="form.orgId" filterable><el-option v-for="item in orgOptions" :key="item.id" :label="item.orgName" :value="item.id" /></el-select></el-form-item></el-col></el-row>
        <el-row :gutter="18"><el-col :span="12"><el-form-item label="手机号" prop="phone"><el-input v-model="form.phone" maxlength="20" /></el-form-item></el-col><el-col :span="12"><el-form-item label="邮箱" prop="email"><el-input v-model="form.email" maxlength="100" /></el-form-item></el-col></el-row>
        <el-form-item label="账号状态"><el-radio-group v-model="form.status"><el-radio :value="1">正常</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="formDialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitForm">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" title="分配角色" width="500px">
      <p class="dialog-tip">用户：{{ roleUser?.realName }}（{{ roleUser?.username }}）</p>
      <el-checkbox-group v-model="selectedRoleIds" class="role-options"><el-checkbox v-for="role in roleOptions" :key="role.id" :value="role.id"><strong>{{ role.roleName }}</strong><small>{{ role.roleCode }}</small></el-checkbox></el-checkbox-group>
      <template #footer><el-button @click="roleDialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitRoles">保存授权</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.user-page { display: grid; gap: 18px; }
.page-heading { display: flex; align-items: flex-end; justify-content: space-between; padding: 4px 2px; }
.page-heading p { margin: 0 0 5px; color: #b28a3c; font-size: 12px; letter-spacing: .12em; }
.page-heading h1 { margin: 0; color: #173e34; font-size: 26px; }
.page-heading span { display: block; margin-top: 7px; color: #7f8c88; font-size: 13px; }
.query-card, .table-card { border-color: var(--platform-border); }
.query-card :deep(.el-card__body) { padding-bottom: 2px; }
.query-card :deep(.el-select) { width: 180px; }
.table-card :deep(.el-card__body) { padding: 0; }
.table-card :deep(.el-pagination) { justify-content: flex-end; padding: 18px; border-top: 1px solid #edf0ef; }
.role-tag { margin: 2px 5px 2px 0; }
.muted { color: #a5afac; }
.dialog-tip { margin: 0 0 18px; padding: 12px; color: #48625b; background: #f1f6f4; border-radius: 6px; }
.role-options { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.role-options :deep(.el-checkbox) { height: auto; margin: 0; padding: 12px; border: 1px solid #dfe7e4; border-radius: 6px; }
.role-options small { display: block; margin-top: 3px; color: #9aa5a1; font-size: 10px; }
@media (max-width: 768px) { .page-heading { align-items: flex-start; } .role-options { grid-template-columns: 1fr; } }
</style>
