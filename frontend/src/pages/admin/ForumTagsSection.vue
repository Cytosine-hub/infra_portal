<template>
  <section class="forum-tags-section">
    <header class="forum-management-header">
      <div>
        <h2>论坛管理</h2>
        <p>管理论坛内容与标签配置。</p>
      </div>
    </header>

    <nav class="forum-tabs" aria-label="论坛管理子栏目" role="tablist">
      <button type="button" class="active" role="tab" aria-selected="true">标签管理</button>
    </nav>

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
      :data="filteredTags"
      :loading="loading"
      empty-text="暂无符合条件的标签"
    >
      <template #cell-postCount="{ value }">{{ value }} 篇</template>
      <template #cell-updatedAt="{ value }">{{ formatDate(value) }}</template>
      <template #actions="{ row }">
        <BaseButton size="sm" variant="ghost" data-action="edit" @click="openEdit(row)">编辑</BaseButton>
        <BaseButton size="sm" variant="danger" data-action="delete" @click="openDelete(row)">删除</BaseButton>
      </template>
    </DataTable>

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
        <label class="field-label">
          <span>所属小组</span>
          <select
            v-model="form.category"
            :disabled="!isSysAdmin || Boolean(editingTag)"
            data-field="category"
          >
            <option value="" disabled>请选择所属小组</option>
            <option v-for="category in categories" :key="category" :value="category">
              {{ category }}
            </option>
          </select>
        </label>
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
const categories = ['中间件', '数据库', '主机', '网络', '安全']
const columns = [
  { key: 'name', label: '标签名称' },
  { key: 'category', label: '所属小组' },
  { key: 'postCount', label: '关联文章数' },
  { key: 'updatedAt', label: '更新时间' }
]
const { notify } = useNotify()
const tags = ref([])
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const formError = ref('')
const keyword = ref('')
const showForm = ref(false)
const showDelete = ref(false)
const editingTag = ref(null)
const deletingTag = ref(null)
const form = reactive({ name: '', category: '' })

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
  form.category = props.isSysAdmin ? '' : props.managedCategory
  formError.value = ''
  showForm.value = true
}

function openEdit(tag) {
  editingTag.value = tag
  form.name = tag.name
  form.category = tag.category
  formError.value = ''
  showForm.value = true
}

function validateForm() {
  const name = form.name.trim()
  if (!name) return '标签名称不能为空'
  if (name.length > MAX_TAG_NAME_LENGTH) return `标签名称不能超过 ${MAX_TAG_NAME_LENGTH} 个字符`
  if (!form.category) return '请选择所属小组'
  return ''
}

async function saveTag() {
  formError.value = validateForm()
  if (formError.value || saving.value) return
  saving.value = true
  try {
    const payload = { name: form.name.trim(), category: form.category }
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
.forum-management-header { margin-bottom: var(--space-lg); }
.forum-management-header h2 { margin: 0; font-size: var(--text-2xl); letter-spacing: 0; }
.forum-management-header p { margin: var(--space-xs) 0 0; color: var(--color-text-secondary); }
.forum-tabs {
  display: flex; border-bottom: 1px solid var(--color-border);
  margin-bottom: var(--space-xl);
}
.forum-tabs button {
  min-height: 44px; padding: var(--space-sm) var(--space-lg); border: 0;
  border-bottom: 2px solid transparent; background: transparent;
  color: var(--color-text-secondary); font-size: var(--text-base); cursor: pointer;
}
.forum-tabs button.active {
  border-bottom-color: var(--color-primary); color: var(--color-primary); font-weight: 600;
}
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
.field-label select {
  min-height: 38px; padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--color-border); border-radius: var(--radius-md);
  background: var(--color-bg); color: var(--color-text); font-size: var(--text-base);
}
.field-label select:focus { outline: none; border-color: var(--color-border-focus); }
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
