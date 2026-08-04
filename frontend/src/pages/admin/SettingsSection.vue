<template>
  <section class="utility-panel type-panel">
    <div class="settings-panel">
      <h3>模块开关</h3>
      <div class="setting-item">
        <label class="setting-label">
          <input type="checkbox" v-model="settings['knowledge-enabled']" true-value="true" false-value="false" />
          <span>知识库模块</span>
        </label>
        <p class="setting-desc">开启后用户可使用知识库上传文档、语义检索等功能</p>
      </div>
      <div class="setting-item">
        <label class="setting-label">
          <input type="checkbox" v-model="settings['diagnostics-enabled']" true-value="true" false-value="false" />
          <span>智能排查模块</span>
        </label>
        <p class="setting-desc">开启后用户可使用 AI 智能排查和运维助手功能</p>
      </div>
      <div class="setting-item">
      </div>
      <div class="form-actions" style="margin-top:20px">
        <button @click="$emit('save')">保存设置</button>
      </div>
    </div>
  </section>
</template>

<script setup>
defineProps({ settings: Object })
defineEmits(['save'])
</script>
