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

    <DocumentPreview
      :document="selectedPreviewDocument"
      :documents="maintenanceDocuments"
      :parameters="standardParameters"
      @close="closePreviewDocument()"
      @preview="previewDocument"
    />

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
import { formatDetail, formatDate, renderMarkdown, documentTypeLabel } from './utils'
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
import DocumentPreview from './components/DocumentPreview.vue'
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
