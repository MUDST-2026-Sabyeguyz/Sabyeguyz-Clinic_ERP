export interface PatientSummary {
  hn: string
  fullName: string
  phone: string
  birthDate: string
  gender: string
  branch: string
  status: 'เปิดใช้งาน' | 'ปิดใช้งาน'
}

export interface PatientProfile extends PatientSummary {
  title: string
  nickname: string
  fullNameEnglish: string
  email: string
  nationality: string
  bloodGroup: string
  customerType: string
  customerSource: string
  insuranceCompany: string
  identityDocumentType: string
  identityNumber: string
  address: string
}

const storedPatientsKey = 'registeredPatients'

export const patients: PatientProfile[] = [
  {
    hn: 'HN2601080022',
    title: 'นาย',
    nickname: 'ชาย',
    fullNameEnglish: 'Somchai Jaidee',
    fullName: 'สมชาย ใจดี',
    phone: '089-555-1234',
    email: 'somchai@example.com',
    nationality: 'ไทย',
    bloodGroup: 'O',
    customerType: 'ลูกค้าทั่วไป',
    customerSource: 'ลูกค้าแนะนำ',
    insuranceCompany: 'ไม่มีประกัน',
    birthDate: '10 มกราคม 2534',
    gender: 'ชาย',
    identityDocumentType: 'บัตรประจำตัวประชาชน',
    identityNumber: '1-2345-67890-12-3',
    address: '99 ถนนราชพฤกษ์ แขวงบางระมาด เขตตลิ่งชัน กรุงเทพมหานคร 10170',
    branch: 'สาขาราชพฤกษ์',
    status: 'เปิดใช้งาน',
  },
  {
    hn: 'HN2601080012',
    title: 'นางสาว',
    nickname: 'วรรณ',
    fullNameEnglish: 'Wanna Mankhong',
    fullName: 'วรรณา มั่นคง',
    phone: '086-242-4411',
    email: 'wanna@example.com',
    nationality: 'ไทย',
    bloodGroup: 'A',
    customerType: 'ลูกค้าทั่วไป',
    customerSource: 'Facebook',
    insuranceCompany: 'AIA',
    birthDate: '22 มีนาคม 2538',
    gender: 'หญิง',
    identityDocumentType: 'บัตรประจำตัวประชาชน',
    identityNumber: '2-3456-78901-23-4',
    address: '88 ถนนสุขุมวิท เขตวัฒนา กรุงเทพมหานคร 10110',
    branch: 'สาขาสุขุมวิท',
    status: 'เปิดใช้งาน',
  },
  {
    hn: 'HN2601080007',
    title: 'นางสาว',
    nickname: 'กนก',
    fullNameEnglish: 'Kanokwan Poonjai',
    fullName: 'กนกวรรณ พูนใจ',
    phone: '095-778-3421',
    email: 'kanokwan@example.com',
    nationality: 'ไทย',
    bloodGroup: 'ไม่ทราบ',
    customerType: 'ลูกค้าทั่วไป',
    customerSource: 'หน้าสาขา',
    insuranceCompany: 'ไม่มีประกัน',
    birthDate: '8 กรกฎาคม 2540',
    gender: 'หญิง',
    identityDocumentType: 'หนังสือเดินทาง',
    identityNumber: 'AA1234567',
    address: '120 ถนนสุขุมวิท เขตวัฒนา กรุงเทพมหานคร 10110',
    branch: 'สาขาราชพฤกษ์',
    status: 'เปิดใช้งาน',
  },
]

export function savePatientProfile(patient: PatientProfile) {
  const index = patients.findIndex((item) => item.hn === patient.hn)
  if (index >= 0) patients[index] = patient
  else patients.unshift(patient)

  const storedPatients = JSON.parse(localStorage.getItem(storedPatientsKey) || '[]') as PatientProfile[]
  const storedIndex = storedPatients.findIndex((item) => item.hn === patient.hn)
  if (storedIndex >= 0) storedPatients[storedIndex] = patient
  else storedPatients.unshift(patient)
  localStorage.setItem(storedPatientsKey, JSON.stringify(storedPatients))
}

export function findPatient(hn: string) {
  const storedPatients = JSON.parse(localStorage.getItem(storedPatientsKey) || '[]') as PatientProfile[]
  return storedPatients.find((item) => item.hn === hn) ?? patients.find((item) => item.hn === hn)
}
