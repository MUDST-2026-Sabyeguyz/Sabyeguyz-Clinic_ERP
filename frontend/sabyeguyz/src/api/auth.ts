export type ApplicationRole = 'MANAGER' | 'THERAPIST'

export interface AuthResponse { id: number; username: string; role: ApplicationRole; token: string }

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export async function login(username: string, password: string): Promise<AuthResponse> {
  const response = await fetch(`${apiBaseUrl}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }),
  })
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new Error(body?.message ?? (response.status === 401 ? 'ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง' : 'เข้าสู่ระบบไม่สำเร็จ'))
  if (!body) throw new Error('เซิร์ฟเวอร์ส่งข้อมูลเข้าสู่ระบบไม่ครบถ้วน')
  return body
}
