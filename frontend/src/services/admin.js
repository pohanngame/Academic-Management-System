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
