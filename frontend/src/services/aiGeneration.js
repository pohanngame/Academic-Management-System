import { http } from './http'

export async function getAiModules() {
  const { data } = await http.get('/ai/modules')
  return data.data
}

export async function createAiTask(payload) {
  const { data } = await http.post('/ai/tasks', payload)
  return data.data
}

export async function listAiTasks() {
  const { data } = await http.get('/ai/tasks')
  return data.data
}

export async function getAiTask(id) {
  const { data } = await http.get(`/ai/tasks/${id}`)
  return data.data
}

export async function updateAiResult(id, editedContent) {
  const { data } = await http.put(`/ai/results/${id}`, { editedContent })
  return data.data
}

export async function confirmAiTask(id) {
  const { data } = await http.post(`/ai/tasks/${id}/confirm`)
  return data.data
}

export async function deleteAiTask(id) {
  const { data } = await http.delete(`/ai/tasks/${id}`)
  return data.data
}

export async function downloadAiWord(task) {
  const response = await http.get(`/ai/tasks/${task.id}/word`, { responseType: 'blob' })
  const blob = new Blob([response.data], {
    type: response.headers['content-type'] || 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileNameFromDisposition(response.headers['content-disposition']) || `ai-generation-${task.id}.docx`
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

export async function downloadAiPdf(task) {
  const response = await http.get(`/ai/tasks/${task.id}/pdf`, {
    responseType: 'blob',
    timeout: 75000
  })
  const blob = new Blob([response.data], {
    type: response.headers['content-type'] || 'application/pdf'
  })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileNameFromDisposition(response.headers['content-disposition']) || `ai-generation-${task.id}.pdf`
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

function fileNameFromDisposition(disposition) {
  if (!disposition) {
    return ''
  }
  const encodedMatch = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (encodedMatch) {
    return decodeURIComponent(encodedMatch[1])
  }
  const plainMatch = disposition.match(/filename="?([^"]+)"?/i)
  return plainMatch?.[1] || ''
}
