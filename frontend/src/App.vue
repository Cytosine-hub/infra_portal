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

      <div v-if="downloading" class="download-progress-overlay">
        <div class="download-progress-card">
          <div class="download-progress-title">正在下载：{{ downloadFileName }}</div>
          <div class="progress-track large">
            <div class="progress-fill" :style="{ width: downloadProgress + '%' }"></div>
          </div>
          <div class="download-progress-info">
            <span>{{ downloadProgress }}%</span>
          </div>
        </div>
      </div>
    </main>

    <FormModal v-model="editing" :title="releaseForm.id ? '编辑资源' : '新增资源'" width="700px" @submit="saveRelease">
        <div class="form-grid">
          <label>分类
            <select v-model="releaseForm.category" required @change="releaseForm.softwareTypeId = ''">
              <option value="">请选择分类</option>
              <option v-for="category in releaseCategoryOptions" :key="category" :value="category">{{ category }}</option>
            </select>
          </label>
          <label>软件
            <select v-model="releaseForm.softwareTypeId" :disabled="!releaseForm.category" required>
              <option value="">请选择软件</option>
              <option v-for="type in releaseSoftwareOptions" :key="type.id" :value="type.id">{{ type.name }}</option>
            </select>
          </label>
          <label>版本号<input v-model.trim="releaseForm.version" required maxlength="60" /></label>
          <label>平台<input v-model.trim="releaseForm.platform" maxlength="60" /></label>
          <label>发布日期<input v-model="releaseForm.releasedAt" type="date" /></label>
          <label class="checkline"><input v-model="releaseForm.published" type="checkbox" />发布</label>
          <label>关联标准
            <select v-model="releaseForm.standardDocumentId" :disabled="!releaseForm.category || !releaseForm.softwareTypeId">
              <option :value="null">不关联</option>
              <option v-for="doc in releaseStandardOptions" :key="doc.id" :value="doc.id">{{ getStandardLabel(doc.id) }}</option>
            </select>
          </label>
          <label class="checkline"><input v-model="releaseForm.standardPackage" type="checkbox" />标准包</label>
          <label v-if="releaseForm.standardPackage">关联参数标准
            <select v-model="releaseForm.parameterStandardId" :disabled="!releaseForm.category || !releaseForm.softwareTypeId">
              <option :value="null">请选择参数标准</option>
              <option v-for="ps in releaseParameterStandardOptions" :key="ps.id" :value="ps.id">{{ ps.title }}</option>
            </select>
          </label>
          <label class="file-field">安装包
            <span class="file-control">
              <input type="file" @change="handleReleaseFileChange" />
              <span class="file-button">选择文件</span>
              <span class="file-name">{{ releaseForm.file?.name || releaseForm.originalFileName || '未选择文件' }}</span>
            </span>
          </label>
          <label class="wide">说明<textarea v-model.trim="releaseForm.description" maxlength="2000" /></label>
        </div>
        <div v-if="uploading" class="upload-progress-bar">
          <div class="progress-track">
            <div class="progress-fill" :style="{ width: uploadProgress + '%' }"></div>
          </div>
          <span class="progress-text">上传中 {{ uploadProgress }}%</span>
        </div>
      <template #actions>
        <BaseButton type="submit" :loading="uploading">{{ uploading ? '上传中...' : '保存' }}</BaseButton>
        <BaseButton variant="ghost" @click="cancelEdit()" :disabled="uploading">取消</BaseButton>
      </template>
    </FormModal>

    <FormModal v-model="showImport" title="批量导入" width="700px" @submit="submitImport">
        <p class="muted" style="margin:0 0 12px">扫描指定目录并按所选软件导入安装包资源。</p>
        <div class="form-grid">
          <label class="wide">目录路径<input v-model.trim="importForm.sourceDirectory" :disabled="importing" required /></label>
          <label>分类
            <select v-model="importForm.category" :disabled="importing" required @change="importForm.softwareTypeId = ''">
              <option value="">请选择分类</option>
              <option v-for="category in activeTypeCategories" :key="category" :value="category">{{ category }}</option>
            </select>
          </label>
          <label>软件
            <select v-model="importForm.softwareTypeId" :disabled="importing || !importForm.category" required>
              <option value="">请选择软件</option>
              <option v-for="type in importSoftwareOptions" :key="type.id" :value="type.id">{{ type.name }}</option>
            </select>
          </label>
          <label>平台<input v-model.trim="importForm.platform" :disabled="importing" /></label>
          <label class="checkline"><input v-model="importForm.recursive" :disabled="importing" type="checkbox" />递归扫描</label>
          <label class="checkline"><input v-model="importForm.published" :disabled="importing" type="checkbox" />导入后发布</label>
          <label class="wide">说明<textarea v-model.trim="importForm.description" :disabled="importing" /></label>
        </div>
        <div v-if="importing" class="loading-panel">
          <LoadingSpinner text="正在导入，请稍候..." />
          <p>正在扫描目录并写入资源记录，导入完成后会显示结果。</p>
        </div>
      <template #actions>
        <BaseButton type="submit" :loading="importing">{{ importing ? '导入中...' : '开始导入' }}</BaseButton>
        <BaseButton variant="ghost" :disabled="importing" @click="closeImportPage()">取消</BaseButton>
      </template>
    </FormModal>

    <FormModal v-model="showTypeDialog" :title="typeForm.id ? '编辑类型' : '新增类型'" @submit="saveType">
      <div class="form-grid single">
        <label>分类
          <select v-model="typeForm.category" required>
            <option value="">请选择分类</option>
            <option v-for="category in softwareTypeCategories" :key="category" :value="category">{{ category }}</option>
          </select>
        </label>
        <label>软件类型名称<input v-model.trim="typeForm.name" required maxlength="120" /></label>
        <label>说明<textarea v-model.trim="typeForm.description" maxlength="500" /></label>
        <label class="checkline"><input v-model="typeForm.active" type="checkbox" />启用</label>
      </div>
    </FormModal>

    <FormModal v-model="showCategoryDialog" title="新增分类" @submit="saveCategory">
      <div class="form-grid single">
        <label>分类名称<input v-model.trim="categoryForm.name" required maxlength="40" placeholder="例如 中间件、数据库、应用软件" /></label>
      </div>
    </FormModal>

    <FormModal v-model="showStandardDialog" :title="standardForm.id ? '编辑标准' : '新增标准'" @submit="saveStandard">
        <div class="form-grid single">
          <label>分类
            <select v-model="standardForm.category" required @change="standardForm.softwareTypeId = ''">
              <option value="">请选择分类</option>
              <option v-for="category in standardCategoryOptions" :key="category" :value="category">{{ category }}</option>
            </select>
          </label>
          <label>软件
            <select v-model="standardForm.softwareTypeId" :disabled="!standardForm.category" required>
              <option value="">请选择软件</option>
              <option v-for="type in standardSoftwareOptions" :key="type.id" :value="type.id">
                {{ type.name }}{{ type.active ? '' : '（停用）' }}
              </option>
            </select>
          </label>
          <label>软件版本<input v-model.trim="standardForm.softwareVersion" required maxlength="80" /></label>
          <label>编码<input v-model.trim="standardForm.code" maxlength="20" /></label>
          <label v-if="adminSection !== 'standardPublish'">说明<textarea v-model.trim="standardForm.summary" maxlength="500" /></label>
        </div>
    </FormModal>

    <FormModal v-model="showParameterDialog" :title="parameterForm.id ? '编辑参数' : '新增参数'" @submit="saveParameter">
      <div class="form-grid single">
        <label>参数编码<input v-model.trim="parameterForm.code" required maxlength="80" placeholder="例如 JDK_VERSION" /></label>
        <label>参数名称<input v-model.trim="parameterForm.name" required maxlength="120" /></label>
        <label>参数值<input v-model.trim="parameterForm.value" required maxlength="500" /></label>
        <label>分类<input v-model.trim="parameterForm.category" maxlength="60" /></label>
        <label>说明<textarea v-model.trim="parameterForm.description" maxlength="500" /></label>
        <label class="checkline"><input v-model="parameterForm.active" type="checkbox" />启用</label>
        <label class="checkline"><input v-model="parameterForm.deploymentStandard" type="checkbox" />是否为部署标准</label>
      </div>
    </FormModal>

    <FormModal v-model="showParamImportDialog" title="批量导入参数" submitText="开始导入" :submitDisabled="paramImporting" @submit="importParameters">
      <div class="form-grid single">
        <p class="muted" style="margin:0 0 12px">请先下载模板，按格式填写后上传 Excel 文件。支持的列：参数编码、参数名称、参数值、分类、说明、是否启用（是/否）、是否部署标准（是/否）。</p>
        <label class="file-field">选择 Excel 文件
          <span class="file-control">
            <input type="file" accept=".xlsx,.xls" @change="handleParamImportFileChange" required />
            <span class="file-button">选择文件</span>
            <span class="file-name">{{ paramImportFile?.name || '未选择文件' }}</span>
          </span>
        </label>
      </div>
      <div v-if="paramImportResult" class="import-result">
        <p>导入完成：成功 <strong>{{ paramImportResult.imported }}</strong> 条，跳过 <strong>{{ paramImportResult.skipped }}</strong> 条</p>
        <ul v-if="paramImportResult.errors.length > 0">
          <li v-for="(err, idx) in paramImportResult.errors" :key="idx" class="import-error">{{ err }}</li>
        </ul>
      </div>
      <template #actions>
        <BaseButton variant="ghost" @click="downloadParameterTemplate()">下载模板</BaseButton>
        <BaseButton type="submit" :loading="paramImporting">{{ paramImporting ? '导入中...' : '开始导入' }}</BaseButton>
        <BaseButton variant="ghost" @click="showParamImportDialog = false; paramImportResult = null">关闭</BaseButton>
      </template>
    </FormModal>

    <div v-if="selectedPreviewDocument" class="modal-backdrop doc-preview-backdrop">
      <div class="doc-preview-full">
        <div class="doc-preview-toolbar">
          <h3>文档预览</h3>
          <button type="button" class="ghost" @click="closePreviewDocument()">返回列表</button>
        </div>
        <div class="doc-preview-layout">
          <aside class="post-dir-panel">
            <div class="post-dir-header">
              <h3>文档列表</h3>
            </div>
            <div class="post-dir-list">
              <button
                v-for="doc in maintenanceDocuments"
                :key="doc.id"
                :class="['post-dir-item', { active: String(doc.id) === String(selectedPreviewDocument?.id) }]"
                @click="previewDocument(doc)"
              >{{ displayTitle(doc) }}</button>
            </div>
          </aside>
          <article class="doc-preview-main">
            <div class="post-article">
              <h1 class="post-title">{{ displayTitle(selectedPreviewDocument) }}</h1>
              <div class="post-author-line">
                <span class="post-date">{{ documentTypeLabel(selectedPreviewDocument.documentType) }}</span>
                <span class="post-date">{{ selectedPreviewDocument.category || '-' }} / {{ selectedPreviewDocument.software || '-' }}</span>
                <span class="post-date">{{ formatDate(selectedPreviewDocument.updatedAt) }}</span>
              </div>
              <p v-if="selectedPreviewDocument.summary" class="description" style="margin-bottom:16px">{{ selectedPreviewDocument.summary }}</p>
              <div class="post-body markdown-preview" v-html="previewRenderedHtml"></div>
            </div>
          </article>
          <aside class="post-toc-panel" v-if="previewTocItems.length">
            <h4 class="toc-title">文档大纲</h4>
            <button
              v-for="item in previewTocItems"
              :key="item.id"
              :class="['toc-link', { active: previewTocActiveId === item.id }]"
              :style="{ '--toc-level': item.level - 1 }"
              @click="scrollToPreviewHeading(item.id)"
            >{{ item.text }}</button>
          </aside>
        </div>
      </div>
    </div>

    <FormModal v-model="showUserDialog" title="新增用户" submitText="创建" @submit="createUser">
      <div class="form-grid single">
        <label>账号<input v-model.trim="userForm.username" required minlength="2" maxlength="60" placeholder="登录账号" /></label>
        <label>用户名<input v-model.trim="userForm.displayName" maxlength="60" placeholder="显示名称（可选）" /></label>
        <label>密码<input v-model="userForm.password" type="password" required minlength="6" maxlength="64" placeholder="至少6位" /></label>
        <label>角色
          <select v-model="userForm.role" required>
            <option v-for="r in allRoles" :key="r.name" :value="r.name">{{ r.name }}</option>
          </select>
        </label>
      </div>
    </FormModal>

    <FormModal v-model="showRoleDialog" :title="'修改角色 — ' + (userFormTarget?.username || '')" @submit="changeUserRole">
      <label>角色
        <select v-model="userForm.role" required>
          <option v-for="r in allRoles" :key="r.name" :value="r.name">{{ r.name }}</option>
        </select>
      </label>
    </FormModal>

    <BaseModal v-model="showImportResultDialog" title="导入结果">
      <div class="result-grid">
        <div><span>扫描文件</span><strong>{{ importResult?.scannedCount ?? 0 }}</strong></div>
        <div><span>成功导入</span><strong>{{ importResult?.importedCount ?? 0 }}</strong></div>
        <div><span>跳过文件</span><strong>{{ importResult?.skippedCount ?? 0 }}</strong></div>
        <div><span>失败文件</span><strong>{{ importResult?.failedCount ?? 0 }}</strong></div>
      </div>
      <template #footer>
        <BaseButton @click="showImportResultDialog = false">确定</BaseButton>
      </template>
    </BaseModal>

    <BaseModal :modelValue="!!deleteTarget" @update:modelValue="closeDeleteReleaseDialog()" title="删除资源" width="400px">
      <p class="confirm-message">
        确认删除 {{ deleteTarget?.middlewareName }} {{ deleteTarget?.version }}？
      </p>
      <template #footer>
        <BaseButton variant="ghost" :disabled="deletingRelease" @click="closeDeleteReleaseDialog()">取消</BaseButton>
        <BaseButton variant="danger" :disabled="deletingRelease" :loading="deletingRelease" @click="confirmDeleteRelease()">确认删除</BaseButton>
      </template>
    </BaseModal>

    <BaseModal v-model="showRevisionModal" :title="revisionDocTitle + ' - 修订历史'" width="700px">
        <div v-if="revisionList.length === 0" class="empty-state" style="padding:40px 0">暂无修订记录</div>
        <div v-else class="revision-list">
          <div v-for="rev in revisionList" :key="rev.id" class="revision-item">
            <div class="revision-header">
              <span class="revision-version">V{{ rev.version }}</span>
              <span class="revision-time">{{ formatTime(rev.revisedAt) }}</span>
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

    <BaseModal :modelValue="!!selectedReview" @update:modelValue="closeReviewDetail()" :title="selectedReview?.documentType === 'PARAMETER_STANDARD' ? [selectedReview?.category, selectedReview?.software].filter(Boolean).join(' / ') : (selectedReview?.documentTitle || '')" width="700px">
      <p class="muted" style="margin: 0 0 12px">
        <span :class="['status', reviewStatusClass(selectedReview?.status)]">{{ selectedReview?.statusLabel }}</span>
        V{{ selectedReview?.documentVersion || '-' }} · {{ selectedReview?.category || '-' }} / {{ selectedReview?.software || '-' }}
      </p>
      <div class="review-meta">
        <p>提交人：{{ selectedReview?.submitterDisplayName || selectedReview?.submitterUsername }} · 提交时间：{{ formatTime(selectedReview?.submittedAt) }}</p>
        <p v-if="selectedReview?.reviewerUsername">审核人：{{ selectedReview?.reviewerUsername }} · 审核时间：{{ formatTime(selectedReview?.reviewedAt) }}</p>
        <p v-if="selectedReview?.reviewComment">审核意见：{{ selectedReview?.reviewComment }}</p>
      </div>
      <div class="diff-view">
        <h4>版本差异对比</h4>
        <pre class="diff-content"><template v-for="(line, idx) in diffLines" :key="idx"><span :class="['diff-line', line.startsWith('+') ? 'diff-line-add' : line.startsWith('-') ? 'diff-line-del' : line.startsWith('@@') ? 'diff-line-info' : '' ]">{{ line }}</span>
