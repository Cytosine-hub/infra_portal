<template>
  <section class="word-preview-page">
    <div class="word-preview-toolbar">
      <div class="toolbar-left">
        <span class="preview-title">{{ docInfo.title || effectiveOriginalFileName || '文档预览' }}</span>
        <span v-if="effectiveOriginalFileName" class="preview-file-name muted">{{ effectiveOriginalFileName }}</span>
      </div>
      <div class="toolbar-actions">
        <button class="ghost" @click="$emit('back')">返回列表</button>
        <button v-if="canManage" class="ghost" :disabled="replacing" @click="replaceFileInput.click()">{{ replacing ? '上传中...' : '替换文档' }}</button>
        <button v-if="canManage" class="ghost" @click="openInfoDialog">编辑信息</button>
        <input ref="replaceFileInput" type="file" accept=".doc,.docx,.pdf" class="hidden-file-input" @change="replaceDocument" />
      </div>
    </div>

    <div v-if="loading" class="preview-loading">加载中...</div>
    <div v-else-if="error" class="preview-error">{{ error }}</div>
    <template v-else>
      <!-- pdf: iframe 渲染 -->
      <iframe v-if="isPdf" :src="pdfBlobUrl" class="preview-body pdf-frame" />
      <!-- docx: docx-preview 渲染 -->
      <div v-else-if="isDocx" ref="docxContainer" class="preview-body docx-container"></div>
      <!-- doc: Tika HTML 渲染 -->
      <div v-else class="preview-body word-html-content" v-html="htmlContent"></div>
    </template>

    <!-- 文档信息弹窗 -->
    <div v-if="showInfoDialog" class="meta-dialog-backdrop" @click.self="onBackdropClick">
      <form class="meta-dialog" @submit.prevent="saveInfo">
        <h3>{{ isNewDoc ? '填写文档信息' : '编辑文档信息' }}</h3>
        <div class="meta-dialog-grid">
          <label>标题<input v-model.trim="infoForm.title" required maxlength="160" placeholder="文档标题" /></label>
          <label>类型
            <select v-model="infoForm.documentType">
              <option value="MANUAL">手册</option>
              <option value="ARTICLE">文章</option>
            </select>
          </label>
          <label>分类
            <select v-model="infoForm.category" required @change="onCategoryChange">
              <option value="">请选择</option>
              <option v-for="cat in softwareTypeCategories" :key="cat" :value="cat">{{ cat }}</option>
            </select>
          </label>
          <label>软件
            <select v-model="infoForm.software" :disabled="!infoForm.category" required @change="onSoftwareChange">
              <option value="">请选择</option>
              <option v-for="sw in softwareOptions" :key="sw" :value="sw">{{ sw }}</option>
            </select>
          </label>
          <label class="wide">关联标准
            <select v-model="infoForm.relatedStandardDocumentId" :disabled="!infoForm.software" required>
              <option value="">请选择</option>
              <option v-for="std in standardOptions" :key="std.id" :value="std.id">
                {{ [std.category, std.software, std.softwareVersion].filter(Boolean).join(' / ') }} · V{{ std.version || '-' }}
              </option>
            </select>
          </label>
          <label class="wide">摘要<input v-model.trim="infoForm.summary" maxlength="500" placeholder="文档摘要（可选）" /></label>
        </div>
        <p v-if="saveError" class="meta-error">{{ saveError }}</p>
        <div class="meta-dialog-actions">
          <button v-if="!isNewDoc" type="button" class="ghost" @click="closeInfoDialog">取消</button>
          <button v-if="isNewDoc" type="button" class="ghost danger" @click="$emit('back')">放弃</button>
          <button type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存' }}</button>
        </div>
      </form>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, reactive, ref, nextTick } from 'vue'
import { request, fetchBinary } from '../api'

const DOCX_RENDER_OPTIONS = {
  className: 'docx-render',
  inWrapper: false,
  ignoreWidth: false,
  ignoreHeight: false,
  ignoreFonts: false,
  breakPages: true,
  useBase64URL: true
}
const MSG_EMPTY_DOC = '文档内容为空'
const MSG_LOAD_FAILED = '加载文档预览失败'

