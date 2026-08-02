<template>
  <div class="documents-tab">
    <Toolbar>
      <template #filters>
        <input ref="fileInput" type="file" multiple hidden @change="onFilesPicked" />
        <BaseButton variant="primary" :loading="uploading" @click="fileInput?.click()">上传文档</BaseButton>
        <BaseButton @click="openImport">从现有内容导入</BaseButton>
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

    <BaseModal v-model="importOpen" title="从现有内容导入" width="760px">
      <div class="import-dialog">
        <TabNav v-model="importType" :tabs="importTabs" aria-label="导入来源" />

        <div v-if="importType === 'parameters'" class="import-pane">
          <p class="import-note">同步所有已发布参数标准；后续再次同步会更新变更并移除已撤下内容。</p>
          <BaseButton variant="primary" :loading="syncing" @click="syncParameterStandards">
            同步参数标准
          </BaseButton>
        </div>

        <div v-else class="import-pane">
          <p v-if="candidatesLoading" class="import-note">正在加载可导入内容…</p>
          <p v-else-if="!candidates.length" class="import-note">当前没有可导入内容</p>
          <div v-else class="import-list">
            <label v-for="item in candidates" :key="item.id" class="import-item">
              <input v-model="selectedIds" type="checkbox" :value="item.id" />
              <span>
                <strong>{{ item.title }}</strong>
                <small>{{ candidateMeta(item) }}</small>
              </span>
            </label>
          </div>
        </div>
      </div>

      <template v-if="importType !== 'parameters'" #footer>
        <BaseButton
          variant="primary"
          :loading="importing"
          :disabled="selectedIds.length === 0"
          @click="importSelected"
        >
          导入选中内容
        </BaseButton>
      </template>
    </BaseModal>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { request } from '../../api.js'
import BaseButton from '../ui/BaseButton.vue'
import BaseModal from '../ui/BaseModal.vue'
import DataTable from '../ui/DataTable.vue'
import TabNav from '../ui/TabNav.vue'
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

const TYPE_LABEL = {
  UPLOAD: '上传',
  STANDARD_DOC: '参数标准',
  STANDARD_DOCUMENT: '标准文档',
  FORUM_POST: '论坛文章',
  MANUAL: '手工录入',
  WEB: '网页'
}
const importTabs = [
  { key: 'parameters', label: '参数标准' },
  { key: 'documents', label: '标准文档' },
  { key: 'forum', label: '论坛文章' }
]

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
const importOpen = ref(false)
const importType = ref('parameters')
const candidates = ref([])
const selectedIds = ref([])
const candidatesLoading = ref(false)
const importing = ref(false)
const syncing = ref(false)

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

function openImport() {
  importType.value = 'parameters'
  candidates.value = []
  selectedIds.value = []
  importOpen.value = true
}

async function loadCandidates() {
  selectedIds.value = []
  candidates.value = []
  if (importType.value === 'parameters') return

  candidatesLoading.value = true
  try {
    if (importType.value === 'documents') {
      candidates.value = await request('/api/public/standards/all') || []
    } else {
      candidates.value = await loadAllForumCandidates()
    }
  } catch (error) {
    props.notify(error.message || '可导入内容加载失败', 'error')
  } finally {
    candidatesLoading.value = false
  }
}

async function loadAllForumCandidates() {
  const items = []
  let page = 0
  while (true) {
    const result = await request(`/api/forum/posts?page=${page}&size=50`)
    const content = result?.content || []
    items.push(...content)
    const totalPages = Number(result?.totalPages) || 0
    if (result?.last || content.length === 0 || page + 1 >= totalPages) break
    page += 1
  }
  return items
}

async function syncParameterStandards() {
  syncing.value = true
  try {
    const result = await request('/api/knowledge/sync-standards', { method: 'POST' })
    props.notify(
      `同步完成：更新 ${result?.indexed ?? 0}，跳过 ${result?.skipped ?? 0}，移除 ${result?.removed ?? 0}，失败 ${result?.failed ?? 0}`,
      result?.failed ? 'warning' : 'success'
    )
    await load()
  } catch (error) {
    props.notify(error.message || '参数标准同步失败', 'error')
  } finally {
    syncing.value = false
  }
}

async function importSelected() {
  const selected = candidates.value.filter(item => selectedIds.value.includes(item.id))
  if (!selected.length) return

  importing.value = true
  let succeeded = 0
  for (const item of selected) {
    try {
      await request('/api/knowledge/import-content', {
        method: 'POST',
        body: {
          sourceId: item.id,
          sourceType: importType.value === 'documents' ? 'STANDARD_DOCUMENT' : 'FORUM_POST'
        }
      })
      succeeded += 1
    } catch (error) {
      props.notify(`${item.title}：${error.message}`, 'error')
    }
  }
  importing.value = false
  if (succeeded) {
    props.notify(`导入完成，成功 ${succeeded}/${selected.length}`, 'success')
    await load()
  }
}

function candidateMeta(item) {
  if (importType.value === 'forum') {
    return item.tags?.length ? item.tags.join(' · ') : '论坛文章'
  }
  return [item.category, item.software].filter(Boolean).join(' · ') || '标准文档'
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
watch(importType, loadCandidates)
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

.import-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.import-pane {
  min-height: 180px;
}

.import-note {
  margin: 0 0 var(--space-md);
  color: var(--color-text-secondary);
  font-size: 0.875rem;
}

.import-list {
  display: flex;
  flex-direction: column;
  max-height: 46vh;
  overflow-y: auto;
  border-top: 1px solid var(--color-border);
}

.import-item {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  gap: var(--space-sm);
  align-items: start;
  padding: var(--space-sm) 0;
  border-bottom: 1px solid var(--color-border);
  cursor: pointer;
}

.import-item input {
  margin-top: 3px;
}

.import-item span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.import-item strong {
  overflow-wrap: anywhere;
  color: var(--color-text);
  font-size: 0.875rem;
}

.import-item small {
  color: var(--color-text-secondary);
}
</style>
