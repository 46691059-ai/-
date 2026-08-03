<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { UserFilled } from '@element-plus/icons-vue'
import { getCurrentProfile } from '../../api/profile'
import type { Profile } from '../../types/profile'

const loading = ref(false)
const profile = ref<Profile | null>(null)

onMounted(async () => {
  loading.value = true
  try { profile.value = await getCurrentProfile() } finally { loading.value = false }
})
</script>

<template>
  <section class="profile-page" v-loading="loading">
    <header><p>PERSONAL CENTER</p><h1>个人中心</h1><span>查看当前登录账号的基础信息</span></header>
    <el-card v-if="profile" shadow="never" class="profile-card">
      <div class="profile-identity"><div class="avatar"><el-icon><UserFilled /></el-icon></div><div><strong>{{ profile.realName }}</strong><span>{{ profile.username }}</span></div></div>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户名">{{ profile.username }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ profile.realName }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ profile.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属组织">{{ profile.orgName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色信息" :span="2"><el-tag v-for="(role,index) in profile.roleNames" :key="profile.roleCodes[index]" effect="plain">{{ role }}</el-tag><span v-if="!profile.roleNames.length">未分配角色</span></el-descriptions-item>
      </el-descriptions>
    </el-card>
  </section>
</template>

<style scoped>
.profile-page{display:grid;gap:20px}.profile-page header p{margin:0 0 5px;color:#b28a3c;font-size:11px;letter-spacing:.15em}.profile-page header h1{margin:0;color:#173e34;font-size:27px}.profile-page header span{display:block;margin-top:8px;color:#84918d}.profile-card{max-width:880px;border-color:var(--platform-border)}.profile-identity{display:flex;align-items:center;gap:14px;margin-bottom:24px}.avatar{width:54px;height:54px;display:grid;place-items:center;color:#fff;background:#21634f;border-radius:50%;font-size:25px}.profile-identity strong,.profile-identity span{display:block}.profile-identity strong{color:#23483e;font-size:20px}.profile-identity span{margin-top:4px;color:#8b9793}.profile-card .el-tag{margin-right:7px}
</style>
