const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface AuthResponse { id: number; username: string; role: 'ADMIN' | 'STAFF' | 'THERAPIST'; token: string }
export async function login(username: string, password: string): Promise<AuthResponse> {
  const response = await fetch(`${base}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) })
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(body.message ?? 'เข้าสู่ระบบไม่สำเร็จ')
  return body
}
export const authToken = () => localStorage.getItem('clinic.jwt')
export const authHeaders = (): Record<string, string> => authToken() ? { Authorization: `Bearer ${authToken()}` } : {}
export const clearAuth = () => localStorage.removeItem('clinic.jwt')
