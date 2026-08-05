<template>
  <div class="forum-personal-center">
    <div class="center-header">
      <h2>个人中心</h2>
      <button class="ghost" @click="$emit('back')">返回论坛</button>
    </div>

    <div class="center-tabs">
      <button :class="{ active: tab === 'posts' }" @click="tab = 'posts'">我的文章</button>
      <button :class="{ active: tab === 'tags' }" @click="openTags">标签管理</button>
    </div>

    <div v-if="loading" class="loading-panel">
      <div class="spinner"></div>
      <p>加载中...</p>
    </div>

    <div v-else-if="tab === 'posts'" class="my-posts-list">
      <article v-for="post in posts" :key="post.id" class="post-card" @click="$emit('openPost', post.id)">
        <div class="post-card-body">
          <h3>{{ post.title }}</h3>
          <p class="post-summary">{{ post.summary }}</p>
          <div class="post-meta">
            <span>{{ formatDate(post.createdAt) }}</span>
            <span>{{ post.viewCount }} 阅读</span>
            <span>{{ post.likeCount }} 赞</span>
            <span>{{ post.commentCount }} 评论</span>
          </div>
        </div>
        <div class="post-actions">
          <button class="ghost" @click.stop="$emit('editPost', post.id)">编辑</button>
          <button class="danger" @click.stop="deletePost(post)">删除</button>
        </div>
      </article>

      <p v-if="posts.length === 0" class="empty-state">暂无文章，去论坛发表第一篇吧！</p>

      <div v-if="totalPages > 1" class="pagination">
        <button :disabled="page <= 0" @click="changePage(page - 1)">上一页</button>
        <span>第 {{ page + 1 }} / {{ totalPages }} 页</span>
        <button :disabled="page >= totalPages - 1" @click="changePage(page + 1)">下一页</button>
      </div>
    </div>

    <div v-else class="my-tags-panel">
      <div class="tags-heading">
        <div><h3>我的文章标签</h3><p>仅展示并管理你发布文章中使用的标签</p></div>
        <BaseInput v-model="tagKeyword" placeholder="搜索标签名称" />
      </div>
      <div class="tag-table-wrap">
        <table>
          <thead><tr><th>标签名称</th><th>关联文章</th><th>更新时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="tag in filteredTags" :key="tag.id">
              <td><span class="tag-name">{{ tag.name }}</span></td>
              <td>{{ tag.postCount }} 篇</td>
              <td>{{ formatDate(tag.updatedAt) }}</td>
              <td class="tag-actions">
                <BaseButton size="sm" variant="ghost" @click="openTagEdit(tag)">编辑</BaseButton>
                <BaseButton size="sm" variant="danger" @click="deleteTag(tag)">删除</BaseButton>
              </td>
            </tr>
            <tr v-if="!filteredTags.length"><td colspan="4" class="empty-state">暂无文章标签</td></tr>
          </tbody>
        </table>
      </div>
    </div>

    <FormModal v-model="showTagForm" title="编辑标签" @submit="saveTag">
      <BaseInput id="personal-tag-name" v-model="tagName" label="标签名称" placeholder="请输入标签名称" />
      <p v-if="tagError" class="form-error" role="alert">{{ tagError }}</p>
    </FormModal>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { request } from '../api'
import BaseButton from './ui/BaseButton.vue'
import BaseInput from './ui/BaseInput.vue'
import FormModal from './ui/FormModal.vue'

const props = defineProps({
  auth: Object,
  notify: { type: Function, required: true },
  confirm: { type: Function, default: null }
})

const emit = defineEmits(['back', 'openPost', 'editPost'])

const tab = ref('posts')
const posts = ref([])
const loading = ref(false)
const page = ref(0)
const totalPages = ref(1)
const totalElements = ref(0)
const tags = ref([])
const tagKeyword = ref('')
const editingTag = ref(null)
const tagName = ref('')
const tagError = ref('')
const showTagForm = ref(false)
const filteredTags = computed(() => tags.value.filter(tag => tag.name.toLowerCase().includes(tagKeyword.value.toLowerCase())))

async function loadPosts() {
  loading.value = true
  try {
    const data = await request(`/api/forum/my-posts?page=${page.value}&size=12`)
    posts.value = data?.content || []
    totalPages.value = data?.totalPages || 1
    totalElements.value = data?.totalElements || 0
  } catch {
    posts.value = []
  } finally {
    loading.value = false
  }
}

function changePage(p) {
  page.value = p
  loadPosts()
}

async function openTags() {
  tab.value = 'tags'
  loading.value = true
  try { tags.value = await request('/api/forum/my-tags') }
  catch (error) { tags.value = []; props.notify(error.message || '标签列表加载失败', 'error') }
  finally { loading.value = false }
}

