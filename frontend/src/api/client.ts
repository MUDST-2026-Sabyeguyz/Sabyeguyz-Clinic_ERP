const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

export type ApiErrorBody = {
  message?: string
  fields?: Record<string, string>
}

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
      if (typeof window !== 'undefined' && window.location.pathname.startsWith('/staff/'))
        window.location.assign('/')
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
  code: string
  numericCode: string
  name: string
  address?: string
  phone?: string
  email?: string
  licenseNumber?: string
  taxId?: string
  active: boolean
}
type BackendBranch = {
  id: number
  branchCode: string
  name: string
  address?: string
  phone?: string
  email?: string | null
  licenseNo?: string | null
  active: boolean
}

function normalizeBranch(branch: BackendBranch): Branch {
  return {
    id: branch.id,
    code: branch.branchCode,
    numericCode: branch.branchCode,
    name: branch.name,
    address: branch.address,
    phone: branch.phone,
    email: branch.email ?? undefined,
    licenseNumber: branch.licenseNo ?? undefined,
    active: branch.active,
  }
}
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
export type Therapist = {
  id: number
  code: string
  fullName: string
  branchCode: string
}
export type PaymentMethod = {
  id: number
  code: string
  name: string
  active: boolean
}
export type AppointmentStatus =
  'CONFIRMED' | 'ARRIVED' | 'IN_SERVICE' | 'COMPLETED' | 'CANCELLED' | 'RESCHEDULED' | 'NO_SHOW'
export type Appointment = {
  id: number
  branchCode: string
  patient: { id: number; hn: string; fullName: string }
  therapist: { id: number; fullName: string }
  service: { id: number; name: string; durationMinutes: number }
  startAt: string
  endAt: string
  status: AppointmentStatus
  note?: string
  cancelReason?: string
}
export type AppointmentPage = Page<Appointment>
export type CoursePurchase = {
  id: number
  patient: Patient
  purchaser?: { id: number; hn: string; fullName: string }
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
  payments: Array<{
    id: number
    type: string
    paymentMethodCode: string
    amount: number
  }>
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
    api.post<AuthResponse>('/api/auth/login', {
      username: identifier,
      password,
    }),
  register: (username: string, password: string, role: 'MANAGER' | 'THERAPIST', email?: string) =>
    api.post<AuthResponse>('/api/auth/register', {
      username,
      email,
      password,
      role,
    }),
}

