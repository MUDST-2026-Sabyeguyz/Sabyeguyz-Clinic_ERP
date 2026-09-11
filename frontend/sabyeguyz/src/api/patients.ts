export interface PatientRegistrationPayload {
  branchCode: string
  username: string
  email: string
  password: string
  title: string
  fullName: string
  birthDate: string
  gender: string
  phone: string
  identityDocumentType: string
  identityNumber: string
  address: string
  drugAllergy: boolean
  foodAllergy: boolean
  chronicDisease: string
  emergencyContactName: string
  emergencyContactRelationship: string
  emergencyContactPhone: string
  privacyConsent: boolean
}

export interface PatientRegistrationResult {
  id: number
  hn: string
  fullName: string
  phone: string
  active: boolean
}

export interface PatientSearchItem {
  id: number
  hn: string
  fullName: string
  phone: string
  active: boolean
}

export interface PatientSearchResult {
  items: PatientSearchItem[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface PatientProfile {
  id: number
  hn: string
  branch: {
    id: number
    code: string
    name: string
    active: boolean
  }
  title: string | null
  fullName: string
  birthDate: string | null
  gender: string | null
  phone: string | null
  identityDocumentType: string | null
  identityNumber: string | null
  address: string | null
  drugAllergy: boolean
  foodAllergy: boolean
  chronicDisease: string | null
  emergencyContactName: string | null
  emergencyContactRelationship: string | null
  emergencyContactPhone: string | null
  privacyConsent: boolean
  active: boolean
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export async function registerPatient(
  payload: PatientRegistrationPayload,
): Promise<PatientRegistrationResult> {
  const response = await authFetch(`${apiBaseUrl}/api/patients`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  const body = await response.json()
  if (!response.ok) throw new Error(body.message ?? 'ไม่สามารถลงทะเบียนผู้ป่วยได้')
  return body
}

async function readError(response: Response, fallback: string) {
  try {
    const body = await response.json()
    return body.message ?? fallback
  } catch {
    return fallback
  }
}

export async function searchPatients(
  query: string,
  page = 0,
  size = 20,
): Promise<PatientSearchResult> {
  const params = new URLSearchParams({ query, page: String(page), size: String(size) })
  const response = await authFetch(`${apiBaseUrl}/api/patients?${params.toString()}`)
  if (!response.ok) throw new Error(await readError(response, 'ไม่สามารถค้นหาผู้ป่วยได้'))
  return response.json()
}

export async function getPatientProfile(id: number): Promise<PatientProfile> {
  const response = await authFetch(`${apiBaseUrl}/api/patients/${id}`)
  if (!response.ok) throw new Error(await readError(response, 'ไม่สามารถโหลดข้อมูลผู้ป่วยได้'))
  return response.json()
}
import { authFetch } from './http'