</template></pre>
      </div>
      <div v-if="selectedReview?.status === 'PENDING' && (isSysAdmin || (isCategoryAdmin && managedCategory === selectedReview?.category))" class="review-actions-panel">
        <div class="form-grid single">
          <label>审核意见<textarea v-model.trim="reviewComment" maxlength="1000" placeholder="请输入审核意见（可选）" /></label>
        </div>
        <div class="form-actions">
          <BaseButton variant="ghost" @click="closeReviewDetail()">取消</BaseButton>
          <BaseButton variant="danger" @click="reviewReject(selectedReview)">驳回</BaseButton>
          <BaseButton variant="success" @click="reviewApprove(selectedReview)">审核通过</BaseButton>
        </div>
      </div>
    </BaseModal>

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
import { formatBytes, formatDetail, formatDate, renderMarkdown, documentTypeLabel } from './utils'
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
import FormModal from './components/ui/FormModal.vue'
import BaseModal from './components/ui/BaseModal.vue'
import BaseButton from './components/ui/BaseButton.vue'
import LoadingSpinner from './components/ui/LoadingSpinner.vue'
import { useAdmin } from './composables/useAdmin'

const { auth, login: authLogin, logout: authLogout, restoreAuth, sha256,
  currentUserRole, isSysAdmin, isCategoryAdmin, isManager, canAccessAdmin, isReadOnly, managedCategory } = useAuth()
