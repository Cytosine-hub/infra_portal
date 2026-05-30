<template>
  <div class="knowledge-layout">
    <!-- 左侧导航 -->
    <aside class="knowledge-nav">
      <h3>知识库</h3>
      <button
        v-for="tab in tabs"
        :key="tab.key"
        :class="['nav-item', { active: currentTab === tab.key }]"
        @click="currentTab = tab.key"
      >
        <span class="nav-icon">{{ tab.icon }}</span>
        {{ tab.label }}
      </button>
    </aside>

    <!-- 右侧内容 -->
    <div class="knowledge-content">
      <!-- 文档列表 -->
      <template v-if="currentTab === 'docs'">
        <div class="content-header">
          <h3>文档列表 ({{ kbDocs.length }})</h3>
          <div class="header-actions">
            <button @click="showUploadModal = true">上传文档</button>
            <button class="ghost" @click="showImportModal = true">导入文档</button>
            <button class="ghost" @click="loadKbDocs">刷新</button>
          </div>
        </div>
        <div class="docs-list" v-if="kbDocs.length > 0">
          <div v-for="(doc, idx) in kbDocs" :key="idx" class="doc-item" @click="handlePreviewDoc(doc)">
            <span class="doc-icon">{{ getDocIcon(doc.source_type) }}</span>
            <div class="doc-info">
              <span class="doc-title">{{ doc.source_title || '未知' }}</span>
              <span class="doc-meta">{{ doc.chunk_count }} 个切片 · {{ doc.source_type }}</span>
            </div>
            <button class="danger ghost" @click.stop="handleDeleteDoc(doc)">删除</button>
          </div>
        </div>
        <p v-else class="empty-state">暂无文档，请上传或导入。</p>
      </template>

      <!-- 知识图谱 -->
      <template v-if="currentTab === 'graph'">
        <div class="graph-wrapper">
          <div ref="graphContainer" class="graph-container"></div>
        </div>
      </template>

      <!-- 检索测试 -->
      <template v-if="currentTab === 'search'">
        <div class="content-header">
          <h3>检索测试</h3>
        </div>
        <div class="search-section">
          <div class="search-bar">
            <input v-model.trim="searchQuery" placeholder="输入关键词测试检索" @keyup.enter="handleSearch" />
            <select v-model.number="searchTopK">
              <option :value="3">Top 3</option>
              <option :value="5">Top 5</option>
              <option :value="10">Top 10</option>
            </select>
            <button :disabled="!searchQuery || searching" @click="handleSearch">
              {{ searching ? '检索中...' : '检索' }}
            </button>
          </div>
          <div v-if="searchResults.length > 0" class="search-results">
            <article v-for="(item, idx) in searchResults" :key="idx" class="search-result-card">
              <div class="result-header">
                <span>{{ item.source_title || item.source || '未知来源' }}</span>
                <span class="result-score">{{ formatScore(item.score) }}</span>
              </div>
              <p class="result-content">{{ item.content || '-' }}</p>
            </article>
          </div>
          <p v-else-if="searched" class="empty-state">未找到相关结果。</p>
        </div>
      </template>

      <!-- 上传弹窗 -->
      <div v-if="showUploadModal" class="modal-backdrop" @click.self="showUploadModal = false">
        <div class="modal-panel">
          <h3>批量上传文档</h3>
          <p>支持 PDF、Word、Markdown 格式</p>
          <label class="file-field">
            <input type="file" accept=".pdf,.doc,.docx,.md,.markdown" multiple @change="onFileChange" />
            <span class="file-button">选择文件</span>
            <span>{{ uploadFiles.length > 0 ? `已选 ${uploadFiles.length} 个` : '未选择' }}</span>
          </label>
          <div v-if="uploadFiles.length > 0" class="file-list">
            <div v-for="(f, i) in uploadFiles" :key="i" class="file-item">
              {{ f.name }} ({{ formatSize(f.size) }})
            </div>
          </div>
          <div class="modal-actions">
            <button :disabled="uploadFiles.length === 0 || uploading" @click="handleBatchUpload">
              {{ uploading ? '上传中...' : '开始上传' }}
            </button>
            <button class="ghost" @click="showUploadModal = false">取消</button>
          </div>
          <div v-if="uploadResults" class="upload-results">
            <span class="success">成功 {{ uploadResults.filter(r => r.status === 'success').length }}</span>
            <span v-if="uploadResults.filter(r => r.status === 'error').length > 0" class="error">
              失败 {{ uploadResults.filter(r => r.status === 'error').length }}
            </span>
          </div>
        </div>
      </div>

      <!-- 导入弹窗（按文档标题搜索） -->
      <div v-if="showImportModal" class="modal-backdrop" @click.self="showImportModal = false">
        <div class="modal-panel">
          <h3>导入文档到知识库</h3>
          <p>从已有标准文档中选择导入</p>
          <input v-model="importSearch" placeholder="搜索文档标题..." @input="filterStandardDocs" />
          <div class="import-doc-list">
            <div
              v-for="doc in filteredStandardDocs"
              :key="doc.id"
              class="import-doc-item"
              @click="handleImportDoc(doc)"
            >
              <span class="doc-type-badge">{{ doc.documentType }}</span>
              <span>{{ doc.title }}</span>
            </div>
            <p v-if="filteredStandardDocs.length === 0 && importSearch" class="empty-state">未找到匹配的文档</p>
          </div>
          <div class="modal-actions">
            <button class="ghost" @click="showImportModal = false">关闭</button>
          </div>
        </div>
      </div>

      <!-- 文档预览弹窗 -->
      <div v-if="showPreviewModal" class="modal-backdrop" @click.self="showPreviewModal = false">
        <div class="modal-panel preview-modal">
          <h3>{{ previewDoc.title || '文档预览' }}</h3>
          <p>{{ previewDoc.sourceType }} · {{ previewDoc.totalChunks }} 个切片</p>
          <div v-if="previewLoading" class="empty-state">加载中...</div>
          <div v-else class="preview-chunks">
            <div v-for="(chunk, i) in previewDoc.chunks" :key="i" class="preview-chunk">
              <div class="chunk-header">切片 #{{ chunk.chunkIndex + 1 }}</div>
              <pre class="chunk-content">{{ chunk.content }}</pre>
            </div>
          </div>
          <div class="modal-actions">
            <button class="ghost" @click="showPreviewModal = false">关闭</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { request } from '../api'
