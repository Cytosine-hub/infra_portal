<template>
  <div>
    <div class="section-toolbar">
      <div class="filters">
        <input v-model.trim="filters.keyword" placeholder="搜索资源" @keyup.enter="$emit('search')" />
        <input v-model.trim="filters.platform" placeholder="平台" @keyup.enter="$emit('search')" />
        <select v-model="filters.published" @change="$emit('search')">
          <option value="">全部状态</option>
          <option value="true">已发布</option>
          <option value="false">未发布</option>
        </select>
        <button @click="$emit('search')">查询</button>
      </div>
      <div class="actions">
        <slot name="actions" />
      </div>
    </div>

    <div class="list-panel">
      <div class="table-wrap">
        <table class="resource-table">
          <colgroup>
            <col class="resource-name" />
            <col class="resource-version" />
            <col class="resource-platform" />
            <col class="resource-status" />
            <col class="resource-standard" />
            <col class="resource-file" />
            <col class="resource-downloads" />
            <col class="resource-actions" />
          </colgroup>
          <thead>
            <tr>
              <th>名称</th>
              <th>版本</th>
              <th>平台</th>
              <th>状态</th>
              <th>关联标准</th>
              <th>文件</th>
              <th>下载</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="release in releases" :key="release.id">
              <td :title="release.middlewareName">{{ release.middlewareName }}</td>
              <td :title="release.version">{{ release.version }}</td>
              <td :title="release.platform || '-'">{{ release.platform || '-' }}</td>
              <td>
                <span :class="['status', release.published ? 'ok' : 'off']">{{ release.published ? '已发布' : '未发布' }}</span>
                <span v-if="release.standardPackage" :class="['status', pkgStatusClass(release.packageStatus)]" style="margin-left:4px">{{ pkgStatusLabel(release.packageStatus) }}</span>
              </td>
              <td :title="release.standardDocumentId ? getStandardLabel(release.standardDocumentId) : '-'">{{ release.standardDocumentId ? getStandardLabel(release.standardDocumentId) : '-' }}</td>
              <td :title="release.originalFileName">
                {{ release.originalFileName }}
                <span v-if="release.standardPackage && release.packageStatus === 'FAILED'" class="package-error-hint" :title="release.packageError">⚠</span>
              </td>
              <td>{{ release.downloadCount }}</td>
              <td class="row-actions">
                <button class="ghost" :disabled="release.published" :title="release.published ? '已发布资源不能编辑，请先下架' : '编辑'" @click="$emit('edit', release)">编辑</button>
                <button class="ghost" @click="$emit('togglePublish', release)">{{ release.published ? '下架' : '发布' }}</button>
                <button v-if="release.standardPackage && (release.packageStatus === 'FAILED' || release.packageStatus === 'SUCCESS')" class="ghost" @click="$emit('regenerate', release)">重新生成</button>
                <button class="danger" :disabled="release.published" :title="release.published ? '已发布资源不能删除，请先下架' : '删除'" @click="$emit('delete', release)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination :page="pageInfo" @change="(p) => $emit('changePage', p)" />
    </div>
  </div>
</template>

<script setup>
import Pagination from '../../components/Pagination.vue'

defineProps({
  releases: { type: Array, default: () => [] },
  filters: { type: Object, required: true },
  pageInfo: { type: Object, default: () => ({}) },
  getStandardLabel: { type: Function, default: () => '' }
})
defineEmits(['search', 'edit', 'togglePublish', 'regenerate', 'delete', 'changePage'])

function pkgStatusLabel(s) {
  if (s === 'SUCCESS') return '已打包'
  if (s === 'FAILED') return '打包失败'
  if (s === 'BUILDING') return '打包中'
  return s || ''
}
function pkgStatusClass(s) {
  if (s === 'SUCCESS') return 'ok'
  if (s === 'FAILED') return 'off'
  if (s === 'BUILDING') return 'warn'
  return ''
}
</script>
