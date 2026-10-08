import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import assert from 'node:assert/strict'
import ts from 'typescript'

function helpers(file) {
  const source = readFileSync(new URL(file, import.meta.url), 'utf8').replace(/^import .*$/gm, '').replace(/export /g, '')
  const context = {}
  runInNewContext(ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, context)
  return context
}
const { normalizeRoute, routeKey, summarizeRoutes, preferredMode } = helpers('../src/utils/travelRoutes.ts')
const path = [{ lng: 113, lat: 22 }, { lng: 114, lat: 23 }]
for (const mode of ['walking', 'driving']) {
  const result = normalizeRoute({ routes: [{ distance: 1250, time: 301, steps: [{ path }] }] }, mode)
  assert.equal(result.status, 'ready')
  assert.equal(result.distance, '1.3 公里')
  assert.equal(result.duration, '6 分钟')
  assert.equal(result.path.length, 2)
  assert.equal(result.distanceMeters, 1250)
}
assert.equal(normalizeRoute({ routes: [{ distance: 500, time: 120, rides: [{ path }] }] }, 'cycling').status, 'ready')
assert.equal(normalizeRoute({ plans: [{ distance: 600, time: 360, path }] }, 'transit').status, 'ready')
assert.equal(normalizeRoute({ plans: [{ distance: 600, time: 360, segments: [{ transit: { path } }] }] }, 'transit').path.length, 2)
assert.equal(normalizeRoute({ plans: [{ distance: 600, time: 360, path: [], segments: [{ transit: { steps: [{ path }] } }] }] }, 'transit').path.length, 2)
for (const input of [null, {}, { routes: [{ distance: 1 }] }, { routes: [{ distance: -1, time: 5 }] }, { routes: [{ distance: 100, time: 20, steps: [] }] }]) {
  assert.equal(normalizeRoute(input, 'walking').status, 'unavailable')
}
const from = { id: 'a', longitude: 113, latitude: 22, city: '珠海' }
const to = { ...from, id: 'b', latitude: 23 }
assert.equal(preferredMode({ ...from, locationStatus: 'matched' }, { ...from, latitude: 22.001, locationStatus: 'matched' }, { short: 'walking', long: 'driving' }), 'walking')
assert.equal(preferredMode({ ...from, locationStatus: 'matched' }, { ...to, locationStatus: 'matched' }, { short: 'walking', long: 'transit' }), 'transit')
assert.notEqual(routeKey(from, to, 'walking'), routeKey(from, to, 'driving'))
assert.notEqual(routeKey(from, to, 'walking'), routeKey(to, from, 'walking'))
assert.notEqual(routeKey(from, to, 'walking'), routeKey(from, { ...to, latitude: 24 }, 'walking'))
const days = [{ places: [from, to] }, { places: [to, from] }]
const metrics = { [routeKey(from, to, 'walking')]: { status: 'ready', distanceMeters: 1250 } }
assert.equal(summarizeRoutes(days, {}, metrics), '1.3 km（已查 1/2 段）')
metrics[routeKey(to, from, 'walking')] = { status: 'ready', distanceMeters: 600 }
assert.equal(summarizeRoutes(days, {}, metrics), '1.9 km')
assert.equal(summarizeRoutes(days, { 'a:b': 'transit' }, metrics), '0.6 km（已查 1/2 段）')
assert.equal(summarizeRoutes([{ places: [from] }], {}, metrics), '暂无交通路段')
assert.equal(summarizeRoutes(days, {}, {}), '里程待查询')
const { poiKind } = helpers('../src/utils/poiCategory.ts')
assert.equal(poiKind({ name: '投影复式LOFT民宿', type: '风景名胜' }), 'hotel')
assert.equal(poiKind({ name: '未知地点' }), null)
assert.equal(poiKind({ typecode: '110101' }), 'attraction')
assert.equal(poiKind({ typecode: '050100' }), 'food')
assert.equal(poiKind({ typecode: '100101' }), 'hotel')
assert.equal(poiKind({ name: '购物中心', type: '风景名胜' }), null)
console.log('Route normalization, coordinate/mode cache isolation and POI classification passed.')

