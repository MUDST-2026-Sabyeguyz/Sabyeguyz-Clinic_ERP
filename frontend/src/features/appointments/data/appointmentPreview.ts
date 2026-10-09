import type {
  Appointment,
  Branch,
  ClinicResource,
  Patient,
  ServiceOption,
  Therapist,
} from '@/api/client'

const today = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Bangkok',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
}).format(new Date())

export const previewBranches: Branch[] = [
  { id: 1, branchCode: '01', name: 'แบริ่ง', active: true },
  { id: 2, branchCode: '02', name: 'พระราม 9', active: true },
]

export const previewPatients: Patient[] = [
  { id: 1, hn: '2601080022', fullName: 'สมชาย ใจดี', phone: '089-123-4567', active: true },
  { id: 2, hn: '2601080012', fullName: 'วรรณา มั่นคง', phone: '081-987-6543', active: true },
  { id: 3, hn: '2601080007', fullName: 'อนันต์ สุขใจ', phone: '095-778-3421', active: true },
  { id: 4, hn: '2601080031', fullName: 'สุภาวดี พรดี', phone: '086-242-4411', active: true },
]

export const previewServices: ServiceOption[] = [
  { id: 1, code: 'MANUAL', name: 'Manual Therapy', durationMinutes: 60, price: 3500 },
  { id: 2, code: 'EXERCISE', name: 'Exercise Therapy', durationMinutes: 60, price: 2500 },
]

export const previewTherapists: Therapist[] = [
  { id: 1, code: 'PT001', fullName: 'กฤชดา', branchCode: '01' },
  { id: 2, code: 'PT002', fullName: 'พิมพ์ชนก', branchCode: '02' },
  { id: 3, code: 'PT003', fullName: 'ณัฐวดี', branchCode: '01' },
]

export const previewRooms: ClinicResource[] = [
  {
    id: 1,
    code: 'ROOM-1',
    name: 'ห้องรักษา 1',
    type: 'ROOM',
    branchCode: '01',
    branchName: 'แบริ่ง',
    status: 'พร้อมใช้งาน',
    active: true,
  },
  {
    id: 2,
    code: 'ROOM-2',
    name: 'ห้องรักษา 2',
    type: 'ROOM',
    branchCode: '02',
    branchName: 'พระราม 9',
    status: 'พร้อมใช้งาน',
    active: true,
  },
]

const appointment = (
  id: number,
  time: string,
  patientIndex: number,
  therapistIndex: number,
  serviceIndex: number,
  status: Appointment['status'],
): Appointment => ({
  id,
  branchCode: previewTherapists[therapistIndex]!.branchCode,
  patient: {
    id: previewPatients[patientIndex]!.id,
    hn: previewPatients[patientIndex]!.hn,
    fullName: previewPatients[patientIndex]!.fullName,
  },
  therapist: {
    id: previewTherapists[therapistIndex]!.id,
    fullName: previewTherapists[therapistIndex]!.fullName,
  },
  service: previewServices[serviceIndex]!,
  startAt: `${today}T${time}:00+07:00`,
  endAt: `${today}T${time}:00+07:00`,
  status,
})

export const previewAppointments: Appointment[] = [
  appointment(1, '09:00', 0, 0, 0, 'RESCHEDULED'),
  appointment(2, '09:00', 1, 2, 1, 'CONFIRMED'),
  appointment(3, '10:30', 1, 0, 1, 'CONFIRMED'),
  appointment(4, '13:00', 2, 1, 0, 'COMPLETED'),
  appointment(5, '14:30', 3, 2, 1, 'CANCELLED'),
  appointment(6, '16:00', 1, 0, 0, 'CONFIRMED'),
]
