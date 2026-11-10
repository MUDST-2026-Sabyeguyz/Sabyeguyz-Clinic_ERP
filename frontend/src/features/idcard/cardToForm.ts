import type { IdCardData } from '@/api/client'
import type { PatientRegistrationForm } from '@/features/patients/data/patientRegistration'

// The five titles the system supports (title_enum); anything else is left for the staff to choose.
const TITLES: Record<string, string> = {
  นาย: 'นาย',
  นาง: 'นาง',
  นางสาว: 'นางสาว',
  'น.ส.': 'นางสาว',
  เด็กชาย: 'เด็กชาย',
  'ด.ช.': 'เด็กชาย',
  เด็กหญิง: 'เด็กหญิง',
  'ด.ญ.': 'เด็กหญิง',
}

export function mapCardToRegistration(card: IdCardData): Partial<PatientRegistrationForm> {
  const form: Partial<PatientRegistrationForm> = {
    identityDocumentType: 'NATIONAL_ID',
    identityNumber: card.nationalId,
    nationality: 'ไทย',
  }
  const title = card.titleTh ? TITLES[card.titleTh.trim()] : undefined
  if (title) form.title = title
  if (card.firstNameTh) form.firstName = card.firstNameTh
  form.middleName = card.middleNameTh ?? ''
  if (card.lastNameTh) form.lastName = card.lastNameTh
  if (card.fullNameEn) form.fullNameEnglish = card.fullNameEn
  if (card.birthDate) form.birthDate = card.birthDate
  if (card.gender === 'M') form.gender = 'ชาย'
  if (card.gender === 'F') form.gender = 'หญิง'
  if (card.address) form.address = card.address
  return form
}

export function cardExpired(card: IdCardData, today: string): boolean {
  return !card.lifetime && !!card.expiryDate && card.expiryDate < today
}
