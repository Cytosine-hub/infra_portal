// @vitest-environment jsdom
// 需求 #26（Issue #3）页面图标修改 —— 首页八个模块图标重新设计 + 顶部“数据迁移”topbar 移除
// 验收用例 TC-01 ~ TC-07 自动化测试

import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import App from '../src/App.vue'
import HomePage from '../src/pages/HomePage.vue'
// 以 Vite raw import 获取源码文本做静态检查，避免在 jsdom/Vite 环境下加载 node: 内置模块导致测试无法运行
import appSource from '../src/App.vue?raw'
import homePageSource from '../src/pages/HomePage.vue?raw'
import moduleIconSource from '../src/components/ui/ModuleIcon.vue?raw'
import { useAuth } from '../src/composables/useAuth.js'
import { publicFeatures } from '../src/config/portalFeatures.js'
import { jobModules } from '../src/modules/index.js'

// 首页八个模块（按页面渲染顺序）：3 个公共入口 + 5 个岗位空间
const EXPECTED_MODULES = [
  { key: 'downloads', label: '软件下载' },
  { key: 'standards', label: '标准发布' },
  { key: 'forum', label: 'infra论坛' },
  { key: 'middleware', label: '中间件' },
  { key: 'database', label: '数据库' },
  { key: 'host', label: '主机' },
  { key: 'network', label: '网络' },
  { key: 'network-security', label: '网络安全' }
]

// 变更前使用的旧图标：单个汉字占位
const LEGACY_TEXT_ICONS = ['软', '标', '论', '中', '数', '网', '主']
const MIGRATION_ENTRY = '数据迁移'

const mountedWrappers = []
const storage = new Map()
const localStorageMock = {
  clear: () => storage.clear(),
  getItem: (key) => (storage.has(key) ? storage.get(key) : null),
  removeItem: (key) => storage.delete(key),
  setItem: (key, value) => storage.set(key, String(value))
}

const releases = [
  { downloadToken: 'mw-1', middlewareName: 'nginx', version: '1.26.3', platform: 'Linux' },
  { downloadToken: 'db-1', middlewareName: 'MySQL', version: '8.4.0', platform: 'Linux' }
]

const loginResponse = {
  token: 'test-token',
  username: 'admin',
  displayName: '管理员',
  role: '系统管理员',
  expiresAt: '2999-01-01T00:00:00Z'
}

function jsonResponse(data) {
  return Promise.resolve(new Response(JSON.stringify(data), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  }))
}