function openTagEdit(tag) {
  editingTag.value = tag
  tagName.value = tag.name
  tagError.value = ''
  showTagForm.value = true
}

async function saveTag() {
  const name = tagName.value.trim()
  if (!name) { tagError.value = '标签名称不能为空'; return }
  if (name.length > 50) { tagError.value = '标签名称不能超过50个字符'; return }
  try {
    await request(`/api/forum/my-tags/${editingTag.value.id}`, { method: 'PUT', body: { name } })
    props.notify('标签已更新', 'success')
    showTagForm.value = false
    await openTags()
  } catch (error) {
    tagError.value = error.message || '标签保存失败'
    props.notify(tagError.value, 'error')
  }
}

function deleteTag(tag) {
  const execute = async () => {
  try {
    await request(`/api/forum/my-tags/${tag.id}`, { method: 'DELETE' })
    props.notify('标签已删除', 'success')
    await openTags()
    } catch (error) { props.notify(error.message || '标签删除失败', 'error') }
  }
  if (props.confirm) props.confirm(`确认从你的文章中删除标签“${tag.name}”？`, execute)
}

async function deletePost(post) {
  if (!confirm(`确认删除「${post.title}」？`)) return
  try {
    await request(`/api/forum/posts/${post.id}`, { method: 'DELETE' })
    props.notify('文章已删除', 'success')
    loadPosts()
  } catch (e) {
    props.notify(e.message || '删除失败', 'error')
  }
}

function formatDate(d) {
  return d ? String(d).slice(0, 10) : ''
}

onMounted(loadPosts)
</script>

<style scoped>
.forum-personal-center {
  max-width: 900px;
  margin: 0 auto;
  padding: var(--space-xl);
}
.center-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-xl);
}
.center-header h2 {
  margin: 0;
}
.center-tabs {
  display: flex;
  gap: var(--space-sm);
  margin-bottom: var(--space-xl);
  border-bottom: 1px solid var(--color-border);
  padding-bottom: var(--space-md);
}
.center-tabs button {
  padding: var(--space-sm) var(--space-lg);
  border: none;
  background: none;
  cursor: pointer;
  border-radius: var(--radius-md);
  font-size: var(--text-base);
  color: var(--color-text-secondary);
}
.center-tabs button.active {
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-weight: 500;
}
.my-posts-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.post-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  padding: var(--space-lg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: border-color var(--transition-fast);
}
.post-card:hover {
  border-color: var(--color-primary-200);
}
.post-card-body {
  flex: 1;
  min-width: 0;
}
.post-card-body h3 {
  margin: 0 0 var(--space-sm);
  font-size: var(--text-lg);
  color: var(--color-text);
}
.post-summary {
  margin: 0 0 var(--space-sm);
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.post-meta {
  display: flex;
  gap: var(--space-md);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}
.post-actions {
  display: flex;
  gap: var(--space-sm);
  flex-shrink: 0;
}
.empty-state {
  text-align: center;
  color: var(--color-text-tertiary);
  padding: var(--space-3xl) 0;
}
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  margin-top: var(--space-xl);
  font-size: var(--text-sm);
}
.loading-panel {
  text-align: center;
  padding: var(--space-3xl) 0;
  color: var(--color-text-tertiary);
}
.my-tags-panel { display: grid; gap: var(--space-lg); }
.tags-heading { display: flex; align-items: end; justify-content: space-between; gap: var(--space-md); }
.tags-heading h3 { margin: 0; font-size: var(--text-xl); letter-spacing: 0; }
.tags-heading p { margin: var(--space-xs) 0 0; color: var(--color-text-secondary); font-size: var(--text-sm); }
.tags-heading :deep(.input-field) { min-height: 40px; }
.tag-table-wrap { overflow-x: auto; border: 1px solid var(--color-border); }
.tag-table-wrap table { width: 100%; border-collapse: collapse; }
.tag-table-wrap th, .tag-table-wrap td { padding: var(--space-md); text-align: left; border-bottom: 1px solid var(--color-border); }
.tag-table-wrap th { color: var(--color-text-secondary); background: var(--color-bg-secondary); font-size: var(--text-sm); }
.tag-name { display: inline-flex; padding: var(--space-xs) var(--space-sm); border-radius: var(--radius-full); background: var(--color-primary-light); color: var(--color-primary); font-weight: 600; }
.tag-actions { display: flex; gap: var(--space-sm); }
.form-error { margin: var(--space-sm) 0 0; color: var(--color-danger); font-size: var(--text-sm); }
@media (max-width: 640px) {
  .tags-heading { align-items: stretch; flex-direction: column; }
  .tags-heading :deep(.input-group) { width: 100%; }
}
</style>
