<script setup lang="ts">
import { ArrowLeft, Collection, DataAnalysis, Setting } from '@element-plus/icons-vue'
import { ElIcon } from 'element-plus'
</script>

<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="platform-brand">
        <span class="platform-brand__mark">企</span>
        <div>
          <strong>国企数治平台</strong>
          <small>治理 · 经营 · 赋能</small>
        </div>
      </div>
      <nav class="module-nav">
        <RouterLink to="/projects" class="module-nav__item">
          <ElIcon><Collection /></ElIcon>
          项目全生命周期
        </RouterLink>
        <span class="module-nav__item module-nav__item--disabled">
          <ElIcon><DataAnalysis /></ElIcon>
          经营分析
        </span>
        <span class="module-nav__item module-nav__item--disabled">
          <ElIcon><Setting /></ElIcon>
          系统管理
        </span>
      </nav>
      <div class="app-sidebar__foot">第一阶段 · 经营管理底座</div>
    </aside>
    <main class="app-main">
      <header class="app-topbar">
        <div class="app-topbar__title"><ArrowLeft :size="15" /> 数字化治理与经营赋能平台</div>
        <span class="app-topbar__user">平台管理员</span>
      </header>
      <section class="app-content">
        <RouterView />
      </section>
    </main>
  </div>
</template>
