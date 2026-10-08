<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useJourneyStore } from '@/stores/journey'
import type { JourneyPlace, Recommendation } from '@/types/journey'
import { normalizeRoute, routeKey, type TravelMode, type TravelRoute } from '@/utils/travelRoutes'
import { poiKind } from '@/utils/poiCategory'

const props = defineProps<{ category: Recommendation['kind'] | null; recommendationsOpen?: boolean; travelPreferences: Record<string, TravelMode> }>()
const emit = defineEmits<{ toast: [message: string]; recommendations: [items: Recommendation[]]; routes: [items: Record<string, TravelRoute>]; weather: [item: { status: string; temperature?: string; weather?: string; city?: string; reportTime?: string }] }>()
const store = useJourneyStore()
const mapElement = ref<HTMLDivElement>()
let map: any
let markers: any[] = []
const markerByPlace = new Map<string, any>()
let routeLines: any[] = []
let AMap: any
const mapReady = ref(false)
const resolving = new WeakMap<JourneyPlace, Promise<void>>()
const attempted = new WeakSet<JourneyPlace>()
const locationTarget = ref<JourneyPlace>()
const locationQuery = ref('')
const locationCandidates = ref<any[]>([])
const searchingLocation = ref(false)
let candidateVersion = 0
function searchPlaces(query: string, city: string): Promise<any[]> {
  return new Promise(resolve => {
    const timer = setTimeout(() => resolve([]), 10000)
    new AMap.PlaceSearch({ city: city || '全国', citylimit: Boolean(city), pageSize: 10, extensions: 'base' })
      .search(query, (status: string, result: any) => {
        clearTimeout(timer)
        resolve(status === 'complete' ? (result?.poiList?.pois ?? []).filter((poi: any) => poi.location) : [])
      })
  })
}
function placeQuery(name: string) {
  return name.replace(/[（(].*?[）)]/g, '').replace(/\s*(午餐|晚餐|早餐|夜景|住宿)\s*$/, '').trim()
}
function bindLocation(place: JourneyPlace, poi: any) {
  place.longitude = Number(poi.location.lng)
  place.latitude = Number(poi.location.lat)
  place.address = String(poi.address || place.address)
  place.locationStatus = 'matched'
}
async function searchLocationCandidates() {
  const target = locationTarget.value
  if (!target || !locationQuery.value.trim()) return
  const version = ++candidateVersion
  searchingLocation.value = true
  const results = await searchPlaces(locationQuery.value.trim(), target.city)
  if (version !== candidateVersion) return
  locationCandidates.value = results
  searchingLocation.value = false
}
function chooseLocation(poi: any) {
  const target = locationTarget.value
  if (!target || !allPlaces().includes(target)) return
  target.name = poi.name
  bindLocation(target, poi)
  locationTarget.value = undefined
  void redraw()
}
const routeCache = new Map<string, Promise<any>>()
let routeQueue: Promise<unknown> = Promise.resolve()
const failedRoutes = new Map<string, number>()
let disposed = false
let routeResults: Record<string, TravelRoute> = {}
const cityCenterCache = new Map<string, Promise<[number, number] | null>>()
const transitCities = new Map<string, Promise<string>>()
function transitCity(city: string): Promise<string> {
  if (!transitCities.has(city)) transitCities.set(city, new Promise(resolve => {
    const timer = setTimeout(() => resolve(city), 10000)
    new AMap.Geocoder().getLocation(city, (status: string, result: any) => {
      clearTimeout(timer)
      resolve(status === 'complete' ? String(result?.geocodes?.[0]?.citycode || city) : city)
    })
  }))
  return transitCities.get(city)!
}
let renderVersion = 0
let weatherVersion = 0
async function refreshWeather() {
  const version = ++weatherVersion
  emit('weather', { status: 'loading' })
  if (!AMap) return
  const city = store.plan.destination
  const result = await new Promise<any>(resolve => {
    const timer = setTimeout(() => resolve(null), 10000)
    const finish = (data: any) => { clearTimeout(timer); resolve(data) }
    try {
      new AMap.Geocoder().getLocation(city, (status: string, response: any) => {
        if (status !== 'complete') return finish(null)
        const adcode = response?.geocodes?.[0]?.adcode
        if (!adcode) return finish(null)
        new AMap.Weather().getLive(adcode, (error: any, data: any) => finish(error ? null : data))
      })
    } catch { finish(null) }
  })
  if (version !== weatherVersion) return
  emit('weather', result && result.temperature != null && Number.isFinite(Number(result.temperature)) && result.weather && result.reportTime
    ? { status: 'ready', temperature: String(result.temperature), weather: result.weather, city: result.city, reportTime: result.reportTime }
    : { status: 'unavailable' })
}
let resizeObserver: ResizeObserver | undefined
let recommendationMarkers: any[] = []
let currentRecommendations: Recommendation[] = []
let focusedRecommendationId = ''
let searchTimer: ReturnType<typeof setTimeout> | undefined
let searchVersion = 0
const amapEnabled = Boolean(__AMAP_KEY__)
const mapX = (longitude: number) => 12 + ((longitude - 113.28) / 0.04) * 76
const mapY = (latitude: number) => 12 + (1 - (latitude - 22.8) / 0.05) * 76

