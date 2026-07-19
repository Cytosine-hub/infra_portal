<template>
  <section class="workspace standards-page">
    <div class="public-module-layout">
      <JobNavigation :model-value="selectedJob" @update:model-value="handleJobChange" />
      <div class="public-module-content">
    <div v-if="selectedStandard" class="standards-detail-layout">
      <!-- 左侧树形目录 -->
      <aside class="standards-tree">
        <div class="tree-header">
          <h3>标准文档</h3>
          <button class="ghost" @click="closeDetail()">返回列表</button>
        </div>
        <div class="tree-content">
          <div v-for="std in filteredStandards" :key="std.id" class="tree-group">
            <!-- 一级目录：参数标准 -->
            <div :class="['tree-item', 'tree-parent', { active: selectedStandard?.id === std.id && !selectedDoc }]" @click="openStandardDetail(std.id)">
              <span class="tree-toggle" @click.stop="toggleExpand(std.id)">{{ expanded[std.id] ? '▼' : '▶' }}</span>
              <span class="tree-label">{{ std.software || '-' }} / {{ std.softwareVersion || '-' }}</span>
            </div>
            <!-- 二级目录：标准文档 -->
            <div v-if="expanded[std.id] && std.relatedDocuments?.length" class="tree-children">
              <div v-for="doc in std.relatedDocuments" :key="doc.id"
                :class="['tree-item', 'tree-child', { active: selectedDoc?.id === doc.id }]"
                @click="openDocDetail(doc.id)">
                <span class="tree-label">{{ doc.title || '未命名' }}</span>
              </div>
            </div>
          </div>
          <p v-if="filteredStandards.length === 0" class="tree-empty">暂无标准</p>
        </div>
      </aside>

      <!-- 右侧内容区 -->
      <article class="standards-detail-content">
        <div class="standard-detail-head">
          <div>
            <h2>{{ displayTitle(selectedStandard) }}</h2>
            <p class="muted">
              {{ selectedStandard.category || '-' }} / {{ selectedStandard.software || '-' }}
              · 软件版本：{{ selectedStandard.softwareVersion || '-' }}
              · 版本：{{ selectedStandard.version || '-' }}
            </p>
          </div>
          <span class="status ok">已发布</span>
        </div>

        <div v-if="loading" class="loading-panel"><div class="spinner"></div><p>加载中...</p></div>

        <!-- 参数标准详情：显示标准文档和参数 -->
        <template v-if="!selectedDoc">
          <div v-if="relatedDocs.length > 0" class="doc-card-list">
            <h3 class="doc-card-list-title">标准文档</h3>
            <div class="doc-card-grid">
              <a v-for="doc in relatedDocs" :key="doc.id" class="doc-card" href="#" @click.prevent="openDocDetail(doc.id)">
                <span class="doc-card-title">{{ doc.title }}</span>
                <span class="doc-card-meta muted">v{{ doc.version || '-' }}</span>
              </a>
            </div>
          </div>
          <div v-else-if="!loading" class="muted doc-empty-hint">暂无标准文档</div>

          <div v-if="params.length > 0" class="public-params-section">
            <div class="public-params-header">
              <h3>参数列表</h3>
              <input v-model.trim="paramSearch" placeholder="搜索参数..." class="public-params-search" @input="paramPage.page = 0" />
            </div>
            <div class="public-params-count muted">共 {{ filteredParams.length }} 项参数</div>
            <div class="public-params-table">
              <div class="public-param-thead">
                <span class="col-name">参数名称</span>
                <span class="col-value">参数值</span>
                <span class="col-type">参数类型</span>
                <span class="col-range">取值范围</span>
                <span class="col-desc">说明</span>
              </div>
              <article v-for="param in pagedParams" :key="param.id" class="public-param-item">
                <div class="public-param-row">
                  <span class="public-param-name">{{ param.name }}</span>
                  <span class="public-param-value">{{ param.value }}</span>
                  <span class="public-param-type">{{ param.paramType || '-' }}</span>
                  <span class="public-param-range">{{ param.valueRange || '-' }}</span>
                  <span class="public-param-desc-cell">
                    {{ param.description || '-' }}
                    <span v-if="param.deploymentStandard" class="status ok" style="font-size:11px;padding:1px 6px;margin-left:6px">部署标准</span>
                  </span>
                </div>
              </article>
            </div>
            <Pagination :page="paramPage" @change="(p) => paramPage.page = p" />
          </div>
        </template>

        <!-- 标准文档详情：显示文档内容 -->
        <template v-else>
          <!-- PDF 文档 -->
          <template v-if="isPdfDoc">
            <PdfDocumentPreview class="public-document-preview" :src="rawPublicUrl" :parameters="params" />
          </template>
          <!-- Word 文档 -->
          <template v-else-if="selectedDoc.storedFileName">
            <WordDocumentPreview
              class="public-document-preview"
              :file-name="selectedDoc.storedFileName"
              :raw-url="rawPublicUrl"
              :preview-url="htmlPublicUrl"
              :parameters="params"
              :request-options="{ token: null }"
            />
          </template>
          <!-- Markdown 文档 -->
          <MarkdownDocumentPreview v-else class="markdown-preview public-document" :html="docHtml" />
        </template>
      </article>

      <aside class="post-toc-panel" v-if="tocItems.length && !selectedDoc?.storedFileName">
        <h4 class="toc-title">文档大纲</h4>
        <button v-for="item in tocItems" :key="item.id"
          :class="['toc-link', { active: activeTocId === item.id }]"
          :style="{ '--toc-level': item.level - 1 }"
          @click="scrollTo(item.id)"
        >{{ item.text }}</button>
      </aside>
    </div>

    <!-- 列表页 -->
    <template v-else>
      <div class="standards-grid">
        <section v-for="group in standardGroups" :key="group.category" class="standard-category-section">
          <div class="standard-category-head">
            <div>
              <p class="eyebrow">Category</p>
              <h2>{{ group.category }}</h2>
            </div>
            <span>{{ group.standards.length }} 项标准</span>
          </div>
          <div class="standard-list">
            <article v-for="standard in group.standards" :key="standard.id" class="standard-row">
              <div class="standard-row-main">
                <button type="button" class="standard-title-link" @click="openStandardDetail(standard.id)">
                  {{ standard.software || '-' }} / {{ displayTitle(standard) }}
                </button>
                <div class="manual-list">
                  <button v-for="doc in standard.relatedDocuments" :key="doc.id" type="button"
                    class="ghost related-document-link" @click="openDocFromList(standard, doc.id)">
                    {{ doc.title }}
                  </button>
                  <span v-if="!standard.relatedDocuments?.length" class="muted">暂无已发布标准文档</span>
                </div>
              </div>
            </article>
          </div>
        </section>
        <EmptyState v-if="filteredStandards.length === 0" message="当前岗位暂无已发布标准，可切换其他岗位查看。" />
      </div>
    </template>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { request } from '../api'
