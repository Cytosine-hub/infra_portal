<template>
  <div class="experience-tab">
    <Toolbar>
      <template #filters>
        <BaseInput v-model="keyword" placeholder="按标题筛选…" @keyup.enter="load" />
        <BaseButton variant="ghost" :loading="loading" @click="load">刷新</BaseButton>
        <BaseButton variant="ghost" @click="exportPages">导出</BaseButton>
        <BaseButton variant="primary" @click="openDraft">从文档起草</BaseButton>
        <BaseButton variant="primary" @click="openEditor(null)">新建页面</BaseButton>
      </template>
    </Toolbar>

    <div v-if="linkWarning" class="link-warning">
      <span>{{ linkWarning.message }}</span>
      <BaseButton size="sm" variant="primary" :loading="relinking" @click="retryRelink">
        重建关联
      </BaseButton>
    </div>

    <div class="layout">
      <div class="list">
        <DataTable
          :columns="columns"
          :data="pages"
          :loading="loading"
          empty-text="还没有经验页面。团队可以把排查心得、Runbook 沉淀在这里。"
        >
          <template #cell-status="{ row }">
            <StatusBadge :status="row.status" :label="STATUS_LABEL[row.status] || row.status" />
          </template>
          <template #actions="{ row }">
            <BaseButton size="sm" variant="ghost" @click="open(row)">查看</BaseButton>
            <BaseButton size="sm" variant="ghost" @click="openEditor(row)">编辑</BaseButton>
            <BaseButton size="sm" variant="danger" @click="confirmRemove(row)">删除</BaseButton>
          </template>
        </DataTable>
      </div>

      <aside v-if="selected" class="detail">
        <h3>{{ selected.title }}</h3>
        <p class="meta">
          {{ [selected.category, selected.software, PAGE_TYPE_LABEL[selected.pageType] || selected.pageType]
             .filter(Boolean).join(' · ') }}
        </p>
        <p v-if="selected.summary" class="summary">{{ selected.summary }}</p>
        <pre class="content">{{ selected.content }}</pre>
        <div v-if="links.length" class="links">
          <h4>关联页面</h4>
          <ul>
            <li v-for="l in links" :key="l.linkId">
              {{ l.relatedTitle }}
              <span class="link-dir">{{ l.direction === 'incoming' ? '被引用' : '引用' }}</span>
            </li>
          </ul>
        </div>
      </aside>
    </div>

    <BaseModal v-model="editorOpen" :title="editing.id ? '编辑经验页面' : '新建经验页面'" width="760px">
      <div class="draft-form">
        <BaseInput v-model="editing.title" label="标题" placeholder="不要包含软件名和版本号，这些由标签承载" />
        <div class="field-row">
          <BaseInput v-model="editing.category" label="分类" />
          <BaseInput v-model="editing.software" label="软件" />
        </div>
        <label class="field">
          <span>类型</span>
          <select v-model="editing.pageType">
            <option v-for="(label, key) in PAGE_TYPE_LABEL" :key="key" :value="key">{{ label }}</option>
          </select>
        </label>
        <BaseInput v-model="editing.summary" label="一句话摘要" />
        <label class="field">
          <span>正文（Markdown，用 [[页面标题]] 关联其他页面）</span>
          <textarea v-model="editing.content" rows="14" class="editor-area"></textarea>
        </label>
        <BaseButton variant="primary" :loading="saving" @click="savePage">保存为草稿</BaseButton>
      </div>
    </BaseModal>

    <BaseModal v-model="draftOpen" title="从文档起草经验页面" width="640px">
      <div class="draft-form">
        <label class="field">
          <span>源文档</span>
          <select v-model="draftSourceId">
            <option value="">请选择…</option>
            <option v-for="s in sources" :key="s.id" :value="s.id">{{ s.title }}</option>
          </select>
        </label>
        <BaseInput v-model="draftTopic" label="主题" placeholder="例如：主从延迟处理" />
        <p class="draft-hint">
          生成的是草稿，需人工核对修改后再保存。模型只依据所选文档内容起草，不会补充文档之外的参数或命令。
        </p>
        <BaseButton variant="primary" :loading="drafting" @click="runDraft">生成草稿</BaseButton>
        <template v-if="draftContent">
          <pre class="draft-preview">{{ draftContent }}</pre>
          <BaseButton variant="primary" @click="draftToEditor">用这份草稿新建页面</BaseButton>
        </template>
      </div>
    </BaseModal>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchBinary, request } from '../../api.js'