async function initMap() {
  if (!amapEnabled || !mapElement.value) { emit('weather', { status: 'unavailable' }); return }
  try {
    ;(window as any)._AMapSecurityConfig = { serviceHost: `${window.location.origin}/_AMapService` }
    const { default: AMapLoader } = await import('@amap/amap-jsapi-loader')
    AMap = await AMapLoader.load({ key: __AMAP_KEY__, version: '2.0', plugins: ['AMap.Scale', 'AMap.ToolBar', 'AMap.Geocoder', 'AMap.Walking', 'AMap.Driving', 'AMap.Riding', 'AMap.Transfer', 'AMap.PlaceSearch', 'AMap.Weather'] })
    map = new AMap.Map(mapElement.value, { zoom: 13, center: [113.295, 22.836], mapStyle: 'amap://styles/whitesmoke', viewMode: '2D', showLabel: true })
    map.addControl(new AMap.Scale({ position: 'RB', offset: [12, 100] }))
    map.addControl(new AMap.ToolBar({ position: 'RB' }))
    mapReady.value = true
    void refreshWeather()
    map.on('moveend', scheduleRecommendationSearch)
    await geocodePendingPlaces()
    await redraw()
    scheduleRecommendationSearch()
  } catch {
    emit('weather', { status: 'unavailable' })
    emit('toast', '高德地图加载失败，已切换到路线示意图')
  }
}
function allPlaces() { return store.plan.days.flatMap(day => day.places) }
function cityCenter(city: string): Promise<[number, number] | null> {
  if (!city) return Promise.resolve(null)
  if (!cityCenterCache.has(city)) {
    cityCenterCache.set(city, new Promise(resolve => {
      const geocoder = new AMap.Geocoder({ city: '全国', extensions: 'all' })
      geocoder.getLocation(city, (status: string, result: any) => {
        const location = status === 'complete' ? result?.geocodes?.[0]?.location : null
        resolve(location ? [location.lng, location.lat] : null)
      })
    }))
  }
  return cityCenterCache.get(city)!
}
async function geocodePendingPlaces() {
  const pending = allPlaces().filter(place => place.locationStatus !== 'matched')
  for (const place of pending) {
    if (resolving.has(place)) { await resolving.get(place); continue }
    if (attempted.has(place)) continue
    attempted.add(place)
    const task = (async () => {
      const query = placeQuery(place.name)
      const results = await searchPlaces(query, place.city)
      if (!allPlaces().includes(place)) return
      const vague = /商圈|一带|附近|建议区域|住宿区域|周边/.test(place.name)
      const exact = results.filter((poi: any) => placeQuery(poi.name) === query)
      if (!vague && exact.length === 1) bindLocation(place, exact[0])
      else place.locationStatus = results.length || vague ? 'ambiguous' : 'failed'
    })().finally(() => resolving.delete(place))
    resolving.set(place, task)
    await task
  }
}
function searchRoute(service: any, from: [number, number], to: [number, number]): Promise<any> {
  return new Promise(resolve => {
    const timer = setTimeout(() => resolve(null), 10000)
    try {
      service.search(new AMap.LngLat(...from), new AMap.LngLat(...to), (status: string, result: any) => {
        clearTimeout(timer)
        if (status !== 'complete') console.warn('Route query failed:', status, typeof result === 'string' ? result : result?.info)
        resolve(status === 'complete' ? result : { info: typeof result === 'string' ? result : result?.info })
      })
    } catch { clearTimeout(timer); resolve(null) }
  })
}
const poiCategories = [
  { kind: 'attraction', label: '游玩', type: '风景名胜', keyword: '景点' },
  { kind: 'food', label: '美食', type: '餐饮服务', keyword: '餐饮' },
  { kind: 'hotel', label: '住宿', type: '住宿服务', keyword: '酒店' },
] as const
function searchCategory(category: typeof poiCategories[number], bounds: any): Promise<any[]> {
  return new Promise(resolve => {
    const search = new AMap.PlaceSearch({ city: store.plan.destination, citylimit: false, type: category.type, pageSize: 50, pageIndex: 1, extensions: 'base' })
    search.searchInBounds(category.keyword, bounds, (status: string, result: any) => resolve(status === 'complete' ? result?.poiList?.pois ?? result?.pois ?? [] : []))
  })
}
function distanceLabel(from: any, longitude: number, latitude: number) {
  const radians = (value: number) => value * Math.PI / 180
  const dLat = radians(latitude - from.lat)
  const dLng = radians(longitude - from.lng)
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(radians(from.lat)) * Math.cos(radians(latitude)) * Math.sin(dLng / 2) ** 2
  const km = 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return km < 1 ? `${Math.max(50, Math.round(km * 1000 / 50) * 50)} m` : `${km.toFixed(1)} km`
}
function scheduleRecommendationSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { void searchVisibleRecommendations() }, 350)
}
function isAllowedPoi(poi: any, category: typeof poiCategories[number]) {
  return poiKind(poi) === category.kind
}
async function searchVisibleRecommendations() {
  if (!map || !AMap) return
  const version = ++searchVersion
  const bounds = map.getBounds()
  const center = map.getCenter()
  const resultGroups = await Promise.all(poiCategories.map(category => searchCategory(category, bounds)))
  if (version !== searchVersion) return
  const seen = new Set<string>()
  const items: Recommendation[] = []
  resultGroups.forEach((pois: any[], categoryIndex) => {
    const category = poiCategories[categoryIndex]
    pois.forEach((poi: any) => {
      if (!isAllowedPoi(poi, category)) return
      const location = poi.location
      if (!location) return
      const id = String(poi.id || `${poi.name}-${location.lng}-${location.lat}`)
      if (seen.has(id)) return
      seen.add(id)
      items.push({ id, name: String(poi.name || '未命名地点'), kind: category.kind, distance: distanceLabel(center, Number(location.lng), Number(location.lat)), address: String(poi.address || poi.pname || ''), longitude: Number(location.lng), latitude: Number(location.lat), icon: category.kind === 'food' ? 'utensils' : category.kind === 'attraction' ? 'landmark' : 'hotel' })
    })
  })
  emit('recommendations', items)
  currentRecommendations = items
  drawRecommendationMarkers(items)
}
function drawRecommendationMarkers(items = currentRecommendations) {
  recommendationMarkers.forEach(marker => marker.setMap(null))
  recommendationMarkers = []
  if (!props.category) return
  const matching = items.filter(item => item.kind === props.category)
  const zoom = map.getZoom()
  const limits = zoom < 12 ? { attraction: 8, food: 0, hotel: 0 }
    : zoom < 13 ? { attraction: 12, food: 2, hotel: 0 }
      : zoom < 14 ? { attraction: 18, food: 4, hotel: 1 }
        : zoom < 15 ? { attraction: 28, food: 8, hotel: 2 }
          : zoom < 16 ? { attraction: 40, food: 16, hotel: 4 }
            : zoom < 17 ? { attraction: 50, food: 32, hotel: 8 }
              : { attraction: Infinity, food: Infinity, hotel: Infinity }
  const priority: Recommendation['kind'][] = [props.category]
  const spacing = zoom < 13 ? 46 : zoom < 14 ? 36 : zoom < 15 ? 24 : zoom < 16 ? 12 : 0
  const occupied: Array<{ left: number; right: number; top: number; bottom: number }> = []
  const visible: Recommendation[] = []
  for (const kind of priority) {
    const candidates = matching.filter(item => item.kind === kind)
      .sort((a, b) => distanceInMeters(a.distance) - distanceInMeters(b.distance))
    let count = 0
    for (const item of candidates) {
      if (count >= limits[kind]) break
      const pixel = map.lngLatToContainer([item.longitude, item.latitude])
      const width = Math.min(148, Math.max(44, [...item.name].reduce((sum, char) => sum + (char.charCodeAt(0) > 255 ? 10 : 6), 0)))
      const box = { left: pixel.x - width / 2, right: pixel.x + width / 2, top: pixel.y - 47, bottom: pixel.y }
      const overlaps = occupied.some(other => box.left < other.right + spacing && box.right + spacing > other.left && box.top < other.bottom + spacing && box.bottom + spacing > other.top)
      if (item.id !== focusedRecommendationId && spacing > 0 && overlaps) continue
      visible.push(item)
      occupied.push(box)
      count++
    }
  }
  if (focusedRecommendationId && !visible.some(item => item.id === focusedRecommendationId)) {
    const focused = matching.find(item => item.id === focusedRecommendationId)
    if (focused) visible.push(focused)
  }
  visible.forEach(item => {
    const content = document.createElement('div')
    content.className = `poi-marker poi-${item.kind}${item.id === focusedRecommendationId ? ' is-focused' : ''}`
    const icon = document.createElement('span')
    icon.className = 'poi-marker-icon'
    icon.setAttribute('aria-hidden', 'true')
    icon.innerHTML = item.kind === 'food'
      ? '<svg viewBox="0 0 24 24"><path d="M6 3v7m-3-7v4a3 3 0 0 0 6 0V3m-3 7v11m9-18v18m0-18c3 2 4 5 4 9h-4"/></svg>'
      : item.kind === 'hotel'
        ? '<svg viewBox="0 0 24 24"><path d="M3 20V5m0 11h18v4M3 9h7a3 3 0 0 1 3 3v4m0-4h5a3 3 0 0 1 3 3v1M6 12h.01"/></svg>'
        : '<svg viewBox="0 0 24 24"><path d="M3 21h18M5 21V8l7-5 7 5v13M9 21v-8h6v8M8 9h.01M16 9h.01"/></svg>'
    const name = document.createElement('span')
    name.className = 'poi-marker-name'
    name.textContent = item.name
    content.append(icon, name)
    const marker = new AMap.Marker({ position: [item.longitude, item.latitude], content, anchor: 'bottom-center', offset: new AMap.Pixel(0, 0), title: `${item.name} · ${poiCategories.find(category => category.kind === item.kind)?.label ?? ''}`, zIndex: item.id === focusedRecommendationId ? 120 : 90 })
    marker.on('click', () => focusRecommendation(item.id))
    marker.setMap(map)
    recommendationMarkers.push(marker)
  })
}
function distanceInMeters(distance: string) {
  const value = Number.parseFloat(distance)
  return distance.endsWith('km') ? value * 1000 : value
}
async function queryTravel(fromPlace: JourneyPlace, toPlace: JourneyPlace, mode: TravelMode): Promise<TravelRoute> {
  const key = routeKey(fromPlace, toPlace, mode)
  if (fromPlace.locationStatus !== 'matched' || toPlace.locationStatus !== 'matched' || !AMap) return { status: 'unavailable' }
  if (routeResults[key]?.status === 'ready') return routeResults[key]
  if (Date.now() - (failedRoutes.get(key) ?? 0) < 30000) return routeResults[key] ?? { status: 'unavailable' }
  routeResults = { ...routeResults, [key]: { status: 'loading' } }
  emit('routes', routeResults)
  if (!routeCache.has(key)) {
    if (routeCache.size > 200) routeCache.clear()
    const task = routeQueue.then(async () => {
      if (disposed) return null
      if (!store.plan.days.some(day => day.places.some((place, index) => day.places[index + 1] && routeKey(place, day.places[index + 1], mode) === key))) return null
      const originCity = fromPlace.city || store.plan.destination
      const destinationCity = toPlace.city || store.plan.destination
      const city = mode === 'transit' ? await transitCity(originCity) : originCity
      const cityd = mode === 'transit' ? await transitCity(destinationCity) : destinationCity
      const Service = { walking: AMap.Walking, cycling: AMap.Riding, driving: AMap.Driving, transit: AMap.Transfer }[mode]
      try {
        return await searchRoute(new Service({ map: null, hideMarkers: true, city, cityd }), [fromPlace.longitude, fromPlace.latitude], [toPlace.longitude, toPlace.latitude])
      } catch { return null }
    }).catch(() => null)
    routeQueue = task.then(() => new Promise(resolve => setTimeout(resolve, 700)), () => undefined)
    routeCache.set(key, task)
  }
  const normalized = normalizeRoute(await routeCache.get(key), mode)
  if (disposed) return normalized
  const validPair = store.plan.days?.some(day => day.places.some((place, index) => day.places[index + 1] && routeKey(place, day.places[index + 1], mode) === key))
  if (validPair) {
    routeResults = { ...routeResults, [key]: normalized }
    emit('routes', routeResults)
  }
  if (normalized.status !== 'ready') { failedRoutes.set(key, Date.now()); routeCache.delete(key) }
  return normalized
}
async function requestAlternatives(fromId: string, toId: string) {
  const places = store.places
  const index = places.findIndex(place => place.id === fromId)
  const from = places[index]
  const to = places[index + 1]
  if (!from || !to || to.id !== toId) return
  await Promise.all((['walking', 'cycling', 'driving', 'transit'] as const).map(mode => queryTravel(from, to, mode)))
}
async function redraw() {
  if (!map || !AMap) return
  const version = ++renderVersion
  markers.forEach(marker => marker.setMap(null))
  markers = []
  markerByPlace.clear()
  routeLines.forEach(line => line.setMap(null))
  routeLines = []
  const currentPlaces = store.places
  const days = store.plan.days ?? [{ places: currentPlaces }]
  const currentKeys = new Set(days.flatMap(day => day.places.slice(0, -1).flatMap((place, index) =>
    (['walking', 'cycling', 'driving', 'transit'] as const).map(mode => routeKey(place, day.places[index + 1], mode)))))
  routeResults = Object.fromEntries(Object.entries(routeResults).filter(([key]) => currentKeys.has(key)))
  emit('routes', routeResults)
  store.places.forEach((place, index) => {
    if (place.locationStatus !== 'matched') return
    const position: [number, number] = [place.longitude, place.latitude]
    const marker = new AMap.Marker({ position, content: `<div class="amap-route-marker ${store.selectedPlaceId === place.id ? 'active' : ''}">${index + 1}</div>`, offset: new AMap.Pixel(-13, -13), title: place.name })
    marker.on('click', () => store.selectPlace(place.id))
    marker.setMap(map)
    markers.push(marker)
    markerByPlace.set(place.id, marker)
  })
  // Finish the visible day first; other days contribute metrics, not map geometry.
  const groups = [currentPlaces, ...days.map(day => day.places).filter(places => places !== currentPlaces)]
  for (const located of groups) {
  for (let i = 0; i < located.length - 1; i++) {
    if (located[i].locationStatus !== 'matched' || located[i + 1].locationStatus !== 'matched') continue
    const mode = props.travelPreferences[`${located[i].id}:${located[i + 1].id}`] ?? 'walking'
    const normalized = await queryTravel(located[i], located[i + 1], mode)
    if (version !== renderVersion) return
    const path = normalized.path ?? []
    if (path.length && located === currentPlaces) {
      const line = new AMap.Polyline({ path, strokeColor: '#24a9e8', strokeWeight: 6, strokeOpacity: 0.92, lineJoin: 'round', lineCap: 'round', showDir: true })
      line.setMap(map)
      routeLines.push(line)
    }
  }
  if (located === currentPlaces) fitDayRoute()
  }
  fitDayRoute()
  nextTick(() => map?.resize())
}
function fitDayRoute() {
  if (!map || !markers.length) return
  const rightPadding = props.recommendationsOpen ? 390 : 48
  map.setFitView([...markers, ...routeLines], false, [54, rightPadding, 70, 48])
}
async function recenter() {
  if (!map || !AMap) return false
  await geocodePendingPlaces()
  await redraw()
  if (markers.length === 1) {
    const position = markers[0].getPosition()
    map.setZoomAndCenter(15, [position.lng, position.lat])
  } else if (!markers.length) {
    const center = await cityCenter(store.plan.destination)
    const destination = center ?? [113.295, 22.836]
    map.setZoomAndCenter(12, destination)
  }
  return true
}
async function focusPlace(placeId: string) {
  const place = store.places.find(item => item.id === placeId)
  if (!map || !place) return false
  store.selectPlace(placeId)
  if (place.locationStatus !== 'matched') {
    await geocodePendingPlaces()
    const resolvedPlace = store.places.find(item => item.id === placeId)
    if (!resolvedPlace) return false
    if (resolvedPlace.locationStatus !== 'matched') {
      locationTarget.value = resolvedPlace
      locationQuery.value = placeQuery(resolvedPlace.name)
      locationCandidates.value = []
      void searchLocationCandidates()
      return false
    }
    await redraw()
  }
  map.setZoomAndCenter(16, [place.longitude, place.latitude])
  return true
}
function focusRecommendation(placeId: string) {
  const item = currentRecommendations.find(candidate => candidate.id === placeId)
  if (!map || !item) return false
  focusedRecommendationId = placeId
  drawRecommendationMarkers()
  map.setZoomAndCenter(Math.max(map.getZoom(), 16), [item.longitude, item.latitude])
  return true
}
function refreshRecommendations() { void searchVisibleRecommendations() }
async function prepareRouteOptimization() {
  if (!map || !AMap) return false
  await geocodePendingPlaces()
  await redraw()
  return store.places.every(place => place.locationStatus === 'matched')
}
defineExpose({ recenter, focusPlace, focusRecommendation, refreshRecommendations, prepareRouteOptimization, requestAlternatives })
watch(() => props.category, () => drawRecommendationMarkers())
watch(() => store.plan.destination, () => { void refreshWeather() })
watch(() => props.travelPreferences, () => { void redraw() }, { deep: true })
watch(() => props.recommendationsOpen, () => fitDayRoute())
watch(() => [store.plan, store.activeDayId, allPlaces().map(place => `${place.id}:${place.name}:${place.city}`).join('|')] as const, async () => {
  if (mapReady.value) await geocodePendingPlaces()
  await redraw()
})
watch(() => store.selectedPlaceId, selectedId => {
  markerByPlace.forEach((marker, placeId) => {
    const index = store.places.findIndex(place => place.id === placeId)
    marker.setContent(`<div class="amap-route-marker ${selectedId === placeId ? 'active' : ''}">${index + 1}</div>`)
  })
})
onMounted(() => {
  initMap()
  if (mapElement.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => { map?.resize(); scheduleRecommendationSearch() })
    resizeObserver.observe(mapElement.value)
  }
})
onBeforeUnmount(() => { disposed = true; weatherVersion++; renderVersion++; clearTimeout(searchTimer); resizeObserver?.disconnect(); map?.destroy() })
</script>

