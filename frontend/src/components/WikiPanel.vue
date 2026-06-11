<template>
  <div class="wiki-panel">
    <!-- 左侧边栏 -->
    <aside class="wiki-sidebar">
      <div class="sidebar-tabs">
        <button :class="{ active: sidebarTab === 'pages' }" @click="sidebarTab = 'pages'">页面</button>
        <button :class="{ active: sidebarTab === 'graph' }" @click="sidebarTab = 'graph'; loadGraph()">图谱</button>
        <button :class="{ active: sidebarTab === 'sources' }" @click="sidebarTab = 'sources'; loadSources()">来源</button>
        <button :class="{ active: sidebarTab === 'lint' }" @click="sidebarTab = 'lint'; loadLintResults()">Lint</button>
      </div>

      <!-- 页面树 -->
      <div v-if="sidebarTab === 'pages'" class="sidebar-content">
        <div class="search-box">
          <input v-model="searchQuery" type="text" placeholder="搜索 Wiki 页面..." @keyup.enter="searchPages" />
        </div>
        <div class="filter-row">
          <select v-model="filterStatus" @change="loadPages">
            <option value="">全部状态</option>
            <option value="ACTIVE">已发布</option>
            <option value="DRAFT">草稿</option>
            <option value="PENDING_REVIEW">待审核</option>
            <option value="CONTRADICTED">有冲突</option>
            <option value="REJECTED">已拒绝</option>
            <option value="ARCHIVED">已归档</option>
          </select>
        </div>

        <!-- 树形目录 -->
        <div class="tree-container">
          <div v-if="loading" class="loading">加载中...</div>
          <div v-else-if="treeData.length === 0" class="empty">暂无页面</div>
          <div v-else>
            <div v-for="cat in treeData" :key="cat.name" class="tree-category">
              <div class="tree-label category-label" @click="cat.expanded = !cat.expanded">
                <span class="tree-arrow">{{ cat.expanded ? '▼' : '▶' }}</span>
                <span class="tree-icon">📁</span>
                <span>{{ cat.name || '未分类' }}</span>
                <span class="tree-count">{{ cat.totalCount }}</span>
              </div>
              <div v-if="cat.expanded" class="tree-children">
                <div v-for="sw in cat.softwares" :key="sw.name" class="tree-software">
                  <div class="tree-label software-label" @click="sw.expanded = !sw.expanded">
                    <span class="tree-arrow">{{ sw.expanded ? '▼' : '▶' }}</span>
                    <span class="tree-icon">📦</span>
                    <span>{{ sw.name || '通用' }}</span>
                    <span class="tree-count">{{ sw.pages.length }}</span>
                  </div>
                  <div v-if="sw.expanded" class="tree-children">
                    <div v-for="page in sw.pages" :key="page.id"
                         :class="['tree-page', { active: selectedPage?.id === page.id }]"
                         @click="selectPage(page)">
                      <span class="page-status-dot" :class="page.status?.toLowerCase()"></span>
                      <span class="page-title-text">{{ page.title }}</span>
                      <span class="page-type-tag">{{ page.pageType }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 图谱 -->
      <div v-if="sidebarTab === 'graph'" class="sidebar-content">
        <div class="graph-stats">
          <div>节点: {{ graphData.nodes?.length || 0 }}</div>
          <div>连接: {{ graphData.links?.length || 0 }}</div>
          <div>社区: {{ graphData.communityCount || 0 }}</div>
        </div>
        <div v-if="graphData.communities" class="community-legend">
          <div class="legend-title">社区聚类</div>
          <div v-for="(name, cid) in graphData.communities" :key="cid" class="legend-item">
            <span class="legend-dot" :style="{ background: communityColor(Number(cid)) }"></span>
            <span class="legend-label">{{ name }}</span>
          </div>
        </div>
      </div>

      <!-- 来源文档列表 -->
      <div v-if="sidebarTab === 'sources'" class="sidebar-content">
        <div class="source-list">
          <div v-if="sources.length === 0" class="empty">暂无来源文档</div>
          <div v-for="src in sources" :key="src.id"
               :class="['source-item', { active: selectedSource?.id === src.id }]"
               @click="selectSource(src)">
            <span class="source-type-tag">{{ src.sourceType }}</span>
            <span class="source-title-text">{{ src.title }}</span>
            <span v-if="isSourceCompiling(src.id)" class="source-status-dot compiling" title="编译中"></span>
            <span v-else-if="src.ingested" class="source-status-dot ingested" title="已编译"></span>
            <span v-else class="source-status-dot pending" title="未编译"></span>
          </div>
        </div>
      </div>

      <!-- Lint 面板 -->
      <div v-if="sidebarTab === 'lint'" class="sidebar-content">
        <div class="lint-summary">
          <div class="lint-stat high">
            <span class="lint-count">{{ lintCount('HIGH') }}</span>
            <span>高</span>
          </div>
          <div class="lint-stat medium">
            <span class="lint-count">{{ lintCount('MEDIUM') }}</span>
            <span>中</span>
          </div>
          <div class="lint-stat low">
            <span class="lint-count">{{ lintCount('LOW') }}</span>
            <span>低</span>
          </div>
        </div>
        <div class="lint-actions">
          <button class="action-btn primary" @click="runLint" :disabled="lintRunning">
            {{ lintRunning ? '检测中...' : '运行 Lint' }}
          </button>
        </div>
        <div class="lint-list">
          <div v-if="lintResults.length === 0" class="empty">暂无问题</div>
          <div v-for="r in lintResults" :key="r.id"
               :class="['lint-item', r.severity?.toLowerCase()]">
            <div class="lint-item-header">
              <span :class="['lint-severity', r.severity?.toLowerCase()]">{{ r.severity }}</span>
              <span class="lint-type">{{ lintTypeLabel(r.lintType) }}</span>
            </div>
            <div class="lint-desc">{{ r.description }}</div>
            <div class="lint-item-actions">
              <button v-if="r.pageId" class="btn-tiny" @click="viewPageById(r.pageId)">查看</button>
              <button class="btn-tiny" @click="resolveLint(r)">标记已解决</button>
            </div>
          </div>
        </div>
      </div>
    </aside>

    <!-- 右侧内容区 -->
    <main class="wiki-content">
      <!-- 操作栏 -->
      <div class="action-bar">
        <div class="action-left">
          <button class="action-btn primary" @click="openUploadDialog" title="上传文档，由 LLM 编译为 Wiki 页面（支持批量上传）">
            上传文档
          </button>
          <button class="action-btn" @click="showTextIngest = true" title="手动粘贴文本内容">手动录入</button>
          <button class="action-btn" @click="openBatchCategory" title="批量设置未分类页面的分类">批量分类</button>
          <span class="action-divider"></span>
          <button class="action-btn" @click="exportWiki" title="导出 Wiki 为 ZIP 包">⬇ 导出</button>
          <label class="action-btn" style="cursor:pointer" title="从 ZIP 包导入 Wiki">
            ⬆ 导入
            <input type="file" accept=".zip" @change="importWiki" style="display:none" />
          </label>
        </div>
        <div class="action-right" v-if="selectedPage && !editing">
          <button v-if="selectedPage.status === 'DRAFT' || selectedPage.status === 'REJECTED'"
                  class="action-btn primary" @click="submitForReview">提交审核</button>
          <button v-if="selectedPage.status === 'PENDING_REVIEW'"
                  class="action-btn success" @click="approvePage">审核通过</button>
          <button v-if="selectedPage.status === 'PENDING_REVIEW'"
                  class="action-btn danger" @click="rejectPage">审核拒绝</button>
          <button v-if="selectedPage.status === 'CONTRADICTED'"
                  class="action-btn warning" @click="showResolveDialog = true">解决冲突</button>
          <button class="action-btn" @click="startEdit">✏ 编辑</button>
          <button class="action-btn danger ghost" @click="deletePage">删除</button>
        </div>
      </div>

      <!-- 导入结果提示 -->
      <div v-if="importResult" class="import-result" :class="importResult.success ? 'success' : 'error'">
        <div>
          <span>{{ importResult.message }}</span>
          <div v-if="importResult.conflicts?.length" class="conflict-links">
            <span v-for="c in importResult.conflicts" :key="c.existingPageId" class="conflict-link-item">
              冲突: <a href="#" @click.prevent="viewPageById(c.existingPageId)">{{ c.title }}</a>
              (现有) vs
              <a href="#" @click.prevent="viewPageById(c.importedPageId)">{{ c.title }} [导入版本]</a>
            </span>
          </div>
        </div>
        <button @click="importResult = null" class="close-btn">&times;</button>
      </div>

      <!-- 图谱视图 -->
      <div v-if="sidebarTab === 'graph'" class="graph-view">
        <div ref="graphContainer" class="graph-container"></div>
        <div v-if="selectedGraphNode" class="graph-node-detail">
          <h4>{{ selectedGraphNode.name }}</h4>
          <p>类型: {{ selectedGraphNode.pageType }}</p>
          <p>分类: {{ selectedGraphNode.category || '通用' }}</p>
          <p>软件: {{ selectedGraphNode.software || '通用' }}</p>
          <p>社区: <span class="community-tag" :style="{ background: communityColor(selectedGraphNode.community) }">{{ selectedGraphNode.communityName || '社区 ' + selectedGraphNode.community }}</span></p>
          <p>状态: <span :class="['status-badge', selectedGraphNode.status?.toLowerCase()]">{{ selectedGraphNode.status }}</span></p>
          <button class="btn-small" @click="viewPageById(selectedGraphNode.pageId)">查看页面</button>
          <button class="btn-small ghost" @click="selectedGraphNode = null">关闭</button>
        </div>
      </div>

      <!-- 来源文档详情 -->
      <div v-else-if="selectedSource" class="source-detail">
        <div class="page-header">
          <h2>{{ selectedSource.title }}</h2>
          <div class="page-meta">
            <span class="meta-tag">{{ selectedSource.sourceType }}</span>
            <span class="meta-tag" v-if="selectedSource.category">{{ selectedSource.category }}</span>
            <span class="meta-tag" v-if="selectedSource.software">{{ selectedSource.software }}</span>
            <span :class="['status-badge', selectedSource.ingested ? 'active' : 'draft']">
              {{ selectedSource.ingested ? '已编译' : '未编译' }}
            </span>
          </div>
          <div class="page-audit" v-if="selectedSource.createdAt">
            上传时间: {{ formatDate(selectedSource.createdAt) }}
            <span v-if="selectedSource.ingestedAt"> | 编译时间: {{ formatDate(selectedSource.ingestedAt) }}</span>
          </div>
          <div class="page-audit" v-if="selectedSource.contentHash">
            内容哈希: <code>{{ selectedSource.contentHash?.substring(0, 16) }}...</code>
          </div>
          <div style="margin-top: 12px; display: flex; gap: 8px;">
            <button v-if="getSourceTask(selectedSource.id)" class="action-btn" @click="showSourceTask(selectedSource.id)">
              查看编译进度 ({{ getSourceTask(selectedSource.id)?.progress || 0 }}%)
            </button>
            <button v-else-if="!selectedSource.ingested" class="action-btn primary" @click="reingestSource">编译此文档</button>
            <button class="action-btn danger" @click="deleteSource">删除此文档</button>
          </div>
        </div>
        <div class="source-content">
          <h3>原始内容</h3>
          <pre class="source-raw">{{ selectedSource.content }}</pre>
        </div>
      </div>

      <!-- 编辑模式 -->
      <div v-else-if="editing" class="edit-mode">
        <div class="edit-header">
          <h3>编辑页面</h3>
          <div class="edit-actions">
            <button class="action-btn success" @click="saveEdit" :disabled="saving">{{ saving ? '保存中...' : '保存' }}</button>
            <button class="action-btn ghost" @click="cancelEdit">取消</button>
          </div>
        </div>
        <div class="edit-form">
          <div class="form-row">
            <label>标题</label>
            <input v-model="editForm.title" type="text" class="form-input" />
          </div>
          <div class="form-row three-col">
            <div>
              <label>分类</label>
              <input v-model="editForm.category" type="text" class="form-input" placeholder="如: 中间件" />
            </div>
            <div>
              <label>软件</label>
              <input v-model="editForm.software" type="text" class="form-input" placeholder="如: Nginx" />
            </div>
            <div>
              <label>版本</label>
              <input v-model="editForm.version" type="text" class="form-input" placeholder="如: 1.24" />
            </div>
          </div>
          <div class="form-row">
            <label>摘要</label>
            <input v-model="editForm.summary" type="text" class="form-input" placeholder="一句话描述..." />
          </div>
          <div class="form-row">
            <label>内容 (Markdown，支持 [[页面名]] 链接)</label>
            <textarea v-model="editForm.content" class="form-textarea" rows="25"></textarea>
          </div>
        </div>
      </div>

      <!-- 页面详情 -->
      <div v-else-if="selectedPage" class="page-detail">
        <div class="page-header">
          <h2>{{ selectedPage.title }}</h2>
          <div class="page-meta">
            <span class="meta-tag">{{ selectedPage.pageType }}</span>
            <span class="meta-tag" v-if="selectedPage.category">{{ selectedPage.category }}</span>
            <span class="meta-tag" v-if="selectedPage.software">{{ selectedPage.software }}</span>
            <span class="meta-tag" v-if="selectedPage.version">v{{ selectedPage.version }}</span>
            <span :class="['status-badge', selectedPage.status?.toLowerCase()]">{{ statusLabel(selectedPage.status) }}</span>
          </div>
          <p v-if="selectedPage.summary" class="page-summary">{{ selectedPage.summary }}</p>
          <div v-if="selectedPage.status === 'CONTRADICTED' && selectedPage.contradictionNote" class="contradiction-banner">
            <strong>冲突说明：</strong> {{ selectedPage.contradictionNote }}
          </div>
          <div class="page-audit" v-if="selectedPage.compiledBy || selectedPage.reviewedBy">
            <span v-if="selectedPage.compiledBy">编译: {{ selectedPage.compiledBy }}</span>
            <span v-if="selectedPage.compiledAt"> | {{ formatDate(selectedPage.compiledAt) }}</span>
            <span v-if="selectedPage.reviewedBy"> | 审核人 ID: {{ selectedPage.reviewedBy }}</span>
            <span v-if="selectedPage.reviewedAt"> | {{ formatDate(selectedPage.reviewedAt) }}</span>
          </div>
        </div>

        <!-- 关联页面 -->
        <div v-if="pageLinks.length > 0" class="related-section">
          <h3>关联页面</h3>
          <div class="related-list">
            <span v-for="link in pageLinks" :key="link.linkId"
                  class="related-chip"
                  :class="[link.direction, link.linkType === 'CONTRADICTS' ? 'contradicts' : '']"
                  @click="viewPageById(link.relatedPageId)">
              {{ link.direction === 'incoming' ? '←' : '→' }} {{ link.relatedTitle }}
              <small>{{ link.linkType }}</small>
            </span>
          </div>
        </div>

        <!-- 页面内容 -->
        <div class="page-body" v-html="renderedContent"></div>
      </div>

      <!-- 欢迎页（无选中内容时显示） -->
      <div v-else class="wiki-welcome">
        <div class="welcome-icon">📚</div>
        <h2>Wiki 知识库</h2>
        <p>从左侧选择页面查看详情，或使用上方按钮上传文档、手动录入</p>
      </div>

      <!-- 解决冲突对话框 -->
      <div v-if="showResolveDialog" class="modal-overlay" @click.self="showResolveDialog = false">
        <div class="modal-dialog wide">
          <h3>解决冲突</h3>
          <p class="modal-desc">当前页面状态为 <span class="status-badge contradicted">CONTRADICTED</span></p>
          <div v-if="selectedPage?.contradictionNote" class="conflict-note">
            <pre class="conflict-note-text">{{ selectedPage.contradictionNote }}</pre>
          </div>
          <div v-if="conflictImportedPageId" class="conflict-compare-hint">
            <a href="#" @click.prevent="viewPageById(conflictImportedPageId)">点击查看导入版本进行对比</a>
          </div>
          <div class="form-row">
            <label>解决方案说明</label>
            <textarea v-model="resolveNote" class="form-textarea" rows="3" placeholder="描述如何解决了冲突，例如：已合并导入版本的 XX 内容..."></textarea>
          </div>
          <div class="modal-actions">
            <button class="action-btn success" @click="resolveKeepCurrent">保留当前版本</button>
            <button class="action-btn primary" @click="resolveUseImported">使用导入版本</button>
            <button class="action-btn ghost" @click="resolveManualMerge">手动合并</button>
            <button class="action-btn ghost" @click="showResolveDialog = false">取消</button>
          </div>
        </div>
      </div>

      <!-- 批量分类对话框 -->
      <div v-if="showBatchCategory" class="modal-overlay" @click.self="showBatchCategory = false">
        <div class="modal-dialog" style="width:480px">
          <h3 style="margin-bottom:16px">批量设置分类</h3>
          <p style="font-size:13px;color:#8b949e;margin-bottom:12px">
            将 {{ uncategorizedPages.length }} 个未分类页面设置为指定分类
          </p>
          <div class="form-group">
            <label>分类</label>
            <select v-model="batchForm.category" class="form-input">
              <option value="">-- 选择分类 --</option>
              <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>软件</label>
            <input v-model="batchForm.software" type="text" class="form-input" placeholder="如: 宝兰德" />
          </div>
          <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:16px">
            <button class="action-btn ghost" @click="showBatchCategory = false">取消</button>
            <button class="action-btn primary" @click="applyBatchCategory" :disabled="!batchForm.category">确定</button>
          </div>
        </div>
      </div>

      <!-- 批量上传对话框 -->
      <div v-if="showUploadDialog" class="modal-overlay" @click.self="closeUploadDialog">
        <div class="modal-dialog upload-dialog">
          <h3>上传文档</h3>
          <p class="modal-desc">选择分类和软件类型后，后台会为每个文件创建异步编译任务。</p>
          <label class="upload-picker">
            <span>选择文件</span>
            <small>支持 Markdown、TXT、PDF、Word，可多选</small>
            <input type="file" accept=".md,.txt,.pdf,.doc,.docx" @change="uploadDocument" multiple />
          </label>
          <div v-if="uploadFiles.length" class="upload-file-list">
            <div v-for="file in uploadFiles" :key="file.name + file.size" class="upload-file-item">
              <span class="upload-file-name">{{ file.name }}</span>
              <span class="upload-file-size">{{ formatFileSize(file.size) }}</span>
            </div>
          </div>
          <div v-else class="upload-empty">尚未选择文件</div>
          <div class="form-row two-col">
            <div>
              <label>分类</label>
              <select v-model="uploadForm.category" class="form-input" required @change="uploadForm.software = ''">
                <option value="">-- 选择分类 --</option>
                <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
              </select>
            </div>
            <div>
              <label>软件类型</label>
              <input v-model.trim="uploadForm.software" class="form-input" list="wiki-upload-software-options"
                     placeholder="选择或输入软件类型" :disabled="!uploadForm.category" />
              <datalist id="wiki-upload-software-options">
                <option v-for="name in uploadSoftwareOptions" :key="name" :value="name" />
              </datalist>
            </div>
          </div>
          <div class="modal-actions">
            <button class="action-btn ghost" @click="closeUploadDialog">取消</button>
            <button class="action-btn primary" @click="submitUploadDocuments"
                    :disabled="!uploadForm.category || !uploadForm.software || !uploadFiles.length || ingesting">
              创建 {{ uploadFiles.length }} 个编译任务
            </button>
          </div>
        </div>
      </div>

      <!-- 手动录入对话框 -->
      <div v-if="showTextIngest" class="modal-overlay" @click.self="showTextIngest = false">
        <div class="modal-dialog wide">
          <h3>手动录入文档</h3>
          <p class="modal-desc">粘贴文本内容，系统将由 LLM 自动编译为 Wiki 页面</p>
          <div class="form-row">
            <label>文档标题</label>
            <input v-model="textIngestForm.title" type="text" class="form-input" placeholder="如: Nginx 1.24 配置指南" />
          </div>
          <div class="form-row three-col">
            <div>
              <label>分类</label>
              <input v-model="textIngestForm.category" type="text" class="form-input" placeholder="如: 中间件" />
            </div>
            <div>
              <label>软件</label>
              <input v-model="textIngestForm.software" type="text" class="form-input" placeholder="如: Nginx" />
            </div>
            <div style="display:flex;align-items:flex-end">
              <button class="action-btn success" @click="submitTextIngest" :disabled="ingesting"
                      style="width:100%">{{ ingesting ? '编译中...' : '开始编译' }}</button>
            </div>
          </div>
          <div class="form-row">
            <label>文档内容</label>
            <textarea v-model="textIngestForm.content" class="form-textarea" rows="15"
                      placeholder="粘贴 Markdown、纯文本或其他格式的文档内容..."></textarea>
          </div>
          <div class="modal-actions">
            <button class="action-btn ghost" @click="showTextIngest = false">关闭</button>
          </div>
        </div>
      </div>

      <!-- 编译结果提示 -->
      <div v-if="ingestResult" class="import-result" :class="ingestResult.success ? 'success' : 'error'">
        <span>{{ ingestResult.message }}</span>
        <button @click="ingestResult = null" class="close-btn">&times;</button>
      </div>

      <!-- 上传编译进度弹窗 -->
      <div v-if="ingesting" class="modal-overlay">
        <div class="modal-dialog" style="width: 400px; text-align: center;">
          <h3 style="margin-bottom: 16px;">文档编译中</h3>
          <div class="progress-ring-wrap">
            <svg class="progress-ring" viewBox="0 0 120 120">
              <circle class="progress-ring-bg" cx="60" cy="60" r="52" />
              <circle class="progress-ring-fill" cx="60" cy="60" r="52"
                      :style="{ strokeDashoffset: 326.7 - (326.7 * ingestProgress / 100) }" />
            </svg>
            <span class="progress-ring-text">{{ ingestProgress }}%</span>
          </div>
          <p class="progress-step-text">{{ ingestStep }}</p>
        </div>
      </div>

    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { request } from '../api'
import MarkdownIt from 'markdown-it'
import ForceGraph from 'force-graph'

const props = defineProps({ auth: Object, notify: Function })

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

// Sidebar state
const sidebarTab = ref('pages')
const searchQuery = ref('')
const filterStatus = ref('')
const loading = ref(false)

// Data
const pages = ref([])
const selectedPage = ref(null)
const pageLinks = ref([])
const sources = ref([])
const selectedSource = ref(null)
const graphData = ref({ nodes: [], links: [] })
const selectedGraphNode = ref(null)
const graphContainer = ref(null)
let graph = null

// Edit state
const editing = ref(false)
const saving = ref(false)
const editForm = ref({ title: '', content: '', summary: '', category: '', software: '', version: '' })

// Review / resolve state
const showResolveDialog = ref(false)
const resolveNote = ref('')

// Import result
const importResult = ref(null)

// Computed: find imported version page ID from contradiction note
const conflictImportedPageId = computed(() => {
  if (!selectedPage.value?.contradictionNote) return null
  const match = selectedPage.value.contradictionNote.match(/「(.+?\[导入版本\])」/)
  if (!match) return null
  const importedTitle = match[1]
  const found = pages.value.find(p => p.title === importedTitle)
  return found?.id || null
})

// Lint state
const lintResults = ref([])
const lintRunning = ref(false)

// Ingest tasks tracking
const ingestTasks = ref([])

function isSourceCompiling(sourceId) {
  return ingestTasks.value.some(t => t.sourceId === sourceId && (t.status === 'PENDING' || t.status === 'PROCESSING'))
}

function getSourceTask(sourceId) {
  return ingestTasks.value.find(t => t.sourceId === sourceId && (t.status === 'PENDING' || t.status === 'PROCESSING'))
}

async function showSourceTask(sourceId) {
  const task = getSourceTask(sourceId)
  if (!task) return
  ingesting.value = true
  ingestProgress.value = task.progress || 0
  ingestStep.value = task.step || '编译中...'
  await pollTasks([task])
  loadSources()
}

async function refreshIngestTasks() {
  try {
    ingestTasks.value = await request('/api/wiki/ingest/tasks')
  } catch {}
}

// Ingest state
const showTextIngest = ref(false)

// Batch category
const showBatchCategory = ref(false)
const batchForm = ref({ category: '', software: '' })
const categories = ['中间件', '数据库', '主机', '网络', '安全']

const uncategorizedPages = computed(() =>
  pages.value.filter(p => !p.category || p.category === '' || p.category === '未分类')
)

function openBatchCategory() {
  batchForm.value = { category: '', software: '' }
  showBatchCategory.value = true
}

async function applyBatchCategory() {
  const ids = uncategorizedPages.value.map(p => p.id)
  if (!ids.length) {
    props.notify?.('没有未分类的页面', 'warning')
    return
  }
  try {
    await request('/api/wiki/pages-batch-category', {
      method: 'PUT',
      body: { ids, category: batchForm.value.category, software: batchForm.value.software }
    })
    showBatchCategory.value = false
    props.notify?.(`已更新 ${ids.length} 个页面的分类`, 'success')
    loadPages()
  } catch (e) {
    props.notify?.('批量分类失败: ' + e.message, 'error')
  }
}
const ingesting = ref(false)
const ingestResult = ref(null)
const ingestProgress = ref(0)       // 0-100
const ingestStep = ref('')           // current step description
const textIngestForm = ref({ title: '', content: '', category: '', software: '' })
const showUploadDialog = ref(false)
const uploadFiles = ref([])
const uploadForm = ref({ category: '', software: '' })
const softwareTypes = ref([])

const uploadSoftwareOptions = computed(() => {
  if (!uploadForm.value.category) return []
  return softwareTypes.value
    .filter(type => type.category === uploadForm.value.category)
    .map(type => type.name)
    .filter(Boolean)
})

async function loadSoftwareTypes() {
  try {
    softwareTypes.value = await request('/api/admin/software-types?activeOnly=true')
  } catch (e) {
    console.warn('Failed to load software types:', e)
  }
}

function openUploadDialog() {
  ingestResult.value = null
  showUploadDialog.value = true
}

function closeUploadDialog() {
  if (ingesting.value) return
  showUploadDialog.value = false
  uploadFiles.value = []
  uploadForm.value = { category: '', software: '' }
}

function formatFileSize(size) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

// Tree data (reactive)
const treeData = ref([])

// Build tree from flat pages
function buildTree(pageList) {
  const catMap = {}
  for (const p of pageList) {
    const catName = p.category || '未分类'
    const swName = p.software || '通用'
    if (!catMap[catName]) catMap[catName] = {}
    if (!catMap[catName][swName]) catMap[catName][swName] = []
    catMap[catName][swName].push(p)
  }

  // Preserve expand state
  const oldTree = treeData.value
  const expandState = {}
  for (const cat of oldTree) {
    expandState[cat.name] = { expanded: cat.expanded }
    for (const sw of (cat.softwares || [])) {
      expandState[cat.name + '/' + sw.name] = { expanded: sw.expanded }
    }
  }

  const result = []
  const sortedCats = Object.keys(catMap).sort((a, b) => {
    if (a === '未分类') return 1
    if (b === '未分类') return -1
    return a.localeCompare(b)
  })
  for (const catName of sortedCats) {
    const swMap = catMap[catName]
    const softwares = []
    let totalCount = 0
    const sortedSw = Object.keys(swMap).sort((a, b) => {
      if (a === '通用') return 1
      if (b === '通用') return -1
      return a.localeCompare(b)
    })
    for (const swName of sortedSw) {
      const pagesSorted = swMap[swName].sort((a, b) => (a.title || '').localeCompare(b.title || ''))
      softwares.push({
        name: swName,
        pages: pagesSorted,
        expanded: expandState[catName + '/' + swName]?.expanded ?? false
      })
      totalCount += pagesSorted.length
    }
    result.push({
      name: catName,
      softwares,
      totalCount,
      expanded: expandState[catName]?.expanded ?? (result.length === 0)
    })
  }
  treeData.value = result
}

// Status label
function statusLabel(status) {
  const map = {
    'ACTIVE': '已发布',
    'DRAFT': '草稿',
    'PENDING_REVIEW': '待审核',
    'CONTRADICTED': '有冲突',
    'REJECTED': '已拒绝',
    'ARCHIVED': '已归档',
    'STALE': '过期'
  }
  return map[status] || status
}

// Format date
function formatDate(dt) {
  if (!dt) return ''
  try {
    const d = new Date(dt)
    return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } catch { return String(dt) }
}

// Computed rendered content
function escapeHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

const renderedContent = computed(() => {
  if (!selectedPage.value?.content) return ''
  let html = md.render(selectedPage.value.content)
  html = html.replace(/\[\[([^\]]+)\]\]/g, (match, title) => {
    const safe = escapeHtml(title)
    return `<a class="wikilink" data-title="${safe}" href="javascript:void(0)">${safe}</a>`
  })
  return html
})

