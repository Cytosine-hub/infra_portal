const TOKEN_KEY = 'mrm.token'
const USER_KEY = 'mrm.user'
const EXPIRES_KEY = 'mrm.expiresAt'

export function getSavedAuth() {
  const token = localStorage.getItem(TOKEN_KEY)
  const user = localStorage.getItem(USER_KEY)

  if (!token || !user) return null

  return { token, user: JSON.parse(user) }
}

export function saveAuth(username, token, user, expiresAt) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
  localStorage.setItem(EXPIRES_KEY, expiresAt)
  return token
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(EXPIRES_KEY)
}

export function fileUrl(path) {
  return path
}

export async function request(path, options = {}) {
  const token = options.token || localStorage.getItem(TOKEN_KEY)
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
      window.location.hash = '#/login'
    }
    let message = response.statusText || 'Request failed'
    try {
      const payload = await response.json()
      const fieldErrors = payload.fieldErrors ? Object.values(payload.fieldErrors).filter(Boolean) : []
      message = fieldErrors.length ? fieldErrors.join('；') : (payload.message || payload.error || message)
    } catch (error) {
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
