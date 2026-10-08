<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import MarkdownIt from 'markdown-it'
import { ArrowUp, Check, CircleStop, PanelLeftClose, PanelLeftOpen, Plus, Sparkles } from 'lucide-vue-next'
import { useChatStore, type ChatMessage } from '@/stores/chat'
import { useJourneyStore } from '@/stores/journey'
import { canApplyJourneyResponse, fromBackendPlan, type BackendJourneyPlan } from '@/utils/journeyResponse'

const props = defineProps<{ collapsed?: boolean }>()
const emit = defineEmits<{ toast: [message: string]; toggleCollapse: [] }>()
const chatStore = useChatStore()
const journeyStore = useJourneyStore()
const { messages } = storeToRefs(chatStore)
const input = ref('')
const inputFocused = ref(false)
const inputElement = ref<HTMLTextAreaElement>()
const sending = ref(false)
const workflowStage = ref(0)
const backendOnline = ref(false)
const messagesElement = ref<HTMLElement>()
const controller = ref<AbortController>()
const markdown = new MarkdownIt({ html: false, breaks: true, linkify: true })
const charCount = computed(() => input.value.length)
const shortcuts = [
  { icon: '🗺️', text: '接下来去哪逛比较合适？' },
  { icon: '🍕', text: '附近有什么值得吃的？' },
  { icon: '🌅', text: '现在天气怎么样？' },
]
const isWelcomeMessage = (message: ChatMessage) => message.role === 'assistant' && message.text.startsWith('圆规帮你规划')

function scrollToBottom() { nextTick(() => { if (messagesElement.value) messagesElement.value.scrollTop = messagesElement.value.scrollHeight }) }
function focusInput() { inputElement.value?.focus() }
function openChatFromComposer() {
  if (!props.collapsed) return
  emit('toggleCollapse')
  nextTick(focusInput)
}
async function checkHealth() {
  try { const response = await fetch('/trip', { method: 'POST', headers: { 'Content-Type': 'application/json;charset=UTF-8' }, body: JSON.stringify({ prompt: '__ping__' }) }); backendOnline.value = response.ok }
  catch { backendOnline.value = false }
}
function reset() { sending.value = false; controller.value = undefined }
async function send(text = input.value, includeJourneyContext = false, task?: string) {
  if (sending.value) { controller.value?.abort(); reset(); emit('toast', '已停止生成'); return }
  const prompt = text.trim()
  if (!prompt) return
  const requestSnapshot = JSON.stringify(journeyStore.plan)
  const requestPrompt = includeJourneyContext ? `${prompt}\n保留用户已选地点、备注和交通偏好。` : prompt
  const history = messages.value.filter(message => !message.streaming && !isWelcomeMessage(message))
    .slice(-12).map(({ role, text }) => ({ role, text }))
  messages.value.push({ id: Date.now(), role: 'user', text: prompt })
  input.value = ''
  sending.value = true
  workflowStage.value = 0
  const workflowTimer = window.setInterval(() => {
    workflowStage.value = Math.min(workflowStage.value + 1, 2)
  }, 2200)
  const requestController = new AbortController()
  controller.value = requestController
  const answerId = Date.now() + 1
  messages.value.push({ id: answerId, role: 'assistant', text: '', streaming: true })
  const answer = messages.value.find(message => message.id === answerId)!
  scrollToBottom()
  try {
    const response = await fetch('/trip/stream', { method: 'POST', headers: { 'Content-Type': 'application/json;charset=UTF-8' }, body: JSON.stringify({ prompt: requestPrompt, task, journeyPlan: JSON.parse(requestSnapshot), activeDayId: journeyStore.activeDayId, history }), signal: requestController.signal })
    if (!response.ok || !response.body) throw new Error(`服务返回 ${response.status}`)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let planApplied = false
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''
      for (const line of lines) {
        if (!line.trim().startsWith('data:')) continue
        try {
          const event = JSON.parse(line.trim().slice(5)) as { type?: string; text?: string; mode?: string; journeyPlan?: BackendJourneyPlan }
          if (event.type === 'TEXT') {
            answer.text += event.text ?? ''
            workflowStage.value = 2
          }
          if (event.type === 'JOURNEY_PLAN' && event.journeyPlan) {
            if (requestController.signal.aborted || planApplied) continue
            if (!canApplyJourneyResponse(journeyStore.plan, requestSnapshot)) {
              emit('toast', '行程已被修改，本次 AI 结果未覆盖当前卡片')
              answer.text += '\n\n行程已被你修改，本次建议未应用到卡片。'
              continue
            }
            try {
              const nextPlan = fromBackendPlan(event.journeyPlan, event.mode === 'edit')
              if (event.mode === 'edit') journeyStore.applyPlanEdit(nextPlan)
              else journeyStore.replacePlan(nextPlan)
              planApplied = true
            } catch {
              emit('toast', 'AI 返回的卡片数据无效，现有行程已保留')
            }
            workflowStage.value = 1
          }
          if (event.type === 'ERROR') answer.text += `\n\n${event.text ?? '生成失败'}`
          if (event.type === 'WARNING') emit('toast', event.text ?? '行程卡片未更新')
          if (event.type === 'DONE') answer.streaming = false
          scrollToBottom()
        } catch { /* Ignore partial or unrelated SSE frames. */ }
      }
    }
    answer.streaming = false
    backendOnline.value = true
  } catch (error) {
    answer.streaming = false
    if (error instanceof DOMException && error.name === 'AbortError') {
      if (!answer.text) messages.value = messages.value.filter(message => message.id !== answerId)
    } else {
      answer.text = '暂时无法连接行程规划服务，请确认服务已启动后重试。'
      backendOnline.value = false
    }
  } finally { window.clearInterval(workflowTimer); if (controller.value === requestController) reset(); scrollToBottom() }
}

