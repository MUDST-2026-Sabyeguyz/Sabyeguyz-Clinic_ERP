const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export type ApiErrorBody = { message?: string; fields?: Record<string, string> }

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly body?: ApiErrorBody,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export function getToken() {
  return localStorage.getItem('authToken')
}

export function clearSession() {
  localStorage.removeItem('authToken')
  localStorage.removeItem('authUser')
  localStorage.removeItem('mockStaffRole')
  localStorage.removeItem('mockStaffPermissions')
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${apiBaseUrl}${path}`, { ...init, headers })
  const text = await response.text()
  let body: T | ApiErrorBody | undefined
  if (text) {
    try {
      body = JSON.parse(text) as T | ApiErrorBody
    } catch {
      body = undefined
    }
  }
  if (!response.ok) {
    if (response.status === 401) {
      clearSession()
      if (typeof window !== 'undefined' && window.location.pathname.startsWith('/staff/')) window.location.assign('/')
    }
    const errorBody = body as ApiErrorBody | undefined
    throw new ApiError(errorBody?.message ?? `Request failed (${response.status})`, response.status, errorBody)
  }
  return body as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) => request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) => request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) => request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: (path: string) => request<void>(path, { method: 'DELETE' }),
}

export type AuthResponse = { id: number; username: string; role: 'MANAGER' | 'THERAPIST'; token: string }
export type Patient = { id: number; hn: string; fullName: string; phone: string; active: boolean }
export type PatientProfile = Patient & {
  branch: { id: number; code: string; name: string; active: boolean }
  title: string; birthDate: string; gender: string; identityDocumentType: string; identityNumber: string
  address: string; drugAllergy: boolean; foodAllergy: boolean; chronicDisease: string
  emergencyContactName: string; emergencyContactRelationship: string; emergencyContactPhone: string
  privacyConsent: boolean
  nickname?: string; bloodGroup?: string; nationality?: string; customerType?: string; customerSource?: string; insuranceCompany?: string
}
export type Page<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number }
export type Branch = { id: number; code: string; numericCode: string; name: string; active: boolean }
export type Staff = { id: number; userId: number; username: string; role: 'MANAGER' | 'THERAPIST'; employeeCode: string; fullName: string; phone?: string; active: boolean }
export type ServiceOption = { id: number; code: string; name: string; durationMinutes: number; price: number }
export type Course = { id: number; code: string; name: string; price: number; sessions: number; bonusSessions: number; validityDays: number; active: boolean }
export type Therapist = { id: number; code: string; fullName: string; branchCode: string }
export type PaymentMethod = { id: number; code: string; name: string; active: boolean }
export type AppointmentStatus = 'CONFIRMED' | 'ARRIVED' | 'IN_SERVICE' | 'COMPLETED' | 'CANCELLED' | 'RESCHEDULED' | 'NO_SHOW'
export type Appointment = {
  id: number; branchCode: string; patient: { id: number; hn: string; fullName: string }
  therapist: { id: number; fullName: string }; service: { id: number; name: string; durationMinutes: number }
  startAt: string; endAt: string; status: AppointmentStatus; note?: string; cancelReason?: string
}
export type AppointmentPage = Page<Appointment>
export type CoursePurchase = { id: number; patient: Patient; branchCode: string; course: { id: number; code: string; name: string }; price: number; sessions: number; bonusSessions: number; purchased: number; used: number; remaining: number; purchasedAt: string; expiresAt: string; expired: boolean; usable: boolean; status: string }
export type SaleSummary = { id: number; receiptNo: string; patient: Patient; branchCode: string; salesperson?: { id: number; employeeCode: string; fullName: string }; total: number; paid: number; status: string; soldAt: string; voidedAt?: string }
export type DashboardResponse = { uniquePatients: number; visits: number; totalRevenue: number; courseRevenue: number; singleVisitRevenue: number; assessmentRevenue: number; receiptsIssued: number; receiptsVoided: number }
export type AppointmentReport = { byStatus: Array<{ status: string; appointments: number }>; appointments: number; visits: number; noShowRate: number }
export type RevenueReport = { totalRevenue: number; byCategory: Array<{ type: string; amount: number; share: number }> }
export type CourseBalanceReport = { purchases: number; rows: Array<{ hn: string; patientFullName: string; branchCode: string; courseName: string; purchased: number; used: number; remaining: number; expired: boolean }> }
export type StaffSalesReport = { rows: Array<{ fullName: string; receipts: number; amount: number }>; unassigned?: { fullName?: string; receipts: number; amount: number } }
export type TransferRecord = { id: number; sourcePatientId: number; destinationPatientId: number; branchCode: string; courseId: number; quantity: number; reason: string; createdAt: string }

export const authApi = {
  login: (username: string, password: string) => api.post<AuthResponse>('/api/auth/login', { username, password }),
  register: (username: string, password: string, role: 'MANAGER' | 'THERAPIST') => api.post<AuthResponse>('/api/auth/register', { username, password, role }),
}

export const patientApi = {
  search: (query = '', page = 0, size = 20) => api.get<Page<Patient>>(`/api/patients?${new URLSearchParams({ query, page: String(page), size: String(size) })}`),
  get: (id: number) => api.get<PatientProfile>(`/api/patients/${id}`),
  register: (payload: object) => api.post<Patient>('/api/patients', payload),
  update: (id: number, payload: object) => api.put<PatientProfile>(`/api/patients/${id}`, payload),
}

export const branchApi = {
  list: () => api.get<Branch[]>('/api/branches'),
  get: (id: number) => api.get<Branch>(`/api/branches/${id}`),
  create: (body: { code: string; numericCode: string; name: string }) => api.post<Branch>('/api/branches', body),
  update: (id: number, body: { code: string; numericCode: string; name: string }) => api.put<Branch>(`/api/branches/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/branches/${id}`),
}

export const catalogApi = {
  courses: () => api.get<Course[]>('/api/catalog/courses'),
  services: () => api.get<ServiceOption[]>('/api/catalog/services'),
  therapists: () => api.get<Therapist[]>('/api/catalog/therapists'),
  lookupServices: () => api.get<Array<{ id: number; code: string; name: string; defaultDurationMinutes: number; active: boolean }>>('/api/lookups/services'),
  lookupTherapists: (branchCode?: string) => api.get<Array<{ id: number; code: string; fullName: string; branchCode: string; active: boolean }>>(`/api/lookups/therapists${branchCode ? `?branchCode=${encodeURIComponent(branchCode)}` : ''}`),
}

export const appointmentApi = {
  search: (params: { branchCode?: string; date?: string; therapistId?: number; status?: AppointmentStatus; page?: number; size?: number } = {}) => {
    const query = new URLSearchParams()
    Object.entries({ page: 0, size: 100, ...params }).forEach(([key, value]) => { if (value !== undefined && value !== '') query.set(key, String(value)) })
    return api.get<AppointmentPage>(`/api/appointments?${query}`)
  },
  get: (id: number) => api.get<Appointment>(`/api/appointments/${id}`),
  create: (body: { branchCode: string; patientId: number; therapistId: number; serviceId: number; startAt: string; note?: string }) => api.post<Appointment>('/api/appointments', body),
  checkIn: (id: number) => api.post<Appointment>(`/api/appointments/${id}/check-in`, {}),
  startService: (id: number) => api.post<Appointment>(`/api/appointments/${id}/start-service`, {}),
  noShow: (id: number) => api.post<Appointment>(`/api/appointments/${id}/no-show`, {}),
  complete: (id: number) => api.post<Appointment>(`/api/appointments/${id}/complete`, {}),
  cancel: (id: number, reason: string) => api.post<Appointment>(`/api/appointments/${id}/cancel`, { reason }),
  reschedule: (id: number, body: { startAt: string; therapistId?: number }) => api.patch<Appointment>(`/api/appointments/${id}`, body),
}

export const treatmentApi = {
  services: () => api.get<ServiceOption[]>('/api/treatments/services'),
  listByPatient: (patientId: number) => api.get<unknown[]>(`/api/treatments/patients/${patientId}`),
  create: (body: { patientId: number; appointmentId?: number; staffId?: number; serviceId: number; note: string }) => api.post('/api/treatments', body),
}

export const courseApi = {
  list: () => api.get<Array<Course & { active: boolean }>>('/api/courses'),
  get: (id: number) => api.get<Course>(`/api/courses/${id}`),
  create: (body: Omit<Course, 'id' | 'active'>) => api.post<Course>('/api/courses', body),
  update: (id: number, body: Omit<Course, 'id' | 'active'>) => api.put<Course>(`/api/courses/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/courses/${id}`),
  patientCourses: (patientId: number) => api.get<CoursePurchase[]>(`/api/patients/${patientId}/courses`),
  purchase: (body: { patientId: number; courseId: number; branchCode: string }) => api.post<CoursePurchase>('/api/course-purchases', body),
  ledger: (id: number) => api.get<{ purchase: CoursePurchase & { commissionStatus?: string; lockedCommissionRate?: number; totalCourseCommissionPool?: number; commissionAllocationPerVisit?: number }; movements: unknown[]; allocations: Array<{visitDate:string; gross:number; fee:number; ownerNet:number}> }>(`/api/course-purchases/${id}`),
  use: (body: { coursePurchaseId: number; appointmentId?: number; note?: string }) => api.post<CoursePurchase>('/api/course-usages', body),
  reverse: (movementId: number, reason: string) => api.post<CoursePurchase>(`/api/course-movements/${movementId}/reverse`, { reason }),
  cancel: (id: number, reason: string) => api.post<CoursePurchase>(`/api/course-purchases/${id}/cancel`, { reason }),
  transfer: (body: { sourcePurchaseId: number; destinationPatientId: number; quantity: number; reason: string }) => api.post('/api/course-transfers', body),
  transferHistory: (patientId?: number) => api.get<TransferRecord[]>(`/api/course-transfers${patientId ? `?patientId=${patientId}` : ''}`),
}

export const salesApi = {
  list: (params: { patientId?: number; branchCode?: string; page?: number; size?: number } = {}) => {
    const query = new URLSearchParams()
    Object.entries({ page: 0, size: 100, ...params }).forEach(([key, value]) => { if (value !== undefined && value !== '') query.set(key, String(value)) })
    return api.get<Page<SaleSummary>>(`/api/payments?${query}`)
  },
  get: (id: number) => api.get('/api/receipts/' + id),
  create: (body: unknown) => api.post('/api/payments', body),
  reverse: (id: number, reason: string) => api.post(`/api/payments/${id}/reverse`, { reason }),
}

export const paymentMethodApi = {
  list: () => api.get<PaymentMethod[]>('/api/payment-methods'),
  create: (body: { code: string; name: string }) => api.post<PaymentMethod>('/api/payment-methods', body),
  update: (id: number, body: { code: string; name: string }) => api.put<PaymentMethod>(`/api/payment-methods/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/payment-methods/${id}`),
  activate: (id: number) => api.patch<PaymentMethod>(`/api/payment-methods/${id}/activate`, {}),
}

export const staffApi = {
  list: () => api.get<Staff[]>('/api/staff'),
  get: (id: number) => api.get<Staff>(`/api/staff/${id}`),
  create: (body: { userId: number; employeeCode: string; fullName: string; phone?: string }) => api.post<Staff>('/api/staff', body),
  update: (id: number, body: { userId: number; employeeCode: string; fullName: string; phone?: string }) => api.put<Staff>(`/api/staff/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/staff/${id}`),
}

export const reportApi = {
  dashboard: (from: string, to: string, branchCode?: string) => api.get<DashboardResponse>(`/api/dashboard/summary?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`),
  appointments: (from: string, to: string, branchCode?: string) => api.get<AppointmentReport>(`/api/reports/appointments?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`),
  revenue: (from: string, to: string, branchCode?: string) => api.get<RevenueReport>(`/api/reports/revenue?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`),
  balances: (from: string, to: string, branchCode?: string, patientId?: number) => api.get<CourseBalanceReport>(`/api/reports/course-balances?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}), ...(patientId ? { patientId: String(patientId) } : {}) })}`),
  staffSales: (from: string, to: string, branchCode?: string) => api.get<StaffSalesReport>(`/api/reports/staff-sales?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`),
}

export const commissionApi = {
  schemes: () => api.get('/api/commission/schemes'),
  tiers: (schemeId: number) => api.get(`/api/commission/schemes/${schemeId}/tiers`),
  createScheme: (body: unknown) => api.post('/api/commission/schemes', body),
  updateScheme: (id: number, body: unknown) => api.put(`/api/commission/schemes/${id}`, body),
  activateScheme: (id: number) => api.patch(`/api/commission/schemes/${id}/activate`, {}),
  deactivateScheme: (id: number) => api.delete(`/api/commission/schemes/${id}`),
  report: (from: string, to: string) => api.get<Array<{ staffId: number | null; staffName: string; branchCode: string; branchName: string; saleCount: number; base: number; rate: number; commission: number }>>(`/api/commission/report?${new URLSearchParams({ from, to })}`),
  closings: (month: string) => api.get<Array<{id:number;month:string;employeeId:number;employeeName:string;sales:number;version:string;rate:number;status:string}>>(`/api/commission/closings?month=${month}-01`),
  closeMonth: (month: string) => api.post(`/api/commission/closings/${month}-01`, {}),
  treatmentFees: () => api.get('/api/commission/treatment-fees'),
}

export type ClinicResource = { id:number; branchCode:string; branchName:string; code:string; name:string; type:string; status:string; active:boolean }
export const resourceApi = {
  list: () => api.get<ClinicResource[]>('/api/resources'), create: (body: object) => api.post<ClinicResource>('/api/resources', body),
  update: (id:number, body:object) => api.put<ClinicResource>(`/api/resources/${id}`, body), deactivate: (id:number) => api.delete(`/api/resources/${id}`), activate: (id:number) => api.patch<ClinicResource>(`/api/resources/${id}/activate`, {}),
}
export type MasterData = { id:number; category:string; code:string; name:string; active:boolean }
export const masterDataApi = {
  list: () => api.get<MasterData[]>('/api/master-data'), create: (body: object) => api.post<MasterData>('/api/master-data', body),
  update: (id:number, body:object) => api.put<MasterData>(`/api/master-data/${id}`, body), deactivate: (id:number) => api.delete(`/api/master-data/${id}`), activate: (id:number) => api.patch<MasterData>(`/api/master-data/${id}/activate`, {}),
}

export const auditApi = { list: (page = 0, size = 50) => api.get(`/api/audit-logs?page=${page}&size=${size}`) }
