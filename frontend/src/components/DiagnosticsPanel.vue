<template>
  <div class="workspace diagnostics-panel">
    <!-- 左侧：会话列表 + 知识库文档 -->
    <aside class="session-sidebar">
      <div class="session-header">
        <h3>排查会话</h3>
        <div class="agent-mode-toggle">
          <button :class="{ active: agentMode === 'rag' }" @click="agentMode = 'rag'" title="知识库检索排查">RAG</button>
          <button :class="{ active: agentMode === 'ops' }" @click="agentMode = 'ops'" title="Agent 自动排查（Skill + 工具调用）">Agent</button>
        </div>
        <button class="ghost" @click="createSession">新建会话</button>
      </div>
      <div class="session-list">
        <button
          v-for="session in sessions"
          :key="session.id"
          :class="['session-item', { active: currentSessionId === session.id }]"
          @click="switchSession(session.id)"
        >
          <span class="session-title">{{ session.title || '未命名会话' }}</span>
          <span class="session-time">{{ formatSessionTime(session.createdAt || session.updatedAt) }}</span>
        </button>
        <p v-if="sessions.length === 0" class="empty-hint">暂无会话，点击上方按钮新建。</p>
      </div>

      <!-- 知识库文档列表 -->
      <div class="kb-section">
        <button class="kb-toggle" @click="showKbDocs = !showKbDocs">
          {{ showKbDocs ? '收起' : '展开' }} 知识库文档 ({{ kbDocs.length }})
        </button>
        <div v-if="showKbDocs" class="kb-list">
          <div v-for="(doc, idx) in kbDocs" :key="idx" class="kb-item">
            <span class="kb-icon">{{ getDocIcon(doc.source_type) }}</span>
            <div class="kb-info">
              <span class="kb-title">{{ doc.source_title || '未知来源' }}</span>
              <span class="kb-meta">{{ doc.chunk_count }} 个切片</span>
            </div>
          </div>
          <p v-if="kbDocs.length === 0" class="empty-hint">知识库为空，请先导入文档。</p>
        </div>
      </div>
    </aside>

    <!-- 右侧：对话区域 -->
    <div class="chat-main">
      <div v-if="!currentSessionId && messages.length === 0 && !readyToSend" class="chat-placeholder">
        <div class="placeholder-content">
          <div class="placeholder-icon">诊</div>
          <h3>智能排查</h3>
          <p v-if="agentMode === 'ops'">Agent 模式：自动匹配排查 Skill，调用工具获取数据，综合分析给出结论。</p>
          <p v-else>RAG 模式：基于知识库检索，结合历史文档回答排查问题。</p>
          <button @click="createSession">新建排查会话</button>
        </div>
      </div>

      <template v-else>
        <!-- 消息区域 -->
        <div ref="chatContainer" class="chat-messages">
          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            :class="['message-row', msg.role === 'user' ? 'message-user' : 'message-ai']"
          >
            <div :class="['message-bubble', msg.role === 'user' ? 'bubble-user' : 'bubble-ai']">
              <!-- Agent 模式：显示匹配的 Skill -->
              <div v-if="msg.role === 'assistant' && msg.skill" class="skill-tag">
                <span class="skill-badge">Skill: {{ msg.skill }}</span>
              </div>
              <div class="message-text" v-html="renderMarkdown(msg.content)"></div>
              <!-- Agent 模式：显示使用的工具 -->
              <div v-if="msg.role === 'assistant' && msg.tools && msg.tools.length > 0" class="tools-tag">
                <span v-for="t in msg.tools" :key="t" class="tool-chip">{{ t }}</span>
              </div>
              <!-- 引用来源 -->
              <div v-if="msg.role === 'assistant' && msg.references && msg.references.length > 0" class="references-section">
                <button
                  class="references-toggle"
                  @click="toggleReferences(idx)"
                >
                  {{ expandedRefs[idx] ? '收起引用' : `查看引用 (${msg.references.length})` }}
                </button>
                <div v-if="expandedRefs[idx]" class="references-list">
                  <div v-for="(ref, rIdx) in msg.references" :key="rIdx" class="reference-item">
                    <span class="ref-source">{{ ref }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div v-if="sending" class="message-row message-ai">
            <div class="message-bubble bubble-ai">
              <div class="typing-indicator">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
          <div v-if="messages.length === 0" class="empty-messages">
            <p>会话为空，请输入问题开始排查。</p>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="chat-input-area">
          <textarea
            ref="inputRef"
            v-model="inputMessage"
            placeholder="输入排查问题，按 Enter 发送（Shift+Enter 换行）"
            rows="2"
            @keydown="handleKeydown"
          ></textarea>
          <button v-if="sending" class="stop-btn" @click="stopSending">停止</button>
          <button v-else :disabled="!inputMessage.trim()" @click="sendMessage">发送</button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { request } from '../api'
import MarkdownIt from 'markdown-it'

const props = defineProps({ auth: Object })

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true
})

