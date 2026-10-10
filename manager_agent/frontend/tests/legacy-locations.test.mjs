import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import ts from 'typescript'
const moduleUrl = file => {
  const code = ts.transpileModule(readFileSync(new URL(file, import.meta.url), 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
  }).outputText
  return `data:text/javascript;base64,${Buffer.from(code).toString('base64')}`
}
const { invalidateLegacySampleLocations } = await import(moduleUrl('../src/utils/legacyLocations.ts'))
const { sampleJourney } = await import(moduleUrl('../src/data/sampleJourney.ts'))
assert.ok(sampleJourney.days[0].places.every(p => p.locationStatus === 'pending' && p.longitude === 0 && p.latitude === 0))
const legacy = structuredClone(sampleJourney)
const coordinates = [[113.2932, 22.8398], [113.2918, 22.8408], [113.3007, 22.8351], [113.3095, 22.8115]]
legacy.days[0].places.forEach((place, i) => Object.assign(place, { longitude: coordinates[i][0], latitude: coordinates[i][1], locationStatus: 'matched' }))
const original = structuredClone(legacy)
invalidateLegacySampleLocations(legacy)
legacy.days[0].places.forEach((place, i) => {
  assert.equal(place.locationStatus, 'pending')
  assert.equal(place.longitude, 0)
  assert.equal(place.description, original.days[0].places[i].description)
  assert.equal(place.id, original.days[0].places[i].id)
})
assert.equal(legacy.days[0].date, original.days[0].date)
const confirmed = structuredClone(original)
confirmed.days[0].places[0].poiId = 'verified-poi'
confirmed.days[0].places[1].longitude += 0.001
invalidateLegacySampleLocations(confirmed)
assert.equal(confirmed.days[0].places[0].locationStatus, 'matched')
assert.equal(confirmed.days[0].places[1].locationStatus, 'matched')
const other = { ...structuredClone(original), id: 'another-trip' }
assert.deepEqual(invalidateLegacySampleLocations(other), { ...original, id: 'another-trip' })
console.log('Legacy demo coordinates invalidated; verified locations and user content preserved.')
