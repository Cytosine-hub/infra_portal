<template>
  <div class="app-shell">
    <header :class="['topbar', route.name === 'home' ? 'portal-topbar' : '']">
      <div :class="{ 'clickable-title': route.name?.startsWith('forum') }" @click="route.name?.startsWith('forum') && navigate('forum')">
        <p class="eyebrow">Infrastructure Portal</p>
        <h1>{{ pageTitle }}</h1>
      </div>
      <div class="topbar-right">
        <nav v-if="route.name !== 'home'" class="nav-tabs" aria-label="Primary">
          <button :class="{ active: false }" @click="navigate('home')">门户首页</button>
          <button v-if="auth.token" :class="{ active: route.name === 'standards' }" @click="navigate('standards')">标准发布</button>
          <button v-if="auth.token" :class="{ active: route.name === 'public' }" @click="navigate('downloads')">下载中心</button>
          <button v-if="auth.token" :class="{ active: route.name?.startsWith('forum') }" @click="navigate('forum')">论坛</button>
          <button v-if="auth.token && siteConfig.knowledgeEnabled" :class="{ active: route.name === 'knowledge' }" @click="navigate('knowledge')">知识库</button>
          <button v-if="auth.token" :class="{ active: route.name === 'wiki' }" @click="navigate('wiki')">Wiki</button>
          <button v-if="auth.token && siteConfig.diagnosticsEnabled" :class="{ active: route.name === 'diagnostics' }" @click="navigate('diagnostics')">智能排查</button>
          <button v-if="canAccessAdmin" :class="{ active: route.name === 'admin' || route.name === 'documentEditor' }" @click="navigate('admin')">管理后台</button>
        </nav>
        <div class="topbar-user">
          <template v-if="auth.token">
            <span class="topbar-username">{{ auth.user?.displayName || auth.user?.username }}</span>
            <span class="topbar-role-tag">{{ currentUserRole }}</span>
            <button class="topbar-logout" @click="logout()">退出</button>
          </template>
          <template v-else>
            <span class="topbar-username guest">未登录</span>
            <button class="topbar-logout" @click="navigate('admin')">登录</button>
          </template>
        </div>
      </div>
    </header>

    <main>
      <DocumentEditor
        v-if="route.name === 'documentEditor'"
        :auth="auth"
        :software-type-categories="softwareTypeCategories"
        :software-types="softwareTypes"
        :standard-document-options="standardDocumentOptions"
        :markdown="markdown"
        :document-id="route.documentId"
        :notify="notify"
        @saved="onDocumentEditorSaved"
        @cancel="onDocumentEditorCancel"
      />
      <template v-else>
      <HomePage
        v-if="route.name === 'home'"
        @navigate="navigate"
        @open-detail="openDetail"
        @notify="notify"
      />

      <DownloadsPage v-else-if="route.name === 'public'" />

      <StandardsPage v-else-if="route.name === 'standards'" />

      <section v-else-if="route.name === 'forum'" class="workspace">
        <ForumPostList
          :auth="auth"
          @open-post="(id) => navigate('forum/post/' + id)"
          @new-post="goForumNew"
          @go-mine="navigate('forum/mine')"
        />
      </section>
      <section v-else-if="route.name === 'forumDetail'" class="workspace">
        <ForumPostDetail
          :auth="auth"
          :post-id="route.postId"
          :markdown="markdown"
          :notify="notify"
          @back="navigate('forum')"
          @edit-post="(id) => navigate('forum/edit/' + id)"
          @login="navigate('admin')"
        />
      </section>
      <section v-else-if="route.name === 'forumEditor'" class="workspace">
        <ForumPostEditor
          :auth="auth"
          :post-id="route.postId"
          :markdown="markdown"
          :notify="notify"
          @saved="navigate('forum')"
          @cancel="navigate('forum')"
        />
      </section>
      <section v-else-if="route.name === 'forumMine'" class="workspace">
        <ForumPersonalCenter
          :auth="auth"
          :notify="notify"
          @back="navigate('forum')"
          @open-post="(id) => navigate('forum/post/' + id)"
          @edit-post="(id) => navigate('forum/edit/' + id)"
        />
      </section>

      <KnowledgePanel v-else-if="route.name === 'knowledge' && siteConfig.knowledgeEnabled" :auth="auth" :notify="notify" />
      <WikiPanel v-else-if="route.name === 'wiki'" :auth="auth" :notify="notify" />
      <DiagnosticsPanel v-else-if="route.name === 'diagnostics' && siteConfig.diagnosticsEnabled" :auth="auth" :notify="notify" />

      <CommandsPage
        v-else-if="route.name === 'commands'"
        :auth="auth"
        :isSysAdmin="isSysAdmin"
        :managedCategory="managedCategory"
        :softwareTypes="softwareTypes"
        :notify="notify"
        :confirm="confirmAction"
      />

      <section v-else class="workspace">
        <div v-if="!auth.token" class="login-page">
          <div class="login-card">
            <div class="login-brand">
              <div class="login-brand-overlay">
                <p class="login-brand-eyebrow">Infrastructure Portal</p>
                <h1>运营集成中心门户</h1>
                <p>资源下载 · 标准发布 · 漏洞通告 · 技术交流</p>
              </div>
            </div>
            <form class="login-form" @submit.prevent="login">
              <h3>登录</h3>
              <label>账号<input v-model.trim="loginForm.username" autocomplete="username" placeholder="请输入账号" /></label>
              <label>密码<input v-model="loginForm.password" type="password" autocomplete="off" placeholder="请输入密码" /></label>
              <button type="submit">登 录</button>
            </form>
          </div>
        </div>

        <template v-else>
          <AdminPage
            :section="adminSection"
            :isSysAdmin="isSysAdmin"
            @switchSection="switchAdminSection"
            @showPassword="showPassword = !showPassword"
            @logout="logout()"
          >
            <template #header-actions>
              <template v-if="adminSection === 'files'">
                <button class="ghost" @click="openImportPage()">批量导入</button>
                <button @click="startCreate()">新增资源</button>
              </template>
              <template v-else-if="adminSection === 'types'">
                <button class="ghost" @click="loadSoftwareMetadata()">刷新</button>
                <button class="ghost" @click="openCreateCategoryDialog()">新增分类</button>
                <button @click="openCreateTypeDialog()">新增类型</button>
              </template>
              <template v-else-if="adminSection === 'standardPublish'">
                <button class="ghost" @click="loadStandardModule()">刷新</button>
                <button @click="openCreateStandardDialog()">新增标准</button>
              </template>
              <template v-else-if="adminSection === 'documentMaintenance'">
                <button class="ghost" @click="loadStandardDocuments()">刷新</button>
                <button @click="goDocumentEditor()">新增文档</button>
              </template>
              <template v-else-if="adminSection === 'users'">
                <button @click="openCreateUserDialog()">新增用户</button>
              </template>
            </template>

              <FormModal v-model="showPassword" title="修改密码" @submit="changePassword">
                <div class="form-grid single">
                  <label>当前密码<input v-model="passwordForm.currentPassword" type="password" required /></label>
                  <label>新密码<input v-model="passwordForm.newPassword" type="password" minlength="8" required /></label>
                  <label>确认密码<input v-model="passwordForm.confirmPassword" type="password" required /></label>
                </div>
              </FormModal>

              <FilesSection v-if="adminSection === 'files'"
                :releases="adminPage.content" :filters="adminFilters" :pageInfo="adminPage"
                :getStandardLabel="getStandardLabel"
                @search="loadAdmin" @edit="startEdit" @togglePublish="togglePublish"
                @regenerate="regeneratePackage" @delete="openDeleteReleaseDialog"
                @changePage="changeAdminPage"
              />
              <TypesSection v-else-if="adminSection === 'types'"
                :types="pagedSoftwareTypes" :categories="softwareTypeCategories"
                :filters="typeFilters" :pageInfo="typePage"
                @applyFilters="applyTypeFilters" @editType="openEditTypeDialog"
                @deleteType="deleteType" @changePage="changeTypePage"
              />
              <StandardsSection v-else-if="adminSection === 'standardPublish'"
                :standards="filteredStandardDocuments" :categories="softwareTypeCategories"
                :filters="standardFilters" :pageInfo="standardPage"
                :selectedStandard="selectedStandard" :parameters="selectedStandardParameters"
                @filterCategoryChange="handleStandardFilterCategoryChange"
                @openDetail="openStandardDetail" @editStandard="openEditStandardDialog"
                @submitReview="submitForReview" @startModify="startModify" @cancelModify="cancelModify"
                @revisionHistory="(doc) => openRevisionHistory(doc, 'PARAMETER_STANDARD')"
                @deleteStandard="confirmDeleteDoc" @changePage="changeStandardPage"
                @backToList="backToStandardList" @downloadTemplate="downloadParameterTemplate"
                @importParams="showParamImportDialog = true" @createParam="openCreateParameterDialog"
                @copyParam="copyParameter" @editParam="openEditParameterDialog"
              />
              <ReviewsSection v-else-if="adminSection === 'reviews'"
                :reviews="pagedReviews" :filterStatus="reviewFilters.status" :pageInfo="reviewPageInfo"
                :isSysAdmin="isSysAdmin" :isCategoryAdmin="isCategoryAdmin" :managedCategory="managedCategory"
                @filterChange="(v) => { reviewFilters.status = v; applyReviewFilters() }"
                @viewDetail="openReviewDetail" @changePage="changeReviewPage"
              />
              <UsersSection v-else-if="adminSection === 'users'"
                :users="userList"
                @changeRole="openChangeRoleDialog" @resetPassword="resetUserPassword"
                @deleteUser="deleteUserAccount"
              />
              <DocumentsSection v-else-if="adminSection === 'documentMaintenance'"
                :documents="pagedMaintenanceDocuments" :filters="maintenanceDocumentFilters"
                :pageInfo="maintenanceDocumentPage" :getStandardLabel="getStandardLabel"
                @applyFilters="applyMaintenanceDocumentFilters" @preview="previewDocument"
                @edit="(doc) => goDocumentEditorEdit(doc.id)" @submitReview="submitForReview"
                @startModify="startModify" @cancelModify="cancelModify"
                @revisionHistory="(doc) => openRevisionHistory(doc, doc.documentType || 'MANUAL')"
                @delete="confirmDeleteDoc" @changePage="changeMaintenanceDocumentPage"
              />
              <SettingsSection v-else-if="adminSection === 'settings'"
                :settings="systemSettings" @save="saveSystemSettings"
              />
          </AdminPage>
        </template>
      </section>
      </template>
    </main>

    <DocumentPreview
      :document="selectedPreviewDocument"
      :documents="maintenanceDocuments"
      :parameters="standardParameters"
      @close="closePreviewDocument()"
      @preview="previewDocument"
    />

    <AdminModals
      :admin="admin"
      :isSysAdmin="isSysAdmin"
      :isCategoryAdmin="isCategoryAdmin"
      :managedCategory="managedCategory"
      :selectedReviewDiff="selectedReviewDiff"
    />

    <Toast :notice="notice" />
    <ConfirmDialog v-model="confirmDialog" />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import { request } from './api'
