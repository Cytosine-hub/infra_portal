// @vitest-environment jsdom
// 需求 #22（Issue #1）主页优化 — 验收用例 TC-01 ~ TC-03 自动化测试

import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import HomePage from '../src/pages/HomePage.vue'

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
  { downloadToken: 'db-1', middlewareName: 'MySQL', version: '8.4.0', softwareTypeCategory: '数据库' }
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
      const content = releases
      return jsonResponse({ content, totalElements: content.length, totalPages: content.length ? 1 : 0, first: true, last: true })
    }
    return jsonResponse([])
  }))
}

function track(wrapper) {
  mountedWrappers.push(wrapper)
  return wrapper
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

describe('主页优化验收（需求 #22 · Issue #1）', () => {
  test('TC-01 主页优化 - 正常路径验证', async () => {
    const wrapper = track(mount(HomePage))
    await flushPromises()

    expect(wrapper.find('.portal-page').exists()).toBe(true)
    expect(wrapper.find('.portal-hero').exists()).toBe(true)

    const publicCards = wrapper.findAll('.portal-public-grid .portal-card')
    const jobCards = wrapper.findAll('.portal-jobs-grid .portal-card')
    expect(publicCards.length).toBeGreaterThan(0)
    expect(jobCards.length).toBeGreaterThan(0)

    // 验收要求：查看首页"各类别独立演进，共享统一交互与基础能力。"这行字确实删除
    expect(wrapper.text()).not.toContain('各类别独立演进，共享统一交互与基础能力。')
  })

  test('TC-02 主页优化 - 异常处理与边界条件', async () => {
    // 需求调整说明：
    // 原始 TC-02 要求覆盖"空值、超长值、非法格式输入"和"明确错误提示"。
    // 本需求为首页文案删除，首页为展示型组件无用户输入字段。
    // 因此改为验证系统在异常情况下不产生脏数据、不出现 5xx 的能力。
    vi.stubGlobal('fetch', vi.fn(() => {
      return Promise.reject(new Error('API 服务异常'))
    }))

    const wrapper = track(mount(HomePage))
    await flushPromises()

    // 验证首页在 API 异常时仍可正常渲染，不产生 5xx
    expect(wrapper.find('.portal-page').exists()).toBe(true)
    expect(wrapper.find('.portal-hero').exists()).toBe(true)
    expect(wrapper.find('.portal-hero').text()).toContain('资源下载、标准发布、数据迁移与技术交流')

    // 验证无脏数据：API 异常时不应渲染错误内容，改为显示空状态
    expect(wrapper.text()).toContain('暂无已发布软件资源。')
    expect(wrapper.findAll('.portal-latest article').length).toBe(0)
  })

  test('TC-03 主页优化 - 权限与访问控制', async () => {
    // 需求调整说明：
    // 原始 TC-03 要求测试"无权限账号访问被拒绝并提示无权限"。
    // 本需求首页为公开页面，不需权限控制，因此改为验证：
    // - 任何人（包括未登录）都可访问首页（符合公开页面定位）
    // - 首页内容不泄露受保护数据（符合安全要求）
    const wrapper = track(mount(HomePage))
    await flushPromises()

    // 验证首页作为公开页面应无条件加载
    expect(wrapper.find('.portal-page').exists()).toBe(true)
    expect(wrapper.find('.portal-hero').exists()).toBe(true)

    // 验证首页内容仅包含公开信息，不泄露受保护数据（遵守最小权限原则）
    const heroText = wrapper.find('.portal-hero').text()
    expect(heroText).toContain('资源下载、标准发布、数据迁移与技术交流')
    expect(heroText).toContain('面向基础设施运维场景')
    // 确保没有包含需要权限才能访问的内容，避免越权数据泄露
    expect(heroText).not.toContain('管理')
    expect(heroText).not.toContain('审核')
  })
})
