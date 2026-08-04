<template>
  <div class="search-tab">
    <Toolbar>
      <template #filters>
        <BaseInput
          v-model="query"
          placeholder="搜索参数名、错误码、命令或直接描述问题…"
          @keyup.enter="runSearch"
        />
        <BaseInput v-model="category" placeholder="分类（可选）" />
        <BaseInput v-model="software" placeholder="软件（可选）" />
        <BaseButton variant="primary" :loading="loading" @click="runSearch">检索</BaseButton>
      </template>
    </Toolbar>

    <LoadingSpinner v-if="loading" text="检索中…" />

    <EmptyState
      v-else-if="searched && !results.length"
      icon="🔍"
      :message="searchError || '没有命中任何内容。可以换个说法，或确认文档是否已导入。'"
    />

    <div v-else-if="results.length" class="results">
      <p class="summary">命中 {{ results.length }} 条</p>
      <article v-for="(item, i) in results" :key="i" class="result">
        <header>
          <StatusBadge :status="item.kind" :label="KIND_LABEL[item.kind]" />
          <span class="title">{{ item.title }}</span>
          <span v-if="item.score != null" class="score">{{ item.score.toFixed(3) }}</span>
        </header>
        <p v-if="item.sectionPath" class="breadcrumb">{{ item.sectionPath }}</p>
        <p class="snippet">{{ item.snippet }}</p>
        <p v-if="item.meta" class="meta">{{ item.meta }}</p>
      </article>
    </div>

    <EmptyState v-else icon="🔍" message="输入问题开始检索。标准、经验和原始文档会一并返回。" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { request } from '../../api.js'
import BaseButton from '../ui/BaseButton.vue'
import BaseInput from '../ui/BaseInput.vue'
import EmptyState from '../ui/EmptyState.vue'
import LoadingSpinner from '../ui/LoadingSpinner.vue'
import StatusBadge from '../ui/StatusBadge.vue'
import Toolbar from '../ui/Toolbar.vue'

const props = defineProps({ notify: { type: Function, required: true } })

const KIND_LABEL = { chunk: '文档片段', page: '经验' }

const query = ref('')
const category = ref('')
const software = ref('')
const results = ref([])
const loading = ref(false)
const searched = ref(false)
const searchError = ref('')

const SEARCH_UNAVAILABLE_MESSAGE = '知识库检索服务暂不可用，请稍后重试。'

function snippetOf(text, limit = 300) {
  if (!text) return ''
  return text.length > limit ? `${text.slice(0, limit)}…` : text
}

async function runSearch() {
  const q = query.value.trim()
  if (!q) return
  loading.value = true
  searchError.value = ''
  try {
    const params = new URLSearchParams({ q, topK: '8' })
    if (category.value.trim()) params.set('category', category.value.trim())
    if (software.value.trim()) params.set('software', software.value.trim())

    // 原始文档片段与经验页面并行检索，失败的一路不影响另一路
    const [chunkAttempt, pageAttempt] = await Promise.allSettled([
      request(`/api/knowledge/search?${params}`),
      request(`/api/knowledge/pages/search?q=${encodeURIComponent(q)}&limit=8`)
    ])
    const chunks = chunkAttempt.status === 'fulfilled' ? chunkAttempt.value || [] : []
    const pages = pageAttempt.status === 'fulfilled' ? pageAttempt.value || [] : []

    results.value = [
      ...(chunks || []).map(c => ({
        kind: 'chunk',
        title: c.sourceTitle || '未命名文档',
        sectionPath: c.sectionPath || '',
        snippet: snippetOf(c.content),
        score: typeof c.score === 'number' ? c.score : null,
        meta: [c.category, c.software, c.sourceType].filter(Boolean).join(' · ')
      })),
      ...(pages || []).map(p => ({
        kind: 'page',
        title: p.title || '未命名页面',
        sectionPath: '',
        snippet: snippetOf(p.summary || p.content),
        score: null,
        meta: [p.category, p.software, p.pageType].filter(Boolean).join(' · ')
      }))
    ]
    searched.value = true
    if ((chunkAttempt.status === 'rejected' || pageAttempt.status === 'rejected')
        && !results.value.length) {
      searchError.value = SEARCH_UNAVAILABLE_MESSAGE
      props.notify(SEARCH_UNAVAILABLE_MESSAGE, 'error')
    }
  } catch (error) {
    searchError.value = SEARCH_UNAVAILABLE_MESSAGE
    props.notify(error.message, 'error')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.search-tab {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.summary {
  color: var(--color-text-secondary);
  font-size: 0.875rem;
  margin: 0;
}

.results {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.result {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  background: var(--color-bg);
}

.result header {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  flex-wrap: wrap;
}

.title {
  font-weight: 600;
  color: var(--color-text);
}

.score {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
  font-size: 0.8125rem;
}

.breadcrumb {
  color: var(--color-primary);
  font-size: 0.8125rem;
  margin: var(--space-xs) 0 0;
}

.snippet {
  color: var(--color-text);
  margin: var(--space-xs) 0 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.meta {
  color: var(--color-text-secondary);
  font-size: 0.8125rem;
  margin: var(--space-xs) 0 0;
}
</style>
