<template>
  <section class="workspace job-workspace">
    <main class="job-workspace-main">
      <slot v-if="feature" :feature="feature" />
      <div v-else class="job-feature-grid">
        <article v-for="item in job.features" :key="item.id" class="job-feature-card" @click="openFeature(item.id)">
          <span>{{ item.icon }}</span>
          <div><h3>{{ item.name }}</h3><p>{{ item.description }}</p></div>
          <BaseButton variant="ghost">进入</BaseButton>
        </article>
        <EmptyState v-if="job.features.length === 0" message="该类别能力正在建设中，可按独立接口配置接入后端服务。" />
      </div>
    </main>
  </section>
</template>

<script setup>
import BaseButton from '../../components/ui/BaseButton.vue'
import EmptyState from '../../components/ui/EmptyState.vue'

const props = defineProps({ job: { type: Object, required: true }, feature: { type: String, default: null } })
const emit = defineEmits(['navigate'])

function openFeature(featureId) {
  emit('navigate', `jobs/${props.job.id}/${featureId}`)
}
</script>

<style scoped>
.job-workspace { padding-top: var(--space-xl); }
.job-workspace-main { display: grid; align-content: start; gap: var(--space-xl); min-width: 0; }
.job-feature-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-lg); }
.job-feature-card { display: grid; grid-template-columns: auto 1fr auto; gap: var(--space-md); align-items: center; border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: var(--space-xl); background: var(--color-bg); cursor: pointer; }
.job-feature-card:hover { border-color: var(--color-primary-200); box-shadow: var(--shadow-md); }
.job-feature-card h3, .job-feature-card p { margin: 0; }
.job-feature-card p { margin-top: var(--space-xs); color: var(--color-text-secondary); }
.job-feature-card > span { font-size: var(--text-2xl); }
@media (max-width: 760px) {
  .job-feature-grid { grid-template-columns: 1fr; }
}
</style>