import { useAuth } from './composables/useAuth'
import { useNotify } from './composables/useNotify'
import { useRoute } from './composables/useRoute'
import Pagination from './components/Pagination.vue'
import DocumentEditor from './components/DocumentEditor.vue'
import ForumPostList from './components/ForumPostList.vue'
import ForumPostDetail from './components/ForumPostDetail.vue'
import ForumPostEditor from './components/ForumPostEditor.vue'
import ForumPersonalCenter from './components/ForumPersonalCenter.vue'
import KnowledgePanel from './components/KnowledgePanel.vue'
import WikiPanel from './components/WikiPanel.vue'
import DiagnosticsPanel from './components/DiagnosticsPanel.vue'
import HomePage from './pages/HomePage.vue'
import DownloadsPage from './pages/DownloadsPage.vue'
import StandardsPage from './pages/StandardsPage.vue'
import CommandsPage from './pages/CommandsPage.vue'
import AdminPage from './pages/admin/AdminPage.vue'
import FilesSection from './pages/admin/FilesSection.vue'
import TypesSection from './pages/admin/TypesSection.vue'
import StandardsSection from './pages/admin/StandardsSection.vue'
import ReviewsSection from './pages/admin/ReviewsSection.vue'
import UsersSection from './pages/admin/UsersSection.vue'
import DocumentsSection from './pages/admin/DocumentsSection.vue'
import SettingsSection from './pages/admin/SettingsSection.vue'
import Toast from './components/ui/Toast.vue'
import ConfirmDialog from './components/ui/ConfirmDialog.vue'
import DocumentPreview from './components/DocumentPreview.vue'
import AdminModals from './components/AdminModals.vue'
import { useAdmin } from './composables/useAdmin'