// Load pages
async function loadPages() {
  loading.value = true
  try {
    let url = '/api/wiki/pages'
    const params = []
    if (filterStatus.value) params.push(`status=${filterStatus.value}`)
    if (params.length) url += '?' + params.join('&')
    const list = await request(url)
    pages.value = list
    buildTree(list)
  } catch (e) {
    console.error('Failed to load pages:', e)
  } finally {
    loading.value = false
  }
}

async function searchPages() {
  if (!searchQuery.value.trim()) {
    loadPages()
    return
  }
  loading.value = true
  try {
    const list = await request(`/api/wiki/pages/search?q=${encodeURIComponent(searchQuery.value)}&limit=50`)
    pages.value = list
    buildTree(list)
  } catch (e) {
    console.error('Search failed:', e)
  } finally {
    loading.value = false
  }
}

async function loadSources() {
  try {
    sources.value = await request('/api/wiki/sources')
    refreshIngestTasks()
  } catch (e) {
    console.error('Failed to load sources:', e)
  }
}

async function selectSource(src) {
  selectedSource.value = src
  selectedPage.value = null
  editing.value = false
}

async function deleteSource() {
  if (!selectedSource.value) return
  if (!confirm(`确定删除文档「${selectedSource.value.title}」？此操作不可恢复。`)) return
  try {
    await request(`/api/wiki/sources/${selectedSource.value.id}`, { method: 'DELETE' })
    selectedSource.value = null
    props.notify?.('文档已删除', 'success')
    loadSources()
  } catch (e) {
    props.notify?.('删除失败: ' + e.message, 'error')
  }
}

