<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import {
  type TreatmentFeeRule,
  branchApi,
  commissionApi,
  courseApi,
  paymentMethodApi,
  resourceApi,
  masterDataApi,
  serviceManagementApi,
  userManagementApi,
} from '@/api/client'
import { clinicToday } from '@/utils/clinicDate'
import { baht2, recordedAt, versionCycles, versionStatus } from '../commissionVersion'

type SectionKey = 'branches' | 'users' | 'services' | 'payments' | 'rooms' | 'commission' | 'tiers' | 'master'
// Rows are edited generically from form fields of different shapes, so their values stay loosely typed.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Row = Record<string, any>
const sections: Array<{ key: SectionKey; label: string }> = [
  { key: 'branches', label: 'สาขา' },
  { key: 'users', label: 'ผู้ใช้และสิทธิ์' },
  { key: 'services', label: 'บริการและคอร์ส' },
  { key: 'payments', label: 'การชำระเงิน' },
  { key: 'rooms', label: 'Room / Resource' },
  { key: 'commission', label: 'กฎค่ามือ' },
  { key: 'tiers', label: 'กฎค่าคอมมิชชัน' },
  { key: 'master', label: 'Master Data' },
]
// ?section=tiers opens that category (the history page links back to it)
function initialSection(): SectionKey {
  const wanted = new URLSearchParams(window.location.search).get('section')
  return sections.find((item) => item.key === wanted)?.key ?? 'branches'
}
const section = ref<SectionKey>(initialSection()),
  drawerOpen = ref(false),
  editingIndex = ref<number | null>(null),
  savedMessage = ref(''),
  saveModalOpen = ref(false)
const permissions = ['Patient', 'Visit', 'Treatment', 'Course', 'Transaction', 'Commission']
const fieldErrors = reactive<Record<string, boolean>>({})
const validationMessage = ref('')
const loadError = ref('')
const option = (value: string) => ({ value, label: value })
const statusOptions = computed(() => {
  return [option('ใช้งาน'), option('ปิดใช้งาน')]
})
const data = reactive<Record<Exclude<SectionKey, 'payments'>, Row[]>>({
  branches: [],
  users: [],
  services: [],
  rooms: [],
  commission: [],
  tiers: [],
  master: [],
})
const paymentMethods = reactive<Array<{ id: number; name: string; note: string; active: boolean }>>(
  [],
)
// a closed branch (ปิดใช้งาน) is not offered anywhere: not for a user, a room or a resource
const openBranches = computed(() => data.branches.filter((branch) => branch.status === 'ใช้งาน'))
const branchOptions = computed(() => openBranches.value.map((branch) => option(String(branch.name))))
const resourceStatuses: Record<string, string> = {
  ACTIVE: 'ใช้งาน',
  INACTIVE: 'ปิดใช้งาน',
}
const status = (active: boolean) => (active ? 'ใช้งาน' : 'ปิดใช้งาน')
const branchRow = (item: Awaited<ReturnType<typeof branchApi.create>>) => ({
  id: item.id,
  code: item.branchCode,
  name: item.name,
  address: item.address,
  contact: item.phone,
  email: item.email ?? '',
  licenseNo: item.licenseNo ?? '',
  openTime: item.bookingOpenTime ?? '',
  closeTime: item.bookingCloseTime ?? '',
  appointmentMinutes: item.defaultAppointmentMinutes ? String(item.defaultAppointmentMinutes) : '',
  status: status(item.active),
})
const userRow = (item: Awaited<ReturnType<typeof userManagementApi.create>>) => ({
  ...item,
  code: item.employeeCode,
  name: item.fullName,
  role: item.role === 'MANAGER' ? 'Manager' : 'Therapist',
  branch: item.branchName ?? 'ทุกสาขา',
  permission: item.role === 'MANAGER' ? permissions.join(' ') : 'Patient Visit Treatment',
  status: status(item.active),
})
const serviceRow = (item: Awaited<ReturnType<typeof serviceManagementApi.create>>) => ({
  ...item,
  type: 'Single Visit',
  // a single visit is always one visit; its own field is the optional appointment length
  sessions: '1',
  duration: item.defaultDurationMinutes ? String(item.defaultDurationMinutes) : '',
  status: status(item.active),
})
const courseRow = (item: Awaited<ReturnType<typeof courseApi.create>>) => ({
  ...item,
  type: 'Course',
  // there is no separate bonus: any earlier bonus sessions are counted in the total
  sessions: String(item.sessions + Number(item.bonusSessions ?? 0)),
  duration: item.defaultDurationMinutes ? String(item.defaultDurationMinutes) : '',
  validityValue: item.validityValue ?? item.validityDays,
  validityUnit: item.validityUnit ?? 'Day',
  status: status(Boolean(item.active)),
})
const resourceRow = (item: Awaited<ReturnType<typeof resourceApi.create>>) => ({
  ...item,
  branch: item.branchName,
  type: item.type === 'ROOM' ? 'Treatment Room' : 'Resource',
  status: resourceStatuses[item.status] ?? item.status,
})
const masterRow = (item: Awaited<ReturnType<typeof masterDataApi.create>>) => ({
  ...item,
  count: 1,
  examples: item.name,
  status: status(item.active),
})
const bahtText = (value: number) =>
  Number(value).toLocaleString('th-TH', { maximumFractionDigits: 2 })
const feeCalculation = (rule: { feeType: string; feeValue: number }) =>
  rule.feeType === 'Fixed'
    ? `${bahtText(rule.feeValue)} บาท / ครั้ง`
    : `${bahtText(rule.feeValue)}% ของยอดรายการ`
