import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import ts from 'typescript'

const source = readFileSync(new URL('../src/utils/journeyResponse.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const { applyRouteOrder, fromBackendPlan, canApplyJourneyResponse } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
const place = { id: 'p1', name: '珠海渔女', city: '珠海', address: '情侣中路', category: 'attraction', startTime: '', durationMinutes: 60, description: '原说明', advice: '用户建议', note: '用户备注', longitude: 113.59, latitude: 22.25, locationStatus: 'matched', icon: 'landmark' }
const sourcePlan = { id: 'trip', title: '珠海', destination: '珠海', days: [{ id: 'd1', dayNumber: 1, date: '2026-10-08', title: '主题', places: [place] }] }
const edited = fromBackendPlan(sourcePlan, true)
assert.equal(edited.days[0].places[0].longitude, 113.59)
assert.equal(edited.days[0].places[0].advice, '用户建议')
assert.equal(edited.days[0].places[0].note, '用户备注')
assert.equal(edited.days[0].places[0].id, 'p1')
assert.equal(fromBackendPlan(sourcePlan).days[0].places[0].locationStatus, 'pending', 'model output must not introduce unverified coordinates')
const snapshot = JSON.stringify(edited)
assert.equal(canApplyJourneyResponse(edited, snapshot), true)
edited.days[0].date = '2026-11-01'
assert.equal(canApplyJourneyResponse(edited, snapshot), false, 'stale response must not overwrite manual edits')
const pending = structuredClone(sourcePlan)
pending.days[0].places[0].locationStatus = 'pending'
assert.equal(fromBackendPlan(pending, true).days[0].places[0].longitude, 0)
assert.throws(() => fromBackendPlan({ days: [] }))
assert.throws(() => fromBackendPlan({ days: [{ places: [place, place] }] }))
assert.throws(() => fromBackendPlan({ days: [{ places: [{ id: 'missing-name' }] }] }))
const storage = new Map()
globalThis.localStorage = { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) }
const asModule = text => `data:text/javascript;base64,${Buffer.from(ts.transpileModule(text, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText).toString('base64')}`
const sampleUrl = asModule(readFileSync(new URL('../src/data/sampleJourney.ts', import.meta.url), 'utf8'))
const legacyUrl = asModule(readFileSync(new URL('../src/utils/legacyLocations.ts', import.meta.url), 'utf8'))
const storeSource = readFileSync(new URL('../src/stores/journey.ts', import.meta.url), 'utf8')
  .replace("from 'pinia'", `from '${import.meta.resolve('pinia')}'`)
  .replace("from 'vue'", `from '${import.meta.resolve('vue')}'`)
  .replace("from '@/data/sampleJourney'", `from '${sampleUrl}'`)
  .replace("from '@/utils/legacyLocations'", `from '${legacyUrl}'`)
const { createPinia, setActivePinia } = await import('pinia')
setActivePinia(createPinia())
const { useJourneyStore } = await import(asModule(storeSource))
const store = useJourneyStore()
store.replacePlan(fromBackendPlan(sourcePlan, true))
store.activeDayId = 'd1'
store.selectedPlaceId = 'p1'
store.applyPlanEdit(edited)
assert.equal(store.currentDay.date, '2026-11-01')
assert.equal(store.activeDayId, 'd1')
assert.equal(store.selectedPlaceId, 'p1')
assert.equal(store.selectedPlace.note, '用户备注')
assert.equal(store.selectedPlace.longitude, 113.59)
assert.throws(() => store.applyPlanEdit({ ...edited, id: 'other-trip' }))
console.log('Journey response checks passed: edits preserve fields, pending coordinates, stale response guard and invalid data rejection.')

const routePlan = structuredClone(sourcePlan)
routePlan.days[0].places.push({ ...place, id: 'p2', name: '日月贝', latitude: 22.28 }, { ...place, id: 'p3', name: '爱情邮局', latitude: 22.24 })
routePlan.days.push({ ...structuredClone(routePlan.days[0]), id: 'd2', dayNumber: 2, places: [{ ...place, id: 'p4' }] })
const reply = structuredClone(routePlan)
reply.days[0].places = [reply.days[0].places[2], reply.days[0].places[0], reply.days[0].places[1]]
reply.days[0].places[0].longitude = 1
reply.days[0].places[0].advice = '不应覆盖人工建议'
const ordered = applyRouteOrder(routePlan, reply, 'd1')
assert.deepEqual(ordered.days[0].places.map(p => p.id), ['p3', 'p1', 'p2'])
for (const p of ordered.days[0].places) assert.deepEqual(p, routePlan.days[0].places.find(old => old.id === p.id))
assert.deepEqual(ordered.days[1], routePlan.days[1])
for (const mutate of [
  p => p.days[0].places.pop(),
  p => p.days[0].places[1] = p.days[0].places[0],
  p => p.days[0].places[0].id = 'unknown',
  p => p.days[0].date = '2027-01-01',
  p => p.id = 'other-trip',
  p => p.days.reverse(),
]) {
  const invalid = structuredClone(reply)
  mutate(invalid)
  assert.throws(() => applyRouteOrder(routePlan, invalid, 'd1'))
}
assert.throws(() => applyRouteOrder(routePlan, reply, 'd2'), 'other day cannot change')
store.replacePlan(routePlan)
store.activeDayId = 'd1'
store.selectedPlaceId = 'p1'
store.applyPlanEdit(ordered)
assert.equal(store.activeDayId, 'd1')
assert.equal(store.selectedPlaceId, 'p1')
assert.equal(store.selectedPlace.advice, '用户建议')
const { nextTick } = await import('vue')
await nextTick()
assert.deepEqual(JSON.parse(storage.get('aitripplan.journey.v1')).days[0].places.map(p => p.id), ['p3', 'p1', 'p2'])
console.log('A2 exact permutations, original card preservation, other-day isolation and saved order passed.')
