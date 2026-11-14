import { api } from './http'

export type IdCardData = {
  nationalId: string
  titleTh: string | null
  firstNameTh: string | null
  middleNameTh: string | null
  lastNameTh: string | null
  fullNameEn: string | null
  birthDate: string | null
  gender: 'M' | 'F' | null
  address: string | null
  issueDate: string | null
  expiryDate: string | null
  lifetime: boolean
}

export type IdCardWorkstation = {
  id: number
  name: string
  branchCode: string
  online: boolean
  status: 'Active' | 'Revoked'
  /** เสียบเครื่องอ่านบัตรจริงอยู่หรือไม่ (null/ไม่มี = ตัวกลางรุ่นเก่าที่ไม่รายงาน) */
  readerConnected?: boolean | null
}

export type IdCardReadPurpose = 'REGISTER'

export type IdCardLookup = {
  exists: boolean
  patient: { id: number; hn: string; fullName: string } | null
}

// DONE always has `lookup`; `card` is present only when the patient is not in the system yet.
export type IdCardReadView = {
  status: 'PENDING' | 'READING' | 'DONE' | 'FAILED'
  outcome?: string
  message?: string
  card?: IdCardData
  lookup?: IdCardLookup
}

export type IdCardWorkstationCreated = {
  id: number
  name: string
  branchCode: string
  pairingCode: string
  pairingExpiresAt: string
}

export const idCardApi = {
  workstations: (branchCode?: string) =>
    api.get<IdCardWorkstation[]>(
      `/api/id-card/workstations${branchCode ? `?branchCode=${encodeURIComponent(branchCode)}` : ''}`,
    ),
  start: (workstationId: number, purpose: IdCardReadPurpose) =>
    api.post<{ id: string; expiresAt: string }>('/api/id-card/reads', { workstationId, purpose }),
  result: (id: string) => api.get<IdCardReadView>(`/api/id-card/reads/${encodeURIComponent(id)}`),
  createWorkstation: (name: string, branchCode: string) =>
    api.post<IdCardWorkstationCreated>('/api/id-card/workstations', { name, branchCode }),
  newPairingCode: (id: number) =>
    api.post<IdCardWorkstationCreated>(`/api/id-card/workstations/${id}/pairing-code`, {}),
  revokeWorkstation: (id: number) => api.post<void>(`/api/id-card/workstations/${id}/revoke`, {}),
}