import MarkdownIt from 'markdown-it'
import Pagination from '../components/Pagination.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import PdfDocumentPreview from '../components/previews/PdfDocumentPreview.vue'
import WordDocumentPreview from '../components/previews/WordDocumentPreview.vue'
import MarkdownDocumentPreview from '../components/previews/MarkdownDocumentPreview.vue'
import JobNavigation from '../shared/jobs/JobNavigation.vue'
import { filterItemsByJob } from '../shared/jobs/jobFilter.js'
import { useJobFilter } from '../shared/jobs/useJobFilter.js'

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

// State
const standards = ref([])
const selectedStandard = ref(null)
const selectedDoc = ref(null)
const activeTocId = ref('')
const params = ref([])
const paramSearch = ref('')
const paramPage = reactive({ page: 0, size: 10, totalPages: 0, totalElements: 0, first: true, last: true })
const loading = ref(false)
const expanded = reactive({})
const { selectedJob, selectJob } = useJobFilter()

const isPdfDoc = computed(() =>
  selectedDoc.value?.storedFileName?.toLowerCase().endsWith('.pdf') ?? false
)
const rawPublicUrl = computed(() =>
  selectedDoc.value?.storedFileName
    ? `/api/public/standards/raw?storedFileName=${encodeURIComponent(selectedDoc.value.storedFileName)}`
    : ''
)
const htmlPublicUrl = computed(() =>
  selectedDoc.value?.storedFileName
    ? `/api/public/standards/preview?storedFileName=${encodeURIComponent(selectedDoc.value.storedFileName)}`
    : ''
)