const { notice, notify, confirmDialog, confirm: confirmAction, handleConfirm, cancelConfirm } = useNotify()
const { route, navigate } = useRoute()

// ── 管理后台 composable（仅状态，函数保留 App.vue 版本以保持副作用逻辑）──
const admin = useAdmin(auth, notify, confirmAction)
const {
  adminSection, showPassword, showImport, importing, importResult, showImportResultDialog,
  editing, uploading, uploadProgress, deleteTarget, deletingRelease,
  showTypeDialog, showCategoryDialog, softwareCategories, softwareTypes,
  showStandardDialog, showParameterDialog, showParamImportDialog, paramImporting, paramImportResult, paramImportFile,
  allParameterStandards, standardDocuments, standardParameters, selectedStandard,
  selectedReview, selectedReviewDiff, reviewComment, allReviews, showRevisionModal, revisionList, revisionDocTitle,
  showUserDialog, showRoleDialog, userFormTarget, userList, allRoles, systemSettings,
  adminFilters, typeFilters, standardFilters, parameterFilters, maintenanceDocumentFilters, reviewFilters, reviewPage,
  adminPage, releaseForm, importForm, passwordForm, categoryForm, typeForm, standardForm, parameterForm, userForm
} = admin

const markdown = new MarkdownIt({ html: false, linkify: true, breaks: true })
const siteConfig = reactive({ knowledgeEnabled: true, diagnosticsEnabled: true })
const downloading = ref(false)
const downloadProgress = ref(0)
const downloadFileName = ref('')
// 公共页面状态已迁移到各页面组件（HomePage/DownloadsPage/StandardsPage）
// ── 管理后台状态已迁移到 composables/useAdmin.js ──
const loginForm = reactive({ username: '', password: '' })
const selectedPreviewDocument = ref(null)
const previewTocActiveId = ref('')
// 审核/差异对话框状态已迁移到 ReviewsSection

// ── 常用命令已迁移到 CommandsPage.vue ──

// ── RBAC helpers 已迁移到 composables/useAuth.js ──

const adminPublishedParam = computed(() => adminFilters.published === '' ? '' : `&published=${adminFilters.published}`)
const adminSectionLabel = computed(() => {
  if (adminSection.value === 'files') return { eyebrow: 'Files', title: '文件管理' }
  if (adminSection.value === 'types') return { eyebrow: 'Types', title: '类型管理' }
  if (adminSection.value === 'standardPublish') return { eyebrow: 'Standards', title: '参数标准' }
  if (adminSection.value === 'documentMaintenance') return { eyebrow: 'Documents', title: '标准文档' }
  if (adminSection.value === 'reviews') return { eyebrow: 'Reviews', title: '审核管理' }
  if (adminSection.value === 'settings') return { eyebrow: 'Settings', title: '系统设置' }
  return { eyebrow: 'Admin', title: '用户管理' }
})
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

// 公共标准页面 computed 已迁移到 StandardsPage.vue

