<template>
  <section class="forum-tags-section">
    <div class="section-heading">
      <div>
        <h3>论坛文章标签</h3>
        <p>标签名称变更会同步到关联文章。</p>
      </div>
      <BaseButton variant="primary" data-action="add" @click="openCreate">添加标签</BaseButton>
    </div>

    <div class="section-toolbar">
      <div class="filters">
        <BaseInput v-model="keyword" placeholder="搜索标签名称" />
      </div>
      <div class="actions">
        <BaseButton variant="ghost" @click="loadTags">刷新</BaseButton>
      </div>
    </div>

    <p v-if="loadError" class="page-error">{{ loadError }}</p>
    <DataTable
      :columns="columns"
      :data="pagedTags"
      :loading="loading"
      empty-text="暂无符合条件的标签"
    >
      <template #cell-postCount="{ value }">{{ value }} 篇</template>
      <template #cell-createdAt="{ value }">{{ formatDate(value) }}</template>
      <template #actions="{ row }">
        <BaseButton size="sm" variant="ghost" data-action="edit" @click="openEdit(row)">编辑</BaseButton>
        <BaseButton size="sm" variant="danger" data-action="delete" @click="openDelete(row)">删除</BaseButton>
      </template>
    </DataTable>
    <nav v-if="pageCount > 1 || tags.length" class="pagination" aria-label="标签分页">
      <span>共 {{ filteredTags.length }} 条</span>
      <button data-page="previous" :disabled="page === 1" @click="page--">上一页</button>
      <span data-page="current">{{ page }}</span>
      <button data-page="next" :disabled="page === pageCount" @click="page++">下一页</button>
    </nav>

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

const props = defineProps({
  isSysAdmin: { type: Boolean, default: false },
  managedCategory: { type: String, default: '' }
})

const MAX_TAG_NAME_LENGTH = 50
const columns = [
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
const page = ref(1)
const pageSize = 10
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
const pageCount = computed(() => Math.max(1, Math.ceil(filteredTags.value.length / pageSize)))
const pagedTags = computed(() => filteredTags.value.slice((page.value - 1) * pageSize, page.value * pageSize))

onMounted(loadTags)

async function loadTags() {
  loading.value = true
  loadError.value = ''
  try {
    tags.value = await listAdminForumTags()
    page.value = 1
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
    const payload = { name: form.name.trim(), category: props.managedCategory || '未分组' }
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
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.forum-tags-section { min-height: 0; display: flex; flex-direction: column; }
.section-heading {
  display: flex; align-items: center; justify-content: space-between;
  gap: var(--space-lg); margin-bottom: var(--space-lg);
}
.section-heading h3 { margin: 0; font-size: var(--text-xl); letter-spacing: 0; }
.section-heading p { margin: var(--space-xs) 0 0; color: var(--color-text-secondary); }
.section-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  gap: var(--space-md); margin-bottom: var(--space-lg);
}
.filters { display: flex; align-items: center; gap: var(--space-sm); min-width: 0; }
.filters select, .field-label select {
  min-height: 38px; padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--color-border); border-radius: var(--radius-md);
  background: var(--color-bg); color: var(--color-text); font-size: var(--text-base);
}
.filters select:focus, .field-label select:focus { outline: none; border-color: var(--color-border-focus); }
.form-grid { display: grid; gap: var(--space-lg); }
.field-label { display: grid; gap: var(--space-xs); color: var(--color-text-secondary); font-size: var(--text-sm); font-weight: 500; }
.field-label select:disabled { background: var(--color-bg-tertiary); opacity: 0.7; }
.form-error, .page-error { color: var(--color-danger); margin: 0; }
.page-error { padding: var(--space-md); background: var(--color-danger-light); border-radius: var(--radius-md); }
.delete-message { margin: 0; line-height: var(--leading-relaxed); color: var(--color-text-secondary); }
@media (max-width: 720px) {
  .section-heading, .section-toolbar { align-items: stretch; flex-direction: column; }
  .filters { align-items: stretch; flex-direction: column; }
}
</style>