const props = defineProps({
  storedFileName: { type: String, required: true },
  docId: { type: [String, Number], default: null },
  isNewDoc: { type: Boolean, default: false },
  initialContent: { type: String, default: '' },
  initialTitle: { type: String, default: '' },
  originalFileName: { type: String, default: '' },
  relatedStandardDocumentId: { type: [String, Number], default: null },
  canManage: { type: Boolean, default: true },
  softwareTypeCategories: { type: Array, default: () => [] },
  softwareTypes: { type: Array, default: () => [] },
  standardDocumentOptions: { type: Array, default: () => [] },
  notify: { type: Function, default: (msg, type) => type === 'error' ? alert(msg) : null }
})

const emit = defineEmits(['back', 'saved', 'replaced'])

const docxContainer = ref(null)
const htmlContent = ref('')
const loading = ref(true)
const error = ref('')
const saving = ref(false)
const saveError = ref('')
const showInfoDialog = ref(false)
const parameters = ref([])
const replacing = ref(false)
const replaceFileInput = ref(null)

// 本地副本：替换文档后更新，避免重新挂载组件
const effectiveStoredFileName = ref(props.storedFileName)
const effectiveOriginalFileName = ref(props.originalFileName)

const docInfo = reactive({ title: props.initialTitle || '', id: props.docId || null })

const infoForm = reactive({
  title: '', documentType: 'MANUAL', summary: '',
  category: '', software: '', relatedStandardDocumentId: ''
})

const isDocx = computed(() => effectiveStoredFileName.value.toLowerCase().endsWith('.docx'))
const isPdf = computed(() => effectiveStoredFileName.value.toLowerCase().endsWith('.pdf'))
const pdfBlobUrl = ref('')

const softwareOptions = computed(() => {
  if (!infoForm.category) return []
  return [...new Set(props.softwareTypes.filter(t => t.category === infoForm.category).map(t => t.name))]
})

const standardOptions = computed(() =>
  props.standardDocumentOptions.filter(s => s.category === infoForm.category && s.software === infoForm.software)
)

function onCategoryChange() {
  infoForm.software = ''
  infoForm.relatedStandardDocumentId = ''
}

function onSoftwareChange() {
  infoForm.relatedStandardDocumentId = ''
}

function openInfoDialog() {
  saveError.value = ''
  if (docInfo.id) {
    loadExistingDocInfo()
  } else {
    infoForm.title = docInfo.title || props.initialTitle || ''
    infoForm.documentType = 'MANUAL'
    infoForm.summary = ''
    infoForm.category = ''
    infoForm.software = ''
    infoForm.relatedStandardDocumentId = ''
  }
  showInfoDialog.value = true
}

async function loadExistingDocInfo() {
  try {
    const doc = await request(`/api/admin/standard-documents/${docInfo.id}`)
    infoForm.title = doc.title || ''
    infoForm.documentType = doc.documentType || 'MANUAL'
    infoForm.summary = doc.summary || ''
    infoForm.category = doc.category || ''
    infoForm.software = doc.software || ''
    infoForm.relatedStandardDocumentId = doc.relatedStandardDocumentId || ''
  } catch (e) {
    props.notify(e.message || '加载文档信息失败', 'error')
  }
}

function closeInfoDialog() {
  if (!props.isNewDoc) showInfoDialog.value = false
}

function onBackdropClick() {
  if (!props.isNewDoc) closeInfoDialog()
}

