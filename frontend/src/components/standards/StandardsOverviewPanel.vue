<template>
  <div class="standards-overview">
    <header class="standards-overview-head">
      <div class="standards-overview-title">
        <h1>标准文档总览</h1>
        <p>按类别、软件与版本聚合展示已发布标准，点击标准名称或标准文档即可查看详情。</p>
      </div>
      <div class="standards-summary">
        <div v-for="metric in metrics" :key="metric.label" class="standards-metric">
          <strong>{{ metric.value }}</strong>
          <span>{{ metric.label }}</span>
        </div>
      </div>
    </header>

    <div class="standards-toolbar">
      <input :value="keyword" type="search" class="standards-search"
        aria-label="搜索标准" placeholder="搜索软件、标准名称、标准文档或版本"
        @input="emit('update:keyword', $event.target.value.trim())" />
      <select :value="sortBy" class="standards-sort" aria-label="标准排序方式"
        @change="emit('update:sortBy', $event.target.value)">
        <option value="recent">按最近更新</option>
        <option value="documents">按文档数量</option>
        <option value="name">按标准名称</option>
      </select>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'StandardsOverviewPanel' })

defineProps({
  metrics: { type: Array, default: () => [] },
  keyword: { type: String, default: '' },
  sortBy: { type: String, default: 'recent' }
})
const emit = defineEmits(['update:keyword', 'update:sortBy'])
</script>

<style scoped>
.standards-overview { display: grid; gap: var(--space-lg); }
.standards-overview-head {
  display: flex; align-items: flex-start; justify-content: space-between;
  flex-wrap: wrap; gap: var(--space-xl);
  border: 1px solid var(--color-border); border-radius: var(--radius-lg);
  padding: var(--space-xl); background: var(--color-bg); box-shadow: var(--shadow-sm);
}
.standards-overview-title { min-width: 0; }
.standards-overview-title h1 { margin: 0 0 var(--space-sm); font-size: var(--text-3xl); line-height: 1.25; }
.standards-overview-title p { margin: 0; color: var(--color-text-secondary); font-size: var(--text-base); }
.standards-summary {
  display: grid; grid-template-columns: repeat(4, minmax(96px, 1fr)); gap: var(--space-sm);
  flex: 1 1 420px; max-width: 520px;
}
.standards-metric {
  border: 1px solid var(--color-border); border-radius: var(--radius-md);
  padding: var(--space-md); background: var(--color-bg-secondary); text-align: left;
}
.standards-metric strong { display: block; font-size: var(--text-2xl); line-height: 1.1; color: var(--color-text); }
.standards-metric span { display: block; margin-top: var(--space-xs); font-size: var(--text-xs); color: var(--color-text-secondary); }

.standards-toolbar {
  display: grid; grid-template-columns: minmax(0, 1fr) 180px; gap: var(--space-md);
  border: 1px solid var(--color-border); border-radius: var(--radius-lg);
  padding: var(--space-md) var(--space-lg); background: var(--color-bg); box-shadow: var(--shadow-sm);
}
.standards-search, .standards-sort {
  min-height: 40px; border: 1px solid var(--color-border); border-radius: var(--radius-md);
  padding: 0 var(--space-md); background: var(--color-bg);
  color: var(--color-text); font-size: var(--text-base); outline: none;
}
.standards-search:focus, .standards-sort:focus {
  border-color: var(--color-border-focus); box-shadow: 0 0 0 3px var(--color-primary-ring);
}
.standards-search::placeholder { color: var(--color-text-tertiary); }

@media (max-width: 1120px) {
  .standards-summary { grid-template-columns: repeat(2, minmax(96px, 1fr)); }
}
@media (max-width: 760px) {
  .standards-overview-head { padding: var(--space-lg); }
  .standards-toolbar { grid-template-columns: 1fr; }
}
</style>
