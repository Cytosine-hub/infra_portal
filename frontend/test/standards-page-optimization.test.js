// @vitest-environment jsdom
// 需求 #30（Issue #11）标准发布页面优化 — 验收用例 TC-01 ~ TC-07 自动化测试

import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import StandardsPage from '../src/pages/StandardsPage.vue'
import EmptyState from '../src/components/ui/EmptyState.vue'
import { useNotify } from '../src/composables/useNotify'
// 以 Vite ?raw 导入源码文本做静态检查，避免在 jsdom 下无法计算真实布局时漏掉样式约束
import standardsPageSource from '../src/pages/StandardsPage.vue?raw'
import categorySectionSource from '../src/components/standards/StandardCategorySection.vue?raw'
import overviewPanelSource from '../src/components/standards/StandardsOverviewPanel.vue?raw'

const LONG_TITLE = '浙江泰隆商业银行生产环境中间件与数据库统一部署运维超长标准文档名称用于验证边界展示-202608版'

const mountedWrappers = []
const storage = new Map()
const localStorageMock = {
  clear: () => storage.clear(),
  getItem: (key) => (storage.has(key) ? storage.get(key) : null),
  removeItem: (key) => storage.delete(key),
  setItem: (key, value) => storage.set(key, String(value))
}

const redisDocs = [
  { id: 101, title: 'Redis 监控模板 v1', version: '1.0', status: 'PUBLISHED', publishedAt: '2026-07-28T09:00:00' },
  { id: 102, title: '浙江泰隆商业银行 Redis 应急处理手册', version: '1.1', status: 'PUBLISHED', publishedAt: '2026-07-20T09:00:00' },
  { id: 103, title: 'Redis 部署标准-202502版', standardVersion: '2.0', status: 'PUBLISHED', updatedAt: '2026-06-18T09:00:00' },
  { id: 104, title: 'Redis 容量评估标准', version: '1.0', status: 'PUBLISHED', publishedAt: '2026-05-06T09:00:00' }
]

const standards = [
  {
    id: 1,
    title: 'Redis 部署标准',
    category: '中间件',
    software: 'Redis',
    softwareVersion: 'V2025.3.0',
    version: '1.2',
    publishedAt: '2026-08-01T10:00:00',
    updatedAt: '2026-08-01T10:00:00',
    relatedDocuments: redisDocs
  },
  {
    id: 2,
    title: 'Kafka 部署标准',
    category: '中间件',
    software: 'Kafka',
    softwareVersion: 'V2025.1.0',
    version: '1.0',
    publishedAt: '2026-07-12T10:00:00',
    updatedAt: '2026-07-12T10:00:00',
    relatedDocuments: [{ id: 201, title: 'Kafka 监控模板 v1', version: '1.0', status: 'PUBLISHED' }]
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
  },
  {
    id: 4,
    title: LONG_TITLE,
    category: '主机',
    software: 'Linux',
    softwareVersion: 'V2026.1.0',
    version: '1.0',
    publishedAt: '2026-05-20T10:00:00',
    updatedAt: '2026-05-20T10:00:00',
    relatedDocuments: []
  }
]

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
    const docMatch = url.pathname.match(/^\/api\/public\/standards\/(\d+)$/)
    if (docMatch) {
      const doc = redisDocs.find((item) => String(item.id) === docMatch[1])
      return doc ? jsonResponse({ ...doc, content: `# ${doc.title}` }) : jsonResponse({ code: 'NOT_FOUND', message: '标准文档不存在' }, 404)
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
  return wrapper
}

function cardOf(wrapper, title) {
  return wrapper.findAll('.standard-card').find((card) => card.text().includes(title))
}

beforeEach(() => {
  Object.defineProperty(window, 'localStorage', { value: localStorageMock, configurable: true })
  vi.stubGlobal('localStorage', localStorageMock)
  window.localStorage.clear()
  installApiMock()
  useNotify().notice.value = null
})

afterEach(() => {
  while (mountedWrappers.length) mountedWrappers.pop().unmount()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  vi.useRealTimers()
})

