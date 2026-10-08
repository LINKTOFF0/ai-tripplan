import assert from 'node:assert/strict'

if (!process.argv.includes('--live')) throw new Error('Requires --live: this test calls the configured real model and map service.')
const endpoint = 'http://127.0.0.1:8081'
const place = (id, name, longitude, latitude) => ({ id, name, city: '珠海', address: '', category: 'attraction', startTime: '', durationMinutes: 60, description: '', advice: '保留我的建议', note: '保留我的备注', longitude, latitude, locationStatus: 'matched', icon: 'landmark' })
const plan = {
  id: 'integration-only', title: '联调专用行程', destination: '珠海',
  days: [{ id: 'day-1', dayNumber: 1, date: '2026-10-08', title: '海岸线', places: [place('p1', '珠海渔女', 113.593, 22.255), place('p2', '珠海日月贝', 113.595, 22.279)] }],
}
async function request(name, prompt, task, journeyPlan = plan, verify = () => {}) {
  const started = Date.now()
  const response = await fetch(`${endpoint}/trip/stream`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt, task, journeyPlan, activeDayId: 'day-1' }),
    signal: AbortSignal.timeout(180000),
  })
  assert.equal(response.status, 200)
  const raw = await response.text()
  const events = raw.split('\n').filter(line => line.startsWith('data:')).map(line => JSON.parse(line.slice(5).trim()))
  assert.ok(events.some(event => event.type === 'DONE'), `${name}: missing DONE`)
  assert.ok(!events.some(event => event.type === 'ERROR'), `${name}: ${events.filter(event => event.type === 'ERROR').map(event => event.text).join('; ')}`)
  const updated = events.find(event => event.type === 'JOURNEY_PLAN')?.journeyPlan
  const text = events.filter(event => event.type === 'TEXT').map(event => event.text).join('')
  verify(updated, text, events)
  console.log(JSON.stringify({ name, seconds: Number(((Date.now() - started) / 1000).toFixed(1)), updated: !!updated, answer: text.slice(0, 1000) }))
  return { updated, text, events }
}

const ping = await fetch(`${endpoint}/trip`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ prompt: '__ping__' }) })
assert.equal((await ping.json()).response, 'pong')
console.log('Backend health: pong')
const selected = process.argv.find(value => value.startsWith('--case='))?.slice(7) ?? 'edits'
if (selected === 'edits') {
  await Promise.all([
    request('date-edit-a', '请把第一天日期改为2026-11-01，保留其他字段。', 'EDIT_DATE', plan, updated => {
      assert.equal(updated.days[0].date, '2026-11-01'); assert.equal(updated.days[0].places[0].longitude, 113.593)
      assert.equal(updated.days[0].places[0].note, '保留我的备注')
    }),
    request('date-edit-b', '请把第一天日期改为2026-12-02，保留其他字段。', 'EDIT_DATE', { ...plan, id: 'parallel-trip', destination: '顺德' }, updated => {
      assert.equal(updated.id, 'parallel-trip'); assert.equal(updated.days[0].date, '2026-12-02')
    }),
  ])
  await request('advice-edit', '把珠海渔女的游玩建议改为：傍晚看日落，注意防晒。', 'EDIT_ADVICE', plan, updated => {
    assert.equal(updated.days[0].places[0].advice, '傍晚看日落，注意防晒。'); assert.equal(updated.days[0].places[1].advice, '保留我的建议')
  })
  await request('order-edit', '请交换第一天两个地点的顺序，珠海日月贝在前，珠海渔女在后。', 'EDIT_ORDER', plan, updated => {
    assert.deepEqual(updated.days[0].places.map(place => place.id), ['p2', 'p1'])
  })
} else if (selected === 'map') {
  await request('weather-only', '查询珠海当前天气，不要修改行程。', 'WEATHER', plan, updated => assert.equal(updated, undefined))
  await request('single-route', '查询珠海渔女到珠海日月贝的步行路线距离和时间，使用已给卡片坐标；不要修改行程。', 'ROUTE_QUERY', plan, updated => assert.equal(updated, undefined))
  console.log('Map event/degradation checks only: successful requests do not prove provider data availability. Inspect tool results for INVALID_USER_KEY.')
} else if (selected === 'route-remote') {
  const threePlaces = structuredClone(plan)
  threePlaces.days[0].places.push(place('p3', '爱情邮局', 113.599, 22.242))
  await request('route-remote', '请实际调用线路制定智能体比较第一天三个已确认地点的顺序，然后应用它建议的卡片排序。固定珠海渔女为第一站，不增删地点；地图工具不可用时明确说明，不能编造真实交通距离和时间。', 'ROUTE_OPTIMIZATION', threePlaces, (updated, text) => {
    if (!updated) {
      assert.match(text, /INVALID_USER_KEY|鉴权|地图.*不可用|无法.*交通/)
      console.log('DEGRADED: route provider unavailable; no card reorder applied.')
      return
    }
    assert.equal(updated.days[0].places[0].id, 'p1')
    assert.deepEqual(updated.days[0].places.map(place => place.id).sort(), ['p1', 'p2', 'p3'])
  })
} else if (selected === 'agents') {
  await request('route-agent', '优化第一天线路，珠海渔女作为起点，保留两个地点。需要比较时调用线路智能体，再实际应用卡片顺序。', 'ROUTE_OPTIMIZATION', plan, updated => {
    assert.ok(updated); assert.equal(updated.days[0].places[0].id, 'p1'); assert.equal(updated.days[0].places.length, 2)
  })
  await request('planner-agent', '请生成珠海一天行程，只安排已确认的珠海渔女和珠海日月贝两个景点，不新增地点，不查天气；调用行程智能体输出卡片。', 'PLAN', plan, updated => {
    assert.ok(updated?.days?.length); assert.ok(updated.days.flatMap(day => day.places).length >= 2)
  })
} else throw new Error('Unknown case')
console.log(`Live ${selected} integration checks passed.`)
