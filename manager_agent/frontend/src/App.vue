<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowLeft, Bike, Bus, Car, Check, ChevronDown, ChevronLeft, ChevronRight, CloudSun, Compass, DraftingCompass, FerrisWheel, Footprints, Hotel, Landmark, ListChecks, Map, MapPin, MoreHorizontal, NotebookPen, PanelLeftOpen, PanelRightClose, Pencil, Plus, RefreshCw, Route, Share2, SlidersHorizontal, Sparkles, Star, Store, Trees, Utensils, X, Trash2 } from 'lucide-vue-next'
import AssistantPanel from '@/components/AssistantPanel.vue'
import DayDatePicker from '@/components/DayDatePicker.vue'
import JourneyMap from '@/components/JourneyMap.vue'
import { recommendations } from '@/data/sampleJourney'
import { useJourneyStore } from '@/stores/journey'
import { usePersonalToolsStore } from '@/stores/personalTools'
import type { JourneyPlace, Recommendation } from '@/types/journey'

const store = useJourneyStore()
watch(() => store.plan.title, title => { document.title = `圆规 AI · ${title || '旅行规划'}` }, { immediate: true })
const mobilePanel = ref<'assistant' | 'itinerary' | 'map'>('assistant')
const activeTab = ref<'overview' | 'day'>('overview')
const activeTool = ref<'note' | 'checklist' | null>(null)
const personalTools = usePersonalToolsStore()
const newChecklistItem = ref('')
const recommendationFilter = ref<Recommendation['kind'] | null>('attraction')
const mapRecommendations = ref<Recommendation[]>(recommendations)
const editingTitle = ref(false)
const titleDraft = ref('')
const titleInput = ref<HTMLInputElement>()
const toast = ref('')
const deleteDayConfirm = ref(false)
const optimizingRoute = ref(false)
const editingDayId = ref('')
const dayTitleDraft = ref('')
const dayTitleInput = ref<HTMLInputElement>()
const editingPlaceAdviceId = ref('')
const placeAdviceDraft = ref('')
const placeAdviceInput = ref<HTMLTextAreaElement>()
const placeMenuId = ref('')
const draggedPlaceId = ref('')
const activeTravelLegIndex = ref<number | null>(null)
type TravelMode = 'walking' | 'cycling' | 'driving' | 'transit'
const travelModes: { id: TravelMode; label: string; icon: typeof Footprints }[] = [
  { id: 'walking', label: '步行', icon: Footprints },
  { id: 'cycling', label: '骑行', icon: Bike },
  { id: 'driving', label: '驾车', icon: Car },
  { id: 'transit', label: '公共交通', icon: Bus },
]
const travelPreferences = ref<Record<string, TravelMode>>(loadTravelPreferences())
const assistantCollapsed = ref(false)
const itineraryCollapsed = ref(false)
const showMapRecommendations = ref(false)
const mapDayMenuOpen = ref(false)
const journeyMap = ref<{ recenter: () => Promise<boolean>; focusPlace: (placeId: string) => Promise<boolean>; focusRecommendation: (placeId: string) => boolean; refreshRecommendations: () => void; prepareRouteOptimization: () => Promise<boolean> } | null>(null)
const workspaceElement = ref<HTMLElement>()
const initialPanelWidths = loadPanelWidths()
const assistantWidth = ref(initialPanelWidths.assistant)
const itineraryWidth = ref(initialPanelWidths.itinerary)
const resizeState = ref<{ panel: 'assistant' | 'itinerary'; startX: number; startWidth: number; workspaceWidth: number } | null>(null)
let toastTimeout: ReturnType<typeof setTimeout> | undefined
const filteredRecommendations = computed(() => recommendationFilter.value ? mapRecommendations.value.filter(item => item.kind === recommendationFilter.value) : [])
const totalPlaceCount = computed(() => store.plan.days.reduce((total, day) => total + day.places.length, 0))
const routeDistance = computed(() => `${(totalPlaceCount.value * 1.8).toFixed(1)} km`)

