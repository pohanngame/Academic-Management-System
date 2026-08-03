import { http } from './http'

async function get(path, params) {
  const { data } = await http.get(path, { params })
  return data.data
}

export const getTeacherDirectory = (params) => get('/access/directory', params)
export async function getAllTeacherDirectory(params = {}) {
  const items = []
  let page = 1
  let total = 0
  do {
    const result = await getTeacherDirectory({ ...params, page, size: 100 })
    items.push(...result.items)
    total = result.total
    if (result.items.length === 0) break
    page += 1
  } while (items.length < total)
  return items
}
export const getAccessCapabilities = () => get('/access/capabilities')
export const getMyAccessRequests = (params) => get('/access/requests/mine', params)
export const getPendingAccessRequests = (params) => get('/access/requests/pending', params)
export const getApprovalCandidates = (id) => get(`/access/requests/${id}/candidates`)
export const getAccessGrants = (params) => get('/access/grants', params)
export const getResourceAudits = (params) => get('/access/audits', params)
export const getSharedResources = (params) => get('/access/resources', params)
export const getSharedResource = (resourceType, id) => get(`/access/resources/${resourceType}/${id}`)
export const getResourcePolicies = (resourceType) => get('/access/policies', { resourceType })
export const getAiEligibleAttachments = () => get('/access/attachments/ai-eligible')

export async function createAccessRequest(payload) {
  const { data } = await http.post('/access/requests', payload)
  return data.data
}

export async function decideAccessRequest(id, payload) {
  const { data } = await http.patch(`/access/requests/${id}/decision`, payload)
  return data.data
}

export const cancelAccessRequest = (id) => http.delete(`/access/requests/${id}`)
export const revokeAccessGrant = (id) => http.delete(`/access/grants/${id}`)

export async function updateResourcePolicies(items) {
  const { data } = await http.put('/access/policies', { items })
  return data.data
}
