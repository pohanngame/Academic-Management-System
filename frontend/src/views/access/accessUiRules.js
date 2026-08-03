export function mergeAttachmentsWithPolicies(files, policies) {
  const byResourceId = new Map(policies.map(policy => [policy.resourceId, policy]))
  return files
    .filter(file => byResourceId.has(file.id))
    .map(file => {
      const policy = byResourceId.get(file.id)
      return { ...file, ...policy, id: file.id, policyId: policy.id }
    })
}

export function grantAction(grant) {
  if (grant.permissionType === 'DOWNLOAD') return 'DOWNLOAD'
  if (grant.permissionType === 'VIEW') return 'PREVIEW'
  return null
}

export function isApprovalCandidateSelectable(candidate) {
  return candidate.allowed === true
}

export function canSubmitApproval(submitting, selectedCount) {
  return !submitting && selectedCount > 0
}

export function canBatchRequest(capabilities) {
  return capabilities?.canBatchRequest === true
}