async function reingestSource() {
  if (!selectedSource.value) return
  ingesting.value = true
  ingestProgress.value = 0
  ingestStep.value = '正在提交编译任务...'
  try {
    const task = await request(`/api/wiki/sources/${selectedSource.value.id}/ingest`, { method: 'POST' })
    ingestStep.value = '编译任务已创建，正在处理...'
    await pollTasks([task])
    loadSources()
  } catch (e) {
    props.notify?.('编译失败: ' + e.message, 'error')
    ingesting.value = false
  }
}

// --- Lint ---
function lintCount(severity) {
  return lintResults.value.filter(r => r.severity === severity && !r.resolved).length
}

function lintTypeLabel(type) {
  const map = { 'ORPHAN': '孤立', 'STALE': '过期', 'BROKEN_LINK': '断链', 'CONTRADICTION': '冲突', 'GAP': '空白' }
  return map[type] || type
}

async function loadLintResults() {
  try {
    lintResults.value = await request('/api/wiki/lint/results')
  } catch (e) {
    console.error('Failed to load lint results:', e)
  }
}

async function runLint() {
  lintRunning.value = true
  try {
    lintResults.value = await request('/api/wiki/lint/run', { method: 'POST' })
    props.notify?.(`Lint 完成，发现 ${lintResults.value.length} 个问题`, 'success')
  } catch (e) {
    props.notify?.('Lint 失败: ' + e.message, 'error')
  } finally {
    lintRunning.value = false
  }
}