const feeRow = (rule: TreatmentFeeRule) => ({
  kind: 'fee',
  id: rule.id,
  name: rule.name?.trim() || 'ค่ามือ — บริการรายครั้งและรักษาแทน',
  recipient: 'นักกายภาพทั้งหมด',
  calculation: feeCalculation(rule),
  status: versionStatus({ effectiveFrom: rule.effectiveFrom, effectiveTo: rule.effectiveTo }).label,
  lastEditedBy: rule.lastEditedBy ?? '-',
  lastEditedAt: recordedAt(rule.lastEditedAt),
  feeType: rule.feeType,
  feeValue: rule.feeValue,
  effectiveFrom: rule.effectiveFrom,
  effectiveTo: rule.effectiveTo ?? '',
  used: rule.used,
})
// The "กฎค่ามือ" page lists the treatment fee rules, which the Manager sets here; the "กฎค่าคอมมิชชัน" page shows the Scheme
// Version in force today with its tiers (a version still waiting to start is on the history page).
type LatestVersion = { id: number; version: number; effectiveFrom: string; effectiveTo: string | null }
type LatestTier = { order: number; minimum: number; maximum: number | null; rate: number }
const latestVersion = ref<LatestVersion | null>(null)
const latestTiers = ref<LatestTier[]>([])
async function loadCommissionRows() {
  try {
    const rules = await commissionApi.treatmentFees()
    const rows: Row[] = rules.map(feeRow)
    try {
      // the schemes come newest start first: the first one that has started and not ended is the one in force
      const today = clinicToday()
      const inForce =
        ((await commissionApi.schemes()) as LatestVersion[]).find(
          (item) => item.effectiveFrom <= today && (!item.effectiveTo || item.effectiveTo >= today),
        ) ?? null
      latestVersion.value = inForce
      latestTiers.value = inForce
        ? ((await commissionApi.tiers(inForce.id)) as LatestTier[])
            .map(({ order, minimum, maximum, rate }) => ({ order, minimum, maximum, rate }))
            .sort((a, b) => a.order - b.order)
        : []
    } catch {
      // the version detail is a convenience: the fee rules still show
    }
    data.commission.splice(0, data.commission.length, ...rows)
    return true
  } catch {
    return false
  }
}
// The treatment fee rules as versions (V1, V2… by start date): the one in force and the one waiting to start are shown.
const feeVersions = computed(() =>
  data.commission
    .slice()
    .sort((a, b) => String(a.effectiveFrom).localeCompare(String(b.effectiveFrom)))
    .map((row, index) => {
      const range = { effectiveFrom: String(row.effectiveFrom), effectiveTo: row.effectiveTo ? String(row.effectiveTo) : null }
      return { number: index + 1, row, range, status: versionStatus(range) }
    }),
)
const feeInForce = computed(() => feeVersions.value.find((item) => item.status.tone === 'current') ?? null)
const feePending = computed(() => feeVersions.value.find((item) => item.status.tone === 'pending') ?? null)
const shownFeeVersions = computed(() =>
  [feeInForce.value, feePending.value].filter((item): item is NonNullable<typeof item> => item !== null),
)
/** The month the next version of a rule can start: the one after the current month ("YYYY-MM"). */
function nextCycleMonth() {
  const [year, month] = clinicToday().split('-').map(Number)
  return new Date(Date.UTC(year!, month!, 1)).toISOString().slice(0, 7)
}
async function loadBackendSettings() {
  const [branches, users, services, courses, payments, resources, master] =
    await Promise.allSettled([
      branchApi.list(),
      userManagementApi.list(),
      serviceManagementApi.list(),
      courseApi.list(),
      paymentMethodApi.list(),
      resourceApi.list(),
      masterDataApi.list(),
    ])
  const failures: string[] = []
  const results = [branches, users, services, courses, payments, resources, master]
  const names = [
    'สาขา',
    'ผู้ใช้งาน',
    'บริการ',
    'คอร์ส',
    'การชำระเงิน',
    'Room / Resource',
    'Master Data',
  ]
  results.forEach((result, index) => {
    if (result.status === 'rejected') failures.push(names[index]!)
  })
  if (branches.status === 'fulfilled')
    data.branches.splice(0, data.branches.length, ...branches.value.map(branchRow))
  if (users.status === 'fulfilled')
    data.users.splice(0, data.users.length, ...users.value.map(userRow))
  data.services.splice(
    0,
    data.services.length,
    ...(services.status === 'fulfilled' ? services.value.map(serviceRow) : []),
    ...(courses.status === 'fulfilled' ? courses.value.map(courseRow) : []),
  )
  if (payments.status === 'fulfilled')
    paymentMethods.splice(
      0,
      paymentMethods.length,
      // there is no cash payment in the system, so it is not offered
      ...payments.value
        .filter((item) => item.name !== 'Cash')
        .map((item) => ({ ...item, note: 'ช่องทางรับชำระเงิน' })),
    )
  if (resources.status === 'fulfilled')
    data.rooms.splice(0, data.rooms.length, ...resources.value.map(resourceRow))
  if (master.status === 'fulfilled')
    data.master.splice(0, data.master.length, ...master.value.map(masterRow))
  if (!(await loadCommissionRows())) failures.push('กฎค่ามือ')
  loadError.value = failures.length ? `ยังโหลดข้อมูลไม่ได้: ${failures.join(', ')}` : ''
  fillDefaultBranch()
}
// A form opened before the branches arrived starts without a branch: give it the default one as soon as it is known.
function fillDefaultBranch() {
  if (
    drawerOpen.value &&
    editingIndex.value === null &&
    (section.value === 'users' || section.value === 'rooms') &&
    !String(form.branch ?? '').trim()
  )
    form.branch = openBranches.value[0]?.name ?? ''
}
onMounted(loadBackendSettings)
const copy = computed(
  () =>
    ({
      branches: ['จัดการสาขา', 'ข้อมูลสาขาทั้งหมดในระบบ'],
      users: ['ผู้ใช้งานและสิทธิ์', 'จัดการผู้ใช้งานและสิทธิ์การเข้าถึงระบบ'],
      services: ['บริการและคอร์ส', 'จัดการรายการบริการและคอร์สทั้งหมดในระบบ'],
      payments: ['การชำระเงิน', 'เลือกวิธีการชำระเงินที่เปิดให้บริการในระบบ'],
      rooms: ['Room / Resource', 'จัดการห้องและทรัพยากรที่ใช้ในการให้บริการ'],
      commission: ['กฎค่ามือ', 'ค่ามือของผู้รักษาแทนและการรักษารายครั้ง ใช้กฎกลางเดียวกับนักกายภาพทุกคน'],
      tiers: ['กฎค่าคอมมิชชัน', 'อัตราค่าคอมมิชชันตามยอดขายคอร์สรายเดือนของพนักงาน'],
      master: ['Master Data', 'จัดการข้อมูลหลักที่ใช้อ้างอิงทั่วทั้งระบบ'],
    })[section.value],
)
const actionLabel = computed(() => {
  const labels: Record<SectionKey, string> = {
    branches: '+ เพิ่มสาขา',
    users: '+ เพิ่มผู้ใช้งาน',
    services: '+ เพิ่มรายการ',
    payments: '',
    rooms: '+ เพิ่มห้อง',
    commission: '',
    tiers: '',
    master: '+ เพิ่มข้อมูล',
  }
  return labels[section.value]
})
const headers = computed(() => {
  const values: Record<SectionKey, string[]> = {
    branches: ['รหัสสาขา', 'ชื่อสาขา', 'ข้อมูลติดต่อ', 'สถานะ'],
    users: ['ผู้ใช้งาน', 'บทบาท', 'สาขา', 'สิทธิ์', 'สถานะ'],
    services: ['รายการ', 'ประเภท', 'ราคา', 'จำนวน/ครั้ง', 'สถานะ'],
    payments: [],
    rooms: ['ห้อง/ทรัพยากร', 'สาขา', 'ประเภท', 'สถานะ'],
    commission: ['ชื่อกฎ', 'ผู้ได้รับค่ามือ', 'วิธีคำนวณ', 'สถานะ', 'ผู้บันทึกล่าสุด', 'แก้ไขล่าสุด'],
    tiers: ['ชื่อกฎ', 'ผู้ได้รับค่าคอมมิชชัน', 'วิธีคำนวณ'],
    master: ['ประเภทข้อมูล', 'จำนวนรายการ', 'ตัวอย่างรายการ', 'สถานะ'],
  }
  return values[section.value]
})
const keys = computed(() => {
  const values: Record<SectionKey, string[]> = {
    branches: ['code', 'name', 'contact', 'status'],
    users: ['name', 'role', 'branch', 'permission', 'status'],
    services: ['name', 'type', 'price', 'sessions', 'status'],
    payments: [],
    rooms: ['name', 'branch', 'type', 'status'],
    commission: ['name', 'recipient', 'calculation', 'status', 'lastEditedBy', 'lastEditedAt'],
    tiers: ['name', 'recipient', 'calculation'],
    master: ['category', 'count', 'examples', 'status'],
  }
  return values[section.value]
})
const form = reactive<Row>({})
const rolePermissions = computed(() =>
  form.role === 'Manager' ? permissions : ['Patient', 'Visit', 'Treatment'],
)
function defaults() {
  if (section.value === 'branches')
    Object.assign(form, {
      code: '',
      name: '',
      address: '',
      contact: '',
      email: '',
      licenseNo: '',
      openTime: '',
      closeTime: '',
      appointmentMinutes: '',
      status: 'ใช้งาน',
    })
  if (section.value === 'users')
    Object.assign(form, {
      name: '',
      role: 'Therapist',
      username: '',
      email: '',
      password: '',
      code: '',
      phone: '',
      branch: openBranches.value[0]?.name ?? '',
      permission: '',
      status: 'ใช้งาน',
    })
  if (section.value === 'services')
    Object.assign(form, {
      name: '',
      type: 'Single Visit',
      price: '',
      sessions: '',
      duration: '',
      validityValue: '',
      validityUnit: 'Day',
      status: 'ใช้งาน',
    })
  if (section.value === 'rooms')
    Object.assign(form, {
      name: '',
      branch: openBranches.value[0]?.name ?? '',
      type: 'Treatment Room',
      capacity: 1,
      status: 'ใช้งาน',
    })
  if (section.value === 'commission')
    Object.assign(form, {
      name: '',
      recipient: 'นักกายภาพทั้งหมด',
      feeType: 'Fixed',
      feeValue: '',
      effectiveFrom: nextCycleMonth(),
    })
  if (section.value === 'master')
    Object.assign(form, { category: 'Customer Group', count: 1, examples: '', status: 'ใช้งาน' })
}
function openDrawer(index: number | null) {
  editingIndex.value = index
  Object.keys(form).forEach((key) => delete form[key])
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
  validationMessage.value = ''
  const row =
    index === null ? undefined : data[section.value as Exclude<SectionKey, 'payments'>]?.[index]
  if (row) Object.assign(form, row)
  else defaults()
  // a rule starts on the 1st of a month: the form takes the month only
  if (section.value === 'commission') form.effectiveFrom = String(form.effectiveFrom ?? '').slice(0, 7)
  drawerOpen.value = true
}
/**
 * The page edits "the fee rule": the form opens with the values of the rule in force, and saving for a later month makes a new
 * version behind the scenes. When a version already waits to start, that version is edited instead of a new one made.
 */
