<template>
  <section class="workspace commands-page">
    <div class="toolbar">
      <h2 style="flex:1">常用命令</h2>
      <input v-model.trim="search" placeholder="搜索命令..." style="max-width:260px" />
      <button v-if="canManage" @click="openCreateDialog()">新增命令</button>
    </div>
    <div class="commands-layout">
      <aside class="commands-sidebar">
        <div class="type-list">
          <button :class="{ active: selectedType === null }" @click="selectedType = null">全部</button>
          <template v-for="cat in typeCategories" :key="cat">
            <div class="type-category-label">{{ cat }}</div>
            <button v-for="t in typesByCategory(cat)" :key="t.id" :class="{ active: selectedType === t.id }" @click="selectedType = t.id">{{ t.name }}</button>
          </template>
        </div>
      </aside>
      <main class="commands-main">
        <div class="command-list">
          <article v-for="cmd in filteredCommands" :key="cmd.id" class="command-card" @click="cmd._expanded = !cmd._expanded">
            <div class="command-header">
              <span class="command-type-tag">{{ getTypeName(cmd) }}</span>
              <span class="command-brief">{{ cmd.briefDescription }}</span>
              <span v-for="cat in parseCats(cmd.categories)" :key="cat" class="command-cat-tag">{{ cat }}</span>
              <div style="margin-left:auto;display:flex;gap:6px">
                <button class="ghost" @click.stop="copyCommand(cmd.commandFormat)">复制</button>
                <button v-if="canManageCmd(cmd)" class="ghost" @click.stop="openEditDialog(cmd)">编辑</button>
                <button v-if="canManageCmd(cmd)" class="danger" @click.stop="deleteCmd(cmd)">删除</button>
              </div>
            </div>
            <pre class="command-code">{{ cmd.commandFormat }}</pre>
            <div v-if="cmd._expanded && cmd.detailedDescription" class="command-detail">
              <div v-html="formatDetail(cmd.detailedDescription)"></div>
            </div>
          </article>
          <EmptyState v-if="filteredCommands.length === 0" message="暂无匹配的命令。" />
        </div>
      </main>
    </div>

    <FormModal v-model="showDialog" :title="form.id ? '编辑常用命令' : '新增常用命令'" @submit="saveCmd()">
      <div class="form-grid single">
        <label>所属类型
          <select v-model="form.softwareTypeId" required>
            <option value="" disabled>请选择</option>
            <optgroup v-for="cat in formCategories" :key="cat" :label="cat">
              <option v-for="t in formTypesByCategory(cat)" :key="t.id" :value="t.id">{{ t.name }}</option>
            </optgroup>
          </select>
        </label>
        <label>命令格式<textarea v-model.trim="form.commandFormat" required rows="4" placeholder="如: redis-cli -h $HOST -p $PORT info"></textarea></label>
        <label>简要说明<input v-model.trim="form.briefDescription" required maxlength="500" placeholder="如: 查看基础信息" /></label>
        <label>详细说明<textarea v-model.trim="form.detailedDescription" rows="6" placeholder="命令的详细说明、参数解释等"></textarea></label>
        <label>分类标签<input v-model.trim="form.categories" placeholder="多个标签用逗号分隔，如: 基础,常用,查询" /></label>
      </div>
    </FormModal>
  </section>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { formatDetail } from '../../../utils'
import EmptyState from '../../../components/ui/EmptyState.vue'
import FormModal from '../../../components/ui/FormModal.vue'

const props = defineProps({
  auth: Object,
  isSysAdmin: Boolean,
  managedCategory: String,
  softwareTypes: { type: Array, default: () => [] },
  moduleRequest: { type: Function, required: true },
  notify: { type: Function, default: () => {} },
  confirm: { type: Function, default: () => {} }
})

// State
const types = ref([])
const commands = ref([])
const selectedType = ref(null)
const search = ref('')
const showDialog = ref(false)
const form = reactive({ id: null, softwareTypeId: '', commandFormat: '', briefDescription: '', detailedDescription: '', categories: '' })

