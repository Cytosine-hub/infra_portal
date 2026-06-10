const TOKEN_KEY = 'mrm.token'
const USER_KEY = 'mrm.user'
const EXPIRES_KEY = 'mrm.expiresAt'

export function getSavedAuth() {
  const token = localStorage.getItem(TOKEN_KEY)
  const user = localStorage.getItem(USER_KEY)
  const expiresAt = localStorage.getItem(EXPIRES_KEY)

  if (!token || !user) return null

  // 校验 token 过期时间
  if (expiresAt) {
    const expiry = new Date(expiresAt)
    if (!isNaN(expiry.getTime()) && expiry < new Date()) {
      clearAuth()
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
    if (response.status === 401) {
      clearAuth()
      window.dispatchEvent(new Event('auth:logout'))
    }
    let message = response.statusText || 'Request failed'
    try {
      const payload = await response.json()
      const fieldErrors = payload.fieldErrors ? Object.values(payload.fieldErrors).filter(Boolean) : []
      message = fieldErrors.length ? fieldErrors.join('；') : (payload.message || payload.error || message)
    } catch {
      // Keep the HTTP status text when the backend did not return JSON.
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

export async function fetchBinary(path) {
  const token = localStorage.getItem(TOKEN_KEY)
  const headers = new Headers()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(path, { headers })
  if (!response.ok) {
    if (response.status === 401) {
      clearAuth()
      window.dispatchEvent(new Event('auth:logout'))
    }
    throw new Error(`文件加载失败 (${response.status})`)
  }
  return response.blob()
}
