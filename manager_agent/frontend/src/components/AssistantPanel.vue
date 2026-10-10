<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import MarkdownIt from 'markdown-it'
import { ArrowUp, Check, CircleStop, PanelLeftClose, PanelLeftOpen, Plus, Sparkles } from 'lucide-vue-next'
import { useChatStore, type ChatMessage } from '@/stores/chat'
import { useJourneyStore } from '@/stores/journey'
import { applyRouteOrder, canApplyJourneyResponse, fromBackendPlan, type BackendJourneyPlan } from '@/utils/journeyResponse'
import type { TravelDefaults, TravelMode } from '@/utils/travelRoutes'

const props = defineProps<{ collapsed?: boolean; travelPreferences: Record<string, TravelMode>; travelDefaults: TravelDefaults }>()
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
async function send(text = input.value, includeJourneyContext = false, task?: string, displayText?: string) {
  if (sending.value) { controller.value?.abort(); reset(); emit('toast', '已停止生成'); return }
  const prompt = text.trim()
  if (!prompt) return
  const requestSnapshot = JSON.stringify(journeyStore.plan)
  const requestDayId = journeyStore.activeDayId
  const preferenceSnapshot = JSON.stringify({ preferences: props.travelPreferences, defaults: props.travelDefaults })
  const requestPrompt = includeJourneyContext ? `${prompt}\n保留用户已选地点、备注和交通偏好。` : prompt
  const history = messages.value.filter(message => !message.streaming && !isWelcomeMessage(message))
    .slice(-12).map(({ role, text }) => ({ role, text }))
  messages.value.push({ id: Date.now(), role: 'user', text: prompt, displayText })
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
    const response = await fetch('/trip/stream', { method: 'POST', headers: { 'Content-Type': 'application/json;charset=UTF-8' }, body: JSON.stringify({ prompt: requestPrompt, task, journeyPlan: JSON.parse(requestSnapshot), activeDayId: requestDayId, travelPreferences: props.travelPreferences, travelDefaults: props.travelDefaults, history }), signal: requestController.signal })
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
          const event = JSON.parse(line.trim().slice(5)) as { type?: string; text?: string; mode?: string; task?: string; journeyPlan?: BackendJourneyPlan }
          if (event.type === 'TEXT') {
            answer.text += event.text ?? ''
            workflowStage.value = 2
          }
          if (event.type === 'JOURNEY_PLAN' && event.journeyPlan) {
            if (requestController.signal.aborted || planApplied) continue
            const routeOrder = task === 'ROUTE_OPTIMIZATION' || event.task === 'ROUTE_OPTIMIZATION'
            if (!canApplyJourneyResponse(journeyStore.plan, requestSnapshot) || (routeOrder && (journeyStore.activeDayId !== requestDayId
                || JSON.stringify({ preferences: props.travelPreferences, defaults: props.travelDefaults }) !== preferenceSnapshot))) {
              emit('toast', '行程已被修改，本次 AI 结果未覆盖当前卡片')
              answer.text += '\n\n行程已被你修改，本次建议未应用到卡片。'
              continue
            }
            try {
              if (routeOrder && event.mode !== 'edit') throw new Error('排序结果不能替换行程')
              const nextPlan = routeOrder ? applyRouteOrder(JSON.parse(requestSnapshot), event.journeyPlan, requestDayId)
                : fromBackendPlan(event.journeyPlan, event.mode === 'edit')
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
async function analyzeItinerary() {
  if (sending.value) { emit('toast', '正在处理上一项请求，请完成或停止后再解析'); return }
  if (props.collapsed) emit('toggleCollapse')
  await send('请读取当前全部日期的行程卡片、地点顺序、日期、游玩建议、用户交通偏好和历史中明确表达的旅行需求，输出一份完整、可直接阅读的文字旅游规划，而不是仅提取需求或整理关键词。以当前卡片为准，历史中的旧地点和旧顺序不能覆盖当前卡片。先概述目的地、天数与旅行风格，再按天逐站说明游玩安排、地点之间的衔接、餐饮与住宿安排，以及交通方式建议，最后给出行前准备、注意事项和待确认事项。按现有卡片顺序组织，有不合理处单独说明改进建议，不直接重排。没有餐饮或住宿卡片时只给安排建议并注明待确认，不把未选择的店铺当成已确定地点。不虚构精确时间、距离、票价、营业时间、预约信息或实时天气；缺少的信息明确待确认。若没有地点卡片，先说明信息不足并询问目的地、天数和偏好，不能声称已经完成规划。本次只输出文字，不调用编辑工具，不新增、删除、替换或修改行程卡片，不返回用于更新卡片的结构化行程。', true, 'GENERAL', '智能解析')
}
async function optimizeRoute() {
  if (sending.value) { emit('toast', '正在处理上一项请求，请完成或停止后再优化'); return }
  if (props.collapsed) emit('toggleCollapse')
  await send('请调用线路智能体优化当前选中日期的完整线路。不固定首站或末站，保留全部已选地点；以总通行时间优先、总距离次之比较完整路线，而非按距首站远近排序。交通偏好仅指定对应有向点对出现时的交通方式，不锁定相邻关系，不以偏好命中数量作为目标；新点对按全局默认策略选择方式。不得为了保留原相邻关系或主观减少折返而放弃更快的合规路线。返回完整地点ID排列并实际应用，列出比较依据与已核实的前后指标。没有穷尽候选或最优性证明，不得声称全局最优；工具失败或关键数据缺失时保留原顺序。不要修改其他日期。', true, 'ROUTE_OPTIMIZATION', '线路优化')
}
defineExpose({ optimizeRoute })
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
        <div v-else-if="message.role === 'user'" class="user-bubble">{{ message.displayText || message.text }}</div>
      </article>
      <div v-if="sending" class="workflow-status" role="status" aria-live="polite">
        <span class="workflow-sparkle"><Sparkles :size="14" /></span>
        <span>{{ ['正在理解你的需求', '正在整理行程方案', '正在准备回复'][workflowStage] }}</span>
        <span class="typing-indicator"><i></i><i></i><i></i></span>
      </div>
      <div v-if="messages.length === 1 && messages[0] && isWelcomeMessage(messages[0])" class="quick-replies"><h2>{{ messages[0].text }} <span>👇</span></h2><button v-for="shortcut in shortcuts" :key="shortcut.text" @click="send(shortcut.text)"><span class="quick-reply-icon">{{ shortcut.icon }}</span>{{ shortcut.text }}<ArrowUp :size="16" /></button></div>
    </div>
    <div v-if="!props.collapsed" class="assistant-presets"><button :disabled="sending" @click="analyzeItinerary"><Sparkles :size="15" /><span>智能解析</span></button><button :disabled="sending" @click="optimizeRoute"><ArrowUp :size="15" /><span>路线优化</span></button><button @click="send('请评价当前行程安排是否合理', true, 'GENERAL')"><Check :size="15" /><span>行程判官</span></button></div>
    <form class="message-composer" @click.capture="openChatFromComposer" @submit.prevent="send()"><label class="composer-shell"><textarea ref="inputElement" v-model="input" rows="1" maxlength="2000" aria-label="旅行规划" @focus="inputFocused = true" @blur="inputFocused = false" @keydown.enter.exact.prevent="send()"></textarea><span v-if="!input && !inputFocused" class="composer-placeholder" @click="focusInput">旅行规划，圆规一手拿捏</span><div class="composer-bottom"><button type="submit" class="composer-send" :class="{ stop: sending }" :aria-label="sending ? '停止生成' : '发送消息'"><CircleStop v-if="sending" :size="17" /><ArrowUp v-else :size="18" /></button></div></label><p class="assistant-disclaimer">内容由 AI 生成，请在出行前核对开放时间与交通信息 <span class="composer-char-count">{{ charCount }} / 2000</span></p></form>
  </aside>
</template>
