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

    <div class="portal-grid">
      <article class="portal-card primary" @click="$emit('navigate', 'downloads')">
        <div class="portal-icon">软</div>
        <div>
          <h3>软件下载</h3>
          <p>查看已发布的中间件安装包、版本说明、平台信息和下载链接。</p>
        </div>
        <button>进入下载中心</button>
      </article>

      <article class="portal-card" @click="$emit('navigate', 'standards')">
        <div class="portal-icon">标</div>
        <div>
          <h3>标准发布</h3>
          <p>集中发布基础设施规范、部署标准、运维手册和检查基线。</p>
        </div>
        <button>查看标准</button>
      </article>

      <article class="portal-card warning" @click="$emit('navigate', 'data-migration')">
        <div class="portal-icon">迁</div>
        <div>
          <h3>数据迁移</h3>
          <p>规划 MySQL、Oracle、OceanBase 等数据库的可插拔迁移能力。</p>
        </div>
        <button>查看设计</button>
      </article>

      <article class="portal-card forum" @click="$emit('navigate', 'forum')">
        <div class="portal-icon">论</div>
        <div>
          <h3>infra论坛</h3>
          <p>沉淀基础设施实践经验，支持问题讨论、方案交流和知识共享。</p>
        </div>
        <button>进入论坛</button>
      </article>
    </div>

    <div class="section-heading portal-tools-heading">
      <div>
        <p class="eyebrow">Tools</p>
        <h3>常用工具</h3>
      </div>
    </div>
    <div class="portal-grid portal-tools-grid">
      <article class="portal-card portal-tool">
        <div class="portal-icon tool-icon">监</div>
        <div><h3>监控面板</h3><p>服务器性能、中间件状态、告警信息统一查看。</p></div>
        <button class="ghost">查看</button>
      </article>
      <article class="portal-card portal-tool" @click="$emit('navigate', 'commands')">
        <div class="portal-icon tool-icon">令</div>
        <div><h3>常用命令</h3><p>中间件常用运维命令速查手册。</p></div>
        <button class="ghost">查看</button>
      </article>
    </div>

    <section class="portal-latest">
      <div class="section-heading">
        <div>
          <p class="eyebrow">Latest</p>
          <h3>最新软件发布</h3>
        </div>
        <button class="ghost" @click="$emit('navigate', 'downloads')">更多</button>
      </div>
      <div class="latest-list">
        <article v-for="release in latestReleases" :key="release.downloadToken">
          <div>
            <h4>{{ release.middlewareName }}</h4>
            <p>{{ release.version }} · {{ release.platform || '通用平台' }}</p>
          </div>
          <button class="ghost" @click="$emit('openDetail', release.downloadToken)">详情</button>
        </article>
        <p v-if="latestReleases.length === 0" class="empty-state">暂无已发布软件资源。</p>
      </div>
    </section>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { request } from '../api'

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
