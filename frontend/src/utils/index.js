/**
 * 前端共享工具函数
 * 替代各组件中重复定义的 formatDate、escapeHtml、renderMarkdown 等
 */
import MarkdownIt from 'markdown-it'

/** Markdown 渲染器（统一配置） */
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

/** HTML 实体转义 */
export function escapeHtml(str) {
  if (!str) return ''
  return str.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/** Markdown 渲染为安全 HTML */
export function renderMarkdown(content) {
  if (!content) return ''
  return md.render(content)
}

/** 格式化日期（中文） */
export function formatDate(dt) {
  if (!dt) return ''
  try {
    const d = new Date(dt)
    if (isNaN(d.getTime())) return String(dt)
    return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } catch { return String(dt) }
}

/** 格式化文件大小 */
export function formatBytes(bytes) {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(i > 0 ? 1 : 0) + ' ' + units[i]
}

/** 状态标签映射 */
export function statusLabel(status) {
  const map = {
    'DRAFT': '草稿', 'PENDING_REVIEW': '待审核', 'ACTIVE': '已发布',
    'REJECTED': '已驳回', 'ARCHIVED': '已归档', 'MODIFYING': '修改中',
    'CONTRADICTED': '有矛盾', 'STALE': '已过期'
  }
  return map[status] || status
}

/** 格式化详情文本（转义 HTML 后换行转 <br>） */
export function formatDetail(text) {
  if (!text) return ''
  return escapeHtml(text).replace(/\n/g, '<br>').replace(/\t/g, '&nbsp;&nbsp;&nbsp;&nbsp;')
}

/** 文档类型标签 */
export function documentTypeLabel(type) {
  if (type === 'STANDARD' || type === 'PARAMETER_STANDARD') return '参数标准'
  if (type === 'ARTICLE') return '文章'
  if (type === 'MANUAL') return '手册'
  return '参数标准'
}
