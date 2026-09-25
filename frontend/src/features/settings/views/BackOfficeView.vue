<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import {
  branchApi,
  commissionApi,
  courseApi,
  masterDataApi,
  paymentMethodApi,
  resourceApi,
  serviceManagementApi,
  userManagementApi,
  type Branch as ApiBranch,
  type ClinicResource,
  type Course,
  type ManagedService,
  type ManagedUser,
  type MasterData,
  type PaymentMethod as ApiPaymentMethod,
} from '@/api/client'

type Section = 'branches' | 'users' | 'services' | 'payments' | 'rooms' | 'commission' | 'master'
type CatalogItem = (ManagedService & { kind: 'service' }) | (Course & { kind: 'course' })
type CommissionTier = {
  id: number
  minimum: number
  maximum: number | null
  rate: number
  order: number
}
type CommissionScheme = {
  id: number
  name: string
  version: string
  effectiveFrom: string
  effectiveTo: string | null
  active: boolean
  tiers: CommissionTier[]
}
type SettingsState = {
  branches: ApiBranch[]
  users: ManagedUser[]
  services: CatalogItem[]
  payments: ApiPaymentMethod[]
  rooms: ClinicResource[]
  commission: CommissionScheme[]
  master: MasterData[]
}
type DisplayRow = { cells: string[]; status: string; index: number }
const permissionLabels = ['Patient', 'Visit', 'Treatment', 'Course', 'Transaction', 'Commission']

const sections: {
  key: Section
  label: string
  heading: string
  description: string
  action?: string
}[] = [
  {
    key: 'branches',
    label: 'สาขา',
    heading: 'จัดการสาขา',
    description: 'ข้อมูลสาขาทั้งหมดในระบบ',
    action: '+ เพิ่มสาขา',
  },
  {
    key: 'users',
    label: 'ผู้ใช้และสิทธิ์',
    heading: 'ผู้ใช้งานและสิทธิ์',
    description: 'จัดการผู้ใช้งานและสิทธิ์การเข้าถึงระบบ',
    action: '+ เพิ่มผู้ใช้งาน',
  },
  {
    key: 'services',
    label: 'บริการและคอร์ส',
    heading: 'บริการและคอร์ส',
    description: 'จัดการรายการบริการและคอร์สทั้งหมดในระบบ',
    action: '+ เพิ่มรายการ',
  },
  {
    key: 'payments',
    label: 'การชำระเงิน',
    heading: 'การชำระเงิน',
    description: 'เลือกวิธีการชำระเงินที่เปิดให้บริการในระบบ',
  },
  {
    key: 'rooms',
    label: 'Room / Resource',
    heading: 'Room / Resource',
    description: 'จัดการห้องและทรัพยากรที่ใช้ในการให้บริการ',
    action: '+ เพิ่มห้อง/ทรัพยากร',
  },
  {
    key: 'commission',
    label: 'Commission Rule',
    heading: 'Commission Rule',
    description: 'จัดการกฎเกณฑ์การคำนวณค่าคอมมิชชั่นในระบบ',
    action: '+ เพิ่มกฎ',
  },
  {
    key: 'master',
    label: 'Master Data',
    heading: 'Master Data',
    description: 'จัดการข้อมูลหลักที่ใช้อ้างอิงทั่วทั้งระบบ',
    action: '+ เพิ่มข้อมูล',
  },
]

const data = reactive<SettingsState>({
  branches: [],
  users: [],
  services: [],
  payments: [],
  rooms: [],
  commission: [],
  master: [],
})
const section = ref<Section>('branches')
const editorOpen = ref(false)
const editingIndex = ref<number | null>(null)
const formError = ref('')
const loading = ref(true)
const saving = ref(false)
const loadErrors = reactive<Record<Section, string>>({
  branches: '',
  users: '',
  services: '',
  payments: '',
  rooms: '',
  commission: '',
  master: '',
})
function createEmptyForm() {
  return {
    code: '',
    branchDisplayCode: '',
    name: '',
    username: '',
    email: '',
    password: '',
    employeeCode: '',
    phone: '',
    role: '',
    branch: '',
    type: '',
    price: '',
    sessions: '',
    durationMinutes: '30',
    bonusSessions: '0',
    validityDays: '365',
    version: '',
    effectiveFrom: '',
    effectiveTo: '',
    tiers: [{ minimum: '0', maximum: '', rate: '' }],
    category: '',
    resourceStatus: 'AVAILABLE',
    active: true,
  }
}

