<template>
  <div class="document-editor-page">
    <div class="editor-toolbar">
      <div class="doc-meta-line">
        <strong class="meta-title-text">{{ documentForm.title || '未命名文档' }}</strong>
        <span class="meta-sep">/</span>
        <span>{{ documentForm.documentType === 'STANDARD' ? '标准' : documentForm.documentType === 'ARTICLE' ? '文章' : '手册' }}</span>
        <span class="meta-sep">/</span>
        <span>{{ documentForm.category || '未分类' }}</span>
        <span class="meta-sep">/</span>
        <span>{{ documentForm.software || '未关联软件' }}</span>
        <span class="meta-sep">/</span>
        <span>{{ metaStandardLabel }}</span>
        <span class="meta-sep">/</span>
        <span :class="['status', statusClass(documentForm.status)]">{{ statusLabel(documentForm.status) }}</span>
        <span class="meta-sep">/</span>
        <span>V{{ documentForm.version || '-' }}</span>
        <button v-if="documentForm.canEdit" class="meta-edit-btn" @click="openMetaDialog()" title="编辑文档信息">编辑信息</button>
      </div>
      <div class="toolbar-actions">
        <button class="ghost" @click="$emit('cancel')">返回列表</button>
        <button class="ghost" @click="showHelp = true">语法</button>
        <button v-if="documentForm.canEdit" :disabled="saving" @click="handleSave">{{ saving ? '保存中...' : '保存' }}</button>
        <span v-else class="readonly-hint">当前状态不可编辑</span>
      </div>
    </div>

    <div class="editor-body">
      <div class="editor-pane">
        <div class="pane-label">Markdown</div>
        <textarea class="editor-textarea" v-model="documentForm.content" :disabled="!documentForm.canEdit" :placeholder="documentForm.canEdit ? '使用 Markdown 编写文档内容，支持 {{参数名}} 占位符，可直接粘贴图片' : '当前状态不可编辑'" @input="onContentChange" @keydown="onEditorKeydown" @paste="onEditorPaste"></textarea>
      </div>
      <div class="preview-pane">
        <div class="pane-label">预览</div>
        <div class="preview-content markdown-preview" v-html="previewHtml"></div>
      </div>
    </div>

    <div v-if="showMetaDialog" class="meta-dialog-backdrop" @click.self="closeMetaDialog">
      <form class="meta-dialog" @submit.prevent="applyMeta">
        <h3>编辑文档信息</h3>
        <div class="meta-dialog-grid">
          <label>标题<input v-model.trim="metaForm.title" required maxlength="160" placeholder="文档标题" /></label>
          <label>类型
            <select v-model="metaForm.documentType" disabled>
              <option value="MANUAL">手册</option>
              <option value="ARTICLE">文章</option>
            </select>
          </label>
          <label>分类
            <select v-model="metaForm.category" required @change="onMetaCategoryChange">
              <option value="">请选择</option>
              <option v-for="cat in softwareTypeCategories" :key="cat" :value="cat">{{ cat }}</option>
            </select>
          </label>
          <label>软件
            <select v-model="metaForm.software" :disabled="!metaForm.category" required @change="onMetaSoftwareChange">
              <option value="">请选择</option>
              <option v-for="sw in metaSoftwareOptions" :key="sw" :value="sw">{{ sw }}</option>
            </select>
          </label>
          <label class="wide">关联标准
            <select v-model="metaForm.relatedStandardDocumentId" :disabled="!metaForm.category || !metaForm.software" required>
              <option value="">请选择</option>
              <option v-for="std in metaStandardOptions" :key="std.id" :value="std.id">
                {{ [std.category, std.software, std.softwareVersion].filter(Boolean).join(' / ') }} · V{{ std.version || '-' }}
              </option>
            </select>
          </label>
          <label class="wide">摘要<input v-model.trim="metaForm.summary" maxlength="500" placeholder="文档摘要（可选）" /></label>
        </div>
        <div class="meta-dialog-actions">
          <button type="button" class="ghost" @click="closeMetaDialog">取消</button>
          <button type="submit">确定</button>
        </div>
      </form>
    </div>
    <MarkdownHelp :show="showHelp" @close="showHelp = false" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { request } from '../api'
import MarkdownHelp from './MarkdownHelp.vue'
import { handleEditorKeydown, handleEditorPaste } from '../editor-utils'

const onEditorKeydown = handleEditorKeydown
const onEditorPaste = (e) => handleEditorPaste(e, (msg) => props.notify(msg, 'error'))

const showHelp = ref(false)

const props = defineProps({
  auth: { type: Object, required: true },
  softwareTypeCategories: { type: Array, required: true },
  softwareTypes: { type: Array, required: true },
  standardDocumentOptions: { type: Array, required: true },
  markdown: { type: Object, required: true },
  documentId: { type: [String, Number], default: null },
  notify: { type: Function, default: (msg, type) => type === 'error' ? alert(msg) : null }
})

