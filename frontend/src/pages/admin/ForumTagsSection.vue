<template>
  <section class="forum-tags-section">
    <header class="forum-management-header">
      <div>
        <h2>论坛管理</h2>
        <p>统一维护论坛内容标签。</p>
      </div>
    </header>

    <nav class="forum-tabs segmented-tabs" aria-label="论坛管理子栏目" role="tablist">
      <button
        id="forum-tags-tab"
        type="button"
        :class="{ active: activeTab === 'tags' }"
        role="tab"
        :aria-selected="activeTab === 'tags'"
        aria-controls="forum-tags-panel"
        @click="activeTab = 'tags'"
      >标签管理</button>
      <button
        id="forum-articles-tab"
        type="button"
        :class="{ active: activeTab === 'articles' }"
        role="tab"
        :aria-selected="activeTab === 'articles'"
        aria-controls="forum-articles-panel"
        @click="activeTab = 'articles'"
      >文章管理</button>
    </nav>

    <div
      v-if="activeTab === 'tags'"
      id="forum-tags-panel"
      class="tag-management-panel"
      role="tabpanel"
      aria-labelledby="forum-tags-tab"
    >
      <div class="tag-toolbar">
        <div class="tag-filters">
          <BaseInput v-model="keyword" placeholder="搜索标签名称" />
        </div>
        <div class="tag-actions">
          <BaseButton @click="loadTags">刷新</BaseButton>
          <BaseButton variant="primary" data-action="add" @click="openCreate">新建标签</BaseButton>
        </div>
      </div>

      <p v-if="loadError" class="page-error">{{ loadError }}</p>
      <DataTable
        :columns="columns"
        :data="filteredTags"
        :loading="loading"
        empty-text="暂无符合条件的标签"
      >
        <template #cell-name="{ value }"><span class="tag-name-chip">{{ value }}</span></template>
        <template #cell-postCount="{ value }">{{ value }} 篇</template>
        <template #cell-createdBy="{ value }">{{ value || '-' }}</template>
        <template #cell-createdAt="{ value }">{{ formatDate(value) }}</template>
        <template #actions="{ row }">
          <div class="tag-row-actions">
            <button type="button" class="ghost" data-action="edit" @click="openEdit(row)">编辑</button>
            <button type="button" class="danger" data-action="delete" @click="openDelete(row)">删除</button>
          </div>
        </template>
      </DataTable>

      <footer class="tag-count" aria-live="polite">共 {{ filteredTags.length }} 个标签</footer>
    </div>

    <section
      v-else
      id="forum-articles-panel"
      class="forum-article-placeholder"
      role="tabpanel"
      aria-labelledby="forum-articles-tab"
    >待开发</section>

    <FormModal
      v-model="showForm"
      :title="editingTag ? '编辑标签' : '添加标签'"
      :submit-text="saving ? '保存中...' : '保存'"
      @submit="saveTag"
    >
      <div class="form-grid">
        <BaseInput
          v-model="form.name"
          label="标签名称"
          placeholder="请输入标签名称"
          data-field="name"
        />
        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      </div>
    </FormModal>

    <BaseModal v-model="showDelete" title="删除标签" width="420px">
      <p class="delete-message">
        确认删除“{{ deletingTag?.name }}”？关联文章将不再展示该标签。
      </p>
      <template #footer>
        <BaseButton variant="ghost" @click="showDelete = false">取消</BaseButton>
        <BaseButton variant="danger" data-confirm="delete" @click="deleteTag">确认删除</BaseButton>
      </template>
    </BaseModal>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createAdminForumTag,
  deleteAdminForumTag,
  listAdminForumTags,
  updateAdminForumTag
} from '../../api.js'
import { useNotify } from '../../composables/useNotify.js'
import BaseButton from '../../components/ui/BaseButton.vue'
import BaseInput from '../../components/ui/BaseInput.vue'
import BaseModal from '../../components/ui/BaseModal.vue'
import DataTable from '../../components/ui/DataTable.vue'
import FormModal from '../../components/ui/FormModal.vue'

const MAX_TAG_NAME_LENGTH = 50
const columns = [
  { key: 'id', label: 'ID' },
  { key: 'name', label: '标签名称' },
  { key: 'postCount', label: '关联文章数' },
  { key: 'createdBy', label: '创建人' },
  { key: 'createdAt', label: '创建时间' }
]
const { notify } = useNotify()
const tags = ref([])
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const formError = ref('')
const keyword = ref('')
const activeTab = ref('tags')
const showForm = ref(false)
const showDelete = ref(false)
const editingTag = ref(null)
const deletingTag = ref(null)
const form = reactive({ name: '' })

