<template>
  <section class="portal-page">
    <div class="portal-hero">
      <div class="portal-copy">
        <p class="eyebrow">统一入口</p>
        <h2>资源下载、标准发布、数据迁移与技术交流</h2>
        <p>面向基础设施运维场景，集中呈现软件资产、规范文件、迁移能力和论坛入口。</p>
      </div>
      <div class="portal-stats">
        <div>
          <strong>{{ stats.totalReleases }}</strong>
          <span>已发布资源</span>
        </div>
        <div>
          <strong>5</strong>
          <span>集成模块</span>
        </div>
      </div>
    </div>

    <div class="portal-grid portal-public-grid">
      <article v-for="feature in publicFeatures" :key="feature.id" class="portal-card" @click="$emit('navigate', feature.id)">
        <div class="portal-icon">{{ feature.icon }}</div>
        <div><h3>{{ feature.title }}</h3><p>{{ feature.description }}</p></div>
        <BaseButton variant="primary">进入</BaseButton>
      </article>
    </div>

    <div class="portal-grid portal-jobs-grid">
      <article v-for="job in jobModules" :key="job.id" class="portal-card portal-job-card" @click="$emit('navigate', `jobs/${job.id}`)">
        <div class="portal-icon tool-icon">{{ job.shortName.slice(0, 1) }}</div>
        <div><h3>{{ job.shortName }}</h3><p>{{ job.description }}</p></div>
        <BaseButton variant="ghost">进入空间</BaseButton>
      </article>
    </div>

    <section class="portal-latest">
      <div class="section-heading">
        <div>
          <h3>最新软件发布</h3>
        </div>
        <BaseButton variant="ghost" @click="$emit('navigate', 'downloads')">更多</BaseButton>
      </div>
      <div class="latest-list">
        <article v-for="release in latestReleases" :key="release.downloadToken">
          <div>
            <h4>{{ release.middlewareName }}</h4>
            <p>{{ release.version }} · {{ release.platform || '通用平台' }}</p>
          </div>
          <BaseButton variant="ghost" @click="$emit('openDetail', release.downloadToken)">详情</BaseButton>
        </article>
        <p v-if="latestReleases.length === 0" class="empty-state">暂无已发布软件资源。</p>
      </div>
    </section>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { request } from '../api'
import { publicFeatures } from '../config/portalFeatures.js'
import { jobModules } from '../modules/index.js'
import BaseButton from '../components/ui/BaseButton.vue'

defineEmits(['navigate', 'openDetail', 'notify'])

const stats = ref({ totalReleases: 0 })
const latestReleases = ref([])

async function loadData() {
  try {
    const page = await request('/api/public/releases?page=0&size=4', { token: null })
    latestReleases.value = page?.content || []
    stats.value.totalReleases = page?.totalElements || 0
  } catch {
    latestReleases.value = []
  }
}

onMounted(loadData)
</script>
