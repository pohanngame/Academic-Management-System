import test from 'node:test'
import assert from 'node:assert/strict'

import {
  canBatchRequest,
  canSubmitApproval,
  grantAction,
  isApprovalCandidateSelectable,
  mergeAttachmentsWithPolicies
} from '../src/views/access/accessUiRules.js'

test('attachment id is never replaced by policy id', () => {
  const rows = mergeAttachmentsWithPolicies(
    [{ id: 40, originalName: 'proof.pdf' }],
    [{ id: 900, resourceId: 40, viewAllowed: true }])

  assert.equal(rows[0].id, 40)
  assert.equal(rows[0].policyId, 900)
})

test('DOWNLOAD shows download while AI_USE has no binary action', () => {
  assert.equal(grantAction({ permissionType: 'DOWNLOAD' }), 'DOWNLOAD')
  assert.equal(grantAction({ permissionType: 'VIEW' }), 'PREVIEW')
  assert.equal(grantAction({ permissionType: 'AI_USE' }), null)
})

test('closed policy candidates cannot be selected', () => {
  assert.equal(isApprovalCandidateSelectable({ allowed: true }), true)
  assert.equal(isApprovalCandidateSelectable({ allowed: false }), false)
})

test('approval submission and batch request are guarded', () => {
  assert.equal(canSubmitApproval(false, 1), true)
  assert.equal(canSubmitApproval(true, 1), false)
  assert.equal(canSubmitApproval(false, 0), false)
  assert.equal(canBatchRequest({ canBatchRequest: false }), false)
  assert.equal(canBatchRequest({ canBatchRequest: true }), true)
})
