import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import assert from 'node:assert/strict'
import ts from 'typescript'

const context = {}
const source = readFileSync(new URL('../src/utils/mapRequestQueue.ts', import.meta.url), 'utf8').replace(/export /g, '')
runInNewContext(ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, context)
const { createMapRequestQueue } = context
let release
const intervals = []
const queue = createMapRequestQueue({ limit: 3, sleep: async ms => { intervals.push(ms) } })
const calls = []
const active = queue.schedule('active', () => new Promise(resolve => { calls.push('active'); release = resolve }))
const background = queue.schedule('poi', async () => { calls.push('poi'); return 'poi' }, () => true, -1)
const route = queue.schedule('route', async () => { calls.push('route'); return 'route' }, () => true, 1)
assert.equal(queue.schedule('route', async () => { throw new Error('duplicate') }), route)
assert.equal((await queue.schedule('overflow', async () => {})).info, 'QUEUE_FULL')
release('active')
await Promise.all([active, background, route])
assert.deepEqual(calls, ['active', 'route', 'poi'])
assert.ok(intervals.every(ms => ms === 700))
const stale = await queue.schedule('stale', async () => { throw new Error('must not execute') }, () => false)
assert.equal(stale.info, 'CANCELLED')
assert.equal((await queue.schedule('throws', async () => { throw new Error('SDK failed') })).info, 'SERVICE_ERROR')
assert.equal(await queue.schedule('after-error', async () => 'ok'), 'ok')
let finish
const busy = queue.schedule('busy', () => new Promise(resolve => { finish = resolve }))
for (let i = 0; !finish && i < 10; i++) await Promise.resolve()
assert.equal(typeof finish, 'function')
const queued = queue.schedule('dispose', async () => { throw new Error('must not execute') })
queue.dispose()
assert.equal((await queued).info, 'CANCELLED')
finish('done')
await busy
assert.equal((await queue.schedule('new', async () => {})).info, 'CANCELLED')
console.log('A1 bounded shared queue: deduplication, priority, spacing, cancellation, exceptions and disposal passed.')
