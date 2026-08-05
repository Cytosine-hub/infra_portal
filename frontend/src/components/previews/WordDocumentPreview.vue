<template>
  <div class="word-document-preview">
    <div v-if="loading" class="preview-state">加载中...</div>
    <div v-else-if="error" class="preview-state error">{{ error }}</div>
    <div v-else-if="isDocx" ref="docxContainer" class="docx-container"></div>
    <div v-else ref="htmlContainer" class="word-html-content" v-html="htmlContent"></div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { fetchBinary, request } from '../../api'
import { enhanceDocumentTables } from './documentTables.js'

const DOCX_RENDER_OPTIONS = {
  className: 'docx-render',
  inWrapper: false,
  ignoreWidth: false,
  ignoreHeight: false,
  ignoreFonts: false,
  breakPages: true,
  useBase64URL: true
}

const props = defineProps({
  fileName: { type: String, required: true },
  rawUrl: { type: String, required: true },
  previewUrl: { type: String, required: true },
  parameters: { type: Array, default: () => [] },
  fetchBlob: { type: Boolean, default: false },
  requestOptions: { type: Object, default: () => ({}) }
})

const docxContainer = ref(null)
const htmlContainer = ref(null)
const htmlContent = ref('')
const loading = ref(false)
const error = ref('')

const isDocx = computed(() => props.fileName.toLowerCase().endsWith('.docx'))

function applyParams(text, params) {
  let result = text || ''
  for (const p of params) {
    if (p?.active !== false && p?.code) {
      result = result.split(`{{${p.code}}}`).join(p.value || '')
    }
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

async function loadDocx() {
  const { renderAsync } = await import('docx-preview')
  const blob = props.fetchBlob
    ? await fetchBinary(props.rawUrl)
    : await fetch(props.rawUrl).then(resp => {
        if (!resp.ok) throw new Error('加载文档失败')
        return resp.blob()
      })
  loading.value = false
  await nextTick()
  if (docxContainer.value) {
    docxContainer.value.innerHTML = ''
    await renderAsync(blob, docxContainer.value, null, DOCX_RENDER_OPTIONS)
    applyParamsToDOM(docxContainer.value, props.parameters)
    enhanceDocumentTables(docxContainer.value)
  }
}

async function loadHtmlPreview() {
  const result = await request(props.previewUrl, props.requestOptions)
  htmlContent.value = applyParams(result?.html || '<p>文档内容为空</p>', props.parameters)
}

async function loadWord() {
  error.value = ''
  htmlContent.value = ''
  loading.value = true
  try {
    if (isDocx.value) {
      await loadDocx()
    } else {
      await loadHtmlPreview()
    }
  } catch (e) {
    error.value = e.message || '加载 Word 预览失败'
  } finally {
    loading.value = false
  }
  // 内容节点在 loading 结束后才挂载，此时再为表格补上横向滚动视口
  if (!isDocx.value && !error.value) {
    await nextTick()
    enhanceDocumentTables(htmlContainer.value)
  }
}

watch(
  () => [props.fileName, props.rawUrl, props.previewUrl, JSON.stringify(props.parameters)],
  loadWord,
  { immediate: true }
)
</script>

<style scoped src="./documentTables.css"></style>

<style scoped>
.word-document-preview {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  background: var(--color-bg-secondary);
}

.docx-container {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  overflow-x: auto;
  overflow-y: auto;
  padding: 0;
}

.docx-container :deep(.docx-render) {
  background: var(--color-bg);
  margin: 0 auto;
  box-shadow: var(--shadow-md);
}

.word-html-content {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-xl) var(--space-2xl);
  line-height: 1.85;
  font-size: var(--text-base);
  background: var(--color-bg);
}

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

.preview-state {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  font-size: var(--text-base);
}

.preview-state.error {
  color: var(--color-danger);
}
</style>
