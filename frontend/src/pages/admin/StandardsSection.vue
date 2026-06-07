<template>
  <section class="utility-panel type-panel">
    <template v-if="!selectedStandard">
      <div class="section-toolbar">
        <div class="filters">
          <select v-model="filters.category" @change="$emit('filterCategoryChange')">
            <option value="">软件分类</option>
            <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
          </select>
        </div>
        <div class="actions">
          <slot name="actions" />
        </div>
      </div>
      <div class="standards-table-wrap">
        <table class="standards-table">
          <thead>
            <tr>
              <th>名称</th>
              <th>软件类型</th>
              <th>版本</th>
              <th>标准版本</th>
              <th>状态</th>
              <th>编码</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="doc in standards" :key="doc.id">
              <td><button class="link-btn" @click="$emit('openDetail', doc)">{{ doc.software || '-' }}</button></td>
              <td>{{ doc.category || '-' }}</td>
              <td>{{ doc.softwareVersion || '-' }}</td>
              <td>{{ doc.version || '-' }}</td>
              <td><span :class="['status', doc._statusClass || statusClass(doc.status)]">{{ doc.statusLabel || statusLabel(doc.status) }}</span></td>
              <td>{{ doc.code || '-' }}</td>
              <td class="table-actions">
                <button class="ghost" @click="$emit('openDetail', doc)">详情</button>
                <button v-if="doc.canEdit" class="ghost" @click="$emit('editStandard', doc)">编辑</button>
                <button v-if="doc.availableActions?.includes('submit-review')" class="ghost" @click="$emit('submitReview', doc)">提交审核</button>
                <button v-if="doc.availableActions?.includes('start-modify')" class="ghost" @click="$emit('startModify', doc)">开始修改</button>
                <button v-if="doc.availableActions?.includes('cancel-modify')" class="ghost" @click="$emit('cancelModify', doc)">取消修改</button>
                <button v-if="doc.status === 'PUBLISHED'" class="ghost" @click="$emit('revisionHistory', doc)">修订历史</button>
                <button v-if="doc.availableActions?.includes('delete')" class="ghost danger" @click="$emit('deleteStandard', doc)">删除</button>
              </td>
            </tr>
            <tr v-if="standards.length === 0">
              <td colspan="7" class="empty-state">暂无标准记录</td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination :page="pageInfo" @change="(p) => $emit('changePage', p)" />
    </template>

    <template v-else>
      <div class="section-toolbar">
        <div class="filters">
          <span class="detail-title">{{ selectedStandard.category || '-' }} / {{ selectedStandard.software || '-' }} · V{{ selectedStandard.version || '-' }}</span>
        </div>
        <div class="actions">
          <button type="button" class="ghost" @click="$emit('backToList')">返回列表</button>
          <button type="button" class="ghost" @click="$emit('downloadTemplate')">下载模板</button>
          <button v-if="selectedStandard.status !== 'PUBLISHED'" type="button" class="ghost" @click="$emit('importParams')">批量导入</button>
          <button v-if="selectedStandard.status !== 'PUBLISHED'" type="button" @click="$emit('createParam')">新增参数</button>
        </div>
      </div>
      <div class="list-panel">
        <div class="table-wrap">
          <table class="resource-table">
            <thead>
              <tr>
                <th>参数编码</th>
                <th>参数名称</th>
                <th>参数值</th>
                <th>分类</th>
                <th>说明</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="param in parameters" :key="param.id">
                <td>
                  {{ param.code }}
                  <span v-if="param.deploymentStandard" class="status ok deployment-badge">部署标准</span>
                </td>
                <td>{{ param.name }}</td>
                <td>{{ param.value }}</td>
                <td>{{ param.category || '-' }}</td>
                <td>{{ param.description || '-' }}</td>
                <td class="row-actions">
                  <button class="ghost" @click="$emit('copyParam', param)">复制占位符</button>
                  <button v-if="selectedStandard.status !== 'PUBLISHED'" class="ghost" @click="$emit('editParam', param)">编辑</button>
                </td>
              </tr>
              <tr v-if="parameters.length === 0">
                <td colspan="6" class="empty-state">该标准暂未配置参数</td>
              </tr>
            </tbody>
          </table>
        </div>
        <Pagination :page="paramPageInfo" @change="(p) => $emit('changeParamPage', p)" />
      </div>
    </template>
  </section>
</template>

<script setup>
import { statusLabel } from '../../utils'
import Pagination from '../../components/Pagination.vue'

defineProps({
  standards: { type: Array, default: () => [] },
  categories: { type: Array, default: () => [] },
  filters: { type: Object, required: true },
  pageInfo: { type: Object, default: () => ({}) },
  selectedStandard: { type: Object, default: null },
  parameters: { type: Array, default: () => [] },
  paramPageInfo: { type: Object, default: () => ({}) }
})
defineEmits(['filterCategoryChange', 'openDetail', 'editStandard', 'submitReview', 'startModify', 'cancelModify', 'revisionHistory', 'deleteStandard', 'changePage', 'backToList', 'downloadTemplate', 'importParams', 'createParam', 'copyParam', 'editParam', 'changeParamPage'])

function statusClass(s) {
  if (s === 'DRAFT') return 'off'
  if (s === 'PENDING_REVIEW') return 'warn'
  if (s === 'PUBLISHED') return 'ok'
  if (s === 'MODIFYING') return 'warn'
  return ''
}
</script>
