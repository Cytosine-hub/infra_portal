<template>
  <div class="health-tab">
    <Toolbar>
      <template #filters>
        <BaseButton variant="primary" :loading="running" @click="runLint">运行体检</BaseButton>
        <span class="hint">检查全部知识来源与页面</span>
      </template>
    </Toolbar>

    <div v-if="corpus" class="stat-row">
      <div v-for="s in statCards" :key="s.label" class="stat">
        <span class="stat-value">{{ s.value }}</span>
        <span class="stat-label">{{ s.label }}</span>
      </div>
    </div>

    <section v-if="corpus" class="corpus">
      <h3>知识库概况</h3>
      <p class="corpus-line">
        来源 {{ corpus.totalSources ?? 0 }} 项 · 页面 {{ corpus.totalPages ?? 0 }} 项 ·
        已启用页面 {{ corpus.activePages ?? 0 }} 项 · 草稿 {{ corpus.draftPages ?? 0 }} 项
      </p>
      <p v-if="!corpus.indexStatusReliable" class="corpus-hint danger">
        向量库查询失败，本次不判定索引状态（避免把「查不到」误读成「没索引」）
      </p>

      <div v-if="sourceTypeEntries.length" class="source-types">
        <span v-for="entry in sourceTypeEntries" :key="entry.type">
          {{ entry.label }} <strong>{{ entry.count }}</strong>
        </span>
      </div>

      <div v-if="corpus.emptySources?.length" class="corpus-block danger">
        <h4>空内容来源（{{ corpus.emptySources.length }}）</h4>
        <ul><li v-for="c in corpus.emptySources" :key="c">{{ c }}</li></ul>
      </div>

      <div v-if="corpus.duplicateContentGroups?.length" class="corpus-block">
        <h4>重复正文（{{ corpus.duplicateContentGroups.length }} 组）</h4>
        <ul><li v-for="c in corpus.duplicateContentGroups" :key="c">{{ c }}</li></ul>
      </div>

      <div v-if="corpus.indexStatusReliable && corpus.unindexedSources?.length" class="corpus-block">
        <h4>未索引来源（{{ corpus.unindexedSources.length }}）</h4>
        <ul><li v-for="c in corpus.unindexedSources" :key="c">{{ c }}</li></ul>
      </div>
    </section>

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
const SOURCE_TYPE_LABEL = {
  UPLOAD: '上传文档',
  STANDARD_DOC: '参数标准',
  STANDARD_DOCUMENT: '标准文档',
  FORUM_POST: '论坛文章',
  EXPERIENCE: '经验来源',
  MANUAL: '手工录入',
  WEB: '网页',
  UNKNOWN: '未分类来源'
}

const columns = [
  { key: 'typeLabel', label: '问题类型' },
  { key: 'severity', label: '严重度', style: 'width: 100px' },
  { key: 'description', label: '说明' }
]

const rawResults = ref([])
const corpus = ref(null)
const loading = ref(false)
const running = ref(false)

const results = computed(() => rawResults.value.map(r => ({
  ...r,
  typeLabel: TYPE_LABEL[r.lintType] || r.lintType
})))

const sourceTypeEntries = computed(() => Object.entries(corpus.value?.sourceTypeCounts || {})
  .map(([type, count]) => ({ type, count, label: SOURCE_TYPE_LABEL[type] || type })))

const statCards = computed(() => {
  if (!corpus.value) return []
  return [
    { label: '知识内容', value: corpus.value.totalKnowledgeItems ?? 0 },
    { label: '知识来源', value: corpus.value.totalSources ?? 0 },
    { label: '知识页面', value: corpus.value.totalPages ?? 0 },
    { label: '已启用页面', value: corpus.value.activePages ?? 0 },
    {
      label: '索引切片',
      value: corpus.value.indexStatusReliable ? (corpus.value.indexedChunks ?? 0) : '--'
    },
    { label: '待处理问题', value: rawResults.value.length }
  ]
})

async function load() {
  loading.value = true
  try {
    const [lintResult, healthResult] = await Promise.allSettled([
      request('/api/knowledge/lint/results'),
      request('/api/knowledge/corpus-health')
    ])
    rawResults.value = lintResult.status === 'fulfilled' ? (lintResult.value || []) : []
    corpus.value = healthResult.status === 'fulfilled' ? healthResult.value : null
    const failure = [lintResult, healthResult].find(result => result.status === 'rejected')
    if (failure) {
      props.notify(failure.reason?.message || '健康度加载失败', 'error')
    }
  } catch (error) {
    props.notify(error.message || '健康度加载失败', 'error')
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

.corpus {
  padding: var(--space-md) 0;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}

.corpus h3 {
  margin: 0 0 var(--space-xs);
  font-size: 1rem;
  color: var(--color-text);
}

.corpus-line {
  margin: 0 0 var(--space-sm);
  color: var(--color-text-secondary);
  font-size: 0.875rem;
}

.corpus-hint {
  margin: 0 0 var(--space-sm);
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
}

.corpus-hint.danger {
  color: var(--color-danger);
}

.corpus-block {
  margin-top: var(--space-sm);
}

.source-types {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs) var(--space-lg);
  padding: var(--space-sm) 0;
  color: var(--color-text-secondary);
  font-size: 0.8125rem;
}

.source-types strong {
  margin-left: 4px;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

.corpus-block h4 {
  margin: 0 0 4px;
  font-size: 0.8125rem;
  color: var(--color-text);
}

.corpus-block.danger h4 {
  color: var(--color-danger);
}

.corpus-block ul {
  margin: 0;
  padding-left: 1.25rem;
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
