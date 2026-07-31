<template>
  <div class="health-tab">
    <Toolbar>
      <template #filters>
        <BaseButton variant="primary" :loading="running" @click="runLint">运行体检</BaseButton>
        <span class="hint">检查孤儿页、断链、过期内容与矛盾条目</span>
      </template>
    </Toolbar>

    <div v-if="corpus" class="stat-row">
      <div v-for="s in statCards" :key="s.label" class="stat">
        <span class="stat-value">{{ s.value }}</span>
        <span class="stat-label">{{ s.label }}</span>
      </div>
    </div>

    <section v-if="corpus" class="corpus">
      <h3>标准覆盖度</h3>
      <p class="corpus-line">
        后台软件 {{ corpus.catalogSoftwareCount }} 类 ·
        覆盖率
        <strong>{{ (corpus.coverage * 100).toFixed(1) }}%</strong>
        （{{ corpus.coveredCells }} / {{ corpus.totalCells }} 个格子）·
        参数 {{ corpus.totalParameters }} 条
      </p>
      <p
        v-if="!corpus.targetCatalogConfigured"
        class="corpus-hint"
        :class="{ danger: corpus.catalogStatusReliable === false }"
      >
        {{ corpus.coverageHint }}
      </p>
      <p v-if="!corpus.indexStatusReliable" class="corpus-hint danger">
        向量库查询失败，本次不判定索引状态（避免把「查不到」误读成「没索引」）
      </p>

      <div v-if="corpus.missingCells?.length" class="corpus-block">
        <h4>待补的标准（{{ corpus.missingCells.length }}）—— 可直接当内容待办</h4>
        <ul><li v-for="c in corpus.missingCells" :key="c">{{ c }}</li></ul>
      </div>

      <div v-if="corpus.unclassifiedStandards?.length" class="corpus-block">
        <h4>标题无法归类的标准（{{ corpus.unclassifiedStandards.length }}）—— 不计入覆盖率，是「归类不了」不是「没写」</h4>
        <ul><li v-for="c in corpus.unclassifiedStandards" :key="c">{{ c }}</li></ul>
      </div>

      <div v-if="corpus.parameterConflicts?.length" class="corpus-block danger">
        <h4>参数取值矛盾（{{ corpus.parameterConflicts.length }}）—— 两份标准都生效，按哪个做都有依据</h4>
        <ul><li v-for="c in corpus.parameterConflicts" :key="c">{{ c }}</li></ul>
      </div>

      <div v-if="corpus.indexStatusReliable && corpus.unindexedSources?.length" class="corpus-block">
        <h4>未索引的文档（{{ corpus.unindexedSources.length }}）—— 列表里看得到，检索命中不了</h4>
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

const statCards = computed(() => {
  if (!corpus.value) return []
  return [
    { label: '知识内容', value: corpus.value.totalKnowledgeItems ?? 0 },
    { label: '经验沉淀', value: corpus.value.experiencePages ?? 0 },
    { label: '上传文档', value: corpus.value.uploadedDocuments ?? 0 },
    { label: '标准文档', value: corpus.value.standardDocuments ?? 0 },
    {
      label: '未索引文档',
      value: corpus.value.indexStatusReliable ? (corpus.value.unindexedSources?.length ?? 0) : '--'
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
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  background: var(--color-bg);
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
