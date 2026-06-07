/**
 * Hash 路由管理 composable
 * 从 App.vue 提取的完整路由解析逻辑
 */
import { reactive } from 'vue'

const routeNames = {
  HOME: 'home',
  PUBLIC: 'public',
  STANDARDS: 'standards',
  FORUM: 'forum',
  FORUM_DETAIL: 'forumDetail',
  FORUM_EDITOR: 'forumEditor',
  FORUM_MINE: 'forumMine',
  KNOWLEDGE: 'knowledge',
  WIKI: 'wiki',
  DIAGNOSTICS: 'diagnostics',
  COMMANDS: 'commands',
  ADMIN: 'admin',
  DOCUMENT_EDITOR: 'documentEditor'
}

function parseRoute() {
  const hash = window.location.hash.replace(/^#/, '')
  if (!hash || hash === '/' || hash === '/home') return { name: 'home', token: null }
  if (hash.startsWith('/admin/document-editor')) {
    const editorMatch = hash.match(/^\/admin\/document-editor\/(\d+)$/)
    return { name: 'documentEditor', documentId: editorMatch ? editorMatch[1] : null }
  }
  if (hash.startsWith('/admin')) return { name: 'admin', token: null }
  if (hash === '/forum/mine') return { name: 'forumMine', postId: null }
  if (hash.startsWith('/forum/new')) return { name: 'forumEditor', postId: null }
  const forumEditMatch = hash.match(/^\/forum\/edit\/(\d+)$/)
  if (forumEditMatch) return { name: 'forumEditor', postId: forumEditMatch[1] }
  const forumPostMatch = hash.match(/^\/forum\/post\/(\d+)$/)
  if (forumPostMatch) return { name: 'forumDetail', postId: forumPostMatch[1] }
  if (hash === '/forum' || hash.startsWith('/forum')) return { name: 'forum', postId: null }
  if (hash === '/knowledge' || hash === '/knowledge/') return { name: 'knowledge' }
  if (hash === '/wiki' || hash === '/wiki/') return { name: 'wiki' }
  if (hash === '/diagnostics' || hash === '/diagnostics/') return { name: 'diagnostics' }
  const detailMatch = hash.match(/^\/downloads\/(.+)$/)
  if (detailMatch) return { name: 'public', token: detailMatch[1] }
  const standardTypeMatch = hash.match(/^\/standards\/(ps|doc)\/(\d+)$/)
  if (standardTypeMatch) return { name: 'standards', standardId: standardTypeMatch[2], standardType: standardTypeMatch[1] }
  const standardMatch = hash.match(/^\/standards\/(\d+)$/)
  if (standardMatch) return { name: 'standards', standardId: standardMatch[1], standardType: null }
  if (hash === '/standards') return { name: 'standards', standardId: null, standardType: null }
  if (hash === '/commands' || hash.startsWith('/commands')) return { name: 'commands' }
  return { name: 'public', token: null }
}

const HASH_PREFIX = '#/'

const route = reactive(Object.assign(
  { documentId: null, postId: null, standardType: null, standardId: null, token: null, returnTo: null },
  parseRoute()
))

export function useRoute() {
  function syncRoute() {
    const parsed = parseRoute()
    Object.assign(route, parsed)
  }

  /** 导航到指定路由，opts.from 可设置返回目标 */
  function navigate(name, opts) {
    route.returnTo = opts?.from || null
    window.location.hash = HASH_PREFIX + name
  }

  return { route, routeNames, syncRoute, navigate }
}
