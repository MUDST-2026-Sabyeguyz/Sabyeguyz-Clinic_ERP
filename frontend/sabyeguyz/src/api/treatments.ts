import { authFetch } from './http'
const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
export interface TreatmentPayload { patientId: number; appointmentId?: number; staffId?: number; serviceId: number; note: string }
export interface Treatment { id: number; service: string; note: string; patientId: number; appointmentId?: number; staffId?: number }
export interface ServiceOption { id: number; code: string; name: string; durationMinutes: number; price: number }
async function request<T>(url: string, options?: RequestInit): Promise<T> { const r=await authFetch(`${base}${url}`,{...options,headers:{'Content-Type':'application/json',...(options?.headers??{})}}); const b=await r.json().catch(()=>null); if(!r.ok) throw new Error(b?.message??'ไม่สามารถบันทึกการรักษาได้'); return b as T }
export const listTreatmentServices=()=>request<ServiceOption[]>('/api/treatments/services')
export const createTreatment=(payload:TreatmentPayload)=>request<Treatment>('/api/treatments',{method:'POST',body:JSON.stringify(payload)})
export const listTreatments=(patientId:number)=>request<Treatment[]>(`/api/treatments/patients/${patientId}`)
