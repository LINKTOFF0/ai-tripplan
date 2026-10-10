import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import ts from 'typescript'
const source = readFileSync(new URL('../src/utils/plannedDates.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const { plannedDateSummary } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
assert.equal(plannedDateSummary([]), '日期待定')
assert.equal(plannedDateSummary([{ date: '' }]), '日期待定')
assert.equal(plannedDateSummary([{ date: '2026-10-03' }]), '2026-10-03')
assert.equal(plannedDateSummary([{ date: '2026-10-05' }, { date: '2026-10-03' }]), '2026-10-03 至 2026-10-05')
assert.equal(plannedDateSummary([{ date: '2026-10-03' }, { date: '' }]), '2026-10-03 · 部分待定')
assert.equal(plannedDateSummary([{ date: '2026-10-03' }, { date: '2026-10-03' }]), '2026-10-03')
console.log('Planned date summary covers single, multiple, missing and independently ordered dates.')