function openFeeEditor() {
  const pending = feePending.value
  if (pending) {
    openDrawer(data.commission.indexOf(pending.row))
    return
  }
  openDrawer(null)
  const current = feeInForce.value?.row
  if (current) Object.assign(form, { name: current.name, feeType: current.feeType, feeValue: current.feeValue })
}
const textOrUndefined = (value: unknown) => String(value ?? '').trim() || undefined
const numberOrUndefined = (value: unknown) => {
  const text = String(value ?? '').trim()
  return text === '' ? undefined : Number(text)
}
// Appointment lengths are optional whole minutes from 5 to 480.
function invalidMinutes(value: unknown) {
  const text = String(value ?? '').trim()
  if (text === '') return false
  const minutes = Number(text)
  return !Number.isInteger(minutes) || minutes < 5 || minutes > 480
}
async function save() {
  fillDefaultBranch()
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
  validationMessage.value = ''
  const required: Record<Exclude<SectionKey, 'payments'>, string[]> = {
    branches: ['code', 'name', 'address', 'contact'],
    users: ['name', 'role', 'branch', 'username', 'code'],
    services: ['name', 'type', 'price'],
    rooms: ['name', 'branch', 'type'],
    commission: ['name', 'feeValue', 'effectiveFrom'],
    tiers: [],
    master: ['category', 'examples'],
  }
  required[section.value as Exclude<SectionKey, 'payments'>].forEach((key) => {
    fieldErrors[key] = String(form[key] ?? '').trim() === ''
  })
  if (section.value === 'branches' && !fieldErrors.contact) {
    const phone = String(form.contact).trim()
    const phoneDigits = phone.replace(/\D/g, '')
    fieldErrors.contact =
      !/^[0-9 -]+$/.test(phone) || (phoneDigits.length !== 9 && phoneDigits.length !== 10)
  }
  if (section.value === 'branches') {
    fieldErrors.code = !/^\d{2}$/.test(String(form.code))
    const email = String(form.email ?? '').trim()
    fieldErrors.email = email !== '' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
    const open = String(form.openTime ?? '').trim()
    const close = String(form.closeTime ?? '').trim()
    // the booking window needs both ends, opening before closing
    fieldErrors.openTime = (open === '') !== (close === '') || (open !== '' && open >= close)
    fieldErrors.closeTime = fieldErrors.openTime
    fieldErrors.appointmentMinutes = invalidMinutes(form.appointmentMinutes)
  }
  if (section.value === 'services') {
    fieldErrors.price =
      String(form.price ?? '').trim() === '' || !Number.isFinite(Number(form.price)) || Number(form.price) < 0
    fieldErrors.duration = invalidMinutes(form.duration)
    if (form.type === 'Course') {
      fieldErrors.sessions = !Number.isInteger(Number(form.sessions)) || Number(form.sessions) < 1
      fieldErrors.validityValue =
        !Number.isInteger(Number(form.validityValue)) || Number(form.validityValue) < 1
    }
  }
  if (section.value === 'commission') {
    const value = Number(form.feeValue)
    fieldErrors.feeValue =
      String(form.feeValue ?? '').trim() === '' ||
      !Number.isFinite(value) ||
      value < 0 ||
      (form.feeType === 'Percentage' && value > 100)
    fieldErrors.effectiveFrom = !/^[0-9]{4}-[0-9]{2}$/.test(String(form.effectiveFrom ?? ''))
  }
  if (Object.values(fieldErrors).some(Boolean)) {
    validationMessage.value = section.value === 'commission'
      ? fieldErrors.feeValue
        ? form.feeType === 'Percentage'
          ? 'เปอร์เซ็นต์ต้องอยู่ระหว่าง 0 ถึง 100'
          : 'จำนวนเงินต้องเป็นตัวเลขไม่ติดลบ'
        : 'กรุณากรอกข้อมูลให้ครบก่อนบันทึก'
      : fieldErrors.contact
      ? 'กรุณากรอกเบอร์โทรศัพท์ให้ครบ 9 หรือ 10 หลัก'
      : fieldErrors.openTime
        ? 'ช่วงเปิดรับนัดต้องกรอกทั้งเวลาเริ่มและเวลาสิ้นสุด โดยเวลาเริ่มอยู่ก่อนเวลาสิ้นสุด'
        : fieldErrors.email
          ? 'รูปแบบอีเมลสาขาไม่ถูกต้อง'
          : fieldErrors.appointmentMinutes || fieldErrors.duration
            ? 'ระยะเวลานัดต้องเป็นจำนวนเต็ม 5 ถึง 480 นาที (เว้นว่างได้)'
            : 'กรุณากรอกข้อมูลให้ครบก่อนบันทึก'
    return
  }
  if (
    section.value === 'users' &&
    editingIndex.value === null &&
    String(form.password ?? '').length < 8
  ) {
    validationMessage.value = 'ผู้ใช้ใหม่ต้องมีรหัสผ่านอย่างน้อย 8 ตัวอักษร'
    return
  }
  const rows = data[section.value as Exclude<SectionKey, 'payments'>]
  const existing = editingIndex.value === null ? undefined : rows[editingIndex.value]
  try {
    let record: Row
    if (section.value === 'branches') {
      const body = {
        branchCode: existing?.code ?? form.code,
        name: form.name,
        address: form.address,
        phone: form.contact,
        email: textOrUndefined(form.email),
        licenseNo: textOrUndefined(form.licenseNo),
        bookingOpenTime: textOrUndefined(form.openTime),
        bookingCloseTime: textOrUndefined(form.closeTime),
        defaultAppointmentMinutes: numberOrUndefined(form.appointmentMinutes),
      }
      record = branchRow(
        existing?.id ? await branchApi.update(existing.id, body) : await branchApi.create(body),
      )
      // the status chosen in the form (ใช้งาน / ปิดใช้งาน) is applied to the branch being edited; a new branch is always open
      if (existing?.id && form.status && form.status !== existing.status) {
        if (form.status === 'ใช้งาน') await branchApi.activate(record.id as number)
        else await branchApi.deactivate(record.id as number)
        record.status = String(form.status)
      }
    } else if (section.value === 'users') {
      const body = {
        username: form.username,
        email: form.email || undefined,
        ...(form.password ? { password: form.password } : {}),
        role: form.role === 'Manager' ? ('MANAGER' as const) : ('THERAPIST' as const),
        employeeCode: form.code,
        fullName: form.name,
        phone: form.phone || undefined,
        branchCode:
          data.branches.find((branch) => branch.name === form.branch)?.code ??
          openBranches.value[0]?.code,
      }
      record = userRow(
        existing?.id
          ? await userManagementApi.update(existing.id, body)
          : await userManagementApi.create({ ...body, password: form.password }),
      )
      // the status chosen in the form (ใช้งาน / ปิดใช้งาน) is applied to the user being edited; a new user is always active
      if (existing?.id && form.status && form.status !== existing.status) {
        if (form.status === 'ใช้งาน') await userManagementApi.activate(existing.id)
        else await userManagementApi.deactivate(existing.id)
        record.status = String(form.status)
      }
    } else if (section.value === 'services') {
      const code = existing?.code || form.code || `SV-${Date.now()}`
      // the type chosen in the form is applied first (the server refuses it once the item has been used)
      if (existing?.id && existing.type && form.type !== existing.type) {
        await serviceManagementApi.changeType(existing.id, form.type === 'Course' ? 'Course' : 'SingleVisit')
      }
      if (form.type === 'Course') {
        const body = {
          code,
          name: form.name,
          price: Number(form.price),
          sessions: Number(form.sessions),
          bonusSessions: 0,
          validityDays: Number(form.validityValue),
          validityValue: Number(form.validityValue),
          validityUnit: (form.validityUnit ?? 'Day') as 'Day' | 'Month' | 'Year',
          defaultDurationMinutes: numberOrUndefined(form.duration),
        }
        record = courseRow(
          existing?.id ? await courseApi.update(existing.id, body) : await courseApi.create(body),
        )
      } else {
        const body = {
          code,
          name: form.name,
          price: Number(form.price),
          defaultDurationMinutes: numberOrUndefined(form.duration),
        }
        record = serviceRow(
          existing?.id
            ? await serviceManagementApi.update(existing.id, body)
            : await serviceManagementApi.create(body),
        )
      }
      // the status chosen in the form (ใช้งาน / ปิดใช้งาน) is applied to the item being edited
      if (existing?.id && form.status && form.status !== existing.status) {
        if (form.status === 'ใช้งาน') await serviceManagementApi.activate(existing.id)
        else await serviceManagementApi.deactivate(existing.id)
        record.status = String(form.status)
      }
    } else if (section.value === 'rooms') {
      const body = {
        branchCode: data.branches.find((branch) => branch.name === form.branch)?.code,
        code: existing?.code || form.code || `RES-${Date.now()}`,
        name: form.name,
        type: form.type === 'Treatment Room' ? 'ROOM' : 'EQUIPMENT',
        // Enabled (ใช้งาน, green) or Disabled (ปิดใช้งาน, red)
        status: form.status === 'ปิดใช้งาน' ? 'INACTIVE' : 'ACTIVE',
      }
      record = resourceRow(
        existing?.id ? await resourceApi.update(existing.id, body) : await resourceApi.create(body),
      )
    } else if (section.value === 'master') {
      const body = {
        category: form.category,
        code: existing?.code || form.code || `ITEM-${Date.now()}`,
        name: String(form.examples).trim(),
      }
      record = masterRow(
        existing?.id
          ? await masterDataApi.update(existing.id, body)
          : await masterDataApi.create(body),
      )
      // the status chosen in the form (ใช้งาน / ปิดใช้งาน) is applied to the item being edited; a new item is always active
      if (existing?.id && form.status && form.status !== existing.status) {
        if (form.status === 'ใช้งาน') await masterDataApi.activate(existing.id)
        else await masterDataApi.deactivate(existing.id)
        record.status = String(form.status)
      }
    } else {
      const body = {
        name: String(form.name).trim(),
        feeType: form.feeType as 'Fixed' | 'Percentage',
        feeValue: Number(form.feeValue),
        // a rule begins on the 1st of the month picked; its end is set by the system (the day before the next rule)
        // keeping the month of a version that waits to start keeps its start date as it is
        effectiveFrom:
          existing?.effectiveFrom && String(existing.effectiveFrom).slice(0, 7) === String(form.effectiveFrom).slice(0, 7)
            ? String(existing.effectiveFrom)
            : `${String(form.effectiveFrom).slice(0, 7)}-01`,
      }
      const rule = existing?.id
        ? await commissionApi.updateTreatmentFee(existing.id, body)
        : await commissionApi.createTreatmentFee(body)
      // saving a rule can end the one that was running, so the whole list is read again
      await loadCommissionRows()
      record = feeRow(rule)
    }
    if (section.value === 'commission') {
      // the rows were reloaded above
    } else if (editingIndex.value === null) rows.push(record)
    else rows[editingIndex.value] = record
    drawerOpen.value = false
    saveModalOpen.value = true
    window.setTimeout(() => {
      saveModalOpen.value = false
    }, 1800)
  } catch (error) {
    validationMessage.value = error instanceof Error ? error.message : 'บันทึกไม่สำเร็จ'
  }
}
function notify(message: string) {
  savedMessage.value = message
  window.setTimeout(() => {
    savedMessage.value = ''
  }, 2200)
}
// Each way of paying has its own Enable / Disable status (any number can be on); every change is confirmed in a pop-up first.
const paymentLabels: Record<string, string> = {
  BankTransfer: 'โอนเงินผ่านธนาคาร',
  PromptPay: 'พร้อมเพย์',
  CreditCard: 'บัตรเครดิต/เดบิต',
}
const paymentLabel = (name: string) => paymentLabels[name] ?? name
const paymentNotes: Record<string, string> = {
  BankTransfer: 'โอนชำระภายใน 24 ชั่วโมงหลังสั่งซื้อ',
  PromptPay: 'สแกน QR Code เพื่อชำระเงินทันที',
  CreditCard: 'Visa, Mastercard',
}
const paymentNote = (name: string) => paymentNotes[name] ?? 'ช่องทางรับชำระเงิน'
const paymentChange = ref<{ index: number; enable: boolean } | null>(null)
// a status takes its colour (green Enable, red Disable) only once it has been chosen here; until then both stay plain
const paymentChosen = reactive(new Set<string>())
function askPaymentStatus(index: number, enable: boolean) {
  const method = paymentMethods[index]
  if (!method) return
  paymentChange.value = { index, enable }
}
async function confirmPaymentStatus() {
  const change = paymentChange.value
  paymentChange.value = null
  const method = change ? paymentMethods[change.index] : undefined
  if (!change || !method) return
  // choosing the status a method already has changes nothing on the server, but it is the choice: it takes its colour
  if (method.active === change.enable) {
    paymentChosen.add(method.name)
    return
  }
  try {
    if (change.enable) await paymentMethodApi.activate(method.id)
    else await paymentMethodApi.deactivate(method.id)
    method.active = change.enable
    paymentChosen.add(method.name)
    notify(`บันทึก ${paymentLabel(method.name)} อัตโนมัติแล้ว`)
  } catch (error) {
    notify(error instanceof Error ? error.message : 'เปลี่ยนสถานะไม่สำเร็จ')
  }
}
const drawerTitle = computed(() => {
  const labels: Record<SectionKey, string> = {
    branches: 'สาขา',
    users: 'ผู้ใช้และสิทธิ์',
    services: 'คอร์ส',
    payments: 'การชำระเงิน',
    rooms: 'ห้อง/ทรัพยากร',
    commission: 'กฎค่ามือ',
    tiers: 'กฎค่าคอมมิชชัน',
    master: 'Master Data',
  }
  if (section.value === 'commission')
    return editingIndex.value === null ? 'แก้กฎค่ามือ (บันทึกเป็นเวอร์ชันใหม่)' : 'แก้ไขเวอร์ชันที่รอเริ่มใช้'
  return `${editingIndex.value === null ? 'เพิ่ม' : 'แก้ไข'} ${labels[section.value]}`
})
</script>

