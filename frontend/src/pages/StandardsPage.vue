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
        <header class="standards-overview-head">
          <div class="standards-overview-title">
            <h1>标准文档总览</h1>
            <p>按类别、软件与版本聚合展示已发布标准，点击标准名称或标准文档即可查看详情。</p>
          </div>
          <div class="standards-summary">
            <div v-for="metric in summaryMetrics" :key="metric.label" class="standards-metric">
              <strong>{{ metric.value }}</strong>
              <span>{{ metric.label }}</span>
            </div>
          </div>
        </header>

        <div class="standards-toolbar">
          <input v-model.trim="keyword" type="search" class="standards-search"
            aria-label="搜索标准" placeholder="搜索软件、标准名称、标准文档或版本" />
          <select v-model="sortBy" class="standards-sort" aria-label="标准排序方式">
            <option value="recent">按最近更新</option>
            <option value="documents">按文档数量</option>
            <option value="name">按标准名称</option>
          </select>
        </div>

        <section v-for="group in standardGroups" :key="group.category" class="standard-category-section">
          <div class="standard-category-head">
            <div class="standard-category-title">
              <h2>{{ group.category }}</h2>
              <span class="standard-category-badge">{{ group.standards.length }} 项标准 · {{ group.documentCount }} 份文档</span>
            </div>
            <p class="standard-category-meta">{{ group.softwareSummary }}</p>
          </div>
          <div class="standard-card-grid">
            <article v-for="standard in group.standards" :key="standard.id" class="standard-row standard-card">
              <div class="standard-card-head">
                <button type="button" class="standard-title-link" :title="displayTitle(standard)"
                  @click="openStandardDetail(standard.id)">
                  {{ displayTitle(standard) }}
                </button>
                <span v-if="standard.softwareVersion" class="standard-card-version">{{ standard.softwareVersion }}</span>
              </div>
              <dl class="standard-card-meta">
                <div v-for="field in metaFields(standard)" :key="field.label" class="standard-card-meta-item">
                  <dt>{{ field.label }}</dt>
                  <dd>{{ field.value }}</dd>
                </div>
              </dl>
              <div class="standard-card-docs">
                <button v-for="doc in visibleDocuments(standard)" :key="doc.id" type="button"
                  class="related-document-link" :title="doc.title || '未命名文档'"
                  @click="openDocFromList(standard, doc.id)">
                  {{ doc.title || '未命名文档' }}
                </button>
                <p v-if="!publishedDocuments(standard).length" class="standard-card-docs-empty">暂无已发布标准文档</p>
              </div>
              <div class="standard-card-actions">
                <span>共 {{ publishedDocuments(standard).length }} 份标准文档</span>
                <button v-if="publishedDocuments(standard).length > DOC_PREVIEW_LIMIT" type="button"
                  class="standard-card-more" @click="toggleDocs(standard.id)">
                  {{ docsExpanded[standard.id] ? '收起' : '查看更多' }}
                </button>
              </div>
            </article>
          </div>
        </section>
        <EmptyState v-if="standardGroups.length === 0" :message="emptyMessage" />
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
import { useNotify } from '../composables/useNotify'

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

// 列表卡片默认只展示前若干份标准文档，其余通过「查看更多」展开，避免大量数据时页面被拉长
const DOC_PREVIEW_LIMIT = 3
const UNCATEGORIZED = '未分类'
const SOFTWARE_SUMMARY_LIMIT = 5

const { notify } = useNotify()

