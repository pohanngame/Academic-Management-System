import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { http } from '../services/http'

const TOKEN_KEY = 'academic_profile_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref(null)

  const isAuthenticated = computed(() => Boolean(token.value))

  function setSession(nextToken, nextUser) {
    token.value = nextToken
    user.value = nextUser
    localStorage.setItem(TOKEN_KEY, nextToken)
  }

  function clearSession() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  async function register(payload) {
    const { data } = await http.post('/auth/register', payload)
    setSession(data.data.token, data.data.user)
    return data.data
  }

  async function login(payload) {
    const { data } = await http.post('/auth/login', payload)
    setSession(data.data.token, data.data.user)
    return data.data
  }

  async function fetchCurrentUser() {
    if (!token.value) {
      return null
    }
    const { data } = await http.get('/auth/me')
    user.value = data.data
    return user.value
  }

  function logout() {
    clearSession()
  }

  return {
    token,
    user,
    isAuthenticated,
    setSession,
    clearSession,
    register,
    login,
    fetchCurrentUser,
    logout
  }
})
