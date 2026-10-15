import { http } from './http'

export async function getMajors(includeInactive = true) {
  const { data } = await http.get('/admin/organization/majors', { params: { includeInactive } })
  return data.data
}

export async function createMajor(payload) {
  const { data } = await http.post('/admin/organization/majors', payload)
  return data.data
}

export async function updateMajor(id, payload) {
  const { data } = await http.put(`/admin/organization/majors/${id}`, payload)
  return data.data
}

export async function getTeacherOrganization(teacherId) {
  const { data } = await http.get(`/admin/organization/teachers/${teacherId}`)
  return data.data
}

export async function getOrganizationTeachers() {
  const { data } = await http.get('/admin/organization/teachers')
  return data.data
}

export async function updateTeacherOrganization(teacherId, payload) {
  const { data } = await http.put(`/admin/organization/teachers/${teacherId}`, payload)
  return data.data
}

export async function getTimetableTeacherLinks() {
  const { data } = await http.get('/admin/timetable/teacher-links')
  return data.data
}

export async function saveTimetableTeacherLink(payload) {
  const { data } = await http.post('/admin/timetable/teacher-links', payload)
  return data.data
}

export async function deleteTimetableTeacherLink(sourceName) {
  const { data } = await http.delete('/admin/timetable/teacher-links', { params: { sourceName } })
  return data.data
}