const form = reactive(createEmptyForm())
watch(
  () => form.type,
  (type) => {
    if (type === 'service') form.sessions = '1'
  },
)
const statusCannotReactivate = computed(
  () =>
    editingIndex.value !== null &&
    !form.active &&
    (section.value === 'branches' || section.value === 'services'),
)

function currentActive(index: number): boolean {
  switch (section.value) {
    case 'branches':
      return data.branches[index]!.active
    case 'users':
      return data.users[index]!.active
    case 'services':
      return data.services[index]!.active
    case 'rooms':
      return data.rooms[index]!.active
    case 'commission':
      return data.commission[index]!.active
    case 'master':
      return data.master
        .filter((item) => item.category === data.master[index]!.category)
        .every((item) => item.active)
    default:
      return true
  }
}

async function applyActiveStatus(id: number, active: boolean) {
  switch (section.value) {
    case 'branches':
      if (!active) await branchApi.deactivate(id)
      break
    case 'users':
      if (active) await userManagementApi.activate(id)
      else await userManagementApi.deactivate(id)
      break
    case 'services': {
      if (active) break
      const item = data.services[editingIndex.value ?? -1]
      if (form.type === 'course' || item?.kind === 'course') await courseApi.deactivate(id)
      else await serviceManagementApi.deactivate(id)
      break
    }
    case 'rooms':
      if (active) await resourceApi.activate(id)
      else await resourceApi.deactivate(id)
      break
    case 'commission':
      if (active) await commissionApi.activateScheme(id)
      else await commissionApi.deactivateScheme(id)
      break
    case 'master':
      if (active) await masterDataApi.activate(id)
      else await masterDataApi.deactivate(id)
      break
  }
}

async function loadSection(key: Section) {
  try {
    switch (key) {
      case 'branches':
        data.branches = await branchApi.list()
        break
      case 'users':
        data.users = await userManagementApi.list()
        break
      case 'services': {
        const [services, courses] = await Promise.all([
          serviceManagementApi.list(),
          courseApi.list(),
        ])
        data.services = [
          ...services.map((item) => ({ ...item, kind: 'service' as const })),
          ...courses.map((item) => ({ ...item, kind: 'course' as const })),
        ]
        break
      }
      case 'payments':
        data.payments = await paymentMethodApi.list()
        break
      case 'rooms':
        data.rooms = await resourceApi.list()
        break
      case 'master':
        data.master = await masterDataApi.list()
        break
      case 'commission': {
        const schemes = (await commissionApi.schemes()) as Omit<CommissionScheme, 'tiers'>[]
        data.commission = await Promise.all(
          schemes.map(async (scheme) => ({
            ...scheme,
            tiers: (await commissionApi.tiers(scheme.id)) as CommissionTier[],
          })),
        )
        break
      }
    }
    loadErrors[key] = ''
  } catch (error) {
    loadErrors[key] = error instanceof Error ? error.message : 'โหลดข้อมูลไม่สำเร็จ'
  }
}

onMounted(async () => {
  await Promise.all(sections.map((item) => loadSection(item.key)))
  loading.value = false
})

