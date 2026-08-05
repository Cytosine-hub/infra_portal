<template>
  <div ref="root" :class="['markdown-document-preview', variant]" v-html="html"></div>
</template>

<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import { enhanceDocumentTables } from './documentTables.js'

const props = defineProps({
  html: { type: String, default: '' },
  variant: { type: String, default: 'default' }
})

const root = ref(null)

// v-html 渲染出的表格不受 Vue 模板控制，内容更新后再补上横向滚动视口
watch(() => props.html, async () => {
  await nextTick()
  enhanceDocumentTables(root.value)
})

onMounted(() => enhanceDocumentTables(root.value))
</script>

<style scoped src="./documentTables.css"></style>

<style scoped>
.markdown-document-preview {
  width: 100%;
  color: var(--color-text);
}

.markdown-document-preview.default {
  line-height: 1.8;
}

.markdown-document-preview.article {
  min-width: 0;
}
</style>
