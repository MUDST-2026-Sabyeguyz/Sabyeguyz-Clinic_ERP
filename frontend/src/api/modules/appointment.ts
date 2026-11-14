import { api } from './http'
import { type Page } from './common'
import { type Course, type ServiceOption } from './catalog'

// สถานะที่ฐานข้อมูลอนุญาตให้บันทึกได้ตาม migration ปัจจุบัน
export type AppointmentStatus =
  | 'UNCONFIRMED'
  | 'CONFIRMED'
  | 'CANCELLED'

// endAt เก็บเวลาจบนัดจริง ไม่คำนวณซ้ำจากระยะเวลาบริการ
export type Appointment = {
  id: number
  requestedServiceType?: 'SingleVisit' | 'Course' | 'Assessment' | null
  branchCode: string
  patient: { id: number | null; hn: string | null; fullName: string | null } | null
  therapist: { id: number; fullName: string }
  service: { id: number | null; name: string | null; durationMinutes: number }
  startAt: string
  endAt: string
  status: AppointmentStatus
  note?: string
  cancelReason?: string
  patientType?: 'CURRENT' | 'NEW'
  contactName?: string | null
  contactPhone?: string | null
  contactNickname?: string | null
}

// หลักฐานจากนัดใช้ตรวจรายการเดิมและกู้ผลหลังคำขอไม่ตอบกลับ
export type AppointmentProgress = {
  appointmentId: number
  appointmentStatus: AppointmentStatus
  patientType: 'CURRENT' | 'NEW'
  state: 'FOUND' | 'NO_LINKED_RECORD' | 'REVIEW_REQUIRED'
  reason: string | null
  visitId: number | null
  servedPatientId: number | null
  completedAt: string | null
  sales: {
    saleId: number
    receiptId: number
    receiptNo: string
    receiptStatus: string
    active: boolean
  }[]
  usages: {
    usageId: number
    movementId: number
    coursePurchaseId: number
    status: string
  }[]
}

export type AppointmentPage = Page<Appointment>

export type AppointmentAvailability = {
  date: string
  openTime: string
  closeTime: string
  windowConfigured: boolean
  durationMinutes: number | null
  stepMinutes: number
  slots: Array<{ time: string; available: boolean; reason: string | null }>
  googleCalendar: 'NOT_CONNECTED' | 'CHECKED' | 'UNAVAILABLE'
}

export const appointmentApi = {
  progress: (id: number) => api.get<unknown>(`/api/appointments/${id}/progress`),
  search: (
    params: {
      branchCode?: string
      date?: string
      startDate?: string
      endDate?: string
      therapistId?: number
      status?: AppointmentStatus
      sortOrder?: 'newest' | 'oldest'
      page?: number
      size?: number
    } = {},
  ) => {
    const query = new URLSearchParams()
    Object.entries({ page: 0, size: 20, ...params }).forEach(([key, value]) => {
      if (value !== undefined && value !== '') query.set(key, String(value))
    })
    return api.get<AppointmentPage>(`/api/appointments?${query}`)
  },
  get: (id: number) => api.get<Appointment>(`/api/appointments/${id}`),
  linkPatient: (id: number, patientId: number) =>
    api.post<Appointment>(`/api/appointments/${id}/link-patient`, { patientId }),
  availability: (params: {
    branchCode: string
    therapistId: number
    date: string
    serviceId?: number
    excludeAppointmentId?: number
    patientId?: number
  }) => {
    const query = new URLSearchParams({
      branchCode: params.branchCode,
      therapistId: String(params.therapistId),
      date: params.date,
      ...(params.serviceId ? { serviceId: String(params.serviceId) } : {}),
      ...(params.patientId ? { patientId: String(params.patientId) } : {}),
      ...(params.excludeAppointmentId
        ? { excludeAppointmentId: String(params.excludeAppointmentId) }
        : {}),
    })
    return api.get<AppointmentAvailability>(`/api/appointments/availability?${query}`)
  },
  create: (body: {
    branchCode: string
    patientId?: number
    therapistId: number
    serviceId?: number
    startAt: string
    endAt: string
    note?: string
    patientType?: 'CURRENT' | 'NEW'
    contactName?: string
    contactPhone?: string
    contactNickname?: string
    requestedServiceType?: 'SingleVisit' | 'Course'
  }) => api.post<Appointment>('/api/appointments', { ...body, status: 'UNCONFIRMED' }),
  confirm: (id: number) => api.post<Appointment>(`/api/appointments/${id}/confirm`, {}),
  checkIn: (id: number) => api.post<Appointment>(`/api/appointments/${id}/check-in`, {}),
  startService: (id: number) => api.post<Appointment>(`/api/appointments/${id}/start-service`, {}),
  noShow: (id: number) => api.post<Appointment>(`/api/appointments/${id}/no-show`, {}),
  complete: (id: number) => api.post<Appointment>(`/api/appointments/${id}/complete`, {}),
  cancel: (id: number, reason: string) =>
    api.post<Appointment>(`/api/appointments/${id}/cancel`, { reason }),
  reschedule: (
    id: number,
    body: {
      startAt: string
      endAt: string
      therapistId?: number
      serviceId?: number
      branchCode?: string
      note?: string | null
    },
  ) => api.patch<Appointment>(`/api/appointments/${id}`, body),
}

export const treatmentApi = {
  services: () => api.get<ServiceOption[]>('/api/treatments/services'),
  listByPatient: (patientId: number) => api.get<unknown[]>(`/api/treatments/patients/${patientId}`),
  create: (body: {
    patientId: number
    appointmentId?: number
    staffId?: number
    serviceId: number
    note: string
  }) => api.post('/api/treatments', body),
}
