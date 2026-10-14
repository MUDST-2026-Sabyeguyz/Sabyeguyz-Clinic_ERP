const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

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
      if (typeof window !== 'undefined' && window.location.pathname.startsWith('/staff/')) {
        window.location.assign('/')
        // The page is being replaced by the login page: do not let the screens that asked report an error.
        return new Promise<T>(() => {})
      }
    }
    const errorBody = body as ApiErrorBody | undefined
    throw new ApiError(
      errorBody?.message ?? `Request failed (${response.status})`,
      response.status,
      errorBody,
    )
  }
  return body as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: (path: string) => request<void>(path, { method: 'DELETE' }),
}

export type AuthResponse = {
  id: number
  username: string
  email?: string
  role: 'MANAGER' | 'THERAPIST'
  branchCode?: string
  branchName?: string
  token: string
}
export type Patient = {
  id: number
  hn: string
  fullName: string
  phone: string
  active: boolean
  branchId?: number
  branchCode?: string
  branchName?: string
}
export type PatientProfile = Patient & {
  firstName?: string
  lastName?: string
  middleName?: string | null
  branch: { id: number; code: string; name: string; active: boolean }
  title: string
  birthDate: string
  gender: string
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
  nickname?: string
  email?: string
  bloodGroup?: string
  nationality?: string
  customerType?: string
  customerSource?: string
  insuranceCompany?: string
}
export type PatientUpdatePayload = {
  firstName?: string
  lastName?: string
  middleName?: string | null
  title: string
  fullName: string
  birthDate: string
  gender: string
  phone: string
  identityDocumentType: 'NATIONAL_ID' | 'PASSPORT'
  identityNumber: string
  address: string
  drugAllergy: boolean
  foodAllergy: boolean
  chronicDisease: string
  emergencyContactName: string
  emergencyContactRelationship: string
  emergencyContactPhone: string
  privacyConsent: boolean
  active: boolean
  nickname: string
  email: string
  bloodGroup: string
  nationality: string
  customerType: string
  customerSource: string
  insuranceCompany: string
}
export type Page<T> = {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}
export type Branch = {
  id: number
  branchCode: string
  name: string
  address: string
  phone: string
  active: boolean
}
export type BranchRequest = Pick<Branch, 'branchCode' | 'name' | 'address' | 'phone'>
export type Staff = {
  id: number
  userId: number
  username: string
  role: 'MANAGER' | 'THERAPIST'
  employeeCode: string
  fullName: string
  phone?: string
  active: boolean
}
export type ServiceOption = {
  id: number
  code: string
  name: string
  durationMinutes: number
  price: number
}
export type Course = {
  id: number
  code: string
  name: string
  price: number
  sessions: number
  bonusSessions: number
  validityDays: number
  active: boolean
}
export type Therapist = { id: number; code: string; fullName: string; branchCode: string }
export type PaymentMethod = { id: number; code: string; name: string; active: boolean }
export type AppointmentStatus =
  | 'UNCONFIRMED'
  | 'CONFIRMED'
  | 'ARRIVED'
  | 'IN_SERVICE'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'RESCHEDULED'
  | 'NO_SHOW'