// State
const standards = ref([])
const keyword = ref('')
const sortBy = ref('recent')
const docsExpanded = reactive({})
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
const filteredStandards = computed(() => filterItemsByJob(standards.value, selectedJob.value, (standard) => standard.category))
// 关键字过滤 + 排序后的标准，概览指标与分类分区都以它为准，保证「看到什么就统计什么」
const visibleStandards = computed(() => {
  const query = keyword.value.toLowerCase()
  const matched = query
    ? filteredStandards.value.filter((standard) => searchText(standard).includes(query))
    : [...filteredStandards.value]
  return matched.sort(compareStandards)
})
const standardGroups = computed(() => {
  const groups = new Map()
  for (const standard of visibleStandards.value) {
    const category = standard.category || UNCATEGORIZED
    if (!groups.has(category)) groups.set(category, { category, standards: [], documentCount: 0, softwareSummary: '' })
    const group = groups.get(category)
    group.standards.push(standard)
    group.documentCount += publishedDocuments(standard).length
  }
  for (const group of groups.values()) {
    const names = [...new Set(group.standards.map((standard) => standard.software).filter(Boolean))]
    group.softwareSummary = names.slice(0, SOFTWARE_SUMMARY_LIMIT).join('、') + (names.length > SOFTWARE_SUMMARY_LIMIT ? ' 等' : '')
  }
  return [...groups.values()]
})
const summaryMetrics = computed(() => {
  const list = visibleStandards.value
  const documentCount = list.reduce((total, standard) => total + publishedDocuments(standard).length, 0)
  const categories = new Set(list.map((standard) => standard.category || UNCATEGORIZED))
  const latest = list.map(standardTimestamp).filter(Boolean).sort().at(-1)
  return [
    { label: '标准总数', value: list.length },
    { label: '标准文档', value: documentCount },
    { label: '标准类别', value: categories.size },
    { label: '最近更新', value: formatDate(latest) }
  ]
})
const emptyMessage = computed(() => (keyword.value
  ? '未找到匹配的标准，请调整搜索关键字或切换其他类别查看。'
  : '暂无已发布标准，可切换其他类别查看。'))
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

function publishedDocuments(standard) {
  return (standard?.relatedDocuments || []).filter((doc) => !doc.status || doc.status === 'PUBLISHED')
}
function visibleDocuments(standard) {
  const docs = publishedDocuments(standard)
  return docsExpanded[standard.id] ? docs : docs.slice(0, DOC_PREVIEW_LIMIT)
}
function toggleDocs(id) {
  docsExpanded[id] = !docsExpanded[id]
}
function standardTimestamp(standard) {
  return standard.publishedAt || standard.updatedAt || standard.createdAt || ''
}
function formatDate(value) {
  if (!value) return '-'
  const matched = String(value).match(/^\d{4}-\d{2}-\d{2}/)
  return matched ? matched[0] : String(value)
}
function metaFields(standard) {
  return [
    { label: '类别', value: standard.category || UNCATEGORIZED },
    { label: '软件', value: standard.software || '-' },
    { label: '软件版本', value: standard.softwareVersion || '-' },
    { label: '标准版本', value: standard.version || '-' },
    { label: '发布时间', value: formatDate(standardTimestamp(standard)) }
  ]
}
function searchText(standard) {
  return [
    displayTitle(standard),
    standard.category,
    standard.software,
    standard.softwareVersion,
    standard.version,
    ...publishedDocuments(standard).map((doc) => doc.title)
  ].filter(Boolean).join(' ').toLowerCase()
}
function compareStandards(left, right) {
  if (sortBy.value === 'name') return displayTitle(left).localeCompare(displayTitle(right), 'zh-Hans-CN')
  if (sortBy.value === 'documents') {
    const gap = publishedDocuments(right).length - publishedDocuments(left).length
    if (gap !== 0) return gap
  }
  return standardTimestamp(right).localeCompare(standardTimestamp(left))
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
  } catch (error) {
    standards.value = []
    notify(error.message, 'error')
  }
}

// 详情加载失败时统一提示并停留在列表页，避免出现空白详情或无响应
async function openStandardDetail(id) {
  loading.value = true
  selectedDoc.value = null
  try {
    selectedStandard.value = await request(`/api/public/parameter-standards/${id}`, { token: null })
    params.value = await request(`/api/public/standard-parameters?parameterStandardId=${id}`, { token: null }) || []
    expanded[id] = true
    await nextTick()
    initScrollSpy()
  } catch (error) {
    notify(error.message, 'error')
  }
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
    return true
  } catch (error) {
    notify(error.message, 'error')
    return false
  }
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

// 从列表直接点文档：先建立标准上下文，再加载文档；文档加载失败则回退到原上下文
async function openDocFromList(standard, docId) {
  const previousStandard = selectedStandard.value
  selectedStandard.value = standard
  expanded[standard.id] = true
  if (!await openDocDetail(docId)) {
    selectedStandard.value = previousStandard
  }
}

onMounted(loadStandards)
onBeforeUnmount(destroyScrollSpy)
</script>

<style scoped>
.public-module-layout { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: var(--space-xl); padding-top: var(--space-xl); }
.public-module-content { min-width: 0; }

/* ===== 标准发布列表：概览 + 工具栏 + 分类分区 ===== */
.standards-grid { display: grid; gap: var(--space-lg); align-items: start; }
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