// Exercise the actual map redraw with fake SDK services, not a duplicate implementation.
const source = readFileSync(new URL('../src/components/JourneyMap.vue', import.meta.url), 'utf8')
const redraw = source.slice(source.indexOf('async function queryTravel('), source.indexOf('function fitDayRoute()'))
const calls = []
const fakeService = mode => class { constructor() { this.mode = mode } }
const context = {
  map: { resize() {} }, AMap: { Walking: fakeService('walking'), Driving: fakeService('driving'), Riding: fakeService('cycling'), Transfer: fakeService('transit'),
    Pixel: class {}, Marker: class { on() {} setMap() {} }, Polyline: class { constructor(options) { this.path = options.path } setMap() {} } },
  renderVersion: 0, markers: [], markerByPlace: new Map(), routeLines: [], routeCache: new Map(), routeResults: {},
  routeQueue: Promise.resolve(), failedRoutes: new Map(), disposed: false, setTimeout: callback => { callback(); return 0 },
  store: { places: [{ ...from, locationStatus: 'matched' }, { ...to, locationStatus: 'matched' }], plan: { destination: '珠海' } },
  props: { travelPreferences: {} }, emit() {}, routeKey, normalizeRoute, transitCity: async () => '0756',
  searchRoute: async service => { calls.push(service.mode); return service.mode === 'transit'
    ? { plans: [{ distance: 600, time: 300, path }] }
    : { routes: [{ distance: 600, time: 300, steps: [{ path }], rides: [{ path }] }] } },
  fitDayRoute() {}, nextTick: callback => callback(),
}
context.store.plan.days = [{ places: context.store.places }]
runInNewContext(ts.transpileModule(redraw, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, context)
for (const mode of ['walking', 'driving', 'cycling', 'transit']) {
  context.props.travelPreferences['a:b'] = mode
  await context.redraw()
  assert.equal(context.routeLines.length, 1)
  assert.equal(context.routeResults[routeKey(from, to, mode)].status, 'ready')
}
assert.deepEqual(calls, ['walking', 'driving', 'cycling', 'transit'])
await context.redraw()
assert.equal(calls.length, 4, 'successful routes are cached')
console.log('Actual map redraw uses selected SDK service and emits matching card metrics.')
await context.requestAlternatives('a', 'b')
assert.equal(calls.length, 4, 'opening alternatives reuses all four cached routes')
context.routeResults = {}
context.routeCache.clear()
const beforeAlternatives = calls.length
await Promise.all([context.requestAlternatives('a', 'b'), context.requestAlternatives('a', 'b')])
assert.equal(calls.length - beforeAlternatives, 4, 'concurrent menu opens deduplicate four requests')
for (const mode of ['walking', 'cycling', 'driving', 'transit']) assert.equal(context.routeResults[routeKey(from, to, mode)].status, 'ready')
const transitKey = routeKey(from, to, 'transit')
delete context.routeResults[transitKey]
context.routeCache.delete(transitKey)
context.searchRoute = async () => { calls.push('no-data'); return { info: 'NO_DATA' } }
await context.queryTravel(context.store.places[0], context.store.places[1], 'transit')
const failedCount = calls.length
await context.queryTravel(context.store.places[0], context.store.places[1], 'transit')
assert.equal(calls.length, failedCount, 'failed query has a cooldown instead of repeated requests')
assert.equal(context.routeResults[transitKey].reason, 'no_data')
context.searchRoute = async service => ({ routes: [{ distance: 600, time: 300, steps: [{ path }] }] })
context.props.travelPreferences['a:b'] = 'walking'

context.store.plan.days = [{ places: context.store.places }, { places: [context.store.places[1], context.store.places[0]] }]
await context.redraw()
assert.equal(context.routeLines.length, 1, 'other days must not draw onto current day map')
assert.equal(context.routeResults[routeKey(to, from, 'walking')].status, 'ready')
context.store.plan.days.pop()
await context.redraw()
assert.equal(context.routeResults[routeKey(to, from, 'walking')], undefined, 'deleted day metrics are removed')

const weatherSource = source.slice(source.indexOf('async function refreshWeather()'), source.indexOf('let resizeObserver:'))
const weatherCalls = []
let weatherResponse = { temperature: '28', weather: '阴', city: '顺德区', reportTime: '2026-10-08 16:00:00' }
const weatherContext = {
  weatherVersion: 0, setTimeout, clearTimeout, store: { plan: { destination: '佛山市顺德区' } },
  emit: (event, value) => weatherCalls.push(value),
  AMap: { Geocoder: class { getLocation(city, callback) { callback('complete', { geocodes: [{ adcode: '440606' }] }) } },
    Weather: class { getLive(adcode, callback) { assert.equal(adcode, '440606'); callback(null, weatherResponse) } } },
}
runInNewContext(ts.transpileModule(weatherSource, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, weatherContext)
await weatherContext.refreshWeather()
assert.equal(weatherCalls.at(-1).status, 'ready')
assert.equal(weatherCalls.at(-1).reportTime, weatherResponse.reportTime)
weatherResponse = { temperature: 'invalid' }
await weatherContext.refreshWeather()
assert.equal(weatherCalls.at(-1).status, 'unavailable')
console.log('Multi-day total, incomplete metrics and real weather success/failure cases passed.')

const app = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
const settingsSource = app.slice(app.indexOf('function saveTravelDefaults()'), app.indexOf('function toggleTravelMenu('))
const saved = new Map()
const settingsContext = {
  travelDefaults: { value: { short: 'walking', long: 'driving' } }, preferenceDraft: { value: { short: 'cycling', long: 'transit' } },
  applyToExisting: { value: false }, travelPreferences: { value: { 'a:b': 'walking', 'other:pair': 'driving' } },
  effectiveTravelPreferences: { value: { 'a:b': 'walking' } }, preferenceDialog: { value: true },
  localStorage: { setItem: (key, value) => saved.set(key, value) }, notify() {},
}
runInNewContext(ts.transpileModule(settingsSource, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, settingsContext)
settingsContext.saveTravelDefaults()
assert.equal(settingsContext.travelPreferences.value['a:b'], 'walking', 'save default preserves individual override')
assert.equal(JSON.parse(saved.get('aitripplan.travel-defaults.v1')).long, 'transit')
settingsContext.applyToExisting.value = true
settingsContext.saveTravelDefaults()
assert.equal(settingsContext.travelPreferences.value['a:b'], undefined, 'explicit apply clears current journey overrides')
assert.equal(settingsContext.travelPreferences.value['other:pair'], 'driving', 'unrelated journey override is preserved')
console.log('Saved defaults and optional application preserve independent leg preferences.')