const currentSection = computed(() => sections.find((item) => item.key === section.value)!)
const resourceStatusLabels: Record<string, string> = {
  AVAILABLE: 'พร้อมใช้งาน',
  IN_USE: 'กำลังใช้งาน',
  MAINTENANCE: 'ซ่อมบำรุง',
}
const columns = computed(() => {
  switch (section.value) {
    case 'branches':
      return ['รหัสสาขา', 'ชื่อสาขา', 'ข้อมูลติดต่อ']
    case 'users':
      return ['ผู้ใช้งาน', 'บทบาท', 'สาขา', 'สิทธิ์']
    case 'services':
      return ['รายการ', 'ประเภท', 'ราคา', 'จำนวน/ครั้ง']
    case 'rooms':
      return ['ห้อง/ทรัพยากร', 'สาขา', 'ประเภท']
    case 'commission':
      return ['ชื่อกฎ', 'ผู้ได้รับค่าคอมมิชชั่น', 'วิธีคำนวณ']
    case 'master':
      return ['ประเภทข้อมูล', 'จำนวนรายการ', 'ตัวอย่างรายการ']
    default:
      return []
  }
})
const rows = computed<DisplayRow[]>(() => {
  switch (section.value) {
    case 'branches':
      return data.branches.map((item, index) => ({
        cells: [`${item.code}${item.numericCode}`, item.name, '—'],
        status: item.active ? 'ใช้งาน' : 'ปิดใช้งาน',
        index,
      }))
    case 'users':
      return data.users.map((item, index) => ({
        cells: [
          item.fullName,
          item.role === 'MANAGER' ? 'Manager' : 'Therapist',
          item.branchName ?? 'ทุกสาขา',
          'ตาม Role',
        ],
        status: item.active ? 'ใช้งาน' : 'ปิดใช้งาน',
        index,
      }))
    case 'services':
      return data.services.map((item, index) => ({
        cells: [
          item.name,
          item.kind === 'course'
            ? 'Course'
            : /assessment/i.test(item.name)
              ? 'Assessment'
              : 'Service',
          '฿' + item.price.toLocaleString('th-TH'),
          item.kind === 'course' ? item.sessions + ' ครั้ง' : '1 ครั้ง',
        ],
        status: item.active ? 'ใช้งาน' : 'ปิดใช้งาน',
        index,
      }))
    case 'rooms':
      return data.rooms.map((item, index) => ({
        cells: [item.name, item.branchName, item.type === 'ROOM' ? 'Treatment Room' : 'Resource'],
        status: item.active ? (resourceStatusLabels[item.status] ?? item.status) : 'ปิดใช้งาน',
        index,
      }))
    case 'commission':
      return data.commission.map((item, index) => ({
        cells: [
          item.name,
          '—',
          item.tiers.map((tier) => `${(tier.rate * 100).toLocaleString('th-TH')}%`).join(', '),
        ],
        status: item.active ? 'เปิดใช้งาน' : 'ปิดใช้งาน',
        index,
      }))
    case 'master':
      return [...new Set(data.master.map((item) => item.category))].map((category) => {
        const items = data.master.filter((item) => item.category === category)
        const names = items.flatMap((item) => item.name.split(',').map((part) => part.trim()))
        return {
          cells: [
            category,
            `${names.filter(Boolean).length} รายการ`,
            names.filter(Boolean).join(', '),
          ],
          status: items.every((item) => item.active) ? 'ใช้งาน' : 'ปิดใช้งาน',
          index: data.master.findIndex((item) => item.category === category),
        }
      })
    default:
      return []
  }
})
const drawerTitle = computed(() => {
  const adding = editingIndex.value === null
  switch (section.value) {
    case 'branches':
      return adding ? 'เพิ่มสาขา' : 'แก้ไขสาขา'
    case 'users':
      return adding ? 'สร้างผู้ใช้' : 'แก้ไข ผู้ใช้และสิทธิ์'
    case 'services':
      return `${adding ? 'เพิ่ม' : 'แก้ไข'}${form.type === 'service' ? 'บริการ' : 'คอร์ส'}`
    case 'rooms':
      return adding ? 'เพิ่มห้อง/ทรัพยากร' : 'แก้ไขห้อง/ทรัพยากร'
    case 'commission':
      return adding ? 'เพิ่มเกณฑ์คอมมิชชั่น' : 'แก้ไขเกณฑ์คอมมิชชั่น'
    case 'master':
      return adding ? 'เพิ่ม Master Data' : 'แก้ไข Master Data'
    default:
      return ''
  }
})

