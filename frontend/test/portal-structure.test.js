// @vitest-environment jsdom

import { readFile } from 'node:fs/promises'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import HomePage from '../src/pages/HomePage.vue'
import DownloadsPage from '../src/pages/DownloadsPage.vue'
import StandardsPage from '../src/pages/StandardsPage.vue'
import ForumPostList from '../src/components/ForumPostList.vue'
import JobNavigation from '../src/shared/jobs/JobNavigation.vue'
import EmptyState from '../src/components/ui/EmptyState.vue'
import { publicFeatures } from '../src/config/portalFeatures.js'
import { jobModules } from '../src/modules/index.js'
import { filterItemsByJob, matchesJob, normalizeJobId } from '../src/shared/jobs/jobFilter.js'
import { parseHashRoute } from '../src/composables/useRoute.js'

const readSource = (path) => readFile(new URL(path, import.meta.url), 'utf8')
const mountedWrappers = []
const storage = new Map()
const localStorageMock = {
  clear: () => storage.clear(),
  getItem: (key) => storage.has(key) ? storage.get(key) : null,
  removeItem: (key) => storage.delete(key),
  setItem: (key, value) => storage.set(key, String(value))
}

const releases = [
  { downloadToken: 'network', middlewareName: 'Network Toolkit', version: '1.0', softwareTypeCategory: '网络' },
  { downloadToken: 'security', middlewareName: 'Security Toolkit', version: '2.0', softwareTypeCategory: '网络安全' },
  { downloadToken: 'database', middlewareName: 'Database Toolkit', version: '3.0', softwareTypeCategory: '数据库' }
]

const standards = [
  { id: 1, title: 'Network Standard', category: '网络', software: 'Switch', relatedDocuments: [] },
  { id: 2, title: 'Security Standard', category: '网络安全', software: 'Firewall', relatedDocuments: [] },
  { id: 3, title: 'Database Standard', category: '数据库', software: 'MySQL', relatedDocuments: [] }
]

const posts = [
  { id: 1, title: 'Network Post', summary: 'network', tags: ['网络'], authorDisplayName: 'A' },
  { id: 2, title: 'Security Post', summary: 'security', tags: ['网络安全'], authorDisplayName: 'B' },
  { id: 3, title: 'Database Post', summary: 'database', tags: ['数据库'], authorDisplayName: 'C' }
]

function jsonResponse(data) {
  return Promise.resolve(new Response(JSON.stringify(data), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  }))
}

function installPublicApiMock() {
  vi.stubGlobal('fetch', vi.fn((input) => {
    const url = new URL(String(input), 'http://localhost')
    if (url.pathname === '/api/public/releases') {
      const category = url.searchParams.get('category')
      const content = category ? releases.filter((release) => release.softwareTypeCategory === category) : releases
      return jsonResponse({ content, totalElements: content.length, totalPages: content.length ? 1 : 0, first: true, last: true })
    }
    if (url.pathname === '/api/public/parameter-standards') return jsonResponse({ content: standards })
    if (url.pathname === '/api/forum/posts') {
      const job = url.searchParams.get('job')
      const content = job ? posts.filter((post) => post.tags.includes(job)) : posts
      return jsonResponse({ content, last: true })
    }
    if (url.pathname === '/api/forum/tags') return jsonResponse([])
    if (url.pathname.includes('/middleware-commands/types')) return jsonResponse([{ id: 10, name: 'Redis', category: '中间件' }])
    if (url.pathname.includes('/middleware-commands')) {
      return jsonResponse([{ id: 20, softwareTypeId: 10, commandFormat: 'redis-cli info', briefDescription: '查看 Redis 信息' }])
    }
    return jsonResponse([])
  }))
}

function track(wrapper) {
  mountedWrappers.push(wrapper)
  return wrapper
}

function findJobButton(wrapper, label) {
  return wrapper.findAll('.job-navigation-button').find((button) => button.text().includes(label))
}

async function selectJob(wrapper, label) {
  const button = findJobButton(wrapper, label)
  expect(button, `岗位导航应包含 ${label}`).toBeTruthy()
  await button.trigger('click')
  await flushPromises()
}

beforeEach(() => {
  Object.defineProperty(window, 'localStorage', { value: localStorageMock, configurable: true })
  vi.stubGlobal('localStorage', localStorageMock)
  window.localStorage.clear()
  installPublicApiMock()
})

