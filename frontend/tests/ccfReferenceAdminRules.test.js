import test from 'node:test'
import assert from 'node:assert/strict'

import {
  canSubmitCcfImport,
  sanitizeCcfImportError,
  shouldShowCcfReferenceAdmin
} from '../src/views/admin/ccfReferenceAdminRules.js'

test('only super administrators can see reference data administration', () => {
  assert.equal(shouldShowCcfReferenceAdmin('SUPER_ADMIN'), true)
  assert.equal(shouldShowCcfReferenceAdmin('TEACHER'), false)
})

test('json and current password are required and duplicate submission is blocked', () => {
  const file = { name: 'ccf.json', size: 128 }
  assert.equal(canSubmitCcfImport(file, 'password', false), true)
  assert.equal(canSubmitCcfImport(file, '', false), false)
  assert.equal(canSubmitCcfImport({ name: 'ccf.txt', size: 10 }, 'password', false), false)
  assert.equal(canSubmitCcfImport(file, 'password', true), false)
})

test('internal paths are never displayed as import errors', () => {
  assert.equal(sanitizeCcfImportError('C:\\app\\tmp\\secret.json'), 'CCF 参考数据导入失败')
  assert.equal(sanitizeCcfImportError('/app/tmp/secret.json'), 'CCF 参考数据导入失败')
})
