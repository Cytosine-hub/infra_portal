<template>
  <section class="forum-management-section">
    <div class="section-heading">
      <div><h3>论坛管理</h3><p>统一管理论坛内容和标签。</p></div>
    </div>
    <TabNav :model-value="activeTab" :tabs="tabs" aria-label="论坛管理功能"
      @update:model-value="activeTab = $event" />
    <ForumTagsSection v-if="activeTab === 'tags'"
      :is-sys-admin="isSysAdmin" :managed-category="managedCategory" />
    <EmptyState v-else :message="`${tabs.find((tab) => tab.key === activeTab)?.label || '该功能'}暂未开放`" />
  </section>
</template>

<script setup>
import { ref } from 'vue'
import EmptyState from '../../components/ui/EmptyState.vue'
import TabNav from '../../components/ui/TabNav.vue'
import ForumTagsSection from './ForumTagsSection.vue'

const props = defineProps({
  tab: { type: String, default: 'tags' },
  isSysAdmin: { type: Boolean, default: false },
  managedCategory: { type: String, default: '' }
})
const tabs = [
  { key: 'posts', label: '帖子管理' },
  { key: 'comments', label: '评论管理' },
  { key: 'tags', label: '标签管理' }
]
const activeTab = ref(props.tab)
</script>

<style scoped>
.forum-management-section { min-height: 0; display: flex; flex-direction: column; gap: var(--space-lg); }
.section-heading { display: flex; align-items: center; justify-content: space-between; }
.section-heading h3 { margin: 0; font-size: var(--text-xl); letter-spacing: 0; }
.section-heading p { margin: var(--space-xs) 0 0; color: var(--color-text-secondary); }
</style>
