<template>
  <div :class="['standards-category-sidebar', { collapsed }]">
    <button
      type="button"
      class="category-collapse-toggle"
      :aria-expanded="collapsed ? 'false' : 'true'"
      :aria-label="toggleLabel"
      :title="toggleLabel"
      @click="$emit('update:collapsed', !collapsed)"
    >{{ collapsed ? '›' : '‹' }}</button>
    <JobNavigation
      v-if="!collapsed"
      :model-value="modelValue"
      @update:model-value="(value) => $emit('update:modelValue', value)"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import JobNavigation from '../../shared/jobs/JobNavigation.vue'

const props = defineProps({
  modelValue: { type: String, default: 'all' },
  collapsed: { type: Boolean, default: false }
})
defineEmits(['update:modelValue', 'update:collapsed'])

const toggleLabel = computed(() => (props.collapsed ? '展开软件分类' : '收起软件分类'))
</script>

<style scoped>
.standards-category-sidebar {
  display: grid;
  gap: var(--space-sm);
  align-content: start;
  position: sticky;
  top: var(--space-lg);
  min-width: 0;
}

.category-collapse-toggle {
  justify-self: end;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg);
  color: var(--color-primary);
  font-size: var(--text-lg);
  line-height: 1;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.category-collapse-toggle:hover {
  border-color: var(--color-primary-100);
  background: var(--color-primary-light);
}

.standards-category-sidebar.collapsed .category-collapse-toggle {
  justify-self: center;
}
</style>
