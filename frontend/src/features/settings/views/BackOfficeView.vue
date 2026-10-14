<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import {
  branchApi,
  commissionApi,
  courseApi,
  paymentMethodApi,
  resourceApi,
  masterDataApi,
  serviceManagementApi,
  userManagementApi,
} from '@/api/client'

type SectionKey = 'branches' | 'users' | 'services' | 'payments' | 'rooms' | 'commission' | 'master'
// Rows are edited generically from form fields of different shapes, so their values stay loosely typed.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
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
const fieldErrors = reactive<Record<string, boolean>>({})
const validationMessage = ref('')
const loadError = ref('')
const option = (value: string) => ({ value, label: value })
const statusOptions = computed(() => {
  if (section.value === 'rooms') return [option('พร้อมใช้งาน'), option('ไม่ว่าง'), option('ซ่อม')]
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
const paymentMethods = reactive<Array<{ id: number; name: string; note: string; active: boolean }>>(
  [],
)
const branchOptions = computed(() => data.branches.map((branch) => option(branch.name)))
const resourceStatuses: Record<string, string> = {
  AVAILABLE: 'พร้อมใช้งาน',
  IN_USE: 'ไม่ว่าง',
  MAINTENANCE: 'ซ่อม',
  ACTIVE: 'พร้อมใช้งาน',
  INACTIVE: 'ปิดใช้งาน',
}
const status = (active: boolean) => (active ? 'ใช้งาน' : 'ปิดใช้งาน')
const branchRow = (item: Awaited<ReturnType<typeof branchApi.create>>) => ({
  id: item.id,
  code: item.branchCode,
  name: item.name,
  address: item.address,
  contact: item.phone,
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
  sessions: String(item.defaultDurationMinutes),
  status: status(item.active),
})
const courseRow = (item: Awaited<ReturnType<typeof courseApi.create>>) => ({
  ...item,
  type: 'Course',
  sessions: String(item.sessions),
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
      ...payments.value.map((item) => ({ ...item, note: 'ช่องทางรับชำระเงิน' })),
    )
  if (resources.status === 'fulfilled')
    data.rooms.splice(0, data.rooms.length, ...resources.value.map(resourceRow))
  if (master.status === 'fulfilled')
    data.master.splice(0, data.master.length, ...master.value.map(masterRow))
  try {
    const schemes = (await commissionApi.schemes()) as Array<{
      id: number
      name: string
      active: boolean
    }>
    if (schemes[0]) {
      const scheme = schemes[0]
      const tiers = (await commissionApi.tiers(scheme.id)) as Array<{ order: number; rate: number }>
      data.commission.splice(
        0,
        data.commission.length,
        ...tiers.map((tier) => ({
          name: `${scheme.name} Tier ${tier.order}`,
          recipient: 'ผู้ขาย',
          calculation: `${tier.rate * 100}% ของยอดขาย`,
          status: scheme.active ? 'เปิดใช้งาน' : 'ปิดใช้งาน',
        })),
      )
    }
  } catch {
    failures.push('Commission Rule')
  }
  loadError.value = failures.length ? `ยังโหลดข้อมูลไม่ได้: ${failures.join(', ')}` : ''
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
const rolePermissions = computed(() =>
  form.role === 'Manager' ? permissions : ['Patient', 'Visit', 'Treatment'],
)
function defaults() {
  if (section.value === 'branches')
    Object.assign(form, { code: '', name: '', address: '', contact: '', status: 'ใช้งาน' })
  if (section.value === 'users')
    Object.assign(form, {
      name: '',
      role: 'Therapist',
      username: '',
      email: '',
      password: '',
      code: '',
      phone: '',
      branch: data.branches[0]?.name ?? '',
      permission: '',
      status: 'ใช้งาน',
    })
  if (section.value === 'services')
    Object.assign(form, {
      name: '',
      type: 'Single Visit',
      price: '',
      sessions: '30',
      bonusSessions: 0,
      validityDays: 365,
      status: 'ใช้งาน',
    })
  if (section.value === 'rooms')
    Object.assign(form, {
      name: '',
      branch: data.branches[0]?.name ?? '',
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
  drawerOpen.value = true
}
async function save() {
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
  validationMessage.value = ''
  const required: Record<Exclude<SectionKey, 'payments'>, string[]> = {
    branches: ['code', 'name', 'address', 'contact'],
    users: ['name', 'role', 'branch', 'username', 'code'],
    services: ['name', 'type', 'price', 'sessions'],
    rooms: ['name', 'branch', 'type'],
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
  if (section.value === 'branches') fieldErrors.code = !/^\d{2}$/.test(String(form.code))
  if (section.value === 'services') {
    fieldErrors.price = !Number.isFinite(Number(form.price)) || Number(form.price) < 0
    fieldErrors.sessions = !Number.isFinite(Number(form.sessions)) || Number(form.sessions) <= 0
  }
  if (Object.values(fieldErrors).some(Boolean)) {
    validationMessage.value = fieldErrors.contact
      ? 'กรุณากรอกเบอร์โทรศัพท์ให้ครบ 9 หรือ 10 หลัก'
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
      }
      record = branchRow(
        existing?.id ? await branchApi.update(existing.id, body) : await branchApi.create(body),
      )
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
          data.branches[0]?.code,
      }
      record = userRow(
        existing?.id
          ? await userManagementApi.update(existing.id, body)
          : await userManagementApi.create({ ...body, password: form.password }),
      )
    } else if (section.value === 'services') {
      const code = existing?.code || form.code || `SV-${Date.now()}`
      if (form.type === 'Course') {
        const body = {
          code,
          name: form.name,
          price: Number(form.price),
          sessions: Number(form.sessions),
          bonusSessions: Number(form.bonusSessions ?? 0),
          validityDays: Number(form.validityDays ?? 365),
        }
        record = courseRow(
          existing?.id ? await courseApi.update(existing.id, body) : await courseApi.create(body),
        )
      } else {
        const body = {
          code,
          name: form.name,
          price: Number(form.price),
          defaultDurationMinutes: Number(form.sessions),
        }
        record = serviceRow(
          existing?.id
            ? await serviceManagementApi.update(existing.id, body)
            : await serviceManagementApi.create(body),
        )
      }
    } else if (section.value === 'rooms') {
      const body = {
        branchCode: data.branches.find((branch) => branch.name === form.branch)?.code,
        code: existing?.code || form.code || `RES-${Date.now()}`,
        name: form.name,
        type: form.type === 'Treatment Room' ? 'ROOM' : 'EQUIPMENT',
        status:
          Object.keys(resourceStatuses).find((key) => resourceStatuses[key] === form.status) ??
          'AVAILABLE',
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
    } else {
      validationMessage.value = 'ยังไม่มี API สำหรับบันทึกเกณฑ์คอมมิชชัน'
      return
    }
    if (editingIndex.value === null) rows.push(record)
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
async function togglePayment(index: number) {
  const method = paymentMethods[index]
  if (!method || (method.active && !window.confirm(`ยืนยันการปิดใช้งาน ${method.name} หรือไม่?`)))
    return
  try {
    if (method.active) await paymentMethodApi.deactivate(method.id)
    else await paymentMethodApi.activate(method.id)
    method.active = !method.active
    notify(`บันทึก ${method.name} อัตโนมัติแล้ว`)
  } catch (error) {
    notify(error instanceof Error ? error.message : 'เปลี่ยนสถานะไม่สำเร็จ')
  }
}
async function toggleUser(user: Row) {
  const active = user.status === 'ใช้งาน'
  if (active && !window.confirm(`ยืนยันการปิดใช้งาน ${user.username} หรือไม่?`)) return
  try {
    if (active) await userManagementApi.deactivate(user.id)
    else await userManagementApi.activate(user.id)
    user.status = active ? 'ปิดใช้งาน' : 'ใช้งาน'
    notify('บันทึกสถานะผู้ใช้แล้ว')
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
          <p v-if="loadError" class="validation-message" role="alert">{{ loadError }}</p>
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
                  <td>
                    <button v-if="section !== 'commission'" class="edit" @click="openDrawer(index)">
                      แก้ไข</button
                    ><button
                      v-if="section === 'users'"
                      class="edit user-toggle"
                      @click="toggleUser(row)"
                    >
                      {{ row.status === 'ใช้งาน' ? 'ปิดใช้งาน' : 'เปิดใช้งาน' }}
                    </button>
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
              :default-value="data.branches[0]?.name ?? ''"
              :default-label="data.branches[0]?.name ?? 'เลือกสาขา'"
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
              :options="
                editingIndex === null
                  ? [option('Single Visit'), option('Course')]
                  : [option(form.type)]
              "
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
            >{{ form.type === 'Course' ? 'จำนวนครั้ง' : 'ระยะเวลา (นาที)'
            }}<input
              v-model="form.sessions"
              :class="{ invalid: fieldErrors.sessions }"
              placeholder="ระบุจำนวนครั้ง" /></label
          ><template v-if="form.type === 'Course'"
            ><label>Bonus<input v-model="form.bonusSessions" type="number" min="0" /></label
            ><label
              >อายุคอร์ส (วัน)<input
                v-model="form.validityDays"
                type="number"
                min="1" /></label></template
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
              :default-value="data.branches[0]?.name ?? ''"
              :default-label="data.branches[0]?.name ?? 'เลือกสาขา'"
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
              placeholder="ระบุชื่อรายการ" /></label
        ></template>
        <label v-if="section === 'rooms'"
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
