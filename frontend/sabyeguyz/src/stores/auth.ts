import { defineStore } from 'pinia'
import { authToken, clearAuth, login } from '../api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({ token: authToken() as string | null, username: localStorage.getItem('clinic.username') as string | null }),
  getters: { isAuthenticated: (state) => Boolean(state.token) },
  actions: {
    async login(username: string, password: string) {
      const result = await login(username, password)
      this.token = result.token; this.username = result.username
      localStorage.setItem('clinic.jwt', result.token); localStorage.setItem('clinic.username', result.username)
    },
    logout() { this.token = null; this.username = null; clearAuth(); localStorage.removeItem('clinic.username') },
  },
})
