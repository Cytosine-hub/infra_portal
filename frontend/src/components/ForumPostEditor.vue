<template>
  <div class="document-editor-page">
    <div class="editor-toolbar">
      <div class="toolbar-left">
        <div class="doc-meta-line">
          <span class="meta-label">标题</span>
          <input v-model.trim="form.title" placeholder="文章标题" maxlength="200" class="meta-title" />
          <span class="meta-sep">/</span>
          <span class="meta-label">标签</span>
          <input v-model.trim="tagInput" placeholder="逗号分隔" class="meta-tags-input" @keyup.enter="addTag" />
          <span v-for="(tag, i) in form.tags" :key="i" class="meta-tag">
            {{ tag }}<button @click="form.tags.splice(i,1)">&times;</button>
          </span>
        </div>
      </div>
      <div class="toolbar-actions">
        <button class="ghost" @click="$emit('cancel')">取消</button>
        <button class="ghost" @click="showHelp = true">语法</button>
        <button :disabled="saving || !form.title.trim() || !form.content.trim()" @click="save">
          {{ saving ? '保存中...' : '发布' }}
        </button>
      </div>
    </div>

    <div class="editor-body">
      <div class="editor-pane">
        <div class="pane-label">Markdown</div>
        <MarkdownToolbar :textarea="editorRef" />
        <textarea ref="editorRef" class="editor-textarea" v-model="form.content" placeholder="使用 Markdown 编写文章内容...可直接粘贴图片" @input="onContentChange" @keydown="onEditorKeydown" @paste="onEditorPaste"></textarea>
      </div>
      <div class="preview-pane">
        <div class="pane-label">预览</div>
        <div class="preview-content markdown-preview" v-html="previewHtml"></div>
      </div>
    </div>
    <MarkdownHelp :show="showHelp" @close="showHelp = false" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { request } from '../api'
import MarkdownHelp from './MarkdownHelp.vue'
import MarkdownToolbar from './MarkdownToolbar.vue'
import { handleEditorKeydown, handleEditorPaste } from '../editor-utils'

const onEditorKeydown = handleEditorKeydown
const onEditorPaste = handleEditorPaste

const showHelp = ref(false)
const editorRef = ref(null)

const props = defineProps({ auth: Object, postId: [String, Number], markdown: Object, notify: { type: Function, default: (msg, type) => type === 'error' ? alert(msg) : null } })
const emit = defineEmits(['saved', 'cancel'])

const form = reactive({ title: '', content: '', tags: [] })
const tagInput = ref('')
const saving = ref(false)
const isEdit = ref(false)
const previewHtml = ref('')
let previewTimer = null

function addTag() {
  const names = tagInput.value.split(/[,，]/).map(s => s.trim()).filter(Boolean)
  for (const name of names) {
    if (!form.tags.includes(name)) form.tags.push(name)
  }
  tagInput.value = ''
}

function renderPreview() {
  try { previewHtml.value = props.markdown.render(form.content || '') }
  catch { previewHtml.value = '<p style="color:#b7333d">渲染出错</p>' }
}

function onContentChange() {
  clearTimeout(previewTimer)
  previewTimer = setTimeout(renderPreview, 200)
}

async function save() {
  saving.value = true
  try {
    const body = { title: form.title, content: form.content, tags: form.tags }
    if (isEdit.value) {
      await request(`/api/forum/posts/${props.postId}`, { method: 'PUT', body })
    } else {
      await request('/api/forum/posts', { method: 'POST', body })
    }
    emit('saved')
  } catch (e) { props.notify(e.message || '保存失败', 'error') }
  finally { saving.value = false }
}

async function load() {
  if (!props.postId) { renderPreview(); return }
  isEdit.value = true
  try {
    const data = await request(`/api/forum/posts/${props.postId}`, { token: null })
    form.title = data.title; form.content = data.content; form.tags = data.tags || []
    renderPreview()
  } catch {}
}

onMounted(() => load())
watch(() => props.postId, () => {
  Object.assign(form, { title: '', content: '', tags: [] })
  isEdit.value = false
  load()
})
</script>

<style scoped>
/* Reuse same structure as DocumentEditor */
.document-editor-page {
  display: flex; flex-direction: column; min-height: 600px; background: #fff; border-radius: 8px; overflow: hidden;
}
.editor-toolbar {
  display: flex; align-items: center; justify-content: space-between; padding: 12px 24px; border-bottom: 1px solid #e3e9f1; gap: 16px; flex-shrink: 0;
}
.toolbar-left { flex: 1; min-width: 0; }
.doc-meta-line { display: flex; align-items: baseline; gap: 6px; flex-wrap: wrap; font-size: 14px; color: #4a5568; }
.meta-label { font-size: 12px; color: #8896a7; font-weight: 500; }
.meta-sep { color: #c4cdd6; font-weight: 300; }
.meta-title { border: none; background: transparent; font-size: 18px; font-weight: 700; color: #182033; padding: 2px 4px; min-width: 200px; max-width: 360px; border-radius: 4px; }
.meta-title:hover, .meta-title:focus { background: #f0f3f7; outline: none; }
.meta-tags-input { border: none; background: transparent; font-size: 14px; color: #4a5568; padding: 2px 4px; width: 100px; border-radius: 4px; }
.meta-tags-input:focus { background: #f0f3f7; outline: none; }
.meta-tag {
  display: inline-flex; align-items: center; gap: 2px; padding: 1px 8px; border-radius: 999px; font-size: 12px; background: #e8eef8; color: #2356a5;
}
.meta-tag button { min-height: auto; padding: 0 2px; background: none; color: #8896a7; font-size: 14px; line-height: 1; }
.toolbar-actions { display: flex; gap: 8px; flex-shrink: 0; }
.toolbar-actions button { font-size: 13px; min-height: 32px; padding: 0 14px; }
.editor-body { display: grid; grid-template-columns: 1fr 1fr; flex: 1; min-height: 0; }
.editor-pane, .preview-pane { display: flex; flex-direction: column; min-height: 0; }
.editor-pane { border-right: 1px solid #e3e9f1; }
.pane-label {
  padding: 6px 14px; font-size: 12px; color: #6b7a8d; font-weight: 600; border-bottom: 1px solid #e3e9f1; flex-shrink: 0; background: #f8f9fb;
}
.editor-textarea {
  flex: 1; border: none; padding: 14px; font-family: Consolas, "SFMono-Regular", monospace; font-size: 14px; line-height: 1.75; resize: none; background: #fefefe; color: #1a1a2e;
}
.editor-textarea:focus { outline: none; }
.preview-content { flex: 1; overflow-y: auto; padding: 14px 18px; border: none; background: #fafbfc; line-height: 1.85; font-size: 14px; max-height: none; }
</style>
