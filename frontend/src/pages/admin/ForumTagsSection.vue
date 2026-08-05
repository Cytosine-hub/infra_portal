<template>
  <section class="forum-tags-section">
    <header class="section-toolbar">
      <div>
        <h3>论坛文章标签管理</h3>
        <p>查看、添加、编辑和删除可管理岗位组的文章标签</p>
      </div>
      <div class="actions">
        <BaseInput v-model="keyword" placeholder="搜索标签名称或创建人" />
        <BaseButton data-test="add-tag" variant="primary" @click="openCreate">添加标签</BaseButton>
      </div>
    </header>

    <div class="tag-summary">
      <div><span>标签总数</span><strong>{{ filteredTags.length }}</strong></div>
      <div><span>关联文章</span><strong>{{ relatedPostCount }}</strong></div>
      <div><span>管理范围</span><strong class="scope-text">{{ isSysAdmin ? '全部岗位组' : managedCategory }}</strong></div>
    </div>

    <div class="list-panel">
      <DataTable :columns="columns" :data="filteredTags" :loading="loading" empty-text="暂无论坛标签">
        <template #cell-name="{ value }"><span class="tag-name">{{ value }}</span></template>
        <template #cell-postCount="{ value }">{{ value }} 篇</template>
        <template #cell-updatedAt="{ value }">{{ formatDate(value) }}</template>
        <template #actions="{ row }">
          <BaseButton size="sm" variant="ghost" @click="openEdit(row)">编辑</BaseButton>
          <BaseButton size="sm" variant="danger" @click="remove(row)">删除</BaseButton>
        </template>
      </DataTable>
    </div>

    <FormModal v-model="showForm" :title="editingTag ? '编辑标签' : '添加标签'" @submit="save">
      <div class="tag-form">
        <BaseInput id="forum-tag-name" v-model="form.name" label="标签名称" placeholder="请输入标签名称"
          data-test="tag-name" />
        <label v-if="isSysAdmin && !editingTag" class="field-label">
          所属岗位组
          <select v-model="form.category">
            <option value="">请选择岗位组</option>
            <option v-for="category in categories" :key="category" :value="category">{{ category }}</option>
          </select>
        </label>
        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      </div>
      <template #actions>
        <BaseButton data-test="save-tag" variant="primary" :loading="saving" @click="save">保存</BaseButton>
        <BaseButton variant="ghost" @click="showForm = false">取消</BaseButton>
      </template>
    </FormModal>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { request } from '../../api'
import BaseButton from '../../components/ui/BaseButton.vue'
import BaseInput from '../../components/ui/BaseInput.vue'
import DataTable from '../../components/ui/DataTable.vue'
import FormModal from '../../components/ui/FormModal.vue'

const props = defineProps({
  isSysAdmin: { type: Boolean, default: false },
  managedCategory: { type: String, default: '' },
  notify: { type: Function, required: true },
  confirm: { type: Function, default: null }
})

const categories = ['中间件', '数据库', '主机', '网络', '安全']
const columns = [
  { key: 'name', label: '标签名称' },
  { key: 'postCount', label: '关联文章' },
  { key: 'category', label: '所属岗位组' },
  { key: 'createdBy', label: '创建人' },
  { key: 'updatedAt', label: '更新时间' }
]
const tags = ref([])
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const showForm = ref(false)
const editingTag = ref(null)
const formError = ref('')
const form = reactive({ name: '', category: '' })

const filteredTags = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return tags.value
  return tags.value.filter(tag => [tag.name, tag.createdBy].some(item => String(item || '').toLowerCase().includes(value)))
})
const relatedPostCount = computed(() => filteredTags.value.reduce((sum, tag) => sum + Number(tag.postCount || 0), 0))

async function load() {
  loading.value = true
  try { tags.value = await request('/api/forum/admin/tags') }
  catch (error) { props.notify(error.message || '标签列表加载失败', 'error') }
  finally { loading.value = false }
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

function validate() {
  const name = form.name.trim()
  if (!name) return '标签名称不能为空'
  if (name.length > 50) return '标签名称不能超过50个字符'
  if (!editingTag.value && !form.category) return '请选择所属岗位组'
  return ''
}

async function save() {
  formError.value = validate()
  if (formError.value) return
  saving.value = true
  try {
    const path = editingTag.value ? `/api/forum/admin/tags/${editingTag.value.id}` : '/api/forum/admin/tags'
    await request(path, { method: editingTag.value ? 'PUT' : 'POST', body: { name: form.name.trim(), category: form.category } })
    props.notify(editingTag.value ? '标签已更新' : '标签已添加', 'success')
    showForm.value = false
    await load()
  } catch (error) {
    formError.value = error.message || '标签保存失败'
    props.notify(formError.value, 'error')
  }
  finally { saving.value = false }
}

function remove(tag) {
  const execute = async () => {
    try {
      await request(`/api/forum/admin/tags/${tag.id}`, { method: 'DELETE' })
      props.notify('标签已删除', 'success')
      await load()
    } catch (error) { props.notify(error.message || '标签删除失败', 'error') }
  }
  if (props.confirm) props.confirm(`确认删除标签“${tag.name}”？`, execute)
  else if (window.confirm(`确认删除标签“${tag.name}”？`)) execute()
}

function formatDate(value) { return value ? String(value).slice(0, 10) : '-' }

onMounted(load)
</script>

<style scoped>
.forum-tags-section { display: flex; flex-direction: column; min-height: 0; }
.section-toolbar h3 { margin: 0; font-size: var(--text-xl); letter-spacing: 0; }
.section-toolbar p { margin: var(--space-xs) 0 0; color: var(--color-text-secondary); font-size: var(--text-sm); }
.tag-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-md); margin-bottom: var(--space-lg); }
.tag-summary > div { border: 1px solid var(--color-border); padding: var(--space-md); background: var(--color-bg); }
.tag-summary span { display: block; color: var(--color-text-secondary); font-size: var(--text-sm); }
.tag-summary strong { display: block; margin-top: var(--space-xs); font-size: var(--text-2xl); }
.tag-summary .scope-text { font-size: var(--text-lg); }
.tag-name { display: inline-flex; padding: var(--space-xs) var(--space-sm); border-radius: var(--radius-full); background: var(--color-primary-light); color: var(--color-primary); font-weight: 600; }
.tag-form { display: grid; gap: var(--space-md); }
.field-label { display: grid; gap: var(--space-xs); color: var(--color-text-secondary); font-size: var(--text-sm); font-weight: 500; }
.field-label select { padding: var(--space-sm) var(--space-md); border: 1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-bg); color: var(--color-text); }
.form-error { margin: 0; color: var(--color-danger); font-size: var(--text-sm); }
@media (max-width: 760px) {
  .section-toolbar { align-items: stretch; flex-direction: column; white-space: normal; }
  .section-toolbar .actions { align-items: stretch; flex-direction: column; }
  .tag-summary { grid-template-columns: minmax(0, 1fr); }
}
</style>
