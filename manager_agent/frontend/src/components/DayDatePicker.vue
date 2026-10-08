<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { CalendarDays, ChevronLeft, ChevronRight, X } from 'lucide-vue-next'

const props = defineProps<{ modelValue: string; dayNumber: number }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const root = ref<HTMLElement>()
const open = ref(false)
const viewDate = ref(new Date())
const weekdays = ['一', '二', '三', '四', '五', '六', '日']

function parseDate(value: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return null
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
  return date.getFullYear() === Number(match[1]) && date.getMonth() === Number(match[2]) - 1 && date.getDate() === Number(match[3]) ? date : null
}
function formatDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
function sameDate(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}
const today = new Date()
const calendarDays = computed(() => {
  const first = new Date(viewDate.value.getFullYear(), viewDate.value.getMonth(), 1)
  const offset = (first.getDay() + 6) % 7
  const start = new Date(first.getFullYear(), first.getMonth(), 1 - offset)
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + index)
    return { date, inMonth: date.getMonth() === viewDate.value.getMonth() }
  })
})
const monthLabel = computed(() => `${viewDate.value.getFullYear()}年${viewDate.value.getMonth() + 1}月`)

function showCalendar() {
  const selected = parseDate(props.modelValue)
  const base = selected ?? new Date()
  viewDate.value = new Date(base.getFullYear(), base.getMonth(), 1)
  open.value = !open.value
}
function changeMonth(amount: number) {
  viewDate.value = new Date(viewDate.value.getFullYear(), viewDate.value.getMonth() + amount, 1)
}
function selectDate(date: Date) {
  emit('update:modelValue', formatDate(date))
  open.value = false
}
function onOutsidePointer(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) open.value = false
}
function onEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && open.value) open.value = false
}
onMounted(() => {
  document.addEventListener('pointerdown', onOutsidePointer)
  document.addEventListener('keydown', onEscape)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onOutsidePointer)
  document.removeEventListener('keydown', onEscape)
})
</script>

<template>
  <div ref="root" class="day-date-picker">
    <button class="day-date-trigger" type="button" :aria-expanded="open" :aria-label="`选择第 ${dayNumber} 天日期`" @click="showCalendar">
      <CalendarDays :size="14" />
      <span>{{ modelValue || '选择日期' }}</span>
      <ChevronRight :size="12" class="day-date-caret" />
    </button>
    <div v-if="open" class="day-calendar" role="dialog" :aria-label="`选择第 ${dayNumber} 天日期`">
      <header class="day-calendar-header">
        <button type="button" aria-label="上个月" @click="changeMonth(-1)"><ChevronLeft :size="16" /></button>
        <strong>{{ monthLabel }}</strong>
        <button type="button" aria-label="下个月" @click="changeMonth(1)"><ChevronRight :size="16" /></button>
      </header>
      <div class="day-calendar-grid day-calendar-weekdays"><span v-for="weekday in weekdays" :key="weekday">{{ weekday }}</span></div>
      <div class="day-calendar-grid day-calendar-days">
        <button v-for="cell in calendarDays" :key="formatDate(cell.date)" type="button" :class="{ outside: !cell.inMonth, selected: modelValue === formatDate(cell.date), today: sameDate(cell.date, today) }" :aria-label="formatDate(cell.date)" :aria-pressed="modelValue === formatDate(cell.date)" @click="selectDate(cell.date)">{{ cell.date.getDate() }}</button>
      </div>
      <footer class="day-calendar-footer">
        <button type="button" @click="selectDate(today)">今天</button>
        <button v-if="modelValue" type="button" class="clear-date" @click="emit('update:modelValue', ''); open = false"><X :size="13" /> 清除日期</button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.day-date-picker { position: relative; display: inline-flex; }
.day-date-trigger { display: inline-flex; min-height: 28px; align-items: center; gap: 6px; padding: 3px 8px; border: 1px solid #dfece9; border-radius: 5px; background: rgba(247, 251, 250, .92); color: #657876; font: inherit; font-size: 10px; cursor: pointer; transition: border-color .15s, background .15s, color .15s; }
.day-date-trigger:hover, .day-date-trigger[aria-expanded="true"] { border-color: #a8d6cf; background: #edf8f5; color: #147f7b; }
.day-date-trigger>span { min-width: 53px; color: inherit; }
.day-date-caret { transform: rotate(90deg); opacity: .65; }
.day-calendar { position: absolute; z-index: 30; top: calc(100% + 7px); left: 0; width: 252px; padding: 10px; border: 1px solid #dfeae7; border-radius: 8px; background: rgba(255, 255, 255, .98); box-shadow: 0 12px 30px rgba(35, 70, 67, .17); color: #314442; backdrop-filter: blur(14px); }
.day-calendar-header { display: grid; height: 32px; grid-template-columns: 32px 1fr 32px; align-items: center; text-align: center; }
.day-calendar-header strong { font-size: 12px; font-weight: 650; }
.day-calendar-header button { display: grid; width: 28px; height: 28px; place-items: center; padding: 0; border: 0; border-radius: 5px; background: transparent; color: #607572; cursor: pointer; }
.day-calendar-header button:hover { background: #edf7f4; color: #147f7b; }
.day-calendar-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; }
.day-calendar-weekdays { margin: 8px 0 3px; color: #9aa9a6; font-size: 9px; text-align: center; }
.day-calendar-weekdays span { display: grid; height: 23px; place-items: center; }
.day-calendar-days button { display: grid; aspect-ratio: 1; place-items: center; padding: 0; border: 1px solid transparent; border-radius: 5px; background: transparent; color: #485b59; font: inherit; font-size: 10px; cursor: pointer; }
.day-calendar-days button:hover { background: #eaf6f2; color: #087f7a; }
.day-calendar-days button.outside { color: #b4bfbd; }
.day-calendar-days button.today { border-color: #a8d6cf; color: #087f7a; font-weight: 700; }
.day-calendar-days button.selected { border-color: #149e9b; background: #149e9b; color: white; font-weight: 700; }
.day-calendar-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 7px; padding-top: 7px; border-top: 1px solid #edf1f0; }
.day-calendar-footer button { display: inline-flex; min-height: 26px; align-items: center; gap: 4px; padding: 3px 7px; border: 0; border-radius: 4px; background: transparent; color: #14847f; font: inherit; font-size: 10px; cursor: pointer; }
.day-calendar-footer button:hover { background: #edf7f4; }
.day-calendar-footer .clear-date { color: #83918f; }
</style>
