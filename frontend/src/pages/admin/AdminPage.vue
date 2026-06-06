<template>
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <div class="sidebar-title">
        <p class="eyebrow">Admin</p>
        <h2>管理台</h2>
      </div>
      <nav class="side-nav" aria-label="Admin">
        <button v-for="item in visibleSections" :key="item.key"
          :class="{ active: section === item.key }"
          @click="$emit('switchSection', item.key)"
        >{{ item.label }}</button>
      </nav>
      <div class="sidebar-actions">
        <button class="ghost" @click="$emit('showPassword')">修改密码</button>
        <button class="danger" @click="$emit('logout')">退出</button>
      </div>
    </aside>

    <section class="admin-content">
      <div class="admin-body">
        <slot />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  section: { type: String, default: 'files' },
  isSysAdmin: { type: Boolean, default: false }
})
defineEmits(['switchSection', 'showPassword', 'logout'])

const allSections = [
  { key: 'files', label: '文件管理', sysAdmin: false },
  { key: 'types', label: '类型管理', sysAdmin: true },
  { key: 'standardPublish', label: '参数标准', sysAdmin: false },
  { key: 'documentMaintenance', label: '标准文档', sysAdmin: false },
  { key: 'reviews', label: '审核管理', sysAdmin: false },
  { key: 'users', label: '用户管理', sysAdmin: true },
  { key: 'settings', label: '系统设置', sysAdmin: true }
]

const visibleSections = computed(() => allSections.filter(s => !s.sysAdmin || props.isSysAdmin))
</script>

<style scoped>
.admin-layout { display: flex; height: 100%; overflow: hidden; }
.admin-sidebar {
  width: 220px; background: var(--color-bg-secondary); border-right: 1px solid var(--color-border);
  padding: var(--space-xl); display: flex; flex-direction: column; flex-shrink: 0;
  overflow-y: auto;
}
.sidebar-title { margin-bottom: var(--space-xl); }
.sidebar-title .eyebrow { color: var(--color-text-tertiary); font-size: var(--text-xs); text-transform: uppercase; margin: 0; }
.sidebar-title h2 { margin: var(--space-xs) 0 0; font-size: var(--text-xl); }
.side-nav { display: flex; flex-direction: column; gap: var(--space-xs); flex: 1; }
.side-nav button {
  background: none; border: none; text-align: left; padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-md); color: var(--color-text-secondary); cursor: pointer;
  font-size: var(--text-base); transition: all var(--transition-fast);
}
.side-nav button:hover { background: var(--color-bg-tertiary); color: var(--color-text); }
.side-nav button.active { background: var(--color-primary-light); color: var(--color-primary); font-weight: 600; }
.sidebar-actions { display: flex; flex-direction: column; gap: var(--space-sm); margin-top: var(--space-lg); }
.admin-content { flex: 1; padding: var(--space-xl); overflow-y: auto; display: flex; flex-direction: column; min-height: 0; }
.admin-body { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.admin-body :deep(.section-toolbar) {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: var(--space-lg); flex-shrink: 0; gap: var(--space-md);
  flex-wrap: nowrap; white-space: nowrap;
}
.admin-body :deep(.section-toolbar .filters) {
  display: flex; align-items: center; gap: var(--space-sm); flex: 1; min-width: 0;
}
.admin-body :deep(.section-toolbar .filters input),
.admin-body :deep(.section-toolbar .filters select) {
  flex-shrink: 1; min-width: 0; width: auto;
}
.admin-body :deep(.section-toolbar .actions) {
  display: flex; align-items: center; gap: var(--space-sm); flex-shrink: 0;
}
.admin-body > :deep(div) { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.admin-body > :deep(div) > .list-panel { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.admin-body > :deep(div) > .list-panel > .table-wrap { flex: 1; overflow-y: auto; min-height: 0; }
.admin-body > :deep(div) > .list-panel > nav { flex-shrink: 0; justify-content: flex-end; padding-top: 12px; }
</style>
