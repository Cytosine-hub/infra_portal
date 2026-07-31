// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'

import { getSavedAuth } from '../src/api.js'

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
})
