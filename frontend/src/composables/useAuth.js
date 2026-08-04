/**
 * 认证状态管理 composable
 * 从 App.vue 中提取，供所有组件共享
 */
import { reactive, computed } from 'vue'
import {
  getSavedAuth,
  getSavedAuthExpiry,
  saveAuth,
  clearAuth,
  handleUnauthorized,
  request
} from '../api'
import CryptoJS from 'crypto-js'

const auth = reactive({ token: '', user: null })
const MAX_TIMER_DELAY = 2_147_483_647
let expiryTimer = null

function clearExpiryTimer() {
  if (expiryTimer !== null) {
    clearTimeout(expiryTimer)
    expiryTimer = null
  }
}

function expireSession() {
  expiryTimer = null
  auth.token = ''
  auth.user = null
  handleUnauthorized()
}

function scheduleExpiryTimer() {
  clearExpiryTimer()
  const expiry = getSavedAuthExpiry()
  if (!expiry) return

  const remaining = expiry.getTime() - Date.now()
  if (remaining <= 0) {
    expireSession()
    return
  }

  expiryTimer = setTimeout(() => {
    const latestExpiry = getSavedAuthExpiry()
    if (latestExpiry && latestExpiry.getTime() > Date.now()) {
      scheduleExpiryTimer()
      return
    }
    expireSession()
  }, Math.min(remaining, MAX_TIMER_DELAY))
}

function sha256(str) {
  return CryptoJS.SHA256(str).toString()
}

const READ_ONLY_ROLES = ['开发经理', '运维经理']
const CATEGORY_ADMIN_ROLES = ['中间件管理员', '数据库管理员', '网络管理员', '主机管理员', '网络安全管理员']
const MANAGER_ROLES = ['中间件管理岗', '数据库管理岗', '主机管理岗', '网络管理岗', '网络安全岗']
const CATEGORY_MAP = {
  '中间件管理岗': '中间件', '中间件管理员': '中间件',
  '数据库管理岗': '数据库', '数据库管理员': '数据库',
  '主机管理岗': '主机', '主机管理员': '主机',
  '网络管理岗': '网络', '网络管理员': '网络',
  '网络安全岗': '安全', '网络安全管理员': '安全'
}

export function useAuth() {
  const currentUserRole = computed(() => auth.user?.role || '')
  const isSysAdmin = computed(() => currentUserRole.value === '系统管理员')
  const isCategoryAdmin = computed(() => CATEGORY_ADMIN_ROLES.includes(currentUserRole.value))
  const isManager = computed(() => MANAGER_ROLES.includes(currentUserRole.value))
  const canAccessAdmin = computed(() => isSysAdmin.value || isCategoryAdmin.value || isManager.value)
  const isReadOnly = computed(() => !auth.token || READ_ONLY_ROLES.includes(currentUserRole.value))
  const managedCategory = computed(() => CATEGORY_MAP[currentUserRole.value] || '')

  async function login(username, password) {
    const pwHash = sha256(password)
    const basicToken = btoa(username + ':' + pwHash)
    const data = await request('/api/auth/login', {
      method: 'POST',
      token: null,
      headers: { Authorization: 'Basic ' + basicToken }
    })
    saveAuth(username, data.token, data, data.expiresAt)
    auth.token = data.token
    auth.user = data
    scheduleExpiryTimer()
    return data
  }

  async function logout(callApi = true) {
    clearExpiryTimer()
    if (callApi && auth.token) {
      try {
        await request('/api/auth/logout', { method: 'POST' })
      } catch { /* ignore */ }
    }
    clearAuth()
    auth.token = ''
    auth.user = null
  }

  function restoreAuth() {
    const saved = getSavedAuth()
    if (saved) {
      auth.token = saved.token
      auth.user = saved.user
      scheduleExpiryTimer()
    } else {
      clearExpiryTimer()
      auth.token = ''
      auth.user = null
    }
  }

  return {
    auth, login, logout, restoreAuth, sha256,
    currentUserRole, isSysAdmin, isCategoryAdmin, isManager,
    canAccessAdmin, isReadOnly, managedCategory
  }
}
