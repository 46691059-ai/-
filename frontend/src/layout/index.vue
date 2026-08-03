<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Expand, Fold, SwitchButton, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AppMenu from '../components/AppMenu.vue'
import { useUserStore } from '../store/user'

const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)
const displayName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || '平台用户',
)

async function handleLogout() {
  try {
    await userStore.logout()
    ElMessage.success('已安全退出')
  } catch {
    ElMessage.warning('服务端退出失败，本地登录状态已清除')
  } finally {
    await router.replace('/login')
  }
}
</script>

<template>
  <el-container class="platform-layout">
    <el-aside :width="collapsed ? '72px' : '248px'" class="platform-sidebar">
      <div class="platform-brand" :class="{ 'platform-brand--collapsed': collapsed }">
        <span class="platform-brand__mark">企</span>
        <div v-show="!collapsed" class="platform-brand__text">
          <strong>国企数治平台</strong>
          <small>治理 · 经营 · 赋能</small>
        </div>
      </div>
      <AppMenu :collapsed="collapsed" />
      <div v-show="!collapsed" class="platform-sidebar__footer">企业级数字化管理底座</div>
    </el-aside>

    <el-container class="platform-workspace" :class="{ 'platform-workspace--collapsed': collapsed }">
      <el-header class="platform-header">
        <div class="platform-header__left">
          <el-button text circle aria-label="折叠菜单" @click="collapsed = !collapsed">
            <el-icon :size="20"><Expand v-if="collapsed" /><Fold v-else /></el-icon>
          </el-button>
          <div>
            <strong>国企数字化治理与经营赋能平台</strong>
            <span>Enterprise Governance Platform</span>
          </div>
        </div>
        <el-dropdown trigger="click">
          <button type="button" class="user-entry">
            <el-icon><UserFilled /></el-icon>
            <span>{{ displayName }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :icon="SwitchButton" @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="platform-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.platform-layout {
  min-height: 100vh;
}

.platform-sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  display: flex;
  flex-direction: column;
  color: #fff;
  background: linear-gradient(180deg, #123f34 0%, #0d3028 100%);
  box-shadow: 8px 0 24px rgb(15 54 44 / 12%);
  overflow: hidden;
  transition: width 0.2s ease;
  z-index: 20;
}

.platform-brand {
  height: 76px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 0 0 auto;
  padding: 0 18px;
  border-bottom: 1px solid rgb(255 255 255 / 9%);
}

.platform-brand--collapsed {
  justify-content: center;
  padding: 0;
}

.platform-brand__mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  color: #f1d18a;
  border: 1px solid #b99953;
  border-radius: 6px;
  font-family: "STSong", serif;
  font-size: 21px;
}

.platform-brand__text strong,
.platform-brand__text small {
  display: block;
  white-space: nowrap;
}

.platform-brand__text strong {
  letter-spacing: 0.08em;
}

.platform-brand__text small {
  margin-top: 4px;
  color: #8db0a7;
  font-size: 11px;
}

:deep(.platform-menu) {
  flex: 1;
  padding: 14px 10px;
  background: transparent;
  border-right: 0;
}

:deep(.platform-menu .el-menu-item) {
  height: 46px;
  margin-bottom: 5px;
  color: #bcd0ca;
  border-radius: 7px;
}

:deep(.platform-menu .el-menu-item:hover) {
  color: #fff;
  background: rgb(255 255 255 / 7%);
}

:deep(.platform-menu .el-menu-item.is-active) {
  color: #fff;
  background: #21634f;
  box-shadow: inset 3px 0 #d1b266;
}

.platform-sidebar__footer {
  padding: 18px;
  color: #72978d;
  border-top: 1px solid rgb(255 255 255 / 8%);
  font-size: 11px;
  white-space: nowrap;
}

.platform-workspace {
  min-width: 0;
  margin-left: 248px;
  transition: margin-left 0.2s ease;
}

.platform-workspace--collapsed {
  margin-left: 72px;
}

.platform-header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: rgb(255 255 255 / 96%);
  border-bottom: 1px solid var(--platform-border);
}

.platform-header__left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.platform-header__left strong,
.platform-header__left span {
  display: block;
}

.platform-header__left strong {
  color: #1c3a32;
  font-size: 15px;
}

.platform-header__left span {
  margin-top: 2px;
  color: #93a19c;
  font-size: 10px;
  letter-spacing: 0.08em;
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 13px;
  color: #245a4b;
  background: #eaf2ef;
  border: 0;
  border-radius: 20px;
  cursor: pointer;
}

.platform-main {
  min-height: calc(100vh - 64px);
  padding: 24px;
  background: var(--platform-bg);
}

@media (max-width: 768px) {
  .platform-sidebar {
    width: 72px !important;
  }

  .platform-brand__text,
  .platform-sidebar__footer {
    display: none;
  }

  .platform-brand {
    justify-content: center;
    padding: 0;
  }

  .platform-workspace {
    margin-left: 72px;
  }

  .platform-header__left span {
    display: none;
  }

  .platform-main {
    padding: 16px;
  }
}
</style>
