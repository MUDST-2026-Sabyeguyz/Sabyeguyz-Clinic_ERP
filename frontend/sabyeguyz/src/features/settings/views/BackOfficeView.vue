<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'

const section = ref('branches')
const editing = ref(false)
const editingIndex = ref(-1)
const saved = ref(false)
const formError = ref('')
const permissionOptions = ['Patient', 'Visit', 'Treatment', 'Course', 'Transaction']
const selectedPermissions = ref<string[]>(['Patient', 'Visit', 'Treatment'])
const branches = reactive([
  { code: 'RP01', name: 'สาขาราชพฤกษ์', detail: '02-123-4567', status: 'Active' },
  { code: 'SK02', name: 'สาขาสุขุมวิท', detail: '02-987-6543', status: 'Active' },
])
const users = reactive([
  { name: 'อรทัย ก.', role: 'Manager', branch: 'ทุกสาขา', permission: 'All', status: 'Active' },
  {
    name: 'กฤษดา น.',
    role: 'นักกายภาพบำบัด',
    branch: 'ราชพฤกษ์',
    permission: 'Patient, Visit, Treatment',
    status: 'Active',
  },
])
const services = reactive([
  {
    name: 'Initial Assessment',
    type: 'Assessment',
    price: '1200',
    detail: '1 ครั้ง',
    expiry: 'ไม่มีกำหนด',
    status: 'Active',
  },
  {
    name: 'Manual Therapy 10 ครั้ง',
    type: 'Course',
    price: '8500',
    detail: '10 + 2 Bonus',
    expiry: '12 เดือน',
    status: 'Active',
  },
])
const paymentMethods = reactive([
  { name: 'Cash', active: true },
  { name: 'Transfer', active: true },
  { name: 'QR', active: true },
  { name: 'Card', active: false },
])
const storedPayments = JSON.parse(localStorage.getItem('enabledPaymentMethods') || 'null') as
  string[] | null
if (storedPayments)
  paymentMethods.forEach((item) => (item.active = storedPayments.includes(item.name)))
else
  localStorage.setItem(
    'enabledPaymentMethods',
    JSON.stringify(paymentMethods.filter((item) => item.active).map((item) => item.name)),
  )