import ForceGraph3D from '3d-force-graph'

const props = defineProps({ auth: Object, notify: { type: Function, default: (msg, type) => type === 'error' ? alert(msg) : alert(msg) } })

// 标签页
const tabs = [
  { key: 'docs', icon: '📄', label: '文档列表' },
  { key: 'graph', icon: '🌐', label: '知识图谱' },
  { key: 'search', icon: '🔍', label: '检索测试' }
]
const currentTab = ref('docs')

// 弹窗
const showUploadModal = ref(false)
const showImportModal = ref(false)
const importSearch = ref('')
const standardDocs = ref([])
const filteredStandardDocs = ref([])

// 文档上传
const uploadFiles = ref([])
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadResults = ref(null)

// 检索测试
const searchQuery = ref('')
const searchTopK = ref(5)
const searching = ref(false)
const searchResults = ref([])
const searched = ref(false)

// 已导入文档
const kbDocs = ref([])
const deleting = ref(false)

// 文档预览
const showPreviewModal = ref(false)
const previewLoading = ref(false)
const previewDoc = ref({ title: '', sourceType: '', chunks: [], totalChunks: 0 })

// 知识图谱
const graphContainer = ref(null)
let graph = null

onMounted(() => { loadKbDocs() })

onBeforeUnmount(() => {
  if (graph) {
    graph._destructor?.()
  }
})

// ── 文档管理 ──