// 公共标准页面 computed 已迁移到 StandardsPage.vue

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
const standardFilterSoftwareOptions = computed(() => softwareTypesByCategory(standardFilters.category, false))
const filteredSoftwareTypes = computed(() => {
  const name = typeFilters.name.trim().toLowerCase()
  return softwareTypes.value.filter(type => {
    const matchesCategory = !typeFilters.category || type.category === typeFilters.category
    const matchesName = !name || type.name.toLowerCase().includes(name)
    return matchesCategory && matchesName
  })
})
const typePage = computed(() => {
  const totalElements = filteredSoftwareTypes.value.length
  const totalPages = Math.max(Math.ceil(totalElements / typeFilters.size), 1)
  const page = Math.min(typeFilters.page, totalPages - 1)
  return {
    content: [],
    page,
    size: typeFilters.size,
    totalElements,
    totalPages,
    first: page <= 0,
    last: page >= totalPages - 1
  }
})
const pagedSoftwareTypes = computed(() => {
  const page = typePage.value.page
  const start = page * typeFilters.size
  return filteredSoftwareTypes.value.slice(start, start + typeFilters.size)
})
const filteredStandardDocuments = computed(() => {
  const keyword = standardFilters.keyword.trim().toLowerCase()
  return standardDocuments.value.filter(doc => {
    if (adminSection.value !== 'standardPublish' && doc.documentType !== 'STANDARD') return false
    const matchesCategory = !standardFilters.category || doc.category === standardFilters.category
    const matchesManagedCategory = !managedCategory.value || doc.category === managedCategory.value
    const matchesStatus = !standardFilters.status || doc.status === standardFilters.status
    const matchesSoftware = !standardFilters.software || doc.software === standardFilters.software
    const matchesKeyword = !keyword ||
      doc.title.toLowerCase().includes(keyword) ||
      (doc.summary || '').toLowerCase().includes(keyword) ||
      (doc.softwareVersion || '').toLowerCase().includes(keyword)
    return matchesCategory && matchesManagedCategory && matchesStatus && matchesSoftware && matchesKeyword
  })
})
const standardDocumentOptions = computed(() => {
  if (adminSection.value === 'standardPublish') return standardDocuments.value
  return allParameterStandards.value
})
const standardDocumentCategories = computed(() => uniqueOptions(standardDocumentOptions.value.map(doc => doc.category)))
const standardPage = computed(() => {
  const totalElements = filteredStandardDocuments.value.length
  const totalPages = Math.max(Math.ceil(totalElements / standardFilters.size), 1)
  const page = Math.min(standardFilters.page, totalPages - 1)
  return { content: [], page, size: standardFilters.size, totalElements, totalPages, first: page <= 0, last: page >= totalPages - 1 }
})
const pagedStandardDocuments = computed(() => {
  const start = standardPage.value.page * standardFilters.size
  return filteredStandardDocuments.value.slice(start, start + standardFilters.size)
})
const parameterPage = computed(() => {
  const totalElements = standardParameters.value.length
  const totalPages = Math.max(Math.ceil(totalElements / parameterFilters.size), 1)
  const page = Math.min(parameterFilters.page, totalPages - 1)
  return { content: [], page, size: parameterFilters.size, totalElements, totalPages, first: page <= 0, last: page >= totalPages - 1 }
})
const pagedStandardParameters = computed(() => {
  const start = parameterPage.value.page * parameterFilters.size
  return standardParameters.value.slice(start, start + parameterFilters.size)
})
const selectedStandardParameters = computed(() => {
  if (!selectedStandard.value) return []
  return standardParameters.value.filter(parameter => {
    const pid = parameter.parameterStandardId || parameter.standardDocumentId
    return String(pid) === String(selectedStandard.value.id)
  })
})
const maintenanceDocuments = computed(() => {
  const keyword = maintenanceDocumentFilters.keyword.trim().toLowerCase()
  return standardDocuments.value.filter(doc => {
    if (doc.documentType === 'STANDARD') return false
    if (managedCategory.value && doc.category !== managedCategory.value) return false
    const matchesType = !maintenanceDocumentFilters.documentType || doc.documentType === maintenanceDocumentFilters.documentType
    const matchesStatus = !maintenanceDocumentFilters.status || doc.status === maintenanceDocumentFilters.status
    const matchesKeyword = !keyword ||
      doc.title.toLowerCase().includes(keyword) ||
      (doc.summary || '').toLowerCase().includes(keyword) ||
      (doc.content || '').toLowerCase().includes(keyword)
    return matchesType && matchesStatus && matchesKeyword
  })
})
const maintenanceDocumentPage = computed(() => {
  const totalElements = maintenanceDocuments.value.length
  const totalPages = Math.max(Math.ceil(totalElements / maintenanceDocumentFilters.size), 1)
  const page = Math.min(maintenanceDocumentFilters.page, totalPages - 1)
  return { content: [], page, size: maintenanceDocumentFilters.size, totalElements, totalPages, first: page <= 0, last: page >= totalPages - 1 }
})
const pagedMaintenanceDocuments = computed(() => {
  const start = maintenanceDocumentPage.value.page * maintenanceDocumentFilters.size
  return maintenanceDocuments.value.slice(start, start + maintenanceDocumentFilters.size)
})

const previewRenderedHtml = computed(() => {
  const doc = selectedPreviewDocument.value
  if (!doc) return ''
  let rendered = doc.renderedContent || doc.content || ''
  for (const param of standardParameters.value) {
    rendered = rendered.split(`{{${param.code}}}`).join(param.value)
  }
  let html = renderMarkdown(rendered)
  let idx = 0
  html = html.replace(/<(h[1-3])([^>]*)>([\s\S]*?)<\/\1>/g, (_, tag, attrs, inner) => {
    if (/id=/.test(attrs)) return `<${tag}${attrs}>${inner}</${tag}>`
    return `<${tag}${attrs} id="pv-toc-${idx++}">${inner}</${tag}>`
  })
  return html
})

const previewTocItems = computed(() => {
  const items = []
  const re = /<(h[1-3])[^>]*id="([^"]*)"[^>]*>([\s\S]*?)<\/\1>/g
  let m
  while ((m = re.exec(previewRenderedHtml.value))) {
    const level = parseInt(m[1][1])
    const id = m[2]
    const text = m[3].replace(/<[^>]+>/g, '').trim()
    if (text) items.push({ level, id, text })
  }
  return items
})

function emptyPage(size) {
  return { content: [], page: 0, size, totalElements: 0, totalPages: 0, first: true, last: true }
}

function uniqueOptions(values) {
  return [...new Set(values.map(value => (value || '').trim()).filter(Boolean))]
}

