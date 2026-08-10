// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ForumTagsSection from '../src/pages/admin/ForumTagsSection.vue'
import AdminPage from '../src/pages/admin/AdminPage.vue'
import { parseHashRoute } from '../src/composables/useRoute.js'
import appSource from '../src/App.vue?raw'
import useAdminSource from '../src/composables/useAdmin.js?raw'

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

  it('TC-FORUM-TAG-001 (TC-01) 论坛管理页面展示标签管理子 Tab', async () => {
    const admin = mount(AdminPage, {
      props: { section: 'forumTags', isSysAdmin: true, canManageForumTags: true }
    })
    const forumEntry = admin.findAll('.side-nav button').find((button) => button.text() === '论坛管理')

    expect(forumEntry).toBeTruthy()
    expect(forumEntry.classes()).toContain('active')
    expect(admin.text()).not.toContain('论坛管理 / 标签管理')

    const wrapper = mountSection({ isSysAdmin: true, managedCategory: '' })
    await flushPromises()

    const tagTab = wrapper.get('[role="tab"]')
    expect(tagTab.text()).toBe('标签管理')
    expect(tagTab.attributes('aria-selected')).toBe('true')
  })

  it('TC-FORUM-TAG-002 (TC-02) 点击论坛管理后进入标签管理并保留核心功能入口', async () => {
    const admin = mount(AdminPage, {
      props: { section: 'files', isSysAdmin: true, canManageForumTags: true }
    })
    const forumEntry = admin.findAll('.side-nav button').find((button) => button.text() === '论坛管理')
    await forumEntry.trigger('click')
    expect(admin.emitted('switchSection')).toContainEqual(['forumTags'])

    const wrapper = mountSection({ isSysAdmin: true, managedCategory: '' })
    await flushPromises()
    expect(wrapper.text()).toContain('性能优化')
    expect(wrapper.get('input[placeholder="搜索标签名称"]')).toBeTruthy()
    expect(wrapper.get('[data-action="add"]')).toBeTruthy()
    expect(wrapper.text()).toContain('3 篇')
    expect(wrapper.findAll('[data-action="edit"]')).toHaveLength(2)
    expect(wrapper.findAll('[data-action="delete"]')).toHaveLength(2)
  })

  it('TC-FORUM-TAG-003 (TC-03) 标签管理主页面不展示小组选择或切换入口', async () => {
    const wrapper = mountSection({ isSysAdmin: true, managedCategory: '' })
    await flushPromises()

    expect(wrapper.find('[aria-label="所属小组筛选"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('全部小组')
  })

  it('TC-FORUM-TAG-004 (TC-04) 刷新论坛管理标签地址后保持标签管理子 Tab', () => {
    expect(parseHashRoute('#/admin/forum/tags')).toEqual({
      name: 'admin', token: null, adminSection: 'forumTags'
    })
    expect(useAdminSource).toContain("s === 'forumTags' ? '#/admin/forum/tags' : '#/admin'")
    expect(appSource).toContain("next.adminSection === 'forumTags'")
  })

  it('TC-FORUM-TAG-006 (TC-06) 无论坛管理权限用户看不到入口且直达地址受后台权限守卫', () => {
    const admin = mount(AdminPage, {
      props: { isSysAdmin: false, canManageForumTags: false }
    })

    expect(admin.text()).not.toContain('论坛管理')
    expect(parseHashRoute('#/admin/forum/tags')).toEqual({
      name: 'admin', token: null, adminSection: 'forumTags'
    })
    expect(appSource).toContain('if (!canAccessAdmin.value)')
    expect(appSource).toContain('if (!canManageForumTags.value)')
    expect(appSource).toContain("window.location.hash = '#/home'")
  })

  it('TC-FORUM-TAG-007 添加编辑删除调用统一API并刷新列表', async () => {
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
    await wrapper.get('[data-field="category"]').setValue('主机')
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

  it('TC-FORUM-TAG-007 (TC-07) 名称空白、超长和后端重名消息均明确展示', async () => {
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
    await wrapper.get('[data-field="category"]').setValue('中间件')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('该小组已存在同名标签')
  })
})
