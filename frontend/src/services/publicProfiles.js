import { http } from './http'

export async function getPublicProfile(slug) {
  const response = await http.get(`/public/profiles/${slug}`)
  return response.data.data
}

export function publicAvatarUrl(fileId) {
  return fileId ? `/api/public/avatars/${fileId}` : ''
}
