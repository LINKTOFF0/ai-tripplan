import type { JourneyPlan, Recommendation } from '@/types/journey'

export const sampleJourney: JourneyPlan = {
  id: 'shunde-day-trip',
  title: '顺德区 1 日游',
  destination: '佛山市顺德区',
  days: [{
    id: 'day-1',
    dayNumber: 1,
    date: '2026-10-03',
    title: '岭南园林与寻味顺德',
    places: [
      { id: 'qinghui-garden', name: '清晖园博物馆', city: '佛山市顺德区', address: '大良清晖路 23 号', category: 'attraction', startTime: '09:00', durationMinutes: 90, description: '从岭南园林开始，晨间光线和人流都更舒适。', longitude: 113.2932, latitude: 22.8398, locationStatus: 'matched', icon: 'landmark' },
      { id: 'huagai-road', name: '华盖路步行街', city: '佛山市顺德区', address: '大良街道华盖路', category: 'other', startTime: '11:00', durationMinutes: 60, description: '骑楼老街散步，沿途可以尝试双皮奶与鱼皮。', longitude: 113.2918, latitude: 22.8408, locationStatus: 'matched', icon: 'store' },
      { id: 'shunde-restaurant', name: '珍之宝酒楼', city: '佛山市顺德区', address: '大良新城区彩虹路', category: 'food', startTime: '12:30', durationMinutes: 90, description: '顺德早茶与经典粤菜，周末建议提前取号。', longitude: 113.3007, latitude: 22.8351, locationStatus: 'matched', icon: 'utensils' },
      { id: 'happy-coast', name: '顺德欢乐海岸 PLUS', city: '佛山市顺德区', address: '大良街道欢乐大道', category: 'attraction', startTime: '15:30', durationMinutes: 180, description: '傍晚游园，日落后可以欣赏摩天轮夜景。', longitude: 113.3095, latitude: 22.8115, locationStatus: 'matched', icon: 'ferris-wheel' },
    ],
  }],
}

export const recommendations: Recommendation[] = [
  { id: 'jinbang-street', name: '金榜上街', kind: 'food', distance: '1.2 km', rating: '4.7', address: '大良金榜上街', longitude: 113.2875, latitude: 22.8366, icon: 'soup' },
  { id: 'shunfeng-mountain', name: '顺峰山公园', kind: 'attraction', distance: '2.8 km', rating: '4.8', address: '南国东路与驹荣南路交汇处', longitude: 113.3074, latitude: 22.8242, icon: 'trees' },
  { id: 'diesel-1959', name: '柴油机 1959', kind: 'attraction', distance: '3.1 km', address: '大良凤翔工业区', longitude: 113.2867, latitude: 22.8581, icon: 'factory' },
  { id: 'shunde-hotel-sample', name: '顺德住宿推荐', kind: 'hotel', distance: '2.4 km', address: '顺德区大良街道', longitude: 113.302, latitude: 22.842, icon: 'hotel' },
]
