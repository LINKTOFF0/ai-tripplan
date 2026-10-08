export function poiKind(poi: { type?: unknown; typecode?: unknown; name?: unknown }) {
  const type = String(poi.type ?? '')
  const code = String(poi.typecode ?? '')
  const name = String(poi.name ?? '')
  if (/购物|商场|商城|百货|零售|商业广场|购物中心/.test(`${type} ${name}`)) return null
  // Known incompatible names override noisy multi-type search results.
  if (/住宿|酒店|宾馆|民宿|旅馆|客栈/.test(`${type} ${name}`) || /^10\d{4}$/.test(code)) return 'hotel'
  if (type.startsWith('餐饮服务') || /^05\d{4}$/.test(code)) return 'food'
  if (type.startsWith('风景名胜') || /^11\d{4}$/.test(code)) return 'attraction'
  return null
}
