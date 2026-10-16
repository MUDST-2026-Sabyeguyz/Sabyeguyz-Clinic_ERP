<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'

type SectionKey = 'branches' | 'users' | 'services' | 'payments' | 'rooms' | 'commission' | 'master'
type Row = Record<string, any>
const sections: Array<{ key: SectionKey; label: string }> = [
  { key: 'branches', label: 'สาขา' },
  { key: 'users', label: 'ผู้ใช้และสิทธิ์' },
  { key: 'services', label: 'บริการและคอร์ส' },
  { key: 'payments', label: 'การชำระเงิน' },
  { key: 'rooms', label: 'Room / Resource' },
  { key: 'commission', label: 'Commission Rule' },
  { key: 'master', label: 'Master Data' },
]
const route = useRoute()
const initialSection = sections.find((item) => item.key === route.query.section)?.key ?? 'branches'
const section = ref<SectionKey>(initialSection),
  drawerOpen = ref(false),
  editingIndex = ref<number | null>(null),
  savedMessage = ref(''),
  saveModalOpen = ref(false)
const permissions = ['Patient', 'Visit', 'Treatment', 'Course', 'Transaction', 'Commission']
const selectedPermissions = ref<string[]>(['Treatment'])
const fieldErrors = reactive<Record<string, boolean>>({})
const validationMessage = ref('')
const option = (value: string) => ({ value, label: value })
const statusOptions = computed(() => {
  if (section.value === 'rooms') return [option('พร้อมใช้งาน'), option('ไม่พร้อมใช้งาน')]
  if (section.value === 'commission') return [option('เปิดใช้งาน'), option('ปิดใช้งาน')]
  return [option('ใช้งาน'), option('ปิดใช้งาน')]
})
const data = reactive<Record<Exclude<SectionKey, 'payments'>, Row[]>>({
  branches: [],
  users: [],
  services: [],
  rooms: [],
  commission: [],
  master: [],
})
const paymentMethods = reactive<Array<{ name: string; note: string; active: boolean }>>([])
const copy = computed(
  () =>
    ({
      branches: ['จัดการสาขา', 'ข้อมูลสาขาทั้งหมดในระบบ'],
      users: ['ผู้ใช้งานและสิทธิ์', 'จัดการผู้ใช้งานและสิทธิ์การเข้าถึงระบบ'],
      services: ['บริการและคอร์ส', 'จัดการรายการบริการและคอร์สทั้งหมดในระบบ'],
      payments: ['การชำระเงิน', 'เลือกวิธีการชำระเงินที่เปิดให้บริการในระบบ'],
      rooms: ['Room / Resource', 'จัดการห้องและทรัพยากรที่ใช้ในการให้บริการ'],
      commission: ['Commission Rule', 'จัดการกฎเกณฑ์การคำนวณค่าคอมมิชชันในระบบ'],
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
    commission: ['ชื่อกฎ', 'ผู้ได้รับค่าคอมมิชชัน', 'วิธีคำนวณ', 'สถานะ'],
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
    commission: ['name', 'recipient', 'calculation', 'status'],
    master: ['category', 'count', 'examples', 'status'],
  }
  return values[section.value]
})
const form = reactive<Row>({})
type CommissionTier = { minimum: string; maximum: string; rate: string }
const commissionTiers = ref<CommissionTier[]>([])
const defaultTiers = (): CommissionTier[] => [
  { minimum: '0', maximum: '49,999', rate: '4%' },
  { minimum: '50,000', maximum: '79,999', rate: '6%' },
  { minimum: '80,000', maximum: '119,999', rate: '8%' },
  { minimum: '120,000', maximum: 'ไม่จำกัด', rate: '12%' },
]
function changeCompensationType() {
  form.rateFormat = form.compensationType === 'course' ? 'เปอร์เซ็นต์ (%)' : 'บาท / ครั้ง'
  form.basis =
    form.compensationType === 'course' ? 'ยอดขายคอร์สหลังส่วนลดรายการ' : 'จำนวนครั้งที่ให้บริการ'
}
function defaults() {
  if (section.value === 'branches')
    Object.assign(form, { code: '', name: '', contact: '', address: '', status: 'ใช้งาน' })
  if (section.value === 'users')
    Object.assign(form, {
      name: '',
      firstName: '',
      lastName: '',
      email: '',
      username: '',
      password: '',
      role: 'Therapist',
      branch: 'แบริ่ง',
      permission: '',
      status: 'ใช้งาน',
    })
  if (section.value === 'services')
    Object.assign(form, {
      name: '',
      type: 'Single Visit',
      price: '',
      sessions: '1 ครั้ง',
      unit: 'ครั้ง',
      expiry: '365 วัน',
      status: 'ใช้งาน',
    })
  if (section.value === 'rooms')
    Object.assign(form, {
      name: '',
      branch: 'แบริ่ง',
      type: 'Treatment Room',
      capacity: 1,
      status: 'พร้อมใช้งาน',
    })
  if (section.value === 'commission')
    Object.assign(form, {
      name: '',
      schemeName: '',
      recipient: 'รายพนักงาน',
      calculation: '',
      compensationType: 'course',
      basis: 'ยอดขายคอร์สหลังส่วนลดรายการ',
      rateFormat: 'เปอร์เซ็นต์ (%)',
      calculationMethod: '',
      startDate: '2026-01-01',
      endDate: '2026-12-31',
      amount: '300',
      status: 'เปิดใช้งาน',
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
  if (section.value === 'users') {
    const [firstName = '', ...lastName] = String(form.name ?? '')
      .trim()
      .split(/\s+/)
    form.firstName = form.firstName ?? firstName
    form.lastName = form.lastName ?? lastName.join(' ')
    form.password = ''
  }
  if (section.value === 'commission') {
    commissionTiers.value = form.tiers
      ? form.tiers.map((tier: CommissionTier) => ({ ...tier }))
      : defaultTiers()
  }
  selectedPermissions.value = String(form.permission ?? '')
    .split(/[,\s]+/)
    .filter(Boolean)
  drawerOpen.value = true
}
function save() {
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
  validationMessage.value = ''
  const required: Record<Exclude<SectionKey, 'payments'>, string[]> = {
    branches: ['code', 'name', 'contact'],
    users: ['firstName', 'lastName', 'email', 'username', 'role', 'branch'],
    services: ['name', 'type', 'price', 'unit', 'sessions', 'expiry'],
    rooms: ['name', 'branch', 'type', 'capacity'],
    commission: [
      'schemeName',
      'recipient',
      'basis',
      'rateFormat',
      'calculationMethod',
      'startDate',
    ],
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
  if (section.value === 'users' && !selectedPermissions.value.length) fieldErrors.permission = true
  if (section.value === 'users') {
    if (!fieldErrors.email)
      fieldErrors.email = !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(form.email))
    if (editingIndex.value === null && !String(form.password ?? '').trim())
      fieldErrors.password = true
  }
  if (section.value === 'commission') {
    if (form.endDate && form.endDate < form.startDate) {
      validationMessage.value = 'วันที่สิ้นสุดต้องไม่ก่อนวันที่เริ่มมีผล'
      return
    }
    const number = (value: string) => Number(value.replace(/[,\s%]/g, ''))
    if (form.compensationType === 'course') {
      let next = 0
      for (const [index, tier] of commissionTiers.value.entries()) {
        const minimum = number(tier.minimum)
        const unlimited = tier.maximum.trim() === 'ไม่จำกัด'
        const maximum = unlimited ? Infinity : number(tier.maximum)
        const rate = number(tier.rate)
        if (
          !tier.minimum.trim() ||
          !tier.maximum.trim() ||
          !tier.rate.trim() ||
          !Number.isFinite(minimum) ||
          minimum !== next ||
          maximum < minimum ||
          Number.isNaN(maximum) ||
          !Number.isFinite(rate) ||
          rate <= 0 ||
          rate > 100 ||
          (unlimited && index !== commissionTiers.value.length - 1)
        ) {
          validationMessage.value =
            'กรุณากำหนด Tier ให้ต่อเนื่อง ไม่ซ้อนทับ และอัตรามากกว่า 0 ถึง 100%'
          return
        }
        next = maximum + 1
      }
    } else if (
      !String(form.amount).trim() ||
      !Number.isFinite(number(String(form.amount))) ||
      number(String(form.amount)) <= 0
    ) {
      validationMessage.value = 'กรุณาระบุค่าตอบแทนต่อครั้งให้มากกว่า 0'
      return
    }
  }
  if (Object.values(fieldErrors).some(Boolean)) {
    validationMessage.value = fieldErrors.contact
      ? 'กรุณากรอกเบอร์โทรศัพท์ให้ครบ 9 หรือ 10 หลัก'
      : 'กรุณากรอกข้อมูลให้ครบก่อนบันทึก'
    return
  }
  if (section.value === 'master') {
    form.count = String(form.examples)
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean).length
  }
  if (section.value === 'users') {
    form.permission = selectedPermissions.value.join(', ')
    form.name = `${String(form.firstName).trim()} ${String(form.lastName).trim()}`
    delete form.password
  }
  if (section.value === 'commission') {
    form.tiers = commissionTiers.value.map((tier) => ({ ...tier }))
    form.calculation =
      form.compensationType === 'course'
        ? `Tier ${commissionTiers.value.map((t) => `${t.rate.replace(/%/g, '').trim()}%`).join(' / ')}`
        : `${form.amount} บาท / ครั้ง`
  }
  const rows = data[section.value as Exclude<SectionKey, 'payments'>]
  if (editingIndex.value === null) rows.push({ ...form })
  else rows[editingIndex.value] = { ...form }
  drawerOpen.value = false
  saveModalOpen.value = true
  window.setTimeout(() => {
    saveModalOpen.value = false
  }, 1800)
}
const drawerTitle = computed(() => {
  const labels: Record<SectionKey, string> = {
    branches: 'สาขา',
    users: 'ผู้ใช้และสิทธิ์',
    services: 'คอร์ส',
    payments: 'การชำระเงิน',
    rooms: 'ห้อง/ทรัพยากร',
    commission: form.compensationType === 'course' ? 'Commission Tier' : 'Commission Rule',
    master: 'Master Data',
  }
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
          </header>
          <div v-if="section === 'payments'" class="payment-list">
            <button
              v-for="method in paymentMethods"
              :key="method.name"
              class="payment-option"
              :class="{ selected: method.active }"
              disabled
            >
              <span class="radio" aria-hidden="true"></span
              ><span
                ><span class="payment-title"
                  ><b>{{ method.name }}</b
                  ><span class="payment-unavailable">ยังไม่เปิดให้บริการ</span></span
                ><small>{{ method.note }}</small></span
              ><i v-if="method.active">✓</i>
            </button>
          </div>
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
                          String(row[key]).startsWith('ปิด') ||
                          String(row[key]).startsWith('ไม่พร้อม'),
                      }"
                      >{{ row[key] }}</span
                    ><template v-else-if="key === 'permission'">{{
                      String(row[key] ?? '')
                        .split(/[,\s]+/)
                        .filter(Boolean)
                        .join(', ')
                    }}</template
                    ><template v-else>{{ row[key] }}</template>
                  </td>
                  <td><button class="edit" @click="openDrawer(index)">แก้ไข</button></td>
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
    <div v-if="saveModalOpen" class="save-success-backdrop" @click="saveModalOpen = false">
      <div class="save-success-modal" role="status">บันทึกเรียบร้อยแล้ว</div>
    </div>
    <div v-if="drawerOpen" class="drawer-backdrop" @click.self="drawerOpen = false">
      <aside class="drawer" :class="{ 'commission-drawer': section === 'commission' }">
        <header>
          <h2>{{ drawerTitle }}</h2>
          <button aria-label="ปิด" @click="drawerOpen = false">×</button>
        </header>
        <template v-if="section === 'branches'"
          ><label
            >รหัสสาขา<input
              v-model="form.code"
              :class="{ invalid: fieldErrors.code }"
              placeholder="เช่น BR03" /></label
          ><label
            >ชื่อสาขา<input
              v-model="form.name"
              :class="{ invalid: fieldErrors.name }"
              placeholder="ระบุชื่อสาขา" /></label
          ><label
            >ข้อมูลติดต่อ<input
              v-model="form.contact"
              type="tel"
              inputmode="numeric"
              maxlength="12"
              :class="{ invalid: fieldErrors.contact }"
              placeholder="เบอร์โทรศัพท์สาขา" /></label
          ><label>ที่อยู่<input v-model="form.address" placeholder="ระบุที่อยู่สาขา" /></label
        ></template>
        <template v-else-if="section === 'users'"
          ><div class="user-name-fields">
            <label
              >ชื่อ<input
                v-model="form.firstName"
                :class="{ invalid: fieldErrors.firstName }"
                placeholder="ระบุชื่อ"
                autocomplete="given-name"
            /></label>
            <label
              >นามสกุล<input
                v-model="form.lastName"
                :class="{ invalid: fieldErrors.lastName }"
                placeholder="ระบุนามสกุล"
                autocomplete="family-name"
            /></label>
          </div>
          <label
            >Email<input
              v-model="form.email"
              type="email"
              :class="{ invalid: fieldErrors.email }"
              placeholder="ระบุ email ของผู้ใช้งาน"
              autocomplete="email" /></label
          ><label
            >username<input
              v-model="form.username"
              :class="{ invalid: fieldErrors.username }"
              placeholder="ระบุ username"
              autocomplete="off" /></label
          ><label
            >รหัสผ่าน<input
              v-model="form.password"
              type="password"
              :class="{ invalid: fieldErrors.password }"
              placeholder="ระบุรหัสผ่าน"
              autocomplete="new-password" /></label
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
            >สาขา<FilterDropdown
              v-model="form.branch"
              :options="[option('แบริ่ง'), option('พระราม 9')]"
              default-value="แบริ่ง"
              default-label="แบริ่ง"
              label="สาขาที่เข้า"
              hide-default-option
              :clear-on-reselect="false"
          /></label>
          <fieldset :class="{ 'invalid-fieldset': fieldErrors.permission }">
            <legend>สิทธิ์การเข้าถึง</legend>
            <label v-for="permission in permissions" :key="permission" class="check"
              ><input v-model="selectedPermissions" type="checkbox" :value="permission" />{{
                permission
              }}</label
            >
          </fieldset></template
        >
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
          ><label
            >หน่วย<input
              v-model="form.unit"
              :class="{ invalid: fieldErrors.unit }"
              placeholder="เช่น ครั้ง" /></label
          ><label
            >จำนวน<input
              v-model="form.sessions"
              :class="{ invalid: fieldErrors.sessions }"
              placeholder="ระบุจำนวนครั้ง" /></label
          ><label
            >วันหมดอายุ<input
              v-model="form.expiry"
              :class="{ invalid: fieldErrors.expiry }"
              placeholder="เช่น 365 วัน" /></label
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
              :options="[option('แบริ่ง'), option('พระราม 9')]"
              default-value="แบริ่ง"
              default-label="แบริ่ง"
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
          ><label
            >ความจุ (คน)<input
              v-model="form.capacity"
              :class="{ invalid: fieldErrors.capacity }"
              type="number"
              min="1" /></label
        ></template>
        <template v-else-if="section === 'commission'">
          <fieldset class="compensation-types">
            <legend>ประเภทค่าตอบแทน</legend>
            <label
              ><input
                v-model="form.compensationType"
                type="radio"
                value="course"
                @change="changeCompensationType"
              />คอมมิชชันการขายคอร์ส</label
            >
            <label
              ><input
                v-model="form.compensationType"
                type="radio"
                value="service"
                @change="changeCompensationType"
              />บริการรายครั้ง</label
            >
          </fieldset>
          <label
            >ชื่อ Scheme<input
              v-model="form.schemeName"
              :class="{ invalid: fieldErrors.schemeName }"
              placeholder="ระบุชื่อ Scheme"
          /></label>
          <label
            >ผู้ได้รับค่าคอมมิชชัน<FilterDropdown
              v-model="form.recipient"
              :options="[option('นักกายภาพบำบัด'), option('รายพนักงาน')]"
              label="ผู้ได้รับค่าคอมมิชชัน"
              default-label="เลือกผู้ได้รับค่าคอมมิชชัน"
              hide-default-option
              :clear-on-reselect="false"
          /></label>
          <label
            >ฐานคำนวณ<input v-model="form.basis" :class="{ invalid: fieldErrors.basis }"
          /></label>
          <label>รูปแบบอัตรา<input v-model="form.rateFormat" readonly /></label>
          <label
            >วิธีคำนวณ<input
              v-model="form.calculationMethod"
              :class="{ invalid: fieldErrors.calculationMethod }"
              placeholder="วิธีคำนวณ"
          /></label>
          <div v-if="form.compensationType === 'course'" class="commission-tier-fields">
            <div class="commission-tier-head">
              <span>ยอดขายขั้นต่ำ</span><span>ยอดขายสูงสุด</span><span>อัตรา (%)</span>
            </div>
            <div v-for="(tier, index) in commissionTiers" :key="index" class="commission-tier-row">
              <input
                v-model="tier.minimum"
                :aria-label="`Tier ${index + 1} ยอดขายขั้นต่ำ`"
                inputmode="decimal"
              />
              <input v-model="tier.maximum" :aria-label="`Tier ${index + 1} ยอดขายสูงสุด`" />
              <input v-model="tier.rate" :aria-label="`Tier ${index + 1} อัตรา`" />
            </div>
          </div>
          <label v-else
            >ค่าตอบแทนต่อครั้ง (บาท)<input v-model="form.amount" inputmode="decimal"
          /></label>
          <div class="commission-dates">
            <label
              >วันที่เริ่มมีผล<ThaiDatePicker v-model="form.startDate" show-calendar-icon
            /></label>
            <label>วันที่สิ้นสุด<ThaiDatePicker v-model="form.endDate" show-calendar-icon /></label>
          </div>
        </template>
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
              placeholder="ระบุรายการตัวอย่าง คั่นด้วยจุลภาค" /></label
        ></template>
        <label
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
