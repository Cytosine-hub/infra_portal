<template>
  <FormModal v-model="admin.editing.value" :title="admin.releaseForm.id ? '编辑资源' : '新增资源'" width="700px" @submit="admin.saveRelease">
    <div class="form-grid">
      <label>分类
        <select v-model="admin.releaseForm.category" required @change="admin.releaseForm.softwareTypeId = ''">
          <option value="">请选择分类</option>
          <option v-for="category in admin.releaseCategoryOptions.value" :key="category" :value="category">{{ category }}</option>
        </select>
      </label>
      <label>软件
        <select v-model="admin.releaseForm.softwareTypeId" :disabled="!admin.releaseForm.category" required>
          <option value="">请选择软件</option>
          <option v-for="type in admin.releaseSoftwareOptions.value" :key="type.id" :value="type.id">{{ type.name }}</option>
        </select>
      </label>
      <label>版本号<input v-model.trim="admin.releaseForm.version" required maxlength="60" /></label>
      <label>平台<input v-model.trim="admin.releaseForm.platform" maxlength="60" /></label>
      <label>发布日期<input v-model="admin.releaseForm.releasedAt" type="date" /></label>
      <label class="checkline"><input v-model="admin.releaseForm.published" type="checkbox" />发布</label>
      <label>关联标准
        <select v-model="admin.releaseForm.standardDocumentId" :disabled="!admin.releaseForm.category || !admin.releaseForm.softwareTypeId">
          <option :value="null">不关联</option>
          <option v-for="doc in admin.releaseStandardOptions.value" :key="doc.id" :value="doc.id">{{ admin.getStandardLabel(doc.id) }}</option>
        </select>
      </label>
      <label class="checkline"><input v-model="admin.releaseForm.standardPackage" type="checkbox" />标准包</label>
      <label v-if="admin.releaseForm.standardPackage">关联参数标准
        <select v-model="admin.releaseForm.parameterStandardId" :disabled="!admin.releaseForm.category || !admin.releaseForm.softwareTypeId">
          <option :value="null">请选择参数标准</option>
          <option v-for="ps in admin.releaseParameterStandardOptions.value" :key="ps.id" :value="ps.id">{{ ps.title }}</option>
        </select>
      </label>
      <label class="file-field">安装包
        <span class="file-control">
          <input type="file" @change="admin.handleReleaseFileChange" />
          <span class="file-button">选择文件</span>
          <span class="file-name">{{ admin.releaseForm.file?.name || admin.releaseForm.originalFileName || '未选择文件' }}</span>
        </span>
      </label>
      <label class="wide">说明<textarea v-model.trim="admin.releaseForm.description" maxlength="2000" /></label>
    </div>
    <div v-if="admin.uploading.value" class="upload-progress-bar">
      <div class="progress-track">
        <div class="progress-fill" :style="{ width: admin.uploadProgress.value + '%' }"></div>
      </div>
      <span class="progress-text">上传中 {{ admin.uploadProgress.value }}%</span>
    </div>
    <template #actions>
      <BaseButton type="submit" :loading="admin.uploading.value">{{ admin.uploading.value ? '上传中...' : '保存' }}</BaseButton>
      <BaseButton variant="ghost" @click="admin.cancelEdit()" :disabled="admin.uploading.value">取消</BaseButton>
    </template>
  </FormModal>

  <FormModal v-model="admin.showImport.value" title="批量导入" width="700px" @submit="admin.submitImport">
    <p class="muted" style="margin:0 0 12px">扫描指定目录并按所选软件导入安装包资源。</p>
    <div class="form-grid">
      <label class="wide">目录路径<input v-model.trim="admin.importForm.sourceDirectory" :disabled="admin.importing.value" required /></label>
      <label>分类
        <select v-model="admin.importForm.category" :disabled="admin.importing.value" required @change="admin.importForm.softwareTypeId = ''">
          <option value="">请选择分类</option>
          <option v-for="category in admin.activeTypeCategories.value" :key="category" :value="category">{{ category }}</option>
        </select>
      </label>
      <label>软件
        <select v-model="admin.importForm.softwareTypeId" :disabled="admin.importing.value || !admin.importForm.category" required>
          <option value="">请选择软件</option>
          <option v-for="type in admin.importSoftwareOptions.value" :key="type.id" :value="type.id">{{ type.name }}</option>
        </select>
      </label>
      <label>平台<input v-model.trim="admin.importForm.platform" :disabled="admin.importing.value" /></label>
      <label class="checkline"><input v-model="admin.importForm.recursive" :disabled="admin.importing.value" type="checkbox" />递归扫描</label>
      <label class="checkline"><input v-model="admin.importForm.published" :disabled="admin.importing.value" type="checkbox" />导入后发布</label>
      <label class="wide">说明<textarea v-model.trim="admin.importForm.description" :disabled="admin.importing.value" /></label>
    </div>
    <div v-if="admin.importing.value" class="loading-panel">
      <LoadingSpinner text="正在导入，请稍候..." />
      <p>正在扫描目录并写入资源记录，导入完成后会显示结果。</p>
    </div>
    <template #actions>
      <BaseButton type="submit" :loading="admin.importing.value">{{ admin.importing.value ? '导入中...' : '开始导入' }}</BaseButton>
      <BaseButton variant="ghost" :disabled="admin.importing.value" @click="admin.closeImportPage()">取消</BaseButton>
    </template>
  </FormModal>

  <FormModal v-model="admin.showTypeDialog.value" :title="admin.typeForm.id ? '编辑类型' : '新增类型'" @submit="admin.saveType">
    <div class="form-grid single">
      <label>分类
        <select v-model="admin.typeForm.category" required>
          <option value="">请选择分类</option>
          <option v-for="category in admin.softwareTypeCategories.value" :key="category" :value="category">{{ category }}</option>
        </select>
      </label>
      <label>软件类型名称<input v-model.trim="admin.typeForm.name" required maxlength="120" /></label>
      <label>说明<textarea v-model.trim="admin.typeForm.description" maxlength="500" /></label>
      <label class="checkline"><input v-model="admin.typeForm.active" type="checkbox" />启用</label>
    </div>
  </FormModal>

  <FormModal v-model="admin.showCategoryDialog.value" title="新增分类" @submit="admin.saveCategory">
    <div class="form-grid single">
      <label>分类名称<input v-model.trim="admin.categoryForm.name" required maxlength="40" placeholder="例如 中间件、数据库、应用软件" /></label>
    </div>
  </FormModal>

  <FormModal v-model="admin.showStandardDialog.value" :title="admin.standardForm.id ? '编辑标准' : '新增标准'" @submit="admin.saveStandard">
    <div class="form-grid single">
      <label>分类
        <select v-model="admin.standardForm.category" required @change="admin.standardForm.softwareTypeId = ''">
          <option value="">请选择分类</option>
          <option v-for="category in admin.standardCategoryOptions.value" :key="category" :value="category">{{ category }}</option>
        </select>
      </label>
      <label>软件
        <select v-model="admin.standardForm.softwareTypeId" :disabled="!admin.standardForm.category" required>
          <option value="">请选择软件</option>
          <option v-for="type in admin.standardSoftwareOptions.value" :key="type.id" :value="type.id">
            {{ type.name }}{{ type.active ? '' : '（停用）' }}
          </option>
        </select>
      </label>
      <label>软件版本<input v-model.trim="admin.standardForm.softwareVersion" required maxlength="80" /></label>
      <label>编码<input v-model.trim="admin.standardForm.code" maxlength="20" /></label>
      <label v-if="admin.adminSection.value !== 'standardPublish'">说明<textarea v-model.trim="admin.standardForm.summary" maxlength="500" /></label>
    </div>
  </FormModal>

  <FormModal v-model="admin.showParameterDialog.value" :title="admin.parameterForm.id ? '编辑参数' : '新增参数'" @submit="admin.saveParameter">
    <div class="form-grid single">
      <label>参数编码<input v-model.trim="admin.parameterForm.code" required maxlength="80" placeholder="例如 JDK_VERSION" /></label>
      <label>参数名称<input v-model.trim="admin.parameterForm.name" required maxlength="120" /></label>
      <label>参数值<input v-model.trim="admin.parameterForm.value" required maxlength="500" /></label>
      <label>分类<input v-model.trim="admin.parameterForm.category" maxlength="60" /></label>
      <label>说明<textarea v-model.trim="admin.parameterForm.description" maxlength="500" /></label>
      <label class="checkline"><input v-model="admin.parameterForm.active" type="checkbox" />启用</label>
      <label class="checkline"><input v-model="admin.parameterForm.deploymentStandard" type="checkbox" />是否为部署标准</label>
    </div>
  </FormModal>

  <FormModal v-model="admin.showParamImportDialog.value" title="批量导入参数" submitText="开始导入" :submitDisabled="admin.paramImporting.value" @submit="admin.importParameters">
    <div class="form-grid single">
      <p class="muted" style="margin:0 0 12px">请先下载模板，按格式填写后上传 Excel 文件。支持的列：参数编码、参数名称、参数值、分类、说明、是否启用（是/否）、是否部署标准（是/否）。</p>
      <label class="file-field">选择 Excel 文件
        <span class="file-control">
          <input type="file" accept=".xlsx,.xls" @change="admin.handleParamImportFileChange" required />
          <span class="file-button">选择文件</span>
          <span class="file-name">{{ admin.paramImportFile.value?.name || '未选择文件' }}</span>
        </span>
      </label>
    </div>
    <div v-if="admin.paramImportResult.value" class="import-result">
      <p>导入完成：成功 <strong>{{ admin.paramImportResult.value.imported }}</strong> 条，跳过 <strong>{{ admin.paramImportResult.value.skipped }}</strong> 条</p>
      <ul v-if="admin.paramImportResult.value.errors.length > 0">
        <li v-for="(err, idx) in admin.paramImportResult.value.errors" :key="idx" class="import-error">{{ err }}</li>
      </ul>
    </div>
    <template #actions>
      <BaseButton variant="ghost" @click="admin.downloadParameterTemplate()">下载模板</BaseButton>
      <BaseButton type="submit" :loading="admin.paramImporting.value">{{ admin.paramImporting.value ? '导入中...' : '开始导入' }}</BaseButton>
      <BaseButton variant="ghost" @click="admin.showParamImportDialog.value = false; admin.paramImportResult.value = null">关闭</BaseButton>
    </template>
  </FormModal>

  <FormModal v-model="admin.showUserDialog.value" title="新增用户" submitText="创建" @submit="admin.createUser">
    <div class="form-grid single">
      <label>账号<input v-model.trim="admin.userForm.username" required minlength="2" maxlength="60" placeholder="登录账号" /></label>
      <label>用户名<input v-model.trim="admin.userForm.displayName" maxlength="60" placeholder="显示名称（可选）" /></label>
      <label>密码<input v-model="admin.userForm.password" type="password" required minlength="6" maxlength="64" placeholder="至少6位" /></label>
      <label>角色
        <select v-model="admin.userForm.role" required>
          <option v-for="r in admin.allRoles.value" :key="r.name" :value="r.name">{{ r.name }}</option>
        </select>
      </label>
    </div>
  </FormModal>

  <FormModal v-model="admin.showRoleDialog.value" :title="'修改角色 — ' + (admin.userFormTarget.value?.username || '')" @submit="admin.changeUserRole">
    <label>角色
      <select v-model="admin.userForm.role" required>
        <option v-for="r in admin.allRoles.value" :key="r.name" :value="r.name">{{ r.name }}</option>
      </select>
    </label>
  </FormModal>

  <BaseModal v-model="admin.showImportResultDialog.value" title="导入结果">
    <div class="result-grid">
      <div><span>扫描文件</span><strong>{{ admin.importResult.value?.scannedCount ?? 0 }}</strong></div>
      <div><span>成功导入</span><strong>{{ admin.importResult.value?.importedCount ?? 0 }}</strong></div>
      <div><span>跳过文件</span><strong>{{ admin.importResult.value?.skippedCount ?? 0 }}</strong></div>
      <div><span>失败文件</span><strong>{{ admin.importResult.value?.failedCount ?? 0 }}</strong></div>
    </div>
    <template #footer>
      <BaseButton @click="admin.showImportResultDialog.value = false">确定</BaseButton>
    </template>
  </BaseModal>

  <BaseModal :modelValue="!!admin.deleteTarget.value" @update:modelValue="admin.closeDeleteReleaseDialog()" title="删除资源" width="400px">
    <p class="confirm-message">
      确认删除 {{ admin.deleteTarget.value?.middlewareName }} {{ admin.deleteTarget.value?.version }}？
    </p>
    <template #footer>
      <BaseButton variant="ghost" :disabled="admin.deletingRelease.value" @click="admin.closeDeleteReleaseDialog()">取消</BaseButton>
      <BaseButton variant="danger" :disabled="admin.deletingRelease.value" :loading="admin.deletingRelease.value" @click="admin.confirmDeleteRelease()">确认删除</BaseButton>
    </template>
  </BaseModal>

  <BaseModal v-model="admin.showRevisionModal.value" :title="admin.revisionDocTitle.value + ' - 修订历史'" width="700px">
    <div v-if="admin.revisionList.value.length === 0" class="empty-state" style="padding:40px 0">暂无修订记录</div>
    <div v-else class="revision-list">
      <div v-for="rev in admin.revisionList.value" :key="rev.id" class="revision-item">
        <div class="revision-header">
          <span class="revision-version">V{{ rev.version }}</span>
          <span class="revision-time">{{ admin.formatTime(rev.revisedAt) }}</span>
          <span class="revision-author">提交人：{{ rev.submittedBy || '-' }}</span>
          <span class="revision-author">修订人：{{ rev.revisedBy || '-' }}</span>
        </div>
        <p v-if="rev.revisionComment" class="revision-comment">审核意见：{{ rev.revisionComment }}</p>
        <details class="revision-content-detail">
          <summary>查看修订详情</summary>
          <div class="revision-content-block">
            <div v-if="rev.content" class="revision-rendered" v-html="renderMarkdown(rev.content)"></div>
            <div v-else class="empty-state" style="padding:12px 0;font-size:13px">无内容快照</div>
          </div>
        </details>
      </div>
    </div>
  </BaseModal>

  <BaseModal :modelValue="!!admin.selectedReview.value" @update:modelValue="admin.closeReviewDetail()" :title="admin.selectedReview.value?.documentType === 'PARAMETER_STANDARD' ? [admin.selectedReview.value?.category, admin.selectedReview.value?.software].filter(Boolean).join(' / ') : (admin.selectedReview.value?.documentTitle || '')" width="700px">
    <p class="muted" style="margin: 0 0 12px">
      <span :class="['status', admin.reviewStatusClass(admin.selectedReview.value?.status)]">{{ admin.selectedReview.value?.statusLabel }}</span>
      V{{ admin.selectedReview.value?.documentVersion || '-' }} · {{ admin.selectedReview.value?.category || '-' }} / {{ admin.selectedReview.value?.software || '-' }}
    </p>
    <div class="review-meta">
      <p>提交人：{{ admin.selectedReview.value?.submitterDisplayName || admin.selectedReview.value?.submitterUsername }} · 提交时间：{{ admin.formatTime(admin.selectedReview.value?.submittedAt) }}</p>
      <p v-if="admin.selectedReview.value?.reviewerUsername">审核人：{{ admin.selectedReview.value?.reviewerUsername }} · 审核时间：{{ admin.formatTime(admin.selectedReview.value?.reviewedAt) }}</p>
      <p v-if="admin.selectedReview.value?.reviewComment">审核意见：{{ admin.selectedReview.value?.reviewComment }}</p>
    </div>
    <div class="diff-view">
      <h4>版本差异对比</h4>
      <pre class="diff-content"><template v-for="(line, idx) in diffLines" :key="idx"><span :class="['diff-line', line.startsWith('+') ? 'diff-line-add' : line.startsWith('-') ? 'diff-line-del' : line.startsWith('@@') ? 'diff-line-info' : '' ]">{{ line }}</span>
