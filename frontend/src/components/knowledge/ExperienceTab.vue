<template>
  <div class="experience-tab">
    <Toolbar>
      <BaseInput v-model="keyword" placeholder="按标题筛选…" @keyup.enter="load" />
      <BaseButton variant="ghost" :loading="loading" @click="load">刷新</BaseButton>
      <BaseButton variant="ghost" @click="exportPages">导出</BaseButton>
    </Toolbar>

    <div class="layout">
      <div class="list">
        <DataTable
          :columns="columns"
          :data="pages"
          :loading="loading"
          empty-text="还没有经验页面。团队可以把排查心得、Runbook 沉淀在这里。"
        >
          <template #cell-status="{ row }">
            <StatusBadge :status="row.status" :label="STATUS_LABEL[row.status] || row.status" />
          </template>
          <template #actions="{ row }">
            <BaseButton size="sm" variant="ghost" @click="open(row)">查看</BaseButton>
            <BaseButton size="sm" variant="danger" @click="confirmRemove(row)">删除</BaseButton>
          </template>
        </DataTable>
      </div>

      <aside v-if="selected" class="detail">
        <h3>{{ selected.title }}</h3>
        <p class="meta">
          {{ [selected.category, selected.software, PAGE_TYPE_LABEL[selected.pageType] || selected.pageType]
             .filter(Boolean).join(' · ') }}
        </p>
        <p v-if="selected.summary" class="summary">{{ selected.summary }}</p>
        <pre class="content">{{ selected.content }}</pre>
        <div v-if="links.length" class="links">
          <h4>关联页面</h4>
          <ul>
            <li v-for="l in links" :key="l.id">{{ l.title || l.toPageTitle || l.toPageId }}</li>
          </ul>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { request } from '../../api.js'
import BaseButton from '../ui/BaseButton.vue'
import BaseInput from '../ui/BaseInput.vue'
import DataTable from '../ui/DataTable.vue'
import StatusBadge from '../ui/StatusBadge.vue'
import Toolbar from '../ui/Toolbar.vue'

const props = defineProps({
  notify: { type: Function, required: true },
  confirm: { type: Function, required: true }
})

const STATUS_LABEL = {
  DRAFT: '草稿', PENDING_REVIEW: '待审核', ACTIVE: '已发布',
  STALE: '已过期', CONTRADICTED: '有矛盾', REJECTED: '已驳回'
}
const PAGE_TYPE_LABEL = {
  EXPERIENCE: '经验', RUNBOOK: '操作手册', CONCEPT: '概念',
  ENTITY: '实体', STANDARD: '标准', SYNTHESIS: '综述', OVERVIEW: '总览'
}

const columns = [
  { key: 'title', label: '标题' },
  { key: 'category', label: '分类', style: 'width: 110px' },
  { key: 'software', label: '软件', style: 'width: 120px' },
  { key: 'status', label: '状态', style: 'width: 100px' }
]

const pages = ref([])
const selected = ref(null)
const links = ref([])
const keyword = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const q = keyword.value.trim()
    pages.value = q
      ? await request(`/api/knowledge/pages/search?q=${encodeURIComponent(q)}&limit=100`)
      : await request('/api/knowledge/pages')
  } catch (error) {
    props.notify(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function open(row) {
  try {
    selected.value = await request(`/api/knowledge/pages/${row.id}`)
    links.value = await request(`/api/knowledge/pages/${row.id}/links`).catch(() => [])
  } catch (error) {
    props.notify(error.message, 'error')
  }
}

function confirmRemove(row) {
  props.confirm(`确认删除经验页面「${row.title}」？`, async () => {
    try {
      await request(`/api/knowledge/pages/${row.id}`, { method: 'DELETE' })
      if (selected.value?.id === row.id) selected.value = null
      props.notify('已删除', 'success')
      await load()
    } catch (error) {
      props.notify(error.message, 'error')
    }
  })
}

async function exportPages() {
  try {
    window.open('/api/knowledge/pages/export', '_blank')
  } catch (error) {
    props.notify(error.message, 'error')
  }
}

onMounted(load)
</script>

<style scoped>
.experience-tab {
  display: flex;
  flex-direction: column;
  gap: var(--space-4, 16px);
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-4, 16px);
}

@media (min-width: 1100px) {
  .layout {
    grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  }
}

.detail {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  padding: var(--space-4, 16px);
  background: var(--color-surface);
  max-height: 70vh;
  overflow: auto;
}

.detail h3 {
  margin: 0 0 var(--space-2, 8px);
  color: var(--color-text);
}

.meta,
.summary {
  color: var(--color-text-muted);
  font-size: 0.875rem;
  margin: 0 0 var(--space-2, 8px);
}

.content {
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text);
  font-size: 0.875rem;
  margin: 0;
}

.links h4 {
  margin: var(--space-4, 16px) 0 var(--space-2, 8px);
  font-size: 0.875rem;
  color: var(--color-text);
}

.links ul {
  margin: 0;
  padding-left: 1.25rem;
  color: var(--color-text-muted);
  font-size: 0.875rem;
}
</style>
