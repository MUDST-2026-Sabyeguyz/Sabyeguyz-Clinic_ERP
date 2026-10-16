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
  email?: string | null
  licenseNo?: string | null
  // "HH:mm"; both set or both empty
  bookingOpenTime?: string | null
  bookingCloseTime?: string | null
  defaultAppointmentMinutes?: number | null
}
export type BranchRequest = Pick<Branch, 'branchCode' | 'name' | 'address' | 'phone'> &
  Partial<
    Pick<
      Branch,
      | 'email'
      | 'licenseNo'
      | 'bookingOpenTime'
      | 'bookingCloseTime'
      | 'defaultAppointmentMinutes'
    >
  >
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
  // validity in days, months or years; validityDays stays for older readers (it means days)
  validityValue?: number
  validityUnit?: 'Day' | 'Month' | 'Year'
  // appointment length in minutes; 0 or empty = use the branch default
  defaultDurationMinutes?: number | null
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
  receiptId?: number
}
export type PaymentLabel = 'FullyPaid' | 'Installment' | 'Deposit'
export type SaleSummary = {
  id: number
  receiptId?: number
  receiptNo: string
  paymentLabel?: PaymentLabel | null
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
  // CoursePurchase | SingleVisit | Assessment (what the line sold)
  itemType?: string
}
export type SaleReceipt = SaleSummary & { note?: string }
export type SaleDetail = SaleReceipt & {
  subtotalBeforeDiscount?: number
  billDiscountAmount?: number
  taxableAmount?: number
  taxAmount?: number
  netAmount?: number
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
  therapistName?: string | null
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
  newCustomers: number
  returningCustomers: number
  courseRenewals: number
  packageUpdates: number
  packageUpdatesScope: 'CLINIC'
  salesTotal: number
  cashReceived: number
  dailySales: Array<{ date: string; amount: number }>
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
  // 0 = no length of its own (the branch default is used)
  defaultDurationMinutes: number
  price: number
  active: boolean
}
export type ManagedServiceRequest = Omit<ManagedService, 'id' | 'active' | 'defaultDurationMinutes'> & {
  defaultDurationMinutes?: number | null
}
export const serviceManagementApi = {
  list: () => api.get<ManagedService[]>('/api/services'),
  create: (body: ManagedServiceRequest) => api.post<ManagedService>('/api/services', body),
  update: (id: number, body: ManagedServiceRequest) =>
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

export type AppointmentSlot = { time: string; available: boolean; reason: string | null }
export type AppointmentAvailability = {
  date: string
  openTime: string
  closeTime: string
  // false = the branch has no booking window yet, so a default 09:00-18:00 is shown
  windowConfigured: boolean
  durationMinutes: number | null
  stepMinutes: number
  slots: AppointmentSlot[]
  // NOT_CONNECTED: the branch has no Google Calendar; CHECKED: its Busy time was read; UNAVAILABLE: Google could not be read
  googleCalendar?: 'NOT_CONNECTED' | 'CHECKED' | 'UNAVAILABLE'
}
export const appointmentApi = {
  availability: (params: {
    branchCode: string
    therapistId: number
    date: string
    serviceId?: number
    excludeAppointmentId?: number
  }) => {
    const query = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== 0) query.set(key, String(value))
    })
    return api.get<AppointmentAvailability>(`/api/appointments/availability?${query}`)
  },
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
    api
      .post<Appointment>(`/api/appointments/${id}/link-patient`, { patientId })
      .then(normalizeAppointment),
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