const rooms = reactive([
  { name: 'ห้องรักษา 1', branch: 'ราชพฤกษ์', type: 'ห้องรักษา', status: 'ว่าง' },
  { name: 'Shockwave Unit 1', branch: 'ราชพฤกษ์', type: 'ทรัพยากร', status: 'ไม่ว่าง' },
  { name: 'ห้องรักษา 3', branch: 'สุขุมวิท', type: 'ห้องรักษา', status: 'ซ่อม' },
])
const commissionRules = reactive([
  {
    name: 'ค่าคอมมิชชันการให้บริการ',
    type: 'นักกายภาพบำบัด',
    calculation: '300 บาทต่อครั้ง',
    calculationType: 'จำนวนเงินคงที่',
    value: '300',
    basis: 'ต่อครั้ง',
    effectiveFrom: '2026-08-01',
    status: 'Active',
  },
  {
    name: 'ค่าคอมมิชชันการขายคอร์ส',
    type: 'ผู้ขาย',
    calculation: '5% ของยอดขาย',
    calculationType: 'เปอร์เซ็นต์',
    value: '5',
    basis: 'ยอดขาย',
    effectiveFrom: '2026-08-01',
    status: 'Active',
  },
])
const masterData = reactive([
  { category: 'กลุ่มลูกค้า', name: 'ลูกค้าทั่วไป', status: 'Active' },
  { category: 'ช่องทางที่รู้จักคลินิก', name: 'Facebook', status: 'Active' },
  { category: 'บริษัทประกัน', name: 'AIA', status: 'Active' },
])
function restoreList<T>(key: string, target: T[]) {
  const stored = localStorage.getItem(key)
  if (stored) target.splice(0, target.length, ...(JSON.parse(stored) as T[]))
}
restoreList('clinicBranches', branches)
restoreList('clinicUsers', users)
restoreList('clinicServices', services)
restoreList('clinicRooms', rooms)
restoreList('clinicCommissionRules', commissionRules)
restoreList('clinicMasterData', masterData)
commissionRules.forEach((rule) => {
  if (rule.name === 'Treatment Standard' || rule.name === 'ค่าบริการต่อครั้ง')
    rule.name = 'ค่าคอมมิชชันการให้บริการ'
  if (rule.name === 'Course Sales') rule.name = 'ค่าคอมมิชชันการขายคอร์ส'
  if (rule.type === 'Treating Staff' || rule.type === 'ผู้ให้บริการ') rule.type = 'นักกายภาพบำบัด'
  if (rule.type === 'Salesperson') rule.type = 'ผู้ขาย'
  if (rule.calculation === '฿300 / Visit') rule.calculation = '300 บาทต่อครั้ง'
})
masterData.forEach((item) => {
  if (item.category === 'Customer Group') item.category = 'กลุ่มลูกค้า'
  if (item.category === 'Referral Channel') item.category = 'ช่องทางที่รู้จักคลินิก'
  if (item.category === 'Insurance Company') item.category = 'บริษัทประกัน'
})
users.forEach((item) => {
  if (item.role === 'Therapist / Doctor') item.role = 'นักกายภาพบำบัด'
})
rooms.forEach((item) => {
  const room = item as { type: string }
  if (room.type === 'Treatment Room') room.type = 'ห้องรักษา'
  if (room.type === 'Resource') room.type = 'ทรัพยากร'
})
function persistAll() {
  localStorage.setItem('clinicBranches', JSON.stringify(branches))
  localStorage.setItem('clinicUsers', JSON.stringify(users))
  localStorage.setItem('clinicServices', JSON.stringify(services))
  localStorage.setItem('clinicRooms', JSON.stringify(rooms))
  localStorage.setItem('clinicCommissionRules', JSON.stringify(commissionRules))
  localStorage.setItem('clinicMasterData', JSON.stringify(masterData))
}
const form = reactive({
  code: '',
  name: '',
  detail: '',
  role: 'นักกายภาพบำบัด',
  permission: 'Patient, Visit, Treatment',
  branch: 'ราชพฤกษ์',
  type: '',
  price: '',
  expiry: '',
  calculation: '',
  calculationType: 'จำนวนเงินคงที่',
  value: '',
  basis: 'ต่อครั้ง',
  effectiveFrom: '2026-08-01',
  category: 'กลุ่มลูกค้า',
  status: 'Active',
})
type EditableRecord = Partial<Record<keyof typeof form, string>>
const sections: Array<[string, string]> = [
  ['branches', 'สาขา'],
  ['users', 'ผู้ใช้และสิทธิ์'],
  ['services', 'บริการและคอร์ส'],
  ['payments', 'การชำระเงิน'],
  ['rooms', 'ห้อง / ทรัพยากร'],
  ['commission', 'เกณฑ์คอมมิชชัน'],
  ['master', 'ข้อมูลพื้นฐาน'],
]
const title = computed(() => sections.find(([key]) => key === section.value)?.[1] ?? '')
function save() {
  formError.value = ''
  if (
    ['branches', 'users', 'services', 'rooms', 'commission', 'master'].includes(section.value) &&
    !form.name.trim()
  ) {
    formError.value = 'กรุณากรอกชื่อก่อนบันทึก'
    return
  }
  if (section.value === 'branches' && !form.code.trim()) {
    formError.value = 'กรุณากรอกรหัสสาขา'
    return
  }
  if (section.value === 'services' && (!form.type || form.price === '' || Number(form.price) < 0)) {
    formError.value = 'กรุณาระบุประเภทและราคาที่ถูกต้อง'
    return
  }
  if (section.value === 'users' && form.role !== 'Manager' && !selectedPermissions.value.length) {
    formError.value = 'นักกายภาพบำบัดต้องมีสิทธิ์อย่างน้อย 1 รายการ'
    return
  }
  if (
    section.value === 'commission' &&
    (!form.type || Number(form.value) <= 0 || !form.effectiveFrom)
  ) {
    formError.value = 'กรุณาระบุผู้ได้รับคอมมิชชัน มูลค่ามากกว่า 0 และวันที่เริ่มใช้'
    return
  }
  if (section.value === 'commission') {
    const unit = form.calculationType === 'เปอร์เซ็นต์' ? '%' : ' บาท'
    form.calculation = `${form.value || '0'}${unit}${form.basis === 'ยอดขาย' ? ' ของยอดขาย' : ` ${form.basis}`}`
  }
  if (section.value === 'users')
    form.permission = form.role === 'Manager' ? 'All' : selectedPermissions.value.join(', ')
  const record = { ...form }
  const collections: Record<string, EditableRecord[]> = {
    branches,
    users,
    services,
    rooms,
    commission: commissionRules,
    master: masterData,
  }
  if (section.value === 'payments') {
    const existing = paymentMethods.find((item) => item.name === form.type)
    if (!existing)
      paymentMethods.push({ name: form.type || 'ช่องทางใหม่', active: form.status === 'Active' })
    persistPayments()
  } else {
    const collection = collections[section.value]
    if (collection) {
      if (editingIndex.value >= 0) {
        const current = collection[editingIndex.value]
        if (
          current?.status === 'Active' &&
          record.status === 'Inactive' &&
          !window.confirm('ข้อมูลนี้อาจถูกใช้งานอยู่ ยืนยันการปิดใช้งานหรือไม่?')
        )
          return
        Object.assign(current!, record)
      } else collection.push(record)
    }
  }
  if (section.value === 'users' && form.role === 'นักกายภาพบำบัด') {
    localStorage.setItem('mockStaffPermissions', form.permission)
  }
  persistAll()
  editing.value = false
  saved.value = true
}
function openEditor(index = -1) {
  editingIndex.value = index
  saved.value = false
  formError.value = ''
  Object.assign(form, {
    code: '',
    name: '',
    detail: '',
    role: 'นักกายภาพบำบัด',
    permission: 'Patient, Visit, Treatment',
    branch: 'ราชพฤกษ์',
    type: '',
    price: '',
    expiry: '',
    calculation: '',
    calculationType: 'จำนวนเงินคงที่',
    value: '',
    basis: 'ต่อครั้ง',
    effectiveFrom: '2026-08-01',
    category: 'กลุ่มลูกค้า',
    status: 'Active',
  })
  const collections: Record<string, EditableRecord[]> = {
    branches,
    users,
    services,
    rooms,
    commission: commissionRules,
    master: masterData,
  }
  if (index >= 0) Object.assign(form, collections[section.value]?.[index] ?? {})
  selectedPermissions.value =
    form.permission === 'All' ? [...permissionOptions] : form.permission.split(', ').filter(Boolean)
  editing.value = true
}
function persistPayments() {
  localStorage.setItem(
    'enabledPaymentMethods',
    JSON.stringify(paymentMethods.filter((item) => item.active).map((item) => item.name)),
  )
}
function togglePayment(method: (typeof paymentMethods)[number]) {
  if (method.active && !window.confirm(`ยืนยันการปิดใช้งาน ${method.name} หรือไม่?`)) return
  method.active = !method.active
  persistPayments()
  saved.value = true
}
</script>