function notify(message: string) {
  toast.value = message
  clearTimeout(toastTimeout)
  toastTimeout = setTimeout(() => { toast.value = '' }, 2200)
}
function loadPanelWidths() {
  try {
    const value = JSON.parse(localStorage.getItem('aitripplan.panel-widths.v1') ?? '{}') as { assistant?: number; itinerary?: number }
    return {
      assistant: typeof value.assistant === 'number' && value.assistant >= 20 && value.assistant <= 36 ? value.assistant : 25,
      itinerary: typeof value.itinerary === 'number' && value.itinerary >= 32 && value.itinerary <= 55 ? value.itinerary : 43,
    }
  } catch { return { assistant: 25, itinerary: 43 } }
}
function loadTravelPreferences(): Record<string, TravelMode> {
  try {
    const saved = JSON.parse(localStorage.getItem('aitripplan.travel-modes.v1') ?? '{}') as Record<string, unknown>
    return Object.fromEntries(Object.entries(saved).filter((entry): entry is [string, TravelMode] => travelModes.some(mode => mode.id === entry[1])))
  } catch { return {} }
}
function savePanelWidths() {
  try { localStorage.setItem('aitripplan.panel-widths.v1', JSON.stringify({ assistant: assistantWidth.value, itinerary: itineraryWidth.value })) } catch { /* Panel resizing still works without storage. */ }
}
function startPanelResize(event: PointerEvent, panel: 'assistant' | 'itinerary') {
  const width = workspaceElement.value?.getBoundingClientRect().width ?? 0
  if (!width) return
  const target = event.currentTarget as HTMLElement
  target.setPointerCapture(event.pointerId)
  resizeState.value = { panel, startX: event.clientX, startWidth: panel === 'assistant' ? assistantWidth.value : itineraryWidth.value, workspaceWidth: width }
  event.preventDefault()
}
function resizePanels(event: PointerEvent) {
  const state = resizeState.value
  if (!state) return
  const delta = (event.clientX - state.startX) / state.workspaceWidth * 100
  const minimumMap = 320 / state.workspaceWidth * 100
  if (state.panel === 'assistant') {
    const minimum = 275 / state.workspaceWidth * 100
    const maximum = Math.min(38, 100 - itineraryWidth.value - minimumMap - 2 / state.workspaceWidth * 100)
    assistantWidth.value = Math.min(maximum, Math.max(minimum, state.startWidth + delta))
  } else {
    const minimum = 420 / state.workspaceWidth * 100
    const assistant = assistantCollapsed.value ? 0 : assistantWidth.value
    const maximum = 100 - assistant - minimumMap - 2 / state.workspaceWidth * 100
    itineraryWidth.value = Math.min(maximum, Math.max(minimum, state.startWidth + delta))
  }
  savePanelWidths()
}
function stopPanelResize() { resizeState.value = null }
function addRecommendation(item: Recommendation) {
  const added = store.addPlace({ id: `place-${item.id}`, name: item.name, city: store.plan.destination, address: item.address, category: item.kind, startTime: '待安排', durationMinutes: 60, description: `来自附近推荐 · ${item.distance}${item.rating ? ` · ${item.rating} 分` : ''}`, longitude: item.longitude, latitude: item.latitude, locationStatus: 'matched', icon: item.icon })
  notify(added ? `${item.name} 已加入行程` : '该地点已在行程中')
  activeTab.value = 'day'
}
function addCustomPlace() {
  const name = window.prompt('输入想添加的地点名称')?.trim()
  if (!name) return
  store.addPlace({ id: `manual-${Date.now()}`, name, city: store.plan.destination, address: '', category: 'other', startTime: '待安排', durationMinutes: 60, description: '手动添加 · 地图位置待确认', longitude: 113.295, latitude: 22.831, locationStatus: 'pending', icon: 'map-pin' })
  notify(`${name} 已加入行程`)
}
function sharePlan() {
  navigator.clipboard?.writeText(location.href).then(() => notify('页面链接已复制')).catch(() => notify('当前浏览器不支持复制链接'))
}
function editTitle() {
  titleDraft.value = store.plan.title
  editingTitle.value = true
  requestAnimationFrame(() => titleInput.value?.focus())
}
function saveTitle() {
  const title = titleDraft.value.trim()
  if (!title) { notify('行程名称不能为空'); titleInput.value?.focus(); return }
  store.plan.title = title.slice(0, 80)
  editingTitle.value = false
  notify('行程名称已保存')
}
function cancelTitleEdit() { editingTitle.value = false; titleDraft.value = store.plan.title }
function editDayTitle() {
  const day = store.currentDay
  if (!day) return
  editingDayId.value = day.id
  dayTitleDraft.value = day.title
  requestAnimationFrame(() => dayTitleInput.value?.focus())
}
function saveDayTitle() {
  const day = store.currentDay
  const title = dayTitleDraft.value.trim()
  if (!day || !title) { dayTitleInput.value?.focus(); return }
  day.title = title.slice(0, 100)
  editingDayId.value = ''
}
function cancelDayTitleEdit() {
  editingDayId.value = ''
  dayTitleDraft.value = store.currentDay?.title ?? ''
}
function editPlaceAdvice(placeId: string) {
  const place = store.places.find(item => item.id === placeId)
  if (!place) return
  editingPlaceAdviceId.value = placeId
  placeAdviceDraft.value = place.advice ?? place.description ?? placeAdvice(place)
  placeMenuId.value = ''
  requestAnimationFrame(() => placeAdviceInput.value?.focus())
}
function savePlaceAdvice(placeId: string) {
  if (editingPlaceAdviceId.value !== placeId) return
  const place = store.places.find(item => item.id === placeId)
  if (!place) return
  place.advice = placeAdviceDraft.value.trim().slice(0, 1000)
  editingPlaceAdviceId.value = ''
}
function togglePlaceMenu(placeId: string) { placeMenuId.value = placeMenuId.value === placeId ? '' : placeId }
function startPlaceReorder(event: DragEvent, placeId: string) {
  if ((event.target as HTMLElement).closest('button, input')) { event.preventDefault(); return }
  draggedPlaceId.value = placeId
  if (event.dataTransfer) { event.dataTransfer.effectAllowed = 'move'; event.dataTransfer.setData('text/plain', placeId) }
}
function reorderPlace(event: DragEvent, targetId: string) {
  event.preventDefault()
  if (!draggedPlaceId.value || draggedPlaceId.value === targetId) return
  const sourceIndex = store.places.findIndex(place => place.id === draggedPlaceId.value)
  const targetIndex = store.places.findIndex(place => place.id === targetId)
  if (sourceIndex >= 0 && targetIndex >= 0) store.movePlace(draggedPlaceId.value, targetIndex - sourceIndex)
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
}
function stopPlaceReorder() { draggedPlaceId.value = '' }
function travelModeFor(from: JourneyPlace, to: JourneyPlace): TravelMode {
  return travelPreferences.value[`${from.id}:${to.id}`] ?? 'walking'
}
function selectTravelMode(from: JourneyPlace, to: JourneyPlace, mode: TravelMode) {
  travelPreferences.value[`${from.id}:${to.id}`] = mode
  activeTravelLegIndex.value = null
  try { localStorage.setItem('aitripplan.travel-modes.v1', JSON.stringify(travelPreferences.value)) } catch { /* Preferences remain active for this session. */ }
}
function estimateLeg(from: JourneyPlace, to: JourneyPlace, mode: TravelMode) {
  if (from.locationStatus !== 'matched' || to.locationStatus !== 'matched') return null
  const toRadians = (degrees: number) => degrees * Math.PI / 180
  const latitudeDelta = toRadians(to.latitude - from.latitude)
  const longitudeDelta = toRadians(to.longitude - from.longitude)
  const latitudeFrom = toRadians(from.latitude)
  const latitudeTo = toRadians(to.latitude)
  const a = Math.sin(latitudeDelta / 2) ** 2 + Math.cos(latitudeFrom) * Math.cos(latitudeTo) * Math.sin(longitudeDelta / 2) ** 2
  const straightLineKm = 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  const routeFactors: Record<TravelMode, number> = { walking: 1.2, cycling: 1.18, driving: 1.32, transit: 1.25 }
  const speeds: Record<TravelMode, number> = { walking: 4.5, cycling: 14, driving: 25, transit: 19 }
  const routeKm = straightLineKm * routeFactors[mode]
  const meters = Math.max(10, Math.round(routeKm * 100)) * 10
  const minutes = Math.max(1, Math.round(routeKm / speeds[mode] * 60) + (mode === 'transit' ? 5 : mode === 'driving' ? 2 : 0))
  return { distance: meters < 1000 ? `${meters} 米` : `${(meters / 1000).toFixed(1)} 公里`, duration: `${minutes} 分钟` }
}
function deleteCurrentDay() {
  const day = store.currentDay
  if (!day || store.plan.days.length <= 1) { notify('行程至少保留一天'); return }
  store.removeDay(day.id)
  deleteDayConfirm.value = false
  activeTab.value = 'day'
  notify('当天日程已删除')
}
function addChecklistItem() {
  if (personalTools.addChecklistItem(newChecklistItem.value)) newChecklistItem.value = ''
}
function placeAdvice(place: JourneyPlace) {
  if (place.advice !== undefined) return place.advice.trim()
  if (place.description?.trim()) return place.description.trim()
  if (place.category === 'food') return '建议提前确认营业时间与排队情况，热门时段预留等位时间。'
  if (place.category === 'hotel') return '出发前确认入住时间、具体地址与抵达方式。'
  if (place.category === 'transport') return '出发前核对乘车点与班次，换乘时预留步行时间。'
  if (place.category === 'attraction') return '出发前确认开放时间与预约要求，并预留充足游览时间。'
  return '出发前确认地点开放状态与交通方式，顺路安排周边停留点。'
}
function selectMapDay(dayId: string) {
  store.activeDayId = dayId
  store.selectedPlaceId = store.currentDay?.places[0]?.id ?? ''
  activeTab.value = 'day'
  activeTool.value = null
  mapDayMenuOpen.value = false
}
function selectOverviewPlace(dayId: string, placeId: string) {
  store.activeDayId = dayId
  store.selectPlace(placeId)
  void journeyMap.value?.focusPlace(placeId)
}
function focusDayPlace(placeId: string) {
  if (store.selectedPlaceId === placeId && store.places.find(place => place.id === placeId)?.locationStatus === 'matched') {
    store.selectedPlaceId = ''
    return
  }
  void journeyMap.value?.focusPlace(placeId)
}
async function recenterMap() {
  if (await journeyMap.value?.recenter()) notify('地图已定位到当天行程')
  else notify('地图暂不可用，请稍后重试')
}
function straightLineDistance(from: JourneyPlace, to: JourneyPlace) {
  const radians = (degrees: number) => degrees * Math.PI / 180
  const latitudeDelta = radians(to.latitude - from.latitude)
  const longitudeDelta = radians(to.longitude - from.longitude)
  const latitudeFrom = radians(from.latitude)
  const latitudeTo = radians(to.latitude)
  const a = Math.sin(latitudeDelta / 2) ** 2 + Math.cos(latitudeFrom) * Math.cos(latitudeTo) * Math.sin(longitudeDelta / 2) ** 2
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}
async function optimizeCurrentRoute() {
  if (optimizingRoute.value || store.places.length < 3) {
    if (store.places.length < 3) notify('至少需要三个地点才能优化路线')
    return
  }
  optimizingRoute.value = true
  try {
    const locationsReady = await journeyMap.value?.prepareRouteOptimization()
    if (!locationsReady || store.places.some(place => !Number.isFinite(place.longitude) || !Number.isFinite(place.latitude))) {
      notify('部分地点无法定位，暂时不能按距离排序')
      return
    }
    const [first, ...remaining] = store.places
    remaining.sort((a, b) => straightLineDistance(first, a) - straightLineDistance(first, b))
    if (store.reorderPlaces([first.id, ...remaining.map(place => place.id)])) notify('已按距首站的直线距离优化顺序')
  } finally {
    optimizingRoute.value = false
  }
}
function toggleMapRecommendations() {
  if (!showMapRecommendations.value) recommendationFilter.value = 'attraction'
  showMapRecommendations.value = !showMapRecommendations.value
}

