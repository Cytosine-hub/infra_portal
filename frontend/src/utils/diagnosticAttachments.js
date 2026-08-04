export const MAX_DIAGNOSTIC_ATTACHMENTS = 5
export const MAX_DIAGNOSTIC_ATTACHMENT_SIZE = 10 * 1024 * 1024
export const MAX_DIAGNOSTIC_ATTACHMENTS_TOTAL_SIZE = 20 * 1024 * 1024

const ALLOWED_EXTENSIONS = new Set([
  'png', 'jpg', 'jpeg', 'webp', 'gif',
  'txt', 'log', 'md', 'json', 'yaml', 'yml', 'xml', 'csv',
  'pdf', 'doc', 'docx', 'xls', 'xlsx'
])

const IMAGE_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'webp', 'gif'])
const CLIPBOARD_IMAGE_EXTENSIONS = new Map([
  ['image/png', 'png'],
  ['image/jpeg', 'jpg'],
  ['image/webp', 'webp'],
  ['image/gif', 'gif']
])

export const DIAGNOSTIC_ATTACHMENT_ACCEPT = Array.from(ALLOWED_EXTENSIONS)
  .map(extension => `.${extension}`)
  .join(',')

function extensionOf(name) {
  const index = String(name || '').lastIndexOf('.')
  return index < 0 ? '' : name.slice(index + 1).toLowerCase()
}

function normalizeClipboardFile(file, index, timestamp) {
  if (!file || extensionOf(file.name) || !CLIPBOARD_IMAGE_EXTENSIONS.has(file.type)) {
    return file
  }
  const suffix = index === 0 ? '' : `-${index + 1}`
  const extension = CLIPBOARD_IMAGE_EXTENSIONS.get(file.type)
  return new File([file], `clipboard-image-${timestamp}${suffix}.${extension}`, {
    type: file.type,
    lastModified: file.lastModified
  })
}

export function diagnosticFilesFromClipboard(clipboardData, timestamp = Date.now()) {
  if (!clipboardData) return []
  const itemFiles = Array.from(clipboardData.items || [])
    .filter(item => item.kind === 'file')
    .map(item => item.getAsFile?.())
    .filter(Boolean)
  const files = itemFiles.length ? itemFiles : Array.from(clipboardData.files || [])
  return files.map((file, index) => normalizeClipboardFile(file, index, timestamp))
}

export function validateDiagnosticAttachments(existing, incoming) {
  const current = Array.from(existing || [])
  const selected = Array.from(incoming || [])
  if (current.length + selected.length > MAX_DIAGNOSTIC_ATTACHMENTS) {
    throw new Error('每次最多上传 5 个附件')
  }
  for (const file of selected) {
    if (!ALLOWED_EXTENSIONS.has(extensionOf(file.name))) {
      throw new Error(`不支持附件格式：${file.name}`)
    }
    if (file.size === 0) {
      throw new Error(`附件内容为空：${file.name}`)
    }
    if (file.size > MAX_DIAGNOSTIC_ATTACHMENT_SIZE) {
      throw new Error(`单个附件不能超过 10MB：${file.name}`)
    }
  }
  const totalSize = [...current, ...selected]
    .reduce((sum, item) => sum + (item.file?.size ?? item.size ?? 0), 0)
  if (totalSize > MAX_DIAGNOSTIC_ATTACHMENTS_TOTAL_SIZE) {
    throw new Error('附件总大小不能超过 20MB')
  }
}

export function createDiagnosticAttachment(file) {
  const extension = extensionOf(file.name)
  const kind = IMAGE_EXTENSIONS.has(extension) ? 'image' : 'document'
  return {
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    file,
    name: file.name,
    contentType: file.type || 'application/octet-stream',
    size: file.size,
    kind,
    previewUrl: kind === 'image' ? URL.createObjectURL(file) : null
  }
}

export function parseAttachmentMetadata(value) {
  if (!value) return []
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function formatAttachmentSize(size) {
  const bytes = Number(size) || 0
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