const { auth, login: authLogin, logout: authLogout, restoreAuth, sha256,
  currentUserRole, isSysAdmin, isCategoryAdmin, isManager, canAccessAdmin, isReadOnly, managedCategory } = useAuth()
const { notice, notify, confirmDialog, confirm: confirmAction, handleConfirm, cancelConfirm } = useNotify()
const { route, navigate } = useRoute()

// ── 管理后台 composable ──
const admin = useAdmin(auth, notify, confirmAction)
const {
  // 状态
  adminSection, showPassword, showImport, importing, importResult, showImportResultDialog,
  editing, uploading, uploadProgress, deleteTarget, deletingRelease,
  showTypeDialog, showCategoryDialog, softwareCategories, softwareTypes,
  showStandardDialog, showParameterDialog, showParamImportDialog, paramImporting, paramImportResult, paramImportFile,
  allParameterStandards, standardDocuments, standardParameters, selectedStandard,
  selectedReview, selectedReviewDiff, reviewComment, allReviews, showRevisionModal, revisionList, revisionDocTitle,
  showUserDialog, showRoleDialog, userFormTarget, userList, allRoles, systemSettings,
  adminFilters, typeFilters, standardFilters, parameterFilters, maintenanceDocumentFilters, reviewFilters, reviewPage,
  adminPage, releaseForm, importForm, passwordForm, categoryForm, typeForm, standardForm, parameterForm, userForm,
  // 加载函数
  loadAdmin, loadSoftwareTypes, loadSoftwareCategories, loadSoftwareMetadata, loadStandardModule, loadAllParameterStandards,
  loadStandardDocuments, loadStandardParameters, loadSystemSettings, saveSystemSettings, loadUsers, loadRoles,
  // 资源 CRUD
  startCreate, startEdit, cancelEdit, handleReleaseFileChange, saveRelease, togglePublish,
  openDeleteReleaseDialog, closeDeleteReleaseDialog, confirmDeleteRelease, regeneratePackage,
  // 批量导入
  openImportPage, closeImportPage, submitImport,
  // 类型管理
  openCreateCategoryDialog, closeCategoryDialog, saveCategory,
  openCreateTypeDialog, closeTypeDialog, saveType,
  // 标准管理
  openCreateStandardDialog, openEditStandardDialog, closeStandardDialog, saveStandard,
  submitForReview, startModify, cancelModify, confirmDeleteDoc,
  // 参数管理
  openCreateParameterDialog, closeParameterDialog, saveParameter,
  handleParamImportFileChange, importParameters, downloadParameterTemplate, copyParameter,
  // 审核管理
  loadReviews, openReviewDetail, closeReviewDetail, reviewApprove, reviewReject,
  openRevisionHistory,
  // 用户管理
  openCreateUserDialog, closeUserDialog, createUser, openRoleDialog, closeRoleDialog, changeUserRole, deleteUserAccount,
  resetUserPassword, openChangeRoleDialog,
  // 密码
  changePassword,
  // Computed
  activeSoftwareTypes, softwareTypeCategories, activeTypeCategories,
  releaseCategoryOptions, releaseSoftwareOptions, releaseStandardOptions, releaseParameterStandardOptions,
  importSoftwareOptions, standardCategoryOptions, standardSoftwareOptions,
  filteredSoftwareTypes, typePageComputed, pagedSoftwareTypes,
  filteredStandardDocuments, standardDocumentOptions, standardPageComputed, selectedStandardParameters,
  maintenanceDocumentsComputed, maintenanceDocumentPageComputed, pagedMaintenanceDocuments,
  // 筛选/分页
  changeTypePage, applyTypeFilters, changeStandardPage, applyStandardFilters, handleStandardFilterCategoryChange,
  openStandardDetail, backToStandardList, changeMaintenanceDocumentPage, applyMaintenanceDocumentFilters,
  changeReviewPage, applyReviewFilters,
  // 工具函数
  getStandardLabel, formatTime, statusLabel, statusClass, reviewStatusClass,
  // 切换管理区域
  switchAdminSection, changeAdminPage
} = admin

