export interface Staff { id: number; userId: number; username: string; role: 'ADMIN' | 'STAFF' | 'THERAPIST'; employeeCode: string; fullName: string; phone: string | null; active: boolean }
export interface StaffPayload { userId: number; employeeCode: string; fullName: string; phone?: string }
const base = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
async function request<T>(url: string, options?: RequestInit): Promise<T> { const response = await fetch(`${base}${url}`, { headers: { 'Content-Type': 'application/json' }, ...options }); if (!response.ok) throw new Error((await response.json().catch(() => ({}))).message || 'ไม่สามารถดำเนินการกับข้อมูล Staff ได้'); return response.status === 204 ? undefined as T : response.json() }
export const listStaff = () => request<Staff[]>('/api/staff')
export const createStaff = (payload: StaffPayload) => request<Staff>('/api/staff', { method: 'POST', body: JSON.stringify(payload) })
export const updateStaff = (id: number, payload: StaffPayload) => request<Staff>(`/api/staff/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
export const deactivateStaff = (id: number) => request<void>(`/api/staff/${id}`, { method: 'DELETE' })
