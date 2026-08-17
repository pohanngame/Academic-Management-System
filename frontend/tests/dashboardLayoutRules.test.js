import test from 'node:test'
import assert from 'node:assert/strict'

import {
  readSidebarCollapsed,
  saveSidebarCollapsed,
  sidebarWidth
} from '../src/layouts/dashboardLayoutRules.js'

test('invalid persisted sidebar values safely default to expanded', () => {
  assert.equal(readSidebarCollapsed(null), false)
  assert.equal(readSidebarCollapsed('invalid'), false)
  assert.equal(readSidebarCollapsed('true'), true)
  assert.equal(readSidebarCollapsed('false'), false)
})

test('sidebar state persists as a simple boolean and controls width', () => {
  const values = []
  saveSidebarCollapsed(true, (key, value) => values.push([key, value]))
  saveSidebarCollapsed(false, (key, value) => values.push([key, value]))
  assert.deepEqual(values.map((entry) => entry[1]), ['true', 'false'])
  assert.equal(sidebarWidth(false), '248px')
  assert.equal(sidebarWidth(true), '64px')
})
