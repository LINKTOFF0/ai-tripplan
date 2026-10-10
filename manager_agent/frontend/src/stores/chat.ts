import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  text: string
  displayText?: string
  streaming?: boolean
}

const STORAGE_KEY = 'aitripplan.chat.v1'
const INITIAL_MESSAGE: ChatMessage = {
  id: 1,
  role: 'assistant',
  text: '圆规帮你规划与完善行程，猜你想问',
}
const MAX_MESSAGES = 200

function loadMessages(): ChatMessage[] {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (!stored) return [{ ...INITIAL_MESSAGE }]

    const parsed: unknown = JSON.parse(stored)
    if (!Array.isArray(parsed)) return [{ ...INITIAL_MESSAGE }]

    const messages = parsed.filter((item): item is ChatMessage =>
      item !== null &&
      typeof item === 'object' &&
      typeof item.id === 'number' &&
      (item.role === 'user' || item.role === 'assistant') &&
      typeof item.text === 'string',
    ).slice(-MAX_MESSAGES)

    if (!messages.length) return [{ ...INITIAL_MESSAGE }]

    if (messages.length === 1 && messages[0].role === 'assistant' && messages[0].text.startsWith('已经为你准备好一份')) {
      return [{ ...INITIAL_MESSAGE }]
    }

    return messages.map(message => {
      if (message.role === 'assistant' && message.streaming) {
        return {
          ...message,
          streaming: false,
          text: message.text ? `${message.text}\n\n（页面刷新，生成已中断）` : '页面刷新，生成已中断。你可以重新发送刚才的问题。',
        }
      }
      return { ...message, streaming: false }
    })
  } catch {
    return [{ ...INITIAL_MESSAGE }]
  }
}

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>(loadMessages())

  watch(messages, value => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(value.slice(-MAX_MESSAGES)))
    } catch {
      // Keep the conversation usable if storage is unavailable or full.
    }
  }, { deep: true })

  function startNewChat() {
    messages.value = [{ ...INITIAL_MESSAGE, id: Date.now() }]
  }

  return { messages, startNewChat }
})
