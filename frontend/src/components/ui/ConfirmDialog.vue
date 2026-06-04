<template>
  <BaseModal :modelValue="!!modelValue" @update:modelValue="$emit('update:modelValue', null)" :width="width">
    <div class="confirm-content">
      <div v-if="icon" class="confirm-icon">{{ icon }}</div>
      <p class="confirm-message">{{ modelValue?.message || '' }}</p>
    </div>
    <template #footer>
      <BaseButton variant="ghost" @click="$emit('update:modelValue', null)">取消</BaseButton>
      <BaseButton :variant="variant" @click="handleConfirm">确认</BaseButton>
    </template>
  </BaseModal>
</template>

<script setup>
import BaseModal from './BaseModal.vue'
import BaseButton from './BaseButton.vue'

const props = defineProps({
  modelValue: { type: Object, default: null }, // { message, onConfirm }
  variant: { type: String, default: 'primary' },
  icon: { type: String, default: '' },
  width: { type: String, default: '400px' }
})
const emit = defineEmits(['update:modelValue'])

function handleConfirm() {
  if (props.modelValue?.onConfirm) props.modelValue.onConfirm()
  emit('update:modelValue', null)
}
</script>

<style scoped>
.confirm-content { text-align: center; padding: var(--space-lg) 0; }
.confirm-icon { font-size: 48px; margin-bottom: var(--space-md); }
.confirm-message { margin: 0; font-size: var(--text-lg); line-height: var(--leading-relaxed); color: var(--color-text); }
</style>
