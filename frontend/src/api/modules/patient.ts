import { api } from './http'
import { type Page } from './common'

export type Patient = {
  id: number
  hn: string
  fullName: string
  phone: string
  active: boolean
  branchId?: number
  branchCode?: string
  branchName?: string
}

export type PatientProfile = Patient & {
  branch: { id: number; code: string; name: string; active: boolean }
  title: string
  birthDate: string
  gender: string
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
  nickname?: string
  email?: string
  bloodGroup?: string
  nationality?: string
  customerType?: string
  customerSource?: string
  insuranceType?: string
  insuranceCompany?: string
}

export type PatientUpdatePayload = {
  title: string
  fullName: string
  birthDate: string
  gender: string
  phone: string
  identityDocumentType: 'NATIONAL_ID' | 'PASSPORT'
  identityNumber: string
  address: string
  drugAllergy: boolean
  foodAllergy: boolean
  chronicDisease: string
  emergencyContactName: string
  emergencyContactRelationship: string
  emergencyContactPhone: string
  privacyConsent: boolean
  active: boolean
  nickname: string
  email: string
  bloodGroup: string
  nationality: string
  customerType: string
  customerSource?: string
  insuranceType?: string
  insuranceCompany: string
}

export const patientApi = {
  search: (query = '', page = 0, size = 20, branchCode = '', sort = 'nameAsc') =>
    api.get<Page<Patient>>(
      `/api/patients?${new URLSearchParams({
        query: /^hn[\s:#-]*/i.test(query.trim())
          ? query
              .trim()
              .replace(/^hn[\s:#-]*/i, '')
              .replace(/[\s-]/g, '')
          : /^\d[\d\s-]*$/.test(query.trim())
            ? query.trim().replace(/\D/g, '')
            : query,
        page: String(page),
        size: String(size),
        sort,
        ...(branchCode ? { branchCode } : {}),
      })}`,
    ),
  searchAll: async (query = '', branchCode = '') => {
    const first = await patientApi.search(query, 0, 100, branchCode)
    const rest = await Promise.all(
      Array.from({ length: Math.max(0, first.totalPages - 1) }, (_, index) =>
        patientApi.search(query, index + 1, 100, branchCode),
      ),
    )
    return [first, ...rest].flatMap((page) => page.items)
  },
  get: (id: number) => api.get<PatientProfile>(`/api/patients/${id}`),
  register: (payload: object) => api.post<Patient>('/api/patients', payload),
  update: (id: number, payload: PatientUpdatePayload) =>
    api.put<PatientProfile>(`/api/patients/${id}`, payload),
}
