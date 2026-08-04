import { onBeforeUnmount, ref } from 'vue'

import {
  createDiagnosticAttachment,
  diagnosticFilesFromClipboard,
  validateDiagnosticAttachments
} from '../utils/diagnosticAttachments'

export function useDiagnosticAttachmentInput(notify) {
  const attachmentInput = ref(null)
  const selectedAttachments = ref([])
  const isDraggingFiles = ref(false)
  let dragDepth = 0

  function addAttachments(files) {
    const incoming = Array.from(files || []).filter(Boolean)
    if (!incoming.length) return false
    try {
      validateDiagnosticAttachments(selectedAttachments.value, incoming)
      selectedAttachments.value.push(...incoming.map(createDiagnosticAttachment))
      return true
    } catch (error) {
      notify?.(error.message, 'error')
      return false
    }
  }

  function handleAttachmentSelection(event) {
    addAttachments(event.target.files)
    event.target.value = ''
  }

  function handlePaste(event) {
    const files = diagnosticFilesFromClipboard(event.clipboardData)
    if (!files.length) return
    event.preventDefault()
    addAttachments(files)
  }

  function hasDraggedFiles(dataTransfer) {
    return Array.from(dataTransfer?.types || []).includes('Files')
      || Boolean(dataTransfer?.files?.length)
  }

  function handleDragEnter(event) {
    if (!hasDraggedFiles(event.dataTransfer)) return
    event.preventDefault()
    dragDepth += 1
    isDraggingFiles.value = true
  }

  function handleDragOver(event) {
    if (!hasDraggedFiles(event.dataTransfer)) return
    event.preventDefault()
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
  }

  function handleDragLeave(event) {
    if (!isDraggingFiles.value) return
    event.preventDefault()
    dragDepth = Math.max(0, dragDepth - 1)
    if (dragDepth === 0) isDraggingFiles.value = false
  }

  function handleDrop(event) {
    if (!hasDraggedFiles(event.dataTransfer)) return
    event.preventDefault()
    const files = Array.from(event.dataTransfer?.files || [])
    resetDragState()
    addAttachments(files)
  }

  function removeAttachment(id) {
    const index = selectedAttachments.value.findIndex(attachment => attachment.id === id)
    if (index < 0) return
    const [removed] = selectedAttachments.value.splice(index, 1)
    if (removed.previewUrl) URL.revokeObjectURL(removed.previewUrl)
  }

  function revokeAttachmentPreviews(attachments) {
    for (const attachment of attachments || []) {
      if (attachment.previewUrl) URL.revokeObjectURL(attachment.previewUrl)
    }
  }

  function clearPendingAttachments() {
    revokeAttachmentPreviews(selectedAttachments.value)
    selectedAttachments.value = []
  }

  function resetDragState() {
    dragDepth = 0
    isDraggingFiles.value = false
  }

  onBeforeUnmount(() => {
    resetDragState()
    clearPendingAttachments()
  })

  return {
    attachmentInput,
    selectedAttachments,
    isDraggingFiles,
    handleAttachmentSelection,
    handlePaste,
    handleDragEnter,
    handleDragOver,
    handleDragLeave,
    handleDrop,
    removeAttachment,
    clearPendingAttachments,
    revokeAttachmentPreviews
  }
}
