import { http } from './http'

export async function importBibtexText(content) {
  const { data } = await http.post('/bibtex/import/text', { content })
  return data.data
}

export async function importBibtexFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await http.post('/bibtex/import/file', formData)
  return data.data
}

export async function listBibtexTasks() {
  const { data } = await http.get('/bibtex/import-tasks')
  return data.data
}

export async function getBibtexTask(id) {
  const { data } = await http.get(`/bibtex/import-tasks/${id}`)
  return data.data
}

export async function updateBibtexItem(id, payload) {
  const { data } = await http.put(`/bibtex/import-items/${id}`, payload)
  return data.data
}

export async function ignoreBibtexItem(id) {
  const { data } = await http.delete(`/bibtex/import-items/${id}`)
  return data.data
}

export async function confirmBibtexTask(id, itemIds, forceDuplicates = false) {
  const { data } = await http.post(`/bibtex/import-tasks/${id}/confirm`, {
    itemIds,
    forceDuplicates
  })
  return data.data
}
