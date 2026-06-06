<template>
  <section class="standards-layout">
    <!-- 左侧树形目录 -->
    <aside class="standards-tree">
      <div class="tree-header">
        <h3>参数标准</h3>
        <slot name="actions" />
      </div>
      <div class="tree-filters">
        <select v-model="filters.category" @change="$emit('filterCategoryChange')">
          <option value="">全部分类</option>
          <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
        </select>
      </div>
      <div class="tree-content">
        <div v-for="std in treeData" :key="std.id" class="tree-group">
          <div :class="['tree-item', 'tree-parent', { active: selectedStandard?.id === std.id }]" @click="$emit('openDetail', std)">
            <span class="tree-toggle" @click.stop="toggleExpand(std.id)">{{ expanded[std.id] ? '▼' : '▶' }}</span>
            <span class="tree-label">{{ std.software || '-' }} / {{ std.softwareVersion || '-' }}</span>
            <span :class="['status', statusClass(std.status)]">{{ statusLabel(std.status) }}</span>
          </div>
          <div v-if="expanded[std.id] && std.relatedDocuments?.length" class="tree-children">
            <div v-for="doc in std.relatedDocuments" :key="doc.id"
              :class="['tree-item', 'tree-child', { active: selectedStandard?.id === doc.id }]"
              @click="$emit('openDetail', doc)">
              <span class="tree-label">{{ doc.title || '未命名' }}</span>
              <span :class="['status', statusClass(doc.status)]">{{ statusLabel(doc.status) }}</span>
            </div>
          </div>
        </div>
        <p v-if="treeData.length === 0" class="tree-empty">暂无标准</p>
      </div>
    </aside>

    <!-- 右侧内容区 -->
    <div class="standards-content">
      <template v-if="!selectedStandard">
        <div class="empty-hint">请从左侧选择一个标准查看详情</div>
      </template>

      <template v-else>
        <div class="content-header">
          <div>
            <h3>{{ selectedStandard.category || '-' }} / {{ selectedStandard.software || '-' }}</h3>
            <p class="muted">软件版本：{{ selectedStandard.softwareVersion || '-' }} · 版本：{{ selectedStandard.version || '-' }}</p>
          </div>
          <div class="content-actions">
            <button type="button" class="ghost" @click="$emit('backToList')">返回列表</button>
            <button type="button" class="ghost" @click="$emit('downloadTemplate')">下载模板</button>
            <button v-if="selectedStandard.status !== 'PUBLISHED'" type="button" class="ghost" @click="$emit('importParams')">批量导入</button>
            <button v-if="selectedStandard.status !== 'PUBLISHED'" type="button" @click="$emit('createParam')">新增参数</button>
          </div>
        </div>
        <div class="list-panel">
          <div class="type-list">
            <article v-for="param in parameters" :key="param.id" class="parameter-item">
              <div>
                <strong>{{ param.code }}</strong>
                <span v-if="param.deploymentStandard" class="status ok">部署标准</span>
                <p>{{ param.name }} = {{ param.value }}</p>
                <p>{{ param.category || '未分类' }} · {{ param.description || '暂无说明' }}</p>
              </div>
              <button class="ghost" @click="$emit('copyParam', param)">复制占位符</button>
              <button v-if="selectedStandard.status !== 'PUBLISHED'" class="ghost" @click="$emit('editParam', param)">编辑</button>
            </article>
            <p v-if="parameters.length === 0" class="empty-state">该标准暂未配置参数。</p>
          </div>
        </div>
      </template>
    </div>
  </section>
</template>

<script setup>
import { reactive, computed } from 'vue'
import { statusLabel } from '../../utils'

const props = defineProps({
  parameterStandards: { type: Array, default: () => [] },
  categories: { type: Array, default: () => [] },
  filters: { type: Object, required: true },
  selectedStandard: { type: Object, default: null },
  parameters: { type: Array, default: () => [] }
})
defineEmits(['filterCategoryChange', 'openDetail', 'backToList', 'downloadTemplate', 'importParams', 'createParam', 'copyParam', 'editParam'])

const treeData = computed(() => {
  const category = props.filters.category
  return props.parameterStandards
    .filter(s => !category || s.category === category)
})

/** @type {Record<string, boolean>} */
const expanded = reactive({})

function toggleExpand(id) {
  expanded[id] = !expanded[id]
}

function statusClass(s) {
  if (s === 'DRAFT') return 'off'
  if (s === 'PENDING_REVIEW') return 'warn'
  if (s === 'PUBLISHED') return 'ok'
  if (s === 'MODIFYING') return 'warn'
  return ''
}
</script>

<style scoped>
.standards-layout {
  display: flex; height: 100%; overflow: hidden;
}
.standards-tree {
  width: 280px; border-right: 1px solid var(--color-border);
  display: flex; flex-direction: column; flex-shrink: 0; overflow: hidden;
}
.tree-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border);
}
.tree-header h3 { margin: 0; font-size: var(--text-base); }
.tree-filters {
  padding: var(--space-sm) var(--space-lg);
  border-bottom: 1px solid var(--color-border);
}
.tree-filters select { width: 100%; }
.tree-content {
  flex: 1; overflow-y: auto; padding: var(--space-sm) 0;
}
.tree-item {
  display: flex; align-items: center; gap: var(--space-sm);
  padding: var(--space-sm) var(--space-lg);
  cursor: pointer; font-size: var(--text-sm);
  transition: background var(--transition-fast);
}
.tree-item:hover { background: var(--color-bg-tertiary); }
.tree-item.active { background: var(--color-primary-light); color: var(--color-primary); }
.tree-parent { font-weight: 500; }
.tree-child { padding-left: calc(var(--space-lg) + 20px); font-weight: 400; }
.tree-toggle {
  width: 16px; text-align: center; font-size: var(--text-xs); color: var(--color-text-tertiary);
  flex-shrink: 0; cursor: pointer;
}
.tree-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tree-empty { padding: var(--space-lg); color: var(--color-text-tertiary); font-size: var(--text-sm); }
.standards-content {
  flex: 1; display: flex; flex-direction: column; overflow: hidden; min-width: 0;
}
.empty-hint {
  display: flex; align-items: center; justify-content: center;
  height: 100%; color: var(--color-text-tertiary);
}
.content-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--space-lg); border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.content-header h3 { margin: 0; }
.content-actions { display: flex; gap: var(--space-sm); }
.list-panel { flex: 1; overflow-y: auto; padding: var(--space-lg); }
.status { font-size: var(--text-xs); margin-left: var(--space-sm); }
</style>
