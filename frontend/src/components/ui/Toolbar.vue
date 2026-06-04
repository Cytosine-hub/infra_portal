<template>
  <div class="toolbar">
    <div class="toolbar-filters">
      <slot name="filters" />
    </div>
    <div v-if="$slots.actions" class="toolbar-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  gap: var(--space-md); margin-bottom: var(--space-lg);
  flex-wrap: wrap;
}
.toolbar-filters {
  display: flex; align-items: center; gap: var(--space-sm);
  flex-wrap: wrap; flex: 1;
}
.toolbar-filters :deep(input),
.toolbar-filters :deep(select) {
  padding: var(--space-xs) var(--space-sm);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  background: var(--color-bg);
  color: var(--color-text);
}
.toolbar-filters :deep(input:focus),
.toolbar-filters :deep(select:focus) {
  border-color: var(--color-border-focus);
  outline: none;
}
.toolbar-actions { display: flex; gap: var(--space-sm); }
</style>