function todayString() {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

function softwareTypesByCategory(category, onlyActive = false) {
  if (!category) return []
  const source = onlyActive ? activeSoftwareTypes.value : softwareTypes.value
  return source.filter(type => type.category === category)
}

function defaultReleaseForm() {
  return { id: null, category: '', softwareTypeId: '', middlewareName: '', version: '', platform: '', description: '', releasedAt: todayString(), published: false, file: null, originalFileName: '', standardDocumentId: null, standardPackage: false, parameterStandardId: null }
}

function defaultImportForm() {
  return { sourceDirectory: '', category: '', softwareTypeId: '', middlewareName: '', platform: '', description: '', published: false, recursive: true }
}

function defaultTypeForm() {
  return { id: null, category: '中间件', name: '', description: '', active: true }
}

function defaultStandardForm() {
  return {
    id: null,
    category: '',
    softwareTypeId: '',
    softwareVersion: '',
    code: '',
    summary: '',
    content: '# 参数标准\n\n'
  }
}

function defaultParameterForm() {
  return { id: null, standardDocumentId: null, parameterStandardId: null, code: '', name: '', value: '', category: '', description: '', active: true, deploymentStandard: false }
}

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

function applyPage(target, source) { Object.assign(target, source) }

async function loadAdmin() {
  const query = `keyword=${encodeURIComponent(adminFilters.keyword)}&platform=${encodeURIComponent(adminFilters.platform)}&page=${adminFilters.page}&size=${adminFilters.size}${adminPublishedParam.value}`
  applyPage(adminPage, await request(`/api/admin/releases?${query}`))
}

// loadSoftwareCategories/loadSoftwareMetadata/loadAllParameterStandards/loadUsers/loadRoles/changePassword
// 已迁移到 composables/useAdmin.js

async function loadSoftwareTypes() {
  softwareTypes.value = await request('/api/admin/software-types')
  if (typeFilters.page >= typePage.value.totalPages) {
    typeFilters.page = Math.max(typePage.value.totalPages - 1, 0)
  }
}

async function loadStandardModule() {
  await loadStandardDocuments()
}

async function loadStandardDocuments() {
  const apiBase = standardApiBase()
  const data = await request(apiBase)
  const list = Array.isArray(data) ? data : (data?.content ?? [])
  standardDocuments.value = list.map(normalizeDoc)
  if (selectedStandard.value) {
    selectedStandard.value = standardDocuments.value.find(doc => doc.id === selectedStandard.value.id) || null
  }
  await loadStandardParameters(selectedStandard.value?.id)
  if (standardFilters.page >= standardPage.value.totalPages) {
    standardFilters.page = Math.max(standardPage.value.totalPages - 1, 0)
  }
  if (maintenanceDocumentFilters.page >= maintenanceDocumentPage.value.totalPages) {
    maintenanceDocumentFilters.page = Math.max(maintenanceDocumentPage.value.totalPages - 1, 0)
  }
}

// loadAllParameterStandards 已迁移到 composables/useAdmin.js

async function loadStandardParameters(targetId = selectedStandard.value?.id) {
  if (!targetId) {
    standardParameters.value = []
    return
  }
  standardParameters.value = await fetchStandardParameters(targetId)
  if (parameterFilters.page >= parameterPage.value.totalPages) {
    parameterFilters.page = Math.max(parameterPage.value.totalPages - 1, 0)
  }
}

function fetchStandardParameters(targetId) {
  const paramName = adminSection.value === 'standardPublish' ? 'parameterStandardId' : 'standardDocumentId'
  return request(`/api/admin/standard-parameters?${paramName}=${encodeURIComponent(targetId)}`)
}

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

function handleDownload(url, fileName) {
  if (!auth.token) {
    notify('请先登录后再下载文件')
    window.location.hash = '#/admin'
    return
  }
  if (downloading.value) {
    notify('有文件正在下载中，请等待完成', 'error')
    return
  }
  downloading.value = true
  downloadProgress.value = 0
  downloadFileName.value = fileName || '文件'

  fetch(fileUrl(url), {
    headers: { 'Authorization': 'Bearer ' + auth.token }
  }).then(async resp => {
    if (!resp.ok) throw new Error('下载失败')
    const contentLength = +(resp.headers.get('Content-Length') || 0)
    const reader = resp.body.getReader()
    const chunks = []
    let received = 0
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      chunks.push(value)
      received += value.length
      if (contentLength > 0) downloadProgress.value = Math.round(received / contentLength * 100)
    }
    const blob = new Blob(chunks)
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = downloadFileName.value
    a.click()
    URL.revokeObjectURL(a.href)
  }).catch(err => {
    notify(err.message || '下载失败', 'error')
  }).finally(() => {
    downloading.value = false
    downloadProgress.value = 0
    downloadFileName.value = ''
  })
}

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

// changeAdminPage 已迁移到 composables/useAdmin.js

function changeTypePage(page) {
  typeFilters.page = Math.max(page, 0)
}

function applyTypeFilters() {
  typeFilters.page = 0
}

function applyStandardFilters() {
  standardFilters.page = 0
}

function handleStandardFilterCategoryChange() {
  standardFilters.software = ''
  applyStandardFilters()
}

function changeStandardPage(page) {
  standardFilters.page = Math.max(page, 0)
}

function openStandardDetail(document) {
  selectedStandard.value = document
  parameterFilters.page = 0
  loadStandardParameters(document.id)
}

function backToStandardList() {
  selectedStandard.value = null
  standardParameters.value = []
}

function changeParameterPage(page) {
  parameterFilters.page = Math.max(page, 0)
}

function changeMaintenanceDocumentPage(page) {
  maintenanceDocumentFilters.page = Math.max(page, 0)
}

function applyMaintenanceDocumentFilters() {
  maintenanceDocumentFilters.page = 0
}

function switchAdminSection(section) {
  adminSection.value = section
  closeImportPage()
  editing.value = false
  selectedStandard.value = null
  if (section === 'types') {
    loadSoftwareTypes()
    loadSoftwareCategories()
  } else if (section === 'standardPublish' || section === 'documentMaintenance') {
    loadSoftwareTypes()
    loadSoftwareCategories()
    loadStandardModule()
    loadAllParameterStandards()
  } else if (section === 'users') {
    loadUsers()
  } else if (section === 'reviews') {
    loadReviews()
  } else if (section === 'settings') {
    loadSystemSettings()
  } else {
    loadAdmin()
    loadSoftwareTypes()
    loadSoftwareCategories()
    loadAllParameterStandards()
  }
}

function openImportPage() {
  editing.value = false
  importResult.value = null
  Object.assign(importForm, defaultImportForm())
  showImport.value = true
}

function closeImportPage() {
  if (importing.value) return
  showImport.value = false
}

function startCreate() {
  Object.assign(releaseForm, defaultReleaseForm())
  editing.value = true
}

function startEdit(release) {
  if (release.published) {
    notify('已发布资源不能编辑，请先下架后再编辑', 'error')
    return
  }
  const selectedType = findSoftwareType(release.softwareTypeId)
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
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  Object.assign(releaseForm, defaultReleaseForm())
}

function handleReleaseFileChange(event) {
  releaseForm.file = event.target.files?.[0] || null
}

