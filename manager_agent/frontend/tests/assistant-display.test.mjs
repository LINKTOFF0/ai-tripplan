import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import ts from 'typescript'

const source = readFileSync(new URL('../src/components/AssistantPanel.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('panel.ts', script, ts.ScriptTarget.Latest, true)
const functions = ast.statements.filter(node => ts.isFunctionDeclaration(node)
  && ['reset', 'send', 'optimizeRoute', 'analyzeItinerary'].includes(node.name?.text)).map(node => node.getText(ast)).join('\n')
const code = ts.transpileModule(functions, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
const messages = { value: [] }
const requests = []
const context = {
  props: { travelPreferences: { 'a:b': 'walking' }, travelDefaults: { short: 'walking', long: 'driving' } },
  messages, journeyStore: { plan: { days: [{ id: 'day-1', places: [] }] }, activeDayId: 'day-1' },
  sending: { value: false }, controller: { value: undefined }, input: { value: '' },
  workflowStage: { value: 0 }, backendOnline: { value: false },
  isWelcomeMessage: () => false, scrollToBottom() {}, emit() {},
  window: { setInterval: () => 1, clearInterval() {} },
  fetch: async (url, options) => {
    requests.push(JSON.parse(options.body))
    return new Response('data: {"type":"TEXT","text":"ok"}\n\ndata: {"type":"DONE"}\n\n')
  },
}
const execute = new Function(...Object.keys(context), `${code}; return { optimizeRoute, send, analyzeItinerary }`)(...Object.values(context))
await execute.optimizeRoute()
assert.equal(messages.value[0].displayText, '线路优化')
assert.ok(messages.value[0].text.startsWith('请调用线路智能体'))
assert.equal(requests[0].task, 'ROUTE_OPTIMIZATION')
assert.ok(requests[0].prompt.includes('不固定首站或末站'))
assert.ok(requests[0].prompt.includes('工具失败或关键数据缺失时保留原顺序'))
assert.deepEqual(requests[0].travelPreferences, context.props.travelPreferences)
assert.equal(requests[0].activeDayId, 'day-1')
await execute.send('普通问题')
assert.equal(messages.value[2].text, '普通问题')
assert.equal(messages.value[2].displayText, undefined)
assert.equal(requests[1].history[0].text, messages.value[0].text)
assert.ok(source.includes('{{ message.displayText || message.text }}'))
const originalPlan = JSON.stringify(context.journeyStore.plan)
await execute.analyzeItinerary()
assert.equal(messages.value[4].displayText, '智能解析')
assert.equal(requests[2].task, 'GENERAL', 'text planning must use the read-only policy')
assert.ok(requests[2].prompt.includes('当前全部日期'))
assert.ok(requests[2].prompt.includes('完整、可直接阅读的文字旅游规划'))
assert.ok(requests[2].prompt.includes('不新增、删除、替换或修改行程卡片'))
assert.ok(requests[2].prompt.includes('不虚构精确时间'))
assert.deepEqual(requests[2].journeyPlan, context.journeyStore.plan)
assert.deepEqual(requests[2].travelDefaults, context.props.travelDefaults)
assert.equal(JSON.stringify(context.journeyStore.plan), originalPlan)
context.sending.value = true
await execute.analyzeItinerary()
assert.equal(requests.length, 3, 'repeated clicks must not abort or submit another request')
console.log('Short labels preserve complete requests; itinerary analysis reads context without editing cards.')