async function loadKbDocs() {
  try {
    kbDocs.value = await request('/api/knowledge/docs')
    if (!Array.isArray(kbDocs.value)) kbDocs.value = []
  } catch { kbDocs.value = [] }
}

async function handleDeleteDoc(doc) {
  if (!confirm(`确定删除文档「${doc.source_title}」？此操作不可恢复。`)) return
  deleting.value = true
  try {
    await request(`/api/knowledge/docs?title=${encodeURIComponent(doc.source_title)}&sourceType=${doc.source_type}`, { method: 'DELETE' })
    await loadKbDocs()
  } catch (e) {
    props.notify('删除失败：' + (e.message || '未知错误'), 'error')
  } finally {
    deleting.value = false
  }
}

function getDocIcon(sourceType) {
  if (sourceType === 'UPLOAD') return '📄'
  if (sourceType === 'STANDARD_DOC') return '📋'
  return '📁'
}

async function handlePreviewDoc(doc) {
  showPreviewModal.value = true
  previewLoading.value = true
  previewDoc.value = { title: doc.source_title, sourceType: doc.source_type, chunks: [], totalChunks: 0 }
  try {
    const q = `title=${encodeURIComponent(doc.source_title)}&sourceType=${encodeURIComponent(doc.source_type)}`
    const data = await request(`/api/knowledge/docs/preview?${q}`)
    previewDoc.value = data
  } catch (e) {
    props.notify('预览失败：' + (e.message || '未知错误'), 'error')
    showPreviewModal.value = false
  } finally {
    previewLoading.value = false
  }
}

function onFileChange(event) {
  const files = event.target.files
  uploadFiles.value = files ? Array.from(files) : []
  uploadResults.value = null
  uploadProgress.value = 0
}

async function handleBatchUpload() {
  if (uploadFiles.value.length === 0 || uploading.value) return
  uploading.value = true
  uploadResults.value = null
  uploadProgress.value = 0
  try {
    const formData = new FormData()
    uploadFiles.value.forEach(f => formData.append('files', f))
    const result = await request('/api/knowledge/batch-upload', { method: 'POST', body: formData })
    uploadResults.value = result?.results || []
    uploadProgress.value = uploadFiles.value.length
  } catch (error) {
    uploadResults.value = [{ fileName: '批量上传', status: 'error', error: error.message }]
  } finally {
    uploading.value = false
  }
}

function formatSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function handleSearch() {
  if (!searchQuery.value || searching.value) return
  searching.value = true
  searchResults.value = []
  searched.value = false
  try {
    const q = encodeURIComponent(searchQuery.value)
    const result = await request(`/api/knowledge/search?q=${q}&topK=${searchTopK.value}`)
    searchResults.value = Array.isArray(result) ? result : (result?.results || result?.data || [])
    searched.value = true
  } catch (error) {
    searchResults.value = []
    searched.value = true
    props.notify(error.message || '检索失败', 'error')
  } finally {
    searching.value = false
  }
}

function formatScore(score) {
  if (score == null) return '-'
  return typeof score === 'number' ? (score * 100).toFixed(1) + '%' : String(score)
}

// ── 导入 ──

async function loadStandardDocs() {
  try {
    standardDocs.value = await request('/api/admin/standard-documents') || []
    filteredStandardDocs.value = standardDocs.value
  } catch {
    standardDocs.value = []
    filteredStandardDocs.value = []
  }
}

function filterStandardDocs() {
  const kw = importSearch.value.toLowerCase()
  filteredStandardDocs.value = standardDocs.value.filter(d =>
    !kw || (d.title || '').toLowerCase().includes(kw)
  )
}

async function handleImportDoc(doc) {
  try {
    await request(`/api/knowledge/import/${doc.id}`, { method: 'POST' })
    props.notify(`导入成功：${doc.title}`, 'success')
    showImportModal.value = false
    await loadKbDocs()
  } catch (e) {
    props.notify('导入失败：' + (e.message || '未知错误'), 'error')
  }
}

// 打开导入弹窗时加载文档列表
watch(showImportModal, (val) => {
  if (val) loadStandardDocs()
})

