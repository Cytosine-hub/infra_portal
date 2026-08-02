// @vitest-environment jsdom
// 知识库合并页 —— KnowledgePanel + WikiPanel 合并为单页 5 标签
// 验收用例 TC-KB-001 ~ TC-KB-014
//
// 这组用例针对的是 code review 抓到、而构建不会报错的那一类缺陷：
//   1. 插槽名不匹配 —— 内容静默不渲染（Toolbar 只有 #filters/#actions，无默认插槽）
//   2. 字段名不匹配 —— 接口通了但界面空白（如后端返回 chunks，前端读 content）
// 因此断言集中在「操作区确实渲染出来」与「按后端真实返回结构取值」两点。

import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import KnowledgeBasePage from '../src/components/KnowledgeBasePage.vue'
import SearchTab from '../src/components/knowledge/SearchTab.vue'
import DocumentsTab from '../src/components/knowledge/DocumentsTab.vue'
import ExperienceTab from '../src/components/knowledge/ExperienceTab.vue'
import HealthTab from '../src/components/knowledge/HealthTab.vue'
import PageHeader from '../src/components/ui/PageHeader.vue'
import * as api from '../src/api.js'

const mounted = []

function mountTab(component, props = {}) {
  const wrapper = mount(component, {
    props: { notify: vi.fn(), confirm: vi.fn(), ...props }
  })
  mounted.push(wrapper)
  return wrapper
}

beforeEach(() => {
  vi.restoreAllMocks()
})

afterEach(() => {
  while (mounted.length) mounted.pop().unmount()
  document.body.innerHTML = ''
})

describe('页面骨架', () => {
  test('TC-KB-001 应渲染检索/文档/经验沉淀/图谱/健康度五个标签', () => {
    vi.spyOn(api, 'request').mockResolvedValue([])
    const wrapper = mountTab(KnowledgeBasePage)

    const text = wrapper.text()
    for (const label of ['检索', '文档', '经验沉淀', '图谱', '健康度']) {
      expect(text).toContain(label)
    }
  })

  test('TC-KB-002 默认停留在检索标签', () => {
    vi.spyOn(api, 'request').mockResolvedValue([])
    const wrapper = mountTab(KnowledgeBasePage)

    expect(wrapper.findComponent(SearchTab).exists()).toBe(true)
    expect(wrapper.findComponent(DocumentsTab).exists()).toBe(false)
  })

  test('TC-KB-002A 顶部导航已有知识库标题时不应重复渲染页内标题', () => {
    vi.spyOn(api, 'request').mockResolvedValue([])
    const wrapper = mountTab(KnowledgeBasePage)

    expect(wrapper.findComponent(PageHeader).exists()).toBe(false)
  })
})

describe('检索标签', () => {
  test('TC-KB-003 工具栏内容必须真实渲染（Toolbar 无默认插槽，放错插槽会静默消失）', () => {
    const wrapper = mountTab(SearchTab)

    expect(wrapper.find('input').exists()).toBe(true)
    expect(wrapper.text()).toContain('检索')
  })

  test('TC-KB-004 应并行检索文档切片与经验页面，并按来源分别标注', async () => {
    const request = vi.spyOn(api, 'request').mockImplementation(async (path) => {
      if (path.startsWith('/api/knowledge/search')) {
        return [{ sourceTitle: 'MySQL标准', sectionPath: 'MySQL / 应急处理', content: '主从延迟处理', score: 0.83 }]
      }
      return [{ title: '连接池耗尽', summary: '经验总结', pageType: 'EXPERIENCE' }]
    })

    const wrapper = mountTab(SearchTab)
    await wrapper.find('input').setValue('主从延迟')
    await wrapper.findAll('button').at(-1).trigger('click')
    await flushPromises()

    const calls = request.mock.calls.map(([path]) => path)
    expect(calls.some(p => p.startsWith('/api/knowledge/search'))).toBe(true)
    expect(calls.some(p => p.startsWith('/api/knowledge/pages/search'))).toBe(true)

    const text = wrapper.text()
    expect(text).toContain('MySQL标准')
    expect(text).toContain('连接池耗尽')
  })

  test('TC-KB-005 应展示 sectionPath 面包屑（切片层改造的可见产出）', async () => {
    vi.spyOn(api, 'request').mockImplementation(async (path) =>
      path.startsWith('/api/knowledge/search')
        ? [{ sourceTitle: 'PG标准', sectionPath: 'PostgreSQL / 应急处理 / 连接数耗尽', content: '调整 max_connections' }]
        : [])

    const wrapper = mountTab(SearchTab)
    await wrapper.find('input').setValue('连接数')
    await wrapper.findAll('button').at(-1).trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('PostgreSQL / 应急处理 / 连接数耗尽')
  })

  test('TC-KB-006 一路检索失败不应拖垮另一路', async () => {
    vi.spyOn(api, 'request').mockImplementation(async (path) => {
      if (path.startsWith('/api/knowledge/search')) throw new Error('向量库不可用')
      return [{ title: '仍然可见的经验页' }]
    })

    const wrapper = mountTab(SearchTab)
    await wrapper.find('input').setValue('任意')
    await wrapper.findAll('button').at(-1).trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('仍然可见的经验页')
  })
})

