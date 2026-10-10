interface Coordinates { longitude: number; latitude: number }
interface Poi { location?: { lng: number; lat: number } }

function valid(longitude: number, latitude: number) {
  return Number.isFinite(longitude) && Number.isFinite(latitude)
    && Math.abs(longitude) <= 180 && Math.abs(latitude) <= 90
}

export function rankPoiCandidates<T extends Poi>(pois: T[], origin?: Coordinates) {
  const located = origin && valid(origin.longitude, origin.latitude)
  return pois.map(poi => {
    const longitude = Number(poi.location?.lng)
    const latitude = Number(poi.location?.lat)
    let distanceMeters: number | undefined
    if (located && valid(longitude, latitude)) {
      const radians = Math.PI / 180
      const lat = (latitude - origin.latitude) * radians
      const lng = (longitude - origin.longitude) * radians
      const a = Math.sin(lat / 2) ** 2 + Math.cos(origin.latitude * radians)
        * Math.cos(latitude * radians) * Math.sin(lng / 2) ** 2
      distanceMeters = 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, a))))
    }
    return { poi, distanceMeters }
  }).sort((a, b) => (a.distanceMeters ?? Infinity) - (b.distanceMeters ?? Infinity))
}
