export const TABLE_VIEWPORT_CLASS = 'doc-table-viewport'

// 文档预览的表格由 v-html / docx-preview 直接生成，Vue 模板无法包裹容器，
// 因此在内容渲染完成后统一为每张表格套一层横向滚动视口（样式见 documentTables.css）。
// 返回本次新包裹的表格数量，便于调用方按需判断。
export function enhanceDocumentTables(container) {
  if (!container || typeof container.querySelectorAll !== 'function') return 0
  const ownerDocument = container.ownerDocument || document
  let wrapped = 0
  container.querySelectorAll('table').forEach((table) => {
    const parent = table.parentElement
    // 已经包裹过的（含嵌套表格）不再重复处理
    if (!parent || table.closest?.(`.${TABLE_VIEWPORT_CLASS}`)) return
    const viewport = ownerDocument.createElement('div')
    viewport.className = TABLE_VIEWPORT_CLASS
    viewport.setAttribute('role', 'region')
    viewport.setAttribute('tabindex', '0')
    viewport.setAttribute('aria-label', '表格内容，可横向滚动查看全部列')
    parent.insertBefore(viewport, table)
    viewport.appendChild(table)
    wrapped += 1
  })
  return wrapped
}
