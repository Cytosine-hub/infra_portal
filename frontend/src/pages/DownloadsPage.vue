<template>
  <section class="workspace">
    <div class="toolbar">
      <div class="filters">
        <input v-model.trim="filters.keyword" placeholder="搜索名称、版本、说明" @keyup.enter="loadData()" />
        <input v-model.trim="filters.platform" placeholder="平台" @keyup.enter="loadData()" />
        <button @click="loadData()">查询</button>
      </div>
    </div>

    <div v-if="selectedRelease" class="detail-layout">
      <button class="ghost" @click="selectedRelease = null">返回列表</button>
      <article class="detail-panel">
        <div>
          <p class="eyebrow">版本详情</p>
          <h2>{{ selectedRelease.middlewareName }}</h2>
          <p class="muted">{{ selectedRelease.version }} · {{ selectedRelease.platform || '通用平台' }}</p>
        </div>
        <p class="description">{{ selectedRelease.description || '暂无版本说明。' }}</p>
        <dl class="meta-grid">
          <div><dt>发布日期</dt><dd>{{ selectedRelease.releasedAt || '-' }}</dd></div>
          <div><dt>文件名</dt><dd>{{ selectedRelease.originalFileName }}</dd></div>
          <div><dt>文件大小</dt><dd>{{ formatBytes(selectedRelease.fileSize) }}</dd></div>
          <div><dt>下载次数</dt><dd>{{ selectedRelease.downloadCount }}</dd></div>
        </dl>
        <a class="primary-link" href="#" @click.prevent="handleDownload(selectedRelease.downloadUrl, selectedRelease.originalFileName)">下载文件</a>
      </article>
    </div>

    <template v-else>
      <div class="release-list-container">
        <div class="release-grid">
          <article v-for="release in page.content" :key="release.downloadToken" class="release-card">
            <div>
              <h2>{{ release.middlewareName }}</h2>
              <p>{{ release.version }} · {{ release.platform || '通用平台' }}</p>
            </div>
            <p class="description">{{ release.description || '暂无版本说明。' }}</p>
            <div class="card-footer">
              <span>{{ formatBytes(release.fileSize) }}</span>
              <div class="card-actions">
                <button class="ghost" @click="openDetail(release.downloadToken)">详情</button>
                <a class="download-button" href="#" @click.prevent="handleDownload(release.downloadUrl, release.originalFileName)">下载</a>
              </div>
            </div>
          </article>
        </div>
        <div class="release-pagination">
          <Pagination :page="page" @change="changePage" />
        </div>
      </div>
    </template>
  </section>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { request } from '../api'
import { formatBytes } from '../utils'
import Pagination from '../components/Pagination.vue'

const filters = reactive({ keyword: '', platform: '', page: 0, size: 12 })
const page = reactive({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0, first: true, last: true })
const selectedRelease = ref(null)

async function loadData() {
  const query = new URLSearchParams(filters).toString()
  const data = await request(`/api/public/releases?${query}`, { token: null })
  Object.assign(page, data)
  selectedRelease.value = null
}

async function openDetail(token) {
  selectedRelease.value = await request(`/api/public/releases/${token}`, { token: null })
}

function changePage(p) {
  filters.page = p
  loadData()
}

async function handleDownload(url, fileName) {
  try {
    const res = await fetch(url)
    if (!res.ok) throw new Error('下载失败')
    const blob = await res.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = fileName || 'download'
    a.click()
    URL.revokeObjectURL(a.href)
  } catch {
    // fallback: 直接打开链接
    window.open(url, '_blank')
  }
}

onMounted(loadData)
</script>

<style scoped>
.release-list-container {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 180px);
}
.release-list-container .release-grid {
  flex: 1;
}
.release-pagination {
  display: flex;
  justify-content: flex-end;
  padding: var(--space-md) 0;
  position: sticky;
  bottom: 0;
  background: var(--color-bg);
}
</style>