const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const inputMessage = ref('')
const sending = ref(false)
const agentMode = ref('ops')
const chatContainer = ref(null)
const inputRef = ref(null)
const expandedRefs = ref({})
const readyToSend = ref(false)
const showKbDocs = ref(false)
const kbDocs = ref([])
let abortController = null

onMounted(() => {
  loadSessions()
  loadKbDocs()
})

async function loadSessions() {
  try {
    sessions.value = await request('/api/agent/sessions')
    if (!Array.isArray(sessions.value)) sessions.value = []
  } catch {
    sessions.value = []
  }
}

async function loadKbDocs() {
  try {
    const result = await request('/api/knowledge/docs')
    kbDocs.value = Array.isArray(result) ? result : []
  } catch {
    kbDocs.value = []
  }
}

async function switchSession(sessionId) {
  if (currentSessionId.value === sessionId) return
  currentSessionId.value = sessionId
  messages.value = []
  expandedRefs.value = {}
  readyToSend.value = true
  await loadMessages(sessionId)
}

async function loadMessages(sessionId) {
  try {
    const result = await request(`/api/agent/sessions/${sessionId}`)
    messages.value = Array.isArray(result) ? result : (result?.messages || result?.data || [])
  } catch {
    messages.value = []
  }
  scrollToBottom()
}

function createSession() {
  currentSessionId.value = null
  messages.value = []
  expandedRefs.value = {}
  readyToSend.value = true
  nextTick(() => { inputRef.value?.focus() })
}

async function sendMessage() {
  const text = inputMessage.value.trim()
  if (!text || sending.value) return

  messages.value.push({ role: 'user', content: text })
  inputMessage.value = ''
  sending.value = true
  scrollToBottom()

  abortController = new AbortController()

  try {
    let result
    if (agentMode.value === 'ops') {
      result = await request('/api/ops-agent/chat', {
        method: 'POST',
        body: { message: text, context: {} },
        signal: abortController.signal
      })
      if (result) {
        messages.value.push({
          role: 'assistant',
          content: result.response || '',
          skill: result.skill || null,
          tools: result.availableTools || []
        })
      }
    } else {
      result = await request('/api/agent/chat', {
        method: 'POST',
        body: { sessionId: currentSessionId.value, message: text },
        signal: abortController.signal
      })
      if (result) {
        if (result.sessionId && !currentSessionId.value) {
          currentSessionId.value = result.sessionId
        }
        messages.value.push({
          role: 'assistant',
          content: result.answer || result.content || '',
          references: result.references || []
        })
        await loadSessions()
      }
    }
  } catch (error) {
    if (error.name === 'AbortError') {
      messages.value.push({ role: 'assistant', content: '*（已停止）*' })
    } else {
      messages.value.push({ role: 'assistant', content: `请求失败：${error.message || '未知错误'}` })
    }
  } finally {
    sending.value = false
    abortController = null
    scrollToBottom()
  }
}

function stopSending() {
  if (abortController) {
    abortController.abort()
  }
}

function handleKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

function toggleReferences(idx) {
  expandedRefs.value = { ...expandedRefs.value, [idx]: !expandedRefs.value[idx] }
}

function formatSessionTime(time) {
  if (!time) return ''
  const str = String(time)
  return str.length >= 10 ? str.slice(0, 10) : str
}

function renderMarkdown(content) {
  if (!content) return ''
  let html = md.render(content)
  // 来源标签高亮
  html = html.replace(/【知识库：(.+?)】/g, '<span class="tag-kb">📚 知识库：$1</span>')
  html = html.replace(/【模型知识】/g, '<span class="tag-model">🧠 模型知识</span>')
  return html
}

function getDocIcon(sourceType) {
  if (sourceType === 'UPLOAD') return '📄'
  if (sourceType === 'STANDARD_DOC') return '📋'
  return '📁'
}
</script>

<style scoped>
.tag-kb {
  display: inline-block;
  background: #e8f4fd;
  color: #1a73e8;
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 3px;
  margin-left: 4px;
  font-weight: 500;
}

.tag-model {
  display: inline-block;
  background: #f0f0f0;
  color: #666;
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 3px;
  margin-left: 4px;
  font-weight: 500;
}

.diagnostics-panel {
  display: flex;
  height: 100%;
  padding: 0;
  overflow: hidden;
}

.session-sidebar {
  width: 280px;
  min-width: 280px;
  background: #f8fafc;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.session-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.session-header h3 {
  margin: 0;
  font-size: 15px;
  color: #1e293b;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  min-height: 0;
}

