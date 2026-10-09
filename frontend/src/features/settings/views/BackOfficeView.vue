<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'

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
const section = ref<SectionKey>('branches'),
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
function defaults() {
  if (section.value === 'branches')
    Object.assign(form, { code: '', name: '', contact: '', status: 'ใช้งาน' })
  if (section.value === 'users')
    Object.assign(form, {
      name: '',
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
      recipient: 'นักกายภาพบำบัด',
      calculation: '',
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
  selectedPermissions.value = String(form.permission ?? '')
    .split(' ')
    .filter(Boolean)
  drawerOpen.value = true
}
function save() {
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
  validationMessage.value = ''
  const required: Record<Exclude<SectionKey, 'payments'>, string[]> = {
    branches: ['code', 'name', 'contact'],
    users: ['name', 'role', 'branch'],
    services: ['name', 'type', 'price', 'sessions'],
    rooms: ['name', 'branch', 'type', 'capacity'],
    commission: ['name', 'recipient', 'calculation'],
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
  if (section.value === 'users') form.permission = selectedPermissions.value.join(' ')
  const rows = data[section.value as Exclude<SectionKey, 'payments'>]
  if (editingIndex.value === null) rows.push({ ...form })
  else rows[editingIndex.value] = { ...form }
  drawerOpen.value = false
  saveModalOpen.value = true
  window.setTimeout(() => {
    saveModalOpen.value = false
  }, 1800)
}
function notify(message: string) {
  savedMessage.value = message
  window.setTimeout(() => {
    savedMessage.value = ''
  }, 2200)
}
function togglePayment(index: number) {
  const method = paymentMethods[index]
  if (!method) return
  method.active = !method.active
  notify(`บันทึก ${method.name} อัตโนมัติแล้ว`)
}
const drawerTitle = computed(() => {
  const labels: Record<SectionKey, string> = {
    branches: 'สาขา',
    users: 'ผู้ใช้และสิทธิ์',
    services: 'คอร์ส',
    payments: 'การชำระเงิน',
    rooms: 'ห้อง/ทรัพยากร',
    commission: 'เกณฑ์คอมมิชชัน',
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
            <p v-else-if="section === 'payments'" class="auto-save-note">
              {{ savedMessage || 'การเปลี่ยนแปลงจะบันทึกอัตโนมัติ' }}
            </p>
          </header>
          <div v-if="section === 'payments'" class="payment-list">
            <button
              v-for="(method, index) in paymentMethods"
              :key="method.name"
              class="payment-option"
              :class="{ selected: method.active }"
              @click="togglePayment(index)"
            >
              <span class="radio" aria-hidden="true"></span
              ><span
                ><b>{{ method.name }}</b
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
        ></template>
        <template v-else-if="section === 'users'"
          ><label
            >ชื่อผู้ใช้งาน<input
              v-model="form.name"
              :class="{ invalid: fieldErrors.name }"
              placeholder="ระบุชื่อผู้ใช้งาน" /></label
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
            >จำนวนครั้ง<input
              v-model="form.sessions"
              :class="{ invalid: fieldErrors.sessions }"
              placeholder="ระบุจำนวนครั้ง" /></label
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
        <template v-else-if="section === 'commission'"
          ><label
            >ชื่อเกณฑ์คอมมิชชัน<input
              v-model="form.name"
              :class="{ invalid: fieldErrors.name }" /></label
          ><label :class="{ 'invalid-field': fieldErrors.recipient }"
            >ผู้ได้รับค่าคอมมิชชัน<FilterDropdown
              v-model="form.recipient"
              :options="[option('นักกายภาพบำบัด'), option('รายพนักงาน')]"
              default-value="นักกายภาพบำบัด"
              default-label="นักกายภาพบำบัด"
              label="ผู้ได้รับค่าคอมมิชชัน"
              hide-default-option
              :clear-on-reselect="false" /></label
          ><label
            >วิธีคำนวณ<input
              v-model="form.calculation"
              :class="{ invalid: fieldErrors.calculation }" /></label
        ></template>
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
