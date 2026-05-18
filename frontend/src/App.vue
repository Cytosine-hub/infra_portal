<template>
  <div class="app-shell">
    <header :class="['topbar', route.name === 'home' ? 'portal-topbar' : '']">
      <div :class="{ 'clickable-title': route.name?.startsWith('forum') }" @click="route.name?.startsWith('forum') && goForum()">
        <p class="eyebrow">Infrastructure Portal</p>
        <h1>{{ pageTitle }}</h1>
      </div>
      <div class="topbar-right">
        <nav v-if="route.name !== 'home'" class="nav-tabs" aria-label="Primary">
          <button :class="{ active: false }" @click="goHome()">门户首页</button>
          <button v-if="auth.token" :class="{ active: route.name === 'standards' }" @click="goStandards()">标准发布</button>
          <button v-if="auth.token" :class="{ active: route.name === 'public' }" @click="goPublic()">下载中心</button>
          <button v-if="auth.token" :class="{ active: route.name?.startsWith('forum') }" @click="goForum()">论坛</button>
          <button v-if="canAccessAdmin" :class="{ active: route.name === 'admin' || route.name === 'documentEditor' }" @click="goAdmin()">管理后台</button>
        </nav>
        <div class="topbar-user">
          <template v-if="auth.token">
            <span class="topbar-username">{{ auth.user?.displayName || auth.user?.username }}</span>
            <span class="topbar-role-tag">{{ currentUserRole }}</span>
            <button class="topbar-logout" @click="logout()">退出</button>
          </template>
          <template v-else>
            <span class="topbar-username guest">未登录</span>
            <button class="topbar-logout" @click="goLogin()">登录</button>
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
        @saved="onDocumentEditorSaved"
        @cancel="onDocumentEditorCancel"
      />
      <template v-else>
      <section v-if="route.name === 'home'" class="portal-page">
        <div class="portal-hero">
          <div class="portal-copy">
            <p class="eyebrow">统一入口</p>
            <h2>资源下载、标准发布、漏洞通告与技术交流</h2>
            <p>面向基础设施运维场景，集中呈现软件资产、规范文件、安全信息和论坛入口。</p>
          </div>
          <div class="portal-stats">
            <div>
              <strong>{{ publicPage.totalElements }}</strong>
              <span>已发布资源</span>
            </div>
            <div>
              <strong>5</strong>
              <span>门户模块</span>
            </div>
          </div>
        </div>

        <div class="portal-grid">
          <article class="portal-card primary" @click="goPublic()">
            <div class="portal-icon">软</div>
            <div>
              <h3>软件下载</h3>
              <p>查看已发布的中间件安装包、版本说明、平台信息和下载链接。</p>
            </div>
            <button>进入下载中心</button>
          </article>

          <article class="portal-card" @click="goStandards()">
            <div class="portal-icon">标</div>
            <div>
              <h3>标准发布</h3>
              <p>集中发布基础设施规范、部署标准、运维手册和检查基线。</p>
            </div>
            <button>查看标准</button>
          </article>

          <article class="portal-card warning" @click="openPortalModule('漏洞发布')">
            <div class="portal-icon">漏</div>
            <div>
              <h3>漏洞发布</h3>
              <p>跟踪漏洞公告、影响范围、修复建议和版本升级要求。</p>
            </div>
            <button>查看漏洞</button>
          </article>

          <article class="portal-card forum" @click="goForum()">
            <div class="portal-icon">论</div>
            <div>
              <h3>infra论坛</h3>
              <p>沉淀基础设施实践经验，支持问题讨论、方案交流和知识共享。</p>
            </div>
            <button>进入论坛</button>
          </article>
        </div>

        <div class="section-heading portal-tools-heading">
          <div>
            <p class="eyebrow">Tools</p>
            <h3>常用工具</h3>
          </div>
        </div>
        <div class="portal-grid portal-tools-grid">
          <article class="portal-card portal-tool">
            <div class="portal-icon tool-icon">库</div>
            <div><h3>知识库</h3><p>运维文档、故障案例、技术方案和经验总结。</p></div>
            <button class="ghost">查看</button>
          </article>
          <article class="portal-card portal-tool">
            <div class="portal-icon tool-icon">监</div>
            <div><h3>监控面板</h3><p>服务器性能、中间件状态、告警信息统一查看。</p></div>
            <button class="ghost">查看</button>
          </article>
          <article class="portal-card portal-tool">
            <div class="portal-icon tool-icon">端</div>
            <div><h3>远程终端</h3><p>Web SSH 终端，快速连接服务器和管理中间件。</p></div>
            <button class="ghost">打开</button>
          </article>
          <article class="portal-card portal-tool">
            <div class="portal-icon tool-icon">备</div>
            <div><h3>备份管理</h3><p>数据库与配置文件定时备份、恢复和版本管理。</p></div>
            <button class="ghost">查看</button>
          </article>
        </div>

        <section class="portal-latest">
          <div class="section-heading">
            <div>
              <p class="eyebrow">Latest</p>
              <h3>最新软件发布</h3>
            </div>
            <button class="ghost" @click="goPublic()">更多</button>
          </div>
          <div class="latest-list">
            <article v-for="release in publicPage.content.slice(0, 4)" :key="release.downloadToken">
              <div>
                <h4>{{ release.middlewareName }}</h4>
                <p>{{ release.version }} · {{ release.platform || '通用平台' }}</p>
              </div>
              <button class="ghost" @click.stop="openDetail(release.downloadToken)">详情</button>
            </article>
            <p v-if="publicPage.content.length === 0" class="empty-state">暂无已发布软件资源。</p>
          </div>
        </section>
      </section>

      <section v-else-if="route.name === 'public'" class="workspace">
        <div class="toolbar">
          <div class="filters">
            <input v-model.trim="publicFilters.keyword" placeholder="搜索名称、版本、说明" @keyup.enter="loadPublic()" />
            <input v-model.trim="publicFilters.platform" placeholder="平台" @keyup.enter="loadPublic()" />
            <button @click="loadPublic()">查询</button>
          </div>
        </div>

        <div v-if="selectedRelease" class="detail-layout">
          <button class="ghost" @click="closeDetail()">返回列表</button>
          <article class="detail-panel">
            <div>
              <p class="eyebrow">版本详情</p>
              <h2>{{ selectedRelease.middlewareName }}</h2>
              <p class="muted">{{ selectedRelease.version }} · {{ selectedRelease.platform || '通用平台' }}</p>
            </div>
            <p class="description">{{ selectedRelease.description || '暂无版本说明。' }}</p>
            <dl class="meta-grid">
              <div><dt>发布日期</dt><dd>{{ selectedRelease.releasedAt || '-' }}</dd></div>
              <div><dt>文件名</dt><dd>{{ selectedRelease.originalFileName }}</dd></div>
              <div><dt>文件大小</dt><dd>{{ formatBytes(selectedRelease.fileSize) }}</dd></div>
              <div><dt>下载次数</dt><dd>{{ selectedRelease.downloadCount }}</dd></div>
            </dl>
            <a class="primary-link" href="#" @click.prevent="handleDownload(selectedRelease.downloadUrl)">下载文件</a>
          </article>
        </div>

        <template v-else>
          <div class="release-grid">
            <article v-for="release in publicPage.content" :key="release.downloadToken" class="release-card">
              <div>
                <h2>{{ release.middlewareName }}</h2>
                <p>{{ release.version }} · {{ release.platform || '通用平台' }}</p>
              </div>
              <p class="description">{{ release.description || '暂无版本说明。' }}</p>
              <div class="card-footer">
                <span>{{ formatBytes(release.fileSize) }}</span>
                <div class="card-actions">
                  <button class="ghost" @click="openDetail(release.downloadToken)">详情</button>
                  <a class="download-button" href="#" @click.prevent="handleDownload(release.downloadUrl)">下载</a>
                </div>
              </div>
            </article>
          </div>
          <Pagination :page="publicPage" @change="changePublicPage" />
        </template>
      </section>

      <section v-else-if="route.name === 'standards'" class="workspace standards-page">
        <div v-if="selectedPublicStandard" class="standards-detail-layout">
          <aside class="standards-tree">
            <div class="tree-header">
              <h3>标准文档</h3>
              <button class="ghost" @click="closePublicStandardDetail()">返回列表</button>
            </div>
            <div class="category-tabs">
              <button
                v-for="cat in publicDocCategories"
                :key="cat"
                :class="['category-tab', { active: publicDocCategory === cat }]"
                @click="publicDocCategory = cat"
              >{{ cat }}</button>
            </div>
            <div class="tree-docs">
              <button
                v-for="doc in filteredPublicDocuments"
                :key="doc.id"
                :class="['tree-doc-link', { active: String(selectedPublicStandard?.id) === String(doc.id) }]"
                @click="openPublicStandardDetail(doc.id)"
              >
                {{ doc.title }}
              </button>
            </div>
          </aside>
          <article class="standards-detail-content">
            <div class="standard-detail-head">
              <div>
                <p class="eyebrow">{{ documentTypeLabel(selectedPublicStandard.documentType) }}</p>
                <h2>{{ selectedPublicStandard.title }}</h2>
                <p class="muted">
                  {{ selectedPublicStandard.category || '-' }} / {{ selectedPublicStandard.software || '-' }}
                  · 软件版本：{{ selectedPublicStandard.softwareVersion || '-' }}
                  · 标准版本：{{ selectedPublicStandard.standardVersion || '-' }}
                </p>
              </div>
              <span class="status ok">已发布</span>
            </div>
            <p v-if="selectedPublicStandard.summary" class="description">{{ selectedPublicStandard.summary }}</p>
            <div v-if="standardsLoading" class="loading-panel"><div class="spinner"></div><p>加载中...</p></div>
            <div v-else class="markdown-preview public-document" v-html="publicStandardHtml"></div>
          </article>
          <aside class="post-toc-panel" v-if="standardTocItems.length">
            <h4 class="toc-title">文档大纲</h4>
            <button
              v-for="item in standardTocItems"
              :key="item.id"
              :class="['toc-link', { active: activeStdTocId === item.id }]"
              :style="{ '--toc-level': item.level - 1 }"
              @click="scrollToStdHeading(item.id)"
            >{{ item.text }}</button>
          </aside>
        </div>

        <template v-else>
          <div class="section-heading standards-heading">
            <div>
              <p class="eyebrow">Standards</p>
              <h3>已发布标准与手册</h3>
            </div>
            <button class="ghost" @click="loadPublicStandards()">刷新</button>
          </div>
          <div class="standards-grid">
            <section v-for="group in publicStandardGroups" :key="group.category" class="standard-category-section">
              <div class="standard-category-head">
                <div>
                  <p class="eyebrow">Category</p>
                  <h2>{{ group.category }}</h2>
                </div>
                <span>{{ group.standards.length }} 项标准</span>
              </div>
              <div class="standard-list">
                <article v-for="standard in group.standards" :key="standard.id" class="standard-row">
                  <div class="standard-row-main">
                    <button type="button" class="standard-title-link" @click="openPublicStandardDetail(standard.id)">
                      {{ standard.software || '-' }} / {{ standard.title }}
                    </button>
                    <div class="manual-list">
                      <button
                        v-for="doc in standard.relatedDocuments"
                        :key="doc.id"
                        type="button"
                        class="ghost related-document-link"
                        @click="openPublicStandardDetail(doc.id)"
                      >
                        {{ doc.title }}
                      </button>
                      <span v-if="!standard.relatedDocuments?.length" class="muted">暂无已发布关联手册</span>
                    </div>
                  </div>
                  <span>{{ standard.softwareVersion || '-' }}</span>
                  <span>{{ formatDate(standard.publishedAt || standard.updatedAt) }}</span>
                </article>
              </div>
            </section>
            <p v-if="publicStandards.length === 0" class="empty-state">暂无已发布标准。</p>
          </div>
        </template>
      </section>

      <section v-else-if="route.name === 'forum'" class="workspace">
        <ForumPostList
          :auth="auth"
          @open-post="goForumPost"
          @new-post="goForumNew"
        />
      </section>
      <section v-else-if="route.name === 'forumDetail'" class="workspace">
        <ForumPostDetail
          :auth="auth"
          :post-id="route.postId"
          :markdown="markdown"
          @back="goForum"
          @edit-post="goForumEdit"
          @login="goLogin"
        />
      </section>
      <section v-else-if="route.name === 'forumEditor'" class="workspace">
        <ForumPostEditor
          :auth="auth"
          :post-id="route.postId"
          :markdown="markdown"
          @saved="onForumPostSaved"
          @cancel="goForum"
        />
      </section>

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
          <div class="admin-layout">
            <aside class="admin-sidebar">
              <div class="sidebar-title">
                <p class="eyebrow">Admin</p>
                <h2>管理台</h2>
              </div>
              <nav class="side-nav" aria-label="Admin">
                <button :class="{ active: adminSection === 'files' }" @click="switchAdminSection('files')">文件管理</button>
                <button :class="{ active: adminSection === 'types' }" @click="switchAdminSection('types')">类型管理</button>
                <button :class="{ active: adminSection === 'standardPublish' }" @click="switchAdminSection('standardPublish')">标准发布</button>
                <button :class="{ active: adminSection === 'documentMaintenance' }" @click="switchAdminSection('documentMaintenance')">标准文档</button>
                <button v-if="isSysAdmin" :class="{ active: adminSection === 'users' }" @click="switchAdminSection('users')">用户管理</button>
              </nav>
              <div class="sidebar-actions">
                <button class="ghost" @click="showPassword = !showPassword">修改密码</button>
                <button class="danger" @click="logout()">退出</button>
              </div>
            </aside>

            <section class="admin-content">
              <div class="admin-header">
                <div>
                  <p class="eyebrow">{{ adminSectionLabel.eyebrow }}</p>
                  <h2>{{ adminSectionLabel.title }}</h2>
                </div>
                <div v-if="adminSection === 'files'" class="admin-actions">
                  <button class="ghost" @click="openImportPage()">批量导入</button>
                  <button @click="startCreate()">新增资源</button>
                </div>
                <div v-else-if="adminSection === 'types'" class="admin-actions">
                  <button class="ghost" @click="loadSoftwareMetadata()">刷新</button>
                  <button class="ghost" @click="openCreateCategoryDialog()">新增分类</button>
                  <button @click="openCreateTypeDialog()">新增类型</button>
                </div>
                <div v-else-if="adminSection === 'standardPublish'" class="admin-actions">
                  <button class="ghost" @click="loadStandardModule()">刷新</button>
                  <button @click="openCreateStandardDialog()">新增标准</button>
                </div>
                <div v-else-if="adminSection === 'documentMaintenance'" class="admin-actions">
                  <button class="ghost" @click="loadStandardDocuments()">刷新</button>
                  <button @click="goDocumentEditor()">新增文档</button>
                </div>
                <div v-else-if="adminSection === 'users'" class="admin-actions">
                  <button @click="openCreateUserDialog()">新增用户</button>
                </div>
              </div>

              <div v-if="showPassword" class="modal-backdrop" @click.self="showPassword = false">
                <form class="modal-panel" @submit.prevent="changePassword">
                  <div class="panel-title">
                    <h3>修改密码</h3>
                    <button type="button" class="ghost" @click="showPassword = false">关闭</button>
                  </div>
                  <div class="form-grid single">
                    <label>当前密码<input v-model="passwordForm.currentPassword" type="password" required /></label>
                    <label>新密码<input v-model="passwordForm.newPassword" type="password" minlength="8" required /></label>
                    <label>确认密码<input v-model="passwordForm.confirmPassword" type="password" required /></label>
                  </div>
                  <div class="form-actions">
                    <button type="submit">保存密码</button>
                    <button type="button" class="ghost" @click="showPassword = false">取消</button>
                  </div>
                </form>
              </div>

              <template v-if="adminSection === 'files'">
                <div class="toolbar">
                  <div class="filters admin-filters">
                    <input v-model.trim="adminFilters.keyword" placeholder="搜索资源" @keyup.enter="loadAdmin()" />
                    <input v-model.trim="adminFilters.platform" placeholder="平台" @keyup.enter="loadAdmin()" />
                    <select v-model="adminFilters.published" @change="loadAdmin()">
                      <option value="">全部状态</option>
                      <option value="true">已发布</option>
                      <option value="false">未发布</option>
                    </select>
                    <button @click="loadAdmin()">查询</button>
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
                          <th>文件</th>
                          <th>下载</th>
                          <th>操作</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="release in adminPage.content" :key="release.id">
                          <td :title="release.middlewareName">{{ release.middlewareName }}</td>
                          <td :title="release.version">{{ release.version }}</td>
                          <td :title="release.platform || '-'">{{ release.platform || '-' }}</td>
                          <td><span :class="['status', release.published ? 'ok' : 'off']">{{ release.published ? '已发布' : '未发布' }}</span></td>
                          <td :title="release.originalFileName">{{ release.originalFileName }}</td>
                          <td>{{ release.downloadCount }}</td>
                          <td class="row-actions">
                            <button
                              class="ghost"
                              :disabled="release.published"
                              :title="release.published ? '已发布资源不能编辑，请先下架' : '编辑'"
                              @click="startEdit(release)"
                            >编辑</button>
                            <button class="ghost" @click="togglePublish(release)">{{ release.published ? '下架' : '发布' }}</button>
                            <button
                              class="danger"
                              :disabled="release.published"
                              :title="release.published ? '已发布资源不能删除，请先下架' : '删除'"
                              @click="openDeleteReleaseDialog(release)"
                            >删除</button>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                  <Pagination :page="adminPage" @change="changeAdminPage" />
                </div>
              </template>

              <section v-else-if="adminSection === 'types'" class="utility-panel type-panel">
                <div class="filters type-filters">
                  <select v-model="typeFilters.category">
                    <option value="">全部分类</option>
                    <option v-for="category in softwareTypeCategories" :key="category" :value="category">{{ category }}</option>
                  </select>
                  <input v-model.trim="typeFilters.name" placeholder="软件名称" @keyup.enter="applyTypeFilters()" />
                  <button type="button" @click="applyTypeFilters()">查询</button>
                </div>
                <div class="list-panel type-list-panel">
                  <div class="type-list">
                    <article v-for="type in pagedSoftwareTypes" :key="type.id" class="type-item">
                      <div>
                        <strong>{{ type.category }} / {{ type.name }}</strong>
                        <p>{{ type.description || '暂无说明' }}</p>
                      </div>
                      <span :class="['status', type.active ? 'ok' : 'off']">{{ type.active ? '启用' : '停用' }}</span>
                      <button class="ghost" @click="openEditTypeDialog(type)">编辑</button>
                      <button class="danger" @click="deleteType(type)">删除</button>
                    </article>
                  </div>
                  <Pagination :page="typePage" @change="changeTypePage" />
                </div>
              </section>

              <section v-else-if="adminSection === 'standardPublish'" class="utility-panel type-panel">
                <template v-if="!selectedStandard">
                  <div class="standards-filter-bar">
                    <select v-model="standardFilters.category" @change="handleStandardFilterCategoryChange">
                      <option value="">软件分类</option>
                      <option v-for="category in softwareTypeCategories" :key="category" :value="category">{{ category }}</option>
                    </select>
                  </div>
                  <div class="standards-table-wrap">
                    <table class="standards-table">
                      <thead>
                        <tr>
                          <th>名称</th>
                          <th>软件类型</th>
                          <th>版本</th>
                          <th>标准版本</th>
                          <th>编码</th>
                          <th>操作</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="doc in filteredStandardDocuments" :key="doc.id">
                          <td>
                            <button class="link-btn" @click="openStandardDetail(doc)">{{ doc.software || '-' }}</button>
                          </td>
                          <td>{{ doc.category || '-' }}</td>
                          <td>{{ doc.softwareVersion || '-' }}</td>
                          <td>{{ doc.standardVersion || '-' }}</td>
                          <td>{{ doc.code || '-' }}</td>
                          <td class="table-actions">
                            <button class="ghost" @click="openStandardDetail(doc)">详情</button>
                            <button class="ghost" @click="openEditStandardDialog(doc)">修改</button>
                            <button class="ghost" @click="toggleDocumentPublish(doc)">{{ doc.status === 'PUBLISHED' ? '下架' : '发布' }}</button>
                          </td>
                        </tr>
                        <tr v-if="filteredStandardDocuments.length === 0">
                          <td colspan="6" class="empty-state">暂无标准记录</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                  <Pagination :page="standardPage" @change="changeStandardPage" />
                </template>

                <template v-else>
                  <div class="panel-title">
                    <div>
                      <h3>{{ selectedStandard.category || '-' }} / {{ selectedStandard.software || '-' }}</h3>
                      <p class="muted">软件版本：{{ selectedStandard.softwareVersion || '-' }} · 标准版本：{{ selectedStandard.standardVersion || '-' }}</p>
                    </div>
                    <div class="admin-actions">
                      <button type="button" class="ghost" @click="backToStandardList()">返回列表</button>
                      <button type="button" @click="openCreateParameterDialog()">新增参数</button>
                    </div>
                  </div>
                  <div class="list-panel type-list-panel">
                    <div class="type-list">
                      <article v-for="param in selectedStandardParameters" :key="param.id" class="parameter-item">
                        <div>
                          <strong>{{ param.code }}</strong>
                          <p>{{ param.name }} = {{ param.value }}</p>
                          <p>{{ param.category || '未分类' }} · {{ param.description || '暂无说明' }}</p>
                        </div>
                        <button class="ghost" @click="copyParameter(param)">复制占位符</button>
                        <button class="ghost" @click="openEditParameterDialog(param)">编辑</button>
                      </article>
                      <p v-if="selectedStandardParameters.length === 0" class="empty-state">该标准暂未配置参数。</p>
                    </div>
                  </div>
                </template>
              </section>

              <section v-else-if="adminSection === 'users'" class="utility-panel type-panel">
                <div class="list-panel type-list-panel">
                  <div class="type-list">
                    <article v-for="user in userList" :key="user.id" class="parameter-item document-item">
                      <div>
                        <strong>{{ user.displayName || user.username }}</strong>
                        <p>账号：{{ user.username }} · {{ user.role }} · {{ formatDate(user.createdAt) }}</p>
                      </div>
                      <button class="ghost" @click="openChangeRoleDialog(user)">改角色</button>
                      <button class="ghost" @click="resetUserPassword(user)">重置密码</button>
                      <button v-if="user.role !== '系统管理员' || userCountByRole('系统管理员') > 1" class="ghost danger" @click="deleteUserAccount(user)">删除</button>
                    </article>
                    <p v-if="userList.length === 0" class="empty-state">暂无用户。</p>
                  </div>
                </div>
              </section>

              <section v-else class="utility-panel type-panel">
                <div class="filters document-filters">
                  <select v-model="maintenanceDocumentFilters.documentType" @change="applyMaintenanceDocumentFilters()">
                    <option value="">全部类型</option>
                    <option value="MANUAL">手册</option>
                    <option value="ARTICLE">文章</option>
                  </select>
                  <select v-model="maintenanceDocumentFilters.status" @change="applyMaintenanceDocumentFilters()">
                    <option value="">全部状态</option>
                    <option value="DRAFT">草稿</option>
                    <option value="PUBLISHED">已发布</option>
                  </select>
                  <input v-model.trim="maintenanceDocumentFilters.keyword" placeholder="搜索标题/摘要" @keyup.enter="applyMaintenanceDocumentFilters()" />
                  <button type="button" @click="applyMaintenanceDocumentFilters()">查询</button>
                </div>
                <div class="list-panel type-list-panel">
                  <div class="type-list">
                    <article v-for="doc in pagedMaintenanceDocuments" :key="doc.id" class="parameter-item document-item">
                      <div>
                        <strong>{{ doc.title }}</strong>
                        <p>{{ doc.status === 'PUBLISHED' ? '已发布' : '草稿' }} · {{ doc.documentType === 'ARTICLE' ? '文章' : '手册' }}</p>
                        <p>关联标准：{{ getStandardLabel(doc.relatedStandardDocumentId) }}</p>
                      </div>
                      <button class="ghost" @click="previewDocument(doc)">预览</button>
                      <button class="ghost" @click="goDocumentEditorEdit(doc.id)">编辑</button>
                      <button class="ghost" @click="toggleDocumentPublish(doc)">{{ doc.status === 'PUBLISHED' ? '下架' : '发布' }}</button>
                    </article>
                    <p v-if="pagedMaintenanceDocuments.length === 0" class="empty-state">暂无文档，点击“新增文档”创建手册或文章。</p>
                  </div>
                  <Pagination :page="maintenanceDocumentPage" @change="changeMaintenanceDocumentPage" />
                </div>
              </section>
            </section>
          </div>
        </template>
      </section>
      </template>
    </main>

    <div v-if="editing" class="modal-backdrop" @click.self="cancelEdit()">
      <form class="modal-panel wide-modal form-modal" @submit.prevent="saveRelease">
        <div class="panel-title">
          <h3>{{ releaseForm.id ? '编辑资源' : '新增资源' }}</h3>
          <button type="button" class="ghost" @click="cancelEdit()">关闭</button>
        </div>
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
          <label class="file-field">安装包
            <span class="file-control">
              <input type="file" @change="handleReleaseFileChange" />
              <span class="file-button">选择文件</span>
              <span class="file-name">{{ releaseForm.file?.name || releaseForm.originalFileName || '未选择文件' }}</span>
            </span>
          </label>
          <label class="wide">说明<textarea v-model.trim="releaseForm.description" maxlength="2000" /></label>
        </div>
        <div class="form-actions">
          <button type="submit">保存</button>
          <button type="button" class="ghost" @click="cancelEdit()">取消</button>
        </div>
      </form>
    </div>

    <div v-if="showImport" class="modal-backdrop" @click.self="closeImportPage()">
      <form class="modal-panel wide-modal form-modal" @submit.prevent="submitImport">
        <div class="panel-title">
          <div>
            <h3>批量导入</h3>
            <p>扫描指定目录并按所选软件导入安装包资源。</p>
          </div>
          <button type="button" class="ghost" :disabled="importing" @click="closeImportPage()">关闭</button>
        </div>
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
        <div class="form-actions">
          <button type="submit" :disabled="importing">{{ importing ? '导入中...' : '开始导入' }}</button>
          <button type="button" class="ghost" :disabled="importing" @click="closeImportPage()">取消</button>
        </div>
        <div v-if="importing" class="loading-panel">
          <span class="spinner"></span>
          <strong>正在导入，请稍候</strong>
          <p>正在扫描目录并写入资源记录，导入完成后会显示结果。</p>
        </div>
      </form>
    </div>

    <div v-if="showTypeDialog" class="modal-backdrop" @click.self="closeTypeDialog()">
      <form class="modal-panel" @submit.prevent="saveType">
        <div class="panel-title">
          <h3>{{ typeForm.id ? '编辑类型' : '新增类型' }}</h3>
          <button type="button" class="ghost" @click="closeTypeDialog()">关闭</button>
        </div>
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
        <div class="form-actions">
          <button type="submit">保存</button>
          <button type="button" class="ghost" @click="closeTypeDialog()">取消</button>
        </div>
      </form>
    </div>

    <div v-if="showCategoryDialog" class="modal-backdrop" @click.self="closeCategoryDialog()">
      <form class="modal-panel" @submit.prevent="saveCategory">
        <div class="panel-title">
          <h3>新增分类</h3>
          <button type="button" class="ghost" @click="closeCategoryDialog()">关闭</button>
        </div>
        <div class="form-grid single">
          <label>分类名称<input v-model.trim="categoryForm.name" required maxlength="40" placeholder="例如 中间件、数据库、应用软件" /></label>
        </div>
        <div class="form-actions">
          <button type="submit">保存</button>
          <button type="button" class="ghost" @click="closeCategoryDialog()">取消</button>
        </div>
      </form>
    </div>

    <div v-if="showStandardDialog" class="modal-backdrop" @click.self="closeStandardDialog()">
      <form class="modal-panel" @submit.prevent="saveStandard">
        <div class="panel-title">
          <h3>{{ standardForm.id ? '编辑标准' : '新增标准' }}</h3>
          <button type="button" class="ghost" @click="closeStandardDialog()">关闭</button>
        </div>
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
          <label>标准版本<input v-model.trim="standardForm.standardVersion" required maxlength="80" /></label>
          <label>编码<input v-model.trim="standardForm.code" maxlength="20" /></label>
          <label>说明<textarea v-model.trim="standardForm.summary" maxlength="500" /></label>
        </div>
        <div class="form-actions">
          <button type="submit">保存</button>
          <button type="button" class="ghost" @click="closeStandardDialog()">取消</button>
        </div>
      </form>
    </div>

    <div v-if="showParameterDialog" class="modal-backdrop" @click.self="closeParameterDialog()">
      <form class="modal-panel" @submit.prevent="saveParameter">
        <div class="panel-title">
          <h3>{{ parameterForm.id ? '编辑参数' : '新增参数' }}</h3>
          <button type="button" class="ghost" @click="closeParameterDialog()">关闭</button>
        </div>
        <div class="form-grid single">
          <label>参数编码<input v-model.trim="parameterForm.code" required maxlength="80" placeholder="例如 JDK_VERSION" /></label>
          <label>参数名称<input v-model.trim="parameterForm.name" required maxlength="120" /></label>
          <label>参数值<input v-model.trim="parameterForm.value" required maxlength="500" /></label>
          <label>分类<input v-model.trim="parameterForm.category" maxlength="60" /></label>
          <label>说明<textarea v-model.trim="parameterForm.description" maxlength="500" /></label>
          <label class="checkline"><input v-model="parameterForm.active" type="checkbox" />启用</label>
        </div>
        <div class="form-actions">
          <button type="submit">保存</button>
          <button type="button" class="ghost" @click="closeParameterDialog()">取消</button>
        </div>
      </form>
    </div>

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
              >{{ doc.title }}</button>
            </div>
          </aside>
          <article class="doc-preview-main">
            <div class="post-article">
              <h1 class="post-title">{{ selectedPreviewDocument.title }}</h1>
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

    <div v-if="showUserDialog" class="modal-backdrop" @click.self="closeUserDialog()">
      <form class="modal-panel" @submit.prevent="createUser">
        <div class="panel-title"><h3>新增用户</h3><button type="button" class="ghost" @click="closeUserDialog()">关闭</button></div>
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
        <div class="form-actions">
          <button type="button" class="ghost" @click="closeUserDialog()">取消</button>
          <button type="submit">创建</button>
        </div>
      </form>
    </div>

    <div v-if="showRoleDialog" class="modal-backdrop" @click.self="closeRoleDialog()">
      <form class="modal-panel" @submit.prevent="changeUserRole">
        <div class="panel-title"><h3>修改角色 — {{ userFormTarget?.username }}</h3><button type="button" class="ghost" @click="closeRoleDialog()">关闭</button></div>
        <label>角色
          <select v-model="userForm.role" required>
            <option v-for="r in allRoles" :key="r.name" :value="r.name">{{ r.name }}</option>
          </select>
        </label>
        <div class="form-actions">
          <button type="button" class="ghost" @click="closeRoleDialog()">取消</button>
          <button type="submit">保存</button>
        </div>
      </form>
    </div>

    <div v-if="showImportResultDialog" class="modal-backdrop" @click.self="showImportResultDialog = false">
      <article class="modal-panel">
        <div class="panel-title">
          <h3>导入结果</h3>
          <button type="button" class="ghost" @click="showImportResultDialog = false">关闭</button>
        </div>
        <div class="result-grid">
          <div>
            <span>扫描文件</span>
            <strong>{{ importResult?.scannedCount ?? 0 }}</strong>
          </div>
          <div>
            <span>成功导入</span>
            <strong>{{ importResult?.importedCount ?? 0 }}</strong>
          </div>
          <div>
            <span>跳过文件</span>
            <strong>{{ importResult?.skippedCount ?? 0 }}</strong>
          </div>
          <div>
            <span>失败文件</span>
            <strong>{{ importResult?.failedCount ?? 0 }}</strong>
          </div>
        </div>
        <div class="form-actions">
          <button type="button" @click="showImportResultDialog = false">确定</button>
        </div>
      </article>
    </div>

    <div v-if="deleteTarget" class="modal-backdrop" @click.self="closeDeleteReleaseDialog()">
      <article class="modal-panel">
        <div class="panel-title">
          <h3>删除资源</h3>
          <button type="button" class="ghost" :disabled="deletingRelease" @click="closeDeleteReleaseDialog()">关闭</button>
        </div>
        <p class="confirm-message">
          确认删除 {{ deleteTarget.middlewareName }} {{ deleteTarget.version }}？
        </p>
        <div class="form-actions">
          <button type="button" class="ghost" :disabled="deletingRelease" @click="closeDeleteReleaseDialog()">取消</button>
          <button type="button" class="danger" :disabled="deletingRelease" @click="confirmDeleteRelease()">
            {{ deletingRelease ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </article>
    </div>

    <p v-if="notice" :class="['notice', notice.type]">{{ notice.message }}</p>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import { clearAuth, fileUrl, getSavedAuth, request, saveAuth } from './api'
import CryptoJS from 'crypto-js'
import Pagination from './components/Pagination.vue'
import DocumentEditor from './components/DocumentEditor.vue'
import ForumPostList from './components/ForumPostList.vue'
import ForumPostDetail from './components/ForumPostDetail.vue'
import ForumPostEditor from './components/ForumPostEditor.vue'

function sha256(str) {
  return Promise.resolve(CryptoJS.SHA256(str).toString())
}

const markdown = new MarkdownIt({ html: false, linkify: true, breaks: true })
const route = reactive(Object.assign({ documentId: null, postId: null }, parseRoute()))
const notice = ref('')
const auth = reactive({ token: '', user: null })
const selectedRelease = ref(null)
const publicStandards = ref([])
const selectedPublicStandard = ref(null)
const publicDocuments = ref([])
const publicDocCategory = ref('全部')
const activeStdTocId = ref('')

let stdScrollHandler = null
function initStdScrollSpy() {
  if (stdScrollHandler) window.removeEventListener('scroll', stdScrollHandler)
  stdScrollHandler = () => {
    const items = standardTocItems.value
    if (!items.length) return
    let current = ''
    for (const item of items) {
      const el = document.getElementById(item.id)
      if (el && el.getBoundingClientRect().top <= 120) current = item.id
    }
    activeStdTocId.value = current
  }
  window.addEventListener('scroll', stdScrollHandler, { passive: true })
}
function destroyStdScrollSpy() {
  if (stdScrollHandler) { window.removeEventListener('scroll', stdScrollHandler); stdScrollHandler = null }
}
const standardsLoading = ref(false)
const editing = ref(false)
const adminSection = ref('files')
const showImport = ref(false)
const importing = ref(false)
const importResult = ref(null)
const showImportResultDialog = ref(false)
const showPassword = ref(false)
const showTypes = ref(false)
const showCategoryDialog = ref(false)
const showTypeDialog = ref(false)
const showStandardDialog = ref(false)
const selectedPreviewDocument = ref(null)
const previewTocActiveId = ref('')
const showParameterDialog = ref(false)
const selectedStandard = ref(null)
const deleteTarget = ref(null)
const deletingRelease = ref(false)

const publicFilters = reactive({ keyword: '', platform: '', page: 0, size: 12 })
const adminFilters = reactive({ keyword: '', platform: '', published: '', page: 0, size: 10 })
const typeFilters = reactive({ category: '', name: '', page: 0, size: 10 })
const standardFilters = reactive({ keyword: '', category: '', software: '', status: '', page: 0, size: 10 })
const parameterFilters = reactive({ page: 0, size: 10 })
const maintenanceDocumentFilters = reactive({ keyword: '', documentType: '', status: '', page: 0, size: 10 })
const publicPage = reactive(emptyPage(12))
const adminPage = reactive(emptyPage(10))
const softwareCategories = ref([])
const softwareTypes = ref([])
const standardDocuments = ref([])
const standardParameters = ref([])
const userList = ref([])
const showUserDialog = ref(false)
const showRoleDialog = ref(false)
const userFormTarget = ref(null)
const userForm = reactive({ username: '', displayName: '', password: '', role: '开发经理' })
const allRoles = ref([])
const loginForm = reactive({ username: '', password: '' })
const releaseForm = reactive(defaultReleaseForm())
const importForm = reactive(defaultImportForm())
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const categoryForm = reactive({ name: '' })
const typeForm = reactive(defaultTypeForm())
const standardForm = reactive(defaultStandardForm())
const parameterForm = reactive(defaultParameterForm())

// ── RBAC helpers ──
const currentUserRole = computed(() => auth.user?.role || '')
const isSysAdmin = computed(() => currentUserRole.value === '系统管理员')
const isManager = computed(() => ['中间件管理岗', '数据库管理岗', '主机管理岗', '网络管理岗', '网络安全岗'].includes(currentUserRole.value))
const isReadOnly = computed(() => currentUserRole.value === '开发经理' || currentUserRole.value === '运维经理')
const canAccessAdmin = computed(() => isSysAdmin.value || isManager.value)
const managedCategory = computed(() => {
  const map = { '中间件管理岗': '中间件', '数据库管理岗': '数据库', '主机管理岗': '主机', '网络管理岗': '网络', '网络安全岗': '安全' }
  return map[currentUserRole.value] || ''
})

const adminPublishedParam = computed(() => adminFilters.published === '' ? '' : `&published=${adminFilters.published}`)
const adminSectionLabel = computed(() => {
  if (adminSection.value === 'files') return { eyebrow: 'Files', title: '文件管理' }
  if (adminSection.value === 'types') return { eyebrow: 'Types', title: '类型管理' }
  if (adminSection.value === 'standardPublish') return { eyebrow: 'Standards', title: '标准发布' }
  if (adminSection.value === 'documentMaintenance') return { eyebrow: 'Documents', title: '标准文档' }
  return { eyebrow: 'Admin', title: '用户管理' }
})
const pageTitle = computed(() => {
  if (route.name === 'home') return '运营集成中心门户'
  if (route.name === 'public') return '软件下载'
  if (route.name === 'standards') return '标准发布'
  if (route.name === 'documentEditor') return '文档编辑'
  if (route.name && route.name.startsWith('forum')) return 'infra论坛'
  return '管理后台'
})
const publicStandardHtml = computed(() => {
  let html = markdown.render(selectedPublicStandard.value?.renderedContent || selectedPublicStandard.value?.content || '')
  let idx = 0
  html = html.replace(/<(h[1-3])([^>]*)>([\s\S]*?)<\/\1>/g, (_, tag, attrs, inner) => {
    if (/id=/.test(attrs)) return `<${tag}${attrs}>${inner}</${tag}>`
    return `<${tag}${attrs} id="std-toc-${idx++}">${inner}</${tag}>`
  })
  return html
})

const standardTocItems = computed(() => {
  const items = []
  const re = /<(h[1-3])[^>]*id="([^"]*)"[^>]*>([\s\S]*?)<\/\1>/g
  let m
  while ((m = re.exec(publicStandardHtml.value))) {
    const level = parseInt(m[1][1])
    const id = m[2]
    const text = m[3].replace(/<[^>]+>/g, '').trim()
    if (text) items.push({ level, id, text })
  }
  return items
})
const publicDocumentGroups = computed(() => {
  const groups = new Map()
  for (const doc of publicDocuments.value) {
    const category = doc.category || '未分类'
    if (!groups.has(category)) groups.set(category, [])
    groups.get(category).push(doc)
  }
  for (const docs of groups.values()) {
    docs.sort((a, b) => {
      if (a.documentType === 'STANDARD' && b.documentType !== 'STANDARD') return -1
      if (a.documentType !== 'STANDARD' && b.documentType === 'STANDARD') return 1
      return (a.title || '').localeCompare(b.title || '')
    })
  }
  return Array.from(groups, ([category, documents]) => ({ category, documents }))
})

