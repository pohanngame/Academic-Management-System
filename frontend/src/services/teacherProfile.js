import { http } from './http'

export async function getTeacherProfile() {
  const { data } = await http.get('/teacher/profile')
  return data.data
}

export async function updateTeacherProfile(payload) {
  const { data } = await http.put('/teacher/profile', payload)
  return data.data
}
