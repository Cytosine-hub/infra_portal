<template>
  <section class="utility-panel type-panel">
    <div class="section-toolbar">
      <div class="filters"></div>
      <div class="actions">
        <slot name="actions" />
      </div>
    </div>
    <div class="list-panel type-list-panel">
      <div class="type-list">
        <article v-for="user in users" :key="user.id" class="parameter-item document-item">
          <div>
            <strong>{{ user.displayName || user.username }}</strong>
            <p>账号：{{ user.username }} · {{ user.role }} · {{ formatDate(user.createdAt) }}</p>
          </div>
          <button class="ghost" @click="$emit('changeRole', user)">改角色</button>
          <button class="ghost" @click="$emit('resetPassword', user)">重置密码</button>
          <button v-if="user.role !== '系统管理员' || adminCount > 1" class="ghost danger" @click="$emit('deleteUser', user)">删除</button>
        </article>
        <p v-if="users.length === 0" class="empty-state">暂无用户。</p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { formatDate } from '../../utils'

const props = defineProps({ users: { type: Array, default: () => [] } })
defineEmits(['changeRole', 'resetPassword', 'deleteUser'])

const adminCount = computed(() => props.users.filter(u => u.role === '系统管理员').length)
</script>