const filteredTags = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLocaleLowerCase()
  return tags.value.filter((tag) => {
    const matchesKeyword = !normalizedKeyword
      || tag.name.toLocaleLowerCase().includes(normalizedKeyword)
    return matchesKeyword
  })
})

onMounted(loadTags)

async function loadTags() {
  loading.value = true
  loadError.value = ''
  try {
    tags.value = await listAdminForumTags()
  } catch (error) {
    loadError.value = error.message
    notify(error.message, 'error')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingTag.value = null
  form.name = ''
  formError.value = ''
  showForm.value = true
}

function openEdit(tag) {
  editingTag.value = tag
  form.name = tag.name
  formError.value = ''
  showForm.value = true
}

function validateForm() {
  const name = form.name.trim()
  if (!name) return '标签名称不能为空'
  if (name.length > MAX_TAG_NAME_LENGTH) return `标签名称不能超过 ${MAX_TAG_NAME_LENGTH} 个字符`
  return ''
}

async function saveTag() {
  formError.value = validateForm()
  if (formError.value || saving.value) return
  saving.value = true
  try {
    const payload = { name: form.name.trim() }
    if (editingTag.value) {
      await updateAdminForumTag(editingTag.value.id, payload)
      notify('标签已更新，关联文章已同步', 'success')
    } else {
      await createAdminForumTag(payload)
      notify('标签已添加', 'success')
    }
    showForm.value = false
    await loadTags()
  } catch (error) {
    formError.value = error.message
    notify(error.message, 'error')
  } finally {
    saving.value = false
  }
}

function openDelete(tag) {
  deletingTag.value = tag
  showDelete.value = true
}

async function deleteTag() {
  if (!deletingTag.value) return
  try {
    await deleteAdminForumTag(deletingTag.value.id)
    showDelete.value = false
    notify('标签已删除，关联文章已同步', 'success')
    await loadTags()
  } catch (error) {
    notify(error.message, 'error')
  }
}

function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })
}
</script>

<style scoped>
.forum-tags-section {
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.forum-management-header { margin-bottom: var(--space-lg); }
.forum-management-header h2 {
  margin: 0;
  font-size: var(--text-3xl);
  letter-spacing: 0;
}
.forum-management-header p {
  margin: var(--space-xs) 0 0;
  color: var(--color-text-secondary);
}
.forum-tabs {
  display: inline-flex;
  align-self: flex-start;
  padding: var(--space-xs);
  margin-bottom: var(--space-xl);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
}
.forum-tabs button {
  min-height: 40px;
  padding: var(--space-sm) var(--space-xl);
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: var(--text-base);
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast);
}
.forum-tabs button.active {
  background: var(--color-primary);
  color: var(--color-text-inverse);
  font-weight: 600;
}
.forum-article-placeholder {
  display: grid;
  min-height: 180px;
  place-items: center;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
  color: var(--color-text-secondary);
}
.tag-management-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
  box-shadow: var(--shadow-xs);
}
.tag-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-lg);
  margin: 0;
  border-bottom: 1px solid var(--color-border);
}
.tag-filters {
  display: flex;
  align-items: center;
  flex: 0 1 22rem;
  min-width: 0;
}
.tag-filters :deep(.input-group) { width: 100%; }
.tag-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.tag-management-panel :deep(.table-wrap) {
  min-height: 0;
  overflow: auto;
}
.tag-management-panel :deep(.data-table) { min-width: 52rem; }
.tag-management-panel :deep(.data-table th) {
  padding: var(--space-md) var(--space-lg);
  border-bottom-width: 1px;
  background: var(--color-bg-secondary);
}
.tag-management-panel :deep(th.col-actions) {
  text-align: left;
}
.tag-management-panel :deep(.data-table td) {
  padding: var(--space-lg);
}
.tag-management-panel :deep(td.row-actions) {
  display: table-cell;
  text-align: left;
}
.tag-row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: var(--space-sm);
}
.tag-name-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: var(--space-xs) var(--space-md);
  border-radius: var(--radius-full);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--text-sm);
  font-weight: 600;
  white-space: nowrap;
}
.tag-count {
  padding: var(--space-md) var(--space-lg);
  border-top: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  text-align: right;
}
.form-grid { display: grid; gap: var(--space-lg); }
.form-error, .page-error { color: var(--color-danger); margin: 0; }
.page-error {
  padding: var(--space-md) var(--space-lg);
  background: var(--color-danger-light);
}
.delete-message {
  margin: 0;
  line-height: var(--leading-relaxed);
  color: var(--color-text-secondary);
}
@media (max-width: 720px) {
  .forum-management-header h2 { font-size: var(--text-2xl); }
  .forum-tabs { width: 100%; }
  .forum-tabs button { flex: 1; }
  .tag-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .tag-filters { flex-basis: auto; }
  .tag-actions { justify-content: flex-end; }
}
</style>