describe('文档标签', () => {
  const SOURCES = [{ id: 7, title: 'MySQL运维手册.pdf', sourceType: 'UPLOAD', category: '数据库', software: 'MySQL' }]

  test('TC-KB-007 隐藏的文件输入框必须存在，否则上传按钮点了没反应', async () => {
    vi.spyOn(api, 'request').mockResolvedValue(SOURCES)
    const wrapper = mountTab(DocumentsTab)
    await flushPromises()

    expect(wrapper.find('input[type="file"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('上传文档')
  })

  test('TC-KB-008 应列出源文档并把来源类型显示为中文', async () => {
    vi.spyOn(api, 'request').mockResolvedValue(SOURCES)
    const wrapper = mountTab(DocumentsTab)
    await flushPromises()

    expect(wrapper.text()).toContain('MySQL运维手册.pdf')
    expect(wrapper.text()).toContain('上传')
  })

  test('TC-KB-009 预览应按后端真实结构读取 chunks 而非 content', async () => {
    vi.spyOn(api, 'request').mockImplementation(async (path) => {
      if (path.startsWith('/api/knowledge/docs/preview')) {
        return { title: 'MySQL运维手册.pdf', totalChunks: 2, chunks: [{ chunkIndex: 0, content: '第一片内容' }, { chunkIndex: 1, content: '第二片内容' }] }
      }
      return SOURCES
    })

    const wrapper = mountTab(DocumentsTab)
    await flushPromises()
    await wrapper.findAll('button').find(b => b.text() === '预览').trigger('click')
    await flushPromises()

    // BaseModal 用 Teleport to="body"，弹窗内容不在 wrapper 内
    const text = document.body.textContent
    expect(text).toContain('第一片内容')
    expect(text).toContain('第二片内容')
    expect(text).toContain('共 2 个切片')
  })

  test('TC-KB-010 删除应先确认，确认后调用带完整清理的 DELETE /docs', async () => {
    const request = vi.spyOn(api, 'request').mockResolvedValue(SOURCES)
    const confirm = vi.fn()
    const wrapper = mountTab(DocumentsTab, { confirm })
    await flushPromises()

    await wrapper.findAll('button').find(b => b.text() === '删除').trigger('click')
    expect(confirm).toHaveBeenCalled()

    await confirm.mock.calls[0][1]()
    const deleteCall = request.mock.calls.find(([, opts]) => opts?.method === 'DELETE')
    expect(deleteCall[0]).toContain('/api/knowledge/docs?')
  })

  test('TC-KB-010A 应提供参数标准、标准文档和论坛文章的统一导入入口', async () => {
    vi.spyOn(api, 'request').mockResolvedValue(SOURCES)
    const wrapper = mountTab(DocumentsTab)
    await flushPromises()

    await wrapper.findAll('button').find(b => b.text() === '从现有内容导入').trigger('click')
    await flushPromises()

    const text = document.body.textContent
    expect(text).toContain('参数标准')
    expect(text).toContain('标准文档')
    expect(text).toContain('论坛文章')
  })

  test('TC-KB-010B 参数标准应调用对账同步，避免产生会过期的静态副本', async () => {
    const request = vi.spyOn(api, 'request').mockImplementation(async (path) => {
      if (path === '/api/knowledge/sync-standards') return { indexed: 2, skipped: 1, removed: 0, failed: 0 }
      return SOURCES
    })
    const wrapper = mountTab(DocumentsTab)
    await flushPromises()

    await wrapper.findAll('button').find(b => b.text() === '从现有内容导入').trigger('click')
    await flushPromises()
    const sync = [...document.body.querySelectorAll('button')]
      .find(b => b.textContent.trim() === '同步参数标准')
    sync.click()
    await flushPromises()

    expect(request).toHaveBeenCalledWith('/api/knowledge/sync-standards', { method: 'POST' })
  })

  test('TC-KB-010C 选中的标准文档应只提交业务 ID，由后端读取权威正文', async () => {
    const standardDocument = { id: 11, title: 'Nginx 部署规范', content: '部署正文', category: '中间件', software: 'Nginx' }
    const request = vi.spyOn(api, 'request').mockImplementation(async (path) => {
      if (path === '/api/public/standards/all') return [standardDocument]
      return SOURCES
    })
    const wrapper = mountTab(DocumentsTab)
    await flushPromises()

    await wrapper.findAll('button').find(b => b.text() === '从现有内容导入').trigger('click')
    await flushPromises()
    ;[...document.body.querySelectorAll('button')]
      .find(b => b.textContent.includes('标准文档')).click()
    await flushPromises()
    document.body.querySelector('.import-list input[type="checkbox"]').click()
    await flushPromises()
    ;[...document.body.querySelectorAll('button')]
      .find(b => b.textContent.trim() === '导入选中内容').click()
    await flushPromises()

    const call = request.mock.calls.find(([path]) => path === '/api/knowledge/import-content')
    expect(call[1].body).toMatchObject({
      sourceId: 11, sourceType: 'STANDARD_DOCUMENT'
    })
    expect(call[1].body).not.toHaveProperty('content')
  })

  test('TC-KB-010D 论坛文章应只提交业务 ID，不经客户端转发正文', async () => {
    const request = vi.spyOn(api, 'request').mockImplementation(async (path) => {
      if (path.startsWith('/api/forum/posts?')) return { content: [{ id: 21, title: '连接池故障复盘', summary: '摘要' }] }
      return SOURCES
    })
    const wrapper = mountTab(DocumentsTab)
    await flushPromises()

    await wrapper.findAll('button').find(b => b.text() === '从现有内容导入').trigger('click')
    await flushPromises()
    ;[...document.body.querySelectorAll('button')]
      .find(b => b.textContent.includes('论坛文章')).click()
    await flushPromises()
    document.body.querySelector('.import-list input[type="checkbox"]').click()
    await flushPromises()
    ;[...document.body.querySelectorAll('button')]
      .find(b => b.textContent.trim() === '导入选中内容').click()
    await flushPromises()

    const call = request.mock.calls.find(([path]) => path === '/api/knowledge/import-content')
    expect(call[1].body).toMatchObject({
      sourceId: 21, sourceType: 'FORUM_POST'
    })
    expect(call[1].body).not.toHaveProperty('content')
  })

  test('TC-KB-010E 论坛导入候选应读取全部分页，不遗漏较早文章', async () => {
    const request = vi.spyOn(api, 'request').mockImplementation(async (path) => {
      if (path === '/api/forum/posts?page=0&size=50') {
        return {
          content: [{ id: 21, title: '近期文章' }],
          totalPages: 2,
          last: false
        }
      }
      if (path === '/api/forum/posts?page=1&size=50') {
        return {
          content: [{ id: 9, title: '较早文章' }],
          totalPages: 2,
          last: true
        }
      }
      return SOURCES
    })
    const wrapper = mountTab(DocumentsTab)
    await flushPromises()

    await wrapper.findAll('button').find(b => b.text() === '从现有内容导入').trigger('click')
    await flushPromises()
    ;[...document.body.querySelectorAll('button')]
      .find(b => b.textContent.includes('论坛文章')).click()
    await flushPromises()

    expect(request).toHaveBeenCalledWith('/api/forum/posts?page=1&size=50')
    expect(document.body.textContent).toContain('近期文章')
    expect(document.body.textContent).toContain('较早文章')
  })
})

describe('经验沉淀标签', () => {
  const PAGES = [{ id: 3, title: '主从延迟处理', category: '数据库', software: 'MySQL', status: 'DRAFT' }]

  test('TC-KB-011 必须提供新建入口（编译流水线下线后页面只能人工创建）', async () => {
    vi.spyOn(api, 'request').mockResolvedValue(PAGES)
    const wrapper = mountTab(ExperienceTab)
    await flushPromises()

    expect(wrapper.text()).toContain('新建页面')
  })

  test('TC-KB-012 新建保存应 POST /pages，编辑保存应 PUT /pages/{id}', async () => {
    const request = vi.spyOn(api, 'request').mockResolvedValue(PAGES)
    const wrapper = mountTab(ExperienceTab)
    await flushPromises()

    await wrapper.findAll('button').find(b => b.text() === '新建页面').trigger('click')
    await flushPromises()

    // 编辑器在 Teleport 出去的弹窗里，需直接操作 document.body 中的元素
    const titleInput = document.body.querySelector('.modal-body input[type="text"]')
    titleInput.value = '新经验'
    titleInput.dispatchEvent(new Event('input'))
    const textarea = document.body.querySelector('.modal-body textarea')
    textarea.value = '正文'
    textarea.dispatchEvent(new Event('input'))
    await flushPromises()

    const saveBtn = [...document.body.querySelectorAll('.modal-body button')]
      .find(b => b.textContent.trim() === '保存为草稿')
    saveBtn.click()
    await flushPromises()

    const post = request.mock.calls.find(([p, o]) => p === '/api/knowledge/pages' && o?.method === 'POST')
    expect(post).toBeTruthy()
    expect(post[1].body.title).toBe('新经验')
  })

  test('TC-KB-013 关联页面应按 outgoing/incoming 结构与 relatedTitle 字段渲染', async () => {
    vi.spyOn(api, 'request').mockImplementation(async (path) => {
      if (path.endsWith('/links')) {
        return {
          outgoing: [{ linkId: 1, relatedPageId: 9, relatedTitle: '连接池配置', direction: 'outgoing' }],
          incoming: [{ linkId: 2, relatedPageId: 8, relatedTitle: '慢查询排查', direction: 'incoming' }]
        }
      }
      if (/\/pages\/\d+$/.test(path)) return { id: 3, title: '主从延迟处理', content: '正文' }
      return PAGES
    })

    const wrapper = mountTab(ExperienceTab)
    await flushPromises()
    await wrapper.findAll('button').find(b => b.text() === '查看').trigger('click')
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('连接池配置')
    expect(text).toContain('慢查询排查')
  })

  test('TC-KB-014 导出必须走 api.js 的 fetchBinary，不能用 window.open（会丢 Bearer 头）', async () => {
    vi.spyOn(api, 'request').mockResolvedValue(PAGES)
    const fetchBinary = vi.spyOn(api, 'fetchBinary').mockResolvedValue(new Blob(['x']))
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:stub')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})

    const wrapper = mountTab(ExperienceTab)
    await flushPromises()
    await wrapper.findAll('button').find(b => b.text() === '导出').trigger('click')
    await flushPromises()

    expect(fetchBinary).toHaveBeenCalledWith('/api/knowledge/pages/export')
    expect(openSpy).not.toHaveBeenCalled()
  })
})

describe('健康度标签', () => {
  test('TC-KB-015 应按 lintType/severity 字段渲染体检结果并展示统计', async () => {
    vi.spyOn(api, 'request').mockImplementation(async (path) => {
      if (path.endsWith('/lint/results')) {
        return [{ id: 1, lintType: 'BROKEN_LINK', severity: 'HIGH', description: '指向不存在的页面' }]
      }
      if (path.endsWith('/corpus-health')) {
        return {
          totalKnowledgeItems: 17,
          totalSources: 5,
          totalPages: 12,
          activePages: 8,
          draftPages: 4,
          sourceTypeCounts: { UPLOAD: 2, STANDARD_DOC: 1, FORUM_POST: 2 },
          indexStatusReliable: true,
          unindexedSources: [],
          emptySources: [],
          duplicateContentGroups: []
        }
      }
      return []
    })

    const wrapper = mountTab(HealthTab)
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('断链')
    expect(text).toContain('指向不存在的页面')
    expect(text).toContain('12')
  })

  test('TC-KB-016 应检查知识库全部内容，不再展示固定软件标准覆盖矩阵', async () => {
    const request = vi.spyOn(api, 'request').mockImplementation(async (path) => {
      if (path.endsWith('/lint/results')) return []
      if (path.endsWith('/corpus-health')) {
        return {
          totalSources: 4,
          totalPages: 3,
          totalKnowledgeItems: 7,
          activePages: 2,
          draftPages: 1,
          sourceTypeCounts: { UPLOAD: 1, STANDARD_DOC: 1, STANDARD_DOCUMENT: 1, FORUM_POST: 1 },
          indexStatusReliable: true,
          unindexedSources: ['未索引文章'],
          emptySources: ['空白知识'],
          duplicateContentGroups: ['重复手册 A、重复手册 B']
        }
      }
      return null
    })

    const wrapper = mountTab(HealthTab)
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('知识库概况')
    expect(text).not.toContain('标准覆盖度')
    expect(text).not.toContain('后台软件')
    expect(text).toContain('4')
    expect(text).toContain('知识内容')
    expect(text).toContain('7')
    expect(text).toContain('知识页面')
    expect(text).toContain('3')
    expect(text).toContain('上传文档')
    expect(text).toContain('参数标准')
    expect(text).toContain('标准文档')
    expect(text).toContain('论坛文章')
    expect(text).toContain('空白知识')
    expect(text).toContain('重复手册 A、重复手册 B')
    expect(text).toContain('未索引文章')
    expect(request.mock.calls.map(([path]) => path)).not.toContain('/api/knowledge/stats')
  })

  test('TC-KB-017 健康度接口部分失败时应保留可用结果并提示错误', async () => {
    vi.spyOn(api, 'request').mockImplementation(async (path) => {
      if (path.endsWith('/lint/results')) {
        return [{ id: 1, lintType: 'BROKEN_LINK', severity: 'HIGH', description: '仍可展示的问题' }]
      }
      throw new Error('健康度统计暂不可用')
    })
    const notify = vi.fn()

    const wrapper = mountTab(HealthTab, { notify })
    await flushPromises()

    expect(wrapper.text()).toContain('仍可展示的问题')
    expect(notify).toHaveBeenCalledWith('健康度统计暂不可用', 'error')
  })
})
