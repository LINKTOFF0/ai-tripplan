import type { JourneyPlan, JourneyPlace } from '@/types/journey'

export interface BackendJourneyPlan {
  id?: string
  title?: string
  destination?: string
  days?: Array<{ id?: string; dayNumber?: number; date?: string; title?: string; places?: Array<Partial<JourneyPlace>> }>
}

export function canApplyJourneyResponse(current: JourneyPlan, requestSnapshot: string): boolean {
  return JSON.stringify(current) === requestSnapshot
}

export function fromBackendPlan(source: BackendJourneyPlan, isEdit = false): JourneyPlan {
  if (!source || !Array.isArray(source.days) || !source.days.length) throw new Error('行程数据无效')
  const ids = new Set<string>()
  return {
    id: source.id || `journey-${Date.now()}`,
    title: source.title || 'AI 旅行计划',
    destination: source.destination || '目的地待确认',
    days: source.days.map((day, dayIndex) => {
      if (!Array.isArray(day.places)) throw new Error('地点数据无效')
      return {
        id: day.id || `day-${dayIndex + 1}`,
        dayNumber: day.dayNumber || dayIndex + 1,
        date: day.date || '',
        title: day.title || `第 ${dayIndex + 1} 天`,
        places: day.places.map((place, placeIndex) => {
          if (!place.name?.trim()) throw new Error('地点名称无效')
          const id = place.id || `day-${dayIndex + 1}-place-${placeIndex + 1}`
          if (ids.has(id)) throw new Error('地点标识重复')
          ids.add(id)
          const category = ['attraction', 'food', 'hotel', 'transport', 'other'].includes(place.category ?? '') ? place.category! : 'other'
          const matched = isEdit && place.locationStatus === 'matched' && Number.isFinite(place.longitude)
            && Number.isFinite(place.latitude) && Math.abs(place.longitude!) <= 180 && Math.abs(place.latitude!) <= 90
            && (place.longitude !== 0 || place.latitude !== 0)
          return {
            id, name: place.name, city: place.city || source.destination || '', address: place.address || '', category,
            startTime: place.startTime || '', durationMinutes: place.durationMinutes ?? 60, description: place.description || '',
            ...(isEdit && typeof place.note === 'string' ? { note: place.note } : {}),
            ...(isEdit && typeof place.advice === 'string' ? { advice: place.advice } : {}),
            longitude: matched ? place.longitude! : 0, latitude: matched ? place.latitude! : 0,
            locationStatus: matched ? 'matched' : 'pending',
            icon: isEdit && place.icon ? place.icon : category === 'food' ? 'utensils' : category === 'hotel' ? 'store' : 'landmark',
          }
        }),
      }
    }),
  }
}
