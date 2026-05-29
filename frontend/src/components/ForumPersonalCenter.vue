<template>
  <div class="forum-personal-center">
    <div class="center-header">
      <h2>个人中心</h2>
      <button class="ghost" @click="$emit('back')">返回论坛</button>
    </div>

    <div class="center-tabs">
      <button :class="{ active: tab === 'posts' }" @click="tab = 'posts'">我的文章</button>
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
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'

const props = defineProps({
  auth: Object,
  notify: { type: Function, default: (msg, type) => type === 'error' ? alert(msg) : null }
})

const emit = defineEmits(['back', 'openPost', 'editPost'])

const tab = ref('posts')
const posts = ref([])
const loading = ref(false)
const page = ref(0)
const totalPages = ref(1)
const totalElements = ref(0)

async function loadPosts() {
  loading.value = true
  try {
    const res = await fetch(`/api/forum/my-posts?page=${page.value}&size=12`, {
      headers: { 'Authorization': `Basic ${props.auth.token}` }
    })
    if (res.ok) {
      const data = await res.json()
      posts.value = data.content || []
      totalPages.value = data.totalPages || 1
      totalElements.value = data.totalElements || 0
    }
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

async function deletePost(post) {
  if (!confirm(`确认删除「${post.title}」？`)) return
  try {
    const res = await fetch(`/api/forum/posts/${post.id}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Basic ${props.auth.token}` }
    })
    if (res.ok) {
      props.notify('文章已删除', 'success')
      loadPosts()
    } else {
      props.notify('删除失败', 'error')
    }
  } catch {
    props.notify('删除失败', 'error')
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
  padding: 24px;
}
.center-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.center-header h2 {
  margin: 0;
}
.center-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 12px;
}
.center-tabs button {
  padding: 8px 16px;
  border: none;
  background: none;
  cursor: pointer;
  border-radius: 6px;
  font-size: 14px;
  color: #6b7280;
}
.center-tabs button.active {
  background: #eff6ff;
  color: #2563eb;
  font-weight: 500;
}
.my-posts-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.post-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.post-card:hover {
  border-color: #93c5fd;
}
.post-card-body {
  flex: 1;
  min-width: 0;
}
.post-card-body h3 {
  margin: 0 0 6px;
  font-size: 15px;
  color: #111827;
}
.post-summary {
  margin: 0 0 8px;
  font-size: 13px;
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.post-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #9ca3af;
}
.post-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.empty-state {
  text-align: center;
  color: #9ca3af;
  padding: 40px 0;
}
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
  font-size: 13px;
}
.loading-panel {
  text-align: center;
  padding: 40px 0;
  color: #9ca3af;
}
</style>
