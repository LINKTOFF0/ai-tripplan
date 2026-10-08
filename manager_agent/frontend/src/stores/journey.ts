import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { sampleJourney } from '@/data/sampleJourney'
import type { JourneyPlace, JourneyPlan } from '@/types/journey'

const STORAGE_KEY = 'aitripplan.journey.v1'

function loadJourney(): JourneyPlan {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) return JSON.parse(stored) as JourneyPlan
  } catch {
    localStorage.removeItem(STORAGE_KEY)
  }
  return structuredClone(sampleJourney)
}

export const useJourneyStore = defineStore('journey', () => {
  const plan = ref<JourneyPlan>(loadJourney())
  const selectedPlaceId = ref(plan.value.days[0]?.places[0]?.id ?? '')
  const activeDayId = ref(plan.value.days[0]?.id ?? '')
  const currentDay = computed(() => plan.value.days.find(day => day.id === activeDayId.value) ?? plan.value.days[0])
  const places = computed(() => currentDay.value?.places ?? [])
  const selectedPlace = computed(() => places.value.find(place => place.id === selectedPlaceId.value))

  watch(plan, value => localStorage.setItem(STORAGE_KEY, JSON.stringify(value)), { deep: true })

  function selectPlace(id: string) { selectedPlaceId.value = id }
  function addPlace(place: JourneyPlace) {
    const day = currentDay.value
    if (!day || day.places.some(item => item.id === place.id)) return false
    day.places.push(place)
    selectedPlaceId.value = place.id
    return true
  }
  function removePlace(id: string) {
    const day = currentDay.value
    if (!day) return
    day.places = day.places.filter(place => place.id !== id)
    if (selectedPlaceId.value === id) selectedPlaceId.value = day.places[0]?.id ?? ''
  }
  function movePlace(id: string, offset: number) {
    const day = currentDay.value
    if (!day) return
    const from = day.places.findIndex(place => place.id === id)
    const to = from + offset
    if (from < 0 || to < 0 || to >= day.places.length) return
    const [place] = day.places.splice(from, 1)
    day.places.splice(to, 0, place)
  }
  function reorderPlaces(ids: string[]) {
    const day = currentDay.value
    if (!day || ids.length !== day.places.length || new Set(ids).size !== day.places.length) return false
    const placesById = new Map(day.places.map(place => [place.id, place]))
    if (ids.some(id => !placesById.has(id))) return false
    day.places = ids.map(id => placesById.get(id)!)
    return true
  }
  function addDay() {
    const dayNumber = plan.value.days.length + 1
    const day = { id: `day-${Date.now()}`, dayNumber, date: '', title: '自由安排的一天', places: [] }
    plan.value.days.push(day)
    activeDayId.value = day.id
  }
  function removeDay(id: string) {
    if (plan.value.days.length <= 1) return false
    const index = plan.value.days.findIndex(day => day.id === id)
    if (index < 0) return false
    plan.value.days.splice(index, 1)
    plan.value.days.forEach((day, dayIndex) => { day.dayNumber = dayIndex + 1 })
    if (activeDayId.value === id) {
      const nextDay = plan.value.days[Math.min(index, plan.value.days.length - 1)]
      activeDayId.value = nextDay.id
      selectedPlaceId.value = nextDay.places[0]?.id ?? ''
    }
    return true
  }
  function replacePlan(next: JourneyPlan) {
    const existing = plan.value.days.flatMap(day => day.places)
    next.days.forEach(day => day.places.forEach(place => {
      const matches = existing.filter(old => old.locationStatus === 'matched' && old.name === place.name && old.city === place.city && (!place.address || old.address === place.address))
      if (matches.length === 1) {
        const old = matches[0]
        Object.assign(place, { longitude: old.longitude, latitude: old.latitude, address: old.address, locationStatus: 'matched' })
      }
    }))
    plan.value = next
    activeDayId.value = next.days[0]?.id ?? ''
    selectedPlaceId.value = next.days[0]?.places[0]?.id ?? ''
  }

  return { plan, activeDayId, currentDay, places, selectedPlaceId, selectedPlace, selectPlace, addPlace, removePlace, movePlace, reorderPlaces, addDay, removeDay, replacePlan }
})