export type CourseExtension = {
  id: number
  courseId: number
  requestedPatientId: number
  value: number
  unit: 'Day' | 'Month' | 'Year'
  reason: string | null
  status: 'Pending' | 'Approved' | 'Rejected'
  requestedAt: string
  decidedAt: string | null
  decisionNote: string | null
  oldExpiry: string | null
  newExpiry: string | null
}
// The original owner asks, a Manager approves; the new expiry date is shared by every holder of the course.
export const courseExtensionApi = {
  list: (courseId: number) =>
    api.get<CourseExtension[]>(`/api/course-extensions?courseId=${courseId}`),
  pending: () => api.get<CourseExtension[]>('/api/course-extensions?status=Pending'),
  create: (body: {
    courseId: number
    value: number
    unit: CourseExtension['unit']
    reason?: string
    requestedByPatientId?: number
  }) => api.post<CourseExtension>('/api/course-extensions', body),
  approve: (id: number, note?: string) =>
    api.post<CourseExtension>(`/api/course-extensions/${id}/approve`, { note }),
  reject: (id: number, note?: string) =>
    api.post<CourseExtension>(`/api/course-extensions/${id}/reject`, { note }),
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
  use: (body: {
    coursePurchaseId: number
    appointmentId?: number
    treatingStaffId?: number
    note?: string
  }) => api.post<CoursePurchase>('/api/course-usages', body),
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
    params: {
      patientId?: number
      branchCode?: string
      page?: number
      size?: number
      receiptsOnly?: boolean
    } = {},
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
  // How the pay is worked out (the tiers and the treatment fee in force today); read only, for anyone signed in
  formula: () => api.get<CommissionFormula>('/api/commission/formula'),
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
        /** null (with ownerNetPending) while the sale month is not closed: the Owner commission is not known yet */
        gross: number | null
        treatmentFee: number
        ownerNet: number | null
        ownerNetPending: boolean
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
        // why the month was last reopened (null if never)
        reopenReason?: string | null
        reopenedAt?: string | null
      }>
    >(`/api/commission/closings?month=${month}-01`),
  closeMonth: (month: string) => api.post(`/api/commission/closings/${month}-01`, {}),
  closeClosing: (id: number) => api.post(`/api/commission/closings/${id}/close`, {}),
  preview: (courseId: number, sellerStaffId?: number) =>
    api.get<{
      netPrice: number
      commissionRate: number
      commissionPool: number
      commissionPerVisit: number
    }>(
      `/api/commission/preview?${new URLSearchParams({
        courseId: String(courseId),
        ...(sellerStaffId ? { sellerStaffId: String(sellerStaffId) } : {}),
      })}`,
    ),
  treatmentFees: () => api.get<TreatmentFeeRule[]>('/api/commission/treatment-fees'),
  createTreatmentFee: (body: TreatmentFeeInput) =>
    api.post<TreatmentFeeRule>('/api/commission/treatment-fees', body),
  updateTreatmentFee: (id: number, body: Partial<TreatmentFeeInput>) =>
    api.put<TreatmentFeeRule>(`/api/commission/treatment-fees/${id}`, body),
  setTreatmentFeeEnabled: (id: number, enabled: boolean) =>
    api.patch<TreatmentFeeRule>(
      `/api/commission/treatment-fees/${id}/${enabled ? 'enable' : 'disable'}`,
      {},
    ),
  backfillSingleVisitFees: (from: string, to: string) =>
    api.post<{ recorded: number }>(
      `/api/commission/single-visit-fees/backfill?${new URLSearchParams({ from, to })}`,
      {},
    ),
  reopenClosing: (id: number, reason: string) =>
    api.post<{ id: number; status: string }>(`/api/commission/closings/${id}/reopen`, { reason }),
  myPayments: () => api.get<StaffPayment[]>('/api/commission/my-payments'),
  // the lines behind a month of the signed-in therapist's own pay, and their own pay adjustments (read only)
  myLines: (month: string) =>
    api.get<TherapistLines>('/api/commission/my-lines?month=' + encodeURIComponent(month)),
  myAdjustments: () => api.get<TherapistAdjustment[]>('/api/commission/my-adjustments'),
  payments: (month: string) => api.get<StaffPayment[]>(`/api/commission/payments?month=${month}`),
  preparePayments: (month: string) =>
    api.post<StaffPayment[]>(`/api/commission/payments/prepare?month=${month}`, {}),
  closePayment: (id: number) => api.post<StaffPayment>(`/api/commission/payments/${id}/close`, {}),
  reopenPayment: (id: number) =>
    api.post<StaffPayment>(`/api/commission/payments/${id}/reopen`, {}),
  // paidDate is the day the money was paid (yyyy-mm-dd, default today); reference is the transfer or slip number
  payPayment: (id: number, body: { paidDate?: string; reference?: string } = {}) =>
    api.post<StaffPayment>(`/api/commission/payments/${id}/pay`, body),
  adjustments: (staffId?: number) =>
    api.get<PayAdjustment[]>(`/api/commission/adjustments${staffId ? `?staffId=${staffId}` : ''}`),
  createAdjustment: (body: AdjustmentInput) =>
    api.post<PayAdjustment>('/api/commission/adjustments', body),
  approveAdjustment: (id: number) =>
    api.post<PayAdjustment>(`/api/commission/adjustments/${id}/approve`, {}),
  cancelAdjustment: (id: number) =>
    api.post<PayAdjustment>(`/api/commission/adjustments/${id}/cancel`, {}),
  externalReviewAdjustment: (id: number) =>
    api.post<PayAdjustment>(`/api/commission/adjustments/${id}/external-review`, {}),
  createApplication: (id: number, month: string, amount?: number) =>
    api.post<AdjustmentApplicationRow>(`/api/commission/adjustments/${id}/applications`, {
      month,
      amount,
    }),
  approveApplication: (id: number, reference?: string) =>
    api.post<AdjustmentApplicationRow>(`/api/commission/adjustment-applications/${id}/approve`, {
      reference,
    }),
  cancelApplication: (id: number) =>
    api.post<AdjustmentApplicationRow>(`/api/commission/adjustment-applications/${id}/cancel`, {}),
}