async function resolveLint(result) {
  try {
    await request(`/api/wiki/lint/results/${result.id}/resolve`, { method: 'PUT' })
    result.resolved = true
    props.notify?.('已标记为已解决', 'success')
  } catch (e) {
    props.notify?.('操作失败: ' + e.message, 'error')
  }
}

async function selectPage(page) {
  editing.value = false
  selectedPage.value = page
  selectedSource.value = null
  try {
    pageLinks.value = await request(`/api/wiki/pages/${page.id}/links`)
  } catch {
    pageLinks.value = []
  }
}

async function viewPageById(id) {
  try {
    const page = await request(`/api/wiki/pages/${id}`)
    if (page) {
      sidebarTab.value = 'pages'
      selectedGraphNode.value = null
      selectPage(page)
    }
  } catch (e) {
    console.error('Failed to load page:', e)
  }
}

// --- Edit ---
function startEdit() {
  if (!selectedPage.value) return
  editForm.value = {
    title: selectedPage.value.title || '',
    content: selectedPage.value.content || '',
    summary: selectedPage.value.summary || '',
    category: selectedPage.value.category || '',
    software: selectedPage.value.software || '',
    version: selectedPage.value.version || ''
  }
  editing.value = true
}

function cancelEdit() {
  editing.value = false
}