.session-item {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 10px 12px;
  border: none;
  background: transparent;
  text-align: left;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.session-item:hover { background: #e2e8f0; }
.session-item.active { background: #dbeafe; border-left: 3px solid #2356a5; }

.session-title {
  font-size: 13px;
  font-weight: 500;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time { font-size: 11px; color: #94a3b8; margin-top: 2px; }
.empty-hint { padding: 20px 12px; text-align: center; color: #94a3b8; font-size: 13px; }

/* 知识库文档列表 */
.kb-section {
  border-top: 1px solid #e2e8f0;
  max-height: 45%;
  overflow-y: auto;
  flex-shrink: 0;
}

.kb-toggle {
  width: 100%;
  padding: 10px 16px;
  background: none;
  border: none;
  text-align: left;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  font-weight: 500;
}

.kb-toggle:hover { background: #e2e8f0; }

.kb-list { padding: 0 8px 8px; }

.kb-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  font-size: 12px;
}

.kb-item:hover { background: #e2e8f0; }

.kb-icon { font-size: 16px; }
.kb-info { flex: 1; min-width: 0; }
.kb-title {
  display: block;
  color: #1e293b;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kb-meta { color: #94a3b8; font-size: 11px; }

/* 右侧对话区 */
.chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; overflow: hidden; }

.chat-placeholder { flex: 1; display: flex; align-items: center; justify-content: center; }
.placeholder-content { text-align: center; }
.placeholder-icon {
  width: 64px; height: 64px; border-radius: 16px;
  background: linear-gradient(135deg, #2356a5, #4f86c6);
  color: #fff; font-size: 28px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  margin: 0 auto 16px;
}
.placeholder-content h3 { margin: 0 0 8px; font-size: 18px; color: #1e293b; }
.placeholder-content p { margin: 0 0 16px; color: #64748b; font-size: 14px; }

.chat-messages {
  flex: 1; overflow-y: auto; padding: 20px;
  display: flex; flex-direction: column; gap: 16px;
  min-height: 0;
}

.empty-messages { flex: 1; display: flex; align-items: center; justify-content: center; color: #94a3b8; font-size: 14px; }

.message-row { display: flex; }
.message-user { justify-content: flex-end; }
.message-ai { justify-content: flex-start; }

.message-bubble {
  max-width: 75%; padding: 10px 14px; border-radius: 12px;
  font-size: 14px; line-height: 1.6; word-break: break-word;
}

.bubble-user { background: #2356a5; color: #fff; border-bottom-right-radius: 4px; }
.bubble-ai { background: #eef2f7; color: #1e293b; border-bottom-left-radius: 4px; }

/* Markdown 格式 */
.message-text :deep(h1),
.message-text :deep(h2),
.message-text :deep(h3) { margin: 8px 0 4px; font-size: 15px; }
.message-text :deep(p) { margin: 4px 0; }
.message-text :deep(ul),
.message-text :deep(ol) { margin: 4px 0; padding-left: 20px; }
.message-text :deep(code) {
  background: rgba(0,0,0,0.06); padding: 1px 4px; border-radius: 3px;
  font-size: 13px; font-family: 'Menlo', 'Consolas', monospace;
}
.message-text :deep(pre) {
  background: #1e293b; color: #e2e8f0; padding: 10px 12px;
  border-radius: 6px; overflow-x: auto; margin: 6px 0;
}
.message-text :deep(pre code) { background: none; color: inherit; padding: 0; }
.message-text :deep(table) { border-collapse: collapse; margin: 6px 0; font-size: 13px; }
.message-text :deep(th),
.message-text :deep(td) { border: 1px solid #d1d5db; padding: 4px 8px; }
.message-text :deep(th) { background: #f1f5f9; }
.message-text :deep(blockquote) { border-left: 3px solid #2356a5; margin: 6px 0; padding-left: 10px; color: #64748b; }

/* 打字动画 */
.typing-indicator { display: flex; gap: 4px; padding: 4px 0; }
.typing-indicator span {
  width: 7px; height: 7px; border-radius: 50%; background: #94a3b8;
  animation: typingBounce 1.4s infinite;
}
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typingBounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-5px); }
}

/* 引用 */
.skill-tag { margin-bottom: 6px; }
.skill-badge {
  display: inline-block; background: #dbeafe; color: #1e40af; font-size: 11px;
  padding: 2px 8px; border-radius: 10px; font-weight: 600;
}
.tools-tag { margin-top: 8px; display: flex; flex-wrap: wrap; gap: 4px; }
.tool-chip {
  display: inline-block; background: #f0fdf4; color: #166534; font-size: 11px;
  padding: 2px 8px; border-radius: 10px; border: 1px solid #bbf7d0;
}

.agent-mode-toggle { display: flex; gap: 2px; }
.agent-mode-toggle button {
  padding: 3px 10px; font-size: 12px; border: 1px solid #d1d5db;
  background: #fff; color: #64748b; border-radius: 4px; cursor: pointer;
}
.agent-mode-toggle button.active {
  background: #2356a5; color: #fff; border-color: #2356a5;
}

.references-section { margin-top: 8px; border-top: 1px solid #d1d5db; padding-top: 8px; }
.references-toggle { background: none; border: none; color: #2356a5; font-size: 12px; cursor: pointer; padding: 0; text-decoration: underline; }
.references-list { margin-top: 8px; display: flex; flex-direction: column; gap: 4px; }
.reference-item { background: rgba(255,255,255,0.6); border-radius: 6px; padding: 6px 10px; font-size: 12px; }
.ref-source { font-weight: 600; color: #1e293b; }

/* 输入区 */
.chat-input-area {
  border-top: 1px solid #e2e8f0; padding: 12px 20px;
  display: flex; gap: 10px; align-items: flex-end; background: #fff;
  flex-shrink: 0;
}

.chat-input-area textarea {
  flex: 1; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 8px;
  font-size: 14px; font-family: inherit; resize: none; line-height: 1.5;
}

.chat-input-area textarea:focus {
  outline: none; border-color: #2356a5;
  box-shadow: 0 0 0 2px rgba(35,86,165,0.15);
}

.chat-input-area button { padding: 10px 24px; white-space: nowrap; }
.stop-btn { background: #ef4444; color: #fff; border: none; border-radius: 6px; cursor: pointer; }
.stop-btn:hover { background: #dc2626; }
</style>
