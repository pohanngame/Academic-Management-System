import { http } from './http'

const endpoints = {
  academicQualifications: '/teacher/academic-qualifications',
  teachingAreas: '/teacher/teaching-areas',
  researchAreas: '/teacher/research-areas',
  professionalServices: '/teacher/professional-services',
  workingExperiences: '/teacher/working-experiences'
}

export async function listBlock(type) {
  const { data } = await http.get(endpoints[type])
  return data.data
}

export async function createBlock(type, payload) {
  const { data } = await http.post(endpoints[type], payload)
  return data.data
}

export async function updateBlock(type, id, payload) {
  const { data } = await http.put(`${endpoints[type]}/${id}`, payload)
  return data.data
}

export async function deleteBlock(type, id) {
  const { data } = await http.delete(`${endpoints[type]}/${id}`)
  return data.data
}
