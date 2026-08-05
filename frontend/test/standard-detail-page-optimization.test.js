// @vitest-environment jsdom
// 需求 #31（Issue #12）标准详情页面优化 — 验收用例 TC-01 ~ TC-06 自动化测试

import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import StandardsPage from '../src/pages/StandardsPage.vue'
import { useNotify } from '../src/composables/useNotify'
// 以 Vite ?raw 导入源码文本做静态检查：jsdom 无法计算真实布局，收起宽度与表格滚动能力只能校验样式约束
import standardsPageSource from '../src/pages/StandardsPage.vue?raw'
import categorySidebarSource from '../src/components/standards/StandardsCategorySidebar.vue?raw'

// Vitest 默认不处理 CSS，?raw/?inline 都拿不到样式表原文，这里直接读文件校验滚动视口的样式约束
const documentTablesCssPath = ['src', 'frontend/src']
  .map((prefix) => resolve(process.cwd(), prefix, 'components/previews/documentTables.css'))
  .find((candidate) => existsSync(candidate))
const documentTablesCss = readFileSync(documentTablesCssPath, 'utf8')

const TABLE_COLUMNS = [
  '指标分类', '指标名称', '采集周期', '告警级别', '小鱼告警', '待处理状态', '监控项', 'last函数',
  '触发表达式', 'TP编码', '数据类型', '相关性', '1w阈值', '365d保留', '备注说明'
]
const TABLE_ROW = [
  '性能', 'ThreadPoolRuntime.ExecuteThreadTotalCount', '2m', '警告', '小鱼推送', '当前值异常', '线程池总数',
  'last(/wls/thread,total)', '当前值 > 120', 'TP_WLS_001', '数字', '相关', '1w', '365d', '线程总数持续升高需关注'
]

const TABLE_MARKDOWN = [
  '# Weblogic监控模板v1',
  '',
  `| ${TABLE_COLUMNS.join(' | ')} |`,
  `| ${TABLE_COLUMNS.map(() => '---').join(' | ')} |`,
  `| ${TABLE_ROW.join(' | ')} |`,
  ''
].join('\n')

const TABLE_HTML = `<h1>Weblogic部署标准-202502版</h1><table><thead><tr>${
  TABLE_COLUMNS.map((col) => `<th>${col}</th>`).join('')
}</tr></thead><tbody><tr>${
  TABLE_ROW.map((cell) => `<td>${cell}</td>`).join('')
}</tr></tbody></table>`

const weblogicDocs = [
  { id: 101, title: 'Weblogic监控模板v1', version: '1.0', status: 'PUBLISHED', publishedAt: '2026-07-28T09:00:00', relatedStandardDocumentId: 1, content: TABLE_MARKDOWN },
  { id: 102, title: 'Weblogic部署标准-202502版', version: '2.0', status: 'PUBLISHED', publishedAt: '2026-06-18T09:00:00', relatedStandardDocumentId: 1, storedFileName: 'weblogic-202502.doc' }
]

const standards = [
  {
    id: 1,
    title: 'WebLogic 部署标准',
    category: '中间件',
    software: 'WebLogic',
    softwareVersion: 'V2025.3.0',
    version: '1.0',
    publishedAt: '2026-08-01T10:00:00',
    updatedAt: '2026-08-01T10:00:00',
    relatedDocuments: weblogicDocs
  },
  {
    id: 3,
    title: 'MySQL 备份恢复标准',
    category: '数据库',
    software: 'MySQL',
    softwareVersion: 'V2026.1.0',
    version: '3.1',
    publishedAt: '2026-06-03T10:00:00',
    updatedAt: '2026-06-03T10:00:00',
    relatedDocuments: []
  }
]

const mountedWrappers = []
const storage = new Map()
const localStorageMock = {
  clear: () => storage.clear(),
  getItem: (key) => (storage.has(key) ? storage.get(key) : null),
  removeItem: (key) => storage.delete(key),
  setItem: (key, value) => storage.set(key, String(value))
}

function jsonResponse(data, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' }
  }))
}

function installApiMock(list = standards) {
  vi.stubGlobal('fetch', vi.fn((input) => {
    const url = new URL(String(input), 'http://localhost')
    if (url.pathname === '/api/public/parameter-standards') return jsonResponse({ content: list })
    const detailMatch = url.pathname.match(/^\/api\/public\/parameter-standards\/(\d+)$/)
    if (detailMatch) {
      const found = list.find((item) => String(item.id) === detailMatch[1])
      return found ? jsonResponse(found) : jsonResponse({ code: 'NOT_FOUND', message: '标准不存在' }, 404)
    }
    if (url.pathname === '/api/public/standard-parameters') return jsonResponse([])
    if (url.pathname === '/api/public/standards/preview') return jsonResponse({ html: TABLE_HTML })
    const docMatch = url.pathname.match(/^\/api\/public\/standards\/(\d+)$/)
    if (docMatch) {
      const doc = weblogicDocs.find((item) => String(item.id) === docMatch[1])
      return doc ? jsonResponse(doc) : jsonResponse({ code: 'NOT_FOUND', message: '标准文档不存在' }, 404)
    }
    return jsonResponse([])
  }))
}

