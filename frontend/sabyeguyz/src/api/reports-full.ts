import { authFetch } from './http'
const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
export type ReportKind = 'appointments' | 'revenue' | 'course-balances' | 'staff-sales'
export async function loadReport<T>(kind: ReportKind, from: string, to: string): Promise<T> {
  const response = await authFetch(`${base}/api/reports/${kind}?from=${from}&to=${to}`)
  const body = await response.json().catch(() => undefined)
  if (!response.ok) throw new Error(body?.message ?? 'ไม่สามารถโหลดรายงานได้')
  return body
}
