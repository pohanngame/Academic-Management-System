import test from 'node:test'
import assert from 'node:assert/strict'

import {
  homeRouteForRole,
  isDashboardPathAllowed,
  safePostLoginTarget
} from '../src/router/roleNavigation.js'

test('SUPER_ADMIN and TEACHER have separate default landing pages', () => {
  assert.equal(homeRouteForRole('SUPER_ADMIN'), '/dashboard/admin')
  assert.equal(homeRouteForRole('TEACHER'), '/dashboard')
})

test('redirect is accepted only inside the authenticated role boundary', () => {
  assert.equal(
    safePostLoginTarget('/dashboard/admin/organization', 'SUPER_ADMIN'),
    '/dashboard/admin/organization')
  assert.equal(
    safePostLoginTarget('/dashboard/ai-generation', 'SUPER_ADMIN'),
    '/dashboard/admin')
  assert.equal(
    safePostLoginTarget('/dashboard/admin', 'TEACHER'),
    '/dashboard')
  assert.equal(
    safePostLoginTarget('//example.test/dashboard', 'TEACHER'),
    '/dashboard')
})

test('dashboard route access is role isolated', () => {
  assert.equal(isDashboardPathAllowed('/dashboard/papers', 'SUPER_ADMIN'), false)
  assert.equal(isDashboardPathAllowed('/dashboard/admin', 'SUPER_ADMIN'), true)
  assert.equal(isDashboardPathAllowed('/dashboard/admin/organization', 'TEACHER'), false)
  assert.equal(isDashboardPathAllowed('/dashboard/access', 'TEACHER'), true)
})