export type Appointment = {
  id: number
  branchCode: string
  patient: { id: number | null; hn: string | null; fullName: string; phone?: string | null }
  therapist: { id: number; fullName: string }
  service: { id: number; name: string; durationMinutes: number }
  startAt: string
  endAt: string
  status: AppointmentStatus
  note?: string
  cancelReason?: string
  patientType?: 'NEW' | 'CURRENT'
  contactName?: string | null
  contactPhone?: string | null
}
export type AppointmentPage = Page<Appointment>
export type CoursePurchase = {
  id: number
  patient: Patient
  branchCode: string
  course: { id: number; code: string; name: string }
  price: number
  sessions: number
  bonusSessions: number
  purchased: number
  used: number
  remaining: number
  purchasedAt: string
  expiresAt: string
  expired: boolean
  usable: boolean
  status: string
}
export type SaleSummary = {
  id: number
  receiptNo: string
  patient: Patient
  branchCode: string
  salesperson?: { id: number; employeeCode: string; fullName: string }
  total: number
  paid: number
  status: string
  soldAt: string
  voidedAt?: string
  voidReason?: string
  itemDescription?: string
}
export type SaleReceipt = SaleSummary & { note?: string }
export type SaleDetail = SaleReceipt & {
  items: Array<{
    id: number
    type: string
    description: string
    quantity: number
    lineTotal: number
  }>
  payments: Array<{ id: number; type: string; paymentMethodCode: string; amount: number }>
}
export type CourseUsageTransaction = {
  id: number
  coursePurchaseId: number
  patientName: string
  patientHn: string
  courseName: string
  branchCode: string
  usedAt: string
  voidedAt?: string | null
  voidReason?: string | null
}
export type DashboardResponse = {
  uniquePatients: number
  patientsByGender: Array<{ gender: string; patients: number }>
  visits: number
  totalRevenue: number
  courseRevenue: number
  singleVisitRevenue: number
  assessmentRevenue: number
  receiptsIssued: number
  receiptsVoided: number
}
export type AppointmentReport = {
  byStatus: Array<{ status: string; appointments: number }>
  appointments: number
  visits: number
  noShowRate: number
}
export type RevenueReport = {
  totalRevenue: number
  byCategory: Array<{ type: string; amount: number; share: number }>
  dailyByCategory: Array<{
    date: string
    type: 'COURSE' | 'SINGLE_VISIT' | 'ASSESSMENT'
    amount: number
  }>
}
export type CourseBalanceReport = {
  purchases: number
  rows: Array<{
    hn: string
    patientFullName: string
    branchCode: string
    courseName: string
    purchased: number
    used: number
    remaining: number
    purchasedAt: string
    expiresAt: string
    expired: boolean
  }>
}
export type StaffSalesReport = {
  rows: Array<{ fullName: string; receipts: number; amount: number }>
  unassigned?: { fullName?: string; receipts: number; amount: number }
}
export type TransferRecord = {
  id: number
  sourcePatientId: number
  sourcePatientHn: string
  sourcePatientName: string
  destinationPatientId: number
  destinationPatientHn: string
  destinationPatientName: string
  branchCode: string
  courseId: number
  quantity: number
  reason: string
  createdAt: string
}

export const authApi = {
  login: (identifier: string, password: string) =>
    api.post<AuthResponse>('/api/auth/login', { username: identifier, password }),
  register: (username: string, password: string, role: 'MANAGER' | 'THERAPIST', email?: string) =>
    api.post<AuthResponse>('/api/auth/register', { username, email, password, role }),
}

export const patientApi = {
  identityLookup: (nationalId: string) =>
    api.get<{ exists: boolean; patient: Pick<Patient, 'id' | 'hn' | 'fullName'> | null }>(
      `/api/patients/identity-lookup?${new URLSearchParams({ nationalId })}`,
    ),
  search: (query = '', page = 0, size = 20, branchCode = '', sort = 'nameAsc') =>
    api.get<Page<Patient>>(
      `/api/patients?${new URLSearchParams({ query, page: String(page), size: String(size), sort, ...(branchCode ? { branchCode } : {}) })}`,
    ),
  searchAll: async (query = '') => {
    const first = await patientApi.search(query, 0, 100)
    const rest = await Promise.all(
      Array.from({ length: Math.max(0, first.totalPages - 1) }, (_, index) =>
        patientApi.search(query, index + 1, 100),
      ),
    )
    return [first, ...rest].flatMap((page) => page.items)
  },
  get: (id: number) => api.get<PatientProfile>(`/api/patients/${id}`),
  register: (payload: object) => api.post<Patient>('/api/patients', payload),
  update: (id: number, payload: PatientUpdatePayload) =>
    api.put<PatientProfile>(`/api/patients/${id}`, payload),
}