const publicDocCategories = computed(() => {
  const cats = new Set()
  for (const doc of publicDocuments.value) {
    if (doc.category) cats.add(doc.category)
  }
  return ['全部', ...Array.from(cats)]
})

const filteredPublicDocuments = computed(() => {
  const tab = publicDocCategory.value
  const docs = publicDocuments.value.filter(d => tab === '全部' || d.category === tab)
  docs.sort((a, b) => {
    if (a.documentType === 'STANDARD' && b.documentType !== 'STANDARD') return -1
    if (a.documentType !== 'STANDARD' && b.documentType === 'STANDARD') return 1
    return (a.title || '').localeCompare(b.title || '')
  })
  return docs
})

const publicStandardGroups = computed(() => {
  const groups = new Map()
  for (const standard of publicStandards.value) {
    const category = standard.category || '未分类'
    if (!groups.has(category)) {
      groups.set(category, [])
    }
    groups.get(category).push(standard)
  }
  return Array.from(groups, ([category, standards]) => ({ category, standards }))
})
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
    if (doc.documentType !== 'STANDARD') return false
    const matchesCategory = !standardFilters.category || doc.category === standardFilters.category
    const matchesManagedCategory = !managedCategory.value || doc.category === managedCategory.value
    const matchesStatus = !standardFilters.status || doc.status === standardFilters.status
    const matchesSoftware = !standardFilters.software || doc.software === standardFilters.software
    const matchesKeyword = !keyword ||
      doc.title.toLowerCase().includes(keyword) ||
      (doc.summary || '').toLowerCase().includes(keyword) ||
      (doc.softwareVersion || '').toLowerCase().includes(keyword) ||
      (doc.standardVersion || '').toLowerCase().includes(keyword)
    return matchesCategory && matchesManagedCategory && matchesStatus && matchesSoftware && matchesKeyword
  })
})
const standardDocumentOptions = computed(() => standardDocuments.value.filter(doc => doc.documentType === 'STANDARD'))
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
  return standardParameters.value.filter(parameter =>
    String(parameter.standardDocumentId) === String(selectedStandard.value.id)
  )
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
  let html = ''
  try { html = markdown.render(rendered) } catch { html = '<p style="color:#b7333d">Markdown 渲染出错</p>' }
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
  return { id: null, category: '', softwareTypeId: '', middlewareName: '', version: '', platform: '', description: '', releasedAt: todayString(), published: false, file: null, originalFileName: '' }
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
    standardVersion: '',
    code: '',
    summary: '',
    content: '# 参数标准\n\n'
  }
}