let scrollHandler = null

// Computed
const relatedDocs = computed(() => {
  if (!selectedStandard.value) return []
  return (selectedStandard.value.relatedDocuments || []).filter(d => d.status === 'PUBLISHED')
})
const standardGroups = computed(() => {
  const groups = new Map()
  for (const s of filteredStandards.value) {
    const cat = s.category || '未分类'
    if (!groups.has(cat)) groups.set(cat, { category: cat, standards: [] })
    groups.get(cat).standards.push(s)
  }
  return [...groups.values()]
})
const filteredStandards = computed(() => filterItemsByJob(standards.value, selectedJob.value, (standard) => standard.category))
const docHtml = computed(() => {
  const doc = selectedDoc.value || selectedStandard.value
  if (!doc) return ''
  let html = md.render(doc.renderedContent || doc.content || '')
  let idx = 0
  html = html.replace(/<(h[1-3])([^>]*)>([\s\S]*?)<\/\1>/g, (_, tag, attrs, inner) => {
    return `<${tag} id="std-heading-${idx++}"${attrs}>${inner}</${tag}>`
  })
  return html
})
const tocItems = computed(() => {
  const doc = selectedDoc.value || selectedStandard.value
  if (!doc) return []
  const content = doc.renderedContent || doc.content || ''
  const items = []
  const regex = /(?:^|\n)#{1,3}\s+(.+)/g
  let m, idx = 0
  while ((m = regex.exec(content)) !== null) {
    const level = m[0].trim().split(' ')[0].length
    items.push({ id: `std-heading-${idx}`, level, text: m[1].trim() })
    idx++
  }
  return items
})
const filteredParams = computed(() => {
  if (!paramSearch.value) return params.value
  const q = paramSearch.value.toLowerCase()
  return params.value.filter(p => (p.name && p.name.toLowerCase().includes(q)) || (p.value && p.value.toLowerCase().includes(q)) || (p.description && p.description.toLowerCase().includes(q)))
})
const pagedParams = computed(() => {
  const total = filteredParams.value.length
  const totalPages = Math.max(1, Math.ceil(total / paramPage.size))
  paramPage.totalPages = totalPages
  paramPage.totalElements = total
  paramPage.first = paramPage.page <= 0
  paramPage.last = paramPage.page >= totalPages - 1
  if (paramPage.page >= totalPages) paramPage.page = Math.max(0, totalPages - 1)
  const start = paramPage.page * paramPage.size
  return filteredParams.value.slice(start, start + paramPage.size)
})

// Functions
function displayTitle(doc) {
  if (!doc) return ''
  return doc.title || [doc.category, doc.software, doc.softwareVersion].filter(Boolean).join(' / ') || '未命名'
}
function toggleExpand(id) {
  expanded[id] = !expanded[id]
}

function handleJobChange(jobId) {
  selectJob(jobId)
  if (selectedStandard.value && !filterItemsByJob([selectedStandard.value], jobId, (standard) => standard.category).length) {
    closeDetail()
  }
}

async function loadStandards() {
  try {
    const data = await request('/api/public/parameter-standards?size=100', { token: null })
    standards.value = (Array.isArray(data) ? data : (data?.content ?? []))
  } catch { standards.value = [] }
}

