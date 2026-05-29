<template>
  <div class="forum-page">
    <div class="forum-hero">
      <p>沉淀基础设施实践经验，支持问题讨论、方案交流和知识共享</p>
      <div class="forum-hero-bar">
        <input v-model.trim="keyword" placeholder="搜索文章标题或内容..." @keyup.enter="search" />
        <button @click="search">搜索</button>
        <button v-if="auth.token" class="primary-btn" @click="$emit('newPost')">发表文章</button>
        <button v-if="auth.token" class="ghost" @click="$emit('goMine')">个人中心</button>
      </div>
    </div>

    <div class="forum-body">
      <div class="forum-main">
        <div v-if="loading" class="loading-panel"><div class="spinner"></div><p>加载中...</p></div>
        <template v-else>
          <article v-for="post in posts" :key="post.id" class="forum-card" @click="$emit('openPost', post.id)">
            <div class="forum-card-body">
              <h3>{{ post.title }}</h3>
              <p class="forum-card-summary">{{ post.summary }}</p>
              <div class="forum-card-tags">
                <span v-for="tag in (post.tags || [])" :key="tag" class="forum-tag" @click.stop="filterByTag(tag)">{{ tag }}</span>
              </div>
            </div>
            <div class="forum-card-meta">
              <span>{{ post.authorDisplayName }}</span>
              <span>{{ formatDate(post.createdAt) }}</span>
              <span>{{ post.viewCount }} 阅读</span>
              <span>{{ post.likeCount }} 赞</span>
              <span>{{ post.commentCount }} 评论</span>
            </div>
          </article>
          <p v-if="posts.length === 0" class="empty-state">暂无文章，快来发表第一篇吧！</p>
          <div v-if="totalPages > 1" class="pagination">
            <button :disabled="page <= 0" @click="changePage(page - 1)">上一页</button>
            <span>第 {{ page + 1 }} / {{ totalPages }} 页，共 {{ totalElements }} 篇</span>
            <button :disabled="page >= totalPages - 1" @click="changePage(page + 1)">下一页</button>
          </div>
        </template>
      </div>

      <aside class="forum-sidebar">
        <div class="forum-sidebar-card">
          <h4>热门标签</h4>
          <div class="forum-tag-cloud">
            <button v-for="tag in tags" :key="tag.name" :class="['forum-tag', { active: activeTag === tag.name }]"
                    @click="filterByTag(tag.name)">
              {{ tag.name }} <small>{{ tag.postCount }}</small>
            </button>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { request } from '../api'

const props = defineProps({ auth: Object })
const emit = defineEmits(['openPost', 'newPost', 'goMine'])

const posts = ref([])
const tags = ref([])
const keyword = ref('')
const activeTag = ref('')
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const loading = ref(false)

async function loadPosts() {
  loading.value = true
  try {
    const params = new URLSearchParams({ keyword: keyword.value, tag: activeTag.value, page: page.value, size: 12 })
    const data = await request(`/api/forum/posts?${params}`, { token: null })
    posts.value = Array.isArray(data?.content) ? data.content : []
    totalPages.value = data?.totalPages || 0
    totalElements.value = data?.totalElements || 0
  } catch { posts.value = [] }
  finally { loading.value = false }
}

async function loadTags() {
  try { tags.value = await request('/api/forum/tags', { token: null }) } catch { tags.value = [] }
}

function search() { page.value = 0; loadPosts() }
function filterByTag(tag) { activeTag.value = activeTag.value === tag ? '' : tag; page.value = 0; loadPosts() }
function changePage(p) { page.value = p; loadPosts() }

function formatDate(v) { return v ? String(v).slice(0, 10) : '' }

onMounted(() => { loadPosts(); loadTags() })
watch(() => props.auth.token, () => { loadPosts() })
</script>

<style scoped>
.forum-page { }
.forum-hero {
  background: linear-gradient(120deg, #1a3650, #1a4a6e);
  color: #fff; padding: 32px 28px 24px; border-radius: 8px; margin-bottom: 20px;
}
.forum-hero h2 { margin: 0 0 6px; font-size: 26px; letter-spacing: 0; }
.forum-hero p { margin: 0 0 16px; color: #a0c4e0; }
.forum-hero-bar { display: flex; gap: 10px; }
.forum-hero-bar input { flex: 1; min-height: 40px; border-radius: 6px; border: none; padding: 0 14px; font-size: 14px; }
.forum-hero-bar input:focus { outline: none; }
.forum-hero-bar button { min-height: 40px; white-space: nowrap; }
.primary-btn { background: #e8890c; }
.primary-btn:hover { background: #c7740a; }
.forum-body { display: grid; grid-template-columns: 1fr 260px; gap: 20px; align-items: start; }
.forum-main { min-width: 0; }
.forum-card {
  border: 1px solid #e3e9f1; border-radius: 8px; padding: 16px 18px; margin-bottom: 12px;
  background: #fff; cursor: pointer; transition: box-shadow 0.15s;
}
.forum-card:hover { box-shadow: 0 4px 16px rgba(35,58,93,0.08); }
.forum-card h3 { margin: 0 0 6px; font-size: 17px; letter-spacing: 0; }
.forum-card-summary { color: #67768a; font-size: 13px; line-height: 1.6; margin: 0 0 10px; }
.forum-card-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.forum-tag {
  display: inline-block; padding: 2px 10px; border-radius: 999px; font-size: 12px;
  background: #eef2f7; color: #526071; border: none; cursor: pointer;
}
.forum-tag:hover, .forum-tag.active { background: #2356a5; color: #fff; }
.forum-card-meta { display: flex; gap: 14px; font-size: 12px; color: #8896a7; }
.forum-sidebar-card { border: 1px solid #e3e9f1; border-radius: 8px; padding: 16px; background: #fff; }
.forum-sidebar-card h4 { margin: 0 0 10px; font-size: 14px; letter-spacing: 0; }
.forum-tag-cloud { display: flex; flex-wrap: wrap; gap: 6px; }
.forum-tag-cloud .forum-tag small { color: #8896a7; margin-left: 2px; }
.pagination { display: flex; align-items: center; justify-content: center; gap: 12px; padding: 14px 0; color: #526071; }
</style>
