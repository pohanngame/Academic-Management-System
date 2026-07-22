import { http } from './http'

export async function getExportFields() {
  const { data } = await http.get('/export/fields')
  return data.data
}

export async function listExportTemplates() {
  const { data } = await http.get('/export/templates')
  return data.data
}

export async function saveExportTemplate(payload) {
  const { data } = await http.post('/export/templates', payload)
  return data.data
}

export async function deleteExportTemplate(id) {
  const { data } = await http.delete(`/export/templates/${id}`)
  return data.data
}

export async function downloadExportFile(type, payload) {
  const endpoint = type === 'WORD' ? '/export/word' : '/export/excel'
  const response = await http.post(endpoint, payload, { responseType: 'blob' })
  const blob = new Blob([response.data], {
    type: response.headers['content-type'] || 'application/octet-stream'
  })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileNameFromDisposition(response.headers['content-disposition']) || fallbackName(type)
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

function fallbackName(type) {
  return type === 'WORD' ? 'academic-profile.docx' : 'academic-profile.xlsx'
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
