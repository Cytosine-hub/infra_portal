<template>
  <section class="knowledge-base">
    <PageHeader eyebrow="基础设施知识库" title="知识库" />

    <TabNav v-model="currentTab" :tabs="tabs" aria-label="知识库功能" />

    <div class="tab-body">
      <SearchTab v-if="currentTab === 'search'" :notify="notify" />
      <DocumentsTab v-else-if="currentTab === 'documents'" :notify="notify" :confirm="confirm" />
      <ExperienceTab v-else-if="currentTab === 'experience'" :notify="notify" :confirm="confirm" />
      <GraphTab v-else-if="currentTab === 'graph'" :notify="notify" />
      <HealthTab v-else-if="currentTab === 'health'" :notify="notify" />
    </div>
  </section>
</template>

<script setup>
import { defineAsyncComponent, ref } from 'vue'
import PageHeader from './ui/PageHeader.vue'
import TabNav from './ui/TabNav.vue'
import SearchTab from './knowledge/SearchTab.vue'
import DocumentsTab from './knowledge/DocumentsTab.vue'
import ExperienceTab from './knowledge/ExperienceTab.vue'
import HealthTab from './knowledge/HealthTab.vue'

// 图谱依赖 force-graph，体积较大且仅在该标签用到，按需加载
const GraphTab = defineAsyncComponent(() => import('./knowledge/GraphTab.vue'))

defineProps({
  auth: { type: Object, default: null },
  notify: { type: Function, required: true },
  confirm: { type: Function, default: (message, onConfirm) => onConfirm?.() }
})

const tabs = [
  { key: 'search', icon: '🔍', label: '检索' },
  { key: 'documents', icon: '📄', label: '文档' },
  { key: 'experience', icon: '📗', label: '经验沉淀' },
  { key: 'graph', icon: '🌐', label: '图谱' },
  { key: 'health', icon: '🩺', label: '健康度' }
]

const currentTab = ref('search')
</script>

<style scoped>
.knowledge-base {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.tab-body {
  min-height: 400px;
}
</style>
