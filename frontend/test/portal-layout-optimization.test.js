// @vitest-environment jsdom
// 需求 #17（Issue #10）页面优化2 — 验收用例 TC-01 ~ TC-07 自动化测试

import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import HomePage from '../src/pages/HomePage.vue'
import DownloadsPage from '../src/pages/DownloadsPage.vue'
import StandardsPage from '../src/pages/StandardsPage.vue'
import JobNavigation from '../src/shared/jobs/JobNavigation.vue'
import { jobModules, getJobModule } from '../src/modules/index.js'
// 以 Vite ?raw 导入源码文本做静态检查，避免在 jsdom/Vite 环境下加载 node: 内置模块导致测试无法运行
import jobNavigationSource from '../src/shared/jobs/JobNavigation.vue?raw'
import appSource from '../src/App.vue?raw'

const sourceMap = {
  '../src/shared/jobs/JobNavigation.vue': jobNavigationSource,
  '../src/App.vue': appSource
}
const readSource = (path) => sourceMap[path]
const mountedWrappers = []
const storage = new Map()
const localStorageMock = {
  clear: () => storage.clear(),
  getItem: (key) => (storage.has(key) ? storage.get(key) : null),
  removeItem: (key) => storage.delete(key),
  setItem: (key, value) => storage.set(key, String(value))
}

const releases = [
  { downloadToken: 'mw-1', middlewareName: 'nginx', version: '1.26.3', softwareTypeCategory: '中间件' },
  { downloadToken: 'db-1', middlewareName: 'MySQL', version: '8.4.0', softwareTypeCategory: '数据库' },
  { downloadToken: 'net-1', middlewareName: '连通性检测包', version: '2.0', softwareTypeCategory: '网络' }
]

const standards = [
  { id: 1, category: '数据库', software: 'MySQL', softwareVersion: '8.4.0', version: '1.0', relatedDocuments: [] }
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
    if (url.pathname === '/api/public/parameter-standards') {
      return jsonResponse(standards)
    }
    return jsonResponse([])
  }))
}

function track(wrapper) {
  mountedWrappers.push(wrapper)
  return wrapper
}

function findJobButton(wrapper, label) {
  return wrapper.findAll('.job-navigation-button').find((button) => button.text().trim() === label)
}

async function selectJob(wrapper, label) {
  const button = findJobButton(wrapper, label)
  expect(button, `导航栏应包含按钮：${label}`).toBeTruthy()
  await button.trigger('click')
  await flushPromises()
}

