// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ForumTagsSection from '../src/pages/admin/ForumTagsSection.vue'
import ForumManagementSection from '../src/pages/admin/ForumManagementSection.vue'
import AdminPage from '../src/pages/admin/AdminPage.vue'
import { parseHashRoute } from '../src/composables/useRoute.js'
import appSource from '../src/App.vue?raw'

const tags = [
  { id: 1, name: '性能优化', category: '中间件', postCount: 3 },
  { id: 2, name: '索引设计', category: '数据库', postCount: 2 }
]
const storage = new Map()
const localStorageMock = {
  clear: () => storage.clear(),
  getItem: (key) => storage.has(key) ? storage.get(key) : null,
  removeItem: (key) => storage.delete(key),
  setItem: (key, value) => storage.set(key, String(value))
}

function response(body, status = 200) {
  return Promise.resolve(new Response(body == null ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  }))
}

describe('论坛标签管理验收', () => {
  beforeEach(() => {
    Object.defineProperty(globalThis, 'localStorage', { value: localStorageMock, configurable: true })
    window.localStorage.setItem('mrm.token', 'test-token')
    vi.stubGlobal('fetch', vi.fn(() => response(tags)))
  })

  afterEach(() => {
    window.localStorage.clear()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  function mountSection(props) {
    return mount(ForumTagsSection, {
      props,
      global: { stubs: { teleport: true } }
    })
  }

  it('TC-FORUM-TAG-001 (TC-01) 标签管理作为论坛管理子Tab展示且不是独立菜单', async () => {
    const admin = mount(AdminPage, {
      props: { section: 'forum', isSysAdmin: true, canManageForumTags: true }
    })
    expect(admin.findAll('.side-nav button').map((button) => button.text())).toContain('论坛管理')
    expect(admin.text()).not.toContain('论坛管理 / 标签管理')

    const wrapper = mount(ForumManagementSection, {
      props: { tab: 'tags', isSysAdmin: true, managedCategory: '' },
      global: { stubs: { teleport: true } }
    })
    await flushPromises()

    expect(wrapper.findAll('[role="tab"]').map((tab) => tab.text())).toEqual(['帖子管理', '评论管理', '标签管理'])
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('标签管理')
    expect(wrapper.text()).toContain('性能优化')
    expect(wrapper.text()).toContain('3 篇')
    expect(wrapper.findAll('[data-action="edit"]')).toHaveLength(2)
    expect(wrapper.findAll('[data-action="delete"]')).toHaveLength(2)
  })

  it('TC-FORUM-TAG-002 (TC-02) 点击标签管理子Tab正常加载列表和操作区', async () => {
    vi.mocked(fetch).mockImplementation(() => response([tags[0]]))
    const wrapper = mount(ForumManagementSection, {
      props: { tab: 'posts', isSysAdmin: false, managedCategory: '中间件' },
      global: { stubs: { teleport: true } }
    })
    await wrapper.findAll('[role="tab"]')[2].trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('性能优化')
    expect(wrapper.text()).not.toContain('索引设计')
    expect(wrapper.find('[data-action="add"]').exists()).toBe(true)
    expect(wrapper.find('[data-field="category"]').exists()).toBe(false)
  })

  it('TC-FORUM-TAG-003 (TC-03) 标签管理页面不展示小组选择按钮且刷新后仍不存在', () => {
    const wrapper = mountSection({ isSysAdmin: true, managedCategory: '' })
    expect(wrapper.find('[aria-label="所属小组筛选"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('全部小组')
    expect(wrapper.text()).not.toContain('请选择所属小组')
  })

  it('TC-FORUM-TAG-004 (TC-04) 移除小组选择后标签列表默认加载并支持分页', async () => {
    const manyTags = Array.from({ length: 11 }, (_, index) => ({
      ...tags[index % tags.length], id: index + 1, name: `标签${index + 1}`
    }))
    vi.mocked(fetch).mockImplementation(() => response(manyTags))
    const wrapper = mountSection({ isSysAdmin: true, managedCategory: '' })
    await flushPromises()
    expect(wrapper.findAll('[data-row-key]').length).toBeGreaterThan(0)
    expect(wrapper.find('[data-page="next"]').exists()).toBe(true)
    await wrapper.get('[data-page="next"]').trigger('click')
    expect(wrapper.find('[data-page="current"]').text()).toBe('2')
  })

  it('TC-FORUM-TAG-005 (TC-05) 无标签数据时显示空状态且无小组选择按钮', async () => {
    vi.mocked(fetch).mockImplementation(() => response([]))
    const wrapper = mountSection({ isSysAdmin: true, managedCategory: '' })
    await flushPromises()
    expect(wrapper.text()).toContain('暂无符合条件的标签')
    expect(wrapper.find('[aria-label="所属小组筛选"]').exists()).toBe(false)
  })

  it('TC-FORUM-TAG-006 (TC-06) 历史标签管理入口归属论坛管理并选中标签子Tab', () => {
    expect(parseHashRoute('#/admin/forum-tags')).toEqual({
      name: 'admin', token: null, adminSection: 'forum', adminForumTab: 'tags'
    })
  })

  it('TC-FORUM-TAG-007 (TC-07) 无论坛管理权限用户不可见论坛管理入口', () => {
    const admin = mount(AdminPage, {
      props: { isSysAdmin: false, canManageForumTags: false }
    })

    expect(admin.text()).not.toContain('论坛管理')
    expect(appSource).toContain('if (!canAccessAdmin.value)')
    expect(appSource).toContain("window.location.hash = '#/home'")
    expect(appSource).toMatch(
      /next\.name === 'admin' && next\.adminSection === 'forum'[\s\S]*!isSysAdmin\.value && !isCategoryAdmin\.value/
    )
    expect(parseHashRoute('#/admin/forum-tags').adminSection).toBe('forum')
    expect(parseHashRoute('#/admin/forum/tags').adminForumTab).toBe('tags')
    expect(parseHashRoute('#/admin/forum').adminForumTab).toBe('tags')
  })

  it('TC-FORUM-TAG-008 添加编辑删除调用统一API并刷新列表', async () => {
    vi.mocked(fetch).mockImplementation((input, options = {}) => {
      const method = options.method || 'GET'
      if (method === 'GET') return response(tags)
      if (method === 'DELETE') return response(null, 204)
      return response(tags[0])
    })
    const wrapper = mountSection({ isSysAdmin: true, managedCategory: '' })
    await flushPromises()

    await wrapper.get('[data-action="add"]').trigger('click')
    await wrapper.get('[data-field="name"] input').setValue('容量规划')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(fetch).toHaveBeenCalledWith('/api/admin/forum-tags', expect.objectContaining({ method: 'POST' }))

    await wrapper.get('[data-action="edit"]').trigger('click')
    await wrapper.get('[data-field="name"] input').setValue('性能调优')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(fetch).toHaveBeenCalledWith('/api/admin/forum-tags/1', expect.objectContaining({ method: 'PUT' }))

    await wrapper.get('[data-action="delete"]').trigger('click')
    await wrapper.get('[data-confirm="delete"]').trigger('click')
    await flushPromises()
    expect(fetch).toHaveBeenCalledWith('/api/admin/forum-tags/1', expect.objectContaining({ method: 'DELETE' }))
  })

  it('TC-FORUM-TAG-009 名称空白、超长和后端重名消息均明确展示', async () => {
    const wrapper = mountSection({ isSysAdmin: true, managedCategory: '' })
    await flushPromises()
    await wrapper.get('[data-action="add"]').trigger('click')

    await wrapper.get('[data-field="name"] input').setValue('   ')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('标签名称不能为空')

    await wrapper.get('[data-field="name"] input').setValue('超'.repeat(51))
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('标签名称不能超过 50 个字符')

    vi.mocked(fetch).mockImplementation(() => response({ message: '该小组已存在同名标签' }, 400))
    await wrapper.get('[data-field="name"] input').setValue('性能优化')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('该小组已存在同名标签')
  })
})