async function saveEdit() {
  if (!selectedPage.value) return
  saving.value = true
  try {
    const updated = await request(`/api/wiki/pages/${selectedPage.value.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(editForm.value)
    })
    selectedPage.value = updated
    editing.value = false
    props.notify?.('页面已保存', 'success')
    loadPages()
  } catch (e) {
    props.notify?.('保存失败: ' + e.message, 'error')
  } finally {
    saving.value = false
  }
}

// --- Review workflow ---
async function submitForReview() {
  if (!selectedPage.value) return
  try {
    const updated = await request(`/api/wiki/pages/${selectedPage.value.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: 'PENDING_REVIEW' })
    })
    selectedPage.value = updated
    props.notify?.('已提交审核', 'success')
    loadPages()
  } catch (e) {
    props.notify?.('提交失败: ' + e.message, 'error')
  }
}

async function approvePage() {
  if (!selectedPage.value) return
  try {
    const updated = await request(`/api/wiki/pages/${selectedPage.value.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: 'ACTIVE' })
    })
    selectedPage.value = updated
    props.notify?.('审核通过，页面已发布', 'success')
    loadPages()
  } catch (e) {
    props.notify?.('操作失败: ' + e.message, 'error')
  }
}

async function rejectPage() {
  if (!selectedPage.value) return
  try {
    const updated = await request(`/api/wiki/pages/${selectedPage.value.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: 'REJECTED' })
    })
    selectedPage.value = updated
    props.notify?.('审核已拒绝', 'success')
    loadPages()
  } catch (e) {
    props.notify?.('操作失败: ' + e.message, 'error')
  }
}

// --- Contradiction resolution ---
async function resolveKeepCurrent() {
  if (!selectedPage.value) return
  try {
    // 当前页面 → ACTIVE
    const updated = await request(`/api/wiki/pages/${selectedPage.value.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: 'ACTIVE', contradictionNote: '[已解决] 保留当前版本。' + (resolveNote.value ? ' ' + resolveNote.value : '') })
    })
    // 导入版本 → ARCHIVED
    if (conflictImportedPageId.value) {
      await request(`/api/wiki/pages/${conflictImportedPageId.value}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: 'ARCHIVED', contradictionNote: '已裁决：保留原版本，此导入版本已归档。' })
      })
    }
    selectedPage.value = updated
    showResolveDialog.value = false
    resolveNote.value = ''
    props.notify?.('已保留当前版本，导入版本已归档', 'success')
    loadPages()
  } catch (e) {
    props.notify?.('操作失败: ' + e.message, 'error')
  }
}

async function resolveUseImported() {
  if (!selectedPage.value || !conflictImportedPageId.value) {
    props.notify?.('未找到导入版本页面', 'error')
    return
  }
  try {
    // 当前页面 → ARCHIVED
    await request(`/api/wiki/pages/${selectedPage.value.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: 'ARCHIVED', contradictionNote: '已裁决：使用导入版本替代，此版本已归档。' + (resolveNote.value ? ' ' + resolveNote.value : '') })
    })
    // 导入版本 → ACTIVE，去掉 [导入版本] 后缀
    const importedPage = await request(`/api/wiki/pages/${conflictImportedPageId.value}`)
    const cleanTitle = importedPage.title.replace(/ \[导入版本\]$/, '')
    const activated = await request(`/api/wiki/pages/${conflictImportedPageId.value}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: 'ACTIVE', title: cleanTitle, contradictionNote: null })
    })
    selectedPage.value = activated
    showResolveDialog.value = false
    resolveNote.value = ''
    props.notify?.('已使用导入版本，原版本已归档', 'success')
    loadPages()
  } catch (e) {
    props.notify?.('操作失败: ' + e.message, 'error')
  }
}

async function resolveManualMerge() {
  if (!selectedPage.value) return
  try {
    const updated = await request(`/api/wiki/pages/${selectedPage.value.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: 'DRAFT', contradictionNote: '[手动合并中] 请编辑当前版本内容后手动提交审核。' + (resolveNote.value ? ' ' + resolveNote.value : '') })
    })
    selectedPage.value = updated
    showResolveDialog.value = false
    resolveNote.value = ''
    props.notify?.('已改为 DRAFT 状态，请手动编辑合并后提交审核', 'success')
    loadPages()
  } catch (e) {
    props.notify?.('操作失败: ' + e.message, 'error')
  }
}

async function archivePage() {
  if (!selectedPage.value) return
  try {
    const updated = await request(`/api/wiki/pages/${selectedPage.value.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: 'ARCHIVED' })
    })
    selectedPage.value = updated
    props.notify?.('页面已归档', 'success')
    loadPages()
  } catch (e) {
    props.notify?.('操作失败: ' + e.message, 'error')
  }
}

// --- Delete ---
async function deletePage() {
  if (!selectedPage.value) return
  if (!confirm(`确定删除页面「${selectedPage.value.title}」？此操作不可恢复。`)) return
  try {
    await request(`/api/wiki/pages/${selectedPage.value.id}`, { method: 'DELETE' })
    selectedPage.value = null
    pageLinks.value = []
    props.notify?.('页面已删除', 'success')
    loadPages()
  } catch (e) {
    props.notify?.('删除失败: ' + e.message, 'error')
  }
}

// --- Import / Export ---
async function exportWiki() {
  try {
    const resp = await fetch('/api/wiki/export', {
      headers: { 'Authorization': `Bearer ${props.auth?.token || ''}` }
    })
    if (!resp.ok) throw new Error(await readWikiError(resp, '导出失败'))
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `wiki-export-${new Date().toISOString().slice(0, 10)}.zip`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    props.notify?.('导出成功', 'success')
  } catch (e) {
    props.notify?.('导出失败: ' + e.message, 'error')
  }
}