describe('标准发布页面优化验收', () => {
  test('TC-STANDARDS-001 (TC-01) 每条标准完整展示名称、分类、软件版本、标准版本与发布时间', async () => {
    const wrapper = await mountPage()

    expect(wrapper.findAll('.standard-card')).toHaveLength(standards.length)

    const redisCard = cardOf(wrapper, 'Redis 部署标准')
    expect(redisCard.find('.standard-title-link').text()).toBe('Redis 部署标准')
    const metaText = redisCard.find('.standard-card-meta').text()
    expect(metaText).toContain('中间件')
    expect(metaText).toContain('Redis')
    expect(metaText).toContain('V2025.3.0')
    expect(metaText).toContain('1.2')
    expect(metaText).toContain('2026-08-01')

    // 关联标准文档同样要展示版本与发布时间，不能只有标题
    const docLinks = redisCard.findAll('.related-document-link')
    const monitorDoc = docLinks.find((link) => link.text().includes('Redis 监控模板 v1'))
    expect(monitorDoc.find('.related-document-meta').text()).toContain('1.0')
    expect(monitorDoc.find('.related-document-meta').text()).toContain('2026-07-28')
    // 缺少 version/publishedAt 时回退到 standardVersion/updatedAt
    await redisCard.find('.standard-card-more').trigger('click')
    const fallbackDoc = cardOf(wrapper, 'Redis 部署标准').findAll('.related-document-link')
      .find((link) => link.text().includes('Redis 部署标准-202502版'))
    expect(fallbackDoc.find('.related-document-meta').text()).toContain('2.0')
    expect(fallbackDoc.find('.related-document-meta').text()).toContain('2026-06-18')

    // 概览指标：标准总数 / 标准文档 / 标准类别 / 最近更新
    const metrics = wrapper.findAll('.standards-metric')
    expect(metrics).toHaveLength(4)
    expect(metrics[0].text()).toContain('4')
    expect(metrics[1].text()).toContain('5')
    expect(metrics[2].text()).toContain('3')
    expect(metrics[3].text()).toContain('2026-08-01')
  })

  test('TC-STANDARDS-002 (TC-02) 标准按分类分区展示且归属正确', async () => {
    const wrapper = await mountPage()

    const sections = wrapper.findAll('.standard-category-section')
    expect(sections).toHaveLength(3)

    const middleware = sections.find((section) => section.find('.standard-category-title h2').text() === '中间件')
    expect(middleware.find('.standard-category-badge').text()).toContain('2')
    expect(middleware.findAll('.standard-card')).toHaveLength(2)
    expect(middleware.text()).toContain('Redis 部署标准')
    expect(middleware.text()).not.toContain('MySQL 备份恢复标准')

    const database = sections.find((section) => section.find('.standard-category-title h2').text() === '数据库')
    expect(database.findAll('.standard-card')).toHaveLength(1)
    expect(database.text()).toContain('MySQL 备份恢复标准')

    // 左侧类别导航切换后只保留该类别的标准
    const databaseNav = wrapper.findAll('.job-navigation-button').find((button) => button.text().includes('数据库'))
    await databaseNav.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.standard-category-section')).toHaveLength(1)
    expect(wrapper.findAll('.standard-card')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('Redis 部署标准')
  })

  test('TC-STANDARDS-003 (TC-03) 点击标准与标准文档均跳转到对应详情', async () => {
    const wrapper = await mountPage()

    await cardOf(wrapper, 'Redis 部署标准').find('.standard-title-link').trigger('click')
    await flushPromises()
    expect(wrapper.find('.standards-detail-layout').exists()).toBe(true)
    expect(wrapper.find('.standard-detail-head h2').text()).toBe('Redis 部署标准')
    // 详情页的标准文档卡片同样带版本与发布时间
    const detailDoc = wrapper.findAll('.doc-card').find((card) => card.text().includes('Redis 监控模板 v1'))
    expect(detailDoc.find('.doc-card-meta').text()).toContain('1.0')
    expect(detailDoc.find('.doc-card-meta').text()).toContain('2026-07-28')

    await wrapper.find('.tree-header button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.standards-detail-layout').exists()).toBe(false)

    const docLink = cardOf(wrapper, 'Redis 部署标准').findAll('.related-document-link')
      .find((link) => link.text().includes('Redis 监控模板 v1'))
    await docLink.trigger('click')
    await flushPromises()
    expect(wrapper.find('.standards-detail-layout').exists()).toBe(true)
    expect(wrapper.find('.markdown-document-preview').text()).toContain('Redis 监控模板 v1')
  })

  test('TC-STANDARDS-004 (TC-04) 大量标准下分区完整、文档折叠且支持关键字定位', async () => {
    const many = Array.from({ length: 30 }, (_, index) => ({
      id: 1000 + index,
      title: `批量标准-${index}`,
      category: ['中间件', '数据库', '主机'][index % 3],
      software: `Software-${index}`,
      softwareVersion: 'V2025.1.0',
      version: '1.0',
      publishedAt: '2026-07-01T10:00:00',
      relatedDocuments: []
    }))
    installApiMock([...standards, ...many])
    const wrapper = await mountPage()

    expect(wrapper.findAll('.standard-card')).toHaveLength(34)
    expect(wrapper.findAll('.standard-category-section')).toHaveLength(3)

    // 文档超过 3 份时先折叠，点击“查看更多”展开全部
    const redisCard = cardOf(wrapper, 'Redis 部署标准')
    expect(redisCard.findAll('.related-document-link')).toHaveLength(3)
    expect(redisCard.find('.standard-card-actions').text()).toContain('4')
    await redisCard.find('.standard-card-more').trigger('click')
    expect(cardOf(wrapper, 'Redis 部署标准').findAll('.related-document-link')).toHaveLength(4)

    // 关键字搜索快速定位
    await wrapper.find('.standards-search').setValue('批量标准-7')
    expect(wrapper.findAll('.standard-card')).toHaveLength(1)
    expect(wrapper.text()).toContain('批量标准-7')
    expect(wrapper.text()).not.toContain('Redis 部署标准')
  })

  test('TC-STANDARDS-008 (TC-04) 列表结构拆分为子组件，单个组件不超过 500 行', async () => {
    const wrapper = await mountPage()

    // 总览面板与分类分区由独立子组件渲染，页面只负责数据编排
    expect(wrapper.findComponent({ name: 'StandardsOverviewPanel' }).exists()).toBe(true)
    expect(wrapper.findAllComponents({ name: 'StandardCategorySection' })).toHaveLength(3)

    for (const source of [standardsPageSource, categorySectionSource, overviewPanelSource]) {
      expect(source.split('\n').length).toBeLessThanOrEqual(500)
    }
  })

  test('TC-STANDARDS-005 (TC-05) 超长标准名称不破坏布局且可正常进入详情', async () => {
    const wrapper = await mountPage()

    const longCard = cardOf(wrapper, LONG_TITLE)
    const titleLink = longCard.find('.standard-title-link')
    expect(titleLink.attributes('title')).toBe(LONG_TITLE)
    // 长文本必须允许换行截断，避免撑破卡片
    expect(categorySectionSource).toMatch(/\.standard-title-link\s*\{[^}]*overflow-wrap:\s*anywhere/)
    expect(categorySectionSource).toMatch(/\.related-document-title\s*\{[^}]*overflow-wrap:\s*anywhere/)

    await titleLink.trigger('click')
    await flushPromises()
    expect(wrapper.find('.standard-detail-head h2').text()).toBe(LONG_TITLE)
  })

  test('TC-STANDARDS-006 (TC-06) 无标准数据时展示空数据提示', async () => {
    installApiMock([])
    const wrapper = await mountPage()

    expect(wrapper.findAll('.standard-card')).toHaveLength(0)
    expect(wrapper.findComponent(EmptyState).exists()).toBe(true)
    expect(wrapper.text()).toContain('暂无已发布标准')
    // 空数据下概览与工具栏仍然完整，不出现空白页
    expect(wrapper.findAll('.standards-metric')).toHaveLength(4)
    expect(wrapper.find('.standards-search').exists()).toBe(true)
  })

  test('TC-STANDARDS-007 (TC-07) 详情加载失败时给出中文提示且不暴露技术细节', async () => {
    const wrapper = await mountPage()
    const { notice } = useNotify()

    vi.mocked(fetch).mockImplementation((input) => {
      const url = new URL(String(input), 'http://localhost')
      if (url.pathname === '/api/public/parameter-standards') return jsonResponse({ content: standards })
      if (url.pathname === '/api/public/parameter-standards/1') {
        return jsonResponse({ status: 403, code: 'STANDARD_FORBIDDEN', message: '无权访问该标准' }, 403)
      }
      return jsonResponse([])
    })

    await cardOf(wrapper, 'Redis 部署标准').find('.standard-title-link').trigger('click')
    await flushPromises()

    expect(notice.value).toEqual({ message: '无权访问该标准', type: 'error' })
    expect(wrapper.find('.standards-detail-layout').exists()).toBe(false)
    expect(wrapper.findAll('.standard-card')).toHaveLength(standards.length)
    expect(wrapper.text()).not.toContain('Error')
  })

  test('TC-STANDARDS-007 (TC-07) 标准文档详情加载失败时保持列表可用并提示', async () => {
    const wrapper = await mountPage()
    const { notice } = useNotify()

    vi.mocked(fetch).mockImplementation((input) => {
      const url = new URL(String(input), 'http://localhost')
      if (url.pathname === '/api/public/parameter-standards') return jsonResponse({ content: standards })
      if (url.pathname === '/api/public/standards/101') {
        return jsonResponse({ status: 404, code: 'STANDARD_DOCUMENT_NOT_FOUND', message: '标准文档不存在' }, 404)
      }
      return jsonResponse([])
    })

    const docLink = cardOf(wrapper, 'Redis 部署标准').findAll('.related-document-link')
      .find((link) => link.text().includes('Redis 监控模板 v1'))
    await docLink.trigger('click')
    await flushPromises()

    expect(notice.value).toEqual({ message: '标准文档不存在', type: 'error' })
    expect(wrapper.find('.standards-detail-layout').exists()).toBe(false)
    expect(wrapper.findAll('.standard-card')).toHaveLength(standards.length)
  })
})