<template>
  <Teleport to="body">
    <div v-if="locationTarget" class="location-dialog-backdrop" @click.self="locationTarget = undefined">
      <section class="location-dialog" role="dialog" aria-modal="true" aria-labelledby="location-dialog-title" @keydown.esc="locationTarget = undefined">
        <header><h2 id="location-dialog-title">确认地图地点</h2><button aria-label="关闭" @click="locationTarget = undefined">×</button></header>
        <p>{{ locationTarget.name }} · {{ locationTarget.city }}</p>
        <form @submit.prevent="searchLocationCandidates"><input v-model="locationQuery" aria-label="地点名称或地址" placeholder="搜索具体景点、餐厅或酒店" /><button :disabled="searchingLocation">搜索</button></form>
        <p v-if="searchingLocation" role="status">正在查找地点…</p>
        <p v-else-if="!locationCandidates.length">暂未找到地点，请补充具体名称后重试。</p>
        <button v-for="poi in locationCandidates" :key="poi.id" class="location-candidate" @click="chooseLocation(poi)"><strong>{{ poi.name }}</strong><span>{{ poi.cityname }} {{ poi.adname }} {{ poi.address }}</span></button>
      </section>
    </div>
  </Teleport>
  <div ref="mapElement" class="map-surface" :class="{ 'amap-ready': mapReady }">
    <template v-if="!mapReady">
      <div class="map-paper"><div class="map-block block-one"></div><div class="map-block block-two"></div><div class="map-block block-three"></div><div class="map-block block-four"></div><div class="park-shape park-one"></div><div class="park-shape park-two"></div><div class="waterway waterway-one"></div><div class="waterway waterway-two"></div><div class="map-road road-one"></div><div class="map-road road-two"></div><div class="map-road road-three"></div><div class="map-road road-four"></div><svg class="route-line" viewBox="0 0 100 100" preserveAspectRatio="none"><polyline :points="store.places.filter(place => place.locationStatus === 'matched').map(place => `${mapX(place.longitude).toFixed(1)},${mapY(place.latitude).toFixed(1)}`).join(' ')" /></svg><button v-for="(place, index) in store.places.filter(place => place.locationStatus === 'matched')" :key="place.id" class="map-pin" :class="{ active: store.selectedPlaceId === place.id }" :style="{ left: `${mapX(place.longitude)}%`, top: `${mapY(place.latitude)}%` }" :title="place.name" @click="store.selectPlace(place.id)"><span>{{ index + 1 }}</span></button></div>
      <div class="map-provider-note">路线示意图 <span>配置高德 Key 后显示真实地图</span></div>
    </template>
  </div>
</template>

<style scoped>
.location-dialog-backdrop { position: fixed; inset: 0; z-index: 1000; background: #132f3540; display: grid; place-items: center; padding: 20px; }
.location-dialog { width: min(440px, 100%); max-height: 80vh; overflow: auto; padding: 20px; background: white; border: 1px solid #dce8e7; border-radius: 8px; }
.location-dialog header, .location-dialog form { display: flex; align-items: center; gap: 12px; }
.location-dialog h2 { flex: 1; font-size: 18px; }
.location-dialog p, .location-candidate span { color: #70858a; font-size: 13px; overflow-wrap: anywhere; }
.location-dialog input { min-width: 0; flex: 1; padding: 10px; border: 1px solid #cadedd; border-radius: 4px; }
.location-dialog button { padding: 8px; cursor: pointer; }
.location-candidate { display: flex; flex-direction: column; gap: 6px; width: 100%; text-align: left; background: white; border: 0; border-bottom: 1px solid #e6eeee; }
.location-candidate:hover { background: #edf8f6; }
</style>
