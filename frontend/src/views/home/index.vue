<script setup lang="ts">
import { computed } from 'vue'
import { Key, OfficeBuilding, UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '../../store/user'

const userStore = useUserStore()
const displayName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || '平台用户',
)
</script>

<template>
  <div class="home-page">
    <section class="welcome-card">
      <div>
        <p class="welcome-card__eyebrow">DIGITAL GOVERNANCE PLATFORM</p>
        <h1>{{ displayName }}，欢迎使用国企数治平台</h1>
        <p>统一组织、统一身份、统一权限，为后续治理与经营模块提供安全可靠的数字底座。</p>
      </div>
      <span class="welcome-card__mark">企</span>
    </section>

    <section class="account-grid">
      <article class="account-card">
        <el-icon><UserFilled /></el-icon>
        <div><span>当前用户</span><strong>{{ userStore.userInfo?.username || '-' }}</strong></div>
      </article>
      <article class="account-card">
        <el-icon><OfficeBuilding /></el-icon>
        <div><span>所属组织</span><strong>{{ userStore.userInfo?.orgName || '-' }}</strong></div>
      </article>
      <article class="account-card">
        <el-icon><Key /></el-icon>
        <div><span>权限数量</span><strong>{{ userStore.permissions.length }}</strong></div>
      </article>
    </section>

    <section class="permission-panel">
      <div class="permission-panel__header">
        <div><span>ACCESS CONTROL</span><h2>当前权限信息</h2></div>
        <el-tag type="success" effect="plain">RBAC已生效</el-tag>
      </div>
      <div v-if="userStore.permissions.length" class="permission-list">
        <el-tag v-for="permission in userStore.permissions" :key="permission" effect="plain">
          {{ permission }}
        </el-tag>
      </div>
      <el-empty v-else description="当前账号暂无业务权限" :image-size="72" />
    </section>
  </div>
</template>

<style scoped>
.home-page { display: grid; gap: 20px; }
.welcome-card {
  min-height: 210px; display: flex; align-items: center; justify-content: space-between;
  padding: 40px 46px; color: #fff; background: radial-gradient(circle at 88% 15%, rgb(215 184 108 / 22%), transparent 25%), #154b3d;
  border-radius: 12px; overflow: hidden;
}
.welcome-card__eyebrow { margin: 0 0 12px; color: #8eb1a7 !important; font-size: 11px; letter-spacing: .18em; }
.welcome-card h1 { margin: 0 0 14px; font-family: "STSong", serif; font-size: clamp(26px, 3vw, 38px); font-weight: 500; }
.welcome-card p { max-width: 720px; margin: 0; color: #bfd1cc; line-height: 1.8; }
.welcome-card__mark { color: rgb(237 207 133 / 18%); font-family: "STSong", serif; font-size: 150px; line-height: 1; }
.account-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.account-card { display: flex; align-items: center; gap: 16px; padding: 22px; background: #fff; border: 1px solid var(--platform-border); border-radius: 10px; }
.account-card > .el-icon { width: 42px; height: 42px; color: var(--platform-primary); background: #eaf3f0; border-radius: 8px; font-size: 20px; }
.account-card span, .account-card strong { display: block; }
.account-card span { color: #87938f; font-size: 12px; }
.account-card strong { margin-top: 5px; color: #213a33; font-size: 17px; }
.permission-panel { padding: 26px; background: #fff; border: 1px solid var(--platform-border); border-radius: 10px; }
.permission-panel__header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 22px; padding-bottom: 18px; border-bottom: 1px solid #edf1ef; }
.permission-panel__header span { color: #a47d30; font-size: 10px; letter-spacing: .16em; }
.permission-panel__header h2 { margin: 5px 0 0; color: #213b34; font-size: 20px; }
.permission-list { display: flex; flex-wrap: wrap; gap: 10px; }
@media (max-width: 760px) { .account-grid { grid-template-columns: 1fr; } .welcome-card { padding: 30px; } .welcome-card__mark { display: none; } }
</style>