export const branchApi = {
  list: () => api.get<Branch[]>('/api/branches'),
  get: (id: number) => api.get<Branch>(`/api/branches/${id}`),
  create: (body: BranchRequest) => api.post<Branch>('/api/branches', body),
  update: (id: number, body: BranchRequest) => api.put<Branch>(`/api/branches/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/branches/${id}`),
}

export const catalogApi = {
  courses: () => api.get<Course[]>('/api/catalog/courses'),
  services: () => api.get<ServiceOption[]>('/api/catalog/services'),
  therapists: () => api.get<Therapist[]>('/api/catalog/therapists'),
  lookupServices: () =>
    api.get<
      Array<{
        id: number
        code: string
        name: string
        defaultDurationMinutes: number
        active: boolean
      }>
    >('/api/lookups/services'),
  lookupTherapists: (branchCode?: string) =>
    api.get<
      Array<{ id: number; code: string; fullName: string; branchCode: string; active: boolean }>
    >(
      `/api/lookups/therapists${branchCode ? `?branchCode=${encodeURIComponent(branchCode)}` : ''}`,
    ),
}

export type ManagedService = {
  id: number
  code: string
  name: string
  defaultDurationMinutes: number
  price: number
  active: boolean
}
export const serviceManagementApi = {
  list: () => api.get<ManagedService[]>('/api/services'),
  create: (body: Omit<ManagedService, 'id' | 'active'>) =>
    api.post<ManagedService>('/api/services', body),
  update: (id: number, body: Omit<ManagedService, 'id' | 'active'>) =>
    api.put<ManagedService>(`/api/services/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/services/${id}`),
}

// The v2 backend names statuses in PascalCase (Confirmed, InService, NoShow); the screens use UPPER_SNAKE.
// Either spelling is accepted from the server, so the old backend keeps working too.
const statusFromServer = (status: string) =>
  status.replace(/([a-z])([A-Z])/g, '$1_$2').toUpperCase() as AppointmentStatus
const statusToServer: Record<AppointmentStatus, string> = {
  UNCONFIRMED: 'Unconfirmed',
  CONFIRMED: 'Confirmed',
  ARRIVED: 'Arrived',
  IN_SERVICE: 'InService',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  RESCHEDULED: 'Rescheduled',
  NO_SHOW: 'NoShow',
}
const normalizeAppointment = (item: Appointment): Appointment => ({
  ...item,
  status: statusFromServer(item.status),
})

