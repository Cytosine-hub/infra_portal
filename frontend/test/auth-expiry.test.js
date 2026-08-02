// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'

import { getSavedAuth, request } from '../src/api.js'

const storage = new Map()
const localStorageMock = {
  clear: () => storage.clear(),
  getItem: (key) => storage.get(key) ?? null,
  removeItem: (key) => storage.delete(key),
  setItem: (key, value) => storage.set(key, String(value))
}

describe('登录有效期解析', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-31T10:56:00Z'))
    Object.defineProperty(window, 'localStorage', { value: localStorageMock, configurable: true })
    vi.stubGlobal('localStorage', localStorageMock)
    localStorageMock.clear()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  test('TC-AUTH-002 旧版无时区 expiresAt 应按 UTC 解析而不是按浏览器本地时区误判过期', () => {
    localStorageMock.setItem('mrm.token', 'valid-token')
    localStorageMock.setItem('mrm.user', JSON.stringify({ username: 'tester' }))
    localStorageMock.setItem('mrm.expiresAt', '2026-07-31T12:56:00.000000000')

    expect(getSavedAuth()).toEqual({
      token: 'valid-token',
      user: { username: 'tester' }
    })
  })

  test('TC-AUTH-004 带时区的过期 expiresAt 应清理已保存登录态', () => {
    localStorageMock.setItem('mrm.token', 'expired-token')
    localStorageMock.setItem('mrm.user', JSON.stringify({ username: 'tester' }))
    localStorageMock.setItem('mrm.expiresAt', '2026-07-31T10:55:59Z')

    expect(getSavedAuth()).toBeNull()
    expect(localStorageMock.getItem('mrm.token')).toBeNull()
    expect(localStorageMock.getItem('mrm.user')).toBeNull()
    expect(localStorageMock.getItem('mrm.expiresAt')).toBeNull()
  })

  test('TC-AUTH-006 接口返回英文 401 时应统一提示中文并广播过期原因', async () => {
    localStorageMock.setItem('mrm.token', 'expired-token')
    localStorageMock.setItem('mrm.user', JSON.stringify({ username: 'tester' }))
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(
      JSON.stringify({ message: 'Unauthorized' }),
      { status: 401, statusText: 'Unauthorized', headers: { 'Content-Type': 'application/json' } }
    ))))
    const logoutListener = vi.fn()
    window.addEventListener('auth:logout', logoutListener, { once: true })

    await expect(request('/api/protected')).rejects.toThrow('登录已过期，请重新登录')

    expect(logoutListener).toHaveBeenCalledOnce()
    expect(logoutListener.mock.calls[0][0].detail).toEqual({
      reason: 'expired',
      message: '登录已过期，请重新登录'
    })
  })
})
