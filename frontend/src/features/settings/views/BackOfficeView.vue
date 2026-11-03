<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import MonthFilterPicker from '@/features/staff/components/MonthFilterPicker.vue'
import TimeFilterPicker from '@/features/staff/components/TimeFilterPicker.vue'
import MasterDataGroups from '../components/MasterDataGroups.vue'
import SettingsNavIcon from '../components/SettingsNavIcon.vue'
import TreatmentFeeRuleDrawer from '../components/TreatmentFeeRuleDrawer.vue'
import {
  type TreatmentFeeRule,
  type PermissionCatalogItem,
  type UserPermissionOverride,
  ApiError,
  branchApi,
  commissionApi,
  courseApi,
  paymentMethodApi,
  paymentStatusApi,
  resourceApi,
  masterDataApi,
  serviceManagementApi,
  userManagementApi,
} from '@/api/client'
import { clinicToday } from '@/utils/clinicDate'
import { isBranchPhoneValid, isOptionalPhoneValid, limitPhoneDigits } from '@/utils/phone'
import { baht2, recordedAt, versionCycles, versionStatus } from '../commissionVersion'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'

type SectionKey =
  'branches' | 'users' | 'services' | 'payments' | 'rooms' | 'commission' | 'tiers' | 'master'
type SettingsNavIconName = Exclude<
  InstanceType<typeof SettingsNavIcon>['$props']['name'],
  'calendar' | 'settings'
>
// ฟอร์มหลายประเภทใช้แถวข้อมูลร่วมกัน จึงตรวจชนิดค่าก่อนบันทึก
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Row = Record<string, any>
const sections: Array<{ key: SectionKey; label: string; hint: string; icon: SettingsNavIconName }> =
  [
    { key: 'branches', label: 'สาขา', hint: 'ข้อมูลและเวลาทำการ', icon: 'branch' },
    { key: 'users', label: 'ผู้ใช้และสิทธิ์', hint: 'บัญชีและการเข้าถึง', icon: 'users' },
    { key: 'services', label: 'บริการและคอร์ส', hint: 'รายการและราคา', icon: 'services' },
    { key: 'payments', label: 'การชำระเงิน', hint: 'ช่องทางและสถานะ', icon: 'payments' },
    { key: 'rooms', label: 'Room / Resource', hint: 'ห้องและทรัพยากร', icon: 'rooms' },
    { key: 'commission', label: 'กฎค่ามือ', hint: 'ค่ามือการรักษา', icon: 'commission' },
    { key: 'tiers', label: 'กฎค่าคอมมิชชัน', hint: 'อัตราตามยอดขาย', icon: 'tiers' },
    { key: 'master', label: 'Master Data', hint: 'ข้อมูลพื้นฐานระบบ', icon: 'master' },
  ]
// เปิดหมวดที่ส่งมาจาก URL เช่นหน้าประวัติ Scheme
function initialSection(): SectionKey {
  const wanted = new URLSearchParams(window.location.search).get('section')
  return sections.find((item) => item.key === wanted)?.key ?? 'branches'
}
const section = ref<SectionKey>(initialSection()),
  drawerOpen = ref(false),
  editingIndex = ref<number | null>(null),
  savedMessage = ref(''),
  saveModalOpen = ref(false)
const fieldErrors = reactive<Record<string, boolean>>({})
const validationMessage = ref('')
const loadError = ref('')
const permissionOverrides = ref<UserPermissionOverride[]>([])
const permissionCatalog = ref<PermissionCatalogItem[]>([])
const permissionOverridesLoading = ref(false)
const permissionOverridesError = ref('')
const selectedPermissionId = ref('')
const newOverrideAllowed = ref(true)
const availablePermissions = computed(() => {
  const existing = new Set(permissionOverrides.value.map((override) => override.permissionId))
  return permissionCatalog.value.filter((permission) => !existing.has(permission.id))
})
const option = (value: string) => ({ value, label: value })
const serviceTypeOptions = [
  { value: 'Single Visit', label: 'บริการรายครั้ง' },
  { value: 'Course', label: 'คอร์ส' },
]
const userRoleOptions = [
  { value: 'Therapist', label: 'นักกายภาพ' },
  { value: 'Manager', label: 'ผู้จัดการ' },
]
const serviceTypeLabel = (value: unknown) =>
  value === 'Single Visit'
    ? 'บริการรายครั้ง'
    : value === 'Course'
      ? 'คอร์ส'
      : value === 'Treatment Room'
        ? 'ห้องรักษา'
        : String(value ?? '-')
const roleLabel = (value: unknown) =>
  value === 'Therapist' ? 'นักกายภาพ' : value === 'Manager' ? 'ผู้จัดการ' : String(value ?? '-')
const permissionList = (value: unknown) =>
  String(value ?? '')
    .split(/[\s,]+/)
    .filter(Boolean)
    .map((permission) => ({ key: permission, label: permission }))
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
const rowsPerPage = 10
const currentPage = ref(1)
const userRoleFilter = ref('ทั้งหมด')
const userStatusFilter = ref('ทั้งหมด')
const serviceSearch = ref('')
const serviceTypeFilter = ref('ทั้งหมด')
const paymentMethods = reactive<Array<{ id: number; name: string; note: string; active: boolean }>>(
  [],
)
const paymentStatuses = reactive<
  Array<{ code: 'INSTALLMENT' | 'DEPOSIT' | 'PAID' | 'FULL'; label: string; enabled: boolean }>
