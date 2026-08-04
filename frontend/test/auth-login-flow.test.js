// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import App from '../src/App.vue'
import { useAuth } from '../src/composables/useAuth.js'

const storage = new Map()
const localStorageMock = {
  clear: () => storage.clear(),
  getItem: (key) => storage.get(key) ?? null,
  removeItem: (key) => storage.delete(key),
  setItem: (key, value) => storage.set(key, String(value))
}

function response(data, status = 200, statusText = 'OK') {
  return Promise.resolve(new Response(JSON.stringify(data), {
    status,
    statusText,
    headers: { 'Content-Type': 'application/json' }
  }))
}

describe('登录错误边界', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    Object.defineProperty(window, 'localStorage', { value: localStorageMock, configurable: true })
    vi.stubGlobal('localStorage', localStorageMock)
    localStorageMock.clear()
    useAuth().logout(false)
    window.location.hash = '#/admin'
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  test('TC-AUTH-001 登录成功后的数据加载 403 不得被误报为登录失败', async () => {
    vi.stubGlobal('fetch', vi.fn((input) => {
      const path = new URL(String(input), 'http://localhost').pathname
      if (path === '/api/auth/login') {
        return response({
          token: 'valid-token', username: 'sysadmin', displayName: '系统管理员',
          role: '系统管理员', expiresAt: '2999-01-01T00:00:00Z'
        })
      }
      if (path === '/api/public/config') return response({ knowledgeEnabled: true, diagnosticsEnabled: true })
      return response({ message: 'Forbidden' }, 403, 'Forbidden')
    }))

    const wrapper = mount(App)
    await flushPromises()
    await wrapper.find('.login-form input[autocomplete="username"]').setValue('sysadmin')
    await wrapper.find('.login-form input[type="password"]').setValue('admin123')
    await wrapper.find('.login-form').trigger('submit')
    await flushPromises()

    expect(useAuth().auth.token).toBe('valid-token')
    expect(wrapper.find('.login-page').exists()).toBe(false)
    expect(wrapper.find('.toast-message').text()).toContain('登录成功')
    expect(wrapper.find('.toast-message').text()).not.toBe('Forbidden')

    wrapper.unmount()
  })

  test('TC-AUTH-003 已登录用户在首页也应看到完整顶部导航', async () => {
    localStorageMock.setItem('mrm.token', 'valid-token')
    localStorageMock.setItem('mrm.user', JSON.stringify({
      username: 'sysadmin', displayName: '系统管理员', role: '系统管理员'
    }))
    localStorageMock.setItem('mrm.expiresAt', '2999-01-01T00:00:00Z')
    window.location.hash = '#/home'
    vi.stubGlobal('fetch', vi.fn((input) => {
      const path = new URL(String(input), 'http://localhost').pathname
      if (path === '/api/public/config') return response({ knowledgeEnabled: true, diagnosticsEnabled: true })
      if (path === '/api/public/releases') return response({ content: [] })
      return response([])
    }))

    const wrapper = mount(App)
    await flushPromises()

    const navigation = wrapper.find('nav.nav-tabs')
    expect(navigation.exists()).toBe(true)
    expect(navigation.text()).toContain('首页')
    expect(navigation.text()).toContain('论坛')
    expect(navigation.text()).toContain('智能排查')

    wrapper.unmount()
  })

  test('TC-AUTH-005 Token 到期后应主动退出并提示重新登录', async () => {
    vi.setSystemTime(new Date('2026-08-01T08:00:00Z'))
    localStorageMock.setItem('mrm.token', 'short-lived-token')
    localStorageMock.setItem('mrm.user', JSON.stringify({
      username: 'sysadmin', displayName: '系统管理员', role: '系统管理员'
    }))
    localStorageMock.setItem('mrm.expiresAt', '2026-08-01T08:00:02Z')
    window.location.hash = '#/home'
    vi.stubGlobal('fetch', vi.fn((input) => {
      const path = new URL(String(input), 'http://localhost').pathname
      if (path === '/api/public/config') return response({ knowledgeEnabled: true, diagnosticsEnabled: true })
      if (path === '/api/public/releases') return response({ content: [] })
      return response([])
    }))

    const wrapper = mount(App)
    await flushPromises()
    expect(useAuth().auth.token).toBe('short-lived-token')

    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()

    expect(useAuth().auth.token).toBe('')
    expect(localStorageMock.getItem('mrm.token')).toBeNull()
    expect(window.location.hash).toBe('#/admin')
    expect(wrapper.find('.login-page').exists()).toBe(true)
    expect(wrapper.find('.toast-message').text()).toContain('登录已过期，请重新登录')

    wrapper.unmount()
  })
})