async function saveInfo() {
  if (!infoForm.relatedStandardDocumentId) {
    saveError.value = '请选择关联标准'
    return
  }
  saveError.value = ''
  saving.value = true
  try {
    const body = {
      title: infoForm.title,
      documentType: infoForm.documentType,
      summary: infoForm.summary || '',
      relatedStandardDocumentId: infoForm.relatedStandardDocumentId,
      content: props.initialContent || '',
      storedFileName: effectiveStoredFileName.value,
      originalFileName: effectiveOriginalFileName.value
    }
    let result
    if (docInfo.id) {
      result = await request(`/api/admin/standard-documents/${docInfo.id}`, { method: 'PUT', body })
    } else {
      result = await request('/api/admin/standard-documents', { method: 'POST', body })
    }
    docInfo.title = result.title
    docInfo.id = result.id
    showInfoDialog.value = false
    props.notify('文档信息已保存', 'success')
    emit('saved')
  } catch (e) {
    saveError.value = e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

function applyParams(text, params) {
  let result = text
  for (const p of params) {
    if (p.active !== false) result = result.split(`{{${p.code}}}`).join(p.value || '')
  }
  return result
}

function applyParamsToDOM(container, params) {
  if (!container || !params.length) return
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT)
  let node
  while ((node = walker.nextNode())) {
    node.textContent = applyParams(node.textContent, params)
  }
}

async function loadParameters() {
  if (!props.relatedStandardDocumentId) return
  try {
    parameters.value = await request(
      `/api/admin/standard-parameters?parameterStandardId=${props.relatedStandardDocumentId}`
    )
  } catch {
    parameters.value = []
  }
}

async function loadPreview() {
  loading.value = true
  error.value = ''
  try {
    if (isPdf.value) {
      const blob = await fetchBinary(`/api/admin/standard-documents/raw?storedFileName=${encodeURIComponent(effectiveStoredFileName.value)}`)
      if (pdfBlobUrl.value) URL.revokeObjectURL(pdfBlobUrl.value)
      pdfBlobUrl.value = URL.createObjectURL(blob)
    } else if (isDocx.value) {
      const { renderAsync } = await import('docx-preview')
      const blob = await fetchBinary(`/api/admin/standard-documents/raw?storedFileName=${encodeURIComponent(effectiveStoredFileName.value)}`)
      // 先关 loading 让容器 DOM 渲染出来，再调 renderAsync
      loading.value = false
      await nextTick()
      if (docxContainer.value) {
        await renderAsync(blob, docxContainer.value, null, DOCX_RENDER_OPTIONS)
        applyParamsToDOM(docxContainer.value, parameters.value)
      }
    } else {
      const result = await request(`/api/admin/standard-documents/preview?storedFileName=${encodeURIComponent(effectiveStoredFileName.value)}`)
      htmlContent.value = applyParams(result.html || `<p>${MSG_EMPTY_DOC}</p>`, parameters.value)
    }
  } catch (e) {
    error.value = e.message || MSG_LOAD_FAILED
    props.notify(error.value, 'error')
  } finally {
    loading.value = false
  }
}

async function replaceDocument(event) {
  if (!props.canManage) return
  const file = event.target.files[0]
  if (!file) return
  replacing.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('convertToMarkdown', 'false')
    const result = await request('/api/admin/standard-documents/upload', { method: 'POST', body: fd })
    if (!result.storedFileName) { props.notify('上传失败，未获得文件名', 'error'); return }
    effectiveStoredFileName.value = result.storedFileName
    effectiveOriginalFileName.value = result.originalFileName || file.name
    if (docInfo.id) {
      if (!infoForm.relatedStandardDocumentId) await loadExistingDocInfo()
      await request(`/api/admin/standard-documents/${docInfo.id}`, {
        method: 'PUT',
        body: {
          title: infoForm.title || docInfo.title,
          documentType: infoForm.documentType || 'MANUAL',
          summary: infoForm.summary || '',
          relatedStandardDocumentId: infoForm.relatedStandardDocumentId,
          content: '',
          storedFileName: result.storedFileName,
          originalFileName: result.originalFileName || file.name
        }
      })
    }
    emit('replaced', { storedFileName: result.storedFileName, originalFileName: result.originalFileName || file.name })
    props.notify('文档已替换，正在刷新预览...', 'success')
    await loadPreview()
  } catch (e) {
    props.notify(e.message || '替换失败', 'error')
  } finally {
    replacing.value = false
    event.target.value = ''
  }
}

onBeforeUnmount(() => {
  if (pdfBlobUrl.value) URL.revokeObjectURL(pdfBlobUrl.value)
})

onMounted(async () => {
  await loadParameters()
  await loadPreview()
  if (props.canManage && props.isNewDoc) {
    infoForm.title = props.initialTitle || ''
    infoForm.documentType = 'MANUAL'
    showInfoDialog.value = true
  } else if (props.docId) {
    docInfo.id = props.docId
    docInfo.title = props.initialTitle
    if (props.canManage) {
      openInfoDialog()
    }
  }
})
</script>

