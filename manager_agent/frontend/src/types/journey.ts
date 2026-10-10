export type PlaceCategory = 'attraction' | 'food' | 'hotel' | 'transport' | 'other'
export type LocationStatus = 'pending' | 'matched' | 'ambiguous' | 'failed'

export interface JourneyPlace {
  id: string
  name: string
  city: string
  address: string
  category: PlaceCategory
  startTime: string
  durationMinutes: number
  description: string
  note?: string
  advice?: string
  longitude: number
  latitude: number
  locationStatus: LocationStatus
  poiId?: string
  icon: string
}

export interface JourneyDay {
  id: string
  dayNumber: number
  date: string
  title: string
  places: JourneyPlace[]
}

export interface JourneyPlan {
  id: string
  title: string
  destination: string
  days: JourneyDay[]
}

export interface Recommendation {
  id: string
  name: string
  kind: 'food' | 'attraction' | 'hotel'
  distance: string
  rating?: string
  address: string
  longitude: number
  latitude: number
  icon: string
}
