import test from 'node:test'
import assert from 'node:assert/strict'

import {
  normalizeRegistrationPayload,
  registrationFieldError,
  shouldSubmitRegistration
} from '../src/views/auth/registerRules.js'

test('registration boundaries match the backend contract', () => {
  assert.ok(registrationFieldError('username', 'ab'))
  assert.equal(registrationFieldError('username', 'abc'), '')
  assert.equal(registrationFieldError('username', 'a'.repeat(64)), '')
  assert.ok(registrationFieldError('username', 'a'.repeat(65)))

  assert.ok(registrationFieldError('email', 'invalid'))
  assert.equal(registrationFieldError('email', 'teacher@example.com'), '')
  assert.ok(registrationFieldError('email', `${'a'.repeat(117)}@example.com`))

  assert.ok(registrationFieldError('password', 'a'.repeat(7)))
  assert.equal(registrationFieldError('password', 'a'.repeat(8)), '')
  assert.equal(registrationFieldError('password', 'a'.repeat(64)), '')
  assert.ok(registrationFieldError('password', 'a'.repeat(65)))

  assert.ok(registrationFieldError('displayName', '   '))
  assert.equal(registrationFieldError('displayName', 'a'.repeat(128)), '')
  assert.ok(registrationFieldError('displayName', 'a'.repeat(129)))
  assert.equal(registrationFieldError('department', 'a'.repeat(128)), '')
  assert.ok(registrationFieldError('department', 'a'.repeat(129)))
  assert.equal(registrationFieldError('title', 'a'.repeat(128)), '')
  assert.ok(registrationFieldError('title', 'a'.repeat(129)))
})

test('confirmation and normalization never rewrite passwords', () => {
  assert.ok(registrationFieldError('confirmPassword', '', { password: '' }))
  assert.ok(registrationFieldError('confirmPassword', 'different', { password: 'password' }))
  const payload = normalizeRegistrationPayload({
    username: ' teacher ', email: ' teacher@example.com ', displayName: ' 张老师 ',
    department: ' 计算机学院 ', title: ' 副教授 ', password: ' secret value ',
    confirmPassword: ' secret value '
  })
  assert.equal(payload.username, 'teacher')
  assert.equal(payload.email, 'teacher@example.com')
  assert.equal(payload.displayName, '张老师')
  assert.equal(payload.department, '计算机学院')
  assert.equal(payload.title, '副教授')
  assert.equal(payload.password, ' secret value ')
  assert.equal(payload.confirmPassword, ' secret value ')
})

test('invalid or loading registration cannot submit', () => {
  assert.equal(shouldSubmitRegistration({ valid: false, loading: false }), false)
  assert.equal(shouldSubmitRegistration({ valid: true, loading: true }), false)
  assert.equal(shouldSubmitRegistration({ valid: true, loading: false }), true)
})
