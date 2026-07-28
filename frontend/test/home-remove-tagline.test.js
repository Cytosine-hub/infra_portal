// @vitest-environment jsdom
// 需求 #23（Issue #2）主页优化（拆解）— 去掉首页“各类别独立演进，共享统一交互与基础能力。”一行文案
// 验收用例 TC-01 ~ TC-06 自动化测试

import { readFile } from 'node:fs/promises'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import HomePage from '../src/pages/HomePage.vue'
import { jobModules } from '../src/modules/index.js'

const TAGLINE = '各类别独立演进，共享统一交互与基础能力。'
const TAGLINE_KEYWORD = '各类别独立演进'

const readSource = (path) => readFile(new URL(path, import.meta.url), 'utf8')
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
      return jsonResponse({ content: releases, totalElements: releases.length, totalPages: 1, first: true, last: true })
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

describe('主页优化（拆解）验收用例（需求 #23 · Issue #2）', () => {
  test('TC-01 首页不再展示指定文案', async () => {
    const wrapper = track(mount(HomePage))
    await flushPromises()
    expect(wrapper.text()).not.toContain(TAGLINE)
    expect(wrapper.text()).not.toContain(TAGLINE_KEYWORD)
  })

  test('TC-02 移除文案后首页其他内容正常展示', async () => {
    const wrapper = track(mount(HomePage))
    await flushPromises()

    // 标题与主标语
    expect(wrapper.text()).toContain('资源下载、标准发布、数据迁移与技术交流')
    // 公共分类入口卡片
    expect(wrapper.findAll('.portal-public-grid .portal-card').length).toBeGreaterThan(0)
    // 岗位分类入口卡片
    expect(wrapper.findAll('.portal-job-card')).toHaveLength(jobModules.length)
    // 最新软件发布模块
    expect(wrapper.text()).toContain('最新软件发布')
    expect(wrapper.text()).toContain('nginx')
  })

  test('TC-03 移除文案后页面布局无异常空白', async () => {
    const wrapper = track(mount(HomePage))
    await flushPromises()

    // 分隔线仅作为逻辑分隔存在，且不再保留承载原文案的段落占位
    const divider = wrapper.find('.portal-section-divider')
    expect(divider.exists()).toBe(true)
    expect(divider.findAll('p')).toHaveLength(0)
    expect(divider.text().trim()).toBe('')
    // 分隔线之后紧接岗位入口区，模块衔接自然
    expect(wrapper.find('.portal-jobs-grid').exists()).toBe(true)
  })

  test('TC-04 不同屏幕尺寸下均不展示指定文案', async () => {
    // 文案在模板中被彻底移除，不依赖任何视口/媒体查询条件渲染
    const source = await readSource('../src/pages/HomePage.vue')
    expect(source).not.toContain(TAGLINE_KEYWORD)

    const wrapper = track(mount(HomePage))
    await flushPromises()
    expect(wrapper.text()).not.toContain(TAGLINE_KEYWORD)
  })

  test('TC-05 刷新页面后指定文案仍不出现', async () => {
    const wrapper = track(mount(HomePage))
    await flushPromises()
    expect(wrapper.text()).not.toContain(TAGLINE_KEYWORD)

    // 模拟刷新：卸载后重新挂载
    wrapper.unmount()
    mountedWrappers.pop()
    const refreshed = track(mount(HomePage))
    await flushPromises()
    expect(refreshed.text()).not.toContain(TAGLINE_KEYWORD)
    // 刷新后其他内容仍正常
    expect(refreshed.text()).toContain('资源下载、标准发布、数据迁移与技术交流')
  })

  test('TC-06 页面源码和可访问文本中不包含指定文案（未通过隐藏样式保留）', async () => {
    const source = await readSource('../src/pages/HomePage.vue')
    expect(source).not.toContain(TAGLINE)
    expect(source).not.toContain(TAGLINE_KEYWORD)

    const wrapper = track(mount(HomePage))
    await flushPromises()
    // 渲染后的 DOM（含隐藏元素）中同样不包含该文案
    expect(wrapper.html()).not.toContain(TAGLINE_KEYWORD)
  })
})
