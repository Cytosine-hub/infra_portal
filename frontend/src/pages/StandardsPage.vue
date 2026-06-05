<template>
  <section class="workspace standards-page">
    <div v-if="selectedStandard" class="standards-detail-layout">
      <aside class="standards-tree">
        <div class="tree-header">
          <h3>标准文档</h3>
          <button class="ghost" @click="closeDetail()">返回列表</button>
        </div>
        <div class="category-tabs">
          <button
            v-for="cat in docCategories"
            :key="cat"
            :class="['category-tab', { active: docCategory === cat }]"
            @click="docCategory = cat"
          >{{ cat }}</button>
        </div>
        <div class="tree-docs">
          <button
            v-for="doc in filteredDocuments"
            :key="docKey(doc)"
            :class="['tree-doc-link', { active: selectedKey === docKey(doc) }]"
            @click="openDocDetail(doc.id, doc.documentType)"
          >
            {{ displayTitle(doc) }}
          </button>
        </div>
      </aside>
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
        <div v-if="selectedStandard.documentType !== 'MANUAL' && selectedStandard.documentType !== 'ARTICLE' && relatedDocs.length > 0" class="doc-nav-list">
          <a
            v-for="doc in relatedDocs"
            :key="docKey(doc)"
            class="doc-nav-link"
            href="#"
            @click.prevent="openDocDetail(doc.id, doc.documentType)"
          >
            <span class="doc-nav-title">{{ displayTitle(doc) }}</span>
            <span class="doc-nav-meta muted">{{ typeLabel(doc.documentType) }} · v{{ doc.version || '-' }}</span>
          </a>
        </div>

        <div v-if="selectedStandard.documentType !== 'MANUAL' && selectedStandard.documentType !== 'ARTICLE' && relatedDocs.length === 0 && params.length === 0 && !loading" class="muted" style="padding:16px 0">暂无相关手册和参数标准！</div>

        <div v-if="loading" class="loading-panel"><div class="spinner"></div><p>加载中...</p></div>
        <div v-else-if="selectedStandard.documentType === 'MANUAL' || selectedStandard.documentType === 'ARTICLE'" class="markdown-preview public-document" v-html="standardHtml"></div>

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
              <span class="col-desc">说明</span>
            </div>
            <article v-for="param in pagedParams" :key="param.id" class="public-param-item">
              <div class="public-param-row">
                <span class="public-param-name">{{ param.name }}</span>
                <span class="public-param-value">{{ param.value }}</span>
                <span class="public-param-desc-cell">
                  {{ param.description || '-' }}
                  <span v-if="param.deploymentStandard" class="status ok" style="font-size:11px;padding:1px 6px;margin-left:6px">部署标准</span>
                </span>
              </div>
            </article>
          </div>
          <Pagination :page="paramPage" @change="(p) => paramPage.page = p" />
        </div>
      </article>
      <aside class="post-toc-panel" v-if="tocItems.length">
        <h4 class="toc-title">文档大纲</h4>
        <button
          v-for="item in tocItems"
          :key="item.id"
          :class="['toc-link', { active: activeTocId === item.id }]"
          :style="{ '--toc-level': item.level - 1 }"
          @click="scrollTo(item.id)"
        >{{ item.text }}</button>
      </aside>
    </div>

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
                  <span v-if="standard.status === 'MODIFYING'" class="status warn" style="font-size:12px;font-weight:400;margin-left:8px">修改中</span>
                </button>
                <div class="manual-list">
                  <button
                    v-for="doc in standard.relatedDocuments"
                    :key="doc.id"
                    type="button"
                    class="ghost related-document-link"
                    @click="openDocDetail(doc.id)"
                  >
                    {{ doc.title }}
                  </button>
                  <span v-if="!standard.relatedDocuments?.length" class="muted">暂无已发布关联手册</span>
                </div>
              </div>
              <span>{{ standard.softwareVersion || '-' }}</span>
              <span>{{ formatDate(standard.publishedAt || standard.updatedAt) }}</span>
            </article>
          </div>
        </section>
        <p v-if="standards.length === 0" class="empty-state">暂无已发布标准。</p>
      </div>
    </template>
  </section>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { request } from '../api'
