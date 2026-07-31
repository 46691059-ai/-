<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  ElButton, ElDatePicker, ElForm, ElFormItem, ElInput, ElInputNumber,
  ElOption, ElSelect, type FormInstance, type FormRules,
} from 'element-plus'
import type { ProjectCreateInput, ProjectType, RiskLevel } from '../../types/project'

export interface ProjectFormModel extends ProjectCreateInput {
  actualStartDate?: string
  actualEndDate?: string
  version: number
}

const props = defineProps<{
  mode: 'create' | 'edit'
  initial?: Partial<ProjectFormModel>
  submitting?: boolean
}>()
const emit = defineEmits<{ submit: [value: ProjectFormModel]; cancel: [] }>()
const formRef = ref<FormInstance>()
const form = reactive<ProjectFormModel>(defaults())
const rules: FormRules<ProjectFormModel> = {
  projectNo: [
    { required: true, message: '请输入项目编号', trigger: 'blur' },
    { max: 64, message: '项目编号不能超过64个字符', trigger: 'blur' },
  ],
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  projectType: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  departmentId: [{ required: true, message: '请输入责任部门ID', trigger: 'blur' }],
  leaderId: [{ required: true, message: '请输入负责人员工ID', trigger: 'blur' }],
  riskLevel: [{ required: true, message: '请选择风险等级', trigger: 'change' }],
  budgetAmount: [{ required: true, message: '请输入预算金额', trigger: 'blur' }],
  expectedIncome: [{ required: true, message: '请输入预计收入', trigger: 'blur' }],
  expectedProfit: [{ required: true, message: '请输入预计利润', trigger: 'blur' }],
}

watch(
  () => props.initial,
  (value) => Object.assign(form, defaults(), value ?? {}),
  { immediate: true, deep: true },
)

function defaults(): ProjectFormModel {
  return {
    projectNo: '',
    projectName: '',
    projectType: 'DIGITAL' as ProjectType,
    projectMode: '',
    departmentId: '',
    leaderId: '',
    startDate: undefined,
    endDate: undefined,
    actualStartDate: undefined,
    actualEndDate: undefined,
    budgetAmount: 0,
    expectedIncome: 0,
    expectedProfit: 0,
    riskLevel: 'LOW' as RiskLevel,
    remark: '',
    version: 0,
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.startDate && form.endDate && form.startDate > form.endDate) {
    formRef.value?.scrollToField('startDate')
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
        <p>建立统一项目主档，并关联责任部门和员工主数据。</p>
      </div>
      <div class="form-grid">
        <ElFormItem label="项目编号" prop="projectNo">
          <ElInput v-model="form.projectNo" :disabled="mode === 'edit'" placeholder="如 PRJ-2026-001" />
        </ElFormItem>
        <ElFormItem label="项目名称" prop="projectName">
          <ElInput v-model="form.projectName" placeholder="请输入项目全称" />
        </ElFormItem>
        <ElFormItem label="项目类型" prop="projectType">
          <ElSelect v-model="form.projectType">
            <ElOption label="投资项目" value="INVESTMENT" />
            <ElOption label="经营项目" value="OPERATION" />
            <ElOption label="工程项目" value="ENGINEERING" />
            <ElOption label="数字项目" value="DIGITAL" />
            <ElOption label="研发项目" value="RD" />
            <ElOption label="其他" value="OTHER" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="项目模式">
          <ElInput v-model="form.projectMode" placeholder="如 自营、合作、委托" />
        </ElFormItem>
        <ElFormItem label="责任部门ID" prop="departmentId">
          <ElInput v-model="form.departmentId" placeholder="组织管理中的部门ID" />
        </ElFormItem>
        <ElFormItem label="负责人员工ID" prop="leaderId">
          <ElInput v-model="form.leaderId" placeholder="员工主数据ID" />
        </ElFormItem>
        <ElFormItem label="风险等级" prop="riskLevel">
          <ElSelect v-model="form.riskLevel">
            <ElOption label="低风险" value="LOW" />
            <ElOption label="中风险" value="MEDIUM" />
            <ElOption label="高风险" value="HIGH" />
            <ElOption label="重大风险" value="CRITICAL" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="备注" class="form-grid__wide">
          <ElInput v-model="form.remark" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </ElFormItem>
      </div>
    </section>

    <section class="form-section">
      <div class="form-section__head">
        <div><span>02</span><h2>计划与经营目标</h2></div>
        <p>项目创建后自动生成储备、立项、实施、运营、评价、归档六个阶段。</p>
      </div>
      <div class="form-grid">
        <ElFormItem label="计划开始日期" prop="startDate">
          <ElDatePicker v-model="form.startDate" value-format="YYYY-MM-DD" type="date" />
        </ElFormItem>
        <ElFormItem label="计划结束日期" prop="endDate">
          <ElDatePicker v-model="form.endDate" value-format="YYYY-MM-DD" type="date" />
        </ElFormItem>
        <ElFormItem label="预算金额（元）" prop="budgetAmount">
          <ElInputNumber v-model="form.budgetAmount" :min="0" :precision="2" />
        </ElFormItem>
        <ElFormItem label="预计收入（元）" prop="expectedIncome">
          <ElInputNumber v-model="form.expectedIncome" :min="0" :precision="2" />
        </ElFormItem>
        <ElFormItem label="预计利润（元）" prop="expectedProfit">
          <ElInputNumber v-model="form.expectedProfit" :precision="2" />
        </ElFormItem>
      </div>
    </section>

    <section v-if="mode === 'edit'" class="form-section">
      <div class="form-section__head">
        <div><span>03</span><h2>实际执行</h2></div>
        <p>项目状态与总体进度由阶段状态自动反算。</p>
      </div>
      <div class="form-grid">
        <ElFormItem label="实际开始日期">
          <ElDatePicker v-model="form.actualStartDate" value-format="YYYY-MM-DD" type="date" />
        </ElFormItem>
        <ElFormItem label="实际结束日期">
          <ElDatePicker v-model="form.actualEndDate" value-format="YYYY-MM-DD" type="date" />
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
