import { http } from './http'

export async function searchPublicationVenues(query, type, limit = 20) {
  const { data } = await http.get('/publication-venues/search', { params: { query, type, limit } })
  return data.data
}

export async function getJournalMetricSuggestions(params) {
  const { data } = await http.get('/journal-metrics/suggestions', { params })
  return data.data
}

export async function previewPaperRatings(page = 1, size = 20) {
  const { data } = await http.get('/teacher/papers/ratings/preview', { params: { page, size } })
  return data.data
}

export async function confirmPaperRatings(items, replaceExisting = false) {
  const { data } = await http.post('/teacher/papers/ratings/confirm', { items, replaceExisting })
  return data.data
}