function extractHeaderBlock(appSource) {
  const match = appSource.match(/<header[\s\S]*?<\/header>/)
  expect(match, 'App.vue 应包含 <header> 区块').toBeTruthy()
  return match[0]
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

describe('门户页面优化2验收用例（需求 #17 · Issue #10）', () => {
  test('TC-01 公共模块左侧导航栏按钮尺寸优化', async () => {
    const navSource = await readSource('../src/shared/jobs/JobNavigation.vue')
    // 按钮不再渲染二级小字子标签，容器自身使用收敛后的紧凑间距令牌
    expect(navSource).not.toContain('<small>')
    const relativeMinHeight = navSource.match(/\.job-navigation-button\s*\{[\s\S]*?min-height:\s*([\d.]+)(em|rem)/)
    expect(relativeMinHeight, '岗位导航按钮应使用相对单位定义最小高度').toBeTruthy()
    expect(Number(relativeMinHeight[1])).toBeGreaterThanOrEqual(2)
    expect(Number(relativeMinHeight[1])).toBeLessThanOrEqual(3)

    const wrapper = track(mount(DownloadsPage))
    await flushPromises()

    const buttons = wrapper.findAll('.job-navigation-button')
    expect(buttons).toHaveLength(6)
    // 校验真实渲染结果：BaseButton 必须显式使用紧凑尺寸 sm，
    // 否则其默认 md 尺寸的 `.btn.md` 内边距规则特异性高于父组件的 scoped 样式，会覆盖掉紧凑样式
    buttons.forEach((button) => {
      expect(button.classes()).toContain('sm')
      expect(button.classes()).not.toContain('md')
    })
    // 每个按钮仅一行文案，无二级说明文字
    buttons.forEach((button) => expect(button.findAll('small')).toHaveLength(0))

    // 依次点击类别按钮，选中态与内容切换均正常
    await selectJob(wrapper, '中间件')
    expect(wrapper.find('.job-navigation-button.active').text()).toBe('中间件')
    expect(wrapper.text()).toContain('nginx')
    expect(wrapper.text()).not.toContain('MySQL')

    await selectJob(wrapper, '数据库')
    expect(wrapper.find('.job-navigation-button.active').text()).toBe('数据库')
    expect(wrapper.text()).toContain('MySQL')
    expect(wrapper.text()).not.toContain('nginx')

    await selectJob(wrapper, '网络')
    expect(wrapper.find('.job-navigation-button.active').text()).toBe('网络')
    expect(wrapper.text()).toContain('连通性检测包')
  })

  test('TC-02 公共模块左侧导航栏类别名称不显示"岗位"', async () => {
    const wrapper = track(mount(DownloadsPage))
    await flushPromises()

    const labels = wrapper.findAll('.job-navigation-button').map((button) => button.text().trim())
    expect(labels).toEqual(['全部', '中间件', '数据库', '主机', '网络', '网络安全'])
    labels.forEach((label) => expect(label).not.toContain('岗位'))

    await selectJob(wrapper, '数据库')
    expect(wrapper.findAll('.job-navigation-button').map((b) => b.text().trim())).toEqual(labels)

    // 模拟刷新页面：卸载后重新挂载，文案应保持一致
    wrapper.unmount()
    mountedWrappers.pop()
    const refreshed = track(mount(DownloadsPage))
    await flushPromises()
    expect(refreshed.findAll('.job-navigation-button').map((b) => b.text().trim())).toEqual(labels)
  })

  test.each(jobModules)('TC-03 岗位独立空间（$shortName）内部不显示左侧导航栏', async (job) => {
    const wrapper = track(mount(job.entryComponent, { props: { job, feature: null, context: {} } }))
    await flushPromises()

    expect(wrapper.find('.job-navigation').exists()).toBe(false)
    expect(wrapper.findComponent(JobNavigation).exists()).toBe(false)
    expect(wrapper.find('.job-workspace-header').exists()).toBe(false)

    // 模拟刷新/直接访问 URL：重新挂载后表现一致，主体内容正常展示
    wrapper.unmount()
    mountedWrappers.pop()
    const reopened = track(mount(job.entryComponent, { props: { job, feature: null, context: {} } }))
    await flushPromises()
    expect(reopened.find('.job-navigation').exists()).toBe(false)
    expect(reopened.find('.job-workspace-header').exists()).toBe(false)
  })

  test('TC-04 主页去除"选择你的岗位空间"标题', async () => {
    const wrapper = track(mount(HomePage))
    await flushPromises()

    expect(wrapper.text()).not.toContain('选择你的岗位空间')
    expect(wrapper.text()).not.toContain('选择你的工作空间')

    const cards = wrapper.findAll('.portal-job-card')
    expect(cards).toHaveLength(5)
    for (const [index, job] of jobModules.entries()) {
      await cards[index].trigger('click')
      expect(wrapper.emitted('navigate').at(-1)).toEqual([`jobs/${job.id}`])
    }
  })

  test.each(jobModules)('TC-05 独立空间（$shortName）不重复显示岗位标题带', async (job) => {
    const wrapper = track(mount(job.entryComponent, { props: { job, feature: null, context: {} } }))
    await flushPromises()

    expect(wrapper.find('.job-workspace-header').exists()).toBe(false)
    expect(wrapper.find('.job-mark').exists()).toBe(false)
    expect(wrapper.find('.job-feature-grid').exists()).toBe(true)
  })

  test('TC-06 主页与公共模块不显示区分公共区域和岗位区域的分区标题', async () => {
    const home = track(mount(HomePage))
    await flushPromises()
    expect(home.text()).not.toContain('公共区域')
    expect(home.text()).not.toContain('岗位区域')
    expect(home.text()).not.toContain('选择你的岗位空间')
    // 需求 #23：原承载文案的分隔节点已移除，仅靠栅格间距自然划分区域，既无分区标题也无占位分隔节点
    expect(home.find('.portal-section-divider').exists()).toBe(false)
    expect(home.find('.portal-public-grid').element.nextElementSibling).toBe(home.find('.portal-jobs-grid').element)

    const downloads = track(mount(DownloadsPage))
    await flushPromises()
    expect(downloads.text()).not.toContain('公共区域')
    expect(downloads.text()).not.toContain('岗位区域')
  })

  test('TC-07 主页、公共模块与独立空间标题区域不显示英文小字', async () => {
    const appSource = await readSource('../src/App.vue')
    const headerBlock = extractHeaderBlock(appSource)
    expect(headerBlock).not.toContain('Operations Hub')
    expect(headerBlock).not.toContain('eyebrow')

    const home = track(mount(HomePage))
    await flushPromises()
    expect(home.text()).not.toContain('Professional Workspaces')
    expect(home.text()).not.toContain('Latest')

    const downloads = track(mount(DownloadsPage))
    await flushPromises()
    expect(downloads.find('.job-navigation').text()).not.toMatch(/[A-Za-z]/)

    const standards = track(mount(StandardsPage))
    await flushPromises()
    expect(standards.find('.eyebrow').exists()).toBe(false)
    expect(standards.text()).not.toContain('Category')

    for (const job of jobModules) {
      const workspace = track(mount(job.entryComponent, { props: { job, feature: null, context: {} } }))
      await flushPromises()
      expect(workspace.find('.job-workspace-header').exists()).toBe(false)
      expect(workspace.text()).not.toContain(job.englishName)
    }
  })

  test('TC-07 数据库空间"数据迁移"功能页标题区域不显示英文小字', async () => {
    const database = getJobModule('database')
    const wrapper = track(mount(database.entryComponent, { props: { job: database, feature: 'data-migration', context: {} } }))
    await flushPromises()

    expect(wrapper.find('.eyebrow').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Data Migration')
    expect(wrapper.text()).not.toContain('Pattern')
    expect(wrapper.text()).not.toContain('Roadmap')
  })
})