>([])
// ไม่แสดงสาขาที่ปิดใช้งานให้เลือกกับผู้ใช้ ห้อง หรือทรัพยากร
const openBranches = computed(() => data.branches.filter((branch) => branch.status === 'ใช้งาน'))
const branchOptions = computed(() =>
  openBranches.value.map((branch) => option(String(branch.name))),
)
const selectedBranchName = computed(() => selectedManagerBranch()?.name ?? '')
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
  contact: limitPhoneDigits(item.phone ?? ''),
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
  branch: item.branchName ?? 'ยังไม่กำหนดสาขา',
  permission: 'ตามบทบาทหลัก',
  status: status(item.active),
})
const serviceRow = (item: Awaited<ReturnType<typeof serviceManagementApi.create>>) => ({
  ...item,
  type: 'Single Visit',
  // บริการรายครั้งมีจำนวน 1 ครั้ง ส่วน duration คือระยะเวลานัดที่เลือกได้
  sessions: '1',
  duration: item.defaultDurationMinutes ? String(item.defaultDurationMinutes) : '',
  status: status(item.active),
})
const courseRow = (item: Awaited<ReturnType<typeof courseApi.create>>) => ({
  ...item,
  type: 'Course',
  // จำนวนครั้งรวมรวมโบนัสเดิมไว้แล้ว จึงไม่บวกโบนัสแยกอีก
  sessions: String(item.sessions),
  duration: item.defaultDurationMinutes ? String(item.defaultDurationMinutes) : '',
  validityValue: item.validityValue ?? item.validityDays,
  validityUnit: item.validityUnit ?? 'Day',
  status: status(Boolean(item.active)),
})
const resourceRow = (item: Awaited<ReturnType<typeof resourceApi.create>>) => ({
  ...item,
  branch: item.branchName,
  type: 'Treatment Room',
  status: resourceStatuses[item.status] ?? item.status,
})
const masterRow = (item: Awaited<ReturnType<typeof masterDataApi.create>>) => ({
  ...item,
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
// ส่วนนี้แสดงกฎค่ามือและ Scheme Version ที่มีผลในปัจจุบัน
type LatestVersion = {
  id: number
  version: number
  effectiveFrom: string
  effectiveTo: string | null
}
type LatestTier = { order: number; minimum: number; maximum: number | null; rate: number }
const latestVersion = ref<LatestVersion | null>(null)
const latestTiers = ref<LatestTier[]>([])
async function loadCommissionRows() {
  try {
    const rules = (await commissionApi.treatmentFees()) as TreatmentFeeRule[]
    const rows: Row[] = rules.map(feeRow)
    try {
      // เลือก Scheme ที่มีผลตามวันที่ปัจจุบัน
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
      // ถ้าโหลดรายละเอียด Scheme ไม่ได้ ยังแสดงกฎค่ามือได้
    }
    data.commission.splice(0, data.commission.length, ...rows)
    return true
  } catch {
    return false
  }
}
// เรียงกฎค่ามือตามวันเริ่มใช้เพื่อแสดงกฎปัจจุบันและกฎที่รอเริ่ม
const feeVersions = computed(() =>
  data.commission
    .slice()
    .sort((a, b) => String(a.effectiveFrom).localeCompare(String(b.effectiveFrom)))
    .map((row, index) => {
      const range = {
        effectiveFrom: String(row.effectiveFrom),
        effectiveTo: row.effectiveTo ? String(row.effectiveTo) : null,
      }
      return { number: index + 1, row, range, status: versionStatus(range) }
    }),
)
const feeInForce = computed(
  () => feeVersions.value.find((item) => item.status.tone === 'current') ?? null,
)
const feePending = computed(
  () => feeVersions.value.find((item) => item.status.tone === 'pending') ?? null,
)
const shownFeeVersions = computed(() =>
  [feeInForce.value, feePending.value].filter(
    (item): item is NonNullable<typeof item> => item !== null,
  ),
)
/** คืนเดือนเริ่มใช้รอบถัดไปในรูปแบบ YYYY-MM */
function nextCycleMonth() {
  const [year, month] = clinicToday().split('-').map(Number)
  return new Date(Date.UTC(year!, month!, 1)).toISOString().slice(0, 7)
}
async function loadBackendSettings() {
  const [branches, users, services, courses, payments, paymentStatusOptions, resources, master] =
    await Promise.allSettled([
      branchApi.list(),
      userManagementApi.list(),
      serviceManagementApi.list(),
      courseApi.list(),
      paymentMethodApi.list(),
      paymentStatusApi.list(),
      resourceApi.list(),
      masterDataApi.list(),
    ])
  const failures: string[] = []
  const results = [
    branches,
    users,
    services,
    courses,
    payments,
    paymentStatusOptions,
    resources,
    master,
  ]
  const names = [
    'สาขา',
    'ผู้ใช้งาน',
    'บริการ',
    'คอร์ส',
    'การชำระเงิน',
    'สถานะการชำระเงิน',
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
      // ระบบไม่รองรับเงินสด จึงไม่นำมาเป็นตัวเลือก
      ...payments.value
        .filter((item) => item.name !== 'Cash')
        .map((item) => ({ ...item, note: 'ช่องทางรับชำระเงิน' })),
    )
  if (paymentStatusOptions.status === 'fulfilled')
    paymentStatuses.splice(0, paymentStatuses.length, ...paymentStatusOptions.value)
  if (resources.status === 'fulfilled')
    data.rooms.splice(0, data.rooms.length, ...resources.value.map(resourceRow))
  if (master.status === 'fulfilled')
    data.master.splice(0, data.master.length, ...master.value.map(masterRow))
  if (!(await loadCommissionRows())) failures.push('กฎค่ามือ')
  loadError.value = failures.length ? `ยังโหลดข้อมูลไม่ได้: ${failures.join(', ')}` : ''
  fillDefaultBranch()
}
// เติมสาขาเริ่มต้นให้ฟอร์มที่เปิดก่อนข้อมูลสาขาโหลดเสร็จ
function fillDefaultBranch() {
  if (
    drawerOpen.value &&
    editingIndex.value === null &&
    (section.value === 'users' || section.value === 'rooms') &&
    !String(form.branch ?? '').trim()
  )
    form.branch = selectedBranchName.value || openBranches.value[0]?.name || ''
}
onMounted(loadBackendSettings)
const copy = computed(
  () =>
    ({
      branches: ['จัดการสาขา', 'ข้อมูลสาขาทั้งหมดในระบบ'],
      users: ['ผู้ใช้งานและสิทธิ์', 'จัดการผู้ใช้งานและสิทธิ์การเข้าถึงระบบ'],
      services: ['บริการและคอร์ส', 'จัดการรายการบริการและคอร์สทั้งหมดในระบบ'],
      payments: ['การชำระเงิน', 'เลือกช่องทางและสถานะการชำระเงินที่เปิดให้บริการในระบบ'],
      rooms: ['Room / Resource', 'จัดการห้องและทรัพยากรที่ใช้ในการให้บริการ'],
      commission: [
        'กฎค่ามือ',
        'ค่ามือของผู้รักษาแทนและการรักษารายครั้ง ใช้กฎกลางเดียวกับนักกายภาพทุกคน',
      ],
      tiers: ['กฎค่าคอมมิชชัน', 'อัตราค่าคอมมิชชันตามยอดขายคอร์สรายเดือนของพนักงาน'],
      master: ['Master Data', 'จัดการข้อมูลลูกค้า'],
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
    rooms: ['ห้องรักษา', 'สาขา', 'สถานะ'],
    commission: [
      'ชื่อกฎ',
      'ผู้ได้รับค่ามือ',
      'วิธีคำนวณ',
      'สถานะ',
      'ผู้บันทึกล่าสุด',
      'แก้ไขล่าสุด',
    ],
    tiers: ['ชื่อกฎ', 'ผู้ได้รับค่าคอมมิชชัน', 'วิธีคำนวณ'],
    master: ['ประเภทข้อมูล', 'ชื่อรายการ', 'สถานะ'],
  }
  return values[section.value]
})
const keys = computed(() => {
  const values: Record<SectionKey, string[]> = {
    branches: ['code', 'name', 'contact', 'status'],
    users: ['name', 'role', 'branch', 'permission', 'status'],
    services: ['name', 'type', 'price', 'sessions', 'status'],
    payments: [],
    rooms: ['name', 'branch', 'status'],
    commission: ['name', 'recipient', 'calculation', 'status', 'lastEditedBy', 'lastEditedAt'],
    tiers: ['name', 'recipient', 'calculation'],
    master: ['category', 'examples', 'status'],
  }
  return values[section.value]
})
const filteredTableRows = computed(() => {
  if (section.value === 'payments' || section.value === 'commission' || section.value === 'tiers')
    return [] as Array<{ row: Row; index: number }>

  let rows = data[section.value].map((row, index) => ({ row, index }))
  if (section.value === 'rooms') {
    const branchName = selectedBranchName.value
    if (!branchName) return []
    rows = rows.filter(({ row }) => String(row.branch) === branchName)
  }

  if (section.value === 'users') {
    const branchName = selectedBranchName.value
    if (!branchName) return []
    rows = rows.filter(({ row }) => String(row.branch) === branchName)
    if (userRoleFilter.value !== 'ทั้งหมด')
      rows = rows.filter(({ row }) => String(row.role) === userRoleFilter.value)
    if (userStatusFilter.value !== 'ทั้งหมด')
      rows = rows.filter(({ row }) => String(row.status) === userStatusFilter.value)
  }

  if (section.value === 'services') {
    const query = serviceSearch.value.trim().toLocaleLowerCase('th-TH')
    if (query)
      rows = rows.filter(({ row }) =>
        [row.name, serviceTypeLabel(row.type)].some((value) =>
          String(value ?? '')
            .toLocaleLowerCase('th-TH')
            .includes(query),
        ),
      )
    if (serviceTypeFilter.value !== 'ทั้งหมด')
      rows = rows.filter(({ row }) => String(row.type) === serviceTypeFilter.value)
  }
  return rows
})
const pageCount = computed(() =>
  Math.max(1, Math.ceil(filteredTableRows.value.length / rowsPerPage)),
)
const pagedTableRows = computed(() => {
  const start = (currentPage.value - 1) * rowsPerPage
  return filteredTableRows.value.slice(start, start + rowsPerPage)
})
const tableRange = computed(() => {
  const total = filteredTableRows.value.length
  if (!total) return 'ไม่พบรายการ'
  const start = (currentPage.value - 1) * rowsPerPage + 1
  return `แสดง ${start}-${Math.min(start + rowsPerPage - 1, total)} จาก ${total} รายการ`
})
watch(
  [
    section,
    () => selectedManagerBranch()?.code ?? '',
    userRoleFilter,
    userStatusFilter,
    serviceSearch,
    serviceTypeFilter,
  ],
  () => {
    currentPage.value = 1
  },
)
watch(pageCount, (count) => {
  if (currentPage.value > count) currentPage.value = count
})
const form = reactive<Row>({})
async function loadPermissionOverrides(userId: number) {
  permissionOverridesLoading.value = true
  permissionOverridesError.value = ''
  try {
    // โหลด override ของผู้ใช้พร้อม catalog ที่มี permission ID จริงจาก backend
    const [overrides, catalog] = await Promise.all([
      userManagementApi.permissionOverrides(userId),
      userManagementApi.permissionCatalog(),
    ])
    permissionOverrides.value = overrides
    permissionCatalog.value = catalog
    if (!availablePermissions.value.some((permission) => String(permission.id) === selectedPermissionId.value))
      selectedPermissionId.value = String(availablePermissions.value[0]?.id ?? '')
  } catch (error) {
    permissionOverrides.value = []
    permissionCatalog.value = []
    permissionOverridesError.value = error instanceof Error ? error.message : 'โหลดสิทธิ์เฉพาะผู้ใช้ไม่สำเร็จ'
  } finally {
    permissionOverridesLoading.value = false
  }
}
async function addPermissionOverride() {
  const userId = Number(form.id)
  const permissionId = Number(selectedPermissionId.value)
  if (!Number.isSafeInteger(userId) || userId <= 0 || !Number.isSafeInteger(permissionId) || permissionId <= 0) return
  permissionOverridesError.value = ''
  try {
    // เพิ่ม override ด้วย ID จาก catalog แล้วโหลดผลล่าสุดกลับมา
    await userManagementApi.setPermissionOverride(userId, permissionId, newOverrideAllowed.value)
    await loadPermissionOverrides(userId)
  } catch (error) {
    permissionOverridesError.value = error instanceof Error ? error.message : 'เพิ่มสิทธิ์เฉพาะผู้ใช้ไม่สำเร็จ'
  }
}
async function updatePermissionOverride(override: UserPermissionOverride, allowed: boolean) {
  const userId = Number(form.id)
  if (!Number.isSafeInteger(userId) || userId <= 0) return
  permissionOverridesError.value = ''
  try {
    // เปลี่ยนเฉพาะค่า allow/deny ของ permission ที่เลือก
    await userManagementApi.setPermissionOverride(userId, override.permissionId, allowed)
    await loadPermissionOverrides(userId)
  } catch (error) {
    permissionOverridesError.value = error instanceof Error ? error.message : 'บันทึกสิทธิ์ไม่สำเร็จ'
  }
}
async function resetPermissionOverride(override: UserPermissionOverride) {
  const userId = Number(form.id)
  if (!Number.isSafeInteger(userId) || userId <= 0) return
  permissionOverridesError.value = ''
  try {
    await userManagementApi.resetPermissionOverride(userId, override.permissionId)
    await loadPermissionOverrides(userId)
  } catch (error) {
    permissionOverridesError.value = error instanceof Error ? error.message : 'คืนค่าสิทธิ์ตามบทบาทไม่สำเร็จ'
  }
}
const masterItems = computed(() =>
  data.master.map((row) => ({
    id: Number(row.id),
    category: String(row.category),
    name: String(row.examples),
    status: String(row.status),
  })),
)
function editMasterItem(id: number) {
  const index = data.master.findIndex((row) => Number(row.id) === id)
  if (index >= 0) openDrawer(index)
}
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
      branch: selectedBranchName.value || openBranches.value[0]?.name || '',
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
      branch: selectedBranchName.value || openBranches.value[0]?.name || '',
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
// ปิด drawer เมื่อกดและปล่อยบนพื้นหลังทั้งคู่ กันกรณีลากเลือกข้อความในช่องแล้วปล่อยเมาส์นอกกรอบ
const drawerBackdropPressed = ref(false)
function closeDrawerFromBackdrop() {
  if (drawerBackdropPressed.value) drawerOpen.value = false
  drawerBackdropPressed.value = false
}
function openDrawer(index: number | null) {
  permissionOverrides.value = []
  permissionCatalog.value = []
  permissionOverridesError.value = ''
  selectedPermissionId.value = ''
  newOverrideAllowed.value = true
  editingIndex.value = index
  Object.keys(form).forEach((key) => delete form[key])
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
  validationMessage.value = ''
  const row =
    index === null ? undefined : data[section.value as Exclude<SectionKey, 'payments'>]?.[index]
  if (row) Object.assign(form, row)
  else defaults()
  if (section.value === 'branches') form.contact = limitPhoneDigits(String(form.contact ?? ''))
  if (section.value === 'users') form.phone = limitPhoneDigits(String(form.phone ?? ''))
  drawerOpen.value = true
  if (section.value === 'users' && index !== null && Number.isSafeInteger(Number(form.id)))
    void loadPermissionOverrides(Number(form.id))
}
const textOrUndefined = (value: unknown) => String(value ?? '').trim() || undefined
const numberOrUndefined = (value: unknown) => {
  const text = String(value ?? '').trim()
  return text === '' ? undefined : Number(text)
}
// ระยะเวลานัดไม่บังคับและรับเฉพาะจำนวนเต็ม 5–480 นาที
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
    users: ['name', 'role', 'branch', 'username', 'code', 'email'],
    services: ['name', 'type', 'price'],
    rooms: ['name', 'branch', 'type'],
    commission: [],
    tiers: [],
    master: ['category', 'examples'],
  }
  required[section.value as Exclude<SectionKey, 'payments'>].forEach((key) => {
    fieldErrors[key] = String(form[key] ?? '').trim() === ''
  })
  if (section.value === 'branches' && !fieldErrors.contact) {
    const phone = String(form.contact).trim()
    fieldErrors.contact = !isBranchPhoneValid(phone)
  }
  if (section.value === 'users' && String(form.phone ?? '').trim()) {
    fieldErrors.phone = !isOptionalPhoneValid(String(form.phone).trim())
  }
  if (section.value === 'users') {
    // บัญชีผู้ใช้ต้องมีอีเมลรูปแบบถูกต้องและยาวไม่เกิน 254 ตัวอักษร
    const email = String(form.email ?? '').trim()
    fieldErrors.email = email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
  }
  if (section.value === 'branches') {
    fieldErrors.code = !/^\d{2}$/.test(String(form.code))
    const email = String(form.email ?? '').trim()
    fieldErrors.email = email !== '' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
    const open = String(form.openTime ?? '').trim()
    const close = String(form.closeTime ?? '').trim()
    // เวลาทำการต้องกรอกต้นและปลายคู่กัน และเวลาเปิดต้องมาก่อนเวลาปิด
    fieldErrors.openTime = (open === '') !== (close === '') || (open !== '' && open >= close)
    fieldErrors.closeTime = fieldErrors.openTime
    fieldErrors.appointmentMinutes = invalidMinutes(form.appointmentMinutes)
  }
  if (section.value === 'services') {
    fieldErrors.price =
      String(form.price ?? '').trim() === '' ||
      !Number.isFinite(Number(form.price)) ||
      Number(form.price) < 0
    fieldErrors.duration = invalidMinutes(form.duration)
    if (form.type === 'Course') {
      fieldErrors.sessions = !Number.isInteger(Number(form.sessions)) || Number(form.sessions) < 1
      fieldErrors.validityValue =
        !Number.isInteger(Number(form.validityValue)) || Number(form.validityValue) < 1
    }
  }
  if (Object.values(fieldErrors).some(Boolean)) {
    validationMessage.value =
      fieldErrors.contact
          ? 'กรุณากรอกเบอร์โทรศัพท์ให้ครบ 9 หรือ 10 หลัก'
          : fieldErrors.phone
            ? 'เบอร์โทรต้องเป็นตัวเลขไม่เกิน 10 หลัก'
            : fieldErrors.openTime
              ? 'ช่วงเปิดรับนัดต้องกรอกทั้งเวลาเริ่มและเวลาสิ้นสุด โดยเวลาเริ่มอยู่ก่อนเวลาสิ้นสุด'
              : fieldErrors.email
                ? 'รูปแบบอีเมลสาขาไม่ถูกต้อง'
                : fieldErrors.appointmentMinutes || fieldErrors.duration
                  ? 'ระยะเวลานัดต้องเป็นจำนวนเต็ม 5 ถึง 480 นาที (เว้นว่างได้)'
                  : 'กรุณากรอกข้อมูลให้ครบก่อนบันทึก'
    return
  }
  if (section.value === 'users') {
    const password = String(form.password ?? '')
    if (editingIndex.value === null && password.length < 8) {
      validationMessage.value = 'ผู้ใช้ใหม่ต้องมีรหัสผ่านอย่างน้อย 8 ตัวอักษร'
      return
    }
    // แก้ไขผู้ใช้: เว้นว่างได้ แต่ถ้ากรอกต้องไม่ต่ำกว่า 8 ตัว (backend ปฏิเสธรหัสที่สั้นกว่านี้)
    if (editingIndex.value !== null && password !== '' && password.length < 8) {
      fieldErrors.password = true
      validationMessage.value = 'รหัสผ่านใหม่ต้องมีอย่างน้อย 8 ตัวอักษร (เว้นว่างไว้ถ้าไม่เปลี่ยน)'
      return
    }
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
      // ใช้สถานะจากฟอร์ม; สาขาใหม่เริ่มต้นเป็นใช้งาน
      if (existing?.id && form.status && form.status !== existing.status) {
        if (form.status === 'ใช้งาน') await branchApi.activate(record.id as number)
        else await branchApi.deactivate(record.id as number)
        record.status = String(form.status)
      }
    } else if (section.value === 'users') {
      const body = {
        username: form.username,
        email: String(form.email).trim(),
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
      // ใช้สถานะจากฟอร์ม; ผู้ใช้ใหม่เริ่มต้นเป็นใช้งาน
      if (existing?.id && form.status && form.status !== existing.status) {
        if (form.status === 'ใช้งาน') await userManagementApi.activate(existing.id)
        else await userManagementApi.deactivate(existing.id)
        record.status = String(form.status)
      }
    } else if (section.value === 'services') {
      const code = existing?.code || form.code || `SV-${Date.now()}`
      // ส่งประเภทที่เลือกก่อน; backend ปฏิเสธการเปลี่ยนเมื่อมีการใช้งานแล้ว
      if (existing?.id && existing.type && form.type !== existing.type) {
        await serviceManagementApi.changeType(
          existing.id,
          form.type === 'Course' ? 'Course' : 'SingleVisit',
        )
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
      // ใช้สถานะจากฟอร์มกับรายการที่กำลังแก้ไข
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
        // แสดงสีตามสถานะเปิดหรือปิดใช้งาน
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
      // ใช้สถานะจากฟอร์ม; รายการใหม่เริ่มต้นเป็นใช้งาน
      if (existing?.id && form.status && form.status !== existing.status) {
        if (form.status === 'ใช้งาน') await masterDataApi.activate(existing.id)
        else await masterDataApi.deactivate(existing.id)
        record.status = String(form.status)
      }
    } else {
      throw new Error('หมวดนี้อ่านอย่างเดียว กรุณาใช้หน้าจัดการกฎค่ามือ')
    }
    if (editingIndex.value === null) rows.push(record)
    else rows[editingIndex.value] = record
    drawerOpen.value = false
    saveModalOpen.value = true
    window.setTimeout(() => {
      saveModalOpen.value = false
    }, 1800)
  } catch (error) {
    // ถ้า backend ระบุช่องที่ไม่ผ่าน ให้แสดงรายละเอียดต่อท้ายข้อความกว้างๆ
    const details =
      error instanceof ApiError && error.body?.fields
        ? Object.entries(error.body.fields)
            .map(([field, message]) => `${field}: ${message}`)
            .join(', ')
        : ''
    validationMessage.value =
      error instanceof Error
        ? details
          ? `${error.message} (${details})`
          : error.message
        : 'บันทึกไม่สำเร็จ'
  }
}
function notify(message: string) {
  savedMessage.value = message
  window.setTimeout(() => {
    savedMessage.value = ''
  }, 2200)
}
// ยืนยันก่อนเปลี่ยนสถานะของช่องทางชำระแต่ละรายการ
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
const paymentStatusChange = ref<{ index: number; enable: boolean } | null>(null)
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
  if (method.active === change.enable) return
  try {
    if (change.enable) await paymentMethodApi.activate(method.id)
    else await paymentMethodApi.deactivate(method.id)
    method.active = change.enable
    notify(`บันทึก ${paymentLabel(method.name)} อัตโนมัติแล้ว`)
  } catch (error) {
    notify(error instanceof Error ? error.message : 'เปลี่ยนสถานะไม่สำเร็จ')
  }
}
async function setPaymentStatusEnabled(index: number, enabled: boolean) {
  const option = paymentStatuses[index]
  if (!option || option.enabled === enabled) return
  paymentStatusChange.value = { index, enable: enabled }
}
async function confirmPaymentStatusChange() {
  const change = paymentStatusChange.value
  paymentStatusChange.value = null
  const option = change ? paymentStatuses[change.index] : undefined
  if (!change || !option || option.enabled === change.enable) return
  try {
    const saved = await paymentStatusApi.setEnabled(option.code, change.enable)
    option.enabled = saved.enabled
    notify(`บันทึกสถานะ${saved.label}อัตโนมัติแล้ว`)
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
    return editingIndex.value === null
      ? 'แก้กฎค่ามือ (บันทึกเป็นเวอร์ชันใหม่)'
      : 'แก้ไขเวอร์ชันที่รอเริ่มใช้'
  return `${editingIndex.value === null ? 'เพิ่ม' : 'แก้ไข'} ${labels[section.value]}`
})
</script>

