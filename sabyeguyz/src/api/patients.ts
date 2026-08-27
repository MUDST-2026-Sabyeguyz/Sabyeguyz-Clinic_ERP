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

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export async function registerPatient(payload: PatientRegistrationPayload): Promise<PatientRegistrationResult> {
  const response = await fetch(`${apiBaseUrl}/api/patients`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  const body = await response.json()
  if (!response.ok) throw new Error(body.message ?? 'ไม่สามารถลงทะเบียนผู้ป่วยได้')
  return body
}
