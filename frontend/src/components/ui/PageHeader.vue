<template>
  <div class="page-header">
    <div class="page-header-text">
      <p v-if="eyebrow" class="page-eyebrow">{{ eyebrow }}</p>
      <h2 class="page-title">{{ title }}</h2>
    </div>
    <div v-if="$slots.actions" class="page-header-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  eyebrow: { type: String, default: '' },
  title: { type: String, required: true }
})
</script>

<style scoped>
.page-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: var(--space-xl);
}
.page-eyebrow {
  color: var(--color-text-tertiary); font-size: var(--text-xs);
  text-transform: uppercase; margin: 0;
}
.page-title { margin: var(--space-xs) 0 0; font-size: var(--text-2xl); color: var(--color-text); }
.page-header-actions { display: flex; gap: var(--space-sm); }
</style>
