<template>
  <section class="word-preview-page">
    <div class="word-preview-toolbar">
      <div class="toolbar-left">
        <span class="preview-title">{{ title || '文档预览' }}</span>
        <span v-if="originalFileName" class="preview-file-name muted">{{ originalFileName }}</span>
      </div>
      <div class="toolbar-actions">
        <button class="ghost" @click="$emit('back')">返回列表</button>
        <button class="ghost" @click="$emit('editInfo')">编辑信息</button>
      </div>
    </div>
    <div v-if="loading" class="preview-loading">加载中...</div>
    <div v-else-if="error" class="preview-error">{{ error }}</div>
    <div v-else class="preview-body word-html-content" v-html="htmlContent"></div>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { request } from '../api'

const props = defineProps({
  storedFileName: { type: String, required: true },
  title: { type: String, default: '' },
  originalFileName: { type: String, default: '' },
  notify: { type: Function, default: (msg, type) => type === 'error' ? alert(msg) : null }
})

defineEmits(['back', 'editInfo'])

const htmlContent = ref('')
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    const result = await request(`/api/admin/standard-documents/preview?storedFileName=${encodeURIComponent(props.storedFileName)}`)
    htmlContent.value = result.html || '<p>文档内容为空</p>'
  } catch (e) {
    error.value = e.message || '加载文档预览失败'
    props.notify(error.value, 'error')
  } finally {
    loading.value = false
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
.word-html-content :deep(h1) { font-size: var(--text-2xl); font-weight: 700; margin: var(--space-lg) 0 var(--space-sm); color: var(--color-text); }
.word-html-content :deep(h2) { font-size: var(--text-xl); font-weight: 700; margin: var(--space-lg) 0 var(--space-sm); color: var(--color-text); }
.word-html-content :deep(h3) { font-size: var(--text-lg); font-weight: 600; margin: var(--space-md) 0 var(--space-sm); color: var(--color-text); }
.word-html-content :deep(h4) { font-size: var(--text-base); font-weight: 600; margin: var(--space-md) 0 var(--space-xs); color: var(--color-text); }
.word-html-content :deep(p) { margin: var(--space-sm) 0; }
.word-html-content :deep(table) { border-collapse: collapse; margin: var(--space-md) 0; width: 100%; max-width: 100%; }
.word-html-content :deep(th), .word-html-content :deep(td) { border: 1px solid var(--color-border); padding: var(--space-sm) var(--space-md); text-align: left; font-size: var(--text-sm); }
.word-html-content :deep(th) { background: var(--color-bg-secondary); font-weight: 600; }
.word-html-content :deep(img) { max-width: 100%; height: auto; margin: var(--space-sm) 0; border-radius: var(--radius-sm); }
.word-html-content :deep(ul), .word-html-content :deep(ol) { padding-left: var(--space-xl); margin: var(--space-sm) 0; }
.word-html-content :deep(li) { margin: var(--space-xs) 0; }
.word-html-content :deep(blockquote) { border-left: 3px solid var(--color-primary); padding-left: var(--space-md); margin: var(--space-md) 0; color: var(--color-text-secondary); }
.word-html-content :deep(pre) { background: var(--color-bg-secondary); padding: var(--space-md); border-radius: var(--radius-md); overflow-x: auto; font-size: var(--text-sm); }
.word-html-content :deep(code) { background: var(--color-bg-secondary); padding: var(--space-xs) var(--space-sm); border-radius: var(--radius-sm); font-size: var(--text-sm); }
.word-html-content :deep(hr) { border: none; border-top: 1px solid var(--color-border); margin: var(--space-lg) 0; }
</style>
