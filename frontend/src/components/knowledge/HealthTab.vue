<template>
  <div class="health-tab">
    <Toolbar>
      <template #filters>
        <BaseButton variant="primary" :loading="running" @click="runLint">运行体检</BaseButton>
        <span class="hint">检查孤儿页、断链、过期内容与矛盾条目</span>
      </template>
    </Toolbar>

    <div v-if="stats" class="stat-row">
      <div v-for="s in statCards" :key="s.label" class="stat">
        <span class="stat-value">{{ s.value }}</span>
        <span class="stat-label">{{ s.label }}</span>
      </div>
    </div>

    <DataTable
      :columns="columns"
      :data="results"
      :loading="loading"
      empty-text="没有待处理的问题"
    >
      <template #cell-severity="{ row }">
        <StatusBadge :status="row.severity" :label="SEVERITY_LABEL[row.severity] || row.severity" />
      </template>
      <template #actions="{ row }">
        <BaseButton size="sm" variant="ghost" @click="resolve(row)">标记已处理</BaseButton>
      </template>
    </DataTable>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { request } from '../../api.js'
import BaseButton from '../ui/BaseButton.vue'
import DataTable from '../ui/DataTable.vue'
import StatusBadge from '../ui/StatusBadge.vue'
import Toolbar from '../ui/Toolbar.vue'

const props = defineProps({ notify: { type: Function, required: true } })

const SEVERITY_LABEL = { HIGH: '高', MEDIUM: '中', LOW: '低' }
const TYPE_LABEL = {
  ORPHAN: '孤儿页', STALE: '内容过期', BROKEN_LINK: '断链',
  CONTRADICTION: '内容矛盾', GAP: '知识缺口'
}

const columns = [
  { key: 'typeLabel', label: '问题类型' },
  { key: 'severity', label: '严重度', style: 'width: 100px' },
  { key: 'description', label: '说明' }
]

const rawResults = ref([])
const stats = ref(null)
const loading = ref(false)
const running = ref(false)

const results = computed(() => rawResults.value.map(r => ({
  ...r,
  typeLabel: TYPE_LABEL[r.lintType] || r.lintType
})))

const statCards = computed(() => {
  if (!stats.value) return []
  return [
    { label: '经验页面', value: stats.value.total_pages ?? 0 },
    { label: '原始文档', value: stats.value.total_sources ?? 0 },
    { label: '待索引文档', value: stats.value.uningested_sources ?? 0 },
    { label: '待处理问题', value: rawResults.value.length }
  ]
})

async function load() {
  loading.value = true
  try {
    const [lint, stat] = await Promise.all([
      request('/api/knowledge/lint/results').catch(() => []),
      request('/api/knowledge/stats').catch(() => null)
    ])
    rawResults.value = lint || []
    stats.value = stat
  } catch (error) {
    props.notify(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function runLint() {
  running.value = true
  try {
    await request('/api/knowledge/lint/run', { method: 'POST' })
    props.notify('体检完成', 'success')
    await load()
  } catch (error) {
    props.notify(error.message, 'error')
  } finally {
    running.value = false
  }
}

async function resolve(row) {
  try {
    await request(`/api/knowledge/lint/results/${row.id}/resolve`, { method: 'PUT' })
    await load()
  } catch (error) {
    props.notify(error.message, 'error')
  }
}

onMounted(load)
</script>

<style scoped>
.health-tab {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.hint {
  color: var(--color-text-secondary);
  font-size: 0.8125rem;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--space-sm);
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--space-md);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
}

.stat-label {
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
}
</style>