import BaseButton from '../ui/BaseButton.vue'
import BaseInput from '../ui/BaseInput.vue'
import BaseModal from '../ui/BaseModal.vue'
import DataTable from '../ui/DataTable.vue'
import StatusBadge from '../ui/StatusBadge.vue'
import Toolbar from '../ui/Toolbar.vue'

const props = defineProps({
  notify: { type: Function, required: true },
  confirm: { type: Function, required: true }
})

const STATUS_LABEL = {
  DRAFT: '草稿', PENDING_REVIEW: '待审核', ACTIVE: '已发布',
  STALE: '已过期', CONTRADICTED: '有矛盾', REJECTED: '已驳回'
}
const PAGE_TYPE_LABEL = {
  EXPERIENCE: '经验', RUNBOOK: '操作手册', CONCEPT: '概念',
  ENTITY: '实体', STANDARD: '标准', SYNTHESIS: '综述', OVERVIEW: '总览'
}

const columns = [
  { key: 'title', label: '标题' },
  { key: 'category', label: '分类', style: 'width: 110px' },
  { key: 'software', label: '软件', style: 'width: 120px' },
  { key: 'status', label: '状态', style: 'width: 100px' }
]

const pages = ref([])
const selected = ref(null)
const links = ref([])
const keyword = ref('')
const loading = ref(false)
const sources = ref([])
const draftOpen = ref(false)
const draftSourceId = ref('')
const draftTopic = ref('')
const draftContent = ref('')
const drafting = ref(false)
const editorOpen = ref(false)
const saving = ref(false)
const linkWarning = ref(null)
const relinking = ref(false)
const editing = ref(emptyPage())

function emptyPage() {
  return { id: null, title: '', category: '', software: '', pageType: 'EXPERIENCE', summary: '', content: '' }
}

function openEditor(row) {
  editing.value = row
    ? { ...emptyPage(), ...row }
    : emptyPage()
  editorOpen.value = true
}

function draftToEditor() {
  editing.value = { ...emptyPage(), title: draftTopic.value, content: draftContent.value }
  draftOpen.value = false
  editorOpen.value = true
}

