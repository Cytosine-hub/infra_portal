<template>
  <div class="multi-select" ref="containerRef">
    <div
      class="multi-select-trigger"
      @click="open = !open"
      :class="{ open, empty: modelValue.length === 0 }"
      tabindex="0"
      role="combobox"
      :aria-expanded="open"
      @keydown.enter.prevent="open = !open"
      @keydown.space.prevent="open = !open"
      @keydown.esc="open = false"
    >
      <span v-if="modelValue.length === 0" class="multi-select-placeholder">{{ placeholder }}</span>
      <span v-else class="multi-select-tags">
        <span v-for="value in modelValue" :key="value" class="multi-select-tag" @click.stop>
          {{ value }}
          <button type="button" aria-label="移除平台" @click.stop="remove(value)">×</button>
        </span>
      </span>
      <span class="multi-select-arrow">&#9662;</span>
    </div>
    <div v-if="open" class="multi-select-dropdown">
      <label v-for="opt in options" :key="opt" class="multi-select-option">
        <input type="checkbox" :checked="modelValue.includes(opt)" @change="toggle(opt)" />
        <span>{{ opt }}</span>
      </label>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择' }
})

const emit = defineEmits(['update:modelValue'])
const open = ref(false)
const containerRef = ref(null)

function toggle(opt) {
  const list = [...props.modelValue]
  const idx = list.indexOf(opt)
  if (idx >= 0) list.splice(idx, 1)
  else list.push(opt)
  emit('update:modelValue', list)
}

function remove(opt) {
  emit('update:modelValue', props.modelValue.filter(item => item !== opt))
}

function onClickOutside(e) {
  if (containerRef.value && !containerRef.value.contains(e.target)) open.value = false
}

onMounted(() => document.addEventListener('mousedown', onClickOutside))
onBeforeUnmount(() => document.removeEventListener('mousedown', onClickOutside))
</script>

<style scoped>
.multi-select {
  position: relative;
  min-width: 0;
  width: 100%;
}

.multi-select-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 38px;
  max-height: 38px;
  padding: 5px var(--space-md);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
  cursor: pointer;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
  min-width: 0;
  overflow: hidden;
}

.multi-select-trigger.open,
.multi-select-trigger:focus {
  border-color: var(--color-primary);
  outline: none;
  box-shadow: 0 0 0 3px var(--color-primary-ring);
}

.multi-select-placeholder { color: var(--color-text-tertiary); font-size: var(--text-sm); }

.multi-select-tags {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.multi-select-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  max-width: 7rem;
  height: 24px;
  padding: 0 var(--space-sm);
  border-radius: var(--radius-full);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--text-xs);
  font-weight: 600;
  white-space: nowrap;
}

.multi-select-tag button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--text-lg);
  height: var(--text-lg);
  border: 0;
  padding: 0;
  border-radius: var(--radius-full);
  color: var(--color-primary);
  background: transparent;
  cursor: pointer;
  font-size: var(--text-sm);
  line-height: 1;
}

.multi-select-tag button:hover { background: rgba(var(--color-primary-rgb), 0.12); }

.multi-select-arrow {
  color: var(--color-text-tertiary);
  font-size: var(--text-xs);
  margin-left: var(--space-xs);
  flex-shrink: 0;
}

.multi-select-dropdown {
  position: absolute; top: 100%; left: 0; right: 0; z-index: var(--z-dropdown);
  margin-top: var(--space-xs); border: 1px solid var(--color-border); border-radius: var(--radius-md);
  background: var(--color-bg); box-shadow: var(--shadow-md); max-height: var(--dropdown-max-height); overflow-y: auto;
}
.multi-select-option {
  display: flex; align-items: center; gap: var(--space-sm);
  padding: var(--space-xs) var(--space-sm); cursor: pointer;
  transition: background var(--transition-fast); font-size: var(--text-sm);
}
.multi-select-option:hover { background: var(--color-bg-tertiary); }
.multi-select-option input {
  width: var(--space-md); height: var(--space-md); cursor: pointer;
  accent-color: var(--color-primary); margin: 0;
}
</style>
