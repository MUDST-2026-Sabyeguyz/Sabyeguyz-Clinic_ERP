import { authFetch } from './http'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
export interface ServiceOption { id: number; code: string; name: string; durationMinutes: number; price: number }
export interface TherapistOption { id: number; code: string; fullName: string; branchCode: string }
async function read<T>(path: string): Promise<T> { const response = await authFetch(`${apiBaseUrl}${path}`); if (!response.ok) throw new Error('ไม่สามารถโหลดข้อมูลรายการได้'); return response.json() }
export const getServices = () => read<ServiceOption[]>('/api/catalog/services')
export const getTherapists = () => read<TherapistOption[]>('/api/catalog/therapists')
