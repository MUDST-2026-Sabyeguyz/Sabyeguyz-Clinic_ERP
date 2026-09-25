<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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
    action: '+ เพิ่มวิธีชำระเงิน',
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
    numericCode: '',
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
    sessions: '1',
    durationMinutes: '30',
    bonusSessions: '0',
    validityDays: '365',
    version: '',
    effectiveFrom: '',
    effectiveTo: '',
    tiers: [{ minimum: '0', maximum: '', rate: '' }],
    category: '',
    resourceStatus: 'AVAILABLE',
  }
}

const form = reactive(createEmptyForm())

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
      return ['รหัสสาขา', 'ชื่อสาขา', 'รหัสตัวเลข']
    case 'users':
      return ['ผู้ใช้งาน', 'บทบาท', 'สาขา', 'ชื่อเข้าระบบ']
    case 'services':
      return ['รายการ', 'ประเภท', 'ราคา', 'จำนวน/ระยะเวลา']
    case 'rooms':
      return ['ห้อง/ทรัพยากร', 'สาขา', 'ประเภท']
    case 'commission':
      return ['ชื่อกฎ', 'เวอร์ชัน', 'อัตราคอมมิชชั่น']
    case 'master':
      return ['ประเภทข้อมูล', 'รหัส', 'ชื่อรายการ']
    default:
      return []
  }
})
const rows = computed<DisplayRow[]>(() => {
  switch (section.value) {
    case 'branches':
      return data.branches.map((item, index) => ({
        cells: [item.code, item.name, item.numericCode],
        status: item.active ? 'ใช้งาน' : 'ปิดใช้งาน',
        index,
      }))
    case 'users':
      return data.users.map((item, index) => ({
        cells: [
          item.fullName,
          item.role === 'MANAGER' ? 'Manager' : 'Therapist',
          item.branchName ?? 'ทุกสาขา',
          item.username,
        ],
        status: item.active ? 'ใช้งาน' : 'ปิดใช้งาน',
        index,
      }))
    case 'services':
      return data.services.map((item, index) => ({
        cells: [
          item.name,
          item.kind === 'course' ? 'Course' : 'Service',
          '฿' + item.price.toLocaleString('th-TH'),
          item.kind === 'course' ? item.sessions + ' ครั้ง' : item.defaultDurationMinutes + ' นาที',
        ],
        status: item.active ? 'ใช้งาน' : 'ปิดใช้งาน',
        index,
      }))
    case 'rooms':
      return data.rooms.map((item, index) => ({
        cells: [item.name, item.branchName, item.type === 'ROOM' ? 'ห้องรักษา' : 'ทรัพยากร'],
        status: item.active ? (resourceStatusLabels[item.status] ?? item.status) : 'ปิดใช้งาน',
        index,
      }))
    case 'commission':
      return data.commission.map((item, index) => ({
        cells: [
          item.name,
          item.version,
          item.tiers.map((tier) => `${(tier.rate * 100).toLocaleString('th-TH')}%`).join(', '),
        ],
        status: item.active ? 'เปิดใช้งาน' : 'ปิดใช้งาน',
        index,
      }))
    case 'master':
      return data.master.map((item, index) => ({
        cells: [item.category, item.code, item.name],
        status: item.active ? 'ใช้งาน' : 'ปิดใช้งาน',
        index,
      }))
    default:
      return []
  }
})
const drawerTitle = computed(() => {
  const adding = editingIndex.value === null
  switch (section.value) {
    case 'branches':
      return adding ? 'เพิ่มสาขา' : 'แก้ไขสาขา'
    case 'payments':
      return 'เพิ่มวิธีชำระเงิน'
    case 'users':
      return adding ? 'สร้างผู้ใช้' : 'แก้ไขผู้ใช้'
    case 'services':
      return adding ? 'เพิ่มบริการ/คอร์ส' : 'แก้ไขบริการ/คอร์ส'
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
    switch (section.value) {
      case 'branches': {
        const item = data.branches[index]!
        Object.assign(form, { code: item.code, numericCode: item.numericCode, name: item.name })
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
        Object.assign(form, { category: item.category, code: item.code, name: item.name })
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
  saving.value = true
  try {
    switch (section.value) {
      case 'payments': {
        if (!code || !name) throw new Error('กรุณาระบุรหัสและชื่อวิธีชำระเงิน')
        await paymentMethodApi.create({ code, name })
        break
      }
      case 'branches': {
        if (!code || code.length > 2 || !/^\d{2}$/.test(form.numericCode) || !name)
          throw new Error('กรุณาระบุรหัสสาขาไม่เกิน 2 ตัว รหัสตัวเลข 2 หลัก และชื่อสาขา')
        const body = { code, numericCode: form.numericCode, name }
        if (current === null) await branchApi.create(body)
        else await branchApi.update(data.branches[current]!.id, body)
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
        if (current === null) await userManagementApi.create({ ...body, password: form.password })
        else await userManagementApi.update(data.users[current]!.id, body)
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
          if (existing) await courseApi.update(existing.id, body)
          else await courseApi.create(body)
        } else if (form.type === 'service') {
          const defaultDurationMinutes = Number(form.durationMinutes)
          if (!Number.isInteger(defaultDurationMinutes) || defaultDurationMinutes < 1)
            throw new Error('กรุณาระบุระยะเวลาบริการเป็นจำนวนนาที')
          const body = { code, name, price, defaultDurationMinutes }
          if (existing) await serviceManagementApi.update(existing.id, body)
          else await serviceManagementApi.create(body)
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
        if (current === null) await resourceApi.create(body)
        else await resourceApi.update(data.rooms[current]!.id, body)
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
        if (current === null) await commissionApi.createScheme(body)
        else await commissionApi.updateScheme(data.commission[current]!.id, body)
        break
      }
      case 'master': {
        if (!form.category.trim() || !code || !name)
          throw new Error('กรุณาระบุประเภท รหัส และชื่อรายการ')
        const body = { category: form.category.trim(), code, name }
        if (current === null) await masterDataApi.create(body)
        else await masterDataApi.update(data.master[current]!.id, body)
        break
      }
      default:
        return
    }
    await loadSection(section.value)
    editorOpen.value = false
  } catch (error) {
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
          <label>รหัสสาขา<input v-model="form.code" maxlength="2" placeholder="เช่น BR" /></label>
          <label>ชื่อสาขา<input v-model="form.name" placeholder="ระบุชื่อสาขา" /></label>
          <label
            >รหัสตัวเลข 2 หลัก<input
              v-model="form.numericCode"
              inputmode="numeric"
              maxlength="2"
              :readonly="editingIndex !== null"
              placeholder="เช่น 01"
          /></label>
        </template>
        <template v-else-if="section === 'users'">
          <label>ชื่อผู้ใช้งาน<input v-model="form.name" placeholder="ระบุชื่อผู้ใช้งาน" /></label>
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
          <p class="settings-form-note">สิทธิ์การเข้าถึงกำหนดตาม Role ของผู้ใช้</p>
        </template>
        <template v-else-if="section === 'services'">
          <label
            >รหัสรายการ<input v-model="form.code" placeholder="ระบุรหัสบริการหรือคอร์ส"
          /></label>
          <label
            >ชื่อรายการ<input v-model="form.name" placeholder="ระบุชื่อคอร์สหรือบริการ"
          /></label>
          <label
            >ประเภท<select v-model="form.type" :disabled="editingIndex !== null">
              <option value="">เลือกประเภท</option>
              <option value="service">บริการ</option>
              <option value="course">คอร์ส</option>
            </select></label
          >
          <label
            >ราคา (บาท)<input v-model="form.price" type="number" min="0" placeholder="ระบุราคา"
          /></label>
          <label v-if="form.type === 'service'"
            >ระยะเวลา (นาที)<input v-model="form.durationMinutes" type="number" min="1"
          /></label>
          <template v-if="form.type === 'course'">
            <label>จำนวนครั้ง<input v-model="form.sessions" type="number" min="1" /></label>
            <label
              >จำนวนครั้งโบนัส<input v-model="form.bonusSessions" type="number" min="0"
            /></label>
            <label
              >อายุคอร์ส (วัน)<input v-model="form.validityDays" type="number" min="1"
            /></label>
          </template>
        </template>
        <template v-else-if="section === 'payments'">
          <label>รหัสวิธีชำระเงิน<input v-model="form.code" placeholder="เช่น CASH" /></label>
          <label
            >ชื่อวิธีชำระเงิน<input v-model="form.name" placeholder="ระบุชื่อวิธีชำระเงิน"
          /></label>
        </template>
        <template v-else-if="section === 'rooms'">
          <label
            >รหัสห้อง/ทรัพยากร<input v-model="form.code" placeholder="ระบุรหัสห้อง/ทรัพยากร"
          /></label>
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
              <option value="ROOM">ห้องรักษา</option>
              <option value="EQUIPMENT">ทรัพยากร</option>
            </select></label
          >
          <label
            >สถานะ<select v-model="form.resourceStatus">
              <option value="AVAILABLE">พร้อมใช้งาน</option>
              <option value="IN_USE">กำลังใช้งาน</option>
              <option value="MAINTENANCE">ซ่อมบำรุง</option>
            </select></label
          >
        </template>
        <template v-else-if="section === 'commission'">
          <label>ชื่อเกณฑ์คอมมิชชั่น<input v-model="form.name" placeholder="ระบุชื่อกฎ" /></label>
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
            >ประเภทข้อมูล<input v-model="form.category" placeholder="เช่น CUSTOMER_GROUP"
          /></label>
          <label>รหัส<input v-model="form.code" placeholder="ระบุรหัสรายการ" /></label>
          <label>ชื่อรายการ<input v-model="form.name" placeholder="ระบุชื่อรายการ" /></label>
        </template>
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
