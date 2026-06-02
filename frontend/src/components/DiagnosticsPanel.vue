<template>
  <div class="workspace diagnostics-panel">
    <!-- 左侧：会话列表 + 知识库文档 -->
    <aside class="session-sidebar">
      <div class="session-header">
        <h3>排查会话</h3>
        <button class="ghost" @click="createSession">新建会话</button>
      </div>
      <div class="session-list">
        <button
          v-for="session in displaySessions"
          :key="session.id"
          :class="['session-item', { active: currentSessionId === session.id }]"
          @click="switchSession(session.id)"
        >
          <span v-if="session.mode === 'ops'" class="session-mode-tag agent">Agent</span>
          <span class="session-title">{{ session.title || '未命名会话' }}</span>
          <span class="session-time">{{ formatSessionTime(session.createdAt || session.updatedAt) }}</span>
        </button>
        <p v-if="displaySessions.length === 0" class="empty-hint">暂无会话，点击上方按钮新建。</p>
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

      <!-- Skill 管理 -->
      <div class="skill-section">
        <button class="skill-toggle" @click="showSkillPanel = !showSkillPanel">
          {{ showSkillPanel ? '收起' : '展开' }} Skill 管理 ({{ skills.length }})
        </button>
        <div v-if="showSkillPanel" class="skill-list">
          <div
            v-for="skill in skills"
            :key="skill.name"
            class="skill-item"
            @click="viewSkill(skill)"
          >
            <div class="skill-item-head">
              <span class="skill-item-name">{{ skill.name }}</span>
              <div class="skill-item-actions">
                <button class="ghost" @click.stop="startEditSkill(skill)">编辑</button>
                <button class="ghost danger" @click.stop="confirmDeleteSkill(skill)">删除</button>
              </div>
            </div>
            <div class="skill-item-desc">{{ skill.description || '暂无描述' }}</div>
            <div class="skill-item-meta">
              <span>关键词: {{ (skill.trigger?.keywords || []).join('/') || '-' }}</span>
              <span>{{ (skill.steps || []).length }} 步骤</span>
            </div>
          </div>
          <p v-if="skills.length === 0" class="empty-hint">暂无 Skill，点击下方按钮新增。</p>
          <button class="skill-new-btn" @click="startCreateSkill">+ 新增 Skill</button>
        </div>
      </div>

      <!-- 删除确认 -->
      <div v-if="deleteSkillTarget" class="confirm-overlay" @click.self="deleteSkillTarget = null">
        <div class="confirm-dialog">
          <p>确认删除 Skill <strong>{{ deleteSkillTarget.name }}</strong>？</p>
          <div class="confirm-actions">
            <button @click="deleteSkill(deleteSkillTarget.name)">确认</button>
            <button class="ghost" @click="deleteSkillTarget = null">取消</button>
          </div>
        </div>
      </div>

      <!-- 保存经验弹窗 -->
      <div v-if="saveExpTarget" class="confirm-overlay" @click.self="saveExpTarget = null">
        <div class="confirm-dialog" style="min-width:380px;max-width:480px">
          <p style="font-weight:600;font-size:15px;margin-bottom:12px">保存排查经验</p>
          <div class="skill-form-group">
            <label>Skill 名称</label>
            <input v-model.trim="saveExpForm.name" placeholder="如 tomcat-cpu-troubleshoot" />
          </div>
          <div class="skill-form-group">
            <label>描述</label>
            <input v-model.trim="saveExpForm.description" placeholder="简述排查场景" />
          </div>
          <div class="skill-form-group">
            <label>触发关键词（逗号分隔）</label>
            <input v-model.trim="saveExpForm.keywords" placeholder="如 tomcat,CPU高,CPU飙高" />
          </div>
          <div class="confirm-actions" style="margin-top:16px">
            <button @click="submitSaveExperience">保存</button>
            <button class="ghost" @click="saveExpTarget = null">取消</button>
          </div>
        </div>
      </div>
    </aside>

    <!-- 右侧：对话区域 / Skill 编辑 -->
    <div class="chat-main">
      <!-- Skill 查看模式 -->
      <template v-if="skillPanelMode === 'skillView' && editingSkill">
        <div class="skill-edit-panel">
          <div class="skill-edit-header">
            <h3>{{ editingSkill.name }}</h3>
            <div style="display:flex;gap:8px">
              <button class="ghost" @click="startEditSkill(editingSkill)">编辑</button>
              <button class="ghost" @click="cancelSkillEdit">返回</button>
            </div>
          </div>
          <div class="skill-detail-section">
            <h4>描述</h4>
            <p>{{ editingSkill.description || '暂无描述' }}</p>
          </div>
          <div class="skill-detail-section">
            <h4>触发关键词</h4>
            <div class="skill-detail-keywords">
              <span v-for="kw in (editingSkill.trigger?.keywords || [])" :key="kw" class="keyword-chip">{{ kw }}</span>
              <span v-if="!editingSkill.trigger?.keywords?.length" class="empty-hint" style="padding:0">无关键词</span>
            </div>
          </div>
          <div class="skill-detail-section">
            <h4>执行步骤</h4>
            <div v-for="(step, idx) in (editingSkill.steps || [])" :key="idx" class="skill-detail-step">
              <div class="skill-detail-step-head">
                <span class="step-number">步骤 {{ idx + 1 }}</span>
                <span v-if="step.tool" class="step-tool-badge">Tool: {{ step.tool }}</span>
                <span v-else class="step-prompt-badge">LLM Prompt</span>
              </div>
              <p style="font-size:13px;margin:4px 0">{{ step.description || '暂无描述' }}</p>
              <div v-if="step.tool && step.args" style="font-size:12px;color:#64748b;margin-top:4px">
                参数: {{ Object.entries(step.args).map(([k,v]) => k + '=' + v).join(', ') }}
              </div>
              <div v-if="step.prompt" style="font-size:12px;color:#64748b;margin-top:4px;white-space:pre-wrap">{{ step.prompt }}</div>
            </div>
            <p v-if="!(editingSkill.steps || []).length" class="empty-hint" style="padding:0">无执行步骤</p>
          </div>
        </div>
      </template>

      <!-- Skill 编辑模式 -->
      <template v-else-if="skillPanelMode === 'skillEdit'">
        <div class="skill-edit-panel">
          <div class="skill-edit-header">
            <h3>{{ editingSkill ? '编辑 Skill' : '新增 Skill' }}</h3>
            <button class="ghost" @click="cancelSkillEdit">取消</button>
          </div>

          <!-- 基本信息 -->
          <div class="skill-form-group">
            <label>名称 (唯一标识)</label>
            <input v-model.trim="skillForm.name" :disabled="!!editingSkill" placeholder="例如 network_diagnosis" />
          </div>
          <div class="skill-form-group">
            <label>功能描述</label>
            <textarea v-model.trim="skillForm.description" placeholder="描述该 Skill 的用途" rows="2"></textarea>
          </div>

          <!-- 触发关键词 -->
          <div class="skill-form-group">
            <label>触发关键词</label>
            <div class="keyword-chips">
              <span v-for="(kw, idx) in skillForm.triggerKeywords" :key="idx" class="keyword-chip">
                {{ kw }}
                <button @click="removeSkillKeyword(idx)">&times;</button>
              </span>
            </div>
            <div class="keyword-add-row">
              <input
                v-model="newKeyword"
                placeholder="输入关键词后按添加"
                @keydown.enter.prevent="addSkillKeyword"
              />
              <button class="ghost" @click="addSkillKeyword">添加</button>
            </div>
          </div>

          <!-- 执行步骤 -->
          <div class="skill-form-group">
            <label>执行步骤</label>
            <div v-for="(step, idx) in skillForm.steps" :key="idx" class="step-card">
              <div class="step-header">
                <span class="step-number">步骤 {{ idx + 1 }}</span>
                <div class="step-actions">
                  <button v-if="idx > 0" class="ghost" @click="moveSkillStep(idx, -1)">上移</button>
                  <button v-if="idx < skillForm.steps.length - 1" class="ghost" @click="moveSkillStep(idx, 1)">下移</button>
                  <button class="ghost danger" @click="removeSkillStep(idx)">删除</button>
                </div>
              </div>
              <div class="step-type-toggle">
                <button :class="{ active: step.tool !== null }" @click="step.tool = step.tool || ''; step.prompt = null">Tool 调用</button>
                <button :class="{ active: step.prompt !== null }" @click="step.prompt = step.prompt || ''; step.tool = null">LLM 提示词</button>
              </div>
              <div class="skill-form-group" style="margin-bottom:8px">
                <input v-model.trim="step.description" placeholder="步骤描述（可选）" />
              </div>
              <!-- Tool 模式 -->
              <template v-if="step.tool !== null">
                <div class="skill-form-group" style="margin-bottom:8px">
                  <select v-model="step.tool">
                    <option value="">选择 Tool</option>
                    <option v-for="t in availableTools" :key="t.name" :value="t.name">{{ t.name }} - {{ t.description || '' }}</option>
                  </select>
                </div>
                <div class="skill-form-group" style="margin-bottom:0">
                  <label style="font-size:11px">参数 (key=value，支持变量模板)</label>
                  <div v-for="(val, key) in step.args" :key="key" class="arg-row" style="margin-bottom:4px">
                    <input :value="key" disabled style="background:#f1f5f9" />
                    <input v-model="step.args[key]" placeholder="变量或固定值" />
                    <button class="ghost danger" @click="removeStepArg(idx, key)">删</button>
                  </div>
                  <div class="arg-row">
                    <input v-model="newArgKey[idx]" placeholder="参数名" />
                    <button class="ghost" @click="addStepArg(idx)">添加参数</button>
                  </div>
                </div>
              </template>
              <!-- Prompt 模式 -->
              <template v-if="step.prompt !== null">
                <div class="skill-form-group" style="margin-bottom:0">
                  <textarea v-model="step.prompt" placeholder="LLM 提示词，支持 {{'{{'}}variable{{'}}'}} 模板" rows="3"></textarea>
                </div>
              </template>
            </div>
            <button class="skill-new-btn" @click="addSkillStep">+ 添加步骤</button>
          </div>

          <div class="skill-form-actions">
            <button @click="saveSkill">保存</button>
            <button class="ghost" @click="cancelSkillEdit">取消</button>
          </div>
        </div>
      </template>

      <!-- 聊天模式（默认） -->
      <template v-else>
        <div v-if="!currentSessionId && messages.length === 0 && !readyToSend" class="chat-placeholder">
          <div class="placeholder-content">
            <div class="placeholder-icon">诊</div>
            <h3>智能排查</h3>
            <p>输入问题开始排查，支持 RAG 知识库检索和 Agent 自动排查两种模式。</p>
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
                <!-- Agent 模式：保存为经验 -->
                <div v-if="msg.role === 'assistant' && msg.tools && msg.tools.length > 0 && !msg._savedExp" class="save-exp-row">
                  <button class="save-exp-btn" @click="openSaveExperience(msg, idx)">保存为经验</button>
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
            <div class="input-box">
              <textarea
                ref="inputRef"
                v-model="inputMessage"
                placeholder="输入排查问题，按 Enter 发送（Shift+Enter 换行）"
                rows="2"
                @keydown="handleKeydown"
              ></textarea>
              <div class="input-bottom-bar">
                <button
                  :class="['agent-toggle-btn', { active: agentMode === 'ops' }]"
                  @click="toggleAgentMode"
                  title="开启后自动匹配排查 Skill，调用工具分析"
                >
                  Agent
                </button>
              </div>
            </div>
            <button v-if="sending" class="stop-btn" @click="stopSending">停止</button>
            <button v-else :disabled="!inputMessage.trim()" @click="sendMessage">发送</button>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { request, getSavedAuth, clearAuth } from '../api'
