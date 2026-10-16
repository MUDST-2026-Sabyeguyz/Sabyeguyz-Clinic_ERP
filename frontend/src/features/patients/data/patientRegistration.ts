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

export const patientTitleOptions = [
  { value: 'นาย', label: 'นาย / Mr.' },
  { value: 'นางสาว', label: 'นางสาว / Miss' },
  { value: 'นาง', label: 'นาง / Mrs.' },
  { value: 'เด็กชาย', label: 'เด็กชาย / Boy' },
  { value: 'เด็กหญิง', label: 'เด็กหญิง / Girl' },
]
export const patientGenderOptions = [
  { value: 'Male', label: 'ชาย' },
  { value: 'Female', label: 'หญิง' },
  { value: 'NotSpecified', label: 'ไม่ระบุ' },
]

const hnSequenceKey = 'mockPatientHnSequence'

export function createMockHn() {
  const nextSequence = Number(localStorage.getItem(hnSequenceKey) ?? '0') + 1
  localStorage.setItem(hnSequenceKey, String(nextSequence))
  const now = new Date()
  const dateCode = `${String(now.getFullYear()).slice(-2)}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
  return `HN${dateCode}${String(nextSequence).padStart(4, '0')}`
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
