export interface PatientRegistrationForm {
  branchCode: string
  email: string
  title: string
  customerType: string
  firstName: string
  middleName: string
  lastName: string
  nickname: string
  fullNameEnglish: string
  birthDate: string
  gender: string
  phone: string
  identityDocumentType: string
  identityNumber: string
  address: string
  bloodGroup: string
  nationality: string
  customerSource: string
  insuranceCompany: string
  salesChannel: string
  paymentType: string
  drugAllergy: boolean
  foodAllergy: boolean
  chronicDisease: string
  privacyConsent: boolean
}

export function formatPhoneNumber(value: string) {
  const digits = value.replace(/\D/g, '')
  return digits.replace(/^(\d{3})(\d{3})(\d{4})$/, '$1-$2-$3')
}

export function formatThaiDate(value: string) {
  const date = new Date(`${value}T00:00:00`)
  const months = [
    'มกราคม',
    'กุมภาพันธ์',
    'มีนาคม',
    'เมษายน',
    'พฤษภาคม',
    'มิถุนายน',
    'กรกฎาคม',
    'สิงหาคม',
    'กันยายน',
    'ตุลาคม',
    'พฤศจิกายน',
    'ธันวาคม',
  ]
  return `${date.getDate()} ${months[date.getMonth()]} ${date.getFullYear() + 543}`
}