// ── 知识图谱 ──

watch(currentTab, async (tab) => {
  if (tab === 'graph') {
    await nextTick()
    initGraph()
  }
})

async function initGraph() {
  if (!graphContainer.value) return
  try {
    // 确保容器有尺寸
    await nextTick()
    const container = graphContainer.value
    const w = container.clientWidth || container.offsetWidth || 600
    const h = container.clientHeight || container.offsetHeight || 400

    const data = await request('/api/knowledge/graph')
    if (!data || !data.nodes?.length) return

    graph = ForceGraph3D({ controlType: 'orbit' })(container)
      .width(w)
      .height(h)
      .backgroundColor('#1a2744')
      .nodeLabel(n => `${n.name} (${n.val}次)`)
      .nodeColor(n => n.group === 'keyword' ? '#4a9eff' : '#4aff8e')
      .nodeVal(n => Math.max(n.val * 0.6, 1.5))
      .nodeResolution(8)
      .linkColor(() => 'rgba(255,255,255,0.4)')
      .linkWidth(l => Math.max(l.value * 1.5, 1))
      .linkDirectionalParticles(1)
      .linkDirectionalParticleWidth(2)
      .linkDirectionalParticleColor(() => '#4a9eff')
      .onNodeClick(n => {
        if (n && n.x != null) {
          const distance = 80
          const distRatio = 1 + distance / Math.sqrt((n.x || 0) ** 2 + (n.y || 0) ** 2 + (n.z || 0) ** 2)
          graph.cameraPosition(
            { x: (n.x || 0) * distRatio, y: (n.y || 0) * distRatio, z: (n.z || 0) * distRatio },
            n,
            1500
          )
        }
      })

    const N = data.nodes.length
    const r = Math.max(50, N * 2)
    data.nodes.forEach((n, i) => {
      const phi = Math.acos(-1 + 2 * i / N)
      const theta = Math.sqrt(N * Math.PI) * phi
      n.x = r * Math.cos(theta) * Math.sin(phi)
      n.y = r * Math.sin(theta) * Math.sin(phi)
      n.z = r * Math.cos(phi)
    })
    graph.graphData(data)

    graph.d3Force('charge').strength(-120)
    graph.d3Force('link').distance(60)
  } catch (e) {
    console.warn('3D graph init error:', e)
  }
}
</script>

<style scoped>
/* 布局 */
.knowledge-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(35,58,93,0.06);
}

/* 左侧导航 */
.knowledge-nav {
  width: 200px;
  min-width: 200px;
  background: #f8fafc;
  border-right: 1px solid #d8e1ec;
  padding: 16px 0;
  flex-shrink: 0;
}

.knowledge-nav h3 {
  color: #1e293b;
  font-size: 16px;
  padding: 0 16px 16px;
  margin: 0;
  border-bottom: 1px solid #e2e8f0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 12px 16px;
  border: none;
  background: none;
  color: #526071;
  font-size: 14px;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
}

