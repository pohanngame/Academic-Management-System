import { http } from './http'

const endpoints = {
  projects: '/teacher/projects',
  teachingCourses: '/teacher/teaching-courses',
  papers: '/teacher/papers',
  patents: '/teacher/patents',
  certificates: '/teacher/certificates'
}

export async function listRecord(type) {
  const { data } = await http.get(endpoints[type])
  return data.data
}

export async function createRecord(type, payload) {
  const { data } = await http.post(endpoints[type], payload)
  return data.data
}

export async function updateRecord(type, id, payload) {
  const { data } = await http.put(`${endpoints[type]}/${id}`, payload)
  return data.data
}

export async function deleteRecord(type, id) {
  const { data } = await http.delete(`${endpoints[type]}/${id}`)
  return data.data
}
