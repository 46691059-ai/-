<template>
  <section class="system-entry">
    <div class="system-entry__mark">系</div>
    <p>SYSTEM FOUNDATION</p>
    <h1>系统管理</h1>
    <span>系统基础模块入口已就绪，具体管理功能将在后续Sprint建设。</span>
  </section>
</template>

<style scoped>
.system-entry {
  min-height: 420px;
  display: grid;
  place-items: center;
  align-content: center;
  padding: 48px;
  text-align: center;
  background: #fff;
  border: 1px solid var(--platform-border);
  border-radius: 12px;
}
.system-entry__mark { width: 58px; height: 58px; display: grid; place-items: center; color: #e6c77d; background: #164d3f; border-radius: 9px; font-family: "STSong", serif; font-size: 27px; }
.system-entry p { margin: 22px 0 8px; color: #aa8337; font-size: 10px; letter-spacing: .18em; }
.system-entry h1 { margin: 0 0 12px; color: #203b34; font-size: 30px; }
.system-entry span { color: #84908c; }
</style>
