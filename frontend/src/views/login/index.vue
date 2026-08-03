<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '../../store/user'
import type { LoginRequest } from '../../types/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive<LoginRequest>({ username: '', password: '' })
const rules: FormRules<LoginRequest> = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    await router.replace(redirect)
  } catch (error) {
    if (error instanceof Error && error.message.startsWith('登录响应')) {
      ElMessage.error(error.message)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-intro">
      <div class="login-intro__badge">国资数治</div>
      <p class="login-intro__eyebrow">ENTERPRISE DIGITAL GOVERNANCE</p>
      <h1>国企数字化治理<br />与经营赋能平台</h1>
      <p class="login-intro__description">
        面向县域国有企业，构建治理规范、经营协同、风险可控的一体化数字底座。
      </p>
      <div class="login-intro__features">
        <span>统一身份</span><span>权限隔离</span><span>安全审计</span>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <div class="login-card__mark">企</div>
        <h2>欢迎登录</h2>
        <p>请使用平台账号进入管理中心</p>
        <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent>
          <el-form-item prop="username">
            <el-input v-model.trim="form.username" :prefix-icon="User" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              :prefix-icon="Lock"
              type="password"
              show-password
              placeholder="请输入密码"
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="submitting"
            class="login-submit"
            @click="handleLogin"
          >
            登录平台
          </el-button>
        </el-form>
        <div class="login-card__security">JWT安全认证 · 全链路操作审计</div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(420px, 1.15fr) minmax(480px, 0.85fr);
  background: #f5f7f6;
}

.login-intro {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: clamp(60px, 9vw, 140px);
  color: #fff;
  background:
    radial-gradient(circle at 78% 18%, rgb(215 183 105 / 18%), transparent 24%),
    linear-gradient(145deg, #164f40, #0d3028 70%);
  overflow: hidden;
}

.login-intro::after {
  content: '';
  position: absolute;
  right: -15%;
  bottom: -28%;
  width: 560px;
  height: 560px;
  border: 1px solid rgb(255 255 255 / 8%);
  border-radius: 50%;
  box-shadow: 0 0 0 80px rgb(255 255 255 / 2%), 0 0 0 160px rgb(255 255 255 / 2%);
}

.login-intro__badge {
  width: fit-content;
  margin-bottom: 48px;
  padding: 8px 13px;
  color: #edd18e;
  border: 1px solid rgb(218 186 111 / 55%);
  border-radius: 4px;
  font-size: 13px;
  letter-spacing: 0.2em;
}

.login-intro__eyebrow {
  margin: 0 0 18px;
  color: #89aea4;
  font-size: 11px;
  letter-spacing: 0.22em;
}

.login-intro h1 {
  margin: 0;
  font-family: "STSong", "SimSun", serif;
  font-size: clamp(38px, 4.4vw, 62px);
  font-weight: 500;
  line-height: 1.28;
  letter-spacing: 0.04em;
}

.login-intro__description {
  max-width: 560px;
  margin: 28px 0 36px;
  color: #b8ccc6;
  font-size: 15px;
  line-height: 1.9;
}

.login-intro__features {
  display: flex;
  gap: 14px;
  z-index: 1;
}

.login-intro__features span {
  padding: 7px 12px;
  color: #d8e4e0;
  background: rgb(255 255 255 / 6%);
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: 18px;
  font-size: 12px;
}

.login-panel {
  display: grid;
  place-items: center;
  padding: 48px;
}

.login-card {
  width: min(410px, 100%);
  padding: 48px 44px;
  background: #fff;
  border: 1px solid #e0e7e4;
  border-radius: 14px;
  box-shadow: 0 28px 80px rgb(22 63 52 / 9%);
}

.login-card__mark {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  margin-bottom: 24px;
  color: #e7c879;
  background: #164f40;
  border-radius: 8px;
  font-family: "STSong", serif;
  font-size: 24px;
}

.login-card h2 {
  margin: 0 0 8px;
  color: #1c3931;
  font-size: 27px;
}

.login-card > p {
  margin: 0 0 30px;
  color: #87938f;
  font-size: 13px;
}

.login-card :deep(.el-input__wrapper) {
  min-height: 48px;
  box-shadow: 0 0 0 1px #dfe6e3 inset;
}

.login-submit {
  width: 100%;
  height: 48px;
  margin-top: 6px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.login-card__security {
  margin-top: 26px;
  padding-top: 20px;
  color: #a0aaa6;
  border-top: 1px solid #eef1f0;
  text-align: center;
  font-size: 11px;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-intro {
    min-height: 310px;
    padding: 48px 32px;
  }

  .login-intro__badge {
    margin-bottom: 24px;
  }

  .login-intro__description,
  .login-intro__features {
    display: none;
  }

  .login-panel {
    padding: 30px 20px;
  }
}
</style>