async function saveRelease() {
  const formData = new FormData()
  const selectedType = findSoftwareType(releaseForm.softwareTypeId)
  if (!selectedType) {
    notify('请选择软件类型')
    return
  }
  if (!releaseForm.id && !releaseForm.file) {
    notify('请上传安装包', 'error')
    return
  }
  releaseForm.middlewareName = selectedType.name
  for (const key of ['middlewareName', 'version', 'platform', 'description', 'releasedAt', 'published', 'standardDocumentId']) {
    formData.append(key, releaseForm[key] ?? '')
  }
  formData.append('softwareTypeId', releaseForm.softwareTypeId)
  formData.append('standardPackage', releaseForm.standardPackage)
  if (releaseForm.standardPackage && releaseForm.parameterStandardId) {
    formData.append('parameterStandardId', releaseForm.parameterStandardId)
  }
  if (releaseForm.file) {
    formData.append('file', releaseForm.file)
  }

  const url = releaseForm.id ? `/api/admin/releases/${releaseForm.id}` : '/api/admin/releases'
  const method = releaseForm.id ? 'PUT' : 'POST'

  uploading.value = true
  uploadProgress.value = 0

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
          } catch {}
          reject(new Error(message))
        }
      }
      xhr.onerror = () => reject(new Error('网络错误'))
      xhr.open(method, url)
      xhr.setRequestHeader('Authorization', 'Bearer ' + auth.token)
      xhr.send(formData)
    })
    notify('资源已保存', 'success')
    cancelEdit()
    await loadAdmin()
  } catch (error) {
    notify(error.message || '保存失败', 'error')
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

async function togglePublish(release) {
  const actionText = release.published ? '下架' : '发布'
  try {
    await request(`/api/admin/releases/${release.id}/${release.published ? 'unpublish' : 'publish'}`, { method: 'POST' })
    notify(`资源已${actionText}`, 'success')
    await loadAdmin()
  } catch (error) {
    notify(error.message || `资源${actionText}失败`, 'error')
  }
}

async function regeneratePackage(release) {
  try {
    await request(`/api/admin/releases/${release.id}/regenerate-package`, { method: 'POST' })
    notify('标准包已提交重新生成', 'success')
    await loadAdmin()
  } catch (error) {
    notify(error.message || '重新生成失败', 'error')
  }
}

function openDeleteReleaseDialog(release) {
  if (release.published) {
    notify('已发布资源不能删除，请先下架后再删除', 'error')
    return
  }
  deleteTarget.value = release
}

function closeDeleteReleaseDialog() {
  if (deletingRelease.value) return
  deleteTarget.value = null
}

async function confirmDeleteRelease() {
  const release = deleteTarget.value
  if (!release) return
  if (release.published) {
    notify('已发布资源不能删除，请先下架后再删除', 'error')
    deleteTarget.value = null
    return
  }

  const shouldMoveToPreviousPage = adminPage.content.length <= 1 && adminFilters.page > 0
  deletingRelease.value = true
  try {
    await request(`/api/admin/releases/${release.id}`, { method: 'DELETE' })
    if (shouldMoveToPreviousPage) {
      adminFilters.page -= 1
    }
    deleteTarget.value = null
    notify('资源已删除', 'success')
  } catch (error) {
    notify(error.message || '删除失败', 'error')
    return
  } finally {
    deletingRelease.value = false
  }

  try {
    await loadAdmin()
  } catch (error) {
    notify(error.message || '删除成功，但列表刷新失败', 'error')
  }
}

async function submitImport() {
  if (importing.value) return
  const selectedType = findSoftwareType(importForm.softwareTypeId)
  if (!selectedType) {
    notify('请选择软件类型')
    return
  }
  importing.value = true
  try {
    const { category, ...payload } = importForm
    const body = {
      ...payload,
      middlewareName: selectedType.name
    }
    const result = await request('/api/admin/releases/import', { method: 'POST', body })
    importResult.value = result
    showImportResultDialog.value = true
    Object.assign(importForm, defaultImportForm())
    showImport.value = false
    notify('批量导入完成', 'success')
    await loadAdmin()
  } catch (error) {
    notify(error.message || '批量导入失败', 'error')
  } finally {
    importing.value = false
  }
}

function findSoftwareType(id) {
  return softwareTypes.value.find(type => String(type.id) === String(id))
}

function findSoftwareTypeIdByCategoryAndName(category, name) {
  const targetCategory = (category || '').trim().toLowerCase()
  const targetName = (name || '').trim().toLowerCase()
  if (!targetCategory || !targetName) return ''
  const matched = softwareTypes.value.find(type =>
    (type.category || '').trim().toLowerCase() === targetCategory &&
    (type.name || '').trim().toLowerCase() === targetName
  )
  return matched ? matched.id : ''
}

function getStandardLabel(id) {
  const standard = allParameterStandards.value.find(doc => String(doc.id) === String(id)) ||
    standardDocuments.value.find(doc => String(doc.id) === String(id))
  if (!standard) return '-'
  return [standard.category, standard.software, standard.softwareVersion].filter(Boolean).join(' / ') +
    (standard.version ? ` · V${standard.version}` : '')
}

function displayTitle(doc) {
  if (!doc) return ''
  if (doc.documentType === 'MANUAL' || doc.documentType === 'ARTICLE') return doc.title
  return [doc.category, doc.software, doc.version].filter(Boolean).join(' / ') || doc.title
}

function openCreateCategoryDialog() {
  categoryForm.name = ''
  showCategoryDialog.value = true
}

function closeCategoryDialog() {
  showCategoryDialog.value = false
  categoryForm.name = ''
}

async function saveCategory() {
  try {
    softwareCategories.value = await request('/api/admin/software-type-categories', {
      method: 'POST',
      body: categoryForm
    })
    notify('分类已新增', 'success')
    closeCategoryDialog()
  } catch (error) {
    notify(error.message || '分类新增失败', 'error')
  }
}

function startCreateType() {
  Object.assign(typeForm, defaultTypeForm())
}

function startEditType(type) {
  Object.assign(typeForm, {
    id: type.id,
    category: type.category,
    name: type.name,
    description: type.description || '',
    active: type.active
  })
}

function openCreateTypeDialog() {
  startCreateType()
  showTypeDialog.value = true
}

function openEditTypeDialog(type) {
  startEditType(type)
  showTypeDialog.value = true
}

function closeTypeDialog() {
  showTypeDialog.value = false
  startCreateType()
}

async function saveType() {
  const actionText = typeForm.id ? '修改' : '新增'
  try {
    await request(typeForm.id ? `/api/admin/software-types/${typeForm.id}` : '/api/admin/software-types', {
      method: typeForm.id ? 'PUT' : 'POST',
      body: typeForm
    })
    notify(`类型已${actionText}`, 'success')
    closeTypeDialog()
    await loadSoftwareMetadata()
  } catch (error) {
    notify(error.message || `类型${actionText}失败`, 'error')
  }
}

async function deleteType(type) {
  confirmAction(`确认删除类型 ${type.category} / ${type.name}？`, () => doDeleteType(type))
}
async function doDeleteType(type) {
  try {
    await request(`/api/admin/software-types/${type.id}`, { method: 'DELETE' })
    notify('类型已删除', 'success')
    await loadSoftwareMetadata()
  } catch (error) {
    notify(error.message || '类型删除失败', 'error')
  }
}

function openCreateStandardDialog() {
  Object.assign(standardForm, defaultStandardForm())
  showStandardDialog.value = true
}

function openEditStandardDialog(document) {
  const selectedType = findSoftwareType(document.softwareTypeId || findSoftwareTypeIdByCategoryAndName(document.category, document.software))
  Object.assign(standardForm, {
    id: document.id,
    category: selectedType?.category || document.category || '',
    softwareTypeId: selectedType?.id || '',
    softwareVersion: document.softwareVersion || '',
    code: document.code || '',
    summary: document.summary || '',
    content: document.content || '# 参数标准\n\n'
  })
  showStandardDialog.value = true
}

function closeStandardDialog() {
  showStandardDialog.value = false
  Object.assign(standardForm, defaultStandardForm())
}

function buildStandardTitle(selectedType) {
  return [selectedType?.category, selectedType?.name, standardForm.softwareVersion]
    .filter(Boolean)
    .join(' / ')
}

async function saveStandard() {
  const selectedType = findSoftwareType(standardForm.softwareTypeId)
  if (!selectedType) {
    notify('请选择软件类型')
    return
  }
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
  if (adminSection.value !== 'standardPublish') {
    body.summary = standardForm.summary
  }
  const actionText = standardForm.id ? '修改' : '新增'
  try {
    await request(standardForm.id ? `${apiBase}/${standardForm.id}` : apiBase, {
      method: standardForm.id ? 'PUT' : 'POST',
      body
    })
    notify(`标准已${actionText}`, 'success')
    closeStandardDialog()
    await loadStandardDocuments()
  } catch (error) {
    notify(error.message || `标准${actionText}失败`, 'error')
  }
}

function statusLabel(status) {
  const map = { DRAFT: '草稿', PENDING_REVIEW: '审核中', PUBLISHED: '已发布', MODIFYING: '修改中' }
  return map[status] || status
}

function statusClass(status) {
  const map = { DRAFT: 'draft', PENDING_REVIEW: 'pending-review', PUBLISHED: 'published', MODIFYING: 'modifying' }
  return map[status] || 'off'
}

function packageStatusLabel(status) {
  const map = { PENDING: '待生成', PROCESSING: '生成中', SUCCESS: '已生成', FAILED: '生成失败' }
  return map[status] || ''
}

function packageStatusClass(status) {
  const map = { PENDING: 'off', PROCESSING: 'pending-review', SUCCESS: 'ok', FAILED: 'error' }
  return map[status] || 'off'
}

const actionMap = {
  DRAFT: ['submit-review', 'edit', 'delete'],
  PUBLISHED: ['start-modify'],
  MODIFYING: ['submit-review', 'edit', 'cancel-modify', 'delete']
}

function normalizeDoc(doc) {
  const status = doc.status || 'DRAFT'
  doc.status = status
  const underReview = doc.pendingReviewRecordId != null
  if (!doc.statusLabel || underReview) doc.statusLabel = underReview ? '审核中' : statusLabel(status)
  if (!doc._statusClass || underReview) doc._statusClass = underReview ? 'pending-review' : statusClass(status)
  if (doc.canEdit == null) {
    doc.canEdit = !underReview && (status === 'DRAFT' || status === 'MODIFYING')
  }
  if (!Array.isArray(doc.availableActions) || doc.availableActions.length === 0) {
    doc.availableActions = underReview ? [] : (actionMap[status] || [])
  }
  if (doc.hasDiff == null) doc.hasDiff = false
  return doc
}

function standardApiBase() {
  return adminSection.value === 'standardPublish' ? '/api/admin/parameter-standards' : '/api/admin/standard-documents'
}

async function submitForReview(doc) {
  try {
    await request(`${standardApiBase()}/${doc.id}/submit-review`, { method: 'POST' })
    notify('已提交审核', 'success')
    await loadStandardDocuments()
  } catch (error) {
    notify(error.message || '提交审核失败', 'error')
  }
}

async function startModify(doc) {
  try {
    await request(`${standardApiBase()}/${doc.id}/start-modify`, { method: 'POST' })
    notify('已进入修改状态', 'success')
    await loadStandardDocuments()
  } catch (error) {
    notify(error.message || '操作失败', 'error')
  }
}

async function cancelModify(doc) {
  confirmAction(`确认取消修改「${displayTitle(doc)}」？内容将恢复到上次发布版本。`, () => doCancelModify(doc))
}
async function doCancelModify(doc) {
  try {
    await request(`${standardApiBase()}/${doc.id}/cancel-modify`, { method: 'POST' })
    notify('已取消修改，内容已恢复', 'success')
    await loadStandardDocuments()
  } catch (error) {
    notify(error.message || '取消修改失败', 'error')
  }
}

async function confirmDeleteDoc(doc) {
  confirmAction(`确认删除「${displayTitle(doc)}」？此操作不可恢复。`, () => doDeleteDoc(doc))
}
async function doDeleteDoc(doc) {
  try {
    await request(`${standardApiBase()}/${doc.id}`, { method: 'DELETE' })
    notify('已删除', 'success')
    await loadStandardDocuments()
  } catch (error) {
    notify(error.message || '删除失败', 'error')
  }
}

// 审核/差异对话框已迁移到 ReviewsSection

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

function changeReviewPage(page) {
  reviewPage.page = Math.max(page, 0)
}

function reviewStatusClass(status) {
  const map = { PENDING: 'pending-review', APPROVED: 'published', REJECTED: 'draft' }
  return map[status] || 'off'
}

// renderMarkdown 已迁移到 utils/index.js

function formatTime(time) {
  if (!time) return '-'
  if (Array.isArray(time)) {
    const [y, m, d, h, min] = time
    return `${y}-${String(m).padStart(2,'0')}-${String(d).padStart(2,'0')} ${String(h).padStart(2,'0')}:${String(min).padStart(2,'0')}`
  }
  return String(time).replace('T', ' ').substring(0, 16)
}

async function loadReviews() {
  try {
    allReviews.value = await request('/api/admin/reviews')
  } catch (error) {
    allReviews.value = []
  }
}

function applyReviewFilters() {
  reviewPage.page = 0
}

async function loadSystemSettings() {
  try {
    const data = await request('/api/admin/settings')
    Object.assign(systemSettings, data)
  } catch {}
}

async function saveSystemSettings() {
  try {
    await request('/api/admin/settings', { method: 'PUT', body: systemSettings })
    notify('系统设置已保存', 'success')
    loadSiteConfig()
  } catch (e) {
    notify(e.message || '保存失败', 'error')
  }
}

async function openReviewDetail(record) {
  try {
    const detail = await request(`/api/admin/reviews/${record.id}`)
    selectedReview.value = detail
    selectedReviewDiff.value = detail.diff || '无差异信息'
    reviewComment.value = ''
  } catch (error) {
    notify(error.message || '加载审核详情失败', 'error')
  }
}

function closeReviewDetail() {
  selectedReview.value = null
  selectedReviewDiff.value = ''
}

async function openRevisionHistory(doc, documentType) {
  revisionDocTitle.value = doc.title || doc.software || '文档'
  try {
    const list = await request(`/api/admin/revisions?documentId=${doc.id}&documentType=${encodeURIComponent(documentType)}`)
    revisionList.value = Array.isArray(list) ? list : []
    showRevisionModal.value = true
  } catch (error) {
    notify(error.message || '加载修订历史失败', 'error')
  }
}

function closeRevisionModal() {
  showRevisionModal.value = false
  revisionList.value = []
}

async function reviewApprove(record) {
  try {
    await request(`/api/admin/reviews/${record.id}/approve`, {
      method: 'POST',
      body: { comment: reviewComment.value || null }
    })
    notify('审核已通过', 'success')
    closeReviewDetail()
    await loadReviews()
    await loadStandardDocuments()
  } catch (error) {
    notify(error.message || '审核通过失败', 'error')
  }
}

async function reviewReject(record) {
  try {
    await request(`/api/admin/reviews/${record.id}/reject`, {
      method: 'POST',
      body: { comment: reviewComment.value || null }
    })
    notify('已驳回', 'success')
    closeReviewDetail()
    await loadReviews()
    await loadStandardDocuments()
  } catch (error) {
    notify(error.message || '驳回失败', 'error')
  }
}

function previewDocument(document) {
  selectedPreviewDocument.value = document
  previewTocActiveId.value = ''
  if (document.relatedStandardDocumentId) {
    loadStandardParameters(document.relatedStandardDocumentId)
  } else {
    standardParameters.value = []
  }
  nextTick(() => initPreviewScrollSpy())
}

function closePreviewDocument() {
  selectedPreviewDocument.value = null
  previewTocActiveId.value = ''
  if (previewScrollHandler) { window.removeEventListener('scroll', previewScrollHandler); previewScrollHandler = null }
}

let previewScrollHandler = null
function initPreviewScrollSpy() {
  if (previewScrollHandler) window.removeEventListener('scroll', previewScrollHandler)
  previewScrollHandler = () => {
    const items = previewTocItems.value
    if (!items.length) return
    let current = ''
    for (const item of items) {
      const el = document.getElementById(item.id)
      if (el && el.getBoundingClientRect().top <= 120) current = item.id
    }
    previewTocActiveId.value = current
  }
  window.addEventListener('scroll', previewScrollHandler, { passive: true })
}

function scrollToPreviewHeading(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}

function openCreateParameterDialog() {
  if (!selectedStandard.value) {
    notify('请先选择标准')
    return
  }
  const bindingField = adminSection.value === 'standardPublish' ? 'parameterStandardId' : 'standardDocumentId'
  Object.assign(parameterForm, defaultParameterForm(), { [bindingField]: selectedStandard.value.id })
  showParameterDialog.value = true
}

function openEditParameterDialog(parameter) {
  const bindingField = adminSection.value === 'standardPublish' ? 'parameterStandardId' : 'standardDocumentId'
  Object.assign(parameterForm, {
    id: parameter.id,
    [bindingField]: parameter[bindingField] || selectedStandard.value?.id || null,
    code: parameter.code,
    name: parameter.name,
    value: parameter.value,
    category: parameter.category || '',
    description: parameter.description || '',
    active: parameter.active,
    deploymentStandard: parameter.deploymentStandard || false
  })
  showParameterDialog.value = true
}

function closeParameterDialog() {
  showParameterDialog.value = false
  Object.assign(parameterForm, defaultParameterForm())
}

async function saveParameter() {
  const bindingField = adminSection.value === 'standardPublish' ? 'parameterStandardId' : 'standardDocumentId'
  const targetStandardId = selectedStandard.value?.id || parameterForm[bindingField]
  if (!targetStandardId) {
    notify('参数必须绑定到标准')
    return
  }
  const body = {
    ...parameterForm,
    [bindingField]: targetStandardId
  }
  const actionText = parameterForm.id ? '修改' : '新增'
  try {
    await request(parameterForm.id ? `/api/admin/standard-parameters/${parameterForm.id}` : '/api/admin/standard-parameters', {
      method: parameterForm.id ? 'PUT' : 'POST',
      body
    })
    notify(`标准参数已${actionText}`, 'success')
    closeParameterDialog()
    await loadStandardParameters(targetStandardId)
  } catch (error) {
    notify(error.message || `标准参数${actionText}失败`, 'error')
  }
}

async function copyParameter(parameter) {
  const text = `{{${parameter.code}}}`
  await navigator.clipboard.writeText(text)
  notify(`已复制 ${text}`, 'success')
}

// showParamImportDialog, paramImportFile, paramImporting, paramImportResult 已迁移到 composable
function handleParamImportFileChange(e) {
  paramImportFile.value = e.target.files[0] || null
}

function downloadParameterTemplate() {
  fetch('/api/admin/standard-parameters/template', {
    headers: { 'Authorization': `Bearer ${auth.token}` }
  }).then(res => res.blob()).then(blob => {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'parameter-template.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  }).catch(() => notify('模板下载失败', 'error'))
}

async function importParameters() {
  if (!paramImportFile.value) {
    notify('请选择 Excel 文件', 'error')
    return
  }
  const psId = selectedStandard.value?.id
  if (!psId) {
    notify('请先选择参数标准', 'error')
    return
  }
  paramImporting.value = true
  paramImportResult.value = null
  try {
    const formData = new FormData()
    formData.append('file', paramImportFile.value)
    formData.append('parameterStandardId', psId)
    const res = await new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(JSON.parse(xhr.responseText))
        } else {
          reject(new Error('导入失败'))
        }
      }
      xhr.onerror = () => reject(new Error('网络错误'))
      xhr.open('POST', '/api/admin/standard-parameters/import')
      xhr.setRequestHeader('Authorization', 'Bearer ' + auth.token)
      xhr.send(formData)
    })
    paramImportResult.value = res
    await loadStandardParameters(psId)
    if (res.skipped === 0) {
      notify(`成功导入 ${res.imported} 条参数`, 'success')
    }
  } catch (error) {
    notify(error.message || '导入失败', 'error')
  } finally {
    paramImporting.value = false
  }
}

