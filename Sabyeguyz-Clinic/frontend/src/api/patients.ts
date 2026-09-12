import { patientApi, type Patient } from './client'

export interface PatientRegistrationPayload {
  branchCode: string
  username: string
  password: string
  email: string
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