<template>
  <div class="settings-page">
    <StaffHeader />
    <main class="settings-main">
      <header class="settings-title">
        <div>
          <span class="settings-eyebrow">ศูนย์ควบคุมระบบ</span>
          <h1>การตั้งค่า</h1>
          <p>จัดการข้อมูลสาขา ผู้ใช้งาน สิทธิ์การเข้าถึง และการตั้งค่าระบบ</p>
        </div>
      </header>
      <div class="settings-layout">
        <aside class="settings-nav">
          <div class="settings-nav-head">
            <div>
              <h2>หมวดการตั้งค่า</h2>
              <p>เลือกหัวข้อเพื่อจัดการ</p>
            </div>
          </div>
          <nav class="settings-nav-list" aria-label="หมวดการตั้งค่า">
            <button
              v-for="item in sections"
              :key="item.key"
              :class="{ active: section === item.key }"
              @click="section = item.key"
            >
              <span class="settings-nav-icon" aria-hidden="true"
                ><SettingsNavIcon :name="item.icon"
              /></span>
              <span
                ><b>{{ item.label }}</b
                ><small>{{ item.hint }}</small></span
              >
              <span class="settings-nav-arrow" aria-hidden="true">›</span>
            </button>
          </nav>
          <div class="settings-nav-footer">
            <RouterLink class="settings-nav-item" to="/staff/settings/calendar">
              <span class="settings-nav-icon" aria-hidden="true"
                ><SettingsNavIcon name="calendar" /></span
              ><span><b>Google Calendar</b><small>ปฏิทินและการซิงก์</small></span
              ><span class="settings-nav-arrow" aria-hidden="true">›</span>
            </RouterLink>
            <RouterLink class="settings-nav-link" to="/staff/settings/audit-log"
              >ดูประวัติการแก้ไขระบบ <span>→</span></RouterLink
            >
          </div>
        </aside>
        <section class="settings-panel">
          <header class="settings-panel-head">
            <div>
              <h2>{{ copy[0] }}</h2>
              <p>{{ copy[1] }}</p>
              <p v-if="section === 'users'" class="settings-branch-context">
                สาขา: {{ selectedBranchName || 'ยังไม่ได้เลือกสาขา' }}
              </p>
            </div>
            <button v-if="actionLabel" class="settings-add" @click="openDrawer(null)">
              {{ actionLabel }}
            </button>
            <div v-else-if="section === 'commission'" class="settings-actions">
              <RouterLink class="settings-secondary" to="/staff/settings/fee-history"
                >ประวัติเวอร์ชัน</RouterLink
              ><TreatmentFeeRuleDrawer @saved="loadBackendSettings" />
            </div>
            <div v-else-if="section === 'tiers'" class="settings-actions">
              <RouterLink class="settings-secondary" to="/staff/settings/commission-history"
                >ประวัติเวอร์ชัน</RouterLink
              ><RouterLink class="settings-add" to="/staff/settings/commission-tiers"
                >ตั้งค่ากฎค่าคอมมิชชัน</RouterLink
              >
            </div>
            <p v-else-if="section === 'payments'" class="auto-save-note">
              {{ savedMessage || 'การเปลี่ยนแปลงจะบันทึกอัตโนมัติ' }}
            </p>
          </header>
          <div
            v-if="section === 'services' || section === 'users' || section === 'rooms'"
            class="settings-toolbar"
          >
            <label v-if="section === 'services'" class="settings-search">
              <span>ค้นหารายการ</span>
              <input
                v-model="serviceSearch"
                type="search"
                placeholder="ค้นหาชื่อรายการหรือประเภท"
              />
            </label>
            <label v-if="section === 'users'">
              <span>บทบาท</span>
              <FilterDropdown
                v-model="userRoleFilter"
                :options="userRoleOptions"
                default-value="ทั้งหมด"
                default-label="ทั้งหมด"
                label="บทบาท"
                :clear-on-reselect="false"
              />
            </label>
            <label v-if="section === 'users'">
              <span>สถานะ</span>
              <FilterDropdown
                v-model="userStatusFilter"
                :options="statusOptions"
                default-value="ทั้งหมด"
                default-label="ทั้งหมด"
                label="สถานะ"
                :clear-on-reselect="false"
              />
            </label>
            <label v-if="section === 'services'">
              <span>ประเภท</span>
              <FilterDropdown
                v-model="serviceTypeFilter"
                :options="serviceTypeOptions"
                default-value="ทั้งหมด"
                default-label="ทั้งหมด"
                label="ประเภท"
                :clear-on-reselect="false"
              />
            </label>
          </div>
          <p v-if="loadError" class="validation-message" role="alert">{{ loadError }}</p>
          <div v-if="section === 'payments'" class="payment-list">
            <div
              v-for="(method, index) in paymentMethods"
              :key="method.name"
              class="payment-option"
            >
              <span
                ><b>{{ paymentLabel(method.name) }}</b
                ><small
                  >{{ paymentNote(method.name) }} ·
                  {{ method.active ? 'เปิดใช้งานอยู่' : 'ปิดอยู่' }}</small
                ></span
              >
              <span
                class="payment-status"
                role="group"
                :aria-label="`สถานะ ${paymentLabel(method.name)}`"
              >
                <button
                  type="button"
                  class="enable"
                  :class="{ on: method.active }"
                  :aria-pressed="method.active"
                  @click="askPaymentStatus(index, true)"
                >
                  เปิดใช้งาน
                </button>
                <button
                  type="button"
                  class="disable"
                  :class="{ on: !method.active }"
                  :aria-pressed="!method.active"
                  @click="askPaymentStatus(index, false)"
                >
                  ปิดใช้งาน
                </button>
              </span>
            </div>
          </div>
          <section v-if="section === 'payments'" class="payment-list payment-status-settings">
            <h3>สถานะการชำระเงิน</h3>
            <div
              v-for="(option, index) in paymentStatuses"
              :key="option.code"
              class="payment-option"
            >
              <span
                ><b>{{ option.label }}</b
                ><small>{{
                  option.enabled ? 'แสดงในหน้าบริการ' : 'ซ่อนจากหน้าบริการ'
                }}</small></span
              >
              <span class="payment-status" role="group" :aria-label="`สถานะ ${option.label}`">
                <button
                  type="button"
                  class="enable"
                  :class="{ on: option.enabled }"
                  :aria-pressed="option.enabled"
                  @click="setPaymentStatusEnabled(index, true)"
                >
                  เปิดใช้งาน
                </button>
                <button
                  type="button"
                  class="disable"
                  :class="{ on: !option.enabled }"
                  :aria-pressed="!option.enabled"
                  @click="setPaymentStatusEnabled(index, false)"
                >
                  ปิดใช้งาน
                </button>
              </span>
            </div>
          </section>
          <section v-else-if="section === 'tiers'" class="latest-version">
            <p v-if="!latestVersion" class="empty">
              ยังไม่มีเวอร์ชันของกฎค่าคอมมิชชันที่ใช้อยู่ในตอนนี้
            </p>
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
            <p v-if="!feeInForce" class="empty">
              ยังไม่มีกฎค่ามือที่ใช้อยู่ในตอนนี้ การรักษาแทนจะบันทึกไม่ได้จนกว่ากฎจะเริ่มมีผล
            </p>
            <article v-for="item in shownFeeVersions" :key="item.row.id" class="fee-version">
              <header>
                <h3>
                  {{
                    item.status.tone === 'pending' ? 'เวอร์ชันที่รอเริ่มใช้' : 'รายละเอียดเวอร์ชัน'
                  }}
                  V{{ item.number }}
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
          <MasterDataGroups
            v-else-if="section === 'master'"
            :items="masterItems"
            @edit="editMasterItem"
          />
          <div v-else class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th v-for="head in headers" :key="head">{{ head }}</th>
                  <th>การจัดการ</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in pagedTableRows" :key="item.index">
                  <td v-for="key in keys" :key="key">
                    <span
                      v-if="key === 'status'"
                      class="settings-status"
                      :class="{
                        'status-off':
                          String(item.row[key]).startsWith('ปิด') ||
                          String(item.row[key]).startsWith('ไม่พร้อม'),
                      }"
                      >{{ item.row[key] }}</span
                    >
                    <template v-else-if="key === 'type'">
                      {{ serviceTypeLabel(item.row[key]) }}
                    </template>
                    <template v-else-if="key === 'role'">
                      {{ roleLabel(item.row[key]) }}
                    </template>
                    <span v-else-if="key === 'permission'" class="settings-permissions">
                      <span
                        v-for="permission in permissionList(item.row[key])"
                        :key="permission.key"
                        class="settings-permission-chip"
                        >{{ permission.label }}</span
                      >
                    </span>
                    <template v-else>{{ item.row[key] }}</template>
                  </td>
                  <td>
                    <button class="edit" @click="openDrawer(item.index)">แก้ไข</button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div
              v-if="filteredTableRows.length"
              class="table-pagination"
              aria-label="เปลี่ยนหน้ารายการ"
            >
              <p>{{ tableRange }}</p>
              <div>
                <button type="button" :disabled="currentPage === 1" @click="currentPage -= 1">
                  ก่อนหน้า
                </button>
                <span>หน้า {{ currentPage }} / {{ pageCount }}</span>
                <button
                  type="button"
                  :disabled="currentPage === pageCount"
                  @click="currentPage += 1"
                >
                  ถัดไป
                </button>
              </div>
            </div>
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
          ต้องการ{{ paymentChange.enable ? 'เปิดใช้งาน' : 'ปิดใช้งาน' }}
          <b>{{ paymentLabel(paymentMethods[paymentChange.index]!.name) }}</b> หรือไม่?
        </p>
        <div class="confirm-actions">
          <button type="button" class="confirm-cancel" @click="paymentChange = null">ยกเลิก</button>
          <button type="button" class="confirm-ok" @click="confirmPaymentStatus">ยืนยัน</button>
        </div>
      </div>
    </div>
    <div
      v-if="paymentStatusChange"
      class="confirm-backdrop"
      @click.self="paymentStatusChange = null"
    >
      <div class="confirm-modal" role="dialog" aria-modal="true" aria-label="ยืนยันการเปลี่ยนสถานะ">
        <h3>ยืนยันการเปลี่ยนสถานะ</h3>
        <p>
          ต้องการ{{ paymentStatusChange.enable ? 'เปิดใช้งาน' : 'ปิดใช้งาน' }}
          <b>{{ paymentStatuses[paymentStatusChange.index]!.label }}</b> หรือไม่?
        </p>
        <div class="confirm-actions">
          <button type="button" class="confirm-cancel" @click="paymentStatusChange = null">
            ยกเลิก
          </button>
          <button type="button" class="confirm-ok" @click="confirmPaymentStatusChange">
            ยืนยัน
          </button>
        </div>
      </div>
    </div>
    <div v-if="saveModalOpen" class="save-success-backdrop" @click="saveModalOpen = false">
      <div class="save-success-modal" role="status">บันทึกเรียบร้อยแล้ว</div>
    </div>
    <div
      v-if="drawerOpen"
      class="drawer-backdrop"
      @mousedown="drawerBackdropPressed = $event.target === $event.currentTarget"
      @click.self="closeDrawerFromBackdrop"
    >
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
              maxlength="10"
              :class="{ invalid: fieldErrors.contact }"
              placeholder="เบอร์โทรศัพท์สาขา"
              pattern="[0-9]*"
              @input="form.contact = limitPhoneDigits(form.contact)" /></label
          ><label
            >อีเมลสาขา<input
              v-model="form.email"
              type="email"
              :class="{ invalid: fieldErrors.email }"
              placeholder="ไม่บังคับ" /></label
          ><label
            >เลขใบอนุญาต<input
              v-model="form.licenseNo"
              maxlength="50"
              placeholder="ไม่บังคับ" /></label
          ><label
            >เปิดรับนัดตั้งแต่<TimeFilterPicker
              v-model="form.openTime"
              label="เปิดรับนัดตั้งแต่"
              :class="{ invalid: fieldErrors.openTime }" /></label
          ><label
            >ถึงเวลา<TimeFilterPicker
              v-model="form.closeTime"
              label="ถึงเวลา"
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
          ><label
            >อีเมล<input
              v-model="form.email"
              type="email"
              required
              maxlength="254"
              :class="{ invalid: fieldErrors.email }" />
            <small v-if="fieldErrors.email" role="alert">กรอกอีเมลบัญชีที่ถูกต้อง ไม่เกิน 254 ตัวอักษร</small></label
          ><label
            >รหัสผ่าน<input
              v-model="form.password"
              :class="{ invalid: fieldErrors.password }"
              type="password"
              autocomplete="new-password"
              placeholder="เว้นว่างได้เมื่อแก้ไข" /></label
          ><label
            >รหัสพนักงาน<input v-model="form.code" :class="{ invalid: fieldErrors.code }" /></label
          ><label
            >โทรศัพท์<input
              v-model="form.phone"
              type="tel"
              inputmode="numeric"
              pattern="[0-9]*"
              maxlength="10"
              @input="form.phone = limitPhoneDigits(form.phone)" /></label
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
              :options="branchOptions"
              :default-value="openBranches[0]?.name ?? ''"
              :default-label="openBranches[0]?.name ?? 'เลือกสาขา'"
              label="สาขาที่เข้า"
              hide-default-option
              :clear-on-reselect="false"
          /></label>
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
            >ชื่อบริการ/คอร์ส<input
              v-model="form.name"
              :class="{ invalid: fieldErrors.name }"
              placeholder="ระบุชื่อคอร์สหรือบริการ" /></label
          ><label :class="{ 'invalid-field': fieldErrors.type }"
            >ประเภท<FilterDropdown
              v-model="form.type"
              :options="serviceTypeOptions"
              default-value="Single Visit"
              default-label="บริการรายครั้ง"
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
          >
          <p v-else class="hint">บริการรายครั้งคือ 1 ครั้งต่อรายการ และไม่มีวันหมดอายุ</p>
          <label
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
        ></template>
        <template v-else-if="section === 'commission'">
          <p>ตั้งค่ากฎค่ามือได้จากปุ่มด้านบน ระบบจะสร้างเวอร์ชันใหม่ตามเดือนที่เริ่มใช้งาน</p>
        </template>
        <template v-else-if="section === 'master'"
          ><label :class="{ 'invalid-field': fieldErrors.category }"
            >ประเภทข้อมูล<FilterDropdown
              v-model="form.category"
              :options="[
                { value: 'Customer Group', label: 'กลุ่มลูกค้า' },
                { value: 'Referral Channel', label: 'ช่องทางแนะนำ' },
                { value: 'Insurance Type', label: 'บริษัทประกัน' },
              ]"
              default-value="Customer Group"
              default-label="กลุ่มลูกค้า"
              label="ประเภทข้อมูล"
              hide-default-option
              :clear-on-reselect="false" /></label
          ><label
            >ชื่อรายการ<input
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
