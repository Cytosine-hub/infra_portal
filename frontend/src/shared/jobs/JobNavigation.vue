<template>
  <aside class="job-navigation" aria-label="类别筛选">
    <p class="job-navigation-label">按类别查看</p>
    <BaseButton class="job-navigation-button" :class="{ active: modelValue === 'all' }" variant="ghost" size="sm" @click="$emit('update:modelValue', 'all')">全部</BaseButton>
    <BaseButton
      v-for="job in jobModules"
      :key="job.id"
      class="job-navigation-button"
      :class="{ active: modelValue === job.id }"
      variant="ghost"
      size="sm"
      @click="$emit('update:modelValue', job.id)"
    >
      {{ job.shortName }}
    </BaseButton>
  </aside>
</template>

<script setup>
import { jobModules } from '../../modules/index.js'
import BaseButton from '../../components/ui/BaseButton.vue'

defineProps({ modelValue: { type: String, default: 'all' } })
defineEmits(['update:modelValue'])
</script>

<style scoped>
.job-navigation {
  display: grid;
  gap: var(--space-2xs);
  position: sticky;
  top: var(--space-lg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-sm);
  background: var(--color-bg);
  box-shadow: var(--shadow-sm);
}
.job-navigation-label { margin: 0 0 var(--space-xs); color: var(--color-text-tertiary); font-size: var(--text-xs); font-weight: 700; }
.job-navigation-button {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 32px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  padding: var(--space-xs) var(--space-sm);
  color: var(--color-text-secondary);
  background: transparent;
  text-align: left;
  font-size: var(--text-sm);
  font-weight: 700;
}
.job-navigation-button:hover, .job-navigation-button.active { border-color: var(--color-primary-100); color: var(--color-primary); background: var(--color-primary-light); }
@media (max-width: 760px) {
  .job-navigation { position: static; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .job-navigation-label { grid-column: 1 / -1; }
}
</style>