const markdown = new MarkdownIt({ html: false, linkify: true, breaks: true })
const siteConfig = reactive({ knowledgeEnabled: true, diagnosticsEnabled: true })
// 下载进度已迁移到 DownloadsPage.vue
// 公共页面状态已迁移到各页面组件（HomePage/DownloadsPage/StandardsPage）
// ── 管理后台状态已迁移到 composables/useAdmin.js ──
const loginForm = reactive({ username: '', password: '' })
const selectedPreviewDocument = ref(null)
const previewTocActiveId = ref('')
// 审核/差异对话框状态已迁移到 ReviewsSection

// ── 常用命令已迁移到 CommandsPage.vue ──

// ── RBAC helpers 已迁移到 composables/useAuth.js ──

const pageTitle = computed(() => {
  if (route.name === 'home') return '运营集成中心门户'
  if (route.name === 'public') return '软件下载'
  if (route.name === 'standards') return '标准发布'
  if (route.name === 'documentEditor') return '文档编辑'
  if (route.name && route.name.startsWith('forum')) return 'infra论坛'
  if (route.name === 'knowledge') return '知识库管理'
  if (route.name === 'wiki') return 'Wiki 知识库'
  if (route.name === 'diagnostics') return '智能排查'
  if (route.name === 'commands') return '常用命令'
  return '管理后台'
})
// 公共标准页面 computed 已迁移到 StandardsPage.vue

