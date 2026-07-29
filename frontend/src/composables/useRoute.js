import { reactive } from 'vue'
import { getJobModule } from '../modules/index.js'

const routeNames = {
  HOME: 'home', PUBLIC: 'public', STANDARDS: 'standards', FORUM: 'forum', FORUM_DETAIL: 'forumDetail',
  FORUM_EDITOR: 'forumEditor', FORUM_MINE: 'forumMine', KNOWLEDGE: 'knowledge',
  DIAGNOSTICS: 'diagnostics', JOB_MODULE: 'jobModule', ADMIN: 'admin', DOCUMENT_EDITOR: 'documentEditor',
  WORD_PREVIEW: 'wordPreview', DATA_MIGRATION: 'dataMigration'
}

export function parseHashRoute(hashValue = '') {
  const hash = String(hashValue).replace(/^#/, '')
  if (!hash || hash === '/' || hash === '/home') return { name: 'home', token: null }
  if (hash.startsWith('/admin/document-editor')) {
    const match = hash.match(/^\/admin\/document-editor\/(\d+)$/)
    return { name: 'documentEditor', documentId: match ? match[1] : null }
  }
  if (hash.startsWith('/admin/word-preview')) return { name: 'wordPreview' }
  if (hash.startsWith('/admin')) return { name: 'admin', token: null }
  if (hash === '/forum/mine') return { name: 'forumMine', postId: null }
  if (hash.startsWith('/forum/new')) return { name: 'forumEditor', postId: null }
  const forumEditMatch = hash.match(/^\/forum\/edit\/(\d+)$/)
  if (forumEditMatch) return { name: 'forumEditor', postId: forumEditMatch[1] }
  const forumPostMatch = hash.match(/^\/forum\/post\/(\d+)$/)
  if (forumPostMatch) return { name: 'forumDetail', postId: forumPostMatch[1] }
  if (hash === '/forum' || hash.startsWith('/forum')) return { name: 'forum', postId: null }
  if (hash.startsWith('/knowledge')) return { name: 'knowledge' }
  // Wiki 已并入知识库，旧地址重定向避免书签落空
  if (hash.startsWith('/wiki')) return { name: 'knowledge' }
  if (hash.startsWith('/diagnostics')) return { name: 'diagnostics' }
  if (hash.startsWith('/data-migration')) {
    return { name: 'jobModule', jobId: 'database', feature: 'data-migration', legacy: true }
  }
  const jobMatch = hash.match(/^\/jobs\/([^/]+)(?:\/([^/]+))?$/)
  if (jobMatch && getJobModule(jobMatch[1])) {
    return { name: 'jobModule', jobId: jobMatch[1], feature: jobMatch[2] || null }
  }
  if (hash === '/commands' || hash.startsWith('/commands/')) {
    return { name: 'jobModule', jobId: 'middleware', feature: 'commands', legacy: true }
  }
  const detailMatch = hash.match(/^\/downloads\/(.+)$/)
  if (detailMatch) return { name: 'public', token: detailMatch[1] }
  const standardTypeMatch = hash.match(/^\/standards\/(ps|doc)\/(\d+)$/)
  if (standardTypeMatch) return { name: 'standards', standardId: standardTypeMatch[2], standardType: standardTypeMatch[1] }
  const standardMatch = hash.match(/^\/standards\/(\d+)$/)
  if (standardMatch) return { name: 'standards', standardId: standardMatch[1], standardType: null }
  if (hash === '/standards') return { name: 'standards', standardId: null, standardType: null }
  return { name: 'public', token: null }
}

const currentHash = () => typeof window === 'undefined' ? '/home' : window.location.hash
const route = reactive(Object.assign(
  { documentId: null, postId: null, standardType: null, standardId: null, token: null, returnTo: null, jobId: null, feature: null, legacy: false },
  parseHashRoute(currentHash())
))

export function useRoute() {
  function syncRoute() {
    Object.assign(route, {
      documentId: null, postId: null, standardType: null, standardId: null, token: null,
      jobId: null, feature: null, legacy: false
    }, parseHashRoute(currentHash()))
  }

  function navigate(name, opts) {
    route.returnTo = opts?.from || null
    window.location.hash = '#/' + name
  }

  return { route, routeNames, syncRoute, navigate }
}