<style scoped>
.word-preview-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 88px);
  background: var(--color-bg);
}
.word-preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.toolbar-left { display: flex; align-items: center; gap: 12px; }
.preview-title { font-size: var(--text-lg); font-weight: 700; color: var(--color-text); }
.preview-file-name { font-size: var(--text-sm); }
.toolbar-actions { display: flex; gap: 8px; }
.toolbar-actions button { font-size: var(--text-sm); min-height: 32px; padding: 0 14px; }
.preview-loading, .preview-error {
  display: flex; align-items: center; justify-content: center;
  flex: 1; font-size: var(--text-base); color: var(--color-text-secondary);
}
.preview-error { color: var(--color-danger); }
.preview-body {
  flex: 1; overflow-y: auto; padding: 24px 32px;
  line-height: 1.85; font-size: var(--text-base);
}
.pdf-frame {
  flex: 1; border: none; background: var(--color-bg-secondary);
}
.docx-container {
  flex: 1; overflow-y: auto; padding: 0;
  background: var(--color-bg-secondary);
}
.docx-container :deep(.docx-render) { background: #fff; margin: 0 auto; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.word-html-content :deep(h1) { font-size: var(--text-2xl); font-weight: 700; margin: var(--space-lg) 0 var(--space-sm); color: var(--color-text); }
.word-html-content :deep(h2) { font-size: var(--text-xl); font-weight: 700; margin: var(--space-lg) 0 var(--space-sm); color: var(--color-text); }
.word-html-content :deep(h3) { font-size: var(--text-lg); font-weight: 600; margin: var(--space-md) 0 var(--space-sm); color: var(--color-text); }
.word-html-content :deep(p) { margin: var(--space-sm) 0; }
.word-html-content :deep(table) { border-collapse: collapse; margin: var(--space-md) 0; width: 100%; }
.word-html-content :deep(th), .word-html-content :deep(td) { border: 1px solid var(--color-border); padding: var(--space-sm) var(--space-md); text-align: left; font-size: var(--text-sm); }
.word-html-content :deep(th) { background: var(--color-bg-secondary); font-weight: 600; }
.word-html-content :deep(img) { max-width: 100%; height: auto; margin: var(--space-sm) 0; }
.word-html-content :deep(ul), .word-html-content :deep(ol) { padding-left: var(--space-xl); margin: var(--space-sm) 0; }
.word-html-content :deep(li) { margin: var(--space-xs) 0; }
/* 文档信息弹窗 */
.meta-dialog-backdrop {
  position: fixed; inset: 0; background: var(--color-overlay, rgba(0,0,0,0.4));
  display: flex; align-items: center; justify-content: center; z-index: 200;
}
.meta-dialog {
  background: var(--color-bg); border-radius: var(--radius-lg);
  padding: var(--space-xl); width: 520px; max-width: min(90vw, 560px);
  box-shadow: var(--shadow-lg, 0 8px 32px rgba(0,0,0,0.18));
}
.meta-dialog h3 { margin: 0 0 var(--space-lg); font-size: var(--text-lg); }
.meta-dialog-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-md) var(--space-lg);
  margin-bottom: var(--space-lg);
}
.meta-dialog-grid label { display: flex; flex-direction: column; gap: var(--space-xs); font-size: var(--text-sm); color: var(--color-text-secondary); }
.meta-dialog-grid label.wide { grid-column: 1 / -1; }
.meta-dialog-grid input, .meta-dialog-grid select {
  padding: var(--space-sm) var(--space-md); border: 1px solid var(--color-border);
  border-radius: var(--radius-md); font-size: var(--text-base);
  background: var(--color-bg); color: var(--color-text);
}
.meta-dialog-grid input:focus, .meta-dialog-grid select:focus { outline: none; border-color: var(--color-primary); }
.meta-error { color: var(--color-danger); font-size: var(--text-sm); margin: 0 0 var(--space-md); }
.meta-dialog-actions { display: flex; justify-content: flex-end; gap: var(--space-sm); }
.hidden-file-input { display: none; }
</style>
