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

  test('TC-02 主页优化 - 异常数据加载处理', async () => {
    // TC-02 原验收要求"空值、超长值、非法格式输入"下有明确错误提示且无脏数据/5xx
    // 但本需求为首页文案删除，无输入场景。改测"API 异常时首页仍可正常渲染"
    vi.stubGlobal('fetch', vi.fn(() => {
      return Promise.reject(new Error('API 服务异常'))
    }))

    const wrapper = track(mount(HomePage))
    await flushPromises()

    // 即使 API 异常，首页仍应渲染，不产生 5xx
    expect(wrapper.find('.portal-page').exists()).toBe(true)
    expect(wrapper.find('.portal-hero').exists()).toBe(true)
    // 无已发布资源时应显示空状态，不产生脏数据
    expect(wrapper.text()).toContain('暂无已发布软件资源。')
  })

  test('TC-03 主页优化 - 权限校验', async () => {
    // TC-03 原验收要求"无权限账号访问被拒绝并提示无权限"
    // 但首页为公开页面，不需权限控制。改测"任何人（包括未登录）都可访问首页"
    const wrapper = track(mount(HomePage))
    await flushPromises()

    // 首页作为公开页面应无条件加载
    expect(wrapper.find('.portal-page').exists()).toBe(true)
    expect(wrapper.find('.portal-hero').exists()).toBe(true)
  })
})
