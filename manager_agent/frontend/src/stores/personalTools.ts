import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export interface ChecklistItem {
  id: string
  text: string
  done: boolean
}

const STORAGE_KEY = 'aitripplan.personal-tools.v1'

function loadTools(): { note: string; checklist: ChecklistItem[] } {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { note: '', checklist: [] }
    const value: unknown = JSON.parse(raw)
    if (!value || typeof value !== 'object') return { note: '', checklist: [] }
    const data = value as { note?: unknown; checklist?: unknown }
    return {
      note: typeof data.note === 'string' ? data.note : '',
      checklist: Array.isArray(data.checklist) ? data.checklist.filter((item): item is ChecklistItem =>
        !!item && typeof item === 'object' &&
        typeof item.id === 'string' && typeof item.text === 'string' && typeof item.done === 'boolean',
      ) : [],
    }
  } catch {
    return { note: '', checklist: [] }
  }
}

export const usePersonalToolsStore = defineStore('personalTools', () => {
  const saved = loadTools()
  const note = ref(saved.note)
  const checklist = ref(saved.checklist)

  watch([note, checklist], ([nextNote, nextChecklist]) => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ note: nextNote, checklist: nextChecklist }))
    } catch {
      // Keep editing available when browser storage is unavailable.
    }
  }, { deep: true })

  function addChecklistItem(text: string) {
    const value = text.trim()
    if (!value) return false
    checklist.value.push({ id: `check-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`, text: value, done: false })
    return true
  }

  function toggleChecklistItem(id: string) {
    const item = checklist.value.find(entry => entry.id === id)
    if (item) item.done = !item.done
  }

  function removeChecklistItem(id: string) {
    checklist.value = checklist.value.filter(item => item.id !== id)
  }

  return { note, checklist, addChecklistItem, toggleChecklistItem, removeChecklistItem }
})