<template>
  <div class="staff-page">
    <StaffHeader />
    <main class="staff-main">
      <section class="staff-hero">
        <small>BACK OFFICE SETUP RELEASE 1</small>
        <h1>ตั้งค่าระบบ</h1>
        <p>กำหนดโครงสร้างคลินิก สิทธิ์ และข้อมูลพื้นฐานที่ใช้ในระบบ</p>
      </section>

      <div class="settings-layout">
        <aside class="settings-nav">
          <p>หมวดการตั้งค่า</p>
          <button
            v-for="item in sections"
            :key="item[0]"
            :class="{ active: section === item[0] }"
            @click="section = item[0]"
          >
            {{ item[1] }}
          </button>
        </aside>

        <section class="staff-panel settings-panel">
          <header class="settings-head">
            <div>
              <small>BACK OFFICE</small>
              <h2>{{ title }}</h2>
            </div>
            <button v-if="section !== 'payments'" class="primary" @click="openEditor()">
              {{ section === 'commission' ? '+ เพิ่มเกณฑ์' : '+ เพิ่มข้อมูล' }}
            </button>
          </header>

          <table v-if="section === 'branches'" class="staff-table">
            <thead>
              <tr>
                <th>รหัสสาขา</th>
                <th>ชื่อสาขา</th>
                <th>ข้อมูลติดต่อ</th>
                <th>สถานะ</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in branches" :key="item.code">
                <td>
                  <b>{{ item.code }}</b>
                </td>
                <td>{{ item.name }}</td>
                <td>{{ item.detail }}</td>
                <td>
                  <span class="status" :class="{ void: item.status === 'Inactive' }">{{
                    item.status === 'Active' ? 'ใช้งาน' : 'ปิดใช้งาน'
                  }}</span>
                </td>
                <td><button class="secondary" @click="openEditor(index)">แก้ไข</button></td>
              </tr>
            </tbody>
          </table>
          <table v-else-if="section === 'users'" class="staff-table">
            <thead>
              <tr>
                <th>ผู้ใช้งาน</th>
                <th>Role</th>
                <th>สาขาที่เข้าถึง</th>
                <th>Permission</th>
                <th>สถานะ</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in users" :key="item.name">
                <td>
                  <b>{{ item.name }}</b
                  ><small>{{ item.role }}</small>
                </td>
                <td>{{ item.role }}</td>
                <td>{{ item.branch }}</td>
                <td>{{ item.permission }}</td>
                <td>
                  <span class="status" :class="{ void: item.status === 'Inactive' }">{{
                    item.status === 'Active' ? 'ใช้งาน' : 'ปิดใช้งาน'
                  }}</span>
                </td>
                <td><button class="secondary" @click="openEditor(index)">แก้ไข</button></td>
              </tr>
            </tbody>
          </table>
          <table v-else-if="section === 'services'" class="staff-table">
            <thead>
              <tr>
                <th>รายการ</th>
                <th>ประเภท</th>
                <th>ราคา</th>
                <th>จำนวน/Bonus</th>
                <th>หมดอายุ</th>
                <th>สถานะ</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in services" :key="item.name">
                <td>
                  <b>{{ item.name }}</b>
                </td>
                <td>{{ item.type }}</td>
                <td>฿{{ Number(item.price).toLocaleString() }}</td>
                <td>{{ item.detail || '—' }}</td>
                <td>{{ item.expiry || '—' }}</td>
                <td>
                  <span class="status" :class="{ void: item.status === 'Inactive' }">{{
                    item.status === 'Active' ? 'ใช้งาน' : 'ปิดใช้งาน'
                  }}</span>
                </td>
                <td><button class="secondary" @click="openEditor(index)">แก้ไข</button></td>
              </tr>
            </tbody>
          </table>
          <div v-else-if="section === 'payments'" class="setup-cards">
            <article v-for="method in paymentMethods" :key="method.name">
              <div>
                <b>{{ method.name }}</b
                ><small>ช่องทางรับชำระเงิน</small>
              </div>
              <button class="secondary" @click="togglePayment(method)">
                {{ method.active ? 'ปิดใช้งาน' : 'เปิดใช้งาน' }}
              </button>
            </article>
          </div>
          <table v-else-if="section === 'rooms'" class="staff-table">
            <thead>
              <tr>
                <th>ห้อง / ทรัพยากร</th>
                <th>สาขา</th>
                <th>ประเภท</th>
                <th>สถานะ</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in rooms" :key="item.name">
                <td>
                  <b>{{ item.name }}</b>
                </td>
                <td>{{ item.branch }}</td>
                <td>{{ item.type }}</td>
                <td>
                  <span
                    class="status"
                    :class="{ busy: item.status === 'ไม่ว่าง', void: item.status === 'ซ่อม' }"
                    >{{ item.status }}</span
                  >
                </td>
                <td><button class="secondary" @click="openEditor(index)">แก้ไข</button></td>
              </tr>
            </tbody>
          </table>
          <table v-else-if="section === 'commission'" class="staff-table">
            <thead>
              <tr>
                <th>ชื่อเกณฑ์</th>
                <th>ผู้ได้รับค่าคอมมิชชัน</th>
                <th>วิธีคำนวณ</th>
                <th>เริ่มใช้</th>
                <th>สถานะ</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in commissionRules" :key="item.name">
                <td>
                  <b>{{ item.name }}</b>
                </td>
                <td>{{ item.type }}</td>
                <td>{{ item.calculation }}</td>
                <td>{{ item.effectiveFrom || '1 ส.ค. 2569' }}</td>
                <td>
                  <span class="status" :class="{ void: item.status === 'Inactive' }">{{
                    item.status === 'Active' ? 'ใช้งาน' : 'ปิดใช้งาน'
                  }}</span>
                </td>
                <td><button class="secondary" @click="openEditor(index)">แก้ไข</button></td>
              </tr>
            </tbody>
          </table>
          <table v-else class="staff-table">
            <thead>
              <tr>
                <th>หมวดข้อมูล</th>
                <th>รายการ</th>
                <th>สถานะ</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in masterData" :key="`${item.category}-${item.name}`">
                <td>{{ item.category }}</td>
                <td>
                  <b>{{ item.name }}</b>
                </td>
                <td>
                  <span class="status" :class="{ void: item.status === 'Inactive' }">{{
                    item.status === 'Active' ? 'ใช้งาน' : 'ปิดใช้งาน'
                  }}</span>
                </td>
                <td><button class="secondary" @click="openEditor(index)">แก้ไข</button></td>
              </tr>
            </tbody>
          </table>
          <p v-if="saved" class="saved">บันทึกข้อมูลเรียบร้อยแล้ว</p>
        </section>
      </div>
    </main>

    <div v-if="editing" class="drawer-backdrop" @click.self="editing = false">
      <aside class="drawer">
        <header>
          <div>
            <small>BACK OFFICE</small>
            <h2>เพิ่มหรือแก้ไข {{ title }}</h2>
          </div>
          <button @click="editing = false">×</button>
        </header>
        <template v-if="section === 'branches'">
          <label>รหัสสาขา<input v-model="form.code" placeholder="เช่น RP01" /></label>
          <label>ชื่อสาขา<input v-model="form.name" placeholder="ชื่อสาขา" /></label>
          <label
            >ข้อมูลติดต่อ<input v-model="form.detail" placeholder="เบอร์โทรศัพท์หรือที่อยู่"
          /></label>
        </template>
        <template v-else-if="section === 'users'">
          <label>ชื่อผู้ใช้งาน<input v-model="form.name" /></label>
          <label
            >Role<select v-model="form.role">
              <option>Manager</option>
              <option>นักกายภาพบำบัด</option>
            </select></label
          >
          <label
            >สาขาที่เข้าถึง<select v-model="form.branch">
              <option>ทุกสาขา</option>
              <option>ราชพฤกษ์</option>
              <option>สุขุมวิท</option>
            </select></label
          >
          <fieldset class="permission-picker" :disabled="form.role === 'Manager'">
            <legend>Permission</legend>
            <label v-for="item in permissionOptions" :key="item"
              ><input v-model="selectedPermissions" type="checkbox" :value="item" />{{
                item
              }}</label
            >
          </fieldset>
        </template>
        <template v-else-if="section === 'services'">
          <label>ชื่อบริการหรือคอร์ส<input v-model="form.name" /></label>
          <label
            >ประเภท<select v-model="form.type">
              <option>Assessment</option>
              <option>Single Visit</option>
              <option>Course</option>
            </select></label
          >
          <label>ราคา<input v-model="form.price" type="number" min="0" /></label>
          <label
            >จำนวนครั้ง / Bonus<input v-model="form.detail" placeholder="เช่น 10 + 2 Bonus"
          /></label>
          <label
            >อายุการใช้งานคอร์ส<input v-model="form.expiry" placeholder="เช่น 12 เดือน"
          /></label>
        </template>
        <template v-else-if="section === 'payments'">
          <label
            >ช่องทางชำระเงิน<select v-model="form.type">
              <option>Cash</option>
              <option>Transfer</option>
              <option>QR</option>
              <option>Card</option>
            </select></label
          >
        </template>
        <template v-else-if="section === 'rooms'">
          <label>ชื่อห้อง / ทรัพยากร<input v-model="form.name" /></label>
          <label
            >สาขา<select v-model="form.branch">
              <option>ราชพฤกษ์</option>
              <option>สุขุมวิท</option>
            </select></label
          >
          <label
            >ประเภท<select v-model="form.type">
              <option>ห้องรักษา</option>
              <option>ทรัพยากร</option>
            </select></label
          >
          <label
            >สถานะ<select v-model="form.status">
              <option>ว่าง</option>
              <option>ไม่ว่าง</option>
              <option>ซ่อม</option>
            </select></label
          >
        </template>
        <template v-else-if="section === 'commission'">
          <label>ชื่อเกณฑ์คอมมิชชัน<input v-model="form.name" /></label>
          <label
            >ผู้ได้รับค่าคอมมิชชัน<select v-model="form.type">
              <option>นักกายภาพบำบัด</option>
              <option>ผู้ขาย</option>
            </select></label
          >
          <label
            >รูปแบบการคำนวณ<select v-model="form.calculationType">
              <option>จำนวนเงินคงที่</option>
              <option>เปอร์เซ็นต์</option>
            </select></label
          >
          <label>มูลค่า<input v-model="form.value" type="number" min="0" /></label>
          <label
            >คิดจาก<select v-model="form.basis">
              <option>ต่อครั้ง</option>
              <option>ยอดขาย</option>
              <option>มูลค่าคอร์สต่อครั้ง</option>
            </select></label
          >
          <label>วันที่เริ่มใช้<input v-model="form.effectiveFrom" type="date" /></label>
        </template>
        <template v-else>
          <label
            >หมวดข้อมูล<select v-model="form.category">
              <option>กลุ่มลูกค้า</option>
              <option>ช่องทางที่รู้จักคลินิก</option>
              <option>บริษัทประกัน</option>
            </select></label
          >
          <label>ชื่อรายการ<input v-model="form.name" /></label>
        </template>
        <label v-if="section !== 'rooms'"
          >สถานะ<select v-model="form.status">
            <option value="Active">ใช้งาน</option>
            <option value="Inactive">ปิดใช้งาน</option>
          </select></label
        >
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <footer>
          <button class="secondary" @click="editing = false">ยกเลิก</button
          ><button class="primary" @click="save">บันทึก</button>
        </footer>
      </aside>
    </div>
  </div>
</template>
<style scoped src="../styles/back-office.css"></style>
