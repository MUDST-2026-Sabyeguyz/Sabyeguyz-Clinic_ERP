import type { Course, Patient, PaymentMethod, ServiceOption, Staff } from '@/api/client'

export const previewServicePatients: Patient[] = [
  {
    id: 1,
    hn: '2601080022',
    fullName: 'สมชาย ใจดี',
    phone: '089-123-4567',
    active: true,
    branchCode: 'BR',
    branchName: 'แบริ่ง',
  },
  {
    id: 2,
    hn: '2601080012',
    fullName: 'วรรณา มั่นคง',
    phone: '081-987-6543',
    active: true,
    branchCode: 'RP9',
    branchName: 'พระราม 9',
  },
  {
    id: 3,
    hn: '2601080007',
    fullName: 'อนันต์ สุขใจ',
    phone: '095-778-3421',
    active: true,
    branchCode: 'RP9',
    branchName: 'พระราม 9',
  },
]

export const previewServiceOptions: ServiceOption[] = [
  { id: 1, code: 'ASSESSMENT', name: 'Initial Assessment', durationMinutes: 60, price: 1200 },
  { id: 2, code: 'MANUAL', name: 'Manual Therapy', durationMinutes: 60, price: 2000 },
  { id: 3, code: 'EXERCISE', name: 'Exercise Therapy', durationMinutes: 60, price: 1200 },
]

export const previewServiceCourses: Course[] = [
  {
    id: 1,
    code: 'COURSE-MANUAL-10',
    name: 'Manual Therapy 10 ครั้ง',
    price: 8500,
    sessions: 10,
    bonusSessions: 0,
    validityDays: 180,
    active: true,
  },
  {
    id: 2,
    code: 'COURSE-EXERCISE-6',
    name: 'Exercise Therapy 6 ครั้ง',
    price: 4500,
    sessions: 6,
    bonusSessions: 0,
    validityDays: 120,
    active: true,
  },
]

export const previewPaymentMethods: PaymentMethod[] = [
  { id: 1, code: 'CLINIC', name: 'ชำระที่คลินิก', active: true },
  { id: 2, code: 'BANK_TRANSFER', name: 'โอนเงินผ่านธนาคาร', active: true },
  { id: 3, code: 'PROMPTPAY', name: 'พร้อมเพย์', active: true },
  { id: 4, code: 'CARD', name: 'บัตรเครดิต/เดบิต', active: true },
]

export const previewServiceStaff: Staff[] = [
  {
    id: 1,
    userId: 1,
    username: 'kritsada',
    role: 'THERAPIST',
    employeeCode: 'PT001',
    fullName: 'กฤชดา',
    active: true,
  },
  {
    id: 2,
    userId: 2,
    username: 'pimchanok',
    role: 'THERAPIST',
    employeeCode: 'PT002',
    fullName: 'พิมพ์ชนก',
    active: true,
  },
  {
    id: 3,
    userId: 3,
    username: 'nattawadee',
    role: 'THERAPIST',
    employeeCode: 'PT003',
    fullName: 'ณัฐวดี',
    active: true,
  },
]
