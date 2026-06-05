/**
 * 管理后台状态和逻辑 composable
 * 从 App.vue 提取，包含文件管理、类型管理、标准管理、用户管理等
 */
import { reactive, ref, computed } from 'vue'
import { request } from '../api'

async function sha256Hash(str) {
  try {
    const msgBuffer = new TextEncoder().encode(str)
    const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer)
    return Array.from(new Uint8Array(hashBuffer)).map(b => b.toString(16).padStart(2, '0')).join('')
  } catch { return str }
}

export function useAdmin(auth, notify, confirm) {
  // ── 管理后台状态 ──
  const adminSection = ref('files')
  const showPassword = ref(false)
  const showImport = ref(false)
  const importing = ref(false)
  const importResult = ref(null)
  const showImportResultDialog = ref(false)
  const editing = ref(false)
  const uploading = ref(false)
  const uploadProgress = ref(0)
  const deleteTarget = ref(null)
  const deletingRelease = ref(false)

  // 类型管理
  const showTypeDialog = ref(false)
  const showCategoryDialog = ref(false)
  const softwareCategories = ref([])
  const softwareTypes = ref([])

  // 标准管理
  const showStandardDialog = ref(false)
  const showParameterDialog = ref(false)
  const showParamImportDialog = ref(false)
  const paramImporting = ref(false)
  const paramImportResult = ref(null)
  const paramImportFile = ref(null)
  const allParameterStandards = ref([])
  const standardDocuments = ref([])
  const standardParameters = ref([])
  const selectedStandard = ref(null)

  // 审核管理
  const selectedReview = ref(null)
  const selectedReviewDiff = ref('')
  const reviewComment = ref('')
  const allReviews = ref([])
  const showRevisionModal = ref(false)
  const revisionList = ref([])
  const revisionDocTitle = ref('')

  // 用户管理
  const showUserDialog = ref(false)
  const showRoleDialog = ref(false)
  const userFormTarget = ref(null)
  const userList = ref([])
  const allRoles = ref([])

  // 系统设置
  const systemSettings = reactive({ 'knowledge-enabled': 'true', 'diagnostics-enabled': 'true' })

  // 筛选器
  const adminFilters = reactive({ keyword: '', platform: '', published: '', page: 0, size: 10 })
  const typeFilters = reactive({ category: '', name: '', page: 0, size: 10 })
  const standardFilters = reactive({ keyword: '', category: '', software: '', status: '', page: 0, size: 10 })
  const parameterFilters = reactive({ page: 0, size: 10 })
  const maintenanceDocumentFilters = reactive({ keyword: '', documentType: '', status: '', page: 0, size: 10 })
  const reviewFilters = reactive({ status: '' })
  const reviewPage = reactive({ page: 0, size: 10 })

  // 分页
  const adminPage = reactive(emptyPage(10))
  const typePage = reactive(emptyPage(10))
  const standardPage = reactive(emptyPage(10))
  const parameterPage = reactive(emptyPage(10))
  const maintenanceDocumentPage = reactive(emptyPage(10))
  const reviewListPage = reactive(emptyPage(10))

  // 表单
  const releaseForm = reactive(defaultReleaseForm())
  const importForm = reactive(defaultImportForm())
  const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const categoryForm = reactive({ name: '' })
  const typeForm = reactive(defaultTypeForm())
  const standardForm = reactive(defaultStandardForm())
  const parameterForm = reactive(defaultParameterForm())
  const userForm = reactive({ username: '', displayName: '', password: '', role: '开发经理' })

  // ── 辅助函数 ──
  function todayString() {
    const now = new Date()
    const month = String(now.getMonth() + 1).padStart(2, '0')
    const day = String(now.getDate()).padStart(2, '0')
    return `${now.getFullYear()}-${month}-${day}`
  }
  function emptyPage(size) { return { content: [], page: 0, size, totalElements: 0, totalPages: 0, first: true, last: true } }
  function defaultReleaseForm() { return { id: null, category: '', softwareTypeId: '', middlewareName: '', version: '', platform: '', description: '', releasedAt: todayString(), published: false, file: null, originalFileName: '', standardDocumentId: null, standardPackage: false, parameterStandardId: null } }
  function defaultImportForm() { return { sourceDirectory: '', category: '', softwareTypeId: '', middlewareName: '', platform: '', description: '', published: false, recursive: true } }
  function defaultTypeForm() { return { id: null, category: '中间件', name: '', description: '', active: true } }
  function defaultStandardForm() { return { id: null, category: '', softwareTypeId: '', softwareVersion: '', code: '', summary: '', content: '# 参数标准\n\n' } }
  function defaultParameterForm() { return { id: null, standardDocumentId: null, parameterStandardId: null, code: '', name: '', value: '', category: '', description: '', active: true, deploymentStandard: false } }
  function applyPage(target, source) { Object.assign(target, source) }
  function findSoftwareType(id) { return softwareTypes.value.find(t => String(t.id) === String(id)) }

  // ── 加载函数 ──
  async function loadAdmin() {
    const pub = adminFilters.published !== '' ? `&published=${adminFilters.published}` : ''
    const query = `keyword=${encodeURIComponent(adminFilters.keyword)}&platform=${encodeURIComponent(adminFilters.platform)}&page=${adminFilters.page}&size=${adminFilters.size}${pub}`
    applyPage(adminPage, await request(`/api/admin/releases?${query}`))
  }

  async function loadSoftwareTypes() {
    softwareTypes.value = await request('/api/admin/software-types')
    if (typeFilters.page >= typePage.value.totalPages) typeFilters.page = Math.max(typePage.value.totalPages - 1, 0)
  }

  async function loadSoftwareCategories() { softwareCategories.value = await request('/api/admin/software-type-categories') }
  async function loadSoftwareMetadata() { await loadSoftwareCategories(); await loadSoftwareTypes() }

  async function loadStandardDocuments() {
    const data = await request('/api/admin/standard-documents?size=1000')
    const list = Array.isArray(data) ? data : (data?.content ?? [])
    standardDocuments.value = list
    if (selectedStandard.value) selectedStandard.value = standardDocuments.value.find(d => d.id === selectedStandard.value.id) || null
    await loadStandardParameters(selectedStandard.value?.id)
  }

  async function loadAllParameterStandards() {
    try {
      const data = await request('/api/admin/parameter-standards')
      const list = Array.isArray(data) ? data : (data?.content ?? [])
      allParameterStandards.value = list.filter(d => d.status === 'PUBLISHED')
    } catch { allParameterStandards.value = [] }
  }

  async function loadStandardParameters(targetId) {
    if (!targetId) { standardParameters.value = []; return }
    standardParameters.value = await request(`/api/admin/standard-parameters?standardDocumentId=${encodeURIComponent(targetId)}`)
  }

  async function loadSystemSettings() {
    try { const data = await request('/api/admin/settings'); if (data) Object.assign(systemSettings, data) } catch {}
  }

  async function saveSystemSettings() {
    try { await request('/api/admin/settings', { method: 'PUT', body: systemSettings }); notify('设置已保存', 'success') } catch { notify('保存失败', 'error') }
  }

  async function loadUsers() { try { userList.value = await request('/api/admin/users') } catch { userList.value = [] } }
  async function loadRoles() { try { allRoles.value = await request('/api/admin/users/roles') } catch { allRoles.value = [] } }

  // ── 密码 ──
  async function changePassword() {
    if (passwordForm.newPassword !== passwordForm.confirmPassword) { notify('两次密码不一致', 'error'); return }
    if (passwordForm.newPassword.length < 8) { notify('密码至少8位', 'error'); return }
    try {
      await request('/api/admin/account/password', { method: 'PUT', body: { currentPassword: passwordForm.currentPassword, newPassword: passwordForm.newPassword } })
      notify('密码已修改', 'success'); showPassword.value = false; Object.assign(passwordForm, { currentPassword: '', newPassword: '', confirmPassword: '' })
    } catch (e) { notify(e.message || '修改失败', 'error') }
  }

  // ── 资源 CRUD ──
  function startCreate() { editing.value = true; Object.assign(releaseForm, defaultReleaseForm()) }
  function startEdit(release) {
    if (release.published) { notify('已发布资源不能编辑，请先下架后再编辑', 'error'); return }
    const selectedType = findSoftwareType(release.softwareTypeId)
    editing.value = true
    Object.assign(releaseForm, {
      id: release.id,
      category: release.softwareTypeCategory || selectedType?.category || '',
      softwareTypeId: release.softwareTypeId || '',
      middlewareName: release.middlewareName,
      version: release.version,
      platform: release.platform || '',
      description: release.description || '',
      releasedAt: release.releasedAt || '',
      published: release.published,
      file: null,
      originalFileName: release.originalFileName || '',
      standardDocumentId: release.standardDocumentId || null,
      standardPackage: release.standardPackage || false,
      parameterStandardId: release.parameterStandardId || null
    })
  }
  function cancelEdit() { editing.value = false; Object.assign(releaseForm, defaultReleaseForm()) }
  function handleReleaseFileChange(e) { releaseForm.file = e.target.files?.[0] || null }

  async function saveRelease() {
    const selectedType = findSoftwareType(releaseForm.softwareTypeId)
    if (!selectedType) { notify('请选择软件类型', 'error'); return }
    if (!releaseForm.id && !releaseForm.file) { notify('请上传安装包', 'error'); return }

    const formData = new FormData()
    releaseForm.middlewareName = selectedType.name
    for (const key of ['middlewareName', 'version', 'platform', 'description', 'releasedAt', 'published', 'standardDocumentId']) {
      formData.append(key, releaseForm[key] ?? '')
    }
    formData.append('softwareTypeId', releaseForm.softwareTypeId)
    formData.append('standardPackage', releaseForm.standardPackage)
    if (releaseForm.standardPackage && releaseForm.parameterStandardId) {
      formData.append('parameterStandardId', releaseForm.parameterStandardId)
    }
    if (releaseForm.file) { formData.append('file', releaseForm.file) }

    const url = releaseForm.id ? `/api/admin/releases/${releaseForm.id}` : '/api/admin/releases'
    const method = releaseForm.id ? 'PUT' : 'POST'
    uploading.value = true; uploadProgress.value = 0

    try {
      await new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest()
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) uploadProgress.value = Math.round(e.loaded / e.total * 100)
        }
        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) {
            resolve(JSON.parse(xhr.responseText || 'null'))
          } else {
            let message = '保存失败'
            try {
              const payload = JSON.parse(xhr.responseText)
              const fieldErrors = payload.fieldErrors ? Object.values(payload.fieldErrors).filter(Boolean) : []
              message = fieldErrors.length ? fieldErrors.join('；') : (payload.message || message)
            } catch (_e) { /* skip parse error */ }
            reject(new Error(message))
          }
        }
        xhr.onerror = () => reject(new Error('网络错误'))
        xhr.open(method, url)
        xhr.setRequestHeader('Authorization', 'Bearer ' + auth.token)
        xhr.send(formData)
      })
      notify('资源已保存', 'success')
      cancelEdit(); await loadAdmin()
    } catch (e) { notify(e.message || '保存失败', 'error') }
    finally { uploading.value = false; uploadProgress.value = 0 }
  }

  async function togglePublish(release) {
    const actionText = release.published ? '下架' : '发布'
    try {
      await request(`/api/admin/releases/${release.id}/${release.published ? 'unpublish' : 'publish'}`, { method: 'POST' })
      notify(`资源已${actionText}`, 'success'); await loadAdmin()
    } catch (e) { notify(e.message || `资源${actionText}失败`, 'error') }
  }

  function openDeleteReleaseDialog(release) {
    if (release.published) { notify('已发布资源不能删除，请先下架后再删除', 'error'); return }
    deleteTarget.value = release
  }
  function closeDeleteReleaseDialog() { if (deletingRelease.value) return; deleteTarget.value = null }
  async function confirmDeleteRelease() {
    const release = deleteTarget.value
    if (!release) return
    if (release.published) { notify('已发布资源不能删除，请先下架后再删除', 'error'); deleteTarget.value = null; return }
    const shouldMoveToPreviousPage = adminPage.content.length <= 1 && adminFilters.page > 0
    deletingRelease.value = true
    try {
      await request(`/api/admin/releases/${release.id}`, { method: 'DELETE' })
      if (shouldMoveToPreviousPage) adminFilters.page -= 1
      deleteTarget.value = null
      notify('资源已删除', 'success')
    } catch (e) { notify(e.message || '删除失败', 'error'); return }
    finally { deletingRelease.value = false }
    try { await loadAdmin() } catch (e) { notify(e.message || '删除成功，但列表刷新失败', 'error') }
  }

  // ── 批量导入 ──
  function openImportPage() { showImport.value = true; Object.assign(importForm, defaultImportForm()) }
  function closeImportPage() { showImport.value = false }
  async function submitImport() {
    importing.value = true
    try {
      const result = await request('/api/admin/releases/import', { method: 'POST', body: importForm })
      importResult.value = result; showImportResultDialog.value = true; closeImportPage(); await loadAdmin()
    } catch (e) { notify(e.message || '导入失败', 'error') }
    finally { importing.value = false }
  }

  // ── 类型管理 ──
  function openCreateCategoryDialog() { categoryForm.name = ''; showCategoryDialog.value = true }
  function closeCategoryDialog() { showCategoryDialog.value = false }
  async function saveCategory() {
    if (!categoryForm.name.trim()) { notify('分类名不能为空', 'error'); return }
    try { await request('/api/admin/software-type-categories', { method: 'POST', body: { name: categoryForm.name.trim() } }); notify('已创建', 'success'); closeCategoryDialog(); await loadSoftwareMetadata() }
    catch (e) { notify(e.message || '创建失败', 'error') }
  }
  function openCreateTypeDialog() { Object.assign(typeForm, defaultTypeForm()); showTypeDialog.value = true }
  function closeTypeDialog() { showTypeDialog.value = false }
  async function saveType() {
    if (!typeForm.category || !typeForm.name.trim()) { notify('分类和名称必填', 'error'); return }
    try {
      const url = typeForm.id ? `/api/admin/software-types/${typeForm.id}` : '/api/admin/software-types'
      await request(url, { method: typeForm.id ? 'PUT' : 'POST', body: typeForm })
      notify('已保存', 'success'); closeTypeDialog(); await loadSoftwareMetadata()
    } catch (e) { notify(e.message || '保存失败', 'error') }
  }

  // ── 标准管理 ──
  function standardApiBase() { return adminSection.value === 'standardPublish' ? '/api/admin/parameter-standards' : '/api/admin/standard-documents' }
  function buildStandardTitle(selectedType) { return [selectedType?.category, selectedType?.name, standardForm.softwareVersion].filter(Boolean).join(' / ') }
  function openCreateStandardDialog(dt) { Object.assign(standardForm, defaultStandardForm()); if (dt) standardForm.documentType = dt; showStandardDialog.value = true }
  function closeStandardDialog() { showStandardDialog.value = false }
  async function saveStandard() {
    const selectedType = findSoftwareType(standardForm.softwareTypeId)
    if (!selectedType) { notify('请选择软件类型', 'error'); return }
    const apiBase = standardApiBase()
    const body = {
      id: standardForm.id,
      title: buildStandardTitle(selectedType),
      softwareTypeId: selectedType.id,
      category: selectedType.category,
      software: selectedType.name,
      softwareVersion: standardForm.softwareVersion,
      code: standardForm.code,
      content: standardForm.content || '# 参数标准\n\n'
    }
    if (adminSection.value !== 'standardPublish') { body.summary = standardForm.summary }
    const actionText = standardForm.id ? '修改' : '新增'
    try {
      await request(standardForm.id ? `${apiBase}/${standardForm.id}` : apiBase, { method: standardForm.id ? 'PUT' : 'POST', body })
      notify(`标准已${actionText}`, 'success'); closeStandardDialog(); await loadStandardDocuments()
    } catch (e) { notify(e.message || `标准${actionText}失败`, 'error') }
  }

  // ── 参数管理 ──
  function openCreateParameterDialog() { Object.assign(parameterForm, defaultParameterForm()); showParameterDialog.value = true }
  function closeParameterDialog() { showParameterDialog.value = false }
  async function saveParameter() {
    if (!parameterForm.code.trim() || !parameterForm.name.trim()) { notify('编码和名称必填', 'error'); return }
    try {
      const url = parameterForm.id ? `/api/admin/standard-parameters/${parameterForm.id}` : '/api/admin/standard-parameters'
      await request(url, { method: parameterForm.id ? 'PUT' : 'POST', body: { ...parameterForm, standardDocumentId: selectedStandard.value?.id } })
      notify('已保存', 'success'); closeParameterDialog(); await loadStandardParameters()
    } catch (e) { notify(e.message || '保存失败', 'error') }
  }
  function handleParamImportFileChange(e) { paramImportFile.value = e.target.files[0] || null }
  async function importParameters() {
    if (!paramImportFile.value) { notify('请选择文件', 'error'); return }
    paramImporting.value = true
    try {
      const fd = new FormData(); fd.append('file', paramImportFile.value)
      if (selectedStandard.value) fd.append('standardDocumentId', selectedStandard.value.id)
      const result = await request('/api/admin/standard-parameters/import', { method: 'POST', body: fd })
      paramImportResult.value = result; await loadStandardParameters(); notify('导入完成', 'success')
    } catch (e) { notify(e.message || '导入失败', 'error') }
    finally { paramImporting.value = false }
  }
  async function downloadParameterTemplate() {
    try { window.open('/api/admin/standard-parameters/template', '_blank') } catch { notify('下载失败', 'error') }
  }

  // ── 审核管理 ──
  async function loadReviews() {
    const q = reviewFilters.status ? `status=${reviewFilters.status}` : ''
    const data = await request(`/api/admin/reviews?${q}&page=${reviewPage.page}&size=${reviewPage.size}`)
    applyPage(reviewListPage, data); allReviews.value = data?.content || []
  }
  async function openReviewDetail(record) {
    try {
      const detail = await request(`/api/admin/reviews/${record.id}`)
      selectedReview.value = detail; selectedReviewDiff.value = detail.diff || '无差异信息'; reviewComment.value = ''
    } catch (e) { notify(e.message || '加载审核详情失败', 'error') }
  }
  function closeReviewDetail() { selectedReview.value = null }
  async function reviewApprove(record) {
    try {
      await request(`/api/admin/reviews/${record.id}/approve`, { method: 'POST', body: { comment: reviewComment.value || null } })
      notify('审核已通过', 'success'); closeReviewDetail(); await loadReviews(); await loadStandardDocuments()
    } catch (e) { notify(e.message || '审核通过失败', 'error') }
  }
  async function reviewReject(record) {
    try {
      await request(`/api/admin/reviews/${record.id}/reject`, { method: 'POST', body: { comment: reviewComment.value || null } })
      notify('已驳回', 'success'); closeReviewDetail(); await loadReviews(); await loadStandardDocuments()
    } catch (e) { notify(e.message || '驳回失败', 'error') }
  }

  // ── 修订历史 ──
  async function openRevisionHistory(doc, documentType) {
    revisionDocTitle.value = doc.title || doc.software || '文档'
    try {
      const list = await request(`/api/admin/revisions?documentId=${doc.id}&documentType=${encodeURIComponent(documentType || 'STANDARD_DOCUMENT')}`)
      revisionList.value = Array.isArray(list) ? list : []
    } catch (e) { revisionList.value = []; notify(e.message || '加载修订历史失败', 'error') }
    showRevisionModal.value = true
  }
  function closeRevisionModal() { showRevisionModal.value = false }

  // ── 用户管理 ──
  function openCreateUserDialog() { Object.assign(userForm, { username: '', displayName: '', password: '', role: '开发经理' }); userFormTarget.value = null; showUserDialog.value = true }
  function closeUserDialog() { showUserDialog.value = false }
  async function createUser() {
    if (!userForm.username.trim() || !userForm.password) { notify('账号和密码必填', 'error'); return }
    try {
      const pwHash = auth.token ? await sha256Hash(userForm.password) : userForm.password
      await request('/api/admin/users', { method: 'POST', body: { ...userForm, password: pwHash } })
      notify('用户已创建', 'success'); closeUserDialog(); await loadUsers()
    } catch (e) { notify(e.message || '创建失败', 'error') }
  }
  function openRoleDialog(user) { userFormTarget.value = user; userForm.role = user.role; showRoleDialog.value = true }
  function closeRoleDialog() { showRoleDialog.value = false }
  async function changeUserRole() {
    try { await request(`/api/admin/users/${userFormTarget.value.id}/role`, { method: 'PUT', body: { role: userForm.role } }); notify('已修改', 'success'); closeRoleDialog(); await loadUsers() }
    catch (e) { notify(e.message || '修改失败', 'error') }
  }
  async function deleteUserAccount(user) {
    confirm(`确定删除用户 ${user.username}？`, async () => {
      try { await request(`/api/admin/users/${user.id}`, { method: 'DELETE' }); notify('已删除', 'success'); await loadUsers() }
      catch (e) { notify(e.message || '删除失败', 'error') }
    })
  }

  function switchAdminSection(s) {
    adminSection.value = s
    showImport.value = false
    editing.value = false
    selectedStandard.value = null
    if (s === 'types') {
      loadSoftwareTypes(); loadSoftwareCategories()
    } else if (s === 'standardPublish' || s === 'documentMaintenance') {
      loadSoftwareTypes(); loadSoftwareCategories(); loadStandardDocuments(); loadAllParameterStandards()
    } else if (s === 'users') {
      loadUsers()
    } else if (s === 'reviews') {
      loadReviews()
    } else if (s === 'settings') {
      loadSystemSettings()
    } else {
      loadAdmin(); loadSoftwareTypes(); loadSoftwareCategories(); loadAllParameterStandards()
    }
  }
  function changeAdminPage(page) { adminFilters.page = page; loadAdmin() }

  // ── Computed 属性 ──
  function uniqueOptions(values) { return [...new Set(values.map(value => (value || '').trim()).filter(Boolean))] }
  function softwareTypesByCategory(category, onlyActive = false) {
    if (!category) return []
    const source = onlyActive ? softwareTypes.value.filter(t => t.active) : softwareTypes.value
    return source.filter(type => type.category === category)
  }

  const activeSoftwareTypes = computed(() => softwareTypes.value.filter(type => type.active))
  const defaultSoftwareCategories = ['中间件', '主机', '数据库', '安全', '网络']
  const softwareTypeCategories = computed(() => uniqueOptions([
    ...defaultSoftwareCategories,
    ...softwareCategories.value,
    ...softwareTypes.value.map(type => type.category)
  ]))
  const activeTypeCategories = computed(() => uniqueOptions(activeSoftwareTypes.value.map(type => type.category)))
  const releaseCategoryOptions = computed(() => releaseForm.id ? softwareTypeCategories.value : activeTypeCategories.value)
  const releaseSoftwareOptions = computed(() => softwareTypesByCategory(releaseForm.category, !releaseForm.id))
  const releaseStandardOptions = computed(() => {
    const selectedType = softwareTypes.value.find(t => String(t.id) === String(releaseForm.softwareTypeId))
    const softwareName = selectedType?.name || ''
    return allParameterStandards.value.filter(s => s.category === releaseForm.category && (!softwareName || s.software === softwareName))
  })
  const releaseParameterStandardOptions = computed(() => {
    const selectedType = softwareTypes.value.find(t => String(t.id) === String(releaseForm.softwareTypeId))
    const softwareName = selectedType?.name || ''
    return allParameterStandards.value.filter(s => s.status === 'PUBLISHED' && s.category === releaseForm.category && (!softwareName || s.software === softwareName))
  })
  const importSoftwareOptions = computed(() => softwareTypesByCategory(importForm.category, true))
  const standardCategoryOptions = computed(() => standardForm.id ? softwareTypeCategories.value : activeTypeCategories.value)
  const standardSoftwareOptions = computed(() => softwareTypesByCategory(standardForm.category, !standardForm.id))
  const filteredSoftwareTypes = computed(() => {
    const name = typeFilters.name.trim().toLowerCase()
    return softwareTypes.value.filter(type => {
      const matchesCategory = !typeFilters.category || type.category === typeFilters.category
      const matchesName = !name || type.name.toLowerCase().includes(name)
      return matchesCategory && matchesName
    })
  })
  const typePageComputed = computed(() => {
    const totalElements = filteredSoftwareTypes.value.length
    const totalPages = Math.max(Math.ceil(totalElements / typeFilters.size), 1)
    const page = Math.min(typeFilters.page, totalPages - 1)
    return { content: [], page, size: typeFilters.size, totalElements, totalPages, first: page <= 0, last: page >= totalPages - 1 }
  })
  const pagedSoftwareTypes = computed(() => {
    const page = typePageComputed.value.page
    const start = page * typeFilters.size
    return filteredSoftwareTypes.value.slice(start, start + typeFilters.size)
  })
  const filteredStandardDocuments = computed(() => {
    const keyword = standardFilters.keyword.trim().toLowerCase()
    return standardDocuments.value.filter(doc => {
      if (adminSection.value !== 'standardPublish' && doc.documentType !== 'STANDARD') return false
      const matchesCategory = !standardFilters.category || doc.category === standardFilters.category
      const matchesStatus = !standardFilters.status || doc.status === standardFilters.status
      const matchesSoftware = !standardFilters.software || doc.software === standardFilters.software
      const matchesKeyword = !keyword || doc.title.toLowerCase().includes(keyword) || (doc.summary || '').toLowerCase().includes(keyword) || (doc.softwareVersion || '').toLowerCase().includes(keyword)
      return matchesCategory && matchesStatus && matchesSoftware && matchesKeyword
    })
  })
  const standardDocumentOptions = computed(() => {
    if (adminSection.value === 'standardPublish') return standardDocuments.value
    return allParameterStandards.value
  })
  const standardPageComputed = computed(() => {
    const totalElements = filteredStandardDocuments.value.length
    const totalPages = Math.max(Math.ceil(totalElements / standardFilters.size), 1)
    const page = Math.min(standardFilters.page, totalPages - 1)
    return { content: [], page, size: standardFilters.size, totalElements, totalPages, first: page <= 0, last: page >= totalPages - 1 }
  })
  const selectedStandardParameters = computed(() => {
    if (!selectedStandard.value) return []
    return standardParameters.value.filter(parameter => {
      const pid = parameter.parameterStandardId || parameter.standardDocumentId
      return String(pid) === String(selectedStandard.value.id)
    })
  })
  const maintenanceDocumentsComputed = computed(() => {
    const keyword = maintenanceDocumentFilters.keyword.trim().toLowerCase()
    return standardDocuments.value.filter(doc => {
      if (doc.documentType !== 'MANUAL' && doc.documentType !== 'ARTICLE') return false
      const matchesType = !maintenanceDocumentFilters.documentType || doc.documentType === maintenanceDocumentFilters.documentType
      const matchesStatus = !maintenanceDocumentFilters.status || doc.status === maintenanceDocumentFilters.status
      const matchesKeyword = !keyword || doc.title.toLowerCase().includes(keyword) || (doc.summary || '').toLowerCase().includes(keyword)
      return matchesType && matchesStatus && matchesKeyword
    })
  })
  const maintenanceDocumentPageComputed = computed(() => {
    const totalElements = maintenanceDocumentsComputed.value.length
    const totalPages = Math.max(Math.ceil(totalElements / maintenanceDocumentFilters.size), 1)
    const page = Math.min(maintenanceDocumentFilters.page, totalPages - 1)
    return { content: [], page, size: maintenanceDocumentFilters.size, totalElements, totalPages, first: page <= 0, last: page >= totalPages - 1 }
  })
  const pagedMaintenanceDocuments = computed(() => {
    const page = maintenanceDocumentPageComputed.value.page
    const start = page * maintenanceDocumentFilters.size
    return maintenanceDocumentsComputed.value.slice(start, start + maintenanceDocumentFilters.size)
  })

  return {
    // State
    adminSection, showPassword, showImport, importing, importResult, showImportResultDialog,
    editing, uploading, uploadProgress, deleteTarget, deletingRelease,
    showTypeDialog, showCategoryDialog, softwareCategories, softwareTypes,
    showStandardDialog, showParameterDialog, showParamImportDialog, paramImporting, paramImportResult, paramImportFile,
    allParameterStandards, standardDocuments, standardParameters, selectedStandard,
    selectedReview, selectedReviewDiff, reviewComment, allReviews, showRevisionModal, revisionList, revisionDocTitle,
    showUserDialog, showRoleDialog, userFormTarget, userList, allRoles, systemSettings,
    adminFilters, typeFilters, standardFilters, parameterFilters, maintenanceDocumentFilters, reviewFilters, reviewPage,
    adminPage, typePage, standardPage, parameterPage, maintenanceDocumentPage, reviewListPage,
    releaseForm, importForm, passwordForm, categoryForm, typeForm, standardForm, parameterForm, userForm,
    // Computed
    activeSoftwareTypes, softwareTypeCategories, activeTypeCategories,
    releaseCategoryOptions, releaseSoftwareOptions, releaseStandardOptions, releaseParameterStandardOptions,
    importSoftwareOptions, standardCategoryOptions, standardSoftwareOptions,
    filteredSoftwareTypes, typePageComputed, pagedSoftwareTypes,
    filteredStandardDocuments, standardDocumentOptions, standardPageComputed, selectedStandardParameters,
    maintenanceDocumentsComputed, maintenanceDocumentPageComputed, pagedMaintenanceDocuments,
    // Functions
    loadAdmin, loadSoftwareTypes, loadSoftwareCategories, loadSoftwareMetadata,
    loadStandardDocuments, loadAllParameterStandards, loadStandardParameters, loadSystemSettings, saveSystemSettings,
    loadUsers, loadRoles, changePassword,
    startCreate, startEdit, cancelEdit, handleReleaseFileChange, saveRelease, togglePublish,
    openDeleteReleaseDialog, closeDeleteReleaseDialog, confirmDeleteRelease,
    openImportPage, closeImportPage, submitImport,
    openCreateCategoryDialog, closeCategoryDialog, saveCategory,
    openCreateTypeDialog, closeTypeDialog, saveType,
    openCreateStandardDialog, closeStandardDialog, saveStandard,
    openCreateParameterDialog, closeParameterDialog, saveParameter, handleParamImportFileChange, importParameters, downloadParameterTemplate,
    loadReviews, openReviewDetail, closeReviewDetail, reviewApprove, reviewReject,
    openRevisionHistory, closeRevisionModal,
    openCreateUserDialog, closeUserDialog, createUser, openRoleDialog, closeRoleDialog, changeUserRole, deleteUserAccount,
    switchAdminSection, changeAdminPage
  }
}
