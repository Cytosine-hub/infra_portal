// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import DiagnosticsPanel from '../src/components/DiagnosticsPanel.vue'

const storage = new Map()
const localStorageMock = {
  clear: () => storage.clear(),
  getItem: (key) => storage.get(key) ?? null,
  removeItem: (key) => storage.delete(key),
  setItem: (key, value) => storage.set(key, String(value))
}

function jsonResponse(data, status = 200, statusText = 'OK') {
  return Promise.resolve(new Response(JSON.stringify(data), {
    status,
    statusText,
    headers: { 'Content-Type': 'application/json' }
  }))
}

describe('智能排查认证失效处理', () => {
  beforeEach(() => {
    Object.defineProperty(window, 'localStorage', { value: localStorageMock, configurable: true })
    vi.stubGlobal('localStorage', localStorageMock)
    localStorageMock.clear()
    localStorageMock.setItem('mrm.token', 'expired-token')
    localStorageMock.setItem('mrm.user', JSON.stringify({ username: 'tester', role: '系统管理员' }))
    localStorageMock.setItem('mrm.expiresAt', '2999-01-01T00:00:00Z')
    window.location.hash = '#/diagnostics'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  test('TC-DIAG-001 流式请求返回 401 时应立即同步登出且不得跳转下载中心', async () => {
    vi.stubGlobal('fetch', vi.fn((input) => {
      const path = new URL(String(input), 'http://localhost').pathname
      if (path === '/api/agent/chat') {
        return jsonResponse({ message: '登录状态已失效' }, 401, 'Unauthorized')
      }
      return jsonResponse([])
    }))
    const logoutListener = vi.fn()
    window.addEventListener('auth:logout', logoutListener, { once: true })

    const wrapper = mount(DiagnosticsPanel, {
      props: {
        auth: { token: 'expired-token', user: { username: 'tester', role: '系统管理员' } },
        notify: vi.fn()
      }
    })
    await flushPromises()

    await wrapper.find('.chat-placeholder button').trigger('click')
    await wrapper.find('.chat-input-area textarea').setValue('检查 Tomcat 故障')
    await wrapper.find('.chat-input-area > button:last-child').trigger('click')
    await flushPromises()

    expect(localStorageMock.getItem('mrm.token')).toBeNull()
    expect(logoutListener).toHaveBeenCalledOnce()
    expect(window.location.hash).toBe('#/diagnostics')

    wrapper.unmount()
  })
})
