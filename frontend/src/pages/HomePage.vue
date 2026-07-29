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
        <div class="portal-icon" :class="`module-icon-${feature.id}`">
          <ModuleIcon :name="feature.id" :label="feature.title" />
        </div>
        <div><h3>{{ feature.title }}</h3><p>{{ feature.description }}</p></div>
        <BaseButton variant="primary">进入</BaseButton>
      </article>
    </div>

    <div class="portal-grid portal-jobs-grid">
      <article v-for="job in jobModules" :key="job.id" class="portal-card portal-job-card" @click="$emit('navigate', `jobs/${job.id}`)">
        <div class="portal-icon" :class="`module-icon-${job.id}`">
          <ModuleIcon :name="job.id" :label="job.shortName" />
        </div>
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
import ModuleIcon from '../components/ui/ModuleIcon.vue'

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

<style scoped>
/* 模块图标底座：固定正方形不被压缩，内部图标随底座等比缩放，窄屏下同样不变形 */
.portal-card .portal-icon {
  box-sizing: border-box;
  width: var(--space-3xl);
  height: var(--space-3xl);
  aspect-ratio: 1;
  flex: 0 0 auto;
  padding: var(--space-sm);
  border-radius: var(--radius-md);
  background: var(--color-primary-light);
  color: var(--color-primary);
}

/* 八个模块各自的图标配色，取自设计令牌，保证互相可区分 */
.portal-card .module-icon-downloads { background: var(--color-primary-light); color: var(--color-primary); }
.portal-card .module-icon-standards { background: var(--color-success-light); color: var(--color-success); }
.portal-card .module-icon-forum { background: var(--color-warning-light); color: var(--color-warning); }
.portal-card .module-icon-middleware { background: var(--color-primary-50); color: var(--color-primary-900); }
.portal-card .module-icon-database { background: var(--color-info-light); color: var(--color-info); }
.portal-card .module-icon-network { background: var(--color-primary-100); color: var(--color-primary-700); }
.portal-card .module-icon-host { background: var(--color-bg-tertiary); color: var(--color-text-secondary); }
.portal-card .module-icon-network-security { background: var(--color-danger-light); color: var(--color-danger); }
</style>
