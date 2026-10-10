import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import assert from 'node:assert/strict'
import ts from 'typescript'

const source = readFileSync(new URL('../src/components/JourneyMap.vue', import.meta.url), 'utf8')
const helpers = source.slice(source.indexOf('function placeQuery('), source.indexOf('async function searchLocationCandidates'))
const resolver = source.slice(source.indexOf('async function geocodePendingPlaces()'), source.indexOf('function searchRoute('))
const code = ts.transpileModule(`${helpers}\n${resolver}`, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
const poi = { name: '珠海渔女', address: '情侣中路', location: { lng: 113.59, lat: 22.25 } }
const place = (name) => ({ name, city: '珠海', locationStatus: 'pending' })
async function check(name, results, expected) {
  const target = place(name)
  let calls = 0
  const context = { resolving: new WeakMap(), attempted: new WeakSet(), allPlaces: () => [target], searchPlaces: async () => { calls++; return results } }
  runInNewContext(code, context)
  await Promise.all([context.geocodePendingPlaces(), context.geocodePendingPlaces()])
  assert.equal(target.locationStatus, expected)
  assert.equal(calls, 1, 'concurrent watchers must share one lookup')
  if (expected === 'matched') assert.equal(target.longitude, 113.59)
}
await check('珠海渔女', [poi], 'matched')
await check('珠海渔女（海滨）夜景', [poi], 'matched')
await check('吉大商圈（景山路一带）午餐', [{ ...poi, name: '吉大商圈' }], 'ambiguous')
await check('珠海渔女', [poi, { ...poi, id: 'duplicate' }], 'ambiguous')
await check('没有此地点', [], 'failed')
console.log('5 location resolution cases passed, including concurrent lookup deduplication.')
const searchSource = source.slice(source.indexOf('async function searchPlaces('), source.indexOf('function placeQuery('))
let searchOptions
const searches = {
  transitCity: async () => '0757', setTimeout, clearTimeout, Number,
  AMap: { PlaceSearch: class {
    constructor(options) { searchOptions = options }
    search(query, callback) { callback('complete', { poiList: { pois: [
      { ...poi, citycode: '0757' }, { ...poi, citycode: '010' }, { ...poi, location: { lng: 'invalid', lat: 22 } },
    ] } }) }
  } },
}
runInNewContext(ts.transpileModule(searchSource, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, searches)
assert.equal((await searches.searchPlaces('清晖园', '佛山市顺德区')).length, 1)
assert.equal(searchOptions.city, '0757')
assert.equal(searchOptions.citylimit, true)
searches.transitCity = async () => '佛山市顺德区'
assert.equal((await searches.searchPlaces('清晖园', '佛山市顺德区')).length, 0, 'unresolved city must not silently search nationwide')
console.log('City-code scoping rejects foreign-city and malformed-coordinate POIs.')
searches.transitCity = async () => '440606'
assert.ok((await searches.searchPlaces('清晖园', '佛山市顺德区')).length > 0)
assert.equal(searchOptions.city, '440606', 'district adcode is also a valid search scope')
const fitSource = source.slice(source.indexOf('function fitDayRoute()'), source.indexOf('async function recenter()'))
const focusSource = source.slice(source.indexOf('async function focusPlace('), source.indexOf('async function relocatePlace('))
const focused = { id: 'garden', locationStatus: 'matched', longitude: 113.25, latitude: 22.80 }
const mapMoves = []
const focusContext = {
  focusedPlaceId: '', markers: [{}], routeLines: [], props: {},
  store: { places: [focused], selectPlace() {} },
  map: { setZoomAndCenter: (zoom, center) => mapMoves.push(center), setFitView: () => mapMoves.push('fit-all') },
}
runInNewContext(ts.transpileModule(`${fitSource}\n${focusSource}`, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, focusContext)
await focusContext.focusPlace('garden')
focusContext.fitDayRoute()
assert.deepEqual(Array.from(mapMoves.at(-1)), [113.25, 22.80], 'late route redraw must retain the clicked card focus')
assert.ok(!mapMoves.includes('fit-all'))
focusContext.store.places = [{ id: 'garden', locationStatus: 'pending', longitude: 0, latitude: 0 }]
focusContext.geocodePendingPlaces = async () => { focusContext.store.places = [{ ...focused, longitude: 113.26 }] }
focusContext.redraw = async () => {}
await focusContext.focusPlace('garden')
assert.deepEqual(Array.from(mapMoves.at(-1)), [113.26, 22.80], 'focus must use current resolved card, not stale object coordinates')
console.log('Card focus survives asynchronous route redraw and uses resolved coordinates.')
