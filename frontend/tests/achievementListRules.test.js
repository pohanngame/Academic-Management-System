import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildBatchPatch,
  canReorderRecordCount,
  displaySequence,
  moveAdjacent,
  normalizeSelectedIds
} from '../src/views/achievements/achievementListRules.js'

test('database sortOrder zeroes are presented as stable one-based sequence', () => {
  assert.deepEqual([0, 0, 0].map((_, index) => displaySequence(index)), [1, 2, 3])
})

test('adjacent movement preserves all ids and refuses list boundaries', () => {
  assert.deepEqual(moveAdjacent([3, 2, 1], 2, -1), [2, 3, 1])
  assert.deepEqual(moveAdjacent([3, 2, 1], 3, -1), [3, 2, 1])
  assert.deepEqual(moveAdjacent([3, 2, 1], 1, 1), [3, 2, 1])
})

test('batch selection is unique and limited to 100 records', () => {
  assert.deepEqual(normalizeSelectedIds([{ id: 2 }, { id: 2 }, { id: 3 }]), [2, 3])
  assert.throws(() => normalizeSelectedIds(Array.from({ length: 101 }, (_, index) => ({ id: index + 1 }))))
})

test('three-state batch patch only contains explicitly chosen fields', () => {
  assert.deepEqual(buildBatchPatch({ isPublic: false, viewAllowed: null, downloadAllowed: true }), {
    isPublic: false,
    downloadAllowed: true
  })
})

test('reorder is explicitly unavailable when a type has more than 100 records', () => {
  assert.equal(canReorderRecordCount(100), true)
  assert.equal(canReorderRecordCount(101), false)
})
