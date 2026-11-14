import { api } from './http'

export type AuthResponse = {
  staffId: number | null
  id: number
  username: string
  email?: string
  role: 'MANAGER' | 'THERAPIST'
  branchCode?: string
  branchName?: string
  token: string
}

export const authApi = {
  login: (identifier: string, password: string) =>
    api.post<AuthResponse>('/api/auth/login', {
      username: identifier,
      password,
    }),
  register: (username: string, password: string, role: 'MANAGER' | 'THERAPIST', email?: string) =>
    api.post<AuthResponse>('/api/auth/register', {
      username,
      email,
      password,
      role,
    }),
}