export const patientApi = {
  search: (query = '', page = 0, size = 20, branchCode = '', sort = 'nameAsc') =>
    api.get<Page<Patient>>(
      `/api/patients?${new URLSearchParams({ query, page: String(page), size: String(size), sort, ...(branchCode ? { branchCode } : {}) })}`,
    ),
  searchAll: async (query = '', branchCode = '') => {
    const first = await patientApi.search(query, 0, 100, branchCode)
    const rest = await Promise.all(
      Array.from({ length: Math.max(0, first.totalPages - 1) }, (_, index) =>
        patientApi.search(query, index + 1, 100, branchCode),
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
  list: () => api.get<BackendBranch[]>('/api/branches').then((items) => items.map(normalizeBranch)),
  get: (id: number) => api.get<BackendBranch>(`/api/branches/${id}`).then(normalizeBranch),
  create: (body: { code: string; numericCode: string; name: string }) =>
    api
      .post<BackendBranch>('/api/branches', {
        branchCode: body.code || body.numericCode,
        name: body.name,
        address: '',
        phone: '',
      })
      .then(normalizeBranch),
  update: (id: number, body: { code: string; numericCode: string; name: string }) =>
    api
      .put<BackendBranch>(`/api/branches/${id}`, {
        branchCode: body.code || body.numericCode,
        name: body.name,
        address: '',
        phone: '',
      })
      .then(normalizeBranch),
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
      Array<{
        id: number
        code: string
        fullName: string
        branchCode: string
        active: boolean
      }>
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

export const appointmentApi = {
  search: (
    params: {
      branchCode?: string
      date?: string
      therapistId?: number
      status?: AppointmentStatus
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
  create: (body: {
    branchCode: string
    patientId: number
    therapistId: number
    serviceId: number
    startAt: string
    note?: string
  }) => api.post<Appointment>('/api/appointments', body),
  checkIn: (id: number) => api.post<Appointment>(`/api/appointments/${id}/check-in`, {}),
  startService: (id: number) => api.post<Appointment>(`/api/appointments/${id}/start-service`, {}),
  noShow: (id: number) => api.post<Appointment>(`/api/appointments/${id}/no-show`, {}),
  complete: (id: number) => api.post<Appointment>(`/api/appointments/${id}/complete`, {}),
  cancel: (id: number, reason: string) =>
    api.post<Appointment>(`/api/appointments/${id}/cancel`, { reason }),
  reschedule: (id: number, body: { startAt: string; therapistId?: number }) =>
    api.patch<Appointment>(`/api/appointments/${id}`, body),
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
  ledger: (id: number) =>
    api.get<{
      purchase: CoursePurchase & {
        commissionStatus?: string
        lockedCommissionRate?: number
        totalCourseCommissionPool?: number
        commissionAllocationPerVisit?: number
      }
      movements: unknown[]
      allocations: Array<{
        visitDate: string
        gross: number
        fee: number
        ownerNet: number
      }>
    }>(`/api/course-purchases/${id}`),
  use: (body: { coursePurchaseId: number; appointmentId?: number; note?: string }) =>
    api.post<CoursePurchase>('/api/course-usages', body),
  reverse: (movementId: number, reason: string) =>
    api.post<CoursePurchase>(`/api/course-movements/${movementId}/reverse`, {
      reason,
    }),
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

export const staffApi = {
  list: () => api.get<Staff[]>('/api/staff'),
  get: (id: number) => api.get<Staff>(`/api/staff/${id}`),
  create: (body: { userId: number; employeeCode: string; fullName: string; phone?: string }) =>
    api.post<Staff>('/api/staff', body),
  update: (
    id: number,
    body: {
      userId: number
      employeeCode: string
      fullName: string
      phone?: string
    },
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

export const reportApi = {
  dashboard: (from: string, to: string, branchCode?: string) =>
    api.get<DashboardResponse>(
      `/api/dashboard/summary?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`,
    ),
  appointments: (from: string, to: string, branchCode?: string) =>
    api.get<AppointmentReport>(
      `/api/reports/appointments?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`,
    ),
  revenue: (from: string, to: string, branchCode?: string) =>
    api.get<RevenueReport>(
      `/api/reports/revenue?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`,
    ),
  balances: (from: string, to: string, branchCode?: string, patientId?: number) =>
    api.get<CourseBalanceReport>(
      `/api/reports/course-balances?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}), ...(patientId ? { patientId: String(patientId) } : {}) })}`,
    ),
  staffSales: (from: string, to: string, branchCode?: string) =>
    api.get<StaffSalesReport>(
      `/api/reports/staff-sales?${new URLSearchParams({ from, to, ...(branchCode ? { branchCode } : {}) })}`,
    ),
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
  treatmentFee: {
    id: number
    feeType: 'Fixed' | 'Percentage'
    feeValue: number
    effectiveFrom: string
    effectiveTo: string | null
    enabled: boolean
    used: boolean
  } | null
}
export type TherapistLine = {
  kind: 'COMMISSION' | 'FEE'
  id: number
  date: string
  patientName: string
  patientHn: string | null
  item: string
  role: string
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
  applications: Array<{
    id: number
    adjustmentId: number
    amount: number
    status: string
    approved: boolean
    approvalReference: string | null
  }>
}

export const commissionApi = {
  schemes: () => api.get('/api/commission/schemes'),
  tiers: (schemeId: number) => api.get(`/api/commission/schemes/${schemeId}/tiers`),
  createScheme: (body: unknown) => api.post('/api/commission/schemes', body),
  updateScheme: (id: number, body: unknown) => api.put(`/api/commission/schemes/${id}`, body),
  activateScheme: (id: number) => api.patch(`/api/commission/schemes/${id}/activate`, {}),
  deactivateScheme: (id: number) => api.delete(`/api/commission/schemes/${id}`),
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
  myPayments: () => api.get<StaffPayment[]>('/api/commission/my-payments'),
  myLines: (month: string) =>
    api.get<TherapistLines>(`/api/commission/my-lines?month=${encodeURIComponent(month)}`),
  myAdjustments: () => api.get<TherapistAdjustment[]>('/api/commission/my-adjustments'),
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

export const calendarIntegrationApi = {
  startGoogleConnect: () =>
    api.post<{ authorizationUrl: string }>('/api/calendar/google/connect', {}),
}