const emit = defineEmits(['saved', 'cancel'])

const documentForm = reactive(defaultDocumentForm())
const saving = ref(false)
const previewHtml = ref('')
const standardParameters = ref([])
const showMetaDialog = ref(false)
const metaForm = reactive(defaultDocumentForm())
let previewTimer = null

const activeSoftwareTypes = computed(() => props.softwareTypes.filter(t => t.active))

const metaSoftwareOptions = computed(() => {
  if (!metaForm.category) return []
  const source = metaForm.id ? props.softwareTypes : activeSoftwareTypes.value
  return [...new Set(source.filter(t => t.category === metaForm.category).map(t => t.name))]
})

const metaStandardOptions = computed(() =>
  (props.standardDocumentOptions || []).filter(doc =>
    doc.category === metaForm.category && doc.software === metaForm.software
  )
)

const metaStandardLabel = computed(() => {
  if (!documentForm.relatedStandardDocumentId) return '未关联标准'
  const std = findStandardDocument(documentForm.relatedStandardDocumentId)
  if (!std) return '未关联标准'
  return [std.category, std.software, std.softwareVersion].filter(Boolean).join(' / ') + (std.version ? ` · V${std.version}` : '')
})

function defaultDocumentForm() {
  return {
    id: null, title: '', documentType: 'MANUAL', summary: '',
    relatedStandardDocumentId: '', category: '', software: '',
    softwareVersion: '', standardVersion: '', status: 'DRAFT', version: '',
    canEdit: true,
    content: '# 手册标题\n\n## 1. 适用范围\n\n## 2. 标准参数\n\n- JDK 版本：{{JDK_VERSION}}\n- 日志保留天数：{{LOG_RETENTION_DAYS}}\n\n## 3. 操作步骤\n\n'
  }
}

function statusLabel(status) {
  const map = { DRAFT: '草稿', PENDING_REVIEW: '审核中', PUBLISHED: '已发布', MODIFYING: '修改中' }
  return map[status] || status
}

function statusClass(status) {
  const map = { DRAFT: 'draft', PENDING_REVIEW: 'pending-review', PUBLISHED: 'published', MODIFYING: 'modifying' }
  return map[status] || 'off'
}

function findStandardDocument(id) {
  return (props.standardDocumentOptions || []).find(doc => String(doc.id) === String(id))
}

function openMetaDialog() {
  Object.assign(metaForm, {
    id: documentForm.id,
    title: documentForm.title,
    documentType: documentForm.documentType,
    summary: documentForm.summary,
    relatedStandardDocumentId: documentForm.relatedStandardDocumentId,
    category: documentForm.category,
    software: documentForm.software,
    softwareVersion: documentForm.softwareVersion,
    standardVersion: documentForm.standardVersion
  })
  showMetaDialog.value = true
}

function closeMetaDialog() {
  showMetaDialog.value = false
}

function onMetaCategoryChange() {
  metaForm.software = ''
  metaForm.relatedStandardDocumentId = ''
}

function onMetaSoftwareChange() {
  metaForm.relatedStandardDocumentId = ''
}

function applyMeta() {
  if (!metaForm.relatedStandardDocumentId) { props.notify('请选择关联标准', 'error'); return }
  const std = findStandardDocument(metaForm.relatedStandardDocumentId)
  Object.assign(documentForm, {
    title: metaForm.title,
    documentType: metaForm.documentType,
    summary: metaForm.summary || '',
    relatedStandardDocumentId: metaForm.relatedStandardDocumentId,
    category: std?.category || metaForm.category,
    software: std?.software || metaForm.software,
    softwareVersion: std?.softwareVersion || metaForm.softwareVersion || ''
  })
  showMetaDialog.value = false
  loadStandardParameters(documentForm.relatedStandardDocumentId)
}

async function loadStandardParameters(standardDocumentId) {
  if (!standardDocumentId) { standardParameters.value = []; renderPreview(); return }
  try {
    standardParameters.value = await request(`/api/admin/standard-parameters?parameterStandardId=${encodeURIComponent(standardDocumentId)}`)
  } catch { standardParameters.value = [] }
  renderPreview()
}

function renderPreview() {
  let rendered = documentForm.content || ''
  for (const param of standardParameters.value) {
    rendered = rendered.split(`{{${param.code}}}`).join(param.value)
  }
  try {
    previewHtml.value = props.markdown.render(rendered)
  } catch {
    previewHtml.value = '<p style="color:#b7333d">Markdown 渲染出错</p>'
  }
}

function onContentChange() {
  clearTimeout(previewTimer)
  previewTimer = setTimeout(renderPreview, 200)
}

watch(() => [documentForm.relatedStandardDocumentId, documentForm.content], () => {
  onContentChange()
}, { deep: false })