<template>
  <div class="settings-page">
    <StaffHeader />
    <main class="settings-main">
      <header class="settings-title">
        <h1>การตั้งค่า</h1>
        <p>จัดการข้อมูลสาขา ผู้ใช้งาน สิทธิ์การเข้าถึง และการตั้งค่าระบบ</p>
      </header>
      <div class="settings-layout">
        <aside class="settings-nav">
          <h2>หมวดการตั้งค่า</h2>
          <button
            v-for="item in sections"
            :key="item.key"
            :class="{ active: section === item.key }"
            @click="section = item.key"
          >
            {{ item.label }}
          </button>
          <RouterLink class="settings-nav-link" to="/staff/settings/audit-log">ประวัติการแก้ไขระบบ</RouterLink>
        </aside>
        <section class="settings-panel">
          <header class="settings-panel-head">
            <div>
              <h2>{{ copy[0] }}</h2>
              <p>{{ copy[1] }}</p>
            </div>
            <button v-if="actionLabel" class="settings-add" @click="openDrawer(null)">
              {{ actionLabel }}
            </button>
            <div v-else-if="section === 'commission'" class="settings-actions">
              <RouterLink class="settings-secondary" to="/staff/settings/fee-history">ประวัติเวอร์ชัน</RouterLink
              ><button class="settings-add" @click="openFeeEditor">แก้กฎค่ามือ</button>
            </div>
            <div v-else-if="section === 'tiers'" class="settings-actions">
              <RouterLink class="settings-secondary" to="/staff/settings/commission-history"
                >ประวัติเวอร์ชัน</RouterLink
              ><RouterLink class="settings-add" to="/staff/commission/tiers">ตั้งค่า Tier</RouterLink>
            </div>
            <p v-else-if="section === 'payments'" class="auto-save-note">
              {{ savedMessage || 'การเปลี่ยนแปลงจะบันทึกอัตโนมัติ' }}
            </p>
          </header>
          <p v-if="loadError" class="validation-message" role="alert">{{ loadError }}</p>
          <div v-if="section === 'payments'" class="payment-list">
            <div
              v-for="(method, index) in paymentMethods"
              :key="method.name"
              class="payment-option"
            >
              <span
                ><b>{{ paymentLabel(method.name) }}</b
                ><small>{{ paymentNote(method.name) }} · {{ method.active ? 'เปิดใช้งานอยู่' : 'ปิดอยู่' }}</small></span
              >
              <span class="payment-status" role="group" :aria-label="`สถานะ ${paymentLabel(method.name)}`">
                <button
                  type="button"
                  class="enable"
                  :class="{ on: paymentChosen.has(method.name) && method.active }"
                  :aria-pressed="method.active"
                  @click="askPaymentStatus(index, true)"
                >
                  Enable
                </button>
                <button
                  type="button"
                  class="disable"
                  :class="{ on: paymentChosen.has(method.name) && !method.active }"
                  :aria-pressed="!method.active"
                  @click="askPaymentStatus(index, false)"
                >
                  Disable
                </button>
              </span>
            </div>
          </div>
          <section v-else-if="section === 'tiers'" class="latest-version">
            <p v-if="!latestVersion" class="empty">ยังไม่มีเวอร์ชันของกฎค่าคอมมิชชันที่ใช้อยู่ในตอนนี้</p>
            <template v-else>
              <header>
                <h3>รายละเอียดเวอร์ชัน V{{ latestVersion.version }}</h3>
                <span class="version-badge" :class="versionStatus(latestVersion).tone">{{
                  versionStatus(latestVersion).label
                }}</span>
              </header>
              <p class="cycle">{{ versionCycles(latestVersion) }}</p>
              <div class="table-wrap">
                <table class="tier-table">
                  <thead>
                    <tr>
                      <th>Tier</th>
                      <th>ยอดขายขั้นต่ำ (บาท)</th>
                      <th>ยอดขายสูงสุด (บาท)</th>
                      <th>คอมมิชชัน</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="tier in latestTiers" :key="tier.order">
                      <td>{{ tier.order }}</td>
                      <td>{{ baht2(tier.minimum) }}</td>
                      <td>{{ tier.maximum === null ? 'ไม่จำกัด' : baht2(tier.maximum) }}</td>
                      <td>{{ Math.round(tier.rate * 10000) / 100 }}%</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </template>
          </section>
          <section v-else-if="section === 'commission'" class="latest-version fee-versions">
            <p v-if="!feeInForce" class="empty">ยังไม่มีกฎค่ามือที่ใช้อยู่ในตอนนี้ การรักษาแทนจะบันทึกไม่ได้จนกว่ากฎจะเริ่มมีผล</p>
            <article v-for="item in shownFeeVersions" :key="item.row.id" class="fee-version">
              <header>
                <h3>
                  {{ item.status.tone === 'pending' ? 'เวอร์ชันที่รอเริ่มใช้' : 'รายละเอียดเวอร์ชัน' }} V{{ item.number }}
                </h3>
                <span class="version-badge" :class="item.status.tone">{{ item.status.label }}</span>
              </header>
              <p class="cycle">{{ versionCycles(item.range) }}</p>
              <div class="table-wrap">
                <table class="tier-table fee-table">
                  <thead>
                    <tr>
                      <th>ชื่อกฎ</th>
                      <th>ผู้ได้รับค่ามือ</th>
                      <th>วิธีคำนวณ</th>
                      <th>ผู้บันทึกล่าสุด</th>
                      <th>แก้ไขล่าสุด</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>{{ item.row.name }}</td>
                      <td>{{ item.row.recipient }}</td>
                      <td>{{ item.row.calculation }}</td>
                      <td>{{ item.row.lastEditedBy }}</td>
                      <td>{{ item.row.lastEditedAt }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </article>
          </section>
          <div v-else class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th v-for="head in headers" :key="head">{{ head }}</th>
                  <th>การจัดการ</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(row, index) in data[section as Exclude<SectionKey, 'payments'>]"
                  :key="index"
                >
                  <td v-for="key in keys" :key="key">
                    <span
                      v-if="key === 'status'"
                      class="settings-status"
                      :class="{
                        'status-off':
                          String(row[key]).startsWith('ปิด') || String(row[key]).startsWith('ไม่พร้อม'),
                      }"
                      >{{ row[key] }}</span
                    ><template v-else>{{ row[key] }}</template>
                  </td>
                  <td>
                    <button class="edit" @click="openDrawer(index)">แก้ไข</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </main>
    <p v-if="savedMessage && section !== 'payments'" class="toast" role="status">
      {{ savedMessage }}
    </p>
    <div v-if="paymentChange" class="confirm-backdrop" @click.self="paymentChange = null">
      <div class="confirm-modal" role="dialog" aria-modal="true" aria-label="ยืนยันการเปลี่ยนสถานะ">
        <h3>ยืนยันการเปลี่ยนสถานะ</h3>
        <p>
          ต้องการ{{ paymentChange.enable ? 'เปิดใช้งาน (Enable)' : 'ปิดใช้งาน (Disable)' }}
          <b>{{ paymentLabel(paymentMethods[paymentChange.index]!.name) }}</b> หรือไม่?
        </p>
                <div class="confirm-actions">
          <button type="button" class="confirm-cancel" @click="paymentChange = null">ยกเลิก</button>
          <button type="button" class="confirm-ok" @click="confirmPaymentStatus">ยืนยัน</button>
        </div>
      </div>
    </div>
    <div v-if="saveModalOpen" class="save-success-backdrop" @click="saveModalOpen = false">
      <div class="save-success-modal" role="status">บันทึกเรียบร้อยแล้ว</div>
    </div>
    <div v-if="drawerOpen" class="drawer-backdrop" @click.self="drawerOpen = false">
      <aside class="drawer">
        <header>
          <h2>{{ drawerTitle }}</h2>
          <button aria-label="ปิด" @click="drawerOpen = false">×</button>
        </header>
        <template v-if="section === 'branches'"
          ><label
            >รหัสสาขา<input
              v-model="form.code"
              :class="{ invalid: fieldErrors.code }"
              :disabled="editingIndex !== null"
              maxlength="2"
              placeholder="เช่น 01" /></label
          ><label
            >ชื่อสาขา<input
              v-model="form.name"
              :class="{ invalid: fieldErrors.name }"
              placeholder="ระบุชื่อสาขา" /></label
          ><label
            >ที่อยู่<input
              v-model="form.address"
              :class="{ invalid: fieldErrors.address }"
              placeholder="ที่อยู่สาขา" /></label
          ><label
            >ข้อมูลติดต่อ<input
              v-model="form.contact"
              type="tel"
              inputmode="numeric"
              maxlength="12"
              :class="{ invalid: fieldErrors.contact }"
              placeholder="เบอร์โทรศัพท์สาขา" /></label
          ><label
            >อีเมลสาขา<input
              v-model="form.email"
              type="email"
              :class="{ invalid: fieldErrors.email }"
              placeholder="ไม่บังคับ" /></label
          ><label
            >เลขใบอนุญาต<input v-model="form.licenseNo" maxlength="50" placeholder="ไม่บังคับ" /></label
          ><label
            >เปิดรับนัดตั้งแต่<input
              v-model="form.openTime"
              type="time"
              :class="{ invalid: fieldErrors.openTime }" /></label
          ><label
            >ถึงเวลา<input
              v-model="form.closeTime"
              type="time"
              :class="{ invalid: fieldErrors.closeTime }" /></label
          ><label
            >ระยะเวลานัดเริ่มต้น (นาที)<input
              v-model="form.appointmentMinutes"
              inputmode="numeric"
              :class="{ invalid: fieldErrors.appointmentMinutes }"
              placeholder="ไม่บังคับ เช่น 60" /></label
          ><label
            v-if="editingIndex !== null"
            class="status-field"
            :class="form.status === 'ปิดใช้งาน' ? 'status-off' : 'status-on'"
            >สถานะ<FilterDropdown
              v-model="form.status"
              :options="statusOptions"
              :default-value="String(form.status ?? '')"
              :default-label="String(form.status ?? 'เลือกสถานะ')"
              label="สถานะ"
              hide-default-option
              :clear-on-reselect="false" /></label
        ></template>
        <template v-else-if="section === 'users'"
          ><label
            >ชื่อผู้ใช้งาน<input
              v-model="form.name"
              :class="{ invalid: fieldErrors.name }"
              placeholder="ระบุชื่อผู้ใช้งาน" /></label
          ><label
            >Username<input
              v-model="form.username"
              :class="{ invalid: fieldErrors.username }" /></label
          ><label>อีเมล<input v-model="form.email" type="email" /></label
          ><label
            >รหัสผ่าน<input
              v-model="form.password"
              type="password"
              autocomplete="new-password"
              placeholder="เว้นว่างได้เมื่อแก้ไข" /></label
          ><label
            >รหัสพนักงาน<input v-model="form.code" :class="{ invalid: fieldErrors.code }" /></label
          ><label>โทรศัพท์<input v-model="form.phone" type="tel" /></label
          ><label :class="{ 'invalid-field': fieldErrors.role }"
            >Role<FilterDropdown
              v-model="form.role"
              :options="[option('Manager'), option('Therapist')]"
              default-value="Therapist"
              default-label="Therapist"
              label="Role"
              hide-default-option
              :clear-on-reselect="false" /></label
          ><label :class="{ 'invalid-field': fieldErrors.branch }"
            >สาขาที่เข้า<FilterDropdown
              v-model="form.branch"
              :options="[option('ทุกสาขา'), ...branchOptions]"
              :default-value="openBranches[0]?.name ?? ''"
              :default-label="openBranches[0]?.name ?? 'เลือกสาขา'"
              label="สาขาที่เข้า"
              hide-default-option
              :clear-on-reselect="false"
          /></label>
          <fieldset disabled>
            <legend>สิทธิ์ตามบทบาท</legend>
            <label v-for="permission in permissions" :key="permission" class="check"
              ><input
                :checked="rolePermissions.includes(permission)"
                type="checkbox"
                :value="permission"
              />{{ permission }}</label
            >
          </fieldset>
          <label
            v-if="editingIndex !== null"
            class="status-field"
            :class="form.status === 'ปิดใช้งาน' ? 'status-off' : 'status-on'"
            >สถานะ<FilterDropdown
              v-model="form.status"
              :options="statusOptions"
              :default-value="String(form.status ?? '')"
              :default-label="String(form.status ?? 'เลือกสถานะ')"
              label="สถานะ"
              hide-default-option
              :clear-on-reselect="false" /></label
        ></template>
        <template v-else-if="section === 'services'"
          ><label
            >ชื่อคอร์ส<input
              v-model="form.name"
              :class="{ invalid: fieldErrors.name }"
              placeholder="ระบุชื่อคอร์สหรือบริการ" /></label
          ><label :class="{ 'invalid-field': fieldErrors.type }"
            >ประเภท<FilterDropdown
              v-model="form.type"
              :options="[option('Single Visit'), option('Course')]"
              default-value="Single Visit"
              default-label="Single Visit"
              label="ประเภท"
              hide-default-option
              :clear-on-reselect="false" /></label
          ><label
            >ราคา (บาท)<input
              v-model="form.price"
              :class="{ invalid: fieldErrors.price }"
              placeholder="ระบุราคา" /></label
          ><template v-if="form.type === 'Course'"
            ><label
              >จำนวนครั้งรวม<input
                v-model="form.sessions"
                :class="{ invalid: fieldErrors.sessions }"
                placeholder="ระบุจำนวนครั้งรวม" /></label
            ><label
              >อายุคอร์ส<input
                v-model="form.validityValue"
                type="number"
                min="1"
                :class="{ invalid: fieldErrors.validityValue }" /></label
            ><label
              >หน่วย<select v-model="form.validityUnit" aria-label="หน่วยอายุคอร์ส">
                <option value="Day">วัน</option>
                <option value="Month">เดือน</option>
                <option value="Year">ปี</option>
              </select></label
            ></template
          ><p v-else class="hint">Single Visit คือ 1 ครั้งต่อรายการ และไม่มีวันหมดอายุ</p
          ><label
            >ระยะเวลานัดเริ่มต้น (นาที)<input
              v-model="form.duration"
              inputmode="numeric"
              :class="{ invalid: fieldErrors.duration }"
              placeholder="ไม่บังคับ เช่น 60" /></label
          ><label
            v-if="editingIndex !== null"
            class="status-field"
            :class="form.status === 'ปิดใช้งาน' ? 'status-off' : 'status-on'"
            >สถานะ<FilterDropdown
              v-model="form.status"
              :options="statusOptions"
              :default-value="String(form.status ?? '')"
              :default-label="String(form.status ?? 'เลือกสถานะ')"
              label="สถานะ"
              hide-default-option
              :clear-on-reselect="false" /></label
        ></template>
        <template v-else-if="section === 'rooms'"
          ><label
            >ชื่อห้อง/ทรัพยากร<input
              v-model="form.name"
              :class="{ invalid: fieldErrors.name }"
              placeholder="ระบุชื่อห้อง เช่น ห้องรักษา 3" /></label
          ><label :class="{ 'invalid-field': fieldErrors.branch }"
            >สาขา<FilterDropdown
              v-model="form.branch"
              :options="branchOptions"
              :default-value="openBranches[0]?.name ?? ''"
              :default-label="openBranches[0]?.name ?? 'เลือกสาขา'"
              label="สาขา"
              hide-default-option
              :clear-on-reselect="false" /></label
          ><label :class="{ 'invalid-field': fieldErrors.type }"
            >ประเภท<FilterDropdown
              v-model="form.type"
              :options="[option('Treatment Room'), option('Resource')]"
              default-value="Treatment Room"
              default-label="Treatment Room"
              label="ประเภท"
              hide-default-option
              :clear-on-reselect="false" /></label
        ></template>
        <template v-else-if="section === 'commission'"
          ><label
            >ชื่อกฎค่ามือ<input
              v-model="form.name"
              maxlength="100"
              :class="{ invalid: fieldErrors.name }"
              placeholder="ระบุชื่อเกณฑ์" /></label
          ><label
            >ผู้ได้รับค่ามือ<input class="locked-field" value="นักกายภาพทั้งหมด" disabled readonly aria-label="ผู้ได้รับค่ามือ" /></label
          ><fieldset class="fee-type" :disabled="form.used">
            <legend>วิธีคำนวณ</legend>
            <label class="check"
              ><input v-model="form.feeType" type="radio" value="Fixed" />จำนวนเงิน (บาท / ครั้ง)</label
            >
            <label class="check"
              ><input v-model="form.feeType" type="radio" value="Percentage" />เปอร์เซ็นต์ (%)</label
            >
          </fieldset>
          <label
            >{{ form.feeType === 'Percentage' ? 'อัตรา (%)' : 'จำนวนเงินต่อครั้ง (บาท)'
            }}<input
              v-model="form.feeValue"
              type="number"
              min="0"
              step="0.01"
              :disabled="form.used"
              :class="{ invalid: fieldErrors.feeValue }"
              :placeholder="
                form.feeType === 'Percentage' ? 'เช่น 10' : 'จำนวนเงินต่อครั้ง เช่น 300 บาท / ครั้ง'
              " /></label
          ><label
            >เดือนที่เริ่มมีผล (เริ่มวันที่ 1 · วันสิ้นสุดระบบกำหนดให้)<input
              v-model="form.effectiveFrom"
              type="month"
              :min="editingIndex === null ? nextCycleMonth() : undefined"
              :disabled="form.used"
              :class="{ invalid: fieldErrors.effectiveFrom }" /></label
          ><p v-if="form.used" class="hint">
            กฎนี้ถูกใช้คำนวณแล้ว แก้ได้เฉพาะชื่อ ถ้าต้องการเปลี่ยนอัตราให้แก้กฎค่ามือเพื่อสร้างเวอร์ชันใหม่
          </p></template
        >
        <template v-else-if="section === 'master'"
          ><label :class="{ 'invalid-field': fieldErrors.category }"
            >ประเภทข้อมูล<FilterDropdown
              v-model="form.category"
              :options="[
                option('Customer Group'),
                option('Referral Channel'),
                option('Insurance Company'),
              ]"
              default-value="Customer Group"
              default-label="Customer Group"
              label="ประเภทข้อมูล"
              hide-default-option
              :clear-on-reselect="false" /></label
          ><label
            >ตัวอย่างรายการ<input
              v-model="form.examples"
              :class="{ invalid: fieldErrors.examples }"
              placeholder="ระบุชื่อรายการ" /></label
          ><label
            v-if="editingIndex !== null"
            class="status-field"
            :class="form.status === 'ปิดใช้งาน' ? 'status-off' : 'status-on'"
            >สถานะ<FilterDropdown
              v-model="form.status"
              :options="statusOptions"
              :default-value="String(form.status ?? '')"
              :default-label="String(form.status ?? 'เลือกสถานะ')"
              label="สถานะ"
              hide-default-option
              :clear-on-reselect="false" /></label
        ></template>
        <label
          v-if="section === 'rooms'"
          class="status-field"
          :class="form.status === 'ปิดใช้งาน' ? 'status-off' : 'status-on'"
          >สถานะ<FilterDropdown
            v-model="form.status"
            :options="statusOptions"
            :default-value="String(form.status ?? '')"
            :default-label="String(form.status ?? 'เลือกสถานะ')"
            label="สถานะ"
            hide-default-option
            :clear-on-reselect="false"
        /></label>
        <p v-if="validationMessage" class="validation-message">{{ validationMessage }}</p>
        <button class="save" @click="save">บันทึก</button>
      </aside>
    </div>
  </div>
</template>
<style scoped src="../styles/back-office.css"></style>
