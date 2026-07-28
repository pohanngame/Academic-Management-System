import { http } from './http'

export async function getAdminOverview() {
  const { data } = await http.get('/admin/overview')
  return data.data
}

export async function getAdminUsers(params) {
  const { data } = await http.get('/admin/users', { params })
  return data.data
}

export async function changeAdminUserRole(id, role) {
  const { data } = await http.patch(`/admin/users/${id}/role`, { role })
  return data.data
}

export async function changeAdminUserStatus(id, status) {
  const { data } = await http.patch(`/admin/users/${id}/status`, { status })
  return data.data
}

export async function getAdminAuditLogs(params) {
  const { data } = await http.get('/admin/audit-logs', { params })
  return data.data
}

export async function exportSystemBackup(currentPassword) {
  return http.post(
    '/admin/backups/export',
    { currentPassword },
    { responseType: 'blob', timeout: 0 }
  )
}

export async function getStagedRestores() {
  const { data } = await http.get('/admin/restores/staged')
  return data.data
}

export async function stageRestorePackage(file, currentPassword) {
  const form = new FormData()
  form.append('file', file)
  form.append('currentPassword', currentPassword)
  const { data } = await http.post('/admin/restores/staged', form, {
    timeout: 0
  })
  return data.data
}

export async function deleteStagedRestore(id, currentPassword) {
  await http.delete(`/admin/restores/staged/${id}`, {
    data: { currentPassword }
  })
}
