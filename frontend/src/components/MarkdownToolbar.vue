<template>
  <div class="md-toolbar" :class="{ disabled }">
    <button v-for="btn in buttons" :key="btn.label" :title="btn.label" :disabled="disabled" @click="btn.action">
      <span v-html="btn.icon" />
    </button>
  </div>
</template>

<script setup>
import { insertAtCursor } from '../editor-utils'

const props = defineProps({
  textarea: { type: Object, default: null },
  disabled: { type: Boolean, default: false }
})

function wrap(before, after, placeholder) {
  if (!props.textarea || props.disabled) return
  const ta = props.textarea
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const selected = ta.value.slice(start, end)
  const text = selected || placeholder
  insertAtCursor(ta, before + text + after)
  if (!selected) {
    ta.selectionStart = start + before.length
    ta.selectionEnd = start + before.length + placeholder.length
  }
  ta.focus()
}

function insertLine(prefix, placeholder) {
  if (!props.textarea || props.disabled) return
  const ta = props.textarea
  const start = ta.selectionStart
  const lineStart = ta.value.lastIndexOf('\n', start - 1) + 1
  const linePrefix = ta.value.slice(lineStart, start)
  const needsNewline = linePrefix.trim() !== '' ? '\n' : ''
  insertAtCursor(ta, needsNewline + prefix + placeholder)
  ta.focus()
}

function insertBlock(text) {
  if (!props.textarea || props.disabled) return
  const ta = props.textarea
  insertAtCursor(ta, '\n' + text + '\n')
  ta.focus()
}

const buttons = [
  { label: '粗体', icon: '<b>B</b>', action: () => wrap('**', '**', '粗体文字') },
  { label: '斜体', icon: '<i>I</i>', action: () => wrap('*', '*', '斜体文字') },
  { label: '删除线', icon: '<s>S</s>', action: () => wrap('~~', '~~', '删除线文字') },
  { label: '行内代码', icon: '<code>&lt;/&gt;</code>', action: () => wrap('`', '`', 'code') },
  { label: '一级标题', icon: 'H1', action: () => insertLine('# ', '标题') },
  { label: '二级标题', icon: 'H2', action: () => insertLine('## ', '标题') },
  { label: '三级标题', icon: 'H3', action: () => insertLine('### ', '标题') },
  { label: '无序列表', icon: '•', action: () => insertLine('- ', '列表项') },
  { label: '有序列表', icon: '1.', action: () => insertLine('1. ', '列表项') },
  { label: '引用', icon: '❝', action: () => insertLine('> ', '引用文字') },
  { label: '链接', icon: '🔗', action: () => wrap('[', '](https://)', '链接文字') },
  { label: '图片', icon: '🖼', action: () => insertAtCursor(props.textarea, '![alt](图片地址)') },
  { label: '代码块', icon: '{}', action: () => insertBlock('```\n代码\n```') },
  { label: '表格', icon: '▦', action: () => insertBlock('| 列A | 列B | 列C |\n|-----|-----|-----|\n| A1  | B1  | C1  |') },
  { label: '分隔线', icon: '—', action: () => insertBlock('---') },
]
</script>

<style scoped>
.md-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  padding: 6px 10px;
  border-bottom: 1px solid #e3e9f1;
  background: #f8f9fb;
  flex-shrink: 0;
}
.md-toolbar button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 28px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: none;
  cursor: pointer;
  font-size: 13px;
  color: #4a5568;
  transition: all 0.15s;
  padding: 0;
  min-height: unset;
}
.md-toolbar button:hover {
  background: #e3e9f1;
  border-color: #d1d9e0;
  color: #2356a5;
}
.md-toolbar button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.md-toolbar button :deep(code) {
  font-size: 11px;
  background: none;
  padding: 0;
}
.md-toolbar button :deep(b) { font-weight: 700; }
.md-toolbar button :deep(i) { font-style: italic; }
.md-toolbar button :deep(s) { text-decoration: line-through; font-size: 12px; }
.md-toolbar.disabled { opacity: 0.5; pointer-events: none; }
</style>