export type TreatmentFeeRule = {
  id: number
  feeType: 'Fixed' | 'Percentage'
  feeValue: number
  effectiveFrom: string
  effectiveTo: string | null
  enabled: boolean
  /** a ledger row already copied the rule: its type, value and start can no longer change */
  used: boolean
}
export type TreatmentFeeInput = {
  feeType: 'Fixed' | 'Percentage'
  feeValue: number
  effectiveFrom: string
  effectiveTo?: string | null
}
export type CommissionFormula = {
  asOf: string
  scheme: {
    id: number
    name: string
    version: number
    effectiveFrom: string
    effectiveTo: string | null
  } | null
  tiers: Array<{
    id: number
    order: number
    minimum: number
    maximum: number | null
    rate: number
    active: boolean
  }>
  treatmentFee: TreatmentFeeRule | null
}
export type AdjustmentApplicationRow = {
  id: number
  adjustmentId: number
  amount: number
  status: 'Draft' | 'Applied' | 'Cancelled'
  approved: boolean
  approvalReference: string | null
}
export type TherapistLine = {
  kind: 'COMMISSION' | 'FEE'
  id: number
  date: string
  patientName: string
  patientHn: string | null
  item: string
  role: string
  /** null = the sale month is not closed yet */
  amount: number | null
  reversal: boolean
  pending: boolean
  paymentStatus: 'OPEN' | 'CLOSED' | 'PAID' | null
  courseNo: string | null
  totalVisits: number | null
  grossAmount: number | null
  lineDiscount: number | null
  base: number | null
  rate: number | null
  tier: string | null
  pool: number | null
  saleNo: string | null
  saleDate: string | null
  saleType: string | null
  feeType: 'Fixed' | 'Percentage' | null
  feeValue: number | null
  feeBase: number | null
  ownerName: string | null
  treatingName: string | null
  visitNo: string | null
  singleVisit: boolean
}
export type TherapistLines = {
  month: string
  courseCommission: number | null
  singleVisitFee: number
  substituteFee: number
  pendingSessions: number
  lines: TherapistLine[]
}
export type TherapistAdjustment = {
  id: number
  originalMonth: string
  appliedMonth: string
  amount: number
  reason: string
  status: string
  processed: number
  remaining: number
  reference: string | null
  approvedBy: string | null
  applications: Array<{
    id: number
    month: string | null
    amount: number
    status: string
    approvedBy: string | null
    approvalReference: string | null
  }>
}
export type StaffPayment = {
  id: number
  staffId: number
  staffName: string
  month: string
  status: 'OPEN' | 'CLOSED' | 'PAID'
  /** null = not calculated yet (a session still waits for its sale month to be closed), never 0 */
  courseCommission: number | null
  singleVisitFee: number | null
  substituteFee: number | null
  adjustment: number | null
  net: number | null
  paidAt: string | null
  paidDate?: string | null
  paymentReference?: string | null
  closingId: number | null
  pendingSessions: number
  applications: AdjustmentApplicationRow[]
}
export type PayAdjustment = {
  id: number
  staffId: number
  staffName: string
  originalMonth: string
  appliedMonth: string
  amount: number
  reason: string
  status: 'Pending' | 'Approved' | 'PartiallyApplied' | 'Applied' | 'ExternalReview' | 'Cancelled'
  processed: number
  remaining: number
  allocationId: number | null
  singleVisitFeeId: number | null
  courseId: number | null
  visitId: number | null
  saleTransactionId: number | null
  applications: AdjustmentApplicationRow[]
}
export type AdjustmentInput = {
  staffId: number
  amount: number
  reason: string
  originalMonth?: string
  appliedMonth?: string
  allocationId?: number
  singleVisitFeeId?: number
  courseId?: number
  visitId?: number
  saleTransactionId?: number
}

