import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { login as loginRequest, type ApplicationRole } from '@/api/auth'

const tokenKey = 'clinic-erp-token'
const userKey = 'clinic-erp-user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(tokenKey) ?? '')
  const stored = localStorage.getItem(userKey)
  const user = ref<{ id: number; username: string; role: ApplicationRole } | null>(stored ? JSON.parse(stored) : null)
  const isAuthenticated = computed(() => token.value.length > 0)

  async function login(username: string, password: string) {
    const result = await loginRequest(username, password)
    token.value = result.token
    user.value = { id: result.id, username: result.username, role: result.role }
    localStorage.setItem(tokenKey, result.token)
    localStorage.setItem(userKey, JSON.stringify(user.value))
  }
  function logout() { token.value = ''; user.value = null; localStorage.removeItem(tokenKey); localStorage.removeItem(userKey) }
  return { token, user, isAuthenticated, login, logout }
})
