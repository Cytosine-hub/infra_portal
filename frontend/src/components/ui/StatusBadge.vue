<template>
  <span :class="['status-badge', variant]">
    <slot>{{ label }}</slot>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, default: '' },
  label: { type: String, default: '' }
})

const variant = computed(() => {
  const map = {
    'ACTIVE': 'success', 'PUBLISHED': 'success', 'APPROVED': 'success', '已发布': 'success',
    'DRAFT': 'warning', 'MODIFYING': 'warning', '待审核': 'warning', '草稿': 'warning',
    'REJECTED': 'danger', 'ARCHIVED': 'danger', 'CONTRADICTED': 'danger', '已驳回': 'danger',
    'PENDING': 'info', 'PENDING_REVIEW': 'info', '待审核': 'info',
    'STALE': 'muted', '已过期': 'muted'
  }
  return map[props.status] || 'default'
})
</script>

<style scoped>
.status-badge {
  display: inline-block; padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: var(--text-xs); font-weight: 500;
  line-height: 1.4; white-space: nowrap;
}
.status-badge.success { background: var(--color-success-light); color: var(--color-success); }
.status-badge.warning { background: var(--color-warning-light); color: var(--color-warning); }
.status-badge.danger { background: var(--color-danger-light); color: var(--color-danger); }
.status-badge.info { background: var(--color-info-light); color: var(--color-info); }
.status-badge.muted { background: var(--color-bg-tertiary); color: var(--color-text-tertiary); }
.status-badge.default { background: var(--color-bg-tertiary); color: var(--color-text-secondary); }
</style>
