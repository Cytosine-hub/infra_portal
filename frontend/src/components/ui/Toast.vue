<template>
  <Transition name="toast">
    <div v-if="notice" :class="['toast', notice.type || 'info']" role="alert">
      <span class="toast-icon">{{ icon }}</span>
      <span class="toast-message">{{ notice.message }}</span>
    </div>
  </Transition>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  notice: { type: Object, default: null } // { message, type }
})

const icon = computed(() => {
  const map = { success: '✓', error: '✕', warning: '⚠', info: 'ℹ' }
  return map[props.notice?.type] || 'ℹ'
})
</script>

<style scoped>
.toast {
  position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%);
  display: flex; align-items: center; gap: var(--space-sm);
  padding: var(--space-md) var(--space-xl);
  border-radius: var(--radius-lg);
  font-size: var(--text-base); font-weight: 500;
  box-shadow: var(--shadow-lg);
  z-index: var(--z-toast);
  pointer-events: auto;
}
.toast.success { background: var(--color-success); color: var(--color-text-inverse); }
.toast.error { background: var(--color-danger); color: var(--color-text-inverse); }
.toast.warning { background: var(--color-warning); color: var(--color-text-inverse); }
.toast.info { background: var(--color-primary); color: var(--color-text-inverse); }
.toast-icon { font-size: 1.1em; }

.toast-enter-active, .toast-leave-active { transition: all var(--transition-normal); }
.toast-enter-from { opacity: 0; transform: translateX(-50%) translateY(20px); }
.toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(-10px); }
</style>