import { formatDate, renderMarkdown } from '../utils'
import MarkdownIt from 'markdown-it'
import Pagination from '../components/Pagination.vue'

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

// State
const standards = ref([])
const selectedStandard = ref(null)
const documents = ref([])
const docCategory = ref('全部')
const activeTocId = ref('')
const params = ref([])
const paramSearch = ref('')
const paramPage = reactive({ page: 0, size: 10, totalPages: 0, totalElements: 0, first: true, last: true })
const loading = ref(false)

let scrollHandler = null

// Computed
const selectedKey = computed(() => docKey(selectedStandard.value))
const docCategories = computed(() => ['全部', ...new Set(documents.value.map(d => d.category || '未分类'))])
const filteredDocuments = computed(() => {
  if (docCategory.value === '全部') return documents.value
  return documents.value.filter(d => (d.category || '未分类') === docCategory.value)
})
const relatedDocs = computed(() => {
  if (!selectedStandard.value) return []
  return documents.value.filter(d => d.parameterStandardId === selectedStandard.value.id)
})
const standardGroups = computed(() => {
  const groups = new Map()
  for (const s of standards.value) {
    const cat = s.category || '未分类'
    if (!groups.has(cat)) groups.set(cat, { category: cat, standards: [] })
    groups.get(cat).standards.push(s)
  }
  return [...groups.values()]
})
const standardHtml = computed(() => {
  if (!selectedStandard.value) return ''
  let html = md.render(selectedStandard.value.renderedContent || selectedStandard.value.content || '')
  let idx = 0
  html = html.replace(/<(h[1-3])([^>]*)>([\s\S]*?)<\/\1>/g, (_, tag, attrs, inner) => {
    const id = `std-heading-${idx++}`
    return `<${tag} id="${id}"${attrs}>${inner}</${tag}>`
  })
  return html
})
const tocItems = computed(() => {
  if (!selectedStandard.value) return []
  const content = selectedStandard.value.renderedContent || selectedStandard.value.content || ''
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
function docKey(doc) { return doc ? `${doc.documentType || 'ps'}_${doc.id}` : '' }
function typeLabel(type) {
  if (type === 'STANDARD' || type === 'PARAMETER_STANDARD') return '参数标准'
  if (type === 'ARTICLE') return '文章'
  if (type === 'MANUAL') return '手册'
  return '标准'
}

async function loadStandards() {
  standards.value = await request('/api/public/parameter-standards?size=100', { token: null })
}
async function loadDocuments() {
  try {
    const [stds, docs] = await Promise.all([
      request('/api/public/parameter-standards?size=100', { token: null }),
      request('/api/public/standards?size=1000', { token: null })
    ])
    standards.value = stds || []
    documents.value = (docs || []).map(d => ({ ...d, parameterStandardId: d.parameterStandardId || null }))
  } catch { /* ignore */ }
}
async function openStandardDetail(id) {
  loading.value = true
  try {
    selectedStandard.value = await request(`/api/public/parameter-standards/${id}`, { token: null })
    params.value = await request(`/api/public/standard-parameters?parameterStandardId=${id}`, { token: null }) || []
    await nextTick()
    initScrollSpy()
  } catch { /* ignore */ }
  finally { loading.value = false }
}
async function openDocDetail(id) {
  loading.value = true
  try {
    selectedStandard.value = await request(`/api/public/standard-documents/${id}`, { token: null })
    params.value = await request(`/api/public/standard-parameters?standardDocumentId=${id}`, { token: null }) || []
    await nextTick()
    initScrollSpy()
  } catch { /* ignore */ }
  finally { loading.value = false }
}
function closeDetail() {
  selectedStandard.value = null
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

onMounted(loadDocuments)
onBeforeUnmount(destroyScrollSpy)
</script>
