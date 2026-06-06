<template>
  <section class="utility-panel type-panel">
    <div class="section-toolbar">
      <div class="filters">
        <select v-model="filters.category">
          <option value="">全部分类</option>
          <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
        </select>
        <input v-model.trim="filters.name" placeholder="软件名称" @keyup.enter="$emit('applyFilters')" />
        <button type="button" @click="$emit('applyFilters')">查询</button>
      </div>
      <div class="actions">
        <slot name="actions" />
      </div>
    </div>
    <div class="list-panel type-list-panel">
      <div class="type-list">
        <article v-for="type in types" :key="type.id" class="type-item">
          <div>
            <strong>{{ type.category }} / {{ type.name }}</strong>
            <p>{{ type.description || '暂无说明' }}</p>
          </div>
          <span :class="['status', type.active ? 'ok' : 'off']">{{ type.active ? '启用' : '停用' }}</span>
          <button class="ghost" @click="$emit('editType', type)">编辑</button>
          <button class="danger" @click="$emit('deleteType', type)">删除</button>
        </article>
      </div>
      <Pagination :page="pageInfo" @change="(p) => $emit('changePage', p)" />
    </div>
  </section>
</template>

<script setup>
import Pagination from '../../components/Pagination.vue'

defineProps({
  types: { type: Array, default: () => [] },
  categories: { type: Array, default: () => [] },
  filters: { type: Object, required: true },
  pageInfo: { type: Object, default: () => ({}) }
})
defineEmits(['applyFilters', 'editType', 'deleteType', 'changePage'])
</script>