// 审核 computed — 从 allReviews 派生分页
const filteredReviews = computed(() => {
  const status = reviewFilters.status
  if (!status) return allReviews.value
  return allReviews.value.filter(r => r.status === status)
})
const reviewPageInfo = computed(() => {
  const totalElements = filteredReviews.value.length
  const totalPages = Math.max(Math.ceil(totalElements / reviewPage.size), 1)
  const page = Math.min(reviewPage.page, totalPages - 1)
  return { content: [], page, size: reviewPage.size, totalElements, totalPages, first: page <= 0, last: page >= totalPages - 1 }
})
const pagedReviews = computed(() => {
  const start = reviewPageInfo.value.page * reviewPage.size
  return filteredReviews.value.slice(start, start + reviewPage.size)
})
const diffLines = computed(() => (selectedReviewDiff.value || '').split('\n'))

// 文档预览 computed 已迁移到 DocumentPreview.vue

// parseRoute/syncRoute 基础逻辑在 composables/useRoute.js
// 这里的 syncRoute 包含路由变化后的数据加载副作用
function syncRoute() {
  const hash = window.location.hash.replace(/^#/, '')
  let next
  if (!hash || hash === '/' || hash === '/home') next = { name: 'home', token: null }
  else if (hash.startsWith('/admin/document-editor')) {
    const m = hash.match(/^\/admin\/document-editor\/(\d+)$/); next = { name: 'documentEditor', documentId: m ? m[1] : null }
  } else if (hash.startsWith('/admin')) next = { name: 'admin', token: null }
  else if (hash === '/forum/mine') next = { name: 'forumMine', postId: null }
  else if (hash.startsWith('/forum/new')) next = { name: 'forumEditor', postId: null }
  else if (/^\/forum\/edit\/(\d+)$/.test(hash)) next = { name: 'forumEditor', postId: hash.match(/\d+/)[0] }
  else if (/^\/forum\/post\/(\d+)$/.test(hash)) next = { name: 'forumDetail', postId: hash.match(/\d+/)[0] }
  else if (hash.startsWith('/forum')) next = { name: 'forum', postId: null }
  else if (hash.startsWith('/knowledge')) next = { name: 'knowledge' }
  else if (hash.startsWith('/wiki')) next = { name: 'wiki' }
  else if (hash.startsWith('/diagnostics')) next = { name: 'diagnostics' }
  else if (/^\/downloads\/(.+)$/.test(hash)) next = { name: 'public', token: hash.match(/^\/downloads\/(.+)$/)[1] }
  else if (/^\/standards\/(ps|doc)\/(\d+)$/.test(hash)) { const m = hash.match(/^\/standards\/(ps|doc)\/(\d+)$/); next = { name: 'standards', standardId: m[2], standardType: m[1] } }
  else if (/^\/standards\/(\d+)$/.test(hash)) next = { name: 'standards', standardId: hash.match(/\d+/)[0], standardType: null }
  else if (hash === '/standards') next = { name: 'standards', standardId: null, standardType: null }
  else if (hash.startsWith('/commands')) next = { name: 'commands' }
  else next = { name: 'public', token: null }
  route.name = next.name
  route.name = next.name
  route.token = next.token
  route.standardId = next.standardId
  route.standardType = next.standardType
  route.documentId = next.documentId
  route.postId = next.postId
  updateDocumentTitle()
  // 独立页面组件自行加载数据（HomePage/DownloadsPage/StandardsPage/CommandsPage/WikiPanel/KnowledgePanel/DiagnosticsPanel）
  const selfManagedRoutes = ['home', 'public', 'standards', 'commands', 'knowledge', 'wiki', 'diagnostics']
  if (selfManagedRoutes.includes(route.name)) return
  if (route.name === 'documentEditor' || route.name === 'forum' || route.name === 'forumDetail' || route.name === 'forumEditor' || route.name === 'forumMine') {
    if (route.name === 'documentEditor' && auth.token) {
      loadSoftwareTypes()
      loadSoftwareCategories()
      loadStandardModule()
    }
    return
  }
  if (auth.token) {
    if (isReadOnly.value) {
      window.location.hash = '#/home'
      return
    }
    loadAdmin()
    loadSoftwareTypes()
    loadSoftwareCategories()
    loadStandardModule()
    loadAllParameterStandards()
  }
}

function updateDocumentTitle() {
  document.title = `${pageTitle.value} - 运营集成中心`
}

// 公共页面数据加载已迁移到各页面组件（HomePage/DownloadsPage/StandardsPage）
// loadAdmin/loadSoftwareTypes/loadStandardModule/loadStandardDocuments/loadStandardParameters
// 已迁移到 composables/useAdmin.js

async function login() {
  try {
    await authLogin(loginForm.username, loginForm.password)
    loginForm.password = ''
    notify('登录成功', 'success')
    if (isReadOnly.value) {
      window.location.hash = '#/home'
    } else {
      await loadSoftwareCategories()
      await loadSoftwareTypes()
      await loadAdmin()
      await loadStandardModule()
    }
  } catch (error) {
    loginForm.password = ''
    notify(error.message || '登录失败', 'error')
  }
}

async function logout(showMessage = true) {
  await authLogout()
  loginForm.username = ''
  loginForm.password = ''
  selectedRelease.value = null
  selectedPublicStandard.value = null
  selectedStandard.value = null
  editing.value = false
  showPassword.value = false
  adminSection.value = 'files'
  Object.assign(adminFilters, { keyword: '', platform: '', published: '', page: 0, size: 10 })
  Object.assign(releaseForm, defaultReleaseForm())
  Object.assign(typeForm, defaultTypeForm())
  Object.assign(standardForm, defaultStandardForm())
  Object.assign(userForm, { username: '', displayName: '', password: '', role: '开发经理' })
  userFormTarget.value = null
  if (showMessage) notify('已退出')
  window.location.hash = '#/home'
}

// 论坛发帖需检查登录状态
function goForumNew() {
  if (!auth.token) { navigate('admin'); return }
  navigate('forum/new')
}

// handleDownload 已迁移到 DownloadsPage.vue

function goDocumentEditor() {
  window.location.hash = '#/admin/document-editor'
}

function goDocumentEditorEdit(id) {
  window.location.hash = `#/admin/document-editor/${id}`
}

function onDocumentEditorSaved() {
  notify('文档已保存', 'success')
  adminSection.value = 'documentMaintenance'
  loadStandardDocuments()
  window.location.hash = '#/admin'
}

function onDocumentEditorCancel() {
  window.location.hash = '#/admin'
  setTimeout(() => { adminSection.value = 'documentMaintenance' }, 0)
}

// 公共页面函数已迁移到各页面组件

// 筛选/分页/标准文档CRUD/审核/工具函数 已迁移到 composables/useAdmin.js

// 文档预览函数已迁移到 DocumentPreview.vue
function previewDocument(document) {
  selectedPreviewDocument.value = document
  if (document.relatedStandardDocumentId) {
    loadStandardParameters(document.relatedStandardDocumentId)
  } else {
    standardParameters.value = []
  }
}
function closePreviewDocument() { selectedPreviewDocument.value = null }

// 用户管理函数已迁移到 composables/useAdmin.js

async function loadSiteConfig() {
  try {
    const cfg = await request('/api/public/config', { token: null })
    siteConfig.knowledgeEnabled = cfg.knowledgeEnabled !== false
    siteConfig.diagnosticsEnabled = cfg.diagnosticsEnabled !== false
  } catch { /* use defaults */ }
}

function handleUnhandledRejection(event) {
  const message = event.reason?.message || '请求失败'
  notify(message, 'error')
  if (event.reason?.status === 401) {
    logout(false)
  }
  event.preventDefault()
}

function handleBeforeUnload(e) {
  if (uploading.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}

function handleAuthLogout() {
  auth.token = ''
  auth.user = null
  window.location.hash = '#/home'
}

onMounted(() => {
  loadSiteConfig()
  restoreAuth()
  syncRoute()
  window.addEventListener('hashchange', syncRoute)
  window.addEventListener('unhandledrejection', handleUnhandledRejection)
  window.addEventListener('auth:logout', handleAuthLogout)
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncRoute)
  window.removeEventListener('unhandledrejection', handleUnhandledRejection)
  window.removeEventListener('auth:logout', handleAuthLogout)
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>
