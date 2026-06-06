<template>
  <section class="utility-panel type-panel">
    <div class="section-toolbar">
      <div class="filters">
        <select v-model="filters.documentType" @change="$emit('applyFilters')">
          <option value="">全部类型</option>
          <option value="MANUAL">手册</option>
          <option value="ARTICLE">文章</option>
        </select>
        <select v-model="filters.status" @change="$emit('applyFilters')">
          <option value="">全部状态</option>
          <option value="DRAFT">草稿</option>
          <option value="PENDING_REVIEW">审核中</option>
          <option value="PUBLISHED">已发布</option>
          <option value="MODIFYING">修改中</option>
        </select>
        <input v-model.trim="filters.keyword" placeholder="搜索标题/摘要" @keyup.enter="$emit('applyFilters')" />
        <button type="button" @click="$emit('applyFilters')">查询</button>
      </div>
      <div class="actions">
        <slot name="actions" />
      </div>
    </div>
    <div class="list-panel type-list-panel">
      <div class="type-list">
        <article v-for="doc in documents" :key="doc.id" class="parameter-item document-item">
          <div>
            <strong>{{ displayTitle(doc) }}</strong>
            <p>
              <span :class="['status', doc._statusClass || statusClass(doc.status)]">{{ doc.statusLabel || statusLabel(doc.status) }}</span>
              V{{ doc.version || '-' }} · {{ doc.documentType === 'ARTICLE' ? '文章' : doc.documentType === 'MANUAL' ? '手册' : '参数标准' }}
              <span v-if="doc.reviewComment" class="review-hint" :title="doc.reviewComment">（审核意见）</span>
            </p>
            <p>关联标准：{{ getStandardLabel(doc.relatedStandardDocumentId) }}</p>
          </div>
          <button class="ghost" @click="$emit('preview', doc)">预览</button>
          <button v-if="doc.canEdit" class="ghost" @click="$emit('edit', doc)">编辑</button>
          <button v-if="doc.availableActions?.includes('submit-review')" class="ghost" @click="$emit('submitReview', doc)">提交审核</button>
          <button v-if="doc.availableActions?.includes('start-modify')" class="ghost" @click="$emit('startModify', doc)">开始修改</button>
          <button v-if="doc.availableActions?.includes('cancel-modify')" class="ghost" @click="$emit('cancelModify', doc)">取消修改</button>
          <button v-if="doc.status === 'PUBLISHED'" class="ghost" @click="$emit('revisionHistory', doc)">修订历史</button>
          <button v-if="doc.availableActions?.includes('delete')" class="ghost danger" @click="$emit('delete', doc)">删除</button>
        </article>
        <p v-if="documents.length === 0" class="empty-state">暂无文档，点击"新增文档"创建手册或文章。</p>
      </div>
      <Pagination :page="pageInfo" @change="(p) => $emit('changePage', p)" />
    </div>
  </section>
</template>

<script setup>
import { statusLabel } from '../../utils'
import Pagination from '../../components/Pagination.vue'

defineProps({
  documents: { type: Array, default: () => [] },
  filters: { type: Object, required: true },
  pageInfo: { type: Object, default: () => ({}) },
  getStandardLabel: { type: Function, default: () => '' }
})
defineEmits(['applyFilters', 'preview', 'edit', 'submitReview', 'startModify', 'cancelModify', 'revisionHistory', 'delete', 'changePage'])

function displayTitle(doc) {
  if (!doc) return ''
  return doc.title || [doc.category, doc.software, doc.softwareVersion].filter(Boolean).join(' / ') || '未命名'
}
function statusClass(s) {
  if (s === 'DRAFT') return 'off'
  if (s === 'PENDING_REVIEW') return 'warn'
  if (s === 'PUBLISHED') return 'ok'
  if (s === 'MODIFYING') return 'warn'
  if (s === 'REJECTED') return 'off'
  return ''
}
</script>
