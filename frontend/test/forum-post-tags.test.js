// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import ForumPostEditor from '../src/components/ForumPostEditor.vue'

const requestMock = vi.fn()

vi.mock('../src/api', () => ({
  request: (...args) => requestMock(...args)
}))

function mountEditor() {
  return mount(ForumPostEditor, {
    props: {
      postId: 1,
      markdown: { render: (content) => `<p>${content}</p>` },
      notify: vi.fn()
    },
    global: {
      stubs: {
        MarkdownHelp: true,
        MarkdownToolbar: true
      }
    }
  })
}

describe('论坛文章再次编辑标签', () => {
  beforeEach(() => {
    requestMock.mockReset()
    requestMock.mockResolvedValue({
      id: 1,
      title: '测试文章',
      content: '正文',
      tags: ['中间件', 'Kafka']
    })
  })

  it('TC-FORUM-001 再次编辑已发布文章时展示已有标签', async () => {
    const wrapper = mountEditor()
    await flushPromises()

    expect(wrapper.findAll('.meta-tag').map((node) => node.text())).toEqual(['中间件×', 'Kafka×'])
  })

  it('TC-FORUM-007 新增重复标签时不产生重复数据', async () => {
    const wrapper = mountEditor()
    await flushPromises()

    await wrapper.find('.meta-tags-input').setValue('kafka')
    await wrapper.find('.meta-tags-input').trigger('keyup.enter')

    expect(wrapper.findAll('.meta-tag').map((node) => node.text().replace('×', '')))
      .toEqual(['中间件', 'Kafka'])
  })
})