async function openStandardDetail(id) {
  loading.value = true
  selectedDoc.value = null
  try {
    selectedStandard.value = await request(`/api/public/parameter-standards/${id}`, { token: null })
    params.value = await request(`/api/public/standard-parameters?parameterStandardId=${id}`, { token: null }) || []
    expanded[id] = true
    await nextTick()
    initScrollSpy()
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function openDocDetail(id) {
  loading.value = true
  try {
    const doc = await request(`/api/public/standards/${id}`, { token: null })
    if (doc?.relatedStandardDocumentId) {
      params.value = await request(`/api/public/standard-parameters?parameterStandardId=${doc.relatedStandardDocumentId}`, { token: null }) || []
    } else {
      params.value = []
    }
    selectedDoc.value = doc
    await nextTick()
    initScrollSpy()
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function closeDetail() {
  selectedStandard.value = null
  selectedDoc.value = null
  params.value = []
  destroyScrollSpy()
}
function scrollTo(id) {
  const el = document.getElementById(id)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
function initScrollSpy() {
  destroyScrollSpy()
  scrollHandler = () => {
    const items = tocItems.value
    if (!items.length) return
    let current = ''
    for (const item of items) {
      const el = document.getElementById(item.id)
      if (el && el.getBoundingClientRect().top <= 120) current = item.id
    }
    activeTocId.value = current
  }
  window.addEventListener('scroll', scrollHandler, { passive: true })
}
function destroyScrollSpy() {
  if (scrollHandler) { window.removeEventListener('scroll', scrollHandler); scrollHandler = null }
}

// 从列表直接点文档：先建立标准上下文，再加载文档
async function openDocFromList(standard, docId) {
  selectedStandard.value = standard
  expanded[standard.id] = true
  await openDocDetail(docId)
}

onMounted(loadStandards)
onBeforeUnmount(destroyScrollSpy)
</script>

<style scoped>
.public-module-layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: var(--space-xl); padding-top: var(--space-xl); }
.public-module-content { min-width: 0; }
.standards-tree {
  width: 260px; border-right: 1px solid var(--color-border);
  display: flex; flex-direction: column; flex-shrink: 0; overflow: hidden;
}
.tree-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border);
}
.tree-header h3 { margin: 0; font-size: var(--text-base); }
.tree-content {
  flex: 1; overflow-y: auto; padding: var(--space-sm) 0;
}
.tree-item {
  display: flex; align-items: center; gap: var(--space-sm);
  padding: var(--space-sm) var(--space-lg);
  cursor: pointer; font-size: var(--text-sm);
  transition: background var(--transition-fast);
}
.tree-item:hover { background: var(--color-bg-tertiary); }
.tree-item.active { background: var(--color-primary-light); color: var(--color-primary); }
.tree-parent { font-weight: 500; }
@media (max-width: 760px) {
  .public-module-layout { grid-template-columns: 1fr; }
}
.tree-child { padding-left: calc(var(--space-lg) + 20px); font-weight: 400; }
.tree-toggle {
  width: 16px; text-align: center; font-size: var(--text-xs); color: var(--color-text-tertiary);
  flex-shrink: 0; cursor: pointer;
}
.tree-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tree-empty { padding: var(--space-lg); color: var(--color-text-tertiary); font-size: var(--text-sm); }

/* 标准文档卡片列表 */
.doc-card-list { margin-bottom: var(--space-lg); }
.doc-card-list-title {
  margin: 0 0 var(--space-md); font-size: var(--text-lg); font-weight: 600;
  color: var(--color-text); padding-bottom: var(--space-sm);
  border-bottom: 1px solid var(--color-border);
}
.doc-card-grid {
  display: flex; flex-wrap: wrap; gap: var(--space-md);
}
.doc-card {
  display: flex; flex-direction: column; gap: var(--space-xs);
  padding: var(--space-md) var(--space-lg); border-radius: var(--radius-lg);
  border: 1px solid var(--color-border); background: var(--color-bg);
  cursor: pointer; transition: box-shadow 0.15s, border-color 0.15s;
  min-width: 200px; flex: 0 0 auto; max-width: 300px;
  text-decoration: none; color: var(--color-text);
}
.doc-card:hover {
  border-color: var(--color-primary); box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.doc-card-title { font-size: var(--text-base); font-weight: 500; color: var(--color-primary); }
.doc-card-meta { font-size: var(--text-xs); color: var(--color-text-tertiary); }
.doc-empty-hint { padding: var(--space-md) 0; }
.public-document-preview {
  width: 100%;
  height: calc(100vh - 220px);
  min-height: 500px;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-bg-secondary);
}
.error-hint { padding: var(--space-md) 0; color: var(--color-danger); font-size: var(--text-sm); }
</style>
