<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { CalendarDays, ChevronDown, X } from 'lucide-vue-next'
import DayDatePicker from '@/components/DayDatePicker.vue'
import { plannedDateSummary } from '@/utils/plannedDates'
import type { JourneyDay } from '@/types/journey'

const props = defineProps<{ days: JourneyDay[] }>()
const emit = defineEmits<{ change: [dayId: string, date: string] }>()
const open = ref(false)
const root = ref<HTMLElement>()
const trigger = ref<HTMLButtonElement>()
const summary = computed(() => plannedDateSummary(props.days))
function outside(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) open.value = false
}
function close() { open.value = false; trigger.value?.focus() }
onMounted(() => document.addEventListener('pointerdown', outside))
onBeforeUnmount(() => document.removeEventListener('pointerdown', outside))
</script>

<template>
  <div ref="root" class="planned-dates" @keydown.esc.stop.prevent="close">
    <button ref="trigger" type="button" class="planned-dates-trigger" :aria-expanded="open" aria-label="修改计划出行时间" :title="`计划出行时间：${summary}`" @click="open = !open">
      <CalendarDays :size="16" /><span><small>计划出行时间</small><strong>{{ summary }}</strong></span><ChevronDown :size="13" />
    </button>
    <section v-if="open" class="planned-dates-panel" aria-label="计划出行日期">
      <header><strong>计划出行日期</strong><button type="button" aria-label="关闭出行日期" @click="close"><X :size="16" /></button></header>
      <div v-for="day in days" :key="day.id" class="planned-date-row">
        <span>DAY {{ day.dayNumber }}</span>
        <DayDatePicker :model-value="day.date" :day-number="day.dayNumber" inline @update:model-value="emit('change', day.id, $event)" />
      </div>
      <p v-if="!days.length">暂无日程</p>
    </section>
  </div>
</template>

<style scoped>
.planned-dates { position: relative; flex: 0 0 auto; }
.planned-dates-trigger { display: flex; align-items: center; gap: 8px; min-height: 40px; max-width: 285px; padding: 5px 9px; border: 1px solid #dfece9; border-radius: 5px; background: #f7fbfa; color: #197f7a; cursor: pointer; font: inherit; text-align: left; }
.planned-dates-trigger > span { display: grid; gap: 2px; min-width: 0; }
.planned-dates-trigger small { font-size: 10px; color: #718783; }
.planned-dates-trigger strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 11px; font-weight: 600; }
.planned-dates-trigger:hover, .planned-dates-trigger[aria-expanded='true'] { border-color: #a8d6cf; background: #edf8f5; }
.planned-dates-panel { position: absolute; right: 0; top: calc(100% + 8px); z-index: 40; width: 290px; max-height: calc(100vh - 100px); overflow: auto; padding: 12px; box-sizing: border-box; background: #fff; border: 1px solid #dfece9; border-radius: 8px; box-shadow: 0 12px 30px rgba(35,70,67,.17); }
.planned-dates-panel header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 7px; font-size: 13px; }
.planned-dates-panel header button { display: grid; place-items: center; width: 28px; height: 28px; border: 0; border-radius: 4px; background: transparent; color: #657876; cursor: pointer; }
.planned-date-row { display: grid; gap: 7px; padding: 10px 0; border-top: 1px solid #edf1f0; }
.planned-date-row > span { color: #197f7a; font-size: 11px; font-weight: 600; }
.planned-dates-panel p { color: #718783; font-size: 12px; }
</style>
