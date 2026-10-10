import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import ts from 'typescript'

const source = readFileSync(new URL('../src/components/PlacePlannedTime.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('time.ts', script, ts.ScriptTarget.Latest, true)
const updateSource = ast.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === 'update').getText(ast)
const code = ts.transpileModule(updateSource, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
const values = []
const update = new Function('emit', `${code}; return update`)((event, value) => {
  assert.equal(event, 'update:modelValue')
  values.push(value)
})
for (const value of ['00:00', '08:30', '23:59', '']) update({ target: { value } })
assert.deepEqual(values, ['00:00', '08:30', '23:59', ''])
for (const value of ['24:00', '09:60', '8:30', 'abc']) update({ target: { value } })
assert.equal(values.length, 4, 'invalid times must not update the plan')
assert.ok(source.includes('@pointerdown.stop'), 'time editing must not start card dragging')
assert.ok(source.includes('@click.stop'), 'time editing must not toggle card advice')
const app = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
assert.ok(app.includes('v-model="place.startTime"'), 'edit the existing plan field used in AI context and persistence')
console.log('Planned arrival time accepts valid times and clearing, rejects invalid times, and isolates card interactions.')
