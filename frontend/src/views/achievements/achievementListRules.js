export function displaySequence(index) {
  return index + 1
}

export function canReorderRecordCount(count) {
  return Number.isInteger(count) && count >= 0 && count <= 100
}

export function moveAdjacent(ids, id, direction) {
  const result = [...ids]
  const index = result.indexOf(id)
  const target = index + direction
  if (index < 0 || target < 0 || target >= result.length) return result
  ;[result[index], result[target]] = [result[target], result[index]]
  return result
}

export function normalizeSelectedIds(rows) {
  const ids = [...new Set((rows || []).map((row) => Number(row.id)).filter(Number.isInteger))]
  if (ids.length > 100) throw new Error('单次最多选择 100 条记录')
  return ids
}

export function buildBatchPatch(values) {
  return Object.fromEntries(Object.entries(values).filter(([, value]) => value !== null && value !== undefined))
}
