<template>
  <section class="standard-category-section">
    <div class="standard-category-head">
      <div class="standard-category-title">
        <h2>{{ group.category }}</h2>
        <span class="standard-category-badge">{{ group.standards.length }} 项标准 · {{ group.documentCount }} 份文档</span>
      </div>
      <p class="standard-category-meta">{{ group.softwareSummary }}</p>
    </div>
    <div class="standard-card-grid">
      <article v-for="standard in group.standards" :key="standard.id" class="standard-row standard-card">
        <div class="standard-card-head">
          <button type="button" class="standard-title-link" :title="displayTitle(standard)"
            @click="emit('open-standard', standard.id)">
            {{ displayTitle(standard) }}
          </button>
          <span v-if="standard.softwareVersion" class="standard-card-version">{{ standard.softwareVersion }}</span>
        </div>
        <dl class="standard-card-meta">
          <div v-for="field in standardMetaFields(standard)" :key="field.label" class="standard-card-meta-item">
            <dt>{{ field.label }}</dt>
            <dd>{{ field.value }}</dd>
          </div>
        </dl>
        <div class="standard-card-docs">
          <button v-for="doc in visibleDocuments(standard)" :key="doc.id" type="button"
            class="related-document-link" :title="doc.title || '未命名文档'"
            @click="emit('open-document', standard, doc.id)">
            <span class="related-document-title">{{ doc.title || '未命名文档' }}</span>
            <span class="related-document-meta">
              <span v-for="field in documentMetaFields(doc)" :key="field.label" class="related-document-meta-item">
                {{ field.label }} {{ field.value }}
              </span>
            </span>
          </button>
          <p v-if="!publishedDocuments(standard).length" class="standard-card-docs-empty">暂无已发布标准文档</p>
        </div>
        <div class="standard-card-actions">
          <span>共 {{ publishedDocuments(standard).length }} 份标准文档</span>
          <button v-if="publishedDocuments(standard).length > DOC_PREVIEW_LIMIT" type="button"
            class="standard-card-more" @click="emit('toggle-documents', standard.id)">
            {{ expandedDocs[standard.id] ? '收起' : '查看更多' }}
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import {
  DOC_PREVIEW_LIMIT,
  displayTitle,
  documentMetaFields,
  publishedDocuments,
  standardMetaFields
} from './standardsCatalog.js'

defineOptions({ name: 'StandardCategorySection' })

const props = defineProps({
  group: { type: Object, required: true },
  expandedDocs: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['open-standard', 'open-document', 'toggle-documents'])

function visibleDocuments(standard) {
  const docs = publishedDocuments(standard)
  return props.expandedDocs[standard.id] ? docs : docs.slice(0, DOC_PREVIEW_LIMIT)
}
</script>

<style scoped>
.standard-category-section {
  display: flex; flex-direction: column; gap: var(--space-lg);
  border: 1px solid var(--color-border); border-radius: var(--radius-lg);
  padding: var(--space-xl); background: var(--color-bg); box-shadow: var(--shadow-sm);
}
.standard-category-head {
  display: flex; align-items: center; justify-content: space-between;
  flex-wrap: wrap; gap: var(--space-sm) var(--space-lg);
  padding-bottom: var(--space-md); border-bottom: 1px solid var(--color-border);
}
.standard-category-title { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-sm); min-width: 0; }
.standard-category-title h2 { margin: 0; font-size: var(--text-2xl); letter-spacing: 0; }
.standard-category-badge {
  display: inline-flex; align-items: center; border-radius: var(--radius-full);
  padding: var(--space-2xs) var(--space-sm);
  background: var(--color-primary-light); color: var(--color-primary);
  font-size: var(--text-xs); font-weight: 700; white-space: nowrap;
}
.standard-category-meta {
  margin: 0; min-width: 0; color: var(--color-text-secondary); font-size: var(--text-sm);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.standard-card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: var(--space-lg); }
.standard-card {
  display: flex; flex-direction: column; gap: var(--space-md); min-width: 0;
  border: 1px solid var(--color-border); border-radius: var(--radius-lg);
  padding: var(--space-lg); background: var(--color-bg);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}
.standard-card:hover { border-color: var(--color-primary-100); box-shadow: var(--shadow-md); }
.standard-card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-sm); }
.standard-title-link {
  flex: 1; min-width: 0; justify-self: start;
  min-height: auto; padding: 0; border: none;
  color: var(--color-text); background: transparent;
  font-size: var(--text-lg); font-weight: 700; line-height: 1.4; text-align: left;
  overflow-wrap: anywhere; word-break: break-word; cursor: pointer;
  transition: color var(--transition-fast);
}
.standard-title-link:hover { color: var(--color-primary); background: transparent; }
.standard-card-version {
  flex: 0 0 auto; display: inline-flex; align-items: center;
  border-radius: var(--radius-sm); padding: var(--space-2xs) var(--space-sm);
  background: var(--color-success-light); color: var(--color-success);
  font-size: var(--text-xs); font-weight: 700; white-space: nowrap;
}
.standard-card-meta { display: flex; flex-wrap: wrap; gap: var(--space-xs) var(--space-md); margin: 0; }
.standard-card-meta-item { display: flex; gap: var(--space-2xs); min-width: 0; font-size: var(--text-xs); }
.standard-card-meta-item dt { color: var(--color-text-tertiary); }
.standard-card-meta-item dt::after { content: '：'; }
.standard-card-meta-item dd { margin: 0; color: var(--color-text-secondary); overflow-wrap: anywhere; }

.standard-card-docs { display: flex; flex-direction: column; gap: var(--space-sm); }
.related-document-link {
  display: flex; flex-direction: column; gap: var(--space-2xs);
  width: 100%; min-height: 34px; border: none;
  border-radius: var(--radius-sm); padding: var(--space-sm) var(--space-md);
  background: var(--color-primary-light); color: var(--color-primary);
  text-align: left; cursor: pointer;
  transition: background var(--transition-fast);
}
.related-document-link:hover { background: var(--color-primary-50); }
.related-document-link:hover .related-document-title { text-decoration: underline; }
.related-document-title {
  font-size: var(--text-base); font-weight: 600; line-height: 1.4;
  overflow-wrap: anywhere; word-break: break-word;
}
.related-document-meta {
  display: flex; flex-wrap: wrap; gap: var(--space-2xs) var(--space-sm);
  color: var(--color-text-secondary); font-size: var(--text-xs); line-height: 1.4;
}
.standard-card-docs-empty { margin: 0; color: var(--color-text-tertiary); font-size: var(--text-sm); }
.standard-card-actions {
  display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm);
  margin-top: auto; padding-top: var(--space-sm); border-top: 1px solid var(--color-border);
  color: var(--color-text-tertiary); font-size: var(--text-xs);
}
.standard-card-more {
  border: 1px solid var(--color-primary-100); border-radius: var(--radius-sm);
  padding: var(--space-xs) var(--space-md);
  background: var(--color-bg); color: var(--color-primary);
  font-size: var(--text-xs); font-weight: 700; white-space: nowrap; cursor: pointer;
}
.standard-card-more:hover { background: var(--color-primary-light); }

@media (max-width: 760px) {
  .standard-category-section { padding: var(--space-lg); }
  .standard-card-grid { grid-template-columns: 1fr; }
  .standard-category-meta { white-space: normal; }
}
</style>
