<template>
  <div class="graph-tab">
    <Toolbar>
      <template #filters>
        <BaseButton variant="ghost" :loading="loading" @click="load">刷新图谱</BaseButton>
        <span class="hint">{{ nodeCount }} 个节点 · {{ linkCount }} 条关系（已过滤低权重边）</span>
      </template>
    </Toolbar>

    <LoadingSpinner v-if="loading" text="构建图谱中…" />
    <EmptyState
      v-else-if="!nodeCount"
      icon="🌐"
      message="还没有可展示的知识关系。导入标准、文档或经验内容后会按软件和分类自动成图。"
    />
    <div v-show="!loading && nodeCount" ref="container" class="graph-container"></div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import ForceGraph from 'force-graph'
import { request } from '../../api.js'
import BaseButton from '../ui/BaseButton.vue'
import EmptyState from '../ui/EmptyState.vue'
import LoadingSpinner from '../ui/LoadingSpinner.vue'
import Toolbar from '../ui/Toolbar.vue'

const props = defineProps({ notify: { type: Function, required: true } })

// 后端已按 MIN_EDGE_WEIGHT 与每节点 Top-N 限边，这里再兜一层，避免同软件页面两两连边造成视觉爆炸
const MIN_WEIGHT = 3

const container = ref(null)
const loading = ref(false)
const nodeCount = ref(0)
const linkCount = ref(0)
let graph = null

const PALETTE = [
  'hsl(210 70% 55%)', 'hsl(150 60% 45%)', 'hsl(30 80% 55%)', 'hsl(280 55% 60%)',
  'hsl(350 65% 58%)', 'hsl(190 60% 45%)', 'hsl(60 60% 45%)', 'hsl(320 50% 55%)'
]

function colorOf(node) {
  const key = node.community ?? node.category ?? ''
  let hash = 0
  for (let i = 0; i < String(key).length; i += 1) {
    hash = (hash * 31 + String(key).charCodeAt(i)) % PALETTE.length
  }
  return PALETTE[hash]
}

function render(data) {
  if (!container.value) return
  if (!graph) {
    graph = ForceGraph()(container.value)
  }
  graph
    .graphData(data)
    .nodeId('id')
    .nodeLabel(n => `${n.name || n.pageId}${n.category ? ` · ${n.category}` : ''}`)
    .nodeColor(colorOf)
    .nodeRelSize(5)
    .linkWidth(l => Math.min(4, (l.weight || 1) / 2))
    .linkColor(() => 'rgba(140, 140, 160, 0.35)')
    .width(container.value.clientWidth)
    .height(container.value.clientHeight)
}

async function load() {
  loading.value = true
  try {
    const raw = await request('/api/knowledge/graph')
    const links = (raw.links || raw.edges || []).filter(l => (l.weight ?? MIN_WEIGHT) >= MIN_WEIGHT)
    const nodes = raw.nodes || []
    nodeCount.value = nodes.length
    linkCount.value = links.length
    if (nodes.length) {
      // 等 v-show 展开后容器才有尺寸，否则画布宽高为 0
      requestAnimationFrame(() => render({ nodes, links }))
    }
  } catch (error) {
    props.notify(error.message, 'error')
  } finally {
    loading.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => {
  graph?._destructor?.()
  graph = null
})
</script>

<style scoped>
.graph-tab {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  height: 100%;
}

.hint {
  color: var(--color-text-secondary);
  font-size: 0.8125rem;
}

.graph-container {
  flex: 1;
  min-height: 480px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-bg);
}
</style>