export const appointmentApi = {
  search: (
    params: {
      branchCode?: string
      query?: string
      date?: string
      therapistId?: number
      status?: AppointmentStatus
      page?: number
      size?: number
    } = {},
  ) => {
    const query = new URLSearchParams()
    Object.entries({ page: 0, size: 20, ...params }).forEach(([key, value]) => {
      if (value !== undefined && value !== '')
        query.set(
          key,
          key === 'status' ? statusToServer[value as AppointmentStatus] : String(value),
        )
    })
    return api
      .get<AppointmentPage>(`/api/appointments?${query}`)
      .then((page) => ({ ...page, items: page.items.map(normalizeAppointment) }))
  },
  get: (id: number) => api.get<Appointment>(`/api/appointments/${id}`).then(normalizeAppointment),
  create: (body: {
    branchCode: string
    patientId?: number
    therapistId: number
    serviceId?: number
    startAt: string
    note?: string
    patientType?: 'NEW' | 'CURRENT'
    contactName?: string
    contactPhone?: string
    requestedServiceType?: 'SingleVisit' | 'Course'
    status?: string
  }) => api.post<Appointment>('/api/appointments', body).then(normalizeAppointment),
  confirm: (id: number) =>
    api.post<Appointment>(`/api/appointments/${id}/confirm`, {}).then(normalizeAppointment),
  linkPatient: (id: number, patientId: number) =>
    api.post<Appointment>(`/api/appointments/${id}/link-patient`, { patientId }).then(normalizeAppointment),
  checkIn: (id: number) =>
    api.post<Appointment>(`/api/appointments/${id}/check-in`, {}).then(normalizeAppointment),
  startService: (id: number) =>
    api.post<Appointment>(`/api/appointments/${id}/start-service`, {}).then(normalizeAppointment),
  noShow: (id: number) =>
    api.post<Appointment>(`/api/appointments/${id}/no-show`, {}).then(normalizeAppointment),
  complete: (id: number) =>
    api.post<Appointment>(`/api/appointments/${id}/complete`, {}).then(normalizeAppointment),
  cancel: (id: number, reason: string) =>
    api.post<Appointment>(`/api/appointments/${id}/cancel`, { reason }).then(normalizeAppointment),
  reschedule: (id: number, body: { startAt: string; therapistId?: number; note?: string | null }) =>
    api.patch<Appointment>(`/api/appointments/${id}`, body).then(normalizeAppointment),
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

type RawMovement = {
  id: number
  type: string
  quantity: number
  reason?: string
  balanceAfter?: number
  createdAt: string
}
type LedgerResponse = {
  purchase: CoursePurchase & {
    commissionStatus?: string
    lockedCommissionRate?: number
    totalCourseCommissionPool?: number
    commissionAllocationPerVisit?: number
  }
  movements: unknown[]
  allocations: Array<{ visitDate: string; gross: number; fee: number; ownerNet: number }>
}

export const courseApi = {
  usageTransactions: () => api.get<CourseUsageTransaction[]>('/api/course-usages'),
  list: () => api.get<Array<Course & { active: boolean }>>('/api/courses'),
  get: (id: number) => api.get<Course>(`/api/courses/${id}`),
  create: (body: Omit<Course, 'id' | 'active'>) => api.post<Course>('/api/courses', body),
  update: (id: number, body: Omit<Course, 'id' | 'active'>) =>
    api.put<Course>(`/api/courses/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/courses/${id}`),
  patientCourses: (patientId: number) =>
    api.get<CoursePurchase[]>(`/api/patients/${patientId}/courses`),
  purchase: (body: { patientId: number; courseId: number; branchCode: string }) =>
    api.post<CoursePurchase>('/api/course-purchases', body),
  // v2 names ledger entries Purchase/Treatment/TransferOut...; the screens use USAGE/TRANSFER_OUT... and show
  // the balance after each entry, which v2 does not send, so it is rebuilt from the current balance (newest first).
  ledger: (id: number) =>
    api.get<LedgerResponse>(`/api/course-purchases/${id}`).then((ledger) => {
      let balance = ledger.purchase.remaining
      const movements = (ledger.movements as RawMovement[]).map((movement) => {
        const type =
          movement.type === 'Treatment'
            ? 'USAGE'
            : movement.type.replace(/([a-z])([A-Z])/g, '$1_$2').toUpperCase()
        const balanceAfter = movement.balanceAfter ?? balance
        balance -= movement.quantity
        return { ...movement, type, balanceAfter }
      })
      return { ...ledger, movements }
    }),
  use: (body: { coursePurchaseId: number; appointmentId?: number; note?: string }) =>
    api.post<CoursePurchase>('/api/course-usages', body),
  reverse: (movementId: number, reason: string) =>
    api.post<CoursePurchase>(`/api/course-movements/${movementId}/reverse`, { reason }),
  cancel: (id: number, reason: string) =>
    api.post<CoursePurchase>(`/api/course-purchases/${id}/cancel`, { reason }),
  transfer: (body: {
    sourcePurchaseId: number
    destinationPatientId: number
    quantity: number
    reason: string
  }) => api.post<TransferRecord>('/api/course-transfers', body),
  transferHistory: (patientId?: number) =>
    api.get<TransferRecord[]>(`/api/course-transfers${patientId ? `?patientId=${patientId}` : ''}`),
}

export const salesApi = {
  list: (
    params: { patientId?: number; branchCode?: string; page?: number; size?: number } = {},
  ) => {
    const query = new URLSearchParams()
    Object.entries({ page: 0, size: 20, ...params }).forEach(([key, value]) => {
      if (value !== undefined && value !== '') query.set(key, String(value))
    })
    return api.get<Page<SaleSummary>>(`/api/payments?${query}`)
  },
  get: (id: number) => api.get<SaleDetail>('/api/receipts/' + id),
  create: (body: unknown) => api.post<SaleReceipt>('/api/payments', body),
  reverse: (id: number, reason: string) =>
    api.post<SaleDetail>(`/api/payments/${id}/reverse`, { reason }),
}

export const paymentMethodApi = {
  list: () => api.get<PaymentMethod[]>('/api/payment-methods'),
  create: (body: { code: string; name: string }) =>
    api.post<PaymentMethod>('/api/payment-methods', body),
  update: (id: number, body: { code: string; name: string }) =>
    api.put<PaymentMethod>(`/api/payment-methods/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/payment-methods/${id}`),
  activate: (id: number) => api.patch<PaymentMethod>(`/api/payment-methods/${id}/activate`, {}),
}

type StaffResponse = Omit<Staff, 'role'> & { role?: string; position?: string }
const normalizeStaff = (staff: StaffResponse): Staff => ({
  ...staff,
  role: (staff.role ?? staff.position ?? '').toUpperCase() as Staff['role'],
})

export const staffApi = {
  list: () => api.get<StaffResponse[]>('/api/staff').then((rows) => rows.map(normalizeStaff)),
  get: (id: number) => api.get<StaffResponse>(`/api/staff/${id}`).then(normalizeStaff),
  create: (body: { userId: number; employeeCode: string; fullName: string; phone?: string }) =>
    api.post<Staff>('/api/staff', body),
  update: (
    id: number,
    body: { userId: number; employeeCode: string; fullName: string; phone?: string },
  ) => api.put<Staff>(`/api/staff/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/staff/${id}`),
}

export type ManagedUser = {
  id: number
  username: string
  email?: string
  role: 'MANAGER' | 'THERAPIST'
  employeeCode: string
  fullName: string
  phone?: string
  branchCode?: string
  branchName?: string
  active: boolean
}
export const userManagementApi = {
  list: () => api.get<ManagedUser[]>('/api/users'),
  create: (body: {
    username: string
    email?: string
    password: string
    role: 'MANAGER' | 'THERAPIST'
    employeeCode: string
    fullName: string
    phone?: string
    branchCode?: string
  }) => api.post<ManagedUser>('/api/users', body),
  update: (
    id: number,
    body: {
      username: string
      email?: string
      password?: string
      role: 'MANAGER' | 'THERAPIST'
      employeeCode: string
      fullName: string
      phone?: string
      branchCode?: string
    },
  ) => api.put<ManagedUser>(`/api/users/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/users/${id}`),
  activate: (id: number) => api.patch<ManagedUser>(`/api/users/${id}/activate`, {}),
}

// v2 names transaction types and statuses in PascalCase (SingleVisit, CoursePurchase, NoShow); the screens use
// SINGLE_VISIT / COURSE / NO_SHOW. The old backend's spellings pass through unchanged.
type RevenueDailyType = 'COURSE' | 'SINGLE_VISIT' | 'ASSESSMENT'
const revenueTypeFromServer = (type: string) =>
  type === 'CoursePurchase'
    ? 'COURSE'
    : /[a-z]/.test(type) && /^[A-Za-z]+$/.test(type)
      ? type.replace(/([a-z])([A-Z])/g, '$1_$2').toUpperCase()
      : type

export const reportApi = {
  dashboard: (from: string, to: string, branchCode?: string) =>
    api.get<DashboardResponse>(
      `/api/dashboard/summary?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`,
    ),
  appointments: (from: string, to: string, branchCode?: string) =>
    api
      .get<AppointmentReport>(
        `/api/reports/appointments?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`,
      )
      .then((report) => ({
        ...report,
        byStatus: report.byStatus.map((item) => ({
          ...item,
          status: statusFromServer(item.status),
        })),
      })),
  revenue: (from: string, to: string, branchCode?: string) =>
    api
      .get<RevenueReport>(
        `/api/reports/revenue?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`,
      )
      .then((report) => ({
        ...report,
        byCategory: report.byCategory.map((item) => ({
          ...item,
          type: revenueTypeFromServer(item.type),
        })),
        dailyByCategory: report.dailyByCategory?.map((item) => ({
          ...item,
          type: revenueTypeFromServer(item.type) as RevenueDailyType,
        })),
      })),
  balances: (from: string, to: string, branchCode?: string, patientId?: number) =>
    api.get<CourseBalanceReport>(
      `/api/reports/course-balances?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}), ...(patientId ? { patientId: String(patientId) } : {}) })}`,
    ),
  staffSales: (from: string, to: string, branchCode?: string) =>
    api.get<StaffSalesReport>(
      `/api/reports/staff-sales?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`,
    ),
}

export const commissionApi = {
  schemes: () => api.get('/api/commission/schemes'),
  tiers: (schemeId: number) => api.get(`/api/commission/schemes/${schemeId}/tiers`),
  createScheme: (body: unknown) => api.post('/api/commission/schemes', body),
  updateScheme: (id: number, body: unknown) => api.put(`/api/commission/schemes/${id}`, body),
  activateScheme: (id: number) => api.patch(`/api/commission/schemes/${id}/activate`, {}),
  deactivateScheme: (id: number) => api.delete(`/api/commission/schemes/${id}`),
  report: (from: string, to: string) =>
    api.get<
      Array<{
        staffId: number | null
        staffName: string
        branchCode: string
        branchName: string
        saleCount: number
        base: number
        rate: number
        commission: number
      }>
    >(`/api/commission/report?${new URLSearchParams({ from, to })}`),
  treatmentReport: (from: string, to: string) =>
    api.get<
      Array<{
        id: number
        visitDate: string
        coursePurchaseId: number
        courseCode: string
        patientId: number
        patientHn: string
        patientName: string
        branchCode: string
        branchName: string
        treatingStaffId: number | null
        treatingStaffName: string
        ownerStaffId: number | null
        ownerStaffName: string
        gross: number
        treatmentFee: number
        ownerNet: number
      }>
    >(`/api/commission/treatment-report?${new URLSearchParams({ from, to })}`),
  closings: (month: string) =>
    api.get<
      Array<{
        id: number
        month: string
        employeeId: number
        employeeName: string
        sales: number
        version: string
        rate: number
        status: string
      }>
    >(`/api/commission/closings?month=${month}-01`),
  closeMonth: (month: string) => api.post(`/api/commission/closings/${month}-01`, {}),
  closeClosing: (id: number) => api.post(`/api/commission/closings/${id}/close`, {}),
  treatmentFees: () => api.get('/api/commission/treatment-fees'),
}

export type ClinicResource = {
  id: number
  branchCode: string
  branchName: string
  code: string
  name: string
  type: string
  status: string
  active: boolean
}
export const resourceApi = {
  list: () => api.get<ClinicResource[]>('/api/resources'),
  create: (body: object) => api.post<ClinicResource>('/api/resources', body),
  update: (id: number, body: object) => api.put<ClinicResource>(`/api/resources/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/resources/${id}`),
  activate: (id: number) => api.patch<ClinicResource>(`/api/resources/${id}/activate`, {}),
}
export type MasterData = {
  id: number
  category: string
  code: string
  name: string
  active: boolean
}
export const masterDataApi = {
  list: () => api.get<MasterData[]>('/api/master-data'),
  create: (body: object) => api.post<MasterData>('/api/master-data', body),
  update: (id: number, body: object) => api.put<MasterData>(`/api/master-data/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/master-data/${id}`),
  activate: (id: number) => api.patch<MasterData>(`/api/master-data/${id}/activate`, {}),
}

export const auditApi = {
  list: (page = 0, size = 50) => api.get(`/api/audit-logs?page=${page}&size=${size}`),
}