import MarkdownIt from 'markdown-it'

const props = defineProps({ auth: Object })

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true
})

const ragSessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const inputMessage = ref('')
const sending = ref(false)
const agentMode = ref('rag')
const chatContainer = ref(null)
const inputRef = ref(null)
const expandedRefs = ref({})
const readyToSend = ref(false)
const showKbDocs = ref(false)
const kbDocs = ref([])
let abortController = null

// ========== Skill 管理 ==========
const showSkillPanel = ref(false)
const skills = ref([])
const availableTools = ref([])
const skillPanelMode = ref('chat') // 'chat' | 'skillEdit' | 'skillView'
const editingSkill = ref(null)
const skillForm = reactive({
  name: '',
  description: '',
  triggerKeywords: [],
  steps: []
})
const newKeyword = ref('')
const newArgKey = ref({})

// ========== 保存经验 ==========
const saveExpTarget = ref(null)
const saveExpIdx = ref(null)
const saveExpForm = reactive({ name: '', description: '', keywords: '' })
const deleteSkillTarget = ref(null)

const displaySessions = computed(() => {
  return ragSessions.value
})

onMounted(() => {
  loadSessions()
  loadKbDocs()
  loadSkills()
  loadAvailableTools()
})

async function loadSessions() {
  try {
    const list = await request('/api/agent/sessions')
    const all = Array.isArray(list) ? list : []
    all.sort((a, b) => new Date(b.updatedAt || b.createdAt) - new Date(a.updatedAt || a.createdAt))
    ragSessions.value = all
  } catch {
    ragSessions.value = []
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
    const session = ragSessions.value.find(s => s.id === sessionId)
    const isOps = session?.mode === 'ops'

    // 两个 API 共享同一条数据，用哪个都行，统一用 /api/agent/sessions/{id}
    const result = await request(`/api/agent/sessions/${sessionId}`)
    messages.value = Array.isArray(result) ? result : (result?.messages || result?.data || [])

    // 切换底部 Agent 按钮状态
    agentMode.value = isOps ? 'ops' : 'rag'
  } catch {
    messages.value = []
  }
  scrollToBottom()
}