const iconMap = { Landmark, Store, Utensils, FerrisWheel, Trees, Compass, CloudSun, Route, Map, Sparkles, Star, Hotel }
function locationIcon(icon: string) {
  return iconMap[({ landmark: 'Landmark', store: 'Store', utensils: 'Utensils', 'ferris-wheel': 'FerrisWheel', trees: 'Trees', factory: 'Compass', soup: 'Utensils', hotel: 'Hotel' } as Record<string, keyof typeof iconMap>)[icon] ?? 'Map']
}
function focusRecommendation(placeId: string) { void journeyMap.value?.focusRecommendation(placeId) }
</script>

<template>
  <div class="app-shell" :class="{ 'assistant-collapsed': assistantCollapsed, 'itinerary-collapsed': itineraryCollapsed }">
    <header class="topbar">
      <div class="topbar-leading">
        <button class="icon-button" aria-label="返回" @click="notify('已在行程工作台')"><ArrowLeft :size="19" /></button>
        <div class="brand-lockup"><div class="brand-symbol"><DraftingCompass :size="17" :stroke-width="1.8" /></div><span>圆规 AI</span><span class="brand-divider"></span><input v-if="editingTitle" ref="titleInput" v-model="titleDraft" class="journey-title-input" maxlength="80" aria-label="行程名称" @keydown.enter.prevent="saveTitle" @keydown.esc="cancelTitleEdit" @blur="saveTitle"><span v-else class="journey-title">{{ store.plan.title }}</span><button class="icon-button tiny" :aria-label="editingTitle ? '保存行程名称' : '编辑行程名称'" @mousedown.prevent @click="editingTitle ? saveTitle() : editTitle()"><Check v-if="editingTitle" :size="14" /><Pencil v-else :size="13" /></button></div>
      </div>
      <div class="topbar-right"><span class="save-indicator"><Check :size="14" /> 已保存</span><span class="day-count">{{ store.plan.days.length }} 天</span><button class="icon-button" aria-label="分享行程" @click="sharePlan"><Share2 :size="18" /></button><button class="icon-button" aria-label="更多选项" @click="notify('更多行程操作将在后续版本开放')"><MoreHorizontal :size="20" /></button></div>
    </header>

    <nav class="mobile-tabs" aria-label="切换工作区">
      <button :class="{ active: mobilePanel === 'assistant' }" @click="mobilePanel = 'assistant'"><Sparkles :size="16" /> AI 助手</button>
      <button :class="{ active: mobilePanel === 'itinerary' }" @click="mobilePanel = 'itinerary'"><Route :size="16" /> 行程</button>
      <button :class="{ active: mobilePanel === 'map' }" @click="mobilePanel = 'map'"><Map :size="16" /> 地图</button>
    </nav>

    <main ref="workspaceElement" class="workspace" :style="{ '--assistant-width': assistantWidth, '--itinerary-width': itineraryWidth }">
      <AssistantPanel class="assistant-column" :class="{ 'mobile-active': mobilePanel === 'assistant' }" :collapsed="assistantCollapsed" @toggle-collapse="assistantCollapsed = !assistantCollapsed" @toast="notify" />
      <div v-if="!assistantCollapsed" class="panel-resizer" :style="{ left: `${assistantWidth}%` }" role="separator" aria-orientation="vertical" aria-label="调整 AI 对话栏宽度" @pointerdown="startPanelResize($event, 'assistant')" @pointermove="resizePanels" @pointerup="stopPanelResize" @pointercancel="stopPanelResize"></div>

      <section class="itinerary-column" :class="{ 'mobile-active': mobilePanel === 'itinerary' }">
        <div class="itinerary-toolbar">
          <div class="tabs" role="tablist">
            <button :class="{ active: activeTab === 'overview' && !activeTool }" @click="activeTab = 'overview'; activeTool = null">总览</button>
            <button v-for="day in store.plan.days" :key="day.id" :class="{ active: activeTab === 'day' && store.activeDayId === day.id && !activeTool }" @click="store.activeDayId = day.id; activeTab = 'day'; activeTool = null">DAY {{ day.dayNumber }}</button>
          </div>
          <div class="toolbar-actions"><button class="toolbar-tool" :class="{ active: activeTool === 'note' }" aria-label="打开便签" @click="activeTool = activeTool === 'note' ? null : 'note'"><NotebookPen :size="15" /><span>便签</span></button><button class="toolbar-tool" :class="{ active: activeTool === 'checklist' }" aria-label="打开清单" @click="activeTool = activeTool === 'checklist' ? null : 'checklist'"><ListChecks :size="15" /><span>清单</span></button><button class="toolbar-text" @click="store.addDay(); activeTab = 'day'; activeTool = null"><Plus :size="15" /> 添加一天</button><button class="icon-button itinerary-collapse-button" aria-label="收起行程详情" title="收起行程详情" @click="itineraryCollapsed = true"><PanelRightClose :size="17" /></button></div>
        </div>

        <div class="itinerary-scroll">
          <section v-if="activeTool === 'note'" class="personal-tool-view note-view"><header><div><em>Note</em><h2>便签</h2></div><button class="tool-done" aria-label="完成便签" @click="activeTool = null"><Check :size="19" /></button></header><textarea v-model="personalTools.note" maxlength="5000" placeholder="点击输入便签…"></textarea><small>{{ personalTools.note.length }} / 5000</small></section>

          <section v-else-if="activeTool === 'checklist'" class="personal-tool-view checklist-view"><header><div><em>Checklist</em><h2>清单</h2></div><button class="tool-done" aria-label="完成清单" @click="activeTool = null"><Check :size="19" /></button></header><div class="checklist-privacy"><span>私人清单仅个人可见</span><span>{{ personalTools.checklist.filter(item => item.done).length }} / {{ personalTools.checklist.length }} 完成</span></div><ul class="checklist-items"><li v-for="item in personalTools.checklist" :key="item.id" :class="{ done: item.done }"><label><input type="checkbox" :checked="item.done" @change="personalTools.toggleChecklistItem(item.id)"><span>{{ item.text }}</span></label><button aria-label="删除清单项目" @click="personalTools.removeChecklistItem(item.id)"><X :size="15" /></button></li><li class="checklist-new-row"><form @submit.prevent="addChecklistItem"><input v-model="newChecklistItem" maxlength="120" placeholder="添加事项"></form></li></ul></section>

          <section v-else-if="activeTab === 'overview'" class="overview-panel">
            <div class="overview-main"><div class="section-kicker"><Sparkles :size="13" /> 行程总览</div><h1>{{ store.plan.title }}</h1><div class="overview-facts"><span><MapPin :size="14" /> {{ store.plan.destination }}</span><span><Route :size="14" /> {{ store.plan.days.length }} 天 · {{ totalPlaceCount }} 个地点</span><span><Compass :size="14" /> {{ routeDistance }}</span></div></div>
            <div class="weather-block"><CloudSun :size="25" /><strong>28°</strong><span>晴间多云</span></div>
          </section>

          <section v-if="activeTab === 'overview' && !activeTool" class="overview-days" aria-label="每日行程">
            <article v-for="day in store.plan.days" :key="day.id" class="day-section overview-day">
              <header class="day-header"><div><div class="day-eyebrow">DAY {{ day.dayNumber }} <DayDatePicker v-model="day.date" :day-number="day.dayNumber" /></div><h2>{{ day.title }}</h2><p>{{ day.places.length }} 个地点 <span class="dot-separator">·</span> {{ day.places.map(place => place.name).join('、') || '尚未安排地点' }}</p></div><button class="overview-edit-button" @click="store.activeDayId = day.id; activeTab = 'day'; activeTool = null">编辑当天 <ChevronRight :size="13" /></button></header>
              <div v-if="day.places.length" class="overview-place-list">
                <button v-for="(place, index) in day.places" :key="place.id" class="overview-place-row" :class="{ selected: store.activeDayId === day.id && store.selectedPlaceId === place.id }" @click="selectOverviewPlace(day.id, place.id)">
                  <span class="overview-sequence">{{ index + 1 }}</span><span class="place-thumb" :class="`thumb-${place.category}`"><component :is="locationIcon(place.icon)" :size="19" /></span><span class="overview-place-copy"><span class="place-titleline"><span class="category-label" :class="`category-${place.category}`">{{ place.category === 'food' ? '美食' : place.category === 'hotel' ? '住宿' : place.category === 'transport' ? '交通' : place.category === 'attraction' ? '游玩' : '其他' }}</span><strong>{{ place.name }}</strong></span><small v-if="place.locationStatus !== 'matched'">待确认定位 · 未加入路线</small><small>{{ place.description || place.address || '地点详情待补充' }}</small></span>
                </button>
              </div>
              <div v-else class="overview-empty-day"><MapPin :size="16" /> 这一天还没有安排地点</div>
            </article>
          </section>

          <section v-if="activeTab === 'day' && !activeTool" class="day-section">
            <header class="day-header"><div><div class="day-eyebrow">DAY {{ store.currentDay?.dayNumber ?? 1 }} <DayDatePicker v-if="store.currentDay" v-model="store.currentDay.date" :day-number="store.currentDay.dayNumber" /></div><div class="day-title-edit"><input v-if="editingDayId === store.currentDay?.id" ref="dayTitleInput" v-model="dayTitleDraft" class="day-title-input" maxlength="100" aria-label="编辑当天备注" @keydown.enter.prevent="saveDayTitle" @keydown.esc="cancelDayTitleEdit" @blur="saveDayTitle"><button v-else class="day-title-note" aria-label="点击编辑当天备注" title="点击编辑当天备注" @click="editDayTitle"><h2>{{ store.currentDay?.title ?? '新的一天' }}</h2><Pencil :size="13" /></button></div><p>{{ store.places.length }} 个地点 <span class="dot-separator">·</span> 顺序可随时调整</p></div><div class="day-header-actions"><button class="route-optimize-button" :disabled="optimizingRoute || store.places.length < 3" @click="optimizeCurrentRoute"><Route :size="14" /><span>{{ optimizingRoute ? '正在优化' : '路线优化' }}</span></button><button class="icon-button" aria-label="删除当天日程" title="删除当天日程" :disabled="store.plan.days.length <= 1" @click="deleteDayConfirm = !deleteDayConfirm"><Trash2 :size="16" /></button></div></header>
            <div v-if="deleteDayConfirm" class="delete-confirm"><span>删除当天日程及其中所有地点？</span><div><button class="delete-cancel" @click="deleteDayConfirm = false">取消</button><button class="delete-accept" @click="deleteCurrentDay">确认删除</button></div></div>
            <div v-if="store.places.length" class="place-list">
              <template v-for="(place, index) in store.places" :key="place.id">
                <article class="place-row minimal-place-row" :class="{ selected: store.selectedPlaceId === place.id, 'is-dragging': draggedPlaceId === place.id }" draggable="true" @dragstart="startPlaceReorder($event, place.id)" @dragenter="reorderPlace($event, place.id)" @dragover.prevent @drop.prevent="stopPlaceReorder" @dragend="stopPlaceReorder" @click="focusDayPlace(place.id)">
                  <div class="place-minimal-top"><span class="category-label" :class="`category-${place.category}`">{{ place.category === 'food' ? '美食' : place.category === 'hotel' ? '住宿' : place.category === 'transport' ? '交通' : place.category === 'attraction' ? '游玩' : '其他' }}</span><div class="place-menu-wrap"><button class="place-more-button" :aria-label="`更多${place.name}操作`" :aria-expanded="placeMenuId === place.id" @click.stop="togglePlaceMenu(place.id)"><MoreHorizontal :size="18" /></button><div v-if="placeMenuId === place.id" class="place-action-menu"><button :disabled="index === 0" @click.stop="store.movePlace(place.id, -1); placeMenuId = ''"><ChevronLeft :size="14" /> 上移</button><button :disabled="index === store.places.length - 1" @click.stop="store.movePlace(place.id, 1); placeMenuId = ''"><ChevronRight :size="14" /> 下移</button><button class="remove-place-action" @click.stop="store.removePlace(place.id); placeMenuId = ''"><X :size="14" /> 移除地点</button></div></div></div>
                  <div class="place-minimal-title"><span>{{ index + 1 }}</span><h3>{{ place.name }}</h3><MapPin v-if="place.locationStatus !== 'matched'" :size="12" class="pending-place-icon" /></div>
                  <small v-if="place.locationStatus !== 'matched'">待确认定位 · 未加入路线</small>
                  <section v-if="store.selectedPlaceId === place.id" class="place-advice" @click.stop><div><Sparkles :size="13" /><strong>游玩建议</strong></div><textarea v-if="editingPlaceAdviceId === place.id" ref="placeAdviceInput" v-model="placeAdviceDraft" maxlength="1000" aria-label="编辑游玩建议" placeholder="添加游玩建议" @keydown.esc.prevent="savePlaceAdvice(place.id)" @blur="savePlaceAdvice(place.id)"></textarea><button v-else class="place-advice-content" @click="editPlaceAdvice(place.id)"><p>{{ placeAdvice(place) || '点击添加游玩建议' }}</p></button><span v-if="place.address"><MapPin :size="12" /> {{ place.address }}</span></section>
                </article>
                <div v-if="index < store.places.length - 1" class="travel-leg"><Route :size="13" /><div class="travel-preference-wrap"><button class="travel-preference-button" :aria-expanded="activeTravelLegIndex === index" @click.stop="activeTravelLegIndex = activeTravelLegIndex === index ? null : index"><component :is="travelModes.find(mode => mode.id === travelModeFor(place, store.places[index + 1]))?.icon" :size="14" /><span>{{ travelModes.find(mode => mode.id === travelModeFor(place, store.places[index + 1]))?.label }}</span><span v-if="estimateLeg(place, store.places[index + 1], travelModeFor(place, store.places[index + 1]))">约 {{ estimateLeg(place, store.places[index + 1], travelModeFor(place, store.places[index + 1]))?.distance }} · {{ estimateLeg(place, store.places[index + 1], travelModeFor(place, store.places[index + 1]))?.duration }}</span><span v-else>位置待确认</span><ChevronDown :size="12" /></button><small>路线估算</small><div v-if="activeTravelLegIndex === index" class="travel-mode-menu"><header><strong>选择交通方式</strong><span><SlidersHorizontal :size="12" /> 偏好</span></header><button v-for="mode in travelModes" :key="mode.id" :class="{ active: travelModeFor(place, store.places[index + 1]) === mode.id }" @click.stop="selectTravelMode(place, store.places[index + 1], mode.id)"><component :is="mode.icon" :size="17" /><span>{{ mode.label }}</span><span v-if="estimateLeg(place, store.places[index + 1], mode.id)">{{ estimateLeg(place, store.places[index + 1], mode.id)?.distance }} <i>|</i> {{ estimateLeg(place, store.places[index + 1], mode.id)?.duration }}</span><span v-else>位置待确认</span><Check v-if="travelModeFor(place, store.places[index + 1]) === mode.id" :size="15" /></button></div></div></div>
              </template>
            </div>
            <div v-else class="empty-day"><MapPin :size="25" /><strong>这一天还没有地点</strong><span>从右侧推荐中添加地点，开始安排路线。</span></div>
            <button class="add-place-button" @click="addCustomPlace"><Plus :size="15" /> 添加地点</button>
          </section>
        </div>
      </section>
      <div v-if="!itineraryCollapsed" class="panel-resizer" :style="{ left: `${(assistantCollapsed ? 0 : assistantWidth) + itineraryWidth}%` }" role="separator" aria-orientation="vertical" aria-label="调整行程栏宽度" @pointerdown="startPanelResize($event, 'itinerary')" @pointermove="resizePanels" @pointerup="stopPanelResize" @pointercancel="stopPanelResize"></div>

      <aside class="map-column" :class="{ 'mobile-active': mobilePanel === 'map' }">
        <button v-if="itineraryCollapsed" class="itinerary-expand-rail" aria-label="展开行程详情" @click="itineraryCollapsed = false; showMapRecommendations = false"><PanelLeftOpen :size="16" /><span>行程详情</span></button>
        <section class="map-wrap"><JourneyMap ref="journeyMap" :category="recommendationFilter" :recommendations-open="itineraryCollapsed && showMapRecommendations" @toast="notify" @recommendations="mapRecommendations = $event" /><div class="map-overlay-top"><div class="map-day-picker"><button class="map-location-chip" :aria-expanded="mapDayMenuOpen" aria-haspopup="listbox" @click="mapDayMenuOpen = !mapDayMenuOpen"><span>DAY {{ store.currentDay?.dayNumber ?? 1 }}</span><strong>{{ store.plan.destination }}</strong><ChevronDown :size="14" /></button><div v-if="mapDayMenuOpen" class="map-day-menu" role="listbox" aria-label="选择地图行程日期"><button v-for="day in store.plan.days" :key="day.id" role="option" :aria-selected="store.activeDayId === day.id" :class="{ active: store.activeDayId === day.id }" @click="selectMapDay(day.id)"><span class="map-day-option-number">DAY {{ day.dayNumber }}</span><span class="map-day-option-copy"><strong>{{ day.title || '自由安排' }}</strong><small>{{ day.date || '日期待定' }} · {{ day.places.length }} 个地点</small></span><Check v-if="store.activeDayId === day.id" :size="15" /></button></div></div><div class="map-toolbar-actions"><button v-if="itineraryCollapsed" class="map-recommend-toggle" :class="{ active: showMapRecommendations }" @click="toggleMapRecommendations"><Sparkles :size="14" /><span>推荐</span><ChevronDown :size="13" /></button><button class="map-icon-button" aria-label="重新定位到当天路线" title="重新定位到当天路线" @click="recenterMap"><Compass :size="17" /></button></div></div></section>
        <section class="recommend-section" :class="{ 'map-recommendations-open': showMapRecommendations }"><header class="recommend-heading"><div><div class="section-kicker"><Sparkles :size="13" /> 当前地图范围内的地点</div><h2>推荐</h2></div><button class="icon-button" aria-label="刷新推荐" title="重新搜索当前地图范围" @click="journeyMap?.refreshRecommendations()"><RefreshCw :size="16" /></button></header><div class="recommend-filters"><button v-for="filter in [{id:'attraction',label:'游玩'},{id:'food',label:'美食'},{id:'hotel',label:'住宿'}] as const" :key="filter.id" :class="{ active: recommendationFilter === filter.id }" @click="recommendationFilter = filter.id"><i :class="`filter-swatch swatch-${filter.id}`"></i>{{ filter.label }}</button></div><div class="recommendation-list"><article v-for="item in filteredRecommendations" :key="item.id" class="recommendation-row" tabindex="0" @click="focusRecommendation(item.id)" @keydown.enter="focusRecommendation(item.id)"><div class="recommendation-art" :class="`art-${item.kind}`"><component :is="locationIcon(item.icon)" :size="21" /></div><div class="recommendation-copy"><h3>{{ item.name }}</h3><p>{{ item.address || '地址暂无' }}</p><div class="recommend-meta"><span class="recommend-category">{{ item.kind === 'attraction' ? '游玩' : item.kind === 'food' ? '美食' : '住宿' }}</span><span>{{ item.distance }}</span><span v-if="item.rating"><Star :size="11" fill="currentColor" /> {{ item.rating }}</span></div></div><button class="recommend-add" :aria-label="`添加${item.name}`" @click.stop="addRecommendation(item)"><Plus :size="17" /></button></article><p v-if="!recommendationFilter" class="recommend-empty">选择游玩、美食或住宿查看地点</p><p v-else-if="!filteredRecommendations.length" class="recommend-empty">当前地图范围没有匹配地点</p></div><button class="more-recommendations" @click="journeyMap?.refreshRecommendations()">搜索当前地图范围 <RefreshCw :size="12" /></button></section>
      </aside>
    </main>
    <Transition name="toast"><div v-if="toast" class="toast-message"><Check :size="15" /> {{ toast }}</div></Transition>
  </div>
</template>