.nav-item:hover { background: #eef2f7; color: #1e293b; }
.nav-item.active { background: #2356a5; color: #fff; }
.nav-icon { font-size: 16px; }

/* 右侧内容 */
.knowledge-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  color: #1e293b;
  position: relative;
}

.content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.content-header h3 { margin: 0; font-size: 17px; color: #1e293b; }
.header-actions { display: flex; gap: 8px; }

/* 文档列表 */
.docs-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.doc-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  transition: all 0.15s;
}

.doc-item:hover { background: #eef2f7; border-color: #c8d5e2; cursor: pointer; }
.doc-icon { font-size: 20px; }
.doc-info { flex: 1; }
.doc-title { display: block; font-size: 14px; font-weight: 500; color: #1e293b; }
.doc-meta { font-size: 12px; color: #94a3b8; }

/* 图谱 */
/* 图谱 */
.graph-wrapper {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
}

.graph-container {
  width: 100%;
  height: 100%;
}

/* 检索测试 — 百度风格 */
.search-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 60px;
}

.search-bar {
  display: flex;
  gap: 12px;
  width: 700px;
  max-width: 95%;
  margin-bottom: 32px;
  align-items: stretch;
}

.search-bar input {
  flex: 1;
  padding: 12px 18px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 15px;
  color: #1e293b;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.search-bar input:focus {
  border-color: #2356a5;
  box-shadow: 0 0 0 3px rgba(35,86,165,0.12);
}

.search-bar select {
  padding: 10px 8px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 13px;
  color: #526071;
  background: #fff;
  width: 80px;
  cursor: pointer;
}

.search-bar button {
  padding: 12px 28px;
  border: none;
  background: #2356a5;
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
}

.search-bar button:hover { background: #1a4590; }
.search-bar button:disabled { background: #94a3b8; }

/* 搜索结果 */
.search-results {
  width: 700px;
  max-width: 95%;
}

.search-result-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 12px;
  transition: border-color 0.15s;
}

.search-result-card:hover { border-color: #2356a5; }

.result-header {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 8px;
}

.result-source { font-weight: 600; color: #526071; }
.result-score { color: #2356a5; font-weight: 500; }

.result-content {
  font-size: 14px;
  color: #334155;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 弹窗 */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(35,58,93,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal-panel {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  min-width: 440px;
  max-width: 520px;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 16px 48px rgba(35,58,93,0.2);
}

.modal-panel h3 { margin: 0 0 6px; font-size: 17px; color: #1e293b; }
.modal-panel p { color: #64748b; font-size: 13px; margin: 0 0 16px; }

.modal-panel input[type="text"],
.modal-panel input:not([type]) {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
  color: #1e293b;
  margin-bottom: 12px;
  box-sizing: border-box;
  outline: none;
}

.modal-panel input:focus { border-color: #2356a5; box-shadow: 0 0 0 2px rgba(35,86,165,0.12); }

.modal-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 16px;
}

.file-field {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  font-size: 14px;
  color: #526071;
  margin-bottom: 12px;
}

.file-field input { display: none; }

.file-button {
  background: #eef2f7;
  color: #2356a5;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  border: 1px solid #d8e1ec;
}

.file-button:hover { background: #dce4ee; }

.file-list {
  max-height: 150px;
  overflow-y: auto;
  margin-bottom: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 4px;
}

.file-item {
  padding: 4px 8px;
  font-size: 13px;
  color: #526071;
}

.import-doc-list {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}

.import-doc-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  font-size: 14px;
  color: #1e293b;
  border-bottom: 1px solid #f1f5f9;
  transition: background 0.1s;
}

.import-doc-item:last-child { border-bottom: none; }
.import-doc-item:hover { background: #eef2f7; }

.doc-type-badge {
  background: #2356a5;
  color: #fff;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 3px;
  flex-shrink: 0;
  font-weight: 500;
}

.upload-results {
  margin-top: 12px;
  padding: 10px 14px;
  background: #f8fafc;
  border-radius: 6px;
  font-size: 13px;
  border: 1px solid #e2e8f0;
}

.upload-results .success { color: #16a34a; margin-right: 12px; font-weight: 500; }
.upload-results .error { color: #dc2626; font-weight: 500; }

/* 文档预览 */
.preview-modal {
  min-width: 600px;
  max-width: 800px;
  max-height: 85vh;
}

.preview-chunks {
  max-height: 55vh;
  overflow-y: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  margin-bottom: 12px;
}

.preview-chunk {
  padding: 12px 16px;
  border-bottom: 1px solid #f1f5f9;
}

.preview-chunk:last-child { border-bottom: none; }

.chunk-header {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 600;
  margin-bottom: 6px;
}

.chunk-content {
  font-size: 13px;
  color: #334155;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-family: inherit;
}

/* 按钮复用全局样式 */
.empty-state { color: #94a3b8; text-align: center; padding: 60px 0; font-size: 14px; }
</style>