async function handleSave() {
  if (!documentForm.relatedStandardDocumentId) { props.notify('请先设置文档信息（关联标准）', 'error'); return }
  saving.value = true
  try {
    const body = { ...documentForm }
    await request(
      documentForm.id ? `/api/admin/standard-documents/${documentForm.id}` : '/api/admin/standard-documents',
      { method: documentForm.id ? 'PUT' : 'POST', body }
    )
    emit('saved')
  } catch (error) {
    props.notify(error.message || '保存失败', 'error')
  } finally { saving.value = false }
}

async function loadDocument(id) {
  try {
    const doc = await request(`/api/admin/standard-documents/${id}`)
    if (!doc) { props.notify('文档不存在', 'error'); emit('cancel'); return }
    const relatedStandard = doc.relatedStandardDocumentId ? findStandardDocument(doc.relatedStandardDocumentId) : null
    Object.assign(documentForm, {
      id: doc.id, title: doc.title, documentType: doc.documentType,
      summary: doc.summary || '', relatedStandardDocumentId: doc.relatedStandardDocumentId || '',
      category: relatedStandard?.category || doc.category || '',
      software: relatedStandard?.software || doc.software || '',
      softwareVersion: relatedStandard?.softwareVersion || doc.softwareVersion || '',
      status: doc.status || 'DRAFT',
      version: doc.version || '',
      canEdit: doc.canEdit !== false,
      content: doc.content || ''
    })
    if (doc.relatedStandardDocumentId) await loadStandardParameters(doc.relatedStandardDocumentId)
    else renderPreview()
  } catch (error) {
    props.notify('加载文档失败: ' + (error.message || '未知错误'), 'error')
    emit('cancel')
  }
}

onMounted(() => {
  if (props.documentId) {
    loadDocument(props.documentId)
  } else {
    renderPreview()
    openMetaDialog()
  }
})
</script>

<style scoped>
.document-editor-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 88px);
  background: #fff;
}

/* ── toolbar ── */
.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-bottom: 1px solid #e3e9f1;
  gap: 16px;
  flex-shrink: 0;
}

.doc-meta-line {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 14px;
  color: #4a5568;
  line-height: 1.6;
}

.meta-title-text {
  font-size: 18px;
  font-weight: 700;
  color: #182033;
}

.meta-sep {
  color: #c4cdd6;
  font-weight: 300;
}

.meta-edit-btn {
  margin-left: 10px;
  font-size: 12px;
  background: none;
  border: 1px solid #d1d9e0;
  color: #6b7a8d;
  padding: 2px 10px;
  border-radius: 4px;
  cursor: pointer;
  min-height: auto;
}

.meta-edit-btn:hover {
  background: #f0f3f7;
  color: #2356a5;
  border-color: #2356a5;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.toolbar-actions button {
  font-size: 13px;
  min-height: 32px;
  padding: 0 14px;
}

/* ── body ── */
.editor-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  flex: 1;
  min-height: 0;
}

.editor-pane,
.preview-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.editor-pane { border-right: 1px solid #e3e9f1; }

.pane-label {
  padding: 6px 14px;
  font-size: 12px;
  color: #6b7a8d;
  font-weight: 600;
  border-bottom: 1px solid #e3e9f1;
  flex-shrink: 0;
  background: #f8f9fb;
}

.editor-textarea {
  flex: 1;
  border: none;
  padding: 14px;
  font-family: Consolas, "SFMono-Regular", "Fira Code", monospace;
  font-size: 14px;
  line-height: 1.75;
  resize: none;
  background: #fefefe;
  color: #1a1a2e;
}

.editor-textarea:focus { outline: none; }

.editor-textarea:disabled {
  background: #f5f5f5;
  color: #999;
  cursor: not-allowed;
}

.readonly-hint {
  font-size: 12px;
  color: #b45309;
  padding: 0 14px;
  line-height: 32px;
}

.preview-content {
  flex: 1;
  overflow-y: auto;
  padding: 14px 18px;
  border: none;
  border-radius: 0;
  max-height: none;
  background: #fafbfc;
  line-height: 1.85;
  font-size: 14px;
}

/* ── meta dialog ── */
.meta-dialog-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.meta-dialog {
  background: #fff;
  border-radius: 10px;
  padding: 24px;
  width: 480px;
  max-width: 90vw;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
}

.meta-dialog h3 {
  margin: 0 0 16px;
  font-size: 16px;
}

.meta-dialog-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.meta-dialog-grid label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  font-weight: 600;
  color: #4a5568;
}

.meta-dialog-grid label.wide {
  grid-column: 1 / -1;
}

.meta-dialog-grid input,
.meta-dialog-grid select {
  padding: 7px 10px;
  border: 1px solid #d1d9e0;
  border-radius: 6px;
  font-size: 14px;
  background: #fff;
  min-height: 36px;
}

.meta-dialog-grid input:focus,
.meta-dialog-grid select:focus {
  outline: none;
  border-color: #2356a5;
}

.meta-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

.meta-dialog-actions button {
  font-size: 13px;
  min-height: 32px;
  padding: 0 16px;
}
</style>
