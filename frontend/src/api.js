const TOKEN_KEY = 'mrm.token'
const USER_KEY = 'mrm.user'
const EXPIRES_KEY = 'mrm.expiresAt'
export const AUTH_EXPIRED_MESSAGE = '登录已过期，请重新登录'

function parseExpiry(expiresAt) {
  if (!expiresAt) return null
  const hasTimeZone = /(?:Z|[+-]\d{2}:\d{2})$/i.test(expiresAt)
  const expiry = new Date(hasTimeZone ? expiresAt : `${expiresAt}Z`)
  return Number.isNaN(expiry.getTime()) ? null : expiry
}

export function getSavedAuthExpiry() {
  return parseExpiry(localStorage.getItem(EXPIRES_KEY))
}

export function getSavedAuth() {
  const token = localStorage.getItem(TOKEN_KEY)
  const user = localStorage.getItem(USER_KEY)
  const expiresAt = localStorage.getItem(EXPIRES_KEY)

  if (!token || !user) return null

  if (expiresAt) {
    const expiry = parseExpiry(expiresAt)
    if (expiry && expiry <= new Date()) {
      handleUnauthorized()
      return null
    }
  }

  try {
    return { token, user: JSON.parse(user) }
  } catch {
    clearAuth()
    return null
  }
}

export function saveAuth(username, token, user, expiresAt) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
  if (expiresAt) localStorage.setItem(EXPIRES_KEY, expiresAt)
  return token
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(EXPIRES_KEY)
}

export function handleUnauthorized() {
  const hadAuth = Boolean(localStorage.getItem(TOKEN_KEY))
  clearAuth()
  if (hadAuth) {
    window.dispatchEvent(new CustomEvent('auth:logout', {
      detail: { reason: 'expired', message: AUTH_EXPIRED_MESSAGE }
    }))
  }
}

export async function request(path, options = {}) {
  const token = 'token' in options ? options.token : localStorage.getItem(TOKEN_KEY)
  const headers = new Headers(options.headers || {})

  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  let body = options.body
  if (body && !(body instanceof FormData) && typeof body !== 'string') {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(body)
  }

  const fetchOptions = {
    method: options.method || 'GET',
    headers,
    body
  }
  if (options.signal) {
    fetchOptions.signal = options.signal
  }

  const response = await fetch(path, fetchOptions)

  if (!response.ok) {
    const authenticationExpired = response.status === 401 && Boolean(token)
    if (authenticationExpired) {
      handleUnauthorized()
    }
    let message = response.statusText || 'Request failed'
    try {
      const payload = await response.json()
      const fieldErrors = payload.fieldErrors ? Object.values(payload.fieldErrors).filter(Boolean) : []
      message = fieldErrors.length ? fieldErrors.join('；') : (payload.message || payload.error || message)
    } catch {
      // Keep the HTTP status text when the backend did not return JSON.
    }
    if (authenticationExpired) {
      message = AUTH_EXPIRED_MESSAGE
    }
    const error = new Error(message)
    error.status = response.status
    throw error
  }

  if (response.status === 204) {
    return null
  }

  const text = await response.text()
  if (!text) {
    return null
  }
  return JSON.parse(text)
}

export async function authorizedFetch(path, options = {}) {
  const token = 'token' in options ? options.token : localStorage.getItem(TOKEN_KEY)
  const headers = new Headers(options.headers || {})
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(path, { ...options, headers })
  if (response.status === 401 && token) {
    handleUnauthorized()
  }
  return response
}

export async function fetchBinary(path) {
  const token = localStorage.getItem(TOKEN_KEY)
  const headers = new Headers()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(path, { headers })
  if (!response.ok) {
    if (response.status === 401) {
      handleUnauthorized()
      throw new Error(AUTH_EXPIRED_MESSAGE)
    }
    throw new Error(`文件加载失败 (${response.status})`)
  }
  return response.blob()
}
