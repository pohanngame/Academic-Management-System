import { http } from './http'

export async function getAiSettings() {
  const { data } = await http.get('/ai/settings')
  return data.data
}

export async function saveAiSettings(payload) {
  const { data } = await http.put('/ai/settings', payload)
  return data.data
}

export async function testAiSettings() {
  const { data } = await http.post('/ai/settings/test', null, { timeout: 65000 })
  return data.data
}

export async function deleteAiSettings() {
  const { data } = await http.delete('/ai/settings')
  return data.data
}
