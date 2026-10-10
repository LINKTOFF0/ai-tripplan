import type { JourneyPlan } from '@/types/journey'

const legacyPoints: Record<string, [string, number, number]> = {
  'qinghui-garden': ['清晖园博物馆', 113.2932, 22.8398],
  'huagai-road': ['华盖路步行街', 113.2918, 22.8408],
  'shunde-restaurant': ['珍之宝酒楼', 113.3007, 22.8351],
  'happy-coast': ['顺德欢乐海岸 PLUS', 113.3095, 22.8115],
}

// Only invalidate known demo coordinates, never unrelated user-confirmed locations.
export function invalidateLegacySampleLocations(plan: JourneyPlan): JourneyPlan {
  if (plan.id !== 'shunde-day-trip') return plan
  for (const day of plan.days) for (const place of day.places) {
    const legacy = legacyPoints[place.id]
    if (!legacy || place.poiId || place.name !== legacy[0]
        || place.longitude !== legacy[1] || place.latitude !== legacy[2]) continue
    place.longitude = 0
    place.latitude = 0
    place.locationStatus = 'pending'
  }
  return plan
}
