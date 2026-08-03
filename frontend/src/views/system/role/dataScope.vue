<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ElMessage, type ElTree } from 'element-plus'
import {
  getDataScopeOrganizationTree,
  getRoleDataScope,
  saveRoleDataScope,
} from '../../../api/dataScope'
import type { OrgTreeNode } from '../../../types/org'
import type { DataScopeType, RoleRecord } from '../../../types/role'

const props = defineProps<{ modelValue: boolean; role: RoleRecord | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>()

const scopeOptions: Array<{ value: DataScopeType; label: string }> = [
  { value: 'ALL', label: '全部数据' },
  { value: 'ORG', label: '本组织' },
  { value: 'ORG_AND_CHILDREN', label: '本组织及下级组织' },
  { value: 'SELF', label: '本人数据' },
  { value: 'CUSTOM', label: '自定义组织' },
]
const loading = ref(false)
const saving = ref(false)
const scope = ref<DataScopeType>('SELF')
const organizationTree = ref<OrgTreeNode[]>([])
const selectedOrganizationIds = ref<string[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()

async function loadOrganizationTree() {
  if (organizationTree.value.length > 0) return
  organizationTree.value = await getDataScopeOrganizationTree()
}

watch(() => props.modelValue, async (visible) => {
  if (!visible || !props.role) return
  loading.value = true
  try {
    const config = await getRoleDataScope(props.role.id)
    scope.value = config.dataScope
    selectedOrganizationIds.value = config.orgIds
    if (scope.value === 'CUSTOM') {
      await loadOrganizationTree()
      await nextTick()
      treeRef.value?.setCheckedKeys(selectedOrganizationIds.value)
    }
  } finally {
    loading.value = false
  }
})

watch(scope, async (value) => {
  if (value !== 'CUSTOM' || !props.modelValue) return
  await loadOrganizationTree()
  await nextTick()
  treeRef.value?.setCheckedKeys(selectedOrganizationIds.value)
})

async function save() {
  if (!props.role) return
  const orgIds = scope.value === 'CUSTOM'
    ? (treeRef.value?.getCheckedKeys(false) || []).map(String)
    : []
  if (scope.value === 'CUSTOM' && orgIds.length === 0) {
    ElMessage.warning('自定义数据范围至少需要选择一个组织')
    return
  }
  saving.value = true
  try {
    await saveRoleDataScope({ roleId: props.role.id, dataScope: scope.value, orgIds })
    ElMessage.success('角色数据权限保存成功')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="角色数据权限配置"
    width="680px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="role" class="role-summary">
      <div>
        <small>当前角色</small>
        <strong>{{ role.roleName }}</strong>
        <span>{{ role.roleCode }}</span>
      </div>
      <el-tag effect="plain">{{ scope }}</el-tag>
    </div>

    <div v-loading="loading" class="scope-panel">
      <el-form label-width="110px">
        <el-form-item label="数据范围">
          <el-radio-group v-model="scope">
            <el-radio v-for="item in scopeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="scope === 'CUSTOM'" label="授权组织">
          <div class="organization-tree">
            <div class="tree-tip">仅勾选需要授权的行政组织，父子节点相互独立。</div>
            <el-tree
              ref="treeRef"
              :data="organizationTree"
              node-key="id"
              show-checkbox
              check-strictly
              default-expand-all
              :props="{ label: 'orgName', children: 'children' }"
            >
              <template #default="{ data }">
                <div class="org-node">
                  <span>{{ data.orgName }}</span>
                  <el-tag size="small" effect="plain">{{ data.orgType }}</el-tag>
                </div>
              </template>
            </el-tree>
          </div>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存数据权限</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.role-summary{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px;padding:14px 16px;background:#f1f6f4;border-radius:8px}.role-summary small,.role-summary strong,.role-summary span{display:block}.role-summary small{color:#a37f38;font-size:10px}.role-summary strong{margin:4px 0;color:#23483e}.role-summary span{color:#8a9793;font-size:11px}.scope-panel{min-height:220px}.scope-panel :deep(.el-radio-group){display:grid;grid-template-columns:repeat(2,minmax(180px,1fr));gap:12px;width:100%}.organization-tree{width:100%;max-height:390px;overflow:auto;border:1px solid #e1e9e6;border-radius:8px}.tree-tip{padding:10px 14px;color:#75837e;background:#fafcfb;border-bottom:1px solid #e8eeec;font-size:12px}.organization-tree :deep(.el-tree){padding:12px}.organization-tree :deep(.el-tree-node__content){height:38px}.org-node{display:flex;align-items:center;gap:9px}
</style>