afterEach(() => {
  while (mountedWrappers.length) mountedWrappers.pop().unmount()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('门户页面结构优化验收', () => {
  test('TC-PORTAL-001 (TC-01) 首页通过布局自然划分公共功能与岗位空间', async () => {
    const wrapper = track(mount(HomePage))
    await flushPromises()

    expect(publicFeatures.map(({ id }) => id)).toEqual(['downloads', 'standards', 'forum'])
    expect(wrapper.findAll('.portal-public-grid .portal-card')).toHaveLength(3)
    expect(wrapper.findAll('.portal-jobs-grid .portal-job-card')).toHaveLength(5)
    expect(wrapper.text()).not.toContain('公共区域')
    expect(wrapper.text()).not.toContain('岗位专属区域')
    expect(wrapper.find('.portal-section-divider').exists()).toBe(true)
  })

  test('TC-PORTAL-002 (TC-02) 五大岗位入口点击后由各自入口组件展示正确岗位', async () => {
    const home = track(mount(HomePage))
    await flushPromises()
    const cards = home.findAll('.portal-job-card')

    for (const [index, job] of jobModules.entries()) {
      await cards[index].trigger('click')
      expect(home.emitted('navigate').at(-1)).toEqual([`jobs/${job.id}`])
      expect(parseHashRoute(`/jobs/${job.id}`)).toEqual({ name: 'jobModule', jobId: job.id, feature: null })
      expect(job.entryComponent).toBeTruthy()

      const entry = track(mount(job.entryComponent, { props: { job, feature: null, context: {} } }))
      expect(entry.find('.job-workspace-header').text()).toContain(`集成中心·${job.shortName}`)
      expect(entry.find('.job-mark').text()).toBe(job.shortName)
    }
  })

  test.each([
    ['软件下载', DownloadsPage, '.release-card', 'Network Toolkit', 'Security Toolkit'],
    ['标准发布', StandardsPage, '.standard-row', 'Network Standard', 'Security Standard'],
    ['infra论坛', ForumPostList, '.forum-card', 'Network Post', 'Security Post']
  ])('TC-PORTAL-003 (TC-03) %s 使用统一左侧岗位导航并精确筛选', async (_name, component, itemSelector, expected, excluded) => {
    const props = component === ForumPostList ? { auth: {} } : {}
    const wrapper = track(mount(component, { props }))
    await flushPromises()

    const navigation = wrapper.findComponent(JobNavigation)
    expect(navigation.exists()).toBe(true)
    expect(wrapper.find('.public-module-layout').element.firstElementChild).toBe(navigation.element)
    expect(navigation.findAll('.job-navigation-button')).toHaveLength(6)

    await selectJob(wrapper, '网络')
    expect(wrapper.findAll(itemSelector)).toHaveLength(1)
    expect(wrapper.text()).toContain(expected)
    expect(wrapper.text()).not.toContain(excluded)
  })

  test('TC-PORTAL-003 (TC-03) 软件下载岗位筛选从服务端第一页返回筛选后分页数据', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = new URL(String(input), 'http://localhost')
      if (url.pathname === '/api/public/releases' && url.searchParams.get('category') === '网络') {
        return jsonResponse({ content: [releases[0]], page: 0, size: 12, totalElements: 1, totalPages: 1, first: true, last: true })
      }
      if (url.pathname === '/api/public/releases') {
        return jsonResponse({ content: [releases[1]], page: 1, size: 12, totalElements: 13, totalPages: 2, first: false, last: true })
      }
      return jsonResponse([])
    })

    const wrapper = track(mount(DownloadsPage))
    await flushPromises()
    await selectJob(wrapper, '网络')

    const releaseRequest = vi.mocked(fetch).mock.calls
      .map(([input]) => new URL(String(input), 'http://localhost'))
      .find((url) => url.pathname === '/api/public/releases' && url.searchParams.get('category') === '网络')
    expect(releaseRequest?.searchParams.get('page')).toBe('0')
    expect(wrapper.findAll('.release-card')).toHaveLength(1)
    expect(wrapper.text()).toContain('Network Toolkit')
    expect(wrapper.text()).toContain('共 1 条')
  })

  test('TC-PORTAL-003 (TC-03) 打开标准详情后切换岗位应关闭跨岗位详情并限制详情树', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = new URL(String(input), 'http://localhost')
      if (url.pathname === '/api/public/parameter-standards') return jsonResponse({ content: standards })
      if (url.pathname === '/api/public/parameter-standards/1') return jsonResponse(standards[0])
      if (url.pathname === '/api/public/standard-parameters') return jsonResponse([])
      return jsonResponse([])
    })

    const wrapper = track(mount(StandardsPage))
    await flushPromises()
    await selectJob(wrapper, '网络')
    await wrapper.find('.standard-title-link').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.standards-tree .tree-group')).toHaveLength(1)
    expect(wrapper.text()).toContain('Network Standard')
    expect(wrapper.text()).not.toContain('Security Standard')

    await selectJob(wrapper, '数据库')
    expect(wrapper.find('.standards-detail-layout').exists()).toBe(false)
    expect(wrapper.findAll('.standard-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('Database Standard')
    expect(wrapper.text()).not.toContain('Network Standard')
  })

  test('TC-PORTAL-004 (TC-04) infra论坛岗位筛选重拉第一页且后续滚动沿用岗位标签', async () => {
    const secondNetworkPost = { id: 4, title: 'Network Post Page 2', summary: 'network page 2', tags: ['网络'], authorDisplayName: 'D' }
    vi.mocked(fetch).mockImplementation((input) => {
      const url = new URL(String(input), 'http://localhost')
      if (url.pathname === '/api/forum/posts' && url.searchParams.get('job') === '网络') {
        const content = url.searchParams.get('page') === '1' ? [secondNetworkPost] : [posts[0]]
        return jsonResponse({ content, page: Number(url.searchParams.get('page')), last: url.searchParams.get('page') === '1' })
      }
      if (url.pathname === '/api/forum/posts') return jsonResponse({ content: [posts[1]], page: 0, last: false })
      if (url.pathname === '/api/forum/tags') return jsonResponse([])
      return jsonResponse([])
    })

    const wrapper = track(mount(ForumPostList, { props: { auth: {} } }))
    await flushPromises()
    await selectJob(wrapper, '网络')

    expect(wrapper.text()).toContain('Network Post')
    expect(wrapper.text()).not.toContain('Security Post')
    const scrollContainer = wrapper.find('.forum-main').element
    Object.defineProperties(scrollContainer, {
      scrollTop: { value: 100, configurable: true },
      clientHeight: { value: 100, configurable: true },
      scrollHeight: { value: 150, configurable: true }
    })
    await wrapper.find('.forum-main').trigger('scroll')
    await flushPromises()

    const forumRequests = vi.mocked(fetch).mock.calls
      .map(([input]) => new URL(String(input), 'http://localhost'))
      .filter((url) => url.pathname === '/api/forum/posts' && url.searchParams.get('job') === '网络')
    expect(forumRequests.map((url) => url.searchParams.get('page'))).toEqual(['0', '1'])
    expect(wrapper.findAll('.forum-card')).toHaveLength(2)
    expect(wrapper.text()).toContain('Network Post Page 2')
  })

  test('TC-PORTAL-004 (TC-04) 岗位筛选处理无数据、单条数据、清除与刷新保留', async () => {
    const wrapper = track(mount(DownloadsPage))
    await flushPromises()

    await selectJob(wrapper, '主机')
    expect(wrapper.find('.empty-state').text()).toContain('当前类别暂无可下载软件')

    await selectJob(wrapper, '数据库')
    expect(wrapper.findAll('.release-card')).toHaveLength(1)
    expect(wrapper.text()).toContain('Database Toolkit')

    await selectJob(wrapper, '全部')
    expect(wrapper.findAll('.release-card')).toHaveLength(3)

    await selectJob(wrapper, '网络安全')
    expect(window.localStorage.getItem('mrm.publicJobFilter')).toBe('network-security')
    wrapper.unmount()
    mountedWrappers.pop()

    const refreshed = track(mount(DownloadsPage))
    await flushPromises()
    expect(refreshed.find('.job-navigation-button.active').text()).toContain('网络安全')
    expect(refreshed.findAll('.release-card')).toHaveLength(1)
    expect(refreshed.text()).toContain('Security Toolkit')
  })

  test('TC-PORTAL-005 (TC-05) 常用命令由中间件模块入口加载并保留旧路由', async () => {
    const middleware = jobModules.find(({ id }) => id === 'middleware')
    const context = {
      auth: {}, isSysAdmin: false, managedCategory: '', softwareTypes: [],
      notify: vi.fn(), confirm: vi.fn()
    }
    const writeText = vi.fn().mockResolvedValue()
    Object.defineProperty(window.navigator, 'clipboard', { value: { writeText }, configurable: true })

    expect(parseHashRoute('/commands')).toEqual({ name: 'jobModule', jobId: 'middleware', feature: 'commands', legacy: true })
    const wrapper = track(mount(middleware.entryComponent, { props: { job: middleware, feature: 'commands', context } }))
    await flushPromises()

    expect(wrapper.text()).toContain('查看 Redis 信息')
    expect(wrapper.text()).toContain('redis-cli info')
    await wrapper.find('.command-card button').trigger('click')
    expect(writeText).toHaveBeenCalledWith('redis-cli info')
  })

  test('TC-PORTAL-005 (TC-05) 常用命令复制失败时应向用户反馈', async () => {
    const middleware = jobModules.find(({ id }) => id === 'middleware')
    const context = {
      auth: {}, isSysAdmin: false, managedCategory: '', softwareTypes: [],
      notify: vi.fn(), confirm: vi.fn()
    }
    const writeText = vi.fn().mockRejectedValue(new Error('clipboard unavailable'))
    Object.defineProperty(window.navigator, 'clipboard', { value: { writeText }, configurable: true })

    const wrapper = track(mount(middleware.entryComponent, { props: { job: middleware, feature: 'commands', context } }))
    await flushPromises()
    await wrapper.find('.command-card button').trigger('click')
    await flushPromises()

    expect(context.notify).toHaveBeenCalledWith('复制失败', 'error')
  })

  test('TC-PORTAL-006 (TC-06) 岗位模块独立注册入口、功能路由与接口配置', async () => {
    expect(new Set(jobModules.map(({ modulePath }) => modulePath)).size).toBe(5)
    expect(new Set(jobModules.map(({ apiConfigKey }) => apiConfigKey)).size).toBe(5)
    expect(jobModules.every(({ entryComponent, resolveFeature, createModule }) => entryComponent && resolveFeature && createModule)).toBe(true)

    const middleware = jobModules.find(({ id }) => id === 'middleware').createModule({ apiBaseUrl: 'https://middleware.example/api' })
    const database = jobModules.find(({ id }) => id === 'database').createModule({ apiBaseUrl: 'https://database.example/api' })
    await middleware.request('/health')
    await database.request('/health')

    expect(fetch).toHaveBeenCalledWith('https://middleware.example/api/health', expect.any(Object))
    expect(fetch).toHaveBeenCalledWith('https://database.example/api/health', expect.any(Object))
    expect(middleware.apiBaseUrl).not.toBe(database.apiBaseUrl)

    const appSource = await readSource('../src/App.vue')
    expect(appSource).toContain('<component')
    expect(appSource).not.toMatch(/import CommandsPage|import DataMigrationPage/)
    expect(appSource).not.toMatch(/<CommandsPage|<DataMigrationPage/)
  })

  test('TC-PORTAL-007 (TC-07) 通用筛选、导航与空状态组件保持复用一致', async () => {
    expect(matchesJob('网络', 'network')).toBe(true)
    expect(matchesJob('网络安全', 'network')).toBe(false)
    expect(matchesJob(['网络安全', '安全'], 'network-security')).toBe(true)
    expect(filterItemsByJob(standards, 'network', (item) => item.category)).toEqual([standards[0]])

    const navigation = track(mount(JobNavigation, { props: { modelValue: 'all' } }))
    await navigation.findAll('.job-navigation-button')[4].trigger('click')
    expect(navigation.emitted('update:modelValue')).toBeTruthy()

    const emptyState = track(mount(EmptyState, { props: { message: '暂无数据' } }))
    expect(emptyState.text()).toContain('暂无数据')
  })

  test('TC-PORTAL-008 (TC-08) agent.md 明确模块独立、代码复用和 UI 一致要求', async () => {
    const agent = await readSource('../../agent.md')
    expect(agent).toMatch(/岗位模块编码独立/)
    expect(agent).toMatch(/通用能力代码复用/)
    expect(agent).toMatch(/UI 风格保持一致/)
    expect(normalizeJobId('unknown')).toBe('all')
  })
})
