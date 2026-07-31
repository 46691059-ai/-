<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import type { ProjectCreateInput, ProjectType, RiskLevel } from '../../types/project'

export interface ProjectFormModel extends ProjectCreateInput {
  actualStartDate?: string
  actualEndDate?: string
  actualIncome: number
  version: number
}

const props = defineProps<{
  mode: 'create' | 'edit'
  initial?: Partial<ProjectFormModel>
  submitting?: boolean
}>()

const emit = defineEmits<{
  submit: [value: ProjectFormModel]
  cancel: []
}>()

const formRef = ref<FormInstance>()
const form = reactive<ProjectFormModel>(defaults())
const rules: FormRules<ProjectFormModel> = {
  projectCode: [
    { required: true, message: '请输入项目编码', trigger: 'blur' },
    { max: 64, message: '项目编码不能超过64个字符', trigger: 'blur' },
  ],
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  projectType: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  orgId: [{ required: true, message: '请输入所属组织ID', trigger: 'blur' }],
  managerUserId: [{ required: true, message: '请输入负责人用户ID', trigger: 'blur' }],
  riskLevel: [{ required: true, message: '请选择风险等级', trigger: 'change' }],
  investmentAmount: [{ required: true, message: '请输入投资金额', trigger: 'blur' }],
  expectedIncome: [{ required: true, message: '请输入预计收益', trigger: 'blur' }],
}

watch(
  () => props.initial,
  (value) => Object.assign(form, defaults(), value ?? {}),
  { immediate: true, deep: true },
)

function defaults(): ProjectFormModel {
  return {
    projectCode: '',
    projectName: '',
    projectType: 'DIGITAL' as ProjectType,
    orgId: '',
    managerUserId: '',
    description: '',
    plannedStartDate: undefined,
    plannedEndDate: undefined,
    investmentAmount: 0,
    expectedIncome: 0,
    actualStartDate: undefined,
    actualEndDate: undefined,
    actualIncome: 0,
    riskLevel: 'LOW' as RiskLevel,
    version: 0,
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (
    form.plannedStartDate &&
    form.plannedEndDate &&
    form.plannedStartDate > form.plannedEndDate
  ) {
    formRef.value?.scrollToField('plannedStartDate')
    return
  }
  emit('submit', { ...form })
}
</script>

<template>
  <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" class="project-form">
    <section class="form-section">
      <div class="form-section__head">
        <div><span>01</span><h2>基本信息</h2></div>
        <p>用于项目库检索、归属和全生命周期追踪。</p>
      </div>
      <div class="form-grid">
        <ElFormItem label="项目编码" prop="projectCode">
          <ElInput v-model="form.projectCode" :disabled="mode === 'edit'" placeholder="如 PRJ-2026-001" />
        </ElFormItem>
        <ElFormItem label="项目名称" prop="projectName">
          <ElInput v-model="form.projectName" placeholder="请输入项目全称" />
        </ElFormItem>
        <ElFormItem label="项目类型" prop="projectType">
          <ElSelect v-model="form.projectType">
            <ElOption label="投资项目" value="INVESTMENT" />
            <ElOption label="工程项目" value="ENGINEERING" />
            <ElOption label="数字化项目" value="DIGITAL" />
            <ElOption label="运营项目" value="OPERATION" />
            <ElOption label="其他" value="OTHER" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="所属组织ID" prop="orgId">
          <ElInput v-model="form.orgId" placeholder="组织管理模块中的组织ID" />
        </ElFormItem>
        <ElFormItem label="负责人用户ID" prop="managerUserId">
          <ElInput v-model="form.managerUserId" placeholder="用户中心中的用户ID" />
        </ElFormItem>
        <ElFormItem label="风险等级" prop="riskLevel">
          <ElSelect v-model="form.riskLevel">
            <ElOption label="低风险" value="LOW" />
            <ElOption label="中风险" value="MEDIUM" />
            <ElOption label="高风险" value="HIGH" />
            <ElOption label="重大风险" value="CRITICAL" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="项目说明" class="form-grid__wide">
          <ElInput v-model="form.description" type="textarea" :rows="4" maxlength="2000" show-word-limit />
        </ElFormItem>
      </div>
    </section>

    <section class="form-section">
      <div class="form-section__head">
        <div><span>02</span><h2>计划与收益</h2></div>
        <p>项目创建后自动生成储备、立项、实施、运营、验收五个阶段。</p>
      </div>
      <div class="form-grid">
        <ElFormItem label="计划开始日期" prop="plannedStartDate">
          <ElDatePicker v-model="form.plannedStartDate" value-format="YYYY-MM-DD" type="date" />
        </ElFormItem>
        <ElFormItem label="计划结束日期" prop="plannedEndDate">
          <ElDatePicker v-model="form.plannedEndDate" value-format="YYYY-MM-DD" type="date" />
        </ElFormItem>
        <ElFormItem label="投资金额（元）" prop="investmentAmount">
          <ElInputNumber v-model="form.investmentAmount" :min="0" :precision="2" controls-position="right" />
        </ElFormItem>
        <ElFormItem label="预计收益（元）" prop="expectedIncome">
          <ElInputNumber v-model="form.expectedIncome" :min="0" :precision="2" controls-position="right" />
        </ElFormItem>
      </div>
    </section>

    <section v-if="mode === 'edit'" class="form-section">
      <div class="form-section__head">
        <div><span>03</span><h2>执行信息</h2></div>
        <p>项目状态与总进度由阶段状态自动反算，避免人工修改造成数据不一致。</p>
      </div>
      <div class="form-grid">
        <ElFormItem label="实际开始日期">
          <ElDatePicker v-model="form.actualStartDate" value-format="YYYY-MM-DD" type="date" />
        </ElFormItem>
        <ElFormItem label="实际结束日期">
          <ElDatePicker v-model="form.actualEndDate" value-format="YYYY-MM-DD" type="date" />
        </ElFormItem>
        <ElFormItem label="实际收益（元）">
          <ElInputNumber v-model="form.actualIncome" :min="0" :precision="2" />
        </ElFormItem>
      </div>
    </section>

    <div class="form-actions">
      <ElButton @click="emit('cancel')">取消</ElButton>
      <ElButton type="primary" :loading="submitting" @click="submit">
        {{ mode === 'create' ? '创建项目' : '保存修改' }}
      </ElButton>
    </div>
  </ElForm>
</template>