function selectSection(key: Section) {
  section.value = key
  formError.value = ''
}
function openEditor(index: number | null = null) {
  editingIndex.value = index
  formError.value = ''
  Object.assign(form, createEmptyForm())
  if (section.value === 'rooms') form.type = 'ROOM'
  if (index !== null) {
    form.active = currentActive(index)
    switch (section.value) {
      case 'branches': {
        const item = data.branches[index]!
        Object.assign(form, {
          branchDisplayCode: `${item.code}${item.numericCode}`,
          name: item.name,
        })
        break
      }
      case 'users': {
        const item = data.users[index]!
        Object.assign(form, {
          name: item.fullName,
          username: item.username,
          email: item.email ?? '',
          employeeCode: item.employeeCode,
          phone: item.phone ?? '',
          role: item.role,
          branch: item.branchCode ?? '',
        })
        break
      }
      case 'services': {
        const item = data.services[index]!
        Object.assign(form, {
          code: item.code,
          name: item.name,
          type: item.kind,
          price: String(item.price),
        })
        if (item.kind === 'course')
          Object.assign(form, {
            sessions: String(item.sessions),
            bonusSessions: String(item.bonusSessions),
            validityDays: String(item.validityDays),
          })
        else form.durationMinutes = String(item.defaultDurationMinutes)
        break
      }
      case 'rooms': {
        const item = data.rooms[index]!
        Object.assign(form, {
          code: item.code,
          name: item.name,
          branch: item.branchCode,
          type: item.type,
          resourceStatus: item.status,
        })
        break
      }
      case 'commission': {
        const item = data.commission[index]!
        Object.assign(form, {
          name: item.name,
          version: item.version,
          effectiveFrom: item.effectiveFrom,
          effectiveTo: item.effectiveTo ?? '',
          tiers: item.tiers.map((tier) => ({
            minimum: String(tier.minimum),
            maximum: tier.maximum === null ? '' : String(tier.maximum),
            rate: String(tier.rate * 100),
          })),
        })
        break
      }
      case 'master': {
        const item = data.master[index]!
        Object.assign(form, {
          category: item.category,
          name: data.master
            .filter((entry) => entry.category === item.category)
            .map((entry) => entry.name)
            .join(', '),
        })
        break
      }
    }
  }
  editorOpen.value = true
}
async function save() {
  if (saving.value) return
  formError.value = ''
  const name = form.name.trim()
  const current = editingIndex.value
  const code = form.code.trim().toUpperCase()
  const previousActive = current === null ? true : currentActive(current)
  let savedId: number | null = null
  saving.value = true
  try {
    switch (section.value) {
      case 'branches': {
        const match = /^([A-Z]{1,2})(\d{2})$/.exec(form.branchDisplayCode.trim().toUpperCase())
        if (!match || !name) throw new Error('กรุณาระบุรหัสสาขา เช่น BR01 และชื่อสาขา')
        if (current !== null && match[2] !== data.branches[current]!.numericCode)
          throw new Error('ไม่สามารถเปลี่ยนตัวเลขท้ายรหัสสาขาได้ เพราะใช้ผูกกับ HN เดิม')
        const body = { code: match[1]!, numericCode: match[2]!, name }
        savedId =
          current === null
            ? (await branchApi.create(body)).id
            : (await branchApi.update(data.branches[current]!.id, body)).id
        break
      }
      case 'users': {
        if (!name || !form.username.trim() || !form.employeeCode.trim() || !form.role)
          throw new Error('กรุณาระบุชื่อ ชื่อเข้าระบบ รหัสพนักงาน และบทบาท')
        if (current === null && form.password.length < 8)
          throw new Error('รหัสผ่านผู้ใช้ใหม่ต้องมีอย่างน้อย 8 ตัวอักษร')
        if (form.password && (form.password.length < 8 || form.password.length > 100))
          throw new Error('รหัสผ่านต้องมีความยาว 8–100 ตัวอักษร')
        const body = {
          username: form.username.trim(),
          email: form.email.trim() || undefined,
          role: form.role as 'MANAGER' | 'THERAPIST',
          employeeCode: form.employeeCode.trim(),
          fullName: name,
          phone: form.phone.trim() || undefined,
          branchCode: form.branch || undefined,
          ...(form.password ? { password: form.password } : {}),
        }
        savedId =
          current === null
            ? (await userManagementApi.create({ ...body, password: form.password })).id
            : (await userManagementApi.update(data.users[current]!.id, body)).id
        break
      }
      case 'services': {
        const price = Number(form.price)
        if (!code || !name || form.price === '' || !Number.isFinite(price) || price < 0)
          throw new Error('กรุณาระบุรหัส ชื่อ และราคาให้ถูกต้อง')
        const existing = current === null ? null : data.services[current]!
        if (form.type === 'course') {
          const sessions = Number(form.sessions),
            bonusSessions = Number(form.bonusSessions),
            validityDays = Number(form.validityDays)
          if (
            ![sessions, bonusSessions, validityDays].every(Number.isInteger) ||
            sessions < 1 ||
            bonusSessions < 0 ||
            validityDays < 1
          )
            throw new Error('จำนวนครั้ง โบนัส และอายุคอร์สต้องเป็นจำนวนเต็มที่ถูกต้อง')
          const body = { code, name, price, sessions, bonusSessions, validityDays }
          savedId = existing
            ? (await courseApi.update(existing.id, body)).id
            : (await courseApi.create(body)).id
        } else if (form.type === 'service') {
          const defaultDurationMinutes = Number(form.durationMinutes)
          if (!Number.isInteger(defaultDurationMinutes) || defaultDurationMinutes < 1)
            throw new Error('กรุณาระบุระยะเวลาบริการเป็นจำนวนนาที')
          const body = { code, name, price, defaultDurationMinutes }
          savedId = existing
            ? (await serviceManagementApi.update(existing.id, body)).id
            : (await serviceManagementApi.create(body)).id
        } else throw new Error('กรุณาเลือกประเภทบริการหรือคอร์ส')
        break
      }
      case 'rooms': {
        if (!code || !name || !form.branch || !form.type || !form.resourceStatus)
          throw new Error('กรุณาระบุรหัส ชื่อ สาขา ประเภท และสถานะ')
        const body = {
          code,
          name,
          branchCode: form.branch,
          type: form.type,
          status: form.resourceStatus,
        }
        savedId =
          current === null
            ? (await resourceApi.create(body)).id
            : (await resourceApi.update(data.rooms[current]!.id, body)).id
        break
      }
      case 'commission': {
        if (!name || !form.version.trim() || !form.effectiveFrom || !form.tiers.length)
          throw new Error('กรุณาระบุชื่อ เวอร์ชัน วันที่เริ่มใช้ และช่วงคอมมิชชั่น')
        if (form.effectiveTo && form.effectiveTo < form.effectiveFrom)
          throw new Error('วันที่สิ้นสุดต้องไม่ก่อนวันที่เริ่มใช้')
        if (form.tiers.some((tier) => tier.minimum === '' || tier.rate === ''))
          throw new Error('กรุณาระบุยอดขั้นต่ำและอัตราคอมมิชชั่นทุกช่วง')
        const tiers = form.tiers.map((tier, index) => ({
          minimum: Number(tier.minimum),
          maximum: tier.maximum === '' ? null : Number(tier.maximum),
          rate: Number(tier.rate) / 100,
          order: index + 1,
        }))
        if (
          tiers.some(
            (tier) =>
              !Number.isFinite(tier.minimum) ||
              tier.minimum < 0 ||
              (tier.maximum !== null &&
                (!Number.isFinite(tier.maximum) || tier.maximum < tier.minimum)) ||
              !Number.isFinite(tier.rate) ||
              tier.rate < 0 ||
              tier.rate > 1,
          )
        )
          throw new Error('กรุณาระบุยอดขั้นต่ำ ยอดสูงสุด และอัตราคอมมิชชั่นให้ถูกต้อง')
        if (
          tiers[0]?.minimum !== 0 ||
          tiers.some(
            (tier, index) =>
              index < tiers.length - 1 &&
              (tier.maximum === null ||
                Math.abs(tiers[index + 1]!.minimum - tier.maximum - 0.01) > 0.000001),
          )
        )
          throw new Error('ช่วงคอมมิชชั่นต้องเริ่มจาก 0 และต่อเนื่องกันทีละ 0.01')
        const body = {
          name,
          version: form.version.trim(),
          effectiveFrom: form.effectiveFrom,
          effectiveTo: form.effectiveTo || null,
          tiers,
        }
        const saved =
          current === null
            ? await commissionApi.createScheme(body)
            : await commissionApi.updateScheme(data.commission[current]!.id, body)
        savedId = (saved as { id: number }).id
        break
      }
      case 'master': {
        const category = form.category.trim()
        const names = form.name
          .split(',')
          .map((part) => part.trim())
          .filter(Boolean)
        if (!category || names.length === 0)
          throw new Error('กรุณาระบุประเภทข้อมูลและตัวอย่างรายการ')
        if (
          current === null &&
          data.master.some((item) => item.category === category.toUpperCase())
        )
          throw new Error('ประเภทข้อมูลนี้มีอยู่แล้ว กรุณาแก้ไขรายการเดิม')
        if (new Set(names.map((item) => item.toLocaleLowerCase())).size !== names.length)
          throw new Error('ตัวอย่างรายการต้องไม่ซ้ำกัน')
        const originalCategory = current === null ? null : data.master[current]!.category
        const existing =
          originalCategory === null
            ? []
            : data.master.filter((item) => item.category === originalCategory)
        if (names.length < existing.length)
          throw new Error('ยังไม่สามารถลบรายการเดิมจากช่องนี้ได้ กรุณาคงจำนวนรายการเดิมไว้')
        for (const [index, itemName] of names.entries()) {
          const original = existing[index]
          const item = original
            ? await masterDataApi.update(original.id, {
                category,
                code: original.code,
                name: itemName,
              })
            : await masterDataApi.create({
                category,
                code: `MD_${crypto.randomUUID().replaceAll('-', '').slice(0, 12).toUpperCase()}`,
                name: itemName,
              })
          if (item.active !== form.active) await applyActiveStatus(item.id, form.active)
        }
        break
      }
      default:
        return
    }
    if (savedId !== null && form.active !== previousActive) {
      await applyActiveStatus(savedId, form.active)
    }
    await loadSection(section.value)
    editorOpen.value = false
  } catch (error) {
    if (section.value === 'master') await loadSection('master')
    formError.value = error instanceof Error ? error.message : 'บันทึกข้อมูลไม่สำเร็จ'
  } finally {
    saving.value = false
  }
}