async function savePage() {
  if (!editing.value.title.trim() || !editing.value.content.trim()) {
    props.notify('标题和正文不能为空', 'error')
    return
  }
  saving.value = true
  try {
    const payload = { ...editing.value }
    const result = payload.id
      ? await request(`/api/knowledge/pages/${payload.id}`, { method: 'PUT', body: payload })
      : await request('/api/knowledge/pages', { method: 'POST', body: payload })

    // 建边失败不影响保存，但必须让用户看见，并记下页面 id 以便重试
    if (result?.linkWarning) {
      linkWarning.value = { message: result.linkWarning, pageId: result.page?.id }
      props.notify(result.linkWarning, 'error')
    } else {
      linkWarning.value = null
      props.notify('已保存为草稿', 'success')
    }
    editorOpen.value = false
    await load()
  } catch (error) {
    props.notify(error.message, 'error')
  } finally {
    saving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const q = keyword.value.trim()
    pages.value = q
      ? await request(`/api/knowledge/pages/search?q=${encodeURIComponent(q)}&limit=100`)
      : await request('/api/knowledge/pages')
  } catch (error) {
    props.notify(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function openDraft() {
  draftContent.value = ''
  draftOpen.value = true
  if (!sources.value.length) {
    sources.value = await request('/api/knowledge/sources').catch(() => [])
  }
}

async function runDraft() {
  if (!draftSourceId.value) {
    props.notify('请先选择源文档', 'error')
    return
  }
  drafting.value = true
  try {
    const result = await request('/api/knowledge/pages/draft', {
      method: 'POST',
      body: { sourceId: String(draftSourceId.value), topic: draftTopic.value }
    })
    draftContent.value = result.content
  } catch (error) {
    props.notify(error.message, 'error')
  } finally {
    drafting.value = false
  }
}

async function retryRelink() {
  if (!linkWarning.value?.pageId) return
  relinking.value = true
  try {
    const result = await request(
      `/api/knowledge/pages/${linkWarning.value.pageId}/relink`, { method: 'POST' })
    if (result?.linkWarning) {
      props.notify(result.linkWarning, 'error')
    } else {
      linkWarning.value = null
      props.notify(`关联已重建，共 ${result.linksCreated} 条`, 'success')
    }
  } catch (error) {
    props.notify(error.message, 'error')
  } finally {
    relinking.value = false
  }
}

async function open(row) {
  try {
    selected.value = await request(`/api/knowledge/pages/${row.id}`)
    const linkData = await request(`/api/knowledge/pages/${row.id}/links`).catch(() => null)
    links.value = linkData
      ? [...(linkData.outgoing || []), ...(linkData.incoming || [])]
      : []
  } catch (error) {
    props.notify(error.message, 'error')
  }
}

function confirmRemove(row) {
  props.confirm(`确认删除经验页面「${row.title}」？`, async () => {
    try {
      await request(`/api/knowledge/pages/${row.id}`, { method: 'DELETE' })
      if (selected.value?.id === row.id) selected.value = null
      props.notify('已删除', 'success')
      await load()
    } catch (error) {
      props.notify(error.message, 'error')
    }
  })
}

async function exportPages() {
  try {
    // 必须走 api.js：接口是 Bearer 头鉴权，浏览器直接开新页不带 token 会 401
    const blob = await fetchBinary('/api/knowledge/pages/export')
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'wiki-export.zip'
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    props.notify(error.message, 'error')
  }
}

onMounted(load)
</script>

<style scoped>
.experience-tab {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.link-warning {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  flex-wrap: wrap;
  padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-md);
  background: var(--color-danger-light);
  color: var(--color-text);
  font-size: 0.875rem;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-md);
}

@media (min-width: 1100px) {
  .link-warning {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  flex-wrap: wrap;
  padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-md);
  background: var(--color-danger-light);
  color: var(--color-text);
  font-size: 0.875rem;
}

.layout {
    grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  }
}

.detail {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  background: var(--color-bg);
  max-height: 70vh;
  overflow: auto;
}

.detail h3 {
  margin: 0 0 var(--space-xs);
  color: var(--color-text);
}

.meta,
.summary {
  color: var(--color-text-secondary);
  font-size: 0.875rem;
  margin: 0 0 var(--space-xs);
}

.content {
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text);
  font-size: 0.875rem;
  margin: 0;
}

.links h4 {
  margin: var(--space-md) 0 var(--space-xs);
  font-size: 0.875rem;
  color: var(--color-text);
}

.draft-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 0.875rem;
  color: var(--color-text);
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-sm);
}

.editor-area {
  width: 100%;
  font-family: inherit;
  font-size: 0.875rem;
  padding: var(--space-sm);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
  color: var(--color-text);
  resize: vertical;
}

.draft-hint {
  color: var(--color-text-secondary);
  font-size: 0.8125rem;
  margin: 0;
}

.draft-preview {
  max-height: 40vh;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 0.875rem;
  color: var(--color-text);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-sm);
  margin: 0;
}

.link-dir {
  color: var(--color-text-secondary);
  font-size: 0.75rem;
  margin-left: var(--space-2xs);
}

.links ul {
  margin: 0;
  padding-left: 1.25rem;
  color: var(--color-text-secondary);
  font-size: 0.875rem;
}
</style>
