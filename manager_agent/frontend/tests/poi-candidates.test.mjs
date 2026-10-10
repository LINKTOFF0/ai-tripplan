import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import ts from 'typescript'
const code = ts.transpileModule(readFileSync(new URL('../src/utils/poiCandidates.ts', import.meta.url), 'utf8'), {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText
const { rankPoiCandidates } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
const origin = { longitude: 113.25, latitude: 22.80 }
const far = { id: 'far', location: { lng: 113.30, lat: 22.85 } }
const near = { id: 'near', location: { lng: 113.251, lat: 22.801 } }
const invalid = { id: 'invalid', location: { lng: NaN, lat: 22 } }
const items = [far, invalid, near]
const ranked = rankPoiCandidates(items, origin)
assert.deepEqual(ranked.map(item => item.poi.id), ['near', 'far', 'invalid'])
assert.ok(ranked[0].distanceMeters < ranked[1].distanceMeters)
assert.deepEqual(items.map(item => item.id), ['far', 'invalid', 'near'])
assert.deepEqual(rankPoiCandidates(items).map(item => item.poi.id), ['far', 'invalid', 'near'])
assert.ok(rankPoiCandidates(items).every(item => item.distanceMeters === undefined))
assert.deepEqual(rankPoiCandidates([near, { ...near, id: 'same' }], origin).map(item => item.poi.id), ['near', 'same'])
assert.equal(rankPoiCandidates([near], { longitude: NaN, latitude: 22 })[0].distanceMeters, undefined)
assert.equal(rankPoiCandidates([{ id: 'at-origin', location: { lng: origin.longitude, lat: origin.latitude } }], origin)[0].distanceMeters, 0)
console.log('POI candidates sort by previous-place straight-line distance without auto-selecting or mutating results.')