async function toggleAgentMode() {
  const newMode = agentMode.value === 'ops' ? 'rag' : 'ops'
  agentMode.value = newMode

  try {
    if (currentSessionId.value) {
      // 已有会话：同步模式到数据库
      await request(`/api/agent/sessions/${currentSessionId.value}/mode`, {
        method: 'PATCH',
        body: { mode: newMode }
      })
    } else {
      // 新建会话：立即创建 session 以便列表显示
      const session = await request('/api/agent/sessions', {
        method: 'POST',
        body: { mode: newMode }
      })
      if (session?.id) {
        currentSessionId.value = session.id
      }
    }
    await loadSessions()
  } catch (e) {
    console.error('Failed to toggle agent mode:', e)
    alert('切换 Agent 模式失败：' + (e.message || '未知错误'))
  }
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

  // 重试提示消息的索引（用于更新或移除）
  let retryMsgIdx = -1

  try {
    const url = agentMode.value === 'ops' ? '/api/ops-agent/chat' : '/api/agent/chat'
    const body = agentMode.value === 'ops'
      ? { sessionId: currentSessionId.value, message: text, context: {} }
      : { sessionId: currentSessionId.value, message: text }

    const auth = getSavedAuth()
    const headers = { 'Content-Type': 'application/json' }
    if (auth?.token) headers['Authorization'] = `Bearer ${auth.token}`

    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
      signal: abortController.signal
    })

    if (!response.ok) {
      if (response.status === 401) {
        clearAuth()
        window.location.hash = '#/login'
        return
      }
      let errMsg = response.statusText
      try { const p = await response.json(); errMsg = p.error || p.message || errMsg } catch {}
      throw Object.assign(new Error(errMsg), { status: response.status })
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = { type: '', data: '' }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent.type = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          currentEvent.data = line.slice(5).trim()
        } else if (line === '' && currentEvent.data) {
          const data = JSON.parse(currentEvent.data)
          currentEvent = { type: '', data: '' }

          if (data.message) {
            if (retryMsgIdx >= 0) {
              messages.value[retryMsgIdx].content = `⏳ ${data.message}`
            } else {
              retryMsgIdx = messages.value.length
              messages.value.push({ role: 'assistant', content: `⏳ ${data.message}` })
            }
            scrollToBottom()
          } else if (data.error) {
            if (retryMsgIdx >= 0) {
              messages.value[retryMsgIdx].content = data.retryFailed
                ? `⚠️ ${data.error}`
                : `请求失败：${data.error}`
            } else {
              messages.value.push({
                role: 'assistant',
                content: data.retryFailed ? `⚠️ ${data.error}` : `请求失败：${data.error}`
              })
            }
          } else {
            if (retryMsgIdx >= 0) {
              messages.value.splice(retryMsgIdx, 1)
              retryMsgIdx = -1
            }
            if (agentMode.value === 'ops') {
              messages.value.push({
                role: 'assistant',
                content: data.response || '',
                skill: data.skill || null,
                tools: data.toolsUsed || []
              })
            } else {
              messages.value.push({
                role: 'assistant',
                content: data.answer || data.content || '',
                references: data.references || []
              })
            }
            if (data.sessionId && !currentSessionId.value) {
              currentSessionId.value = data.sessionId
            }
            await loadSessions()
          }
        }
      }
    }
  } catch (error) {
    if (error.name === 'AbortError') {
      if (retryMsgIdx >= 0) messages.value.splice(retryMsgIdx, 1)
      messages.value.push({ role: 'assistant', content: '*（已停止）*' })
    } else if (error.status === 503) {
      if (retryMsgIdx >= 0) {
        messages.value[retryMsgIdx].content = `⚠️ ${error.message || '模型响应超时，请稍后再试'}`
      } else {
        messages.value.push({ role: 'assistant', content: `⚠️ ${error.message || '模型响应超时，请稍后再试'}` })
      }
    } else {
      if (retryMsgIdx >= 0) messages.value.splice(retryMsgIdx, 1)
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
  html = html.replace(/【知识库：(.+?)】/g, '<span class="tag-kb">📚 知识库：$1</span>')
  html = html.replace(/【模型知识】/g, '<span class="tag-model">🧠 模型知识</span>')
  return html
}

function getDocIcon(sourceType) {
  if (sourceType === 'UPLOAD') return '📄'
  if (sourceType === 'STANDARD_DOC') return '📋'
  return '📁'
}

// ========== Skill 管理方法 ==========
async function loadSkills() {
  try {
    const list = await request('/api/ops-agent/skills')
    skills.value = Array.isArray(list) ? list : []
  } catch {
    skills.value = []
  }
}

async function loadAvailableTools() {
  try {
    const list = await request('/api/ops-agent/tools')
    availableTools.value = Array.isArray(list) ? list : []
  } catch {
    availableTools.value = []
  }
}

function startCreateSkill() {
  editingSkill.value = null
  skillForm.name = ''
  skillForm.description = ''
  skillForm.triggerKeywords = []
  skillForm.steps = []
  newKeyword.value = ''
  newArgKey.value = {}
  skillPanelMode.value = 'skillEdit'
}

function startEditSkill(skill) {
  editingSkill.value = skill
  skillForm.name = skill.name || ''
  skillForm.description = skill.description || ''
  skillForm.triggerKeywords = [...(skill.trigger?.keywords || [])]
  skillForm.steps = (skill.steps || []).map(s => ({
    tool: s.tool || null,
    args: s.args ? { ...s.args } : {},
    prompt: s.prompt || null,
    description: s.description || ''
  }))
  newKeyword.value = ''
  newArgKey.value = {}
  skillPanelMode.value = 'skillEdit'
}

function viewSkill(skill) {
  editingSkill.value = skill
  skillPanelMode.value = 'skillView'
}

function cancelSkillEdit() {
  skillPanelMode.value = 'chat'
  editingSkill.value = null
}

function buildSkillPayload() {
  return {
    name: skillForm.name,
    description: skillForm.description,
    trigger: {
      keywords: [...skillForm.triggerKeywords]
    },
    steps: skillForm.steps.map(s => {
      const step = { description: s.description || '' }
      if (s.tool !== null) {
        step.tool = s.tool
        if (s.args && Object.keys(s.args).length > 0) {
          step.args = { ...s.args }
        }
      } else {
        step.prompt = s.prompt
      }
      return step
    })
  }
}

async function saveSkill() {
  const payload = buildSkillPayload()
  if (!payload.name) return
  try {
    await request('/api/ops-agent/skills', {
      method: 'POST',
      body: payload
    })
    await loadSkills()
    cancelSkillEdit()
  } catch (error) {
    console.error('Failed to save skill:', error)
  }
}

async function deleteSkill(name) {
  try {
    await request(`/api/ops-agent/skills/${encodeURIComponent(name)}`, {
      method: 'DELETE'
    })
    await loadSkills()
    if (editingSkill.value?.name === name) {
      cancelSkillEdit()
    }
    deleteSkillTarget.value = null
  } catch (error) {
    console.error('Failed to delete skill:', error)
  }
}

function confirmDeleteSkill(skill) {
  deleteSkillTarget.value = skill
}

function addSkillKeyword() {
  const kw = newKeyword.value.trim()
  if (kw && !skillForm.triggerKeywords.includes(kw)) {
    skillForm.triggerKeywords.push(kw)
  }
  newKeyword.value = ''
}

function removeSkillKeyword(idx) {
  skillForm.triggerKeywords.splice(idx, 1)
}

function addSkillStep() {
  skillForm.steps.push({
    tool: '',
    args: {},
    prompt: null,
    description: ''
  })
}

function removeSkillStep(idx) {
  skillForm.steps.splice(idx, 1)
}

function moveSkillStep(idx, dir) {
  const target = idx + dir
  if (target < 0 || target >= skillForm.steps.length) return
  const tmp = skillForm.steps[idx]
  skillForm.steps[idx] = skillForm.steps[target]
  skillForm.steps[target] = tmp
  skillForm.steps.splice(0, 0) // trigger reactivity
}

function addStepArg(stepIdx) {
  const key = (newArgKey.value[stepIdx] || '').trim()
  if (key && !skillForm.steps[stepIdx].args[key]) {
    skillForm.steps[stepIdx].args[key] = ''
  }
  newArgKey.value[stepIdx] = ''
}

function removeStepArg(stepIdx, key) {
  delete skillForm.steps[stepIdx].args[key]
}

// ========== 保存经验 ==========
function openSaveExperience(msg, idx) {
  saveExpIdx.value = idx
  // 从当前会话标题推断名称
  const session = ragSessions.value.find(s => s.id === currentSessionId.value)
  const titleHint = session?.title || ''
  saveExpForm.name = titleHint ? titleHint.replace(/[^a-zA-Z0-9一-鿿]/g, '-').substring(0, 30) : ''
  saveExpForm.description = titleHint || ''
  saveExpForm.keywords = ''
  saveExpTarget.value = msg
}

async function submitSaveExperience() {
  if (!saveExpForm.name) { alert('请输入 Skill 名称'); return }
  const toolsUsed = saveExpTarget.value?.tools || []
  // 构建步骤：每个工具一步 + 最后一步综合分析
  const steps = toolsUsed.map(t => ({
    tool: t,
    description: '调用 ' + t
  }))
  steps.push({ prompt: '根据以上排查数据，给出根因分析和修复建议。', description: '综合分析' })

  try {
    await request('/api/ops-agent/experience', {
      method: 'POST',
      body: {
        name: saveExpForm.name,
        description: saveExpForm.description,
        trigger: {
          keywords: saveExpForm.keywords.split(/[,，]/).map(k => k.trim()).filter(Boolean)
        },
        steps
      }
    })
    // 标记该消息已保存
    saveExpTarget.value._savedExp = true
    saveExpTarget.value = null
    await loadSkills()
    alert('经验已保存')
  } catch (e) {
    alert('保存失败: ' + (e.message || '未知错误'))
  }
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
  align-items: center;
  gap: 6px;
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

.session-mode-tag {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  font-weight: 600;
  flex-shrink: 0;
}
.session-mode-tag.agent { background: #dbeafe; color: #1e40af; }

.session-title {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.session-time { font-size: 11px; color: #94a3b8; flex-shrink: 0; }
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

/* Skill 和工具标签 */
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

.save-exp-row { margin-top: 8px; }
.save-exp-btn {
  background: none; border: 1px solid #2356a5; color: #2356a5;
  font-size: 11px; padding: 3px 10px; border-radius: 10px; cursor: pointer;
  transition: all 0.15s;
}
.save-exp-btn:hover { background: #2356a5; color: #fff; }

/* 引用 */
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


.input-box {
  flex: 1;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.input-box:focus-within {
  border-color: #2356a5;
  box-shadow: 0 0 0 2px rgba(35,86,165,0.15);
}

.input-box textarea {
  width: 100%;
  padding: 10px 12px 4px;
  border: none;
  font-size: 14px;
  font-family: inherit;
  resize: none;
  line-height: 1.5;
  outline: none;
  box-sizing: border-box;
}

.input-bottom-bar {
  display: flex;
  align-items: center;
  padding: 2px 8px 6px;
}

.agent-toggle-btn {
  padding: 2px 10px; font-size: 11px; font-weight: 600;
  border: 1px solid #e2e8f0; background: #f8fafc; color: #94a3b8;
  border-radius: 10px; cursor: pointer; transition: all 0.15s;
  line-height: 1.4;
}
.agent-toggle-btn:hover { border-color: #2356a5; color: #2356a5; }
.agent-toggle-btn.active {
  background: #2356a5; color: #fff; border-color: #2356a5;
}

.chat-input-area button { padding: 10px 24px; white-space: nowrap; }
.stop-btn { background: #ef4444; color: #fff; border: none; border-radius: 6px; cursor: pointer; }
.stop-btn:hover { background: #dc2626; }

/* ========== Skill 管理 - 左侧列表 ========== */
.skill-section {
  border-top: 1px solid #e2e8f0;
  flex-shrink: 0;
  max-height: 45%;
  overflow-y: auto;
}

.skill-toggle {
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
.skill-toggle:hover { background: #e2e8f0; }

.skill-list { padding: 0 8px 8px; }

.skill-item {
  display: flex;
  flex-direction: column;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.skill-item:hover { background: #e2e8f0; }

.skill-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.skill-item-name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skill-item-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.skill-item-actions button {
  padding: 2px 6px;
  font-size: 11px;
  border-radius: 4px;
}

.skill-item-desc {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skill-item-meta {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  font-size: 11px;
  color: #94a3b8;
}

.skill-new-btn {
  width: 100%;
  padding: 8px;
  font-size: 12px;
  color: #2356a5;
  background: none;
  border: 1px dashed #cbd5e1;
  border-radius: 6px;
  cursor: pointer;
  margin-top: 4px;
}
.skill-new-btn:hover {
  background: #dbeafe;
  border-color: #2356a5;
}

/* 删除确认浮层 */
.confirm-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.confirm-dialog {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  min-width: 280px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.confirm-dialog p {
  margin: 0 0 16px;
  font-size: 14px;
  color: #1e293b;
}

.confirm-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

/* ========== Skill 编辑/查看 - 右侧面板 ========== */
.skill-edit-panel {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.skill-edit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.skill-edit-header h3 {
  margin: 0;
  font-size: 16px;
  color: #1e293b;
}

.skill-form-group {
  margin-bottom: 14px;
}

.skill-form-group label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 4px;
}

.skill-form-group input,
.skill-form-group textarea,
.skill-form-group select {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
  box-sizing: border-box;
  transition: border-color 0.15s;
}

.skill-form-group input:focus,
.skill-form-group textarea:focus,
.skill-form-group select:focus {
  outline: none;
  border-color: #2356a5;
  box-shadow: 0 0 0 2px rgba(35,86,165,0.15);
}

.skill-form-group textarea {
  resize: vertical;
  min-height: 60px;
}

.keyword-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.keyword-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  background: #dbeafe;
  color: #1e40af;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
}

.keyword-chip button {
  background: none;
  border: none;
  color: #1e40af;
  cursor: pointer;
  font-size: 14px;
  padding: 0;
  line-height: 1;
}

.keyword-add-row {
  display: flex;
  gap: 6px;
  margin-top: 6px;
}

.keyword-add-row input {
  flex: 1;
}

.keyword-add-row button {
  flex-shrink: 0;
}

.step-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 10px;
  background: #f8fafc;
}

.step-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.step-number {
  font-size: 12px;
  font-weight: 700;
  color: #2356a5;
}

.step-actions {
  display: flex;
  gap: 4px;
}

.step-actions button {
  padding: 2px 6px;
  font-size: 11px;
}

.step-type-toggle {
  display: flex;
  gap: 4px;
  margin-bottom: 8px;
}

.step-type-toggle button {
  padding: 4px 12px;
  font-size: 12px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #64748b;
  cursor: pointer;
}

.step-type-toggle button.active {
  background: #2356a5;
  color: #fff;
  border-color: #2356a5;
}

.arg-row {
  display: flex;
  gap: 4px;
}

.arg-row input {
  flex: 1;
  padding: 4px 8px;
  font-size: 12px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
}

.arg-row button {
  flex-shrink: 0;
  padding: 4px 8px;
  font-size: 11px;
}

.skill-form-actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

/* Skill 只读详情 */
.skill-detail-section {
  margin-bottom: 16px;
}

.skill-detail-section h4 {
  margin: 0 0 6px;
  font-size: 13px;
  color: #475569;
  font-weight: 600;
}

.skill-detail-keywords {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.skill-detail-step {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 8px;
  background: #fff;
}

.skill-detail-step-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.step-tool-badge {
  display: inline-block;
  background: #f0fdf4;
  color: #166534;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  border: 1px solid #bbf7d0;
  font-weight: 600;
}

.step-prompt-badge {
  display: inline-block;
  background: #dbeafe;
  color: #1e40af;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 600;
}
</style>
