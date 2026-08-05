import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ForumPersonalCenter from '../src/components/ForumPersonalCenter.vue'
import ForumTagsSection from '../src/pages/admin/ForumTagsSection.vue'

vi.mock('../src/api', () => ({ request: vi.fn(() => Promise.resolve([])) }))

describe('论坛标签管理', () => {
  it('TC-01 个人中心提供仅面向本人文章的标签管理页签', () => {
    const wrapper = mount(ForumPersonalCenter, { props: { auth: { token: 'token' }, notify: vi.fn() } })
    expect(wrapper.text()).toContain('标签管理')
  })

  it('TC-04 管理后台提供论坛文章标签的增删改入口', () => {
    const wrapper = mount(ForumTagsSection, {
      props: { isSysAdmin: true, managedCategory: '', notify: vi.fn() },
      global: { stubs: { teleport: true } }
    })
    expect(wrapper.text()).toContain('论坛文章标签管理')
    expect(wrapper.text()).toContain('添加标签')
  })

  it('TC-07 标签表单显示空白和超长名称校验提示', async () => {
    const wrapper = mount(ForumTagsSection, {
      props: { isSysAdmin: true, managedCategory: '', notify: vi.fn() },
      global: { stubs: { teleport: true } }
    })
    await wrapper.get('[data-test="add-tag"]').trigger('click')
    await wrapper.get('[data-test="save-tag"]').trigger('click')
    expect(wrapper.text()).toContain('标签名称不能为空')
    await wrapper.get('[data-test="tag-name"] input').setValue('x'.repeat(51))
    await wrapper.get('[data-test="save-tag"]').trigger('click')
    expect(wrapper.text()).toContain('标签名称不能超过50个字符')
  })
})