// Computed
const canManage = computed(() => props.isSysAdmin || !!props.managedCategory)
const typeCategories = computed(() => [...new Set(types.value.map(t => t.category))])
const filteredCommands = computed(() => {
  let list = commands.value
  if (selectedType.value != null) list = list.filter(c => c.softwareTypeId === selectedType.value)
  if (search.value) {
    const q = search.value.toLowerCase()
    list = list.filter(c =>
      (c.commandFormat && c.commandFormat.toLowerCase().includes(q)) ||
      (c.briefDescription && c.briefDescription.toLowerCase().includes(q)) ||
      (c.detailedDescription && c.detailedDescription.toLowerCase().includes(q))
    )
  }
  return list
})
const formTypes = computed(() => {
  if (props.isSysAdmin) return props.softwareTypes
  if (!props.managedCategory) return []
  return props.softwareTypes.filter(t => t.category === props.managedCategory)
})
const formCategories = computed(() => [...new Set(formTypes.value.map(t => t.category))])

// Functions
function typesByCategory(cat) { return types.value.filter(t => t.category === cat) }
function formTypesByCategory(cat) { return formTypes.value.filter(t => t.category === cat) }
function getTypeName(cmd) { const t = types.value.find(x => x.id === cmd.softwareTypeId); return t ? t.name : '' }
function getTypeCategory(cmd) { const t = types.value.find(x => x.id === cmd.softwareTypeId); return t ? t.category : '' }
function canManageCmd(cmd) {
  if (!props.auth?.token) return false
  if (props.isSysAdmin) return true
  if (!props.managedCategory) return false
  return getTypeCategory(cmd) === props.managedCategory
}
function parseCats(cats) { if (!cats) return []; try { return JSON.parse(cats) } catch { return [] } }
function copyCommand(text) {
  if (navigator.clipboard?.writeText) {
    navigator.clipboard.writeText(text)
      .then(() => props.notify('已复制到剪贴板'))
      .catch(() => props.notify('复制失败', 'error'))
  } else {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    props.notify('已复制到剪贴板')
  }
}

function defaultForm() { return { id: null, softwareTypeId: '', commandFormat: '', briefDescription: '', detailedDescription: '', categories: '' } }
function openCreateDialog() { Object.assign(form, defaultForm()); showDialog.value = true }
function openEditDialog(cmd) {
  Object.assign(form, {
    id: cmd.id, softwareTypeId: cmd.softwareTypeId,
    commandFormat: cmd.commandFormat || '', briefDescription: cmd.briefDescription || '',
    detailedDescription: cmd.detailedDescription || '',
    categories: (() => { try { return JSON.parse(cmd.categories).join(', ') } catch { return '' } })()
  })
  showDialog.value = true
}

async function loadTypes() {
  try { types.value = await props.moduleRequest('/middleware-commands/types') || [] }
  catch (error) { props.notify(error.message, 'error') }
}
async function loadCommands() {
  try { commands.value = (await props.moduleRequest('/middleware-commands') || []).map(command => ({ ...command, _expanded: false })) }
  catch (error) { props.notify(error.message, 'error') }
}

async function saveCmd() {
  const cats = form.categories ? JSON.stringify(form.categories.split(/[,，]/).map(s => s.trim()).filter(Boolean)) : null
  const body = { softwareTypeId: form.softwareTypeId, commandFormat: form.commandFormat, briefDescription: form.briefDescription, detailedDescription: form.detailedDescription, categories: cats, sortOrder: 0 }
  const isEdit = !!form.id
  try {
    await props.moduleRequest(isEdit ? `/middleware-commands/${form.id}` : '/middleware-commands', {
      method: isEdit ? 'PUT' : 'POST',
      body
    })
    props.notify('命令已保存')
    showDialog.value = false
    await loadTypes()
    await loadCommands()
  } catch (error) { props.notify(error.message, 'error') }
}

async function deleteCmd(cmd) {
  props.confirm(`确定删除命令：${cmd.briefDescription}？`, async () => {
    try {
      await props.moduleRequest(`/middleware-commands/${cmd.id}`, { method: 'DELETE' })
      props.notify('已删除')
      await loadTypes()
      await loadCommands()
    } catch (error) { props.notify(error.message, 'error') }
  })
}

onMounted(async () => { await loadTypes(); await loadCommands() })
</script>
