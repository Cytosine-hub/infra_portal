<template>
  <div class="table-wrap">
    <table class="data-table">
      <thead>
        <tr>
          <th v-for="col in columns" :key="col.key" :style="col.style">{{ col.label }}</th>
          <th v-if="$slots.actions" class="col-actions">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading">
          <td :colspan="columns.length + ($slots.actions ? 1 : 0)" class="table-loading">
            <LoadingSpinner text="加载中..." />
          </td>
        </tr>
        <tr v-else-if="!data || data.length === 0">
          <td :colspan="columns.length + ($slots.actions ? 1 : 0)" class="table-empty">
            <EmptyState :message="emptyText" />
          </td>
        </tr>
        <tr v-for="(row, idx) in data" :key="row.id || idx">
          <td v-for="col in columns" :key="col.key">
            <slot :name="`cell-${col.key}`" :row="row" :value="row[col.key]">
              {{ row[col.key] }}
            </slot>
          </td>
          <td v-if="$slots.actions" class="row-actions">
            <slot name="actions" :row="row" :index="idx" />
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import LoadingSpinner from './LoadingSpinner.vue'
import EmptyState from './EmptyState.vue'

defineProps({
  columns: { type: Array, required: true }, // [{ key, label, style? }]
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无数据' }
})
</script>

<style scoped>
.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: var(--text-base); }
.data-table th {
  text-align: left; padding: var(--space-sm) var(--space-md);
  border-bottom: 2px solid var(--color-border); color: var(--color-text-secondary);
  font-weight: 600; font-size: var(--text-sm); white-space: nowrap;
}
.data-table td {
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--color-border); color: var(--color-text);
}
.data-table tr:hover td { background: var(--color-bg-secondary); }
.col-actions, .row-actions { white-space: nowrap; text-align: right; }
.row-actions { display: flex; gap: var(--space-xs); justify-content: flex-end; }
.table-loading, .table-empty { text-align: center; padding: var(--space-3xl) 0; }
</style>
