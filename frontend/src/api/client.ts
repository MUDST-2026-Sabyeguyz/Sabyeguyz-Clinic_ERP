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
  staffId: number | null
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
  branchCode: string
  name: string
  address?: string
  phone?: string
  email?: string
  licenseNumber?: string
  licenseNo?: string | null
  taxId?: string
  bookingOpenTime?: string | null
  bookingCloseTime?: string | null
  defaultAppointmentMinutes?: number | null
  active: boolean
}
export type BranchRequest = {
  branchCode: string
  name: string
  address?: string
  phone?: string
  email?: string
  licenseNo?: string | null
  bookingOpenTime?: string | null
  bookingCloseTime?: string | null
  defaultAppointmentMinutes?: number | null
}
type BackendBranch = {
  id: number
  branchCode: string
  name: string
  address?: string
  phone?: string
  email?: string | null
  licenseNo?: string | null
  bookingOpenTime?: string | null
  bookingCloseTime?: string | null
  defaultAppointmentMinutes?: number | null
  active: boolean
}

function normalizeBranch(branch: BackendBranch): Branch {
  return {
    id: branch.id,
    code: branch.branchCode,
    numericCode: branch.branchCode,
    branchCode: branch.branchCode,
    name: branch.name,
    address: branch.address,
    phone: branch.phone,
    email: branch.email ?? undefined,
    licenseNumber: branch.licenseNo ?? undefined,
    licenseNo: branch.licenseNo,
    bookingOpenTime: branch.bookingOpenTime,
    bookingCloseTime: branch.bookingCloseTime,
    defaultAppointmentMinutes: branch.defaultAppointmentMinutes,
    active: branch.active,
  }
}
export type Staff = {
  id: number
  branchCode?: string
  branchName?: string
  position?: string
  userId?: number
  username?: string
  role?: 'MANAGER' | 'THERAPIST'
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
  validityValue?: number
  validityUnit?: 'Day' | 'Month' | 'Year'
  defaultDurationMinutes?: number | null
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
export type PaymentLabelCode = 'FULL' | 'PAID' | 'INSTALLMENT' | 'DEPOSIT'
export type PaymentStatusOption = {
  code: PaymentLabelCode
  label: string
  enabled: boolean
}
// สถานะที่ฐานข้อมูลอนุญาตให้บันทึกได้ตาม migration ปัจจุบัน
export type AppointmentStatus =
  | 'UNCONFIRMED'
  | 'CONFIRMED'
  | 'CANCELLED'
// endAt เก็บเวลาจบนัดจริง ไม่คำนวณซ้ำจากระยะเวลาบริการ
export type Appointment = {
  id: number
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
// คำขอต่ออายุใช้ workflow แยกจากการแก้วันหมดอายุโดยตรง
export type CourseExtensionRequest = {
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
export type CoursePurchase = {
  id: number
  receiptId?: number | null
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
  receiptId?: number | null
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
// Payload ขายรวมรายการชำระและการใช้คอร์สในคำขอเดียว
export type SaleCreateRequest = {
  appointmentId?: number
  patientId: number
  branchCode: string
  salespersonStaffId: number
  treatingStaffId?: number
  note?: string
  paymentLabelCode: PaymentLabelCode
  items: Array<{
    type: 'SERVICE' | 'COURSE'
    referenceId: number
    quantity: number
    discountType?: 'PERCENT' | 'FIXED'
    discountValue?: number
  }>
  billDiscountType?: 'PERCENT' | 'FIXED'
  billDiscountValue?: number
  courseUsages?: Array<{ coursePurchaseId: number; note?: string; treatmentId?: number }>
  payments: Array<{ paymentMethodCode: string; amount: number }>
}
export type SaleDetail = SaleReceipt & {
  paymentLabel: 'FullyPaid' | 'Installment' | 'Deposit'
  subtotalBeforeDiscount: number
  billDiscountAmount: number
  netAmount: number
  branch?: { code: string; name: string; address: string; phone: string; email?: string; licenseNumber?: string }
  items: Array<{
    id: number
    type: string
    description: string
    unitName?: string
    quantity: number
    unitPrice?: number
    lineDiscountAmount?: number
    allocatedBillDiscountAmount?: number
    lineTotal: number
  }>
  payments: Array<{
    id: number
    type: string
    paymentMethodCode: string
    amount: number
    paidAt?: string
  }>
}
export type CourseUseRequest = {
  coursePurchaseId: number
  appointmentId: number
  patientId: number
  treatingStaffId: number
  note?: string
  treatmentId?: number
}
export type CourseUsageTransaction = {
  id: number | null
  usageId?: number
  therapistName?: string
  coursePurchaseId: number
  patientName: string
  patientHn: string
  courseName: string
  branchCode: string
  usedAt: string
  voidedAt?: string | null
  voidReason?: string | null
}
export type CourseUsagePrintDocument = {
  id: number
  courseId: number
  usageId: number
  version: number
  issuedAt: string
  issuedBy: string
  documentSnapshot: string
  events: Array<{ id: number; type: 'Issued' | 'Reprint'; printedAt: string; printedBy: string }>
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
  salesTotal: number
  cashReceived: number
  courseRenewals: number
  packageUpdates: number
  packageUpdatesScope: 'CLINIC' | string
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
    type: string
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
      `/api/patients?${new URLSearchParams({
        query: /^hn[\s:#-]*/i.test(query.trim())
          ? query
              .trim()
              .replace(/^hn[\s:#-]*/i, '')
              .replace(/[\s-]/g, '')
          : /^\d[\d\s-]*$/.test(query.trim())
            ? query.trim().replace(/\D/g, '')
            : query,
        page: String(page),
        size: String(size),
        sort,
        ...(branchCode ? { branchCode } : {}),
      })}`,
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
  listActive: async () => (await branchApi.list()).filter((branch) => branch.active !== false),
  get: (id: number) => api.get<BackendBranch>(`/api/branches/${id}`).then(normalizeBranch),
  create: (body: BranchRequest | { code: string; numericCode: string; name: string }) =>
    api
      .post<BackendBranch>('/api/branches', {
        ...body,
        branchCode: 'branchCode' in body ? body.branchCode : body.code || body.numericCode,
        name: body.name,
      })
      .then(normalizeBranch),
  update: (id: number, body: BranchRequest | { code: string; numericCode: string; name: string }) =>
    api
      .put<BackendBranch>(`/api/branches/${id}`, {
        ...body,
        branchCode: 'branchCode' in body ? body.branchCode : body.code || body.numericCode,
        name: body.name,
      })
      .then(normalizeBranch),
  activate: (id: number) => api.patch<BackendBranch>(`/api/branches/${id}/activate`, {}),
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
export type ManagedServiceRequest = Omit<
  ManagedService,
  'id' | 'active' | 'defaultDurationMinutes'
> & {
  defaultDurationMinutes?: number | null
}
export const serviceManagementApi = {
  list: () => api.get<ManagedService[]>('/api/services'),
  create: (body: ManagedServiceRequest) => api.post<ManagedService>('/api/services', body),
  update: (id: number, body: ManagedServiceRequest) =>
    api.put<ManagedService>(`/api/services/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/services/${id}`),
  activate: (id: number) => api.patch<ManagedService>(`/api/services/${id}/activate`, {}),
  changeType: (id: number, type: 'SingleVisit' | 'Course') =>
    api.post<ManagedService>(`/api/services/${id}/type`, { type }),
}

export const appointmentApi = {
  progress: (id: number) => api.get<AppointmentProgress>(`/api/appointments/${id}/progress`),
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
  }) => {
    const query = new URLSearchParams({
      branchCode: params.branchCode,
      therapistId: String(params.therapistId),
      date: params.date,
      ...(params.serviceId ? { serviceId: String(params.serviceId) } : {}),
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

export const courseApi = {
  usagePrints: (courseId: number) =>
    api.get<CourseUsagePrintDocument[]>(`/api/course-purchases/${courseId}/usage-prints`),
  usagePrint: (printId: number) =>
    api.get<CourseUsagePrintDocument>(`/api/course-usage-prints/${printId}`),
  reprintUsage: (printId: number) =>
    api.post<CourseUsagePrintDocument>(`/api/course-usage-prints/${printId}/reprints`, {}),
  reviseUsagePrint: (usageId: number) =>
    api.post<CourseUsagePrintDocument>(`/api/course-usages/${usageId}/print-revisions`, {}),
  // ขอรายการต่ออายุหรือกรองตามคอร์ส/สถานะ
  extensions: (params: { courseId?: number; status?: CourseExtensionRequest['status'] } = {}) => {
    const query = new URLSearchParams()
    if (params.courseId) query.set('courseId', String(params.courseId))
    if (params.status) query.set('status', params.status)
    return api.get<CourseExtensionRequest[]>(
      `/api/course-extensions${query.size ? `?${query.toString()}` : ''}`,
    )
  },
  // ส่งคำขอให้ Manager พิจารณาก่อนปรับวันหมดอายุ
  requestExtension: (body: {
    courseId: number
    value: number
    unit: CourseExtensionRequest['unit']
    reason?: string
    requestedByPatientId: number
  }) => api.post<CourseExtensionRequest>('/api/course-extensions', body),
  approveExtension: (id: number, note?: string) =>
    api.post<CourseExtensionRequest>(`/api/course-extensions/${id}/approve`, { note }),
  rejectExtension: (id: number, note?: string) =>
    api.post<CourseExtensionRequest>(`/api/course-extensions/${id}/reject`, { note }),
  usageTransactions: () => api.get<CourseUsageTransaction[]>('/api/course-usages'),
  list: () => api.get<Array<Course & { active: boolean }>>('/api/courses'),
  get: (id: number) => api.get<Course>(`/api/courses/${id}`),
  create: (body: Omit<Course, 'id' | 'active'>) => api.post<Course>('/api/courses', body),
  update: (id: number, body: Omit<Course, 'id' | 'active'>) =>
    api.put<Course>(`/api/courses/${id}`, body),
  deactivate: (id: number) => api.delete(`/api/courses/${id}`),
  patientCourses: (patientId: number) =>
    api.get<CoursePurchase[]>(`/api/patients/${patientId}/courses`),
  purchase: (body: { patientId: number; courseId: number; branchCode: string; caseOwnerStaffId: number; paymentLabelCode: PaymentLabelCode; discountType?: string; discountValue?: number }) =>
    api.post<CoursePurchase>('/api/course-purchases', body),
  ledger: (id: number) =>
    api.get<{
      purchase: CoursePurchase & {
        commissionStatus?: string
        lockedCommissionRate?: number | null
        totalCourseCommissionPool?: number | null
        commissionAllocationPerVisit?: number | null
      }
      movements: unknown[]
      allocations: Array<{
        visitDate: string
        gross: number | null
        fee: number | null
        ownerNet: number | null
      }>
    }>(`/api/course-purchases/${id}`),
  use: (body: CourseUseRequest) => api.post<CoursePurchase>('/api/course-usages', body),
  useBatch: (body: { usages: CourseUseRequest[] }) => api.post<CoursePurchase[]>('/api/course-usages/batch', body),
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
  findReceipt: (receiptNo: string, branchCode: string) =>
    api.get<SaleDetail>(`/api/receipts/search?${new URLSearchParams({ receiptNo, branchCode })}`),
  create: (body: SaleCreateRequest) => api.post<SaleReceipt>('/api/payments', body),
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

export const paymentStatusApi = {
  list: () => api.get<PaymentStatusOption[]>('/api/payment-statuses'),
  setEnabled: (code: PaymentStatusOption['code'], enabled: boolean) =>
    api.patch<PaymentStatusOption>(`/api/payment-statuses/${code}`, { enabled }),
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
  email: string
  role: 'MANAGER' | 'THERAPIST'
  employeeCode: string
  fullName: string
  phone?: string
  branchCode?: string
  branchName?: string
  active: boolean
}
// สิทธิ์เฉพาะผู้ใช้ที่เพิ่มหรือยกเว้นจาก role หลัก
export type UserPermissionOverride = {
  permissionId: number
  permissionName: string
  allowed: boolean
  updatedBy: number
  updatedAt: string
}
export type PermissionCatalogItem = {
  id: number
  permissionName: string
  moduleName: string | null
}
export const userManagementApi = {
  list: () => api.get<ManagedUser[]>('/api/users'),
  permissionCatalog: () => api.get<PermissionCatalogItem[]>('/api/permissions'),
  permissionOverrides: (userId: number) =>
    api.get<UserPermissionOverride[]>(`/api/users/${userId}/permissions`),
  setPermissionOverride: (userId: number, permissionId: number, allowed: boolean) =>
    api.put<UserPermissionOverride>(`/api/users/${userId}/permissions/${permissionId}`, { allowed }),
  resetPermissionOverride: (userId: number, permissionId: number) =>
    api.delete(`/api/users/${userId}/permissions/${permissionId}`),
  create: (body: {
    username: string
    email: string
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
      email: string
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
export type ManagerAdjustment = {
  id: number
  staffId: number
  staffName: string
  originalMonth: string
  appliedMonth: string
  amount: number
  reason: string
  status: string
  processed: number
  remaining: number
  allocationId: number | null
  singleVisitFeeId: number | null
  courseId: number | null
  visitId: number | null
  saleTransactionId: number | null
  approvedBy?: number | null
  approvedAt?: string | null
  applications: Array<{
    id: number
    adjustmentId: number
    month?: string
    amount: number
    status: string
    approved: boolean
    approvedBy?: number | null
    approvedAt?: string | null
    approvalReference: string | null
  }>
}
// null หมายถึง API ยังไม่มีตัวเลข ไม่ใช่ยอดศูนย์
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
  pendingSessions: number | null
  applications: Array<{
    id: number
    adjustmentId: number
    amount: number
    status: string
    approved: boolean
    approvalReference: string | null
  }>
}

export type TreatmentFeeRule = {
  id: number
  name?: string | null
  feeType: 'Fixed' | 'Percentage'
  feeValue: number
  effectiveFrom: string
  effectiveTo: string | null
  enabled: boolean
  used: boolean
  lastEditedBy?: string | null
  lastEditedAt?: string | null
}
export type TreatmentFeeInput = {
  name?: string
  feeType: 'Fixed' | 'Percentage'
  feeValue: number
  effectiveFrom: string
  effectiveTo?: string | null
  branchCode: string
}

export type CommissionPreviewParams = {
  courseId: number
  branchCode: string
  sellerStaffId?: number
  price?: number
  discountType?: string
  discountValue?: number
}

export type CommissionPreview = {
  netPrice: number
  commissionRate: number
  commissionPool: number
  commissionPerVisit: number
  totalVisits: number
  schemeVersion: number
  monthlySalesToDate: number
}

// Endpoint ปิดยอดและจ่ายคอมมิชชันต้องระบุเดือนกับสาขาตาม contract
export const commissionApi = {
  preview: (params: CommissionPreviewParams) => {
    const query = new URLSearchParams({
      courseId: String(params.courseId),
      branchCode: params.branchCode,
    })
    if (params.sellerStaffId != null) query.set('sellerStaffId', String(params.sellerStaffId))
    if (params.price != null) query.set('price', String(params.price))
    if (params.discountType) query.set('discountType', params.discountType)
    if (params.discountValue != null) query.set('discountValue', String(params.discountValue))
    return api.get<CommissionPreview>(`/api/commission/preview?${query.toString()}`)
  },
  payments: (month: string, branchCode: string) =>
    api.get<StaffPayment[]>(`/api/commission/payments?${new URLSearchParams({ month, branchCode })}`),
  schemes: () => api.get('/api/commission/schemes'),
  tiers: (schemeId: number) => api.get(`/api/commission/schemes/${schemeId}/tiers`),
  createScheme: (body: unknown) => api.post('/api/commission/schemes', body),
  createSchemeVersion: (id: number, body: unknown) =>
    api.post(`/api/commission/schemes/${id}/versions`, body),
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
  closings: (month: string, branchCode: string) =>
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
    >(`/api/commission/closings?${new URLSearchParams({ month, branchCode })}`),
  branchClosing: (month: string, branchCode: string) =>
    api.get<{ id: number | null; branchCode: string; month: string; status: 'OPEN' | 'CLOSED'; closedAt: string | null; closedBy: number | null }>(
      `/api/commission/branch-closings?${new URLSearchParams({ month, branchCode })}`,
    ),
  reopenEmptyBranch: (month: string, branchCode: string, reason: string) =>
    api.post(`/api/commission/branch-closings/${month}/reopen?${new URLSearchParams({ branchCode })}`, { reason }),
  closeMonth: (month: string, branchCode: string) =>
    api.post(`/api/commission/closings/${month}?${new URLSearchParams({ branchCode })}`, {}),
  preparePayments: (month: string, branchCode: string) =>
    api.post<StaffPayment[]>(`/api/commission/payments/prepare?${new URLSearchParams({ month, branchCode })}`, {}),
  closePayments: (month: string, branchCode: string) =>
    api.post<StaffPayment[]>(`/api/commission/payments/close?${new URLSearchParams({ month, branchCode })}`, {}),
  reopenPayment: (id: number) => api.post<StaffPayment>(`/api/commission/payments/${id}/reopen`, {}),
  pay: (id: number, paidDate: string, reference: string) =>
    api.post<StaffPayment>(`/api/commission/payments/${id}/pay`, { paidDate, reference }),
  reopenClosing: (id: number, reason: string) =>
    api.post(`/api/commission/closings/${id}/reopen`, { reason }),
  treatmentFees: () => api.get('/api/commission/treatment-fees'),
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
  myPayments: (month?: string) =>
    api.get<StaffPayment[]>(`/api/commission/my-payments${month ? `?month=${encodeURIComponent(month)}` : ''}`),
  myLines: (month: string) =>
    api.get<TherapistLines>(`/api/commission/my-lines?month=${encodeURIComponent(month)}`),
  myAdjustments: () => api.get<TherapistAdjustment[]>('/api/commission/my-adjustments'),
  adjustments: () => api.get<ManagerAdjustment[]>('/api/commission/adjustments'),
  adjustment: (id: number) => api.get<ManagerAdjustment>(`/api/commission/adjustments/${id}`),
  approveAdjustment: (id: number) =>
    api.post<ManagerAdjustment>(`/api/commission/adjustments/${id}/approve`, {}),
  cancelAdjustment: (id: number) =>
    api.post<ManagerAdjustment>(`/api/commission/adjustments/${id}/cancel`, {}),
  createAdjustmentApplication: (id: number, month: string, amount: number, branchCode: string) =>
    api.post(`/api/commission/adjustments/${id}/applications`, { month, amount, branchCode }),
  approveAdjustmentApplication: (id: number, reference?: string) =>
    api.post(`/api/commission/adjustment-applications/${id}/approve`, { reference }),
  cancelAdjustmentApplication: (id: number) =>
    api.post(`/api/commission/adjustment-applications/${id}/cancel`, {}),
  createAdjustment: (body: {
    staffId: number
    originalMonth: string
    appliedMonth: string
    amount: number
    reason: string
    allocationId?: number
    singleVisitFeeId?: number
    courseId?: number
    visitId?: number
    saleTransactionId?: number
  }) => api.post<ManagerAdjustment>('/api/commission/adjustments', body),
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

export type AuditEntry = {
  id: number
  tableName: string
  recordId: string
  action: 'INSERT' | 'UPDATE' | 'DELETE'
  oldValue: string | null
  newValue: string | null
  changedBy: number | null
  changedAt: string
  changedByName: string | null
}
export type AuditPage = {
  items: AuditEntry[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}
export type AuditFilter = {
  tableName?: string
  recordId?: string
  action?: string
  changedBy?: number
  from?: string
  to?: string
}
export const auditApi = {
  list: (page = 0, size = 50, filter: AuditFilter = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    for (const [key, value] of Object.entries(filter)) {
      if (value !== undefined && value !== '') params.set(key, String(value))
    }
    return api.get<AuditPage>(`/api/audit-logs?${params}`)
  },
  tables: () => api.get<string[]>('/api/audit-logs/tables'),
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
