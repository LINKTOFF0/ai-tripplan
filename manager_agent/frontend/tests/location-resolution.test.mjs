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
