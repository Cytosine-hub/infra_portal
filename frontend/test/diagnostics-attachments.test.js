// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import DiagnosticsPanel from '../src/components/DiagnosticsPanel.vue'

function jsonResponse(data) {
  return Promise.resolve(new Response(JSON.stringify(data), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  }))
}

function sseResponse() {
  const body = [
    'event: result',
    'data: {"answer":"已分析附件","references":[],"sessionId":9}',
    '',
    'event: completed',
    'data: {"sessionId":9}',
    '',
    ''
  ].join('\n')
  return Promise.resolve(new Response(body, {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' }
  }))
}

async function mountPanel(fetchMock, notify = vi.fn()) {
  vi.stubGlobal('fetch', fetchMock)
  localStorage.setItem('mrm.token', 'valid-token')
  localStorage.setItem('mrm.user', JSON.stringify({ username: 'tester', role: '系统管理员' }))
  localStorage.setItem('mrm.expiresAt', '2999-01-01T00:00:00Z')
  const wrapper = mount(DiagnosticsPanel, {
    props: {
      auth: { token: 'valid-token', user: { username: 'tester', role: '系统管理员' } },
      notify
    }
  })
  await flushPromises()
  await wrapper.find('.chat-placeholder button').trigger('click')
  return wrapper
}

async function selectFiles(wrapper, files) {
  const input = wrapper.find('.attachment-input')
  Object.defineProperty(input.element, 'files', { value: files, configurable: true })
  await input.trigger('change')
  await flushPromises()
}

describe('智能排查附件上传', () => {
  beforeEach(() => {
    localStorage.clear()
    Object.defineProperty(URL, 'createObjectURL', {
      value: vi.fn(() => 'blob:http://localhost/screen'),
      configurable: true
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      value: vi.fn(),
      configurable: true
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    localStorage.clear()
  })

  test('TC-DIAG-ATT-001 选择图片后应显示缩略图、名称和大小', async () => {
    const wrapper = await mountPanel(vi.fn(() => jsonResponse([])))
    const image = new File([new Uint8Array([1, 2, 3])], 'screen.png', { type: 'image/png' })

    await selectFiles(wrapper, [image])

    expect(wrapper.find('.attachment-thumbnail').exists()).toBe(true)
    expect(wrapper.find('.pending-attachment').text()).toContain('screen.png')
    expect(wrapper.find('.pending-attachment').text()).toContain('3 B')
    wrapper.unmount()
  })

  test('TC-DIAG-ATT-002 用户应能在发送前移除附件', async () => {
    const wrapper = await mountPanel(vi.fn(() => jsonResponse([])))
    const file = new File(['error'], 'error.log', { type: 'text/plain' })
    await selectFiles(wrapper, [file])

    await wrapper.find('.attachment-remove-btn').trigger('click')

    expect(wrapper.find('.pending-attachment').exists()).toBe(false)
    wrapper.unmount()
  })

  test('TC-DIAG-ATT-003 发送附件时应使用带认证信息的 multipart SSE 请求', async () => {
    let chatRequest
    const fetchMock = vi.fn((input, options = {}) => {
      const path = new URL(String(input), 'http://localhost').pathname
      if (path === '/api/agent/chat') {
        chatRequest = options
        return sseResponse()
      }
      return jsonResponse([])
    })
    const wrapper = await mountPanel(fetchMock)
    const file = new File(['ERROR timeout'], 'app.log', { type: 'text/plain' })
    await selectFiles(wrapper, [file])
    await wrapper.find('.chat-input-area > button:last-child').trigger('click')
    await flushPromises()

    expect(chatRequest.headers.get('Authorization')).toBe('Bearer valid-token')
    expect(chatRequest.body).toBeInstanceOf(FormData)
    expect(chatRequest.body.get('message')).toBe('请分析附件内容并给出排查结论')
    expect(chatRequest.body.getAll('attachments')).toHaveLength(1)
    expect(wrapper.text()).toContain('app.log')
    wrapper.unmount()
  })

  test('TC-DIAG-ATT-004 选择超过 5 个附件时应提示且不加入列表', async () => {
    const notify = vi.fn()
    const wrapper = await mountPanel(vi.fn(() => jsonResponse([])), notify)
    const files = Array.from({ length: 6 }, (_, index) =>
      new File(['x'], `part-${index}.log`, { type: 'text/plain' }))

    await selectFiles(wrapper, files)

    expect(notify).toHaveBeenCalledWith('每次最多上传 5 个附件', 'error')
    expect(wrapper.findAll('.pending-attachment')).toHaveLength(0)
    wrapper.unmount()
  })
})
