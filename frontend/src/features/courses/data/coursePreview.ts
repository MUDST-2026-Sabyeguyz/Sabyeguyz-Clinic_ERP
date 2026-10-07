import type { CoursePurchase, Patient, Staff } from '@/api/client'

export const previewCoursePatients: Patient[] = [
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

export const previewCourseStaff: Staff[] = [
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
]

export const previewCoursePurchases: CoursePurchase[] = [
  {
    id: 1,
    patient: previewCoursePatients[0]!,
    branchCode: 'BR',
    course: { id: 1, code: 'CRS-DEMO-001', name: 'Manual Therapy' },
    price: 8500,
    sessions: 10,
    bonusSessions: 0,
    purchased: 10,
    used: 3,
    remaining: 7,
    purchasedAt: '2026-07-01T09:00:00+07:00',
    expiresAt: '2026-12-31T23:59:59+07:00',
    expired: false,
    usable: true,
    status: 'ACTIVE',
  },
  {
    id: 2,
    patient: previewCoursePatients[0]!,
    branchCode: 'BR',
    course: { id: 2, code: 'COURSE-EXERCISE-6', name: 'Exercise Therapy 6 ครั้ง' },
    price: 4500,
    sessions: 6,
    bonusSessions: 0,
    purchased: 6,
    used: 6,
    remaining: 0,
    purchasedAt: '2026-01-10T09:00:00+07:00',
    expiresAt: '2026-05-10T23:59:59+07:00',
    expired: true,
    usable: false,
    status: 'ACTIVE',
  },
]

export const previewCourseMovements = [
  {
    id: 1,
    type: 'USAGE',
    quantity: -1,
    balanceAfter: 9,
    reason: 'เข้ารับบริการ',
    createdAt: '2026-08-27T10:00:00+07:00',
  },
  {
    id: 2,
    type: 'USAGE',
    quantity: -1,
    balanceAfter: 8,
    reason: 'เข้ารับบริการ',
    createdAt: '2026-09-03T10:00:00+07:00',
  },
  {
    id: 3,
    type: 'USAGE',
    quantity: -1,
    balanceAfter: 7,
    reason: 'เข้ารับบริการ',
    createdAt: '2026-09-10T10:00:00+07:00',
  },
  {
    id: 4,
    type: 'TRANSFER_OUT',
    quantity: -2,
    balanceAfter: 5,
    reason: 'แชร์คอร์ส',
    createdAt: '2026-09-11T10:00:00+07:00',
  },
]
