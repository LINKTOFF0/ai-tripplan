import type { JourneyPlace } from '../types/journey'

export type TravelMode = 'walking' | 'cycling' | 'driving' | 'transit'
export interface TravelDefaults { short: TravelMode; long: TravelMode }
export function preferredMode(from: JourneyPlace, to: JourneyPlace, defaults: TravelDefaults): TravelMode {
  if (from.locationStatus !== 'matched' || to.locationStatus !== 'matched') return defaults.short
  const radians = Math.PI / 180
  const lat = (to.latitude - from.latitude) * radians
  const lng = (to.longitude - from.longitude) * radians
  const a = Math.sin(lat / 2) ** 2 + Math.cos(from.latitude * radians) * Math.cos(to.latitude * radians) * Math.sin(lng / 2) ** 2
  const distance = 6371000 * 2 * Math.asin(Math.min(1, Math.sqrt(a)))
  return distance > 1000 ? defaults.long : defaults.short
}
export interface TravelRoute {
  status: 'loading' | 'ready' | 'unavailable'
  distance?: string
  distanceMeters?: number
  duration?: string
  path?: [number, number][]
  reason?: 'no_data' | 'rate_limit' | 'service_error' | 'queue_full'
  checkedAt?: number
  retryAt?: number
}
export function routeKey(from: JourneyPlace, to: JourneyPlace, mode: TravelMode) {
  return JSON.stringify([from.id, from.longitude, from.latitude, to.id, to.longitude, to.latitude, mode, from.city, to.city])
}
export function normalizeRoute(result: any, mode: TravelMode): TravelRoute {
  const route = mode === 'transit' ? result?.plans?.[0] : result?.routes?.[0]
  if (!route) return { status: 'unavailable', reason: result?.info === 'NO_DATA' ? 'no_data' : result?.info === 'CUQPS_HAS_EXCEEDED_THE_LIMIT' ? 'rate_limit' : result?.info === 'QUEUE_FULL' ? 'queue_full' : 'service_error' }
  const distance = Number(route.distance)
  const time = Number(route.time)
  if (!Number.isFinite(distance) || distance < 0 || !Number.isFinite(time) || time < 0
      || route.distance == null || route.time == null) return { status: 'unavailable' }
  const points = mode === 'transit'
    ? (route.path?.length ? route.path : route.segments?.flatMap((segment: any) => segment.transit?.path?.length ? segment.transit.path : (segment.transit?.steps ?? segment.walking?.steps ?? []).flatMap((step: any) => step.path ?? [])) ?? [])
    : (route.steps ?? route.rides ?? []).flatMap((step: any) => step.path ?? [])
  const path = points.map((point: any) => [Number(point.lng ?? point[0]), Number(point.lat ?? point[1])])
    .filter((point: number[]) => point.every(Number.isFinite)) as [number, number][]
  // A route without geometry cannot keep the card and the map in agreement.
  if (path.length < 2) return { status: 'unavailable' }
  return { status: 'ready', distanceMeters: distance, distance: distance < 1000 ? `${Math.round(distance)} 米` : `${(distance / 1000).toFixed(1)} 公里`, duration: `${Math.max(1, Math.ceil(time / 60))} 分钟`, path }
}

export function summarizeRoutes(days: { places: JourneyPlace[] }[], preferences: Record<string, TravelMode>, routes: Record<string, TravelRoute>) {
  let meters = 0
  let ready = 0
  let total = 0
  for (const day of days) {
    day.places.slice(0, -1).forEach((from, index) => {
      total++
      const to = day.places[index + 1]
      const route = routes[routeKey(from, to, preferences[`${from.id}:${to.id}`] ?? 'walking')]
      if (route?.status === 'ready' && Number.isFinite(route.distanceMeters)) {
        ready++
        meters += route.distanceMeters!
      }
    })
  }
  if (!total) return '暂无交通路段'
  if (!ready) return '里程待查询'
  const distance = `${(meters / 1000).toFixed(1)} km`
  return ready === total ? distance : `${distance}（已查 ${ready}/${total} 段）`
}