function track(wrapper) {
  mountedWrappers.push(wrapper)
  return wrapper
}

async function mountPage() {
  const wrapper = track(mount(StandardsPage))
  await flushPromises()
  await flushPromises()
  return wrapper
}

function cardOf(wrapper, title) {
  return wrapper.findAll('.standard-card').find((card) => card.text().includes(title))
}

async function openStandard(wrapper, title = 'WebLogic 部署标准') {
  await cardOf(wrapper, title).find('.standard-title-link').trigger('click')
  await flushPromises()
}

async function openTreeDoc(wrapper, title) {
  const item = wrapper.findAll('.tree-child').find((node) => node.text().includes(title))
  await item.trigger('click')
  await flushPromises()
  await flushPromises()
}

function toggleButton(wrapper) {
  return wrapper.find('.category-collapse-toggle')
}

beforeEach(() => {
  Object.defineProperty(window, 'localStorage', { value: localStorageMock, configurable: true })
  vi.stubGlobal('localStorage', localStorageMock)
  window.localStorage.clear()
  window.location.hash = '#/standards'
  installApiMock()
  useNotify().notice.value = null
})

afterEach(() => {
  while (mountedWrappers.length) mountedWrappers.pop().unmount()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  vi.useRealTimers()
})

describe('标准详情页面优化验收', () => {
  test('TC-STANDARD-DETAIL-001 (TC-01) 软件分类侧边栏可收起且主体区域随之扩展', async () => {
    const wrapper = await mountPage()
    await openStandard(wrapper)

    // 详情页初始状态：软件分类侧边栏展开
    expect(wrapper.find('.standards-detail-layout').exists()).toBe(true)
    expect(wrapper.find('.job-navigation').exists()).toBe(true)
    const toggle = toggleButton(wrapper)
    expect(toggle.exists()).toBe(true)
    expect(toggle.attributes('aria-expanded')).toBe('true')

    await toggle.trigger('click')

    // 收起后分类列表不再占用原有宽度，详情主体仍完整可见
    expect(wrapper.find('.job-navigation').exists()).toBe(false)
    expect(wrapper.find('.public-module-layout').classes()).toContain('category-collapsed')
    expect(toggleButton(wrapper).attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('.standards-detail-layout').exists()).toBe(true)
    expect(wrapper.find('.standard-detail-head h2').text()).toContain('WebLogic')
    expect(useNotify().notice.value).toBeNull()

    // 收起态的栅格把释放出来的宽度交给主体区域
    expect(standardsPageSource).toMatch(/\.public-module-layout\.category-collapsed\s*\{[^}]*grid-template-columns:[^;]*minmax\(0,\s*1fr\)/)
  })

  test('TC-STANDARD-DETAIL-002 (TC-02) 侧边栏收起后可再次展开且分类筛选功能正常', async () => {
    const wrapper = await mountPage()
    await openStandard(wrapper)
    await toggleButton(wrapper).trigger('click')
    expect(wrapper.find('.job-navigation').exists()).toBe(false)

    await toggleButton(wrapper).trigger('click')

    // 展开后分类内容完整回归，布局恢复
    expect(wrapper.find('.job-navigation').exists()).toBe(true)
    expect(wrapper.findAll('.job-navigation-button').length).toBeGreaterThan(1)
    expect(wrapper.find('.public-module-layout').classes()).not.toContain('category-collapsed')
    expect(toggleButton(wrapper).attributes('aria-expanded')).toBe('true')

    // 分类筛选仍可正常使用
    const databaseNav = wrapper.findAll('.job-navigation-button').find((button) => button.text().includes('数据库'))
    await databaseNav.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.standard-card')).toHaveLength(1)
    expect(wrapper.text()).toContain('MySQL 备份恢复标准')
    expect(useNotify().notice.value).toBeNull()
  })

  test('TC-STANDARD-DETAIL-003 (TC-03) 侧边栏收起状态下详情查看、切换、打开文档与返回均正常', async () => {
    const wrapper = await mountPage()
    await openStandard(wrapper)
    await toggleButton(wrapper).trigger('click')

    // 1. 查看基础信息
    const head = wrapper.find('.standard-detail-head')
    expect(head.text()).toContain('中间件')
    expect(head.text()).toContain('V2025.3.0')

    // 2. 切换到标准文档并打开表格类文档
    await openTreeDoc(wrapper, 'Weblogic监控模板v1')
    expect(wrapper.find('.markdown-document-preview').text()).toContain('Weblogic监控模板v1')
    expect(wrapper.find('.doc-table-viewport').exists()).toBe(true)
    expect(wrapper.find('.job-navigation').exists()).toBe(false)

    // 3. 返回标准详情
    const parentItem = wrapper.findAll('.tree-parent').find((node) => node.text().includes('WebLogic'))
    await parentItem.trigger('click')
    await flushPromises()
    expect(wrapper.find('.doc-card-list').exists()).toBe(true)

    // 4. 返回列表：收起状态保持，页面无异常
    await wrapper.find('.tree-header button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.standards-detail-layout').exists()).toBe(false)
    expect(wrapper.findAll('.standard-card')).toHaveLength(standards.length)
    expect(wrapper.find('.public-module-layout').classes()).toContain('category-collapsed')
    expect(useNotify().notice.value).toBeNull()
  })

  test('TC-STANDARD-DETAIL-004 (TC-04) 表格类文档列数较多时首列到末列内容均可完整查看', async () => {
    const wrapper = await mountPage()
    await openStandard(wrapper)
    await openTreeDoc(wrapper, 'Weblogic监控模板v1')

    const viewport = wrapper.find('.doc-table-viewport')
    expect(viewport.exists()).toBe(true)
    expect(viewport.find('table').exists()).toBe(true)

    // 首列、中间列、末列全部渲染，内容不丢失
    expect(viewport.findAll('th').map((th) => th.text())).toEqual(TABLE_COLUMNS)
    expect(viewport.findAll('tbody td').map((td) => td.text())).toEqual(TABLE_ROW)

    // 横向滚动 + 表格按内容撑开 + 表头与首列固定，保证不被容器截断
    expect(documentTablesCss).toMatch(/\.doc-table-viewport\)?\s*\{[^}]*overflow-x:\s*auto/)
    expect(documentTablesCss).toMatch(/width:\s*max-content/)
    expect(documentTablesCss).toMatch(/white-space:\s*nowrap/)
    expect(documentTablesCss).toMatch(/position:\s*sticky/)
  })

  test('TC-STANDARD-DETAIL-005 (TC-05) 窄屏窗口下表格类文档仍可横向滚动查看全部列', async () => {
    window.innerWidth = 480
    window.dispatchEvent(new Event('resize'))
    const wrapper = await mountPage()
    await openStandard(wrapper)

    // Word 类表格文档（后端 HTML 预览）同样进入横向滚动视口
    await openTreeDoc(wrapper, 'Weblogic部署标准-202502版')
    await flushPromises()

    const viewport = wrapper.find('.doc-table-viewport')
    expect(viewport.exists()).toBe(true)
    expect(viewport.findAll('th').map((th) => th.text())).toEqual(TABLE_COLUMNS)
    expect(viewport.findAll('tbody td').map((td) => td.text())).toEqual(TABLE_ROW)

    // 滚动视口宽度受父容器约束，窄屏下不会撑破布局
    expect(documentTablesCss).toMatch(/max-width:\s*100%/)
    expect(documentTablesCss).toMatch(/min-width:\s*100%/)
    expect(wrapper.find('.public-document-preview').exists()).toBe(true)
    expect(useNotify().notice.value).toBeNull()
  })

  test('TC-STANDARD-DETAIL-006 (TC-06) 浏览器地址栏路径可直接访问并刷新标准详情页面', async () => {
    // 详情与文档都拥有独立路径
    const wrapper = await mountPage()
    await openStandard(wrapper)
    expect(window.location.hash).toBe('#/standards/ps/1')

    await openTreeDoc(wrapper, 'Weblogic监控模板v1')
    expect(window.location.hash).toBe('#/standards/doc/101')

    await wrapper.find('.tree-header button').trigger('click')
    await flushPromises()
    expect(window.location.hash).toBe('#/standards')

    // 直接通过地址栏路径访问文档页面（等价于新标签页打开/刷新）
    mountedWrappers.pop().unmount()
    window.location.hash = '#/standards/doc/101'
    const direct = await mountPage()
    expect(direct.find('.standards-detail-layout').exists()).toBe(true)
    expect(direct.find('.standards-tree').exists()).toBe(true)
    expect(direct.find('.markdown-document-preview').text()).toContain('Weblogic监控模板v1')
    expect(direct.find('.doc-table-viewport').exists()).toBe(true)
    expect(direct.find('.job-navigation').exists()).toBe(true)

    // 刷新（重新挂载同一路径）后依然正常
    mountedWrappers.pop().unmount()
    const refreshed = await mountPage()
    expect(refreshed.find('.standards-detail-layout').exists()).toBe(true)
    expect(refreshed.find('.markdown-document-preview').text()).toContain('Weblogic监控模板v1')

    // 参数标准路径同样可直接访问
    mountedWrappers.pop().unmount()
    window.location.hash = '#/standards/ps/1'
    const standardDirect = await mountPage()
    expect(standardDirect.find('.standard-detail-head h2').text()).toContain('WebLogic')
    expect(standardDirect.find('.doc-card-list').exists()).toBe(true)
    expect(useNotify().notice.value).toBeNull()
  })

  test('TC-STANDARD-DETAIL-007 (TC-01/TC-04) 收起交互与表格滚动能力沉淀为独立组件', async () => {
    const wrapper = await mountPage()

    expect(wrapper.findComponent({ name: 'StandardsCategorySidebar' }).exists()).toBe(true)
    // 页面文件继续保持在 500 行以内
    expect(standardsPageSource.split('\n').length).toBeLessThanOrEqual(500)
    expect(categorySidebarSource.split('\n').length).toBeLessThanOrEqual(500)
  })
})