function defaultParameterForm() {
  return { id: null, standardDocumentId: null, code: '', name: '', value: '', category: '', description: '', active: true }
}

function parseRoute() {
  const hash = window.location.hash.replace(/^#/, '')
  if (!hash || hash === '/' || hash === '/home') return { name: 'home', token: null }
  if (hash.startsWith('/admin/document-editor')) {
    const editorMatch = hash.match(/^\/admin\/document-editor\/(\d+)$/)
    return { name: 'documentEditor', documentId: editorMatch ? editorMatch[1] : null }
  }
  if (hash.startsWith('/admin')) return { name: 'admin', token: null }
  if (hash.startsWith('/forum/new')) return { name: 'forumEditor', postId: null }
  const forumEditMatch = hash.match(/^\/forum\/edit\/(\d+)$/)
  if (forumEditMatch) return { name: 'forumEditor', postId: forumEditMatch[1] }
  const forumPostMatch = hash.match(/^\/forum\/post\/(\d+)$/)
  if (forumPostMatch) return { name: 'forumDetail', postId: forumPostMatch[1] }
  if (hash === '/forum' || hash.startsWith('/forum')) return { name: 'forum', postId: null }
  const detailMatch = hash.match(/^\/downloads\/(.+)$/)
  if (detailMatch) return { name: 'public', token: detailMatch[1] }
  const standardMatch = hash.match(/^\/standards\/(\d+)$/)
  if (standardMatch) return { name: 'standards', standardId: standardMatch[1] }
  if (hash === '/standards') return { name: 'standards', standardId: null }
  return { name: 'public', token: null }
}

function syncRoute() {
  const next = parseRoute()
  route.name = next.name
  route.token = next.token
  route.standardId = next.standardId
  route.documentId = next.documentId
  route.postId = next.postId
  updateDocumentTitle()
  if (route.name === 'documentEditor' || route.name === 'forum' || route.name === 'forumDetail' || route.name === 'forumEditor') {
    if (route.name === 'documentEditor' && auth.token) {
      loadSoftwareTypes()
      loadSoftwareCategories()
      loadStandardModule()
    }
    return
  }
  if (route.name === 'home') {
    loadPublic()
  } else if (route.name === 'public') {
    if (route.token) {
      loadDetail(route.token)
    } else {
      selectedRelease.value = null
      loadPublic()
    }
  } else if (route.name === 'standards') {
    if (route.standardId) {
      loadPublicDocuments()
      loadPublicStandardDetail(route.standardId)
    } else {
      selectedPublicStandard.value = null
      loadPublicStandards()
    }
  } else if (auth.token) {
    if (isReadOnly.value) {
      window.location.hash = '#/home'
      return
    }
    loadAdmin()
    loadSoftwareTypes()
    loadSoftwareCategories()
    loadStandardModule()
  }
}

function updateDocumentTitle() {
  document.title = `${pageTitle.value} - 运营集成中心`
}

function applyPage(target, source) {
  Object.assign(target, source)
}

async function loadPublic() {
  selectedRelease.value = null
  const query = new URLSearchParams(publicFilters).toString()
  applyPage(publicPage, await request(`/api/public/releases?${query}`, { token: null }))
}

async function loadDetail(token) {
  selectedRelease.value = await request(`/api/public/releases/${token}`, { token: null })
}

async function loadPublicStandards() {
  publicStandards.value = await request('/api/public/standards', { token: null })
}

async function loadPublicDocuments() {
  try {
    publicDocuments.value = await request('/api/public/standards/all', { token: null })
  } catch {
    publicDocuments.value = []
  }
}

async function loadPublicStandardDetail(id) {
  standardsLoading.value = true
  activeStdTocId.value = ''
  try {
    selectedPublicStandard.value = await request(`/api/public/standards/${id}`, { token: null })
    await nextTick()
    initStdScrollSpy()
  } finally {
    standardsLoading.value = false
  }
}

async function loadAdmin() {
  const query = `keyword=${encodeURIComponent(adminFilters.keyword)}&platform=${encodeURIComponent(adminFilters.platform)}&page=${adminFilters.page}&size=${adminFilters.size}${adminPublishedParam.value}`
  applyPage(adminPage, await request(`/api/admin/releases?${query}`))
}

async function loadSoftwareTypes() {
  softwareTypes.value = await request('/api/admin/software-types')
  if (typeFilters.page >= typePage.value.totalPages) {
    typeFilters.page = Math.max(typePage.value.totalPages - 1, 0)
  }
}

async function loadSoftwareCategories() {
  softwareCategories.value = await request('/api/admin/software-type-categories')
}

async function loadSoftwareMetadata() {
  await loadSoftwareCategories()
  await loadSoftwareTypes()
}

async function loadStandardModule() {
  await loadStandardDocuments()
}

async function loadStandardDocuments() {
  standardDocuments.value = await request('/api/admin/standard-documents')
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

async function loadStandardParameters(standardDocumentId = selectedStandard.value?.id) {
  if (!standardDocumentId) {
    standardParameters.value = []
    return
  }
  standardParameters.value = await fetchStandardParameters(standardDocumentId)
  if (parameterFilters.page >= parameterPage.value.totalPages) {
    parameterFilters.page = Math.max(parameterPage.value.totalPages - 1, 0)
  }
}

function fetchStandardParameters(standardDocumentId) {
  return request(`/api/admin/standard-parameters?standardDocumentId=${encodeURIComponent(standardDocumentId)}`)
}

async function login() {
  try {
    const pwHash = await sha256(loginForm.password)
    const token = btoa(`${loginForm.username}:${pwHash}`)
    const user = await request('/api/auth/login', { token })
    auth.token = saveAuth(loginForm.username, pwHash, user)
    auth.user = user
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
    notify(error.status === 401 ? '账号或密码错误，请重新输入' : (error.message || '登录失败'), 'error')
  }
}

function logout(showMessage = true) {
  clearAuth()
  auth.token = ''
  auth.user = null
  loginForm.password = ''
  if (showMessage) {
    notify('已退出')
  }
}

function goPublic() {
  window.location.hash = '#/downloads'
}

function goStandards() {
  window.location.hash = '#/standards'
}

function goHome() {
  window.location.hash = '#/home'
}

function goAdmin() {
  window.location.hash = '#/admin'
}

function goLogin() {
  window.location.hash = '#/admin'
}

function goForum() { window.location.hash = '#/forum' }
function goForumPost(id) { window.location.hash = `#/forum/post/${id}` }
function goForumNew() {
  if (!auth.token) { goLogin(); return }
  window.location.hash = '#/forum/new'
}
function goForumEdit(id) { window.location.hash = `#/forum/edit/${id}` }
function onForumPostSaved() { goForum() }

function handleDownload(url) {
  if (!auth.token) {
    notify('请先登录后再下载文件')
    window.location.hash = '#/admin'
    return
  }
  window.open(fileUrl(url), '_blank')
}

function goDocumentEditor() {
  window.location.hash = '#/admin/document-editor'
}

function goDocumentEditorEdit(id) {
  window.location.hash = `#/admin/document-editor/${id}`
}

function onDocumentEditorSaved() {
  notify('文档已保存', 'success')
  loadStandardDocuments()
  window.location.hash = '#/admin'
  setTimeout(() => { adminSection.value = 'documentMaintenance' }, 0)
}

function onDocumentEditorCancel() {
  window.location.hash = '#/admin'
  setTimeout(() => { adminSection.value = 'documentMaintenance' }, 0)
}

function openDetail(token) {
  window.location.hash = `#/downloads/${token}`
}

function closeDetail() {
  window.location.hash = '#/downloads'
}

function openPublicStandardDetail(id) {
  window.location.hash = `#/standards/${id}`
}

function closePublicStandardDetail() {
  selectedPublicStandard.value = null
  destroyStdScrollSpy()
  window.location.hash = '#/standards'
}

function scrollToStdHeading(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}

function openPortalModule(name) {
  notify(`${name}模块正在建设中`)
}

function documentTypeLabel(type) {
  if (type === 'STANDARD') return '标准'
  if (type === 'ARTICLE') return '文章'
  return '手册'
}

function formatDate(value) {
  if (!value) return '-'
  return String(value).slice(0, 10)
}

function changePublicPage(page) {
  publicFilters.page = page
  loadPublic()
}

function changeAdminPage(page) {
  adminFilters.page = page
  loadAdmin()
}

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
  } else if (section === 'users') {
    loadUsers()
  } else {
    loadAdmin()
    loadSoftwareTypes()
    loadSoftwareCategories()
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
    originalFileName: release.originalFileName || ''
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
  for (const key of ['middlewareName', 'version', 'platform', 'description', 'releasedAt', 'published']) {
    formData.append(key, releaseForm[key] ?? '')
  }
  formData.append('softwareTypeId', releaseForm.softwareTypeId)
  if (releaseForm.file) {
    formData.append('file', releaseForm.file)
  }

  try {
    await request(releaseForm.id ? `/api/admin/releases/${releaseForm.id}` : '/api/admin/releases', {
      method: releaseForm.id ? 'PUT' : 'POST',
      body: formData
    })
    notify('资源已保存', 'success')
    cancelEdit()
    await loadAdmin()
  } catch (error) {
    notify(error.message || '保存失败', 'error')
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
  const standard = standardDocuments.value.find(doc => String(doc.id) === String(id))
  if (!standard) return '-'
  return [standard.category, standard.software, standard.softwareVersion, standard.standardVersion]
    .filter(Boolean)
    .join(' / ')
}

function findStandardDocument(id) {
  return standardDocuments.value.find(doc => String(doc.id) === String(id))
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
  if (!confirm(`确认删除类型 ${type.category} / ${type.name}？`)) return
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
    standardVersion: document.standardVersion || '',
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
  return [selectedType?.category, selectedType?.name, standardForm.softwareVersion, standardForm.standardVersion]
    .filter(Boolean)
    .join(' / ')
}

async function saveStandard() {
  const selectedType = findSoftwareType(standardForm.softwareTypeId)
  if (!selectedType) {
    notify('请选择软件类型')
    return
  }
  const body = {
    id: standardForm.id,
    title: buildStandardTitle(selectedType),
    documentType: 'STANDARD',
    summary: standardForm.summary,
    softwareTypeId: selectedType.id,
    category: selectedType.category,
    software: selectedType.name,
    softwareVersion: standardForm.softwareVersion,
    standardVersion: standardForm.standardVersion,
    code: standardForm.code,
    content: standardForm.content || '# 参数标准\n\n'
  }
  const actionText = standardForm.id ? '修改' : '新增'
  try {
    await request(standardForm.id ? `/api/admin/standard-documents/${standardForm.id}` : '/api/admin/standard-documents', {
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

async function toggleDocumentPublish(document) {
  const action = document.status === 'PUBLISHED' ? 'unpublish' : 'publish'
  const actionText = action === 'publish' ? '发布' : '下架'
  try {
    await request(`/api/admin/standard-documents/${document.id}/${action}`, { method: 'POST' })
    notify(`文档已${actionText}`, 'success')
    await loadStandardDocuments()
  } catch (error) {
    notify(error.message || `文档${actionText}失败`, 'error')
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
  Object.assign(parameterForm, defaultParameterForm(), { standardDocumentId: selectedStandard.value.id })
  showParameterDialog.value = true
}

function openEditParameterDialog(parameter) {
  Object.assign(parameterForm, {
    id: parameter.id,
    standardDocumentId: parameter.standardDocumentId || selectedStandard.value?.id || null,
    code: parameter.code,
    name: parameter.name,
    value: parameter.value,
    category: parameter.category || '',
    description: parameter.description || '',
    active: parameter.active
  })
  showParameterDialog.value = true
}

function closeParameterDialog() {
  showParameterDialog.value = false
  Object.assign(parameterForm, defaultParameterForm())
}

async function saveParameter() {
  const targetStandardId = selectedStandard.value?.id || parameterForm.standardDocumentId
  if (!targetStandardId) {
    notify('参数必须绑定到标准')
    return
  }
  const body = {
    ...parameterForm,
    standardDocumentId: targetStandardId
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

async function changePassword() {
  try {
    const cp = await sha256(passwordForm.currentPassword)
    const np = await sha256(passwordForm.newPassword)
    await request('/api/admin/account/password', { method: 'POST', body: { currentPassword: cp, newPassword: np, confirmPassword: np } })
    Object.assign(passwordForm, { currentPassword: '', newPassword: '', confirmPassword: '' })
    notify('密码已更新，请使用新密码重新登录', 'success')
    logout(false)
  } catch (error) {
    notify(error.message || '密码修改失败', 'error')
  }
}

function formatBytes(size) {
  if (!size) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let value = size
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  return `${value.toFixed(value >= 10 || unit === 0 ? 0 : 1)} ${units[unit]}`
}

function notify(message, type = 'info') {
  notice.value = { message, type }
  window.setTimeout(() => {
    if (notice.value?.message === message) notice.value = ''
  }, 3200)
}

// ── User management ──
async function loadUsers() {
  try { userList.value = await request('/api/admin/users') } catch { userList.value = [] }
}

async function loadRoles() {
  try { allRoles.value = await request('/api/admin/users/roles') } catch { allRoles.value = [] }
}

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
  if (!confirm(`确认删除用户 ${user.username}？此操作不可撤销。`)) return
  try {
    await request(`/api/admin/users/${user.id}`, { method: 'DELETE' })
    notify('用户已删除', 'success')
    await loadUsers()
  } catch (error) {
    notify(error.message || '删除失败', 'error')
  }
}

window.addEventListener('hashchange', syncRoute)
window.addEventListener('unhandledrejection', (event) => {
  const message = event.reason?.message || '请求失败'
  notify(message, 'error')
  if (event.reason?.status === 401) {
    logout(false)
  }
  event.preventDefault()
})

onMounted(() => {
  const saved = getSavedAuth()
  if (saved) {
    auth.token = saved.token
    auth.user = saved.user
  }
  syncRoute()
})
</script>