// changePassword/loadUsers/loadRoles 已迁移到 composables/useAdmin.js

function userCountByRole(role) {
  return userList.value.filter(u => u.role === role).length
}

function openCreateUserDialog() {
  Object.assign(userForm, { username: '', displayName: '', password: '', role: '开发经理' })
  if (!allRoles.value.length) loadRoles()
  showUserDialog.value = true
}

function closeUserDialog() {
  showUserDialog.value = false
}

async function createUser() {
  try {
    const pwHash = await sha256(userForm.password)
    await request('/api/admin/users', { method: 'POST', body: { ...userForm, password: pwHash } })
    notify('用户已创建', 'success')
    closeUserDialog()
    await loadUsers()
  } catch (error) {
    notify(error.message || '创建失败', 'error')
  }
}

function openChangeRoleDialog(user) {
  userFormTarget.value = user
  userForm.role = user.role
  if (!allRoles.value.length) loadRoles()
  showRoleDialog.value = true
}

function closeRoleDialog() {
  showRoleDialog.value = false
  userFormTarget.value = null
}

async function changeUserRole() {
  if (!userFormTarget.value) return
  try {
    await request(`/api/admin/users/${userFormTarget.value.id}/role`, { method: 'PUT', body: { role: userForm.role } })
    notify('角色已更新', 'success')
    closeRoleDialog()
    await loadUsers()
  } catch (error) {
    notify(error.message || '更新失败', 'error')
  }
}

async function resetUserPassword(user) {
  const newPwd = prompt('请输入新密码（至少6位）：')
  if (!newPwd) return
  const pwHash = await sha256(newPwd)
  try {
    await request(`/api/admin/users/${user.id}/reset-password`, { method: 'POST', body: { newPassword: pwHash } })
    notify('密码已重置', 'success')
  } catch (error) {
    notify(error.message || '重置失败', 'error')
  }
}

async function deleteUserAccount(user) {
  confirmAction(`确认删除用户 ${user.username}？此操作不可撤销。`, () => doDeleteUser(user))
}
async function doDeleteUser(user) {
  try {
    await request(`/api/admin/users/${user.id}`, { method: 'DELETE' })
    notify('用户已删除', 'success')
    await loadUsers()
  } catch (error) {
    notify(error.message || '删除失败', 'error')
  }
}

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