function newChat() { if (sending.value) return; chatStore.startNewChat() }
let healthTimer: number | undefined
onMounted(() => { checkHealth(); healthTimer = window.setInterval(checkHealth, 30000) })
onUnmounted(() => { window.clearInterval(healthTimer); controller.value?.abort() })
</script>

<template>
  <aside class="assistant-panel" :class="{ 'is-collapsed': props.collapsed }">
    <header class="assistant-header"><div class="assistant-profile"><div class="assistant-avatar"><Sparkles :size="19" /></div><div><h1>圆规 AI</h1><p><span class="service-dot" :class="{ online: backendOnline }"></span>{{ backendOnline ? '规划服务在线' : 'AI 旅行规划助手' }}</p></div></div><div class="assistant-header-actions"><button class="icon-button collapse-button" :aria-label="props.collapsed ? '展开 AI 对话' : '收起 AI 对话'" :title="props.collapsed ? '展开 AI 对话' : '收起 AI 对话'" @click="emit('toggleCollapse')"><PanelLeftOpen v-if="props.collapsed" :size="17" /><PanelLeftClose v-else :size="17" /></button><button class="icon-button outlined new-chat-button" aria-label="开始新对话" :disabled="sending" @click="newChat"><Plus :size="18" /></button></div></header>
    <div v-if="!props.collapsed" ref="messagesElement" class="conversation-scroll" :class="{ 'welcome-state': messages.length === 1 && messages[0] && isWelcomeMessage(messages[0]) }">
      <article v-for="message in messages" :key="message.id" class="chat-message" :class="[message.role, { welcome: isWelcomeMessage(message) }]">
        <div v-if="message.role === 'assistant' && !isWelcomeMessage(message) && message.text" class="message-byline">圆规 AI <span v-if="message.streaming" class="typing-indicator"><i></i><i></i><i></i></span></div>
        <div v-if="message.role === 'assistant' && !isWelcomeMessage(message) && message.text" class="markdown-body" v-html="markdown.render(message.text)"></div>
        <div v-else-if="message.role === 'user'" class="user-bubble">{{ message.text }}</div>
      </article>
      <div v-if="sending" class="workflow-status" role="status" aria-live="polite">
        <span class="workflow-sparkle"><Sparkles :size="14" /></span>
        <span>{{ ['正在理解你的需求', '正在整理行程方案', '正在准备回复'][workflowStage] }}</span>
        <span class="typing-indicator"><i></i><i></i><i></i></span>
      </div>
      <div v-if="messages.length === 1 && messages[0] && isWelcomeMessage(messages[0])" class="quick-replies"><h2>{{ messages[0].text }} <span>👇</span></h2><button v-for="shortcut in shortcuts" :key="shortcut.text" @click="send(shortcut.text)"><span class="quick-reply-icon">{{ shortcut.icon }}</span>{{ shortcut.text }}<ArrowUp :size="16" /></button></div>
    </div>
    <div v-if="!props.collapsed" class="assistant-presets"><button @click="send('请智能解析我的旅行需求并整理关键信息', true, 'GENERAL')"><Sparkles :size="15" /><span>智能解析</span></button><button @click="send('请检查并优化当前行程路线，保留所有已选地点并说明顺序调整依据', true, 'ROUTE_OPTIMIZATION')"><ArrowUp :size="15" /><span>路线优化</span></button><button @click="send('请评价当前行程安排是否合理', true, 'GENERAL')"><Check :size="15" /><span>行程判官</span></button></div>
    <form class="message-composer" @click.capture="openChatFromComposer" @submit.prevent="send()"><label class="composer-shell"><textarea ref="inputElement" v-model="input" rows="1" maxlength="2000" aria-label="旅行规划" @focus="inputFocused = true" @blur="inputFocused = false" @keydown.enter.exact.prevent="send()"></textarea><span v-if="!input && !inputFocused" class="composer-placeholder" @click="focusInput">旅行规划，圆规一手拿捏</span><div class="composer-bottom"><button type="submit" class="composer-send" :class="{ stop: sending }" :aria-label="sending ? '停止生成' : '发送消息'"><CircleStop v-if="sending" :size="17" /><ArrowUp v-else :size="18" /></button></div></label><p class="assistant-disclaimer">内容由 AI 生成，请在出行前核对开放时间与交通信息 <span class="composer-char-count">{{ charCount }} / 2000</span></p></form>
  </aside>
</template>
