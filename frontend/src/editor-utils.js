import { request } from './api'

export function insertAtCursor(textarea, text) {
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  textarea.value = textarea.value.slice(0, start) + text + textarea.value.slice(end)
  const newCursor = start + text.length
  textarea.selectionStart = newCursor
  textarea.selectionEnd = newCursor
  textarea.dispatchEvent(new Event('input', { bubbles: true }))
}

function expandTableShorthand(ta) {
  const start = ta.selectionStart
  const before = ta.value.slice(0, start)
  const match = before.match(/\[(\d+)\]\[(\d+)\]$/)
  if (!match) return false

  const rows = parseInt(match[1], 10)
  const cols = parseInt(match[2], 10)
  if (rows < 1 || cols < 1 || rows > 10 || cols > 10) return false

  const prefixEnd = start - match[0].length
  let prefix = ta.value.slice(0, prefixEnd)
  const suffix = ta.value.slice(start)

  // 确保表格前有空行，markdown 才能正确解析
  if (prefix.length > 0 && !prefix.endsWith('\n\n')) {
    prefix = prefix.endsWith('\n') ? prefix + '\n' : prefix + '\n\n'
  }

  const lines = []
  lines.push('| ' + Array(cols).fill('Header').join(' | ') + ' |')
  lines.push('|' + Array(cols).fill('----------').join('|') + '|')
  for (let r = 1; r < rows; r++) {
    lines.push('| ' + Array(cols).fill('Cell').join(' | ') + ' |')
  }
  lines.push('')

  ta.value = prefix + lines.join('\n') + suffix
  ta.selectionStart = ta.selectionEnd = prefix.length + lines.join('\n').length
  ta.dispatchEvent(new Event('input', { bubbles: true }))
  return true
}

export function handleEditorKeydown(event) {
  if (event.key === ' ' || event.key === 'Enter') {
    if (expandTableShorthand(event.target)) {
      event.preventDefault()
      return
    }
  }

  if (event.key === 'Enter') {
    const ta = event.target
    const start = ta.selectionStart
    const lineStart = ta.value.lastIndexOf('\n', start - 1) + 1
    const currentLine = ta.value.slice(lineStart, start)
    const match = currentLine.match(/^(\s*)([-*+]|\d+\.)\s+(.*)$/)
    if (match) {
      event.preventDefault()
      const indent = match[1]
      const prefix = match[2]
      const rest = match[3]
      if (rest === '') {
        // 空列表项 → 结束列表
        ta.value = ta.value.slice(0, lineStart) + '\n' + ta.value.slice(start)
        ta.selectionStart = ta.selectionEnd = lineStart + 1
        ta.dispatchEvent(new Event('input', { bubbles: true }))
      } else {
        insertAtCursor(ta, '\n' + indent + prefix + ' ')
      }
      return
    }
  }

  if (event.key === 'Tab') {
    event.preventDefault()
    insertAtCursor(event.target, '    ')
  }
}

export async function handleEditorPaste(event, onError) {
  const items = event.clipboardData?.items
  if (!items) return

  for (const item of items) {
    if (item.type.startsWith('image/')) {
      event.preventDefault()
      const file = item.getAsFile()
      if (!file) continue

      try {
        const formData = new FormData()
        formData.append('file', file)
        const result = await request('/api/admin/images/upload', { method: 'POST', body: formData })
        insertAtCursor(event.target, `![](${result.url})`)
      } catch (err) {
        const msg = '图片上传失败: ' + (err.message || '未知错误')
        if (onError) onError(msg); else alert(msg)
      }
      return
    }
  }
}
