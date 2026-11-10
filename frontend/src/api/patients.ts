import { patientApi, type Patient } from './client'

// ข้อมูลลงทะเบียนผู้ป่วย รวมประวัติสุขภาพ ผู้ติดต่อฉุกเฉิน และความยินยอม
export interface PatientRegistrationPayload {
  branchCode: string
  email: string
  title: string
  fullName: string
  firstName?: string
  middleName?: string | null
  lastName?: string
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
  customerType?: string
  nickname?: string
  fullNameEnglish?: string
  bloodGroup?: string
  nationality?: string
  customerSource?: string
  insuranceCompany?: string
  salesChannel?: string
  paymentType?: string
}

export type PatientRegistrationResult = Patient

export function registerPatient(payload: PatientRegistrationPayload) {
  return patientApi.register(payload)
}
