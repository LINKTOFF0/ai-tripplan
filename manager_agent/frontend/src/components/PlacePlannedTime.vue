<script setup lang="ts">
import { Clock, X } from 'lucide-vue-next'
const props = defineProps<{ modelValue: string; placeName: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
function update(event: Event) {
  const value = (event.target as HTMLInputElement).value
  if (!value || /^([01]\d|2[0-3]):[0-5]\d$/.test(value)) emit('update:modelValue', value)
}
</script>

<template>
  <label class="place-planned-time" @click.stop @pointerdown.stop @dragstart.stop.prevent>
    <Clock :size="13" /><span>计划到达</span>
    <input type="time" :value="props.modelValue" :aria-label="`${placeName}计划到达时间`" @input="update" @keydown.esc.stop="($event.target as HTMLInputElement).blur()">
    <button v-if="modelValue" type="button" :aria-label="`清除${placeName}计划到达时间`" title="清除计划时间" @click.stop.prevent="emit('update:modelValue', '')"><X :size="12" /></button>
  </label>
</template>

<style scoped>
.place-planned-time { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin-top: 9px; color: #748986; font-size: 11px; }
.place-planned-time input { width: 106px; height: 28px; box-sizing: border-box; padding: 2px 5px; border: 1px solid #dfece9; border-radius: 4px; background: #f7fbfa; color: #197f7a; font: inherit; font-size: 12px; }
.place-planned-time input:focus { outline: 2px solid #a8d6cf; outline-offset: 1px; }
.place-planned-time button { display: grid; place-items: center; width: 24px; height: 24px; border: 0; border-radius: 4px; background: transparent; color: #748986; cursor: pointer; }
</style>
