<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ElMessage, type ElTree } from 'element-plus'
import { assignRoleMenus, getMenuTree, getRolePermissions } from '../../../api/role'
import type { MenuTreeNode, RoleRecord } from '../../../types/role'

const props = defineProps<{ modelValue: boolean; role: RoleRecord | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>()
const loading = ref(false)
const saving = ref(false)
const treeRef = ref<InstanceType<typeof ElTree>>()
const menuTree = ref<MenuTreeNode[]>([])
const permissionCodes = ref<string[]>([])

watch(() => props.modelValue, async (visible) => {
  if (!visible || !props.role) return
  loading.value = true
  try {
    const [tree, assignment] = await Promise.all([getMenuTree(), getRolePermissions(props.role.id)])
    menuTree.value = tree
    permissionCodes.value = assignment.permissionCodes
    await nextTick()
    treeRef.value?.setCheckedKeys(assignment.menuIds)
  } finally {
    loading.value = false
  }
})

async function save() {
  if (!props.role) return
  saving.value = true
  try {
    const menuIds = (treeRef.value?.getCheckedKeys(false) || []).map(String)
    await assignRoleMenus({ roleId: props.role.id, menuIds })
    ElMessage.success('角色权限保存成功，相关用户需重新登录')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="角色权限配置" width="680px" destroy-on-close @update:model-value="emit('update:modelValue', $event)">
    <div v-if="role" class="role-summary"><div><small>当前角色</small><strong>{{ role.roleName }}</strong><span>{{ role.roleCode }}</span></div><el-tag effect="plain">{{ role.dataScopeType }}</el-tag></div>
    <div v-loading="loading" class="permission-panel">
      <div class="permission-tip">勾选目录、页面和按钮权限。父级关系由服务端自动补全。</div>
      <el-tree ref="treeRef" :data="menuTree" node-key="id" show-checkbox check-strictly default-expand-all :props="{ label: 'menuName', children: 'children' }">
        <template #default="{ data }"><div class="permission-node"><el-tag size="small" :type="data.menuType === 'B' ? 'warning' : data.menuType === 'C' ? 'success' : 'info'" effect="plain">{{ data.menuType }}</el-tag><span>{{ data.menuName }}</span><code v-if="data.permission">{{ data.permission }}</code></div></template>
      </el-tree>
      <div v-if="permissionCodes.length" class="current-codes"><span>当前Spring Security权限：</span>{{ permissionCodes.join('、') }}</div>
    </div>
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存权限</el-button></template>
  </el-dialog>
</template>

<style scoped>
.role-summary { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; padding: 14px 16px; background: #f1f6f4; border-radius: 8px; }
.role-summary small,.role-summary strong,.role-summary span { display: block; }.role-summary small { color:#a37f38;font-size:10px }.role-summary strong{margin:4px 0;color:#23483e}.role-summary span{color:#8a9793;font-size:11px}
.permission-panel { min-height: 360px; max-height: 540px; overflow: auto; border: 1px solid #e1e9e6; border-radius: 8px; }
.permission-tip { padding: 11px 14px; color: #75837e; background: #fafcfb; border-bottom: 1px solid #e8eeec; font-size: 12px; }
.permission-panel :deep(.el-tree) { padding: 12px; }.permission-panel :deep(.el-tree-node__content){height:38px}
.permission-node { display:flex;align-items:center;gap:8px;width:100% }.permission-node code{margin-left:auto;color:#8b9894;font-size:10px}
.current-codes { margin: 8px 14px 14px; padding: 10px; color: #65736f; background: #f8faf9; border-radius: 6px; font-size: 11px; line-height: 1.8; }.current-codes span{color:#2e594d;font-weight:600}
</style>
