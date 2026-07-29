<template>
  <div class="documents-tab">
    <Toolbar>
      <template #filters>
        <input ref="fileInput" type="file" multiple hidden @change="onFilesPicked" />
        <BaseButton variant="primary" :loading="uploading" @click="fileInput?.click()">上传文档</BaseButton>
        <span class="hint">支持 PDF / Word(.doc/.docx) / Excel(.xls/.xlsx) / Markdown</span>
        <BaseButton variant="ghost" :loading="loading" @click="load">刷新</BaseButton>
      </template>
    </Toolbar>

    <p v-if="uploading" class="progress">正在导入 {{ uploadDone }}/{{ uploadTotal }}…</p>

    <DataTable
      :columns="columns"
      :data="sources"
      :loading="loading"
      empty-text="还没有导入任何文档"
    >
      <template #actions="{ row }">
        <BaseButton size="sm" variant="ghost" @click="preview(row)">预览</BaseButton>
        <BaseButton size="sm" variant="danger" @click="confirmRemove(row)">删除</BaseButton>
      </template>
    </DataTable>

    <BaseModal v-model="previewOpen" :title="previewing?.title || ''" width="720px">
      <p class="preview-meta">共 {{ previewTotal }} 个切片</p>
      <pre class="preview">{{ previewContent }}</pre>
    </BaseModal>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { request } from '../../api.js'
import BaseButton from '../ui/BaseButton.vue'
import BaseModal from '../ui/BaseModal.vue'
import DataTable from '../ui/DataTable.vue'
import Toolbar from '../ui/Toolbar.vue'

const props = defineProps({
  notify: { type: Function, required: true },
  confirm: { type: Function, required: true }
})

const columns = [
  { key: 'title', label: '文档' },
  { key: 'sourceTypeLabel', label: '来源' },
  { key: 'category', label: '分类' },
  { key: 'software', label: '软件' }
]

const TYPE_LABEL = { UPLOAD: '上传', STANDARD_DOC: '标准文档', MANUAL: '手工录入', WEB: '网页' }

const rawSources = ref([])
const loading = ref(false)
const uploading = ref(false)
const uploadTotal = ref(0)
const uploadDone = ref(0)
const previewing = ref(null)
const previewOpen = ref(false)
const previewContent = ref('')
const fileInput = ref(null)
const previewTotal = ref(0)

const sources = computed(() => rawSources.value.map(s => ({
  ...s,
  sourceTypeLabel: TYPE_LABEL[s.sourceType] || s.sourceType || '-'
})))

async function load() {
  loading.value = true
  try {
    rawSources.value = await request('/api/knowledge/sources')
  } catch (error) {
    props.notify(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function onFilesPicked(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return

  uploading.value = true
  uploadTotal.value = files.length
  uploadDone.value = 0
  let failed = 0

  // 逐个上传：解析 + 向量化耗时较长，并发会打爆 embedding 服务
  for (const file of files) {
    const form = new FormData()
    form.append('file', file)
    try {
      await request('/api/knowledge/upload', { method: 'POST', body: form })
    } catch (error) {
      failed += 1
      props.notify(`${file.name}：${error.message}`, 'error')
    } finally {
      uploadDone.value += 1
    }
  }

  uploading.value = false
  if (failed < files.length) {
    props.notify(`导入完成，成功 ${files.length - failed}/${files.length}`, 'success')
  }
  await load()
}

async function preview(row) {
  try {
    const params = new URLSearchParams({ title: row.title, sourceType: row.sourceType || '' })
    const result = await request(`/api/knowledge/docs/preview?${params}`)
    // 后端返回按切片序号排列的片段列表，拼接后展示
    previewContent.value = (result.chunks || [])
      .map(c => c.content)
      .join('\n\n———\n\n')
    previewTotal.value = result.totalChunks || 0
    previewing.value = row
    previewOpen.value = true
  } catch (error) {
    props.notify(error.message, 'error')
  }
}

function confirmRemove(row) {
  props.confirm(`确认删除文档「${row.title}」？其切片与向量会一并清除。`, async () => {
    try {
      const params = new URLSearchParams({ title: row.title, sourceType: row.sourceType || '' })
      await request(`/api/knowledge/docs?${params}`, { method: 'DELETE' })
      props.notify('已删除', 'success')
      await load()
    } catch (error) {
      props.notify(error.message, 'error')
    }
  })
}

onMounted(load)
</script>

<style scoped>
.documents-tab {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.upload {
  display: inline-flex;
  cursor: pointer;
}

.hint,
.progress {
  color: var(--color-text-secondary);
  font-size: 0.8125rem;
}

.progress {
  margin: 0;
}

.preview-meta {
  color: var(--color-text-secondary);
  font-size: 0.8125rem;
  margin: 0 0 var(--space-xs);
}

.preview {
  max-height: 60vh;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text);
  font-size: 0.875rem;
  margin: 0;
}
</style>