async function importWiki(event) {
  const file = event.target.files?.[0]
  if (!file) return
  event.target.value = ''
  try {
    const formData = new FormData()
    formData.append('file', file)
    const resp = await fetch('/api/wiki/import', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${props.auth?.token || ''}` },
      body: formData
    })
    if (!resp.ok) throw new Error(await readWikiError(resp, '导入失败'))
    const result = await resp.json()
    const conflictList = (result.conflictDetails || []).map(c => `${c.title}(${c.pageType})`).join('、')
    importResult.value = {
      success: true,
      message: `导入完成: ${result.pagesCreated || 0} 个新页面, ${result.pagesUpdated || 0} 个更新, ${result.conflicts || 0} 个冲突, ${result.linksCreated || 0} 个链接`,
      conflicts: result.conflictDetails || []
    }
    loadPages()
  } catch (e) {
    importResult.value = { success: false, message: '导入失败: ' + e.message }
  }
}

async function readWikiError(resp, fallback) {
  try {
    const payload = await resp.json()
    const message = payload.message || payload.error
    if (message) return message
  } catch (e) {
    console.warn('读取 Wiki 错误响应失败', e)
  }
  return `${fallback}（HTTP ${resp.status}）`
}

// --- Graph ---

// --- Document upload ingest ---
async function uploadDocument(event) {
  const files = Array.from(event.target.files || [])
  if (!files.length) return
  event.target.value = ''
  ingestResult.value = null

  const MAX_SIZE = 10 * 1024 * 1024
  uploadFiles.value = files.filter(f => {
    if (f.size > MAX_SIZE) {
      ingestResult.value = { success: false, message: `「${f.name}」过大（${(f.size / 1024 / 1024).toFixed(1)}MB），最大 10MB` }
      return false
    }
    return true
  })
  if (!uploadFiles.value.length) return
  showUploadDialog.value = true
}

async function submitUploadDocuments() {
  if (!uploadFiles.value.length) return
  if (!uploadForm.value.category || !uploadForm.value.software) {
    ingestResult.value = { success: false, message: '请选择分类和软件类型' }
    return
  }

  // 批量上传：每个文件创建一个任务
  const tasks = []
  const files = [...uploadFiles.value]
  showUploadDialog.value = false

  for (const file of files) {
    ingesting.value = true
    ingestProgress.value = 0
    ingestStep.value = `正在上传「${file.name}」...`

    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('category', uploadForm.value.category)
      formData.append('software', uploadForm.value.software)
      const task = await request('/api/wiki/ingest/upload', {
        method: 'POST',
        body: formData
      })
      tasks.push(task)
    } catch (e) {
      ingestResult.value = { success: false, message: `「${file.name}」上传失败: ${e.message}` }
      ingesting.value = false
      return
    }
  }

  // 轮询所有任务进度
  ingestStep.value = `正在编译 ${tasks.length} 个文档...`
  uploadFiles.value = []
  uploadForm.value = { category: '', software: '' }
  await pollTasks(tasks)
}

async function pollTasks(tasks) {
  const taskIds = tasks.map(t => t.id)
  const totalFiles = taskIds.length
  let completed = 0

  const poll = async () => {
    try {
      const allTasks = await request('/api/wiki/ingest/tasks')
      const myTasks = allTasks.filter(t => taskIds.includes(t.id))

      completed = myTasks.filter(t => isTerminalTaskStatus(t.status)).length
      const processing = myTasks.find(t => t.status === 'PROCESSING')

      if (processing) {
        ingestProgress.value = processing.progress || 0
        ingestStep.value = processing.step || '编译中...'
      } else {
        ingestProgress.value = Math.round((completed / totalFiles) * 100)
        ingestStep.value = `已完成 ${completed}/${totalFiles} 个文档`
      }

      if (completed < totalFiles) {
        setTimeout(poll, 1500)
      } else {
        // 全部完成
        const succeeded = myTasks.filter(t => t.status === 'COMPLETED' || t.status === 'PARTIAL')
        const failed = myTasks.filter(t => t.status === 'FAILED')
        const totalCreated = succeeded.reduce((s, t) => s + (t.pagesCreated || 0), 0)
        const totalUpdated = succeeded.reduce((s, t) => s + (t.pagesUpdated || 0), 0)

        ingestProgress.value = 100
        ingestStep.value = '全部完成'

        let msg = `编译完成: ${totalCreated} 个新页面, ${totalUpdated} 个更新`
        const partial = myTasks.filter(t => t.status === 'PARTIAL')
        if (partial.length) msg += `，${partial.length} 个部分成功`
        if (failed.length) msg += `，${failed.length} 个失败`
        ingestResult.value = { success: failed.length === 0, message: msg }

        ingesting.value = false
        loadPages()
        loadSources()
      }
    } catch {
      setTimeout(poll, 3000)
    }
  }

  await poll()
}

function isTerminalTaskStatus(status) {
  return status === 'COMPLETED' || status === 'PARTIAL' || status === 'FAILED'
}

async function submitTextIngest() {
  if (!textIngestForm.value.content?.trim()) {
    ingestResult.value = { success: false, message: '请输入文档内容' }
    return
  }
  ingesting.value = true
  ingestResult.value = null
  ingestProgress.value = 10
  ingestStep.value = '正在提交文本...'

  try {
    const resp = await fetch('/api/wiki/ingest/text', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${props.auth?.token || ''}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(textIngestForm.value)
    })
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const result = await resp.json()
    const created = result.pagesCreated || 0
    const updated = result.pagesUpdated || 0
    ingestProgress.value = 100
    ingestStep.value = '编译完成'
    ingestResult.value = {
      success: true,
      message: `编译完成: ${created} 个新页面, ${updated} 个更新`
    }
    showTextIngest.value = false
    textIngestForm.value = { title: '', content: '', category: '', software: '' }
    loadPages()
  } catch (e) {
    ingestResult.value = { success: false, message: '编译失败: ' + e.message }
  } finally {
    ingesting.value = false
  }
}
async function loadGraph() {
  try {
    graphData.value = await request('/api/wiki/graph')
    await nextTick()
    await new Promise(r => setTimeout(r, 100))
    initGraph()
  } catch (e) {
    console.error('Failed to load graph:', e)
  }
}

const communityColors = [
  '#4fc3f7', '#81c784', '#ffb74d', '#ff8a65', '#ba68c8',
  '#4dd0e1', '#fff176', '#f06292', '#aed581', '#90caf9',
  '#ce93d8', '#ffcc80', '#80deea', '#ef9a9a', '#a5d6a7'
]

function communityColor(cid) {
  return communityColors[cid % communityColors.length]
}

function initGraph() {
  if (!graphContainer.value || !graphData.value.nodes?.length) return
  if (graph) {
    graph._destructor?.()
    graph = null
  }
  const container = graphContainer.value
  try {
    graph = ForceGraph()
      .backgroundColor('#0a0a1a')
      .graphData(graphData.value)
      .nodeLabel(node => `${node.name}\n类型: ${node.pageType}\n社区: ${node.communityName || node.community}`)
      .nodeColor(node => communityColor(node.community ?? 0))
      .nodeVal(node => {
        const links = graphData.value.links.filter(l => {
          const s = typeof l.source === 'object' ? l.source.id : l.source
          const t = typeof l.target === 'object' ? l.target.id : l.target
          return s === node.id || t === node.id
        })
        return Math.max(links.length * 2, 5)
      })
      .linkColor(link => {
        const w = link.weight || 1
        if (w >= 5) return 'rgba(255,183,77,0.8)'
        if (w >= 3) return 'rgba(144,202,249,0.6)'
        return 'rgba(255,255,255,0.25)'
      })
      .linkWidth(link => {
        const w = link.weight || 1
        return Math.min(Math.max(w * 0.5, 0.5), 5)
      })
      .linkDirectionalArrowLength(4)
      .linkDirectionalArrowRelPos(1)
      .onNodeClick(node => {
        selectedGraphNode.value = node
        graph.centerAt(node.x, node.y, 1000)
        graph.zoom(3, 1000)
      })
      .onNodeHover(node => { container.style.cursor = node ? 'pointer' : null })

    graph(container)
    graph.d3Force('charge').strength(-300)
    graph.d3Force('link').distance(link => {
      const w = link.weight || 1
      return 80 + (6 - Math.min(w, 5)) * 20
    })
    setTimeout(() => graph.zoomToFit(400, 50), 500)
  } catch (e) {
    console.warn('Graph init error:', e)
  }
}

// Handle wikilink clicks
function handleWikiLinkClick(e) {
  const link = e.target.closest('.wikilink')
  if (link) {
    const title = link.dataset.title
    if (title) {
      searchQuery.value = title
      searchPages().then(() => {
        if (pages.value.length > 0) selectPage(pages.value[0])
      })
    }
  }
}

onMounted(() => {
  loadPages()
  loadSoftwareTypes()
  document.addEventListener('click', handleWikiLinkClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleWikiLinkClick)
  if (graph) graph._destructor?.()
})
</script>

<style scoped>
.wiki-panel {
  display: flex;
  height: 100%;
  background: #0d1117;
  color: #c9d1d9;
}

/* Sidebar */
.wiki-sidebar {
  width: 300px;
  min-width: 300px;
  border-right: 1px solid #21262d;
  display: flex;
  flex-direction: column;
  background: #161b22;
}

.sidebar-tabs {
  display: flex;
  border-bottom: 1px solid #21262d;
}

.sidebar-tabs button {
  flex: 1;
  padding: 10px;
  background: none;
  border: none;
  color: #8b949e;
  cursor: pointer;
  font-size: 13px;
  border-bottom: 2px solid transparent;
}

.sidebar-tabs button.active {
  color: #58a6ff;
  border-bottom-color: #58a6ff;
}

.sidebar-content {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.search-box { padding: 8px; }

.search-box input {
  width: 100%;
  padding: 6px 10px;
  background: #0d1117;
  border: 1px solid #30363d;
  border-radius: 6px;
  color: #c9d1d9;
  font-size: 13px;
  box-sizing: border-box;
}

.filter-row {
  display: flex;
  gap: 4px;
  padding: 0 8px 8px;
}

.filter-row select {
  flex: 1;
  padding: 4px 6px;
  background: #0d1117;
  border: 1px solid #30363d;
  border-radius: 4px;
  color: #c9d1d9;
  font-size: 12px;
}

/* Tree */
.tree-container {
  flex: 1;
  overflow-y: auto;
  padding: 0 4px;
}

.tree-category, .tree-software { margin: 2px 0; }

.tree-label {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 8px;
  cursor: pointer;
  font-size: 13px;
  border-radius: 4px;
  user-select: none;
}

.tree-label:hover { background: #1c2128; }

.category-label { font-weight: 600; color: #e6edf3; }
.software-label { padding-left: 16px; color: #c9d1d9; }

.tree-arrow {
  width: 14px;
  font-size: 10px;
  color: #6e7681;
  flex-shrink: 0;
}

.tree-icon { font-size: 14px; flex-shrink: 0; }

.tree-count {
  margin-left: auto;
  font-size: 11px;
  color: #6e7681;
  background: #21262d;
  padding: 1px 6px;
  border-radius: 8px;
}

.tree-children { padding-left: 4px; }

.tree-page {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px 4px 32px;
  cursor: pointer;
  font-size: 12px;
  border-radius: 4px;
}

.tree-page:hover { background: #1c2128; }
.tree-page.active { background: #1f6feb22; border-left: 2px solid #58a6ff; }

.page-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.page-status-dot.active { background: #238636; }
.page-status-dot.draft { background: #9e6a03; }
.page-status-dot.pending_review { background: #58a6ff; }
.page-status-dot.contradicted { background: #da3633; }
.page-status-dot.rejected { background: #da3633; opacity: 0.6; }
.page-status-dot.archived { background: #6e7681; }
.page-status-dot.stale { background: #6e7681; }

.page-title-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-type-tag {
  font-size: 10px;
  color: #8b949e;
  background: #21262d;
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

/* Content area */
.wiki-content {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

/* Action bar */
.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 20px;
  border-bottom: 1px solid #21262d;
  background: #161b22;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 6px;
  position: sticky;
  top: 0;
  z-index: 10;
}

.action-left, .action-right {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
}

.action-btn {
  padding: 5px 12px;
  font-size: 12px;
  background: #21262d;
  border: 1px solid #30363d;
  border-radius: 6px;
  color: #c9d1d9;
  cursor: pointer;
  white-space: nowrap;
}

.action-btn:hover { background: #30363d; }
.action-btn.primary { background: #1f6feb; border-color: #1f6feb; color: #fff; }
.action-btn.primary:hover { background: #388bfd; }
.action-btn.success { background: #238636; border-color: #238636; color: #fff; }
.action-btn.success:hover { background: #2ea043; }
.action-btn.danger { background: #da3633; border-color: #da3633; color: #fff; }
.action-btn.danger:hover { background: #f85149; }
.action-btn.warning { background: #9e6a03; border-color: #9e6a03; color: #fff; }
.action-btn.warning:hover { background: #bb8009; }
.action-btn.ghost { background: transparent; border-color: #30363d; }
.action-btn.ghost:hover { background: #21262d; }
.action-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.action-divider {
  width: 1px;
  height: 20px;
  background: #30363d;
  margin: 0 4px;
}

/* Import result */
.import-result {
  padding: 8px 16px;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #21262d;
  position: sticky;
  top: 48px;
  z-index: 9;
}

.import-result.success { background: #23863622; color: #3fb950; }
.import-result.error { background: #da363322; color: #f85149; }

.close-btn {
  background: none;
  border: none;
  color: inherit;
  font-size: 16px;
  cursor: pointer;
  padding: 0 4px;
}

/* Page detail */
.page-detail { padding: 20px 32px; }

.page-header h2 {
  margin: 0 0 8px;
  font-size: 22px;
  color: #e6edf3;
}

.page-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.meta-tag {
  font-size: 12px;
  background: #21262d;
  padding: 2px 8px;
  border-radius: 4px;
  color: #8b949e;
}

.status-badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.status-badge.active { background: #238636; color: #fff; }
.status-badge.draft { background: #9e6a03; color: #fff; }
.status-badge.pending_review { background: #1f6feb; color: #fff; }
.status-badge.contradicted { background: #da3633; color: #fff; }
.status-badge.rejected { background: #da3633; color: #fff; opacity: 0.7; }
.status-badge.archived { background: #6e7681; color: #fff; }
.status-badge.stale { background: #6e7681; color: #fff; }

.page-summary {
  color: #8b949e;
  font-size: 14px;
  margin: 8px 0 16px;
  padding: 8px 12px;
  background: #161b22;
  border-radius: 6px;
  border-left: 3px solid #30363d;
}

.contradiction-banner {
  background: #da363322;
  border: 1px solid #da3633;
  border-radius: 6px;
  padding: 10px 14px;
  margin: 8px 0 16px;
  font-size: 13px;
  color: #f85149;
}

.page-audit {
  font-size: 12px;
  color: #6e7681;
  margin: 8px 0 16px;
  padding: 6px 0;
  border-bottom: 1px solid #21262d;
}

/* Related pages */
.related-section {
  margin: 16px 0;
  padding: 12px;
  background: #161b22;
  border-radius: 8px;
}

.related-section h3 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #8b949e;
}

.related-list {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.related-chip {
  font-size: 12px;
  padding: 4px 10px;
  background: #21262d;
  border-radius: 16px;
  cursor: pointer;
  transition: background 0.2s;
}

.related-chip:hover { background: #30363d; }
.related-chip.incoming { border-left: 2px solid #58a6ff; }
.related-chip.outgoing { border-left: 2px solid #8b949e; }
.related-chip.contradicts { border-left: 2px solid #da3633; background: #da363322; }

.related-chip small {
  color: #6e7681;
  margin-left: 4px;
}

/* Page body (markdown) */
.page-body {
  line-height: 1.7;
  font-size: 14px;
}

.page-body :deep(h1) { font-size: 20px; margin: 24px 0 12px; color: #e6edf3; border-bottom: 1px solid #21262d; padding-bottom: 8px; }
.page-body :deep(h2) { font-size: 17px; margin: 20px 0 10px; color: #e6edf3; }
.page-body :deep(h3) { font-size: 15px; margin: 16px 0 8px; color: #e6edf3; }
.page-body :deep(p) { margin: 8px 0; }
.page-body :deep(code) { background: #161b22; padding: 2px 6px; border-radius: 4px; font-size: 13px; }
.page-body :deep(pre) { background: #161b22; padding: 12px; border-radius: 6px; overflow-x: auto; }
.page-body :deep(pre code) { background: none; padding: 0; }
.page-body :deep(ul), .page-body :deep(ol) { padding-left: 20px; }
.page-body :deep(li) { margin: 4px 0; }
.page-body :deep(blockquote) { border-left: 3px solid #30363d; padding-left: 12px; color: #8b949e; margin: 12px 0; }
.page-body :deep(table) { border-collapse: collapse; width: 100%; margin: 12px 0; }
.page-body :deep(th), .page-body :deep(td) { border: 1px solid #21262d; padding: 6px 10px; text-align: left; font-size: 13px; }
.page-body :deep(th) { background: #161b22; }
.page-body :deep(.wikilink) { color: #58a6ff; text-decoration: none; cursor: pointer; border-bottom: 1px dashed #58a6ff; }
.page-body :deep(.wikilink:hover) { color: #79c0ff; }

/* Welcome page */
.wiki-welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #8b949e;
  text-align: center;
  padding: 40px;
}

.welcome-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.wiki-welcome h2 {
  color: #e6edf3;
  margin-bottom: 8px;
  font-size: 20px;
}

.wiki-welcome p {
  font-size: 14px;
  max-width: 400px;
}

/* Edit mode */
.edit-mode { padding: 20px 32px; }

.edit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.edit-header h3 { margin: 0; color: #e6edf3; }

.edit-actions { display: flex; gap: 8px; }

.edit-form { display: flex; flex-direction: column; gap: 12px; }

.form-row { display: flex; flex-direction: column; gap: 4px; }
.form-row.three-col { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; }
.form-row.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }

.form-row label {
  font-size: 12px;
  color: #8b949e;
  font-weight: 500;
}

.form-input, .form-textarea {
  padding: 8px 10px;
  background: #0d1117;
  border: 1px solid #30363d;
  border-radius: 6px;
  color: #c9d1d9;
  font-size: 13px;
  font-family: inherit;
}

.form-input:focus, .form-textarea:focus {
  outline: none;
  border-color: #58a6ff;
}

.form-textarea {
  resize: vertical;
  min-height: 200px;
  font-family: 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.5;
}

/* Modal */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-dialog {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 12px;
  padding: 24px;
  width: 90%;
  max-width: 500px;
}

.modal-dialog.wide { max-width: 700px; }
.modal-dialog.upload-dialog {
  --upload-border: #30363d;
  --upload-border-hover: #58a6ff;
  --upload-bg: #0d1117;
  --upload-bg-hover: #1c2128;
  --upload-text: #c9d1d9;
  --upload-text-muted: #8b949e;
  --upload-empty-text: #6e7681;
  max-width: 560px;
}

.modal-dialog h3 { margin: 0 0 8px; color: #e6edf3; }

.upload-picker {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  justify-content: center;
  min-height: 96px;
  border: 1px dashed var(--upload-border);
  border-radius: 8px;
  background: var(--upload-bg);
  color: var(--upload-text);
  cursor: pointer;
  margin: 12px 0;
}

.upload-picker:hover {
  border-color: var(--upload-border-hover);
  background: var(--upload-bg-hover);
}

.upload-picker span {
  font-size: 14px;
  font-weight: 600;
}

.upload-picker small {
  font-size: 12px;
  color: var(--upload-text-muted);
}

.upload-picker input { display: none; }

.upload-empty {
  padding: 10px;
  margin-bottom: 12px;
  color: var(--upload-empty-text);
  font-size: 12px;
  text-align: center;
}

.upload-file-list {
  max-height: 180px;
  overflow-y: auto;
  border: 1px solid #30363d;
  border-radius: 8px;
  margin: 12px 0;
}

.upload-file-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  border-bottom: 1px solid #21262d;
  font-size: 12px;
}

.upload-file-item:last-child { border-bottom: none; }

.upload-file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #c9d1d9;
}

.upload-file-size {
  flex-shrink: 0;
  color: #8b949e;
}

.conflict-note-text {
  background: transparent;
  border: none;
  padding: 0;
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: #f0c0c0;
  white-space: pre-wrap;
  font-family: inherit;
}

.conflict-compare-hint {
  margin: 8px 0 12px;
  font-size: 13px;
}

.conflict-compare-hint a {
  color: #58a6ff;
  text-decoration: underline;
}

.conflict-links {
  margin-top: 6px;
  font-size: 12px;
}

.conflict-link-item {
  display: block;
  margin: 2px 0;
}

.conflict-link-item a {
  color: #58a6ff;
  text-decoration: underline;
  cursor: pointer;
}
.modal-desc { font-size: 13px; color: #8b949e; margin: 0 0 12px; }

.conflict-note {
  background: #da363322;
  border: 1px solid #30363d;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #f0c0c0;
}

.modal-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  justify-content: flex-end;
}

/* Graph view */
.graph-view {
  position: relative;
  flex: 1;
  min-height: 0;
}

.graph-container {
  width: 100%;
  height: 100%;
}

.graph-node-detail {
  position: absolute;
  top: 16px;
  right: 16px;
  background: rgba(22, 27, 34, 0.96);
  border: 1px solid #30363d;
  border-radius: 8px;
  padding: 16px;
  min-width: 200px;
  z-index: 10;
}

.graph-node-detail h4 { margin: 0 0 8px; font-size: 15px; color: #e6edf3; }
.graph-node-detail p { margin: 4px 0; font-size: 13px; color: #8b949e; }

.btn-small {
  padding: 4px 10px;
  font-size: 12px;
  background: #21262d;
  border: 1px solid #30363d;
  border-radius: 4px;
  color: #c9d1d9;
  cursor: pointer;
  margin-right: 6px;
  margin-top: 8px;
}

.btn-small:hover { background: #30363d; }
.btn-small.ghost { background: transparent; }

.graph-stats {
  padding: 12px;
  font-size: 13px;
  color: #8b949e;
}

.graph-stats div { margin: 4px 0; }

.community-legend {
  padding: 8px 12px;
  border-top: 1px solid #21262d;
}

.legend-title {
  font-size: 12px;
  font-weight: 600;
  color: #8b949e;
  margin-bottom: 6px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
  font-size: 11px;
  color: #c9d1d9;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.community-tag {
  display: inline-block;
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 10px;
  color: #000;
  font-weight: 500;
}

/* Empty state */
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: #6e7681;
  font-size: 14px;
}

.loading, .empty {
  padding: 20px;
  text-align: center;
  color: #6e7681;
  font-size: 13px;
}

/* Source list */
.source-list {
  flex: 1;
  overflow-y: auto;
}

.source-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  cursor: pointer;
  border-bottom: 1px solid #21262d;
  font-size: 12px;
}

.source-item:hover { background: #1c2128; }
.source-item.active { background: #1f6feb22; border-left: 2px solid #58a6ff; }

.source-type-tag {
  font-size: 10px;
  color: #8b949e;
  background: #21262d;
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

.source-title-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.source-status-dot.ingested { background: #238636; }
.source-status-dot.pending { background: #9e6a03; }
.source-status-dot.compiling { background: #58a6ff; animation: pulse 1.5s infinite; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* Source detail */
.source-detail { padding: 20px 32px; }

.source-content {
  margin-top: 16px;
}

.source-content h3 {
  font-size: 14px;
  color: #8b949e;
  margin: 0 0 8px;
}

.source-raw {
  background: #161b22;
  border: 1px solid #21262d;
  border-radius: 6px;
  padding: 16px;
  font-size: 13px;
  line-height: 1.6;
  color: #c9d1d9;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 600px;
  overflow-y: auto;
  font-family: 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', monospace;
}

/* Lint panel */
.lint-summary {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid #21262d;
}

.lint-stat {
  flex: 1;
  text-align: center;
  padding: 6px;
  border-radius: 6px;
  font-size: 11px;
  color: #8b949e;
}

.lint-stat.high { background: #da363322; }
.lint-stat.medium { background: #9e6a0322; }
.lint-stat.low { background: #58a6ff22; }

.lint-count {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #e6edf3;
}

.lint-stat.high .lint-count { color: #f85149; }
.lint-stat.medium .lint-count { color: #d29922; }
.lint-stat.low .lint-count { color: #58a6ff; }

.lint-actions {
  padding: 8px 12px;
  border-bottom: 1px solid #21262d;
}

.lint-list {
  flex: 1;
  overflow-y: auto;
}

.lint-item {
  padding: 8px 12px;
  border-bottom: 1px solid #21262d;
  font-size: 12px;
}

.lint-item:hover { background: #1c2128; }

.lint-item-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.lint-severity {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 3px;
}

.lint-severity.high { background: #da3633; color: #fff; }
.lint-severity.medium { background: #9e6a03; color: #fff; }
.lint-severity.low { background: #58a6ff; color: #fff; }

.lint-type {
  font-size: 11px;
  color: #8b949e;
}

.lint-desc {
  color: #c9d1d9;
  line-height: 1.4;
  margin-bottom: 4px;
}

.lint-item-actions {
  display: flex;
  gap: 4px;
}

.btn-tiny {
  padding: 2px 8px;
  font-size: 11px;
  background: #21262d;
  border: 1px solid #30363d;
  border-radius: 4px;
  color: #8b949e;
  cursor: pointer;
}

.btn-tiny:hover { background: #30363d; color: #c9d1d9; }

/* Ingest progress modal */
.progress-ring-wrap {
  position: relative;
  width: 120px;
  height: 120px;
  margin: 0 auto 16px;
}

.progress-ring {
  width: 120px;
  height: 120px;
  transform: rotate(-90deg);
}

.progress-ring-bg {
  fill: none;
  stroke: #21262d;
  stroke-width: 8;
}

.progress-ring-fill {
  fill: none;
  stroke: #58a6ff;
  stroke-width: 8;
  stroke-linecap: round;
  stroke-dasharray: 326.7;
  transition: stroke-dashoffset 0.4s ease;
}

.progress-ring-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 24px;
  font-weight: 700;
  color: #e6edf3;
}

.progress-step-text {
  font-size: 13px;
  color: #8b949e;
  margin: 0;
}
</style>