export type RefundItem = { courseId: number; refundAmount: number; cancelledVisits: number }
export type RefundRow = {
  id: number
  paymentId: number
  amount: number
  paymentMethodCode: string
  status: 'Pending' | 'Completed' | 'Failed' | 'Voided'
  requestedAt: string
  refundedAt: string | null
  items: RefundItem[]
}
export const refundApi = {
  forPayment: (paymentId: number) => api.get<RefundRow[]>(`/api/payments/${paymentId}/refunds`),
  request: (
    paymentId: number,
    body: {
      amount: number
      paymentMethodCode: string
      reason: string
      items?: RefundItem[]
    },
  ) => api.post<RefundRow>(`/api/payments/${paymentId}/refunds`, body),
  complete: (id: number) => api.post<RefundRow>(`/api/refunds/${id}/complete`, {}),
  fail: (id: number) => api.post<RefundRow>(`/api/refunds/${id}/fail`, {}),
  void: (id: number) => api.post<RefundRow>(`/api/refunds/${id}/void`, {}),
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

export type CalendarBranchStatus = {
  branchCode: string
  branchName: string
  googleCalendarId: string | null
  waiting: number
  failed: number
}
export type CalendarSyncResult = {
  pushed: number
  removed: number
  failed: number
  movedFromGoogle: number
  cancelledFromGoogle: number
  revertedInGoogle: number
}
export type CalendarStatus = {
  enabled: boolean
  defaultDurationMinutes: number
  lastRunAt: string | null
  lastResult: CalendarSyncResult | null
  branches: CalendarBranchStatus[]
}

export const calendarApi = {
  status: () => api.get<CalendarStatus>('/api/calendar/status'),
  setBranchCalendar: (branchCode: string, googleCalendarId: string) =>
    api.put<CalendarBranchStatus>(`/api/calendar/branches/${encodeURIComponent(branchCode)}`, {
      googleCalendarId,
    }),
  syncNow: () => api.post<CalendarSyncResult>('/api/calendar/sync', {}),
}
