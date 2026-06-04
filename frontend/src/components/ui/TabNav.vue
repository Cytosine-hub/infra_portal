<template>
  <nav :class="['tab-nav', orientation]" :aria-label="ariaLabel">
    <button
      v-for="tab in tabs"
      :key="tab.key"
      :class="{ active: modelValue === tab.key }"
      @click="$emit('update:modelValue', tab.key)"
    >
      <span v-if="tab.icon" class="tab-icon">{{ tab.icon }}</span>
      {{ tab.label }}
    </button>
  </nav>
</template>

<script setup>
defineProps({
  modelValue: { type: String, required: true },
  tabs: { type: Array, required: true }, // [{ key, label, icon? }]
  orientation: { type: String, default: 'horizontal' }, // horizontal | vertical
  ariaLabel: { type: String, default: '' }
})
defineEmits(['update:modelValue'])
</script>

<style scoped>
.tab-nav {
  display: flex; gap: var(--space-xs);
}
.tab-nav.horizontal { flex-direction: row; border-bottom: 1px solid var(--color-border); }
.tab-nav.vertical { flex-direction: column; }
.tab-nav button {
  background: none; border: none; cursor: pointer;
  padding: var(--space-sm) var(--space-md);
  color: var(--color-text-secondary); font-size: var(--text-base);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
  display: flex; align-items: center; gap: var(--space-xs);
}
.tab-nav button:hover { background: var(--color-bg-tertiary); color: var(--color-text); }
.tab-nav button.active { color: var(--color-primary); font-weight: 600; }
.tab-nav.horizontal button.active { border-bottom: 2px solid var(--color-primary); }
.tab-nav.vertical button.active { background: var(--color-primary-light); }
.tab-icon { font-size: 1.1em; }
</style>
