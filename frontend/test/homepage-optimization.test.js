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

    // 检查首页基本结构存在
    expect(wrapper.find('.portal-page').exists()).toBe(true)
    expect(wrapper.find('.portal-hero').exists()).toBe(true)

    // 确保有公共功能卡片和岗位卡片
    const publicCards = wrapper.findAll('.portal-public-grid .portal-card')
    const jobCards = wrapper.findAll('.portal-jobs-grid .portal-card')
    expect(publicCards.length).toBeGreaterThan(0)
    expect(jobCards.length).toBeGreaterThan(0)

    // 关键验证：确保"各类别独立演进，共享统一交互与基础能力。"这行字已删除
    expect(wrapper.text()).not.toContain('各类别独立演进，共享统一交互与基础能力。')
  })

  test('TC-02 主页优化 - 内容确认', async () => {
    const wrapper = track(mount(HomePage))
    await flushPromises()

    // 页面仍然正常渲染
    expect(wrapper.find('.portal-page').exists()).toBe(true)

    // 分隔符区域应该清空（不包含已删除的文本）
    expect(wrapper.text()).not.toContain('各类别独立演进，共享统一交互与基础能力。')
  })

  test('TC-03 主页优化 - 无权限用户访问', async () => {
    const wrapper = track(mount(HomePage))
    await flushPromises()

    // 首页不需要权限校验，所有用户都可以看到
    expect(wrapper.find('.portal-page').exists()).toBe(true)

    // 页面完整加载，关键内容存在
    expect(wrapper.find('.portal-hero').exists()).toBe(true)
    expect(wrapper.findAll('.portal-card').length).toBeGreaterThan(0)
  })
})