function installApiMock() {
  const fetchMock = vi.fn((input) => {
    const url = new URL(String(input), 'http://localhost')
    if (url.pathname === '/api/auth/login') return jsonResponse(loginResponse)
    if (url.pathname === '/api/public/releases') {
      return jsonResponse({ content: releases, totalElements: releases.length, totalPages: 1, first: true, last: true })
    }
    // 分页接口返回分页结构，其余列表接口返回数组，避免登录后加载数据时出现类型异常
    if (url.pathname.endsWith('/releases') || url.pathname.endsWith('/reviews')) {
      return jsonResponse({ content: [], totalElements: 0, totalPages: 0, first: true, last: true })
    }
    if (url.pathname === '/api/admin/settings') return jsonResponse({})
    return jsonResponse([])
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function track(wrapper) {
  mountedWrappers.push(wrapper)
  return wrapper
}

function iconTiles(wrapper) {
  return wrapper.findAll('.portal-card .portal-icon')
}

function headerText(wrapper) {
  const header = wrapper.find('header.topbar')
  expect(header.exists(), '页面应包含顶部 topbar').toBe(true)
  return header.text()
}

function extractHeaderBlock(source) {
  const match = source.match(/<header[\s\S]*?<\/header>/)
  expect(match, 'App.vue 应包含 <header> 区块').toBeTruthy()
  return match[0]
}

async function loginThrough(wrapper) {
  await wrapper.find('.login-form input[autocomplete="username"]').setValue('admin')
  await wrapper.find('.login-form input[type="password"]').setValue('admin-password')
  await wrapper.find('.login-form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  Object.defineProperty(window, 'localStorage', { value: localStorageMock, configurable: true })
  vi.stubGlobal('localStorage', localStorageMock)
  window.localStorage.clear()
  // useAuth 为模块级单例，用例间需显式重置登录态
  useAuth().logout(false)
  window.location.hash = '#/home'
  installApiMock()
})

afterEach(() => {
  while (mountedWrappers.length) mountedWrappers.pop().unmount()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('页面图标修改验收用例（需求 #26 · Issue #3）', () => {
  test('TC-01 首页八个模块图标已全部更新', async () => {
    const wrapper = track(mount(HomePage))
    await flushPromises()

    const tiles = iconTiles(wrapper)
    expect(tiles).toHaveLength(EXPECTED_MODULES.length)

    tiles.forEach((tile) => {
      // 每个模块渲染且仅渲染一个新图标，图标有实际图形内容（无破图）
      const svgs = tile.findAll('svg[data-module-icon]')
      expect(svgs).toHaveLength(1)
      expect(svgs[0].element.children.length).toBeGreaterThan(0)
      // 旧的汉字占位图标已彻底移除
      expect(tile.text().trim()).toBe('')
      LEGACY_TEXT_ICONS.forEach((legacy) => expect(tile.text()).not.toContain(legacy))
    })

    const renderedKeys = tiles.map((tile) => tile.find('svg[data-module-icon]').attributes('data-module-icon'))
    expect(renderedKeys).toEqual(EXPECTED_MODULES.map((item) => item.key))
    // 首页模板不再直接渲染配置中的文字图标
    expect(homePageSource).not.toContain('{{ feature.icon }}')
    expect(homePageSource).not.toContain('job.shortName.slice(0, 1)')
  })

  test('TC-02 首页八个模块图标语义贴合模块', async () => {
    const wrapper = track(mount(HomePage))
    await flushPromises()

    const tiles = iconTiles(wrapper)
    EXPECTED_MODULES.forEach((module, index) => {
      const svg = tiles[index].find('svg[data-module-icon]')
      // 图标与所属模块一一绑定，并带可读的无障碍标签
      expect(svg.attributes('data-module-icon')).toBe(module.key)
      expect(svg.attributes('role')).toBe('img')
      expect(svg.attributes('aria-label')).toBe(module.label)
    })

    // 八个图标互不相同：标识与图形均唯一
    const keys = tiles.map((tile) => tile.find('svg').attributes('data-module-icon'))
    expect(new Set(keys).size).toBe(EXPECTED_MODULES.length)
    const shapes = tiles.map((tile) => tile.find('svg').element.innerHTML.replace(/\s+/g, ''))
    expect(new Set(shapes).size).toBe(EXPECTED_MODULES.length)
  })

  test('TC-03 首页模块图标在不同窗口尺寸下正常展示', async () => {
    const wrapper = track(mount(HomePage))
    await flushPromises()

    iconTiles(wrapper).forEach((tile) => {
      const svg = tile.find('svg[data-module-icon]')
      // 使用 viewBox 等比缩放，且不写死像素宽高，避免拉伸变形
      expect(svg.attributes('viewBox')).toBeTruthy()
      expect(svg.attributes('width')).toBeUndefined()
      expect(svg.attributes('height')).toBeUndefined()
      expect(svg.attributes('preserveAspectRatio')).not.toBe('none')
    })

    // 图标自身按容器等比铺满，容器保持正方形且不被压缩
    expect(moduleIconSource).toMatch(/width:\s*100%/)
    expect(moduleIconSource).toMatch(/height:\s*100%/)
    expect(homePageSource).toMatch(/aspect-ratio:\s*1/)
    expect(homePageSource).toMatch(/flex:\s*0 0 auto/)
  })

  test('TC-04 登录后顶部数据迁移topbar已移除', async () => {
    // 未登录访问受控路由时展示登录页
    window.location.hash = '#/admin'
    const wrapper = track(mount(App))
    await flushPromises()

    await loginThrough(wrapper)
    expect(useAuth().auth.token).toBe(loginResponse.token)

    const tabs = wrapper.findAll('.nav-tabs button').map((button) => button.text().trim())
    expect(tabs).not.toContain(MIGRATION_ENTRY)
    expect(headerText(wrapper)).not.toContain(MIGRATION_ENTRY)
    // 其他顶部导航项正常展示且可点击跳转
    expect(tabs).toEqual(expect.arrayContaining(['首页', '标准发布', '下载中心', '论坛', '管理后台']))
    const standardsTab = wrapper.findAll('.nav-tabs button').find((button) => button.text().trim() === '标准发布')
    await standardsTab.trigger('click')
    await flushPromises()
    expect(window.location.hash).toContain('standards')
  })

  test('TC-05 未登录和刷新场景下数据迁移入口不异常出现', async () => {
    window.location.hash = '#/admin'
    const guest = track(mount(App))
    await flushPromises()
    expect(headerText(guest)).not.toContain(MIGRATION_ENTRY)

    await loginThrough(guest)
    expect(headerText(guest)).not.toContain(MIGRATION_ENTRY)

    // 模拟刷新：卸载后基于已持久化的登录态重新挂载
    guest.unmount()
    mountedWrappers.pop()
    const refreshed = track(mount(App))
    await flushPromises()
    expect(useAuth().auth.token).toBe(loginResponse.token)
    expect(headerText(refreshed)).not.toContain(MIGRATION_ENTRY)

    // 顶部区域在模板中已无该入口，不依赖任何条件渲染
    expect(extractHeaderBlock(appSource)).not.toContain(MIGRATION_ENTRY)
  })

  test('TC-06 图标资源加载无异常', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const fetchMock = installApiMock()

    const wrapper = track(mount(HomePage))
    await flushPromises()

    // 图标为内联 SVG，不产生任何额外资源请求，也就不存在 404/500/跨域问题
    expect(wrapper.findAll('.portal-icon img')).toHaveLength(0)
    expect(moduleIconSource).not.toContain('<img')
    expect(moduleIconSource).not.toContain('url(')
    expect(moduleIconSource).not.toContain('http')
    const requested = fetchMock.mock.calls.map(([input]) => String(input))
    expect(requested.every((url) => url.startsWith('/api/'))).toBe(true)
    expect(errorSpy).not.toHaveBeenCalled()
    expect(warnSpy).not.toHaveBeenCalled()
  })

  test('TC-07 其他模块功能未受影响', async () => {
    const wrapper = track(mount(HomePage))
    await flushPromises()

    // 首页其余内容保持不变
    expect(wrapper.findAll('.portal-public-grid .portal-card')).toHaveLength(publicFeatures.length)
    expect(wrapper.findAll('.portal-job-card')).toHaveLength(jobModules.length)
    expect(wrapper.text()).toContain('最新软件发布')
    expect(wrapper.text()).toContain('nginx')
    EXPECTED_MODULES.forEach((module) => expect(wrapper.text()).toContain(module.label))

    // 入口跳转保持不变
    await wrapper.findAll('.portal-public-grid .portal-card')[0].trigger('click')
    await wrapper.findAll('.portal-job-card')[0].trigger('click')
    expect(wrapper.emitted('navigate')[0]).toEqual([publicFeatures[0].id])
    expect(wrapper.emitted('navigate')[1]).toEqual([`jobs/${jobModules[0].id}`])

    // 数据库空间的“数据迁移”功能本身未被删除，仅移除了顶部入口
    const databaseModule = jobModules.find((job) => job.id === 'database')
    expect(databaseModule.features.some((feature) => feature.name === MIGRATION_ENTRY)).toBe(true)
  })
})