.standard-category-section {
  display: flex; flex-direction: column; gap: var(--space-lg);
  border: 1px solid var(--color-border); border-radius: var(--radius-lg);
  padding: var(--space-xl); background: var(--color-bg); box-shadow: var(--shadow-sm);
}
.standard-category-head {
  display: flex; align-items: center; justify-content: space-between;
  flex-wrap: wrap; gap: var(--space-sm) var(--space-lg);
  padding-bottom: var(--space-md); border-bottom: 1px solid var(--color-border);
}
.standard-category-title { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-sm); min-width: 0; }
.standard-category-title h2 { margin: 0; font-size: var(--text-2xl); letter-spacing: 0; }
.standard-category-badge {
  display: inline-flex; align-items: center; border-radius: var(--radius-full);
  padding: var(--space-2xs) var(--space-sm);
  background: var(--color-primary-light); color: var(--color-primary);
  font-size: var(--text-xs); font-weight: 700; white-space: nowrap;
}
.standard-category-meta {
  margin: 0; min-width: 0; color: var(--color-text-secondary); font-size: var(--text-sm);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.standard-card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: var(--space-lg); }
.standard-card {
  display: flex; flex-direction: column; gap: var(--space-md); min-width: 0;
  border: 1px solid var(--color-border); border-radius: var(--radius-lg);
  padding: var(--space-lg); background: var(--color-bg);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}
.standard-card:hover { border-color: var(--color-primary-100); box-shadow: var(--shadow-md); }
.standard-card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-sm); }
.standard-title-link {
  flex: 1; min-width: 0; justify-self: start;
  min-height: auto; padding: 0; border: none;
  color: var(--color-text); background: transparent;
  font-size: var(--text-lg); font-weight: 700; line-height: 1.4; text-align: left;
  overflow-wrap: anywhere; word-break: break-word; cursor: pointer;
  transition: color var(--transition-fast);
}
.standard-title-link:hover { color: var(--color-primary); background: transparent; }
.standard-card-version {
  flex: 0 0 auto; display: inline-flex; align-items: center;
  border-radius: var(--radius-sm); padding: var(--space-2xs) var(--space-sm);
  background: var(--color-success-light); color: var(--color-success);
  font-size: var(--text-xs); font-weight: 700; white-space: nowrap;
}
.standard-card-meta { display: flex; flex-wrap: wrap; gap: var(--space-xs) var(--space-md); margin: 0; }
.standard-card-meta-item { display: flex; gap: var(--space-2xs); min-width: 0; font-size: var(--text-xs); }
.standard-card-meta-item dt { color: var(--color-text-tertiary); }
.standard-card-meta-item dt::after { content: '：'; }
.standard-card-meta-item dd { margin: 0; color: var(--color-text-secondary); overflow-wrap: anywhere; }

.standard-card-docs { display: flex; flex-direction: column; gap: var(--space-sm); }
.related-document-link {
  display: block; width: 100%; min-height: 34px; border: none;
  border-radius: var(--radius-sm); padding: var(--space-sm) var(--space-md);
  background: var(--color-primary-light); color: var(--color-primary);
  font-size: var(--text-base); font-weight: 600; line-height: 1.4; text-align: left;
  overflow-wrap: anywhere; word-break: break-word; cursor: pointer;
  transition: background var(--transition-fast);
}
.related-document-link:hover { background: var(--color-primary-50); text-decoration: underline; }
.standard-card-docs-empty { margin: 0; color: var(--color-text-tertiary); font-size: var(--text-sm); }
.standard-card-actions {
  display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm);
  margin-top: auto; padding-top: var(--space-sm); border-top: 1px solid var(--color-border);
  color: var(--color-text-tertiary); font-size: var(--text-xs);
}
.standard-card-more {
  border: 1px solid var(--color-primary-100); border-radius: var(--radius-sm);
  padding: var(--space-xs) var(--space-md);
  background: var(--color-bg); color: var(--color-primary);
  font-size: var(--text-xs); font-weight: 700; white-space: nowrap; cursor: pointer;
}
.standard-card-more:hover { background: var(--color-primary-light); }

@media (max-width: 1120px) {
  .standards-summary { grid-template-columns: repeat(2, minmax(96px, 1fr)); }
}
@media (max-width: 760px) {
  .standards-overview-head, .standard-category-section { padding: var(--space-lg); }
  .standards-toolbar { grid-template-columns: 1fr; }
  .standard-card-grid { grid-template-columns: 1fr; }
  .standard-category-meta { white-space: normal; }
}
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
