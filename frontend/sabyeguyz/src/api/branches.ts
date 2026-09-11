export interface Branch { id: number; code: string; numericCode: string; name: string; active: boolean }
export interface BranchPayload { code: string; numericCode: string; name: string }
import { authHeaders } from './auth'
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${url}`, { headers: { 'Content-Type': 'application/json', ...authHeaders() }, ...options })
  if (!response.ok) throw new Error((await response.json().catch(() => ({}))).message || 'ไม่สามารถดำเนินการกับข้อมูลสาขาได้')
  return response.status === 204 ? undefined as T : response.json()
}
export const listBranches = () => request<Branch[]>('/api/branches')
export const createBranch = (payload: BranchPayload) => request<Branch>('/api/branches', { method: 'POST', body: JSON.stringify(payload) })
export const updateBranch = (id: number, payload: BranchPayload) => request<Branch>(`/api/branches/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
export const deactivateBranch = (id: number) => request<void>(`/api/branches/${id}`, { method: 'DELETE' })