</template></pre>
    </div>
    <div v-if="admin.selectedReview.value?.status === 'PENDING' && (isSysAdmin || (isCategoryAdmin && managedCategory === admin.selectedReview.value?.category))" class="review-actions-panel">
      <div class="form-grid single">
        <label>审核意见<textarea v-model.trim="admin.reviewComment.value" maxlength="1000" placeholder="请输入审核意见（可选）" /></label>
      </div>
      <div class="form-actions">
        <BaseButton variant="ghost" @click="admin.closeReviewDetail()">取消</BaseButton>
        <BaseButton variant="danger" @click="admin.reviewReject(admin.selectedReview.value)">驳回</BaseButton>
        <BaseButton variant="success" @click="admin.reviewApprove(admin.selectedReview.value)">审核通过</BaseButton>
      </div>
    </div>
  </BaseModal>
</template>

<script setup>
import { computed } from 'vue'
import FormModal from './ui/FormModal.vue'
import BaseModal from './ui/BaseModal.vue'
import BaseButton from './ui/BaseButton.vue'
import LoadingSpinner from './ui/LoadingSpinner.vue'
import { renderMarkdown } from '../utils'

const props = defineProps({
  admin: { type: Object, required: true },
  isSysAdmin: { type: Boolean, default: false },
  isCategoryAdmin: { type: Boolean, default: false },
  managedCategory: { type: String, default: '' },
  selectedReviewDiff: { type: String, default: '' }
})

const diffLines = computed(() => (props.selectedReviewDiff || '').split('\n'))
</script>