async function togglePayment(index: number) {
  const payment = data.payments[index]
  if (!payment || saving.value) return
  saving.value = true
  loadErrors.payments = ''
  try {
    if (payment.active) await paymentMethodApi.deactivate(payment.id)
    else await paymentMethodApi.activate(payment.id)
    await loadSection('payments')
  } catch (error) {
    loadErrors.payments = error instanceof Error ? error.message : 'เปลี่ยนวิธีชำระเงินไม่สำเร็จ'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="settings-page">
    <StaffHeader />
    <main class="settings-main">
      <div class="settings-page-heading">
        <h1>การตั้งค่า</h1>
        <p>จัดการข้อมูลสาขา ผู้ใช้งาน สิทธิ์การเข้าถึง และการตั้งค่าระบบ</p>
      </div>
      <div class="settings-layout">
        <nav class="settings-nav" aria-label="หมวดการตั้งค่า">
          <p>หมวดการตั้งค่า</p>
          <button
            v-for="item in sections"
            :key="item.key"
            type="button"
            :class="{ active: section === item.key }"
            @click="selectSection(item.key)"
          >
            {{ item.label }}
          </button>
        </nav>
        <section class="settings-panel">
          <header class="settings-head">
            <div>
              <h2>{{ currentSection.heading }}</h2>
              <p>{{ currentSection.description }}</p>
            </div>
            <button
              v-if="currentSection.action"
              class="settings-primary"
              type="button"
              :disabled="loading || saving"
              @click="openEditor()"
            >
              {{ currentSection.action }}
            </button>
          </header>

          <p v-if="loadErrors[section]" class="settings-error" role="alert">
            โหลดข้อมูลไม่สำเร็จ: {{ loadErrors[section] }}
          </p>
          <p v-if="loading" class="settings-loading" role="status">กำลังโหลดข้อมูล...</p>

          <div v-if="section === 'payments'" class="setup-cards">
            <button
              v-for="(item, index) in data.payments"
              :key="item.id"
              type="button"
              class="payment-card"
              :class="{ 'payment-selected': item.active }"
              :aria-pressed="item.active"
              :disabled="loading || saving"
              @click="togglePayment(index)"
            >
              <span class="payment-copy">
                <b>{{ item.name }}</b>
                <small>{{ item.code }}</small>
                <small class="payment-status">{{ item.active ? 'เปิดใช้งาน' : 'ปิดใช้งาน' }}</small>
              </span>
              <span class="payment-select" aria-hidden="true">{{ item.active ? '✓' : '' }}</span>
            </button>
            <p v-if="!loading && !loadErrors.payments && data.payments.length === 0">
              ยังไม่มีวิธีชำระเงินในระบบ
            </p>
          </div>
          <div v-else class="settings-table-scroll" :class="'settings-table-scroll--' + section">
            <table class="staff-table">
              <thead>
                <tr>
                  <th v-for="column in columns" :key="column" scope="col">{{ column }}</th>
                  <th scope="col">สถานะ</th>
                  <th scope="col">การจัดการ</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in rows" :key="row.index">
                  <td v-for="(cell, cellIndex) in row.cells" :key="cellIndex">{{ cell || '—' }}</td>
                  <td>
                    <span class="status" :class="{ void: row.status === 'ปิดใช้งาน' }">{{
                      row.status
                    }}</span>
                  </td>
                  <td>
                    <button class="secondary" type="button" @click="openEditor(row.index)">
                      แก้ไข
                    </button>
                  </td>
                </tr>
                <tr v-if="!loading && !loadErrors[section] && rows.length === 0">
                  <td :colspan="columns.length + 2">ยังไม่มีข้อมูลในหมวดนี้</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </main>

    <div v-if="editorOpen" class="settings-modal-backdrop" @click.self="editorOpen = false">
      <aside class="settings-modal" role="dialog" aria-modal="true" :aria-label="drawerTitle">
        <header>
          <h2>{{ drawerTitle }}</h2>
          <button type="button" aria-label="ปิด" @click="editorOpen = false">×</button>
        </header>
        <template v-if="section === 'branches'">
          <label
            >รหัสสาขา<input v-model="form.branchDisplayCode" maxlength="4" placeholder="เช่น BR01"
          /></label>
          <label>ชื่อสาขา<input v-model="form.name" placeholder="ระบุชื่อสาขา" /></label>
          <label
            >ข้อมูลติดต่อ<input
              class="settings-visual-only"
              placeholder="เบอร์โทรศัพท์สาขา"
              title="ข้อมูลติดต่อยังไม่มีใน API สาขา"
              disabled
          /></label>
        </template>
        <template v-else-if="section === 'users'">
          <label>ชื่อผู้ใช้งาน<input v-model="form.name" placeholder="ระบุชื่อผู้ใช้งาน" /></label>
          <label
            >Role<select v-model="form.role">
              <option value="">เลือกบทบาท</option>
              <option value="MANAGER">Manager</option>
              <option value="THERAPIST">Therapist</option>
            </select></label
          >
          <label
            >สาขาที่เข้า<select v-model="form.branch">
              <option value="">ทุกสาขา</option>
              <option
                v-for="branch in data.branches.filter((branch) => branch.active)"
                :key="branch.code"
                :value="branch.code"
              >
                {{ branch.name }}
              </option>
            </select></label
          >
          <fieldset class="settings-permissions" disabled>
            <legend>สิทธิ์การเข้าถึง</legend>
            <label v-for="permission in permissionLabels" :key="permission">
              <input type="checkbox" />
              <span>{{ permission }}</span>
            </label>
          </fieldset>
          <div class="settings-form-divider">ข้อมูลบัญชีสำหรับระบบ</div>
          <label>Username<input v-model="form.username" placeholder="ชื่อเข้าระบบ" /></label>
          <label
            >อีเมล<input v-model="form.email" type="email" placeholder="อีเมล (ถ้ามี)"
          /></label>
          <label
            >รหัสพนักงาน<input v-model="form.employeeCode" placeholder="ระบุรหัสพนักงาน"
          /></label>
          <label
            >เบอร์โทรศัพท์<input v-model="form.phone" placeholder="เบอร์โทรศัพท์ (ถ้ามี)"
          /></label>
          <label
            >รหัสผ่าน<input
              v-model="form.password"
              type="password"
              :placeholder="
                editingIndex === null ? 'อย่างน้อย 8 ตัวอักษร' : 'เว้นว่างหากไม่เปลี่ยนรหัสผ่าน'
              "
          /></label>
        </template>
        <template v-else-if="section === 'services'">
          <label
            >{{ form.type === 'course' ? 'ชื่อคอร์ส' : 'ชื่อบริการ'
            }}<input v-model="form.name" placeholder="ระบุชื่อคอร์สหรือบริการ"
          /></label>
          <label
            >ประเภท<select v-model="form.type" :disabled="editingIndex !== null">
              <option value="">เลือกประเภท</option>
              <option value="service">Assessment / Service</option>
              <option value="course">Course</option>
            </select></label
          >
          <label
            >ราคา (บาท)<input v-model="form.price" type="number" min="0" placeholder="ระบุราคา"
          /></label>
          <label
            >จำนวนครั้ง<input
              v-model="form.sessions"
              type="number"
              min="1"
              placeholder="ระบุจำนวนครั้ง"
              :disabled="form.type === 'service'"
          /></label>
          <div class="settings-form-divider">ข้อมูลเพิ่มเติมสำหรับระบบ</div>
          <label
            >รหัสรายการ<input v-model="form.code" placeholder="ระบุรหัสบริการหรือคอร์ส"
          /></label>
          <label v-if="form.type === 'service'"
            >ระยะเวลา (นาที)<input v-model="form.durationMinutes" type="number" min="1"
          /></label>
          <template v-if="form.type === 'course'">
            <label
              >จำนวนครั้งโบนัส<input v-model="form.bonusSessions" type="number" min="0"
            /></label>
            <label
              >อายุคอร์ส (วัน)<input v-model="form.validityDays" type="number" min="1"
            /></label>
          </template>
        </template>
        <template v-else-if="section === 'rooms'">
          <label
            >ชื่อห้อง/ทรัพยากร<input
              v-model="form.name"
              placeholder="ระบุชื่อห้อง เช่น ห้องรักษา 3"
          /></label>
          <label
            >สาขา<select v-model="form.branch" :disabled="editingIndex !== null">
              <option value="">เลือกสาขา</option>
              <option
                v-for="branch in data.branches.filter((branch) => branch.active)"
                :key="branch.code"
                :value="branch.code"
              >
                {{ branch.name }}
              </option>
            </select></label
          >
          <label
            >ประเภท<select v-model="form.type">
              <option value="ROOM">Treatment Room</option>
              <option value="EQUIPMENT">Resource</option>
            </select></label
          >
          <label
            >ความจุ (คน)<input
              class="settings-visual-only"
              placeholder="ระบุจำนวนคนสูงสุด"
              title="API ห้องยังไม่มีข้อมูลความจุ"
              disabled
          /></label>
          <label
            >สถานะห้อง/ทรัพยากร<select v-model="form.resourceStatus">
              <option value="AVAILABLE">พร้อมใช้งาน</option>
              <option value="IN_USE">กำลังใช้งาน</option>
              <option value="MAINTENANCE">ซ่อมบำรุง</option>
            </select></label
          >
          <div class="settings-form-divider">ข้อมูลเพิ่มเติมสำหรับระบบ</div>
          <label
            >รหัสห้อง/ทรัพยากร<input v-model="form.code" placeholder="ระบุรหัสห้อง/ทรัพยากร"
          /></label>
        </template>
        <template v-else-if="section === 'commission'">
          <label>ชื่อเกณฑ์คอมมิชชั่น<input v-model="form.name" placeholder="ระบุชื่อกฎ" /></label>
          <label
            >ผู้ได้รับค่าคอมมิชชั่น<input
              class="settings-visual-only"
              placeholder="เลือกผู้ได้รับ"
              title="API เกณฑ์คอมมิชชั่นยังไม่มีช่องผู้ได้รับ"
              disabled
          /></label>
          <label
            >วิธีคำนวณ<input
              class="settings-visual-only"
              :value="
                form.tiers
                  .map((tier) => (tier.rate ? `${tier.rate}%` : ''))
                  .filter(Boolean)
                  .join(', ')
              "
              placeholder="เช่น 300 บาท/ครั้ง หรือ 5% ของยอดขาย"
              title="API เกณฑ์คอมมิชชั่นใช้ช่วงอัตราแทนวิธีคำนวณในแบบ"
              disabled
          /></label>
          <div class="settings-form-divider">ข้อมูลเพิ่มเติมสำหรับระบบ</div>
          <label>เวอร์ชัน<input v-model="form.version" placeholder="เช่น 1.0" /></label>
          <label>วันที่เริ่มใช้<input v-model="form.effectiveFrom" type="date" /></label>
          <label>วันที่สิ้นสุด (ถ้ามี)<input v-model="form.effectiveTo" type="date" /></label>
          <div v-for="(tier, tierIndex) in form.tiers" :key="tierIndex" class="commission-tier">
            <strong>ช่วงที่ {{ tierIndex + 1 }}</strong>
            <label>ยอดขั้นต่ำ<input v-model="tier.minimum" type="number" min="0" /></label>
            <label
              >ยอดสูงสุด (เว้นว่างหากไม่จำกัด)<input v-model="tier.maximum" type="number" min="0"
            /></label>
            <label
              >อัตราคอมมิชชั่น (%)<input v-model="tier.rate" type="number" min="0" step="0.01"
            /></label>
            <button
              v-if="form.tiers.length > 1"
              type="button"
              class="settings-cancel"
              @click="form.tiers.splice(tierIndex, 1)"
            >
              ลบช่วงนี้
            </button>
          </div>
          <button
            type="button"
            class="settings-cancel"
            @click="form.tiers.push({ minimum: '0', maximum: '', rate: '' })"
          >
            + เพิ่มช่วง
          </button>
        </template>
        <template v-else-if="section === 'master'">
          <label
            >ประเภทข้อมูล<input
              v-model="form.category"
              list="settings-master-categories"
              placeholder="เลือกหรือระบุประเภทข้อมูล"
          /></label>
          <datalist id="settings-master-categories">
            <option
              v-for="category in [...new Set(data.master.map((item) => item.category))]"
              :key="category"
              :value="category"
            />
          </datalist>
          <label
            >ตัวอย่างรายการ<input
              v-model="form.name"
              placeholder="ระบุรายการตัวอย่าง คั่นด้วยจุลภาค"
          /></label>
        </template>
        <label
          >{{ section === 'rooms' ? 'การเปิดใช้งาน' : 'สถานะ' }}
          <select v-model="form.active" :disabled="statusCannotReactivate">
            <option :value="true">ใช้งาน</option>
            <option :value="false">ปิดใช้งาน</option>
          </select>
        </label>
        <p v-if="statusCannotReactivate" class="settings-form-note">
          รายการนี้ปิดใช้งานแล้ว และ backend ยังไม่รองรับการเปิดใช้งานกลับ
        </p>
        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
        <footer>
          <button class="settings-primary" type="button" :disabled="saving" @click="save">
            {{ saving ? 'กำลังบันทึก...' : 'บันทึก' }}
          </button>
        </footer>
      </aside>
    </div>
  </div>
</template>
<style scoped src="../styles/back-office.css"></style>
