import { http } from './http'

export const allowedFileAccept = '.pdf,.jpg,.jpeg,.png,.webp'
export const allowedImageAccept = '.jpg,.jpeg,.png,.webp'
export const allowedWordTemplateAccept = '.docx'

export async function uploadFile(file, businessType, businessId = null) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('businessType', businessType)
  if (businessId !== null && businessId !== undefined) {
    formData.append('businessId', businessId)
  }
  const { data } = await http.post('/files/upload', formData)
  return data.data
}

export async function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await http.post('/teacher/profile/avatar', formData)
  return data.data
}

export async function listFiles(businessType, businessId = null) {
  const params = { businessType }
  if (businessId !== null && businessId !== undefined) {
    params.businessId = businessId
  }
  const { data } = await http.get('/files', { params })
  return data.data
}

export async function deleteFile(id) {
  const { data } = await http.delete(`/files/${id}`)
  return data.data
}

export async function downloadFile(file) {
  const response = await http.get(`/files/${file.id}/download`, {
    responseType: 'blob'
  })
  const blob = new Blob([response.data], {
    type: response.headers['content-type'] || file.mimeType || 'application/octet-stream'
  })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileNameFromDisposition(response.headers['content-disposition']) || file.originalName
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

export async function createFileObjectUrl(id, fallbackMimeType = 'application/octet-stream') {
  const response = await http.get(`/files/${id}/download`, {
    responseType: 'blob'
  })
  const blob = new Blob([response.data], {
    type: response.headers['content-type'] || fallbackMimeType
  })
  return window.URL.createObjectURL(blob)
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
