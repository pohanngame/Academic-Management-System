import { http } from './http'

export async function createOcrTask(fileId, targetType) {
  const { data } = await http.post('/ocr/tasks', { fileId, targetType }, { timeout: 70000 })
  return data.data
}

export async function listOcrTasks() {
  const { data } = await http.get('/ocr/tasks')
  return data.data
}

export async function getOcrTask(id) {
  const { data } = await http.get(`/ocr/tasks/${id}`)
  return data.data
}

export async function updateOcrResult(id, payload) {
  const { data } = await http.put(`/ocr/results/${id}`, payload)
  return data.data
}

export async function ignoreOcrResult(id) {
  const { data } = await http.delete(`/ocr/results/${id}`)
  return data.data
}

export async function confirmOcrTask(id, resultIds) {
  const { data } = await http.post(`/ocr/tasks/${id}/confirm`, { resultIds })
  return data.data
}

export async function confirmCertificateOcrTask(id, resultIds, competitionCategory) {
  const { data } = await http.post(`/ocr/tasks/${id}/confirm`, { resultIds, competitionCategory })
  return data.data
}

export async function uploadCertificateOcr(file) {
  const form = new FormData()
  form.append('file', file)
  const { data } = await http.post('/ocr/certificates/quick', form, {
    headers: { 'Content-Type': 'multipart/form-data' }, timeout: 70000
  })
  return data.data
}

export async function cancelOcrTask(id) {
  await http.delete(`/ocr/tasks/${id}`)
}

export async function retryOcrTask(id) {
  const { data } = await http.post(`/ocr/tasks/${id}/retry`, null, { timeout: 70000 })
  return data.data
}
