<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
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

const section = ref('branches')
const editing = ref(false)
const editingIndex = ref(-1)
const saved = ref(false)
const formError = ref('')
const permissionOptions = ['Patient', 'Visit', 'Treatment', 'Course', 'Transaction']
const selectedPermissions = ref<string[]>(['Patient', 'Visit', 'Treatment'])
const branches = reactive<
  Array<{
    id?: number
    code: string
    name: string
    address: string
    detail: string
    status: string
  }>
>([])
const users = reactive<
  Array<{
    id?: number
    username: string
    email: string
    employeeCode: string
    phone: string
    name: string
    role: string
    branch: string
    permission: string
    status: string
  }>
>([])
const services = reactive<
  Array<{
    id?: number
    code: string
    name: string
    type: string
    price: string
    detail: string
    expiry: string
    status: string
  }>
>([])
const paymentMethods = reactive<
  Array<{ id?: number; code?: string; name: string; active: boolean }>
>([])
const rooms = reactive<
  Array<{
    id?: number
    code?: string
    branchCode?: string
    name: string
    branch: string
    type: string
    status: string
    active?: boolean
  }>
>([])
const commissionRules = reactive<
  Array<{
    name: string
    type: string
    calculation: string
    calculationType: string
    value: string
    basis: string
    effectiveFrom: string
    status: string
  }>
>([])
const masterData = reactive<
  Array<{ id?: number; code?: string; category: string; name: string; status: string }>
>([])
const resourceStatusToUi: Record<string, string> = {
  AVAILABLE: 'ว่าง',
  IN_USE: 'ไม่ว่าง',
  MAINTENANCE: 'ซ่อม',
}
const resourceStatusToApi: Record<string, string> = {
  ว่าง: 'AVAILABLE',
  ไม่ว่าง: 'IN_USE',
  ซ่อม: 'MAINTENANCE',
}
async function loadBackendSettings() {
  const failures: string[] = []
  const [
    branchResult,
    userResult,
    serviceResult,
    courseResult,
    paymentResult,
    resourceResult,
    masterResult,
  ] = await Promise.allSettled([
    branchApi.list(),
    userManagementApi.list(),
    serviceManagementApi.list(),
    courseApi.list(),
    paymentMethodApi.list(),
    resourceApi.list(),
    masterDataApi.list(),
  ])
  if (branchResult.status === 'fulfilled')
    branches.splice(
      0,
      branches.length,
      ...branchResult.value.map((item) => ({
        id: item.id,
        code: item.branchCode,
        name: item.name,
        address: item.address,
        detail: item.phone,
        status: item.active ? 'Active' : 'Inactive',
      })),
    )
  else failures.push('สาขา')
  if (userResult.status === 'fulfilled')
    users.splice(
      0,
      users.length,
      ...userResult.value.map((item) => ({
        id: item.id,
        username: item.username,
        email: item.email ?? '',
        employeeCode: item.employeeCode,
        phone: item.phone ?? '',
        name: item.fullName,
        role: item.role === 'MANAGER' ? 'Manager' : 'นักกายภาพบำบัด',
        branch: item.branchName ?? 'ทุกสาขา',
        permission: item.role === 'MANAGER' ? 'All' : 'Patient, Visit, Treatment',
        status: item.active ? 'Active' : 'Inactive',
      })),
    )
  else failures.push('ผู้ใช้งาน')
  const serviceList = serviceResult.status === 'fulfilled' ? serviceResult.value : []
  const courseList = courseResult.status === 'fulfilled' ? courseResult.value : []
  if (serviceResult.status === 'rejected' || courseResult.status === 'rejected')
    failures.push('บริการและคอร์ส')
  services.splice(
    0,
    services.length,
    ...serviceList.map((item) => ({
      id: item.id,
      code: item.code,
      name: item.name,
      type: 'Service',
      price: String(item.price),
      detail: `${item.defaultDurationMinutes} นาที`,
      expiry: '',
      status: item.active ? 'Active' : 'Inactive',
    })),
    ...courseList.map((item) => ({
      id: item.id,
      code: item.code,
      name: item.name,
      type: 'Course',
      price: String(item.price),
      detail: `${item.sessions} + ${item.bonusSessions} Bonus`,
      expiry: `${item.validityDays} วัน`,
      status: item.active ? 'Active' : 'Inactive',
    })),
  )
  if (paymentResult.status === 'fulfilled')
    paymentMethods.splice(
      0,
      paymentMethods.length,
      ...paymentResult.value.map((item) => ({
        id: item.id,
        code: item.code,
        name: item.name,
        active: item.active,
      })),
    )
  else failures.push('การชำระเงิน')
  if (resourceResult.status === 'fulfilled')
    rooms.splice(
      0,
      rooms.length,
      ...resourceResult.value.map((item) => ({
        id: item.id,
        code: item.code,
        branchCode: item.branchCode,
        name: item.name,
        branch: item.branchName,
        type: item.type === 'ROOM' ? 'ห้องรักษา' : 'ทรัพยากร',
        status: resourceStatusToUi[item.status] ?? item.status,
        active: item.active,
      })),
    )
  else failures.push('Room / Resource')
  if (masterResult.status === 'fulfilled')
    masterData.splice(
      0,
      masterData.length,
      ...masterResult.value.map((item) => ({
        id: item.id,
        code: item.code,
        category: item.category,
        name: item.name,
        status: item.active ? 'Active' : 'Inactive',
      })),
    )
  else failures.push('Master Data')
  try {
    const schemes = (await commissionApi.schemes()) as Array<{
      id: number
      name: string
      version: string
      effectiveFrom: string
      active: boolean
    }>
    const currentScheme = schemes[0]
    if (currentScheme) {
      const tierList = (await commissionApi.tiers(currentScheme.id)) as Array<{
        minimum: number
        maximum: number | null
        rate: number
        order: number
      }>
      commissionRules.splice(
        0,
        commissionRules.length,
        ...tierList.map((tier) => ({
          name: `${currentScheme.name} Tier ${tier.order}`,
          type: 'ผู้ขาย',
          calculation: `${tier.rate * 100}% ของยอดขาย`,
          calculationType: 'เปอร์เซ็นต์',
          value: String(tier.rate * 100),
          basis: 'ยอดขาย',
          effectiveFrom: currentScheme.effectiveFrom,
          status: currentScheme.active ? 'Active' : 'Inactive',
        })),
      )
    }
  } catch {
    failures.push('Commission Rule')
  }
  if (failures.length) formError.value = `ยังโหลดข้อมูลไม่ได้: ${failures.join(', ')}`
}
onMounted(loadBackendSettings)
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
const form = reactive({
  code: '',
  address: '',
  name: '',
  detail: '',
  role: 'นักกายภาพบำบัด',
  username: '',
  email: '',
  password: '',
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
  ['rooms', 'Room / Resource'],
  ['commission', 'Commission Rule'],
  ['master', 'Master Data'],
]
const title = computed(() => sections.find(([key]) => key === section.value)?.[1] ?? '')
const sectionCopy = computed<{ heading: string; description: string }>(
  () =>
    ({
      branches: { heading: 'จัดการสาขา', description: 'ข้อมูลสาขาทั้งหมดในระบบ' },
      users: {
        heading: 'ผู้ใช้งานและสิทธิ์',
        description: 'จัดการผู้ใช้งานและสิทธิ์การเข้าถึงระบบ',
      },
      services: {
        heading: 'จัดการบริการและคอร์ส',
        description: 'จัดการรายการบริการและคอร์สทั้งหมดในระบบ',
      },
      payments: {
        heading: 'การชำระเงิน',
        description: 'เลือกวิธีการชำระเงินที่เปิดให้บริการในระบบ',
      },
      rooms: {
        heading: 'จัดการห้องและทรัพยากร',
        description: 'จัดการห้องและทรัพยากรที่ใช้ในการให้บริการ',
      },
      commission: {
        heading: 'จัดการกฎเกณฑ์การคำนวณค่าคอมมิชชั่น',
        description: 'กำหนดกติกาค่ามือและคอมมิชชันสำหรับบุคลากร',
      },
      master: { heading: 'Master Data', description: 'จัดการข้อมูลหลักที่ใช้อ้างอิงทั่วทั้งระบบ' },
    })[section.value] ?? { heading: title.value, description: '' },
)
const actionLabel = computed(
  () =>
    ({
      branches: '+ เพิ่มสาขา',
      users: '+ เพิ่มผู้ใช้งาน',
      services: '+ เพิ่มรายการ',
      rooms: '+ เพิ่มห้อง/ทรัพยากร',
      commission: '+ เพิ่มกฎ',
      master: '+ เพิ่มข้อมูล',
    })[section.value] ?? '+ เพิ่มข้อมูล',
)
const editorTitle = computed(() => `${editingIndex.value < 0 ? 'เพิ่ม' : 'แก้ไข'} ${title.value}`)
const masterGroups = computed(() => {
  const groups = new Map<string, typeof masterData>()
  masterData.forEach((item) =>
    groups.set(item.category, [...(groups.get(item.category) ?? []), item]),
  )
  return [...groups.entries()].map(([category, items]) => ({ category, items }))
})
async function save() {
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
  if (section.value === 'branches') {
    try {
      const body = {
        branchCode: form.code,
        name: form.name,
        address: form.address,
        phone: form.detail,
      }
      const existing = editingIndex.value >= 0 ? branches[editingIndex.value] : undefined
      const result = existing?.id
        ? await branchApi.update(existing.id, body)
        : await branchApi.create(body)
      const record = {
        id: result.id,
        code: result.branchCode,
        name: result.name,
        address: result.address,
        detail: result.phone,
        status: result.active ? 'Active' : 'Inactive',
      }
      if (editingIndex.value >= 0) Object.assign(branches[editingIndex.value]!, record)
      else branches.push(record)
      editing.value = false
      saved.value = true
      return
    } catch (error) {
      formError.value = error instanceof Error ? error.message : 'บันทึกสาขาไม่สำเร็จ'
      return
    }
  }
  if (section.value === 'rooms') {
    try {
      const old = editingIndex.value >= 0 ? rooms[editingIndex.value] : undefined
      const body = {
        branchCode:
          old?.branchCode ||
          branches.find((b) => b.name.includes(form.branch))?.code ||
          branches[0]?.code ||
          'RP',
        code: form.code || old?.code || `RES-${Date.now()}`,
        name: form.name,
        type: form.type === 'ห้องรักษา' ? 'ROOM' : 'EQUIPMENT',
        status: resourceStatusToApi[form.status] ?? 'AVAILABLE',
      }
      const result = old?.id
        ? await resourceApi.update(old.id, body)
        : await resourceApi.create(body)
      const record = {
        id: result.id,
        code: result.code,
        branchCode: result.branchCode,
        name: result.name,
        branch: result.branchName,
        type: result.type === 'ROOM' ? 'ห้องรักษา' : 'ทรัพยากร',
        status: resourceStatusToUi[result.status] ?? result.status,
        active: result.active,
      }
      if (editingIndex.value >= 0) Object.assign(rooms[editingIndex.value]!, record)
      else rooms.push(record)
      editing.value = false
      saved.value = true
      return
    } catch (error) {
      formError.value = error instanceof Error ? error.message : 'บันทึกทรัพยากรไม่สำเร็จ'
      return
    }
  }
  if (section.value === 'services') {
    try {
      const existing = editingIndex.value >= 0 ? services[editingIndex.value] : undefined
      const code = form.code || existing?.code || `SERVICE-${Date.now()}`
      const numbers = (form.detail.match(/\d+/g) ?? []).map(Number)
      if (form.type === 'Course') {
        const result = existing?.id
          ? await courseApi.update(existing.id, {
              code,
              name: form.name,
              price: Number(form.price),
              sessions: numbers[0] || 1,
              bonusSessions: numbers[1] || 0,
              validityDays: Number(form.expiry.match(/\d+/)?.[0] || 1),
            })
          : await courseApi.create({
              code,
              name: form.name,
              price: Number(form.price),
              sessions: numbers[0] || 1,
              bonusSessions: numbers[1] || 0,
              validityDays: Number(form.expiry.match(/\d+/)?.[0] || 1),
            })
        const record = {
          id: result.id,
          code: result.code,
          name: result.name,
          type: 'Course',
          price: String(result.price),
          detail: `${result.sessions} + ${result.bonusSessions} Bonus`,
          expiry: `${result.validityDays} วัน`,
          status: result.active ? 'Active' : 'Inactive',
        }
        if (editingIndex.value >= 0) Object.assign(services[editingIndex.value]!, record)
        else services.push(record)
      } else {
        const result = existing?.id
          ? await serviceManagementApi.update(existing.id, {
              code,
              name: form.name,
              defaultDurationMinutes: numbers[0] || 30,
              price: Number(form.price),
            })
          : await serviceManagementApi.create({
              code,
              name: form.name,
              defaultDurationMinutes: numbers[0] || 30,
              price: Number(form.price),
            })
        const record = {
          id: result.id,
          code: result.code,
          name: result.name,
          type: 'Service',
          price: String(result.price),
          detail: `${result.defaultDurationMinutes} นาที`,
          expiry: '',
          status: result.active ? 'Active' : 'Inactive',
        }
        if (editingIndex.value >= 0) Object.assign(services[editingIndex.value]!, record)
        else services.push(record)
      }
      editing.value = false
      saved.value = true
      return
    } catch (error) {
      formError.value = error instanceof Error ? error.message : 'บันทึกบริการไม่สำเร็จ'
      return
    }
  }
  if (section.value === 'users') {
    try {
      const existing = editingIndex.value >= 0 ? users[editingIndex.value] : undefined
      if (!existing && form.password.length < 8) {
        formError.value = 'ผู้ใช้ใหม่ต้องมีรหัสผ่านอย่างน้อย 8 ตัวอักษร'
        return
      }
      if (!form.code.trim() || !form.username.trim()) {
        formError.value = 'กรุณากรอก Username และรหัสพนักงาน'
        return
      }
      const branchCode = branches.find((item) => item.name === form.branch)?.code
      const body = {
        username: form.username,
        email: form.email || undefined,
        ...(form.password ? { password: form.password } : {}),
        role: form.role === 'Manager' ? ('MANAGER' as const) : ('THERAPIST' as const),
        employeeCode: form.code,
        fullName: form.name,
        phone: form.detail || undefined,
        branchCode,
      }
      const result = existing?.id
        ? await userManagementApi.update(existing.id, body)
        : await userManagementApi.create({ ...body, password: form.password })
      const record = {
        id: result.id,
        username: result.username,
        email: result.email ?? '',
        employeeCode: result.employeeCode,
        phone: result.phone ?? '',
        name: result.fullName,
        role: result.role === 'MANAGER' ? 'Manager' : 'นักกายภาพบำบัด',
        branch: result.branchName ?? 'ทุกสาขา',
        permission: result.role === 'MANAGER' ? 'All' : selectedPermissions.value.join(', '),
        status: result.active ? 'Active' : 'Inactive',
      }
      if (editingIndex.value >= 0) Object.assign(users[editingIndex.value]!, record)
      else users.push(record)
      editing.value = false
      saved.value = true
      return
    } catch (error) {
      formError.value = error instanceof Error ? error.message : 'บันทึกผู้ใช้ไม่สำเร็จ'
      return
    }
  }
  if (section.value === 'master') {
    try {
      const old = editingIndex.value >= 0 ? masterData[editingIndex.value] : undefined
      const body = {
        category: form.category,
        code: form.code || old?.code || `ITEM-${Date.now()}`,
        name: form.name,
      }
      const result = old?.id
        ? await masterDataApi.update(old.id, body)
        : await masterDataApi.create(body)
      const record = {
        id: result.id,
        code: result.code,
        category: result.category,
        name: result.name,
        status: result.active ? 'Active' : 'Inactive',
      }
      if (editingIndex.value >= 0) Object.assign(masterData[editingIndex.value]!, record)
      else masterData.push(record)
      editing.value = false
      saved.value = true
      return
    } catch (error) {
      formError.value = error instanceof Error ? error.message : 'บันทึกข้อมูลพื้นฐานไม่สำเร็จ'
      return
    }
  }
  if (section.value === 'payments') {
    try {
      const existing = editingIndex.value >= 0 ? paymentMethods[editingIndex.value] : undefined
      const body = {
        code: form.code.trim() || form.type.trim().toUpperCase(),
        name: form.name.trim() || form.type.trim(),
      }
      if (!body.code || !body.name) {
        formError.value = 'กรุณาระบุรหัสและชื่อช่องทางชำระเงิน'
        return
      }
      const result = existing?.id
        ? await paymentMethodApi.update(existing.id, body)
        : await paymentMethodApi.create(body)
      const record = { id: result.id, code: result.code, name: result.name, active: result.active }
      if (editingIndex.value >= 0) Object.assign(paymentMethods[editingIndex.value]!, record)
      else paymentMethods.push(record)
      editing.value = false
      saved.value = true
      return
    } catch (error) {
      formError.value = error instanceof Error ? error.message : 'บันทึกช่องทางชำระเงินไม่สำเร็จ'
      return
    }
  }
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
    saved.value = true
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
  editing.value = false
  saved.value = true
}
function openEditor(index = -1) {
  editingIndex.value = index
  saved.value = false
  formError.value = ''
  Object.assign(form, {
    code: '',
    address: '',
    name: '',
    username: '',
    email: '',
    password: '',
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
  if (section.value === 'users' && index >= 0) {
    const user = users[index]!
    Object.assign(form, {
      username: user.username,
      email: user.email,
      code: user.employeeCode,
      detail: user.phone,
      name: user.name,
      role: user.role,
      branch: user.branch,
      status: user.status,
      password: '',
    })
  }
  selectedPermissions.value =
    form.permission === 'All' ? [...permissionOptions] : form.permission.split(', ').filter(Boolean)
  editing.value = true
}
async function togglePayment(method: (typeof paymentMethods)[number]) {
  if (method.active && !window.confirm(`ยืนยันการปิดใช้งาน ${method.name} หรือไม่?`)) return
  if (!method.id) {
    formError.value = 'รายการช่องทางชำระเงินนี้ยังไม่มี id จาก backend'
    return
  }
  try {
    if (method.active) {
      await paymentMethodApi.deactivate(method.id)
      method.active = false
    } else {
      await paymentMethodApi.activate(method.id)
      method.active = true
    }
    saved.value = true
  } catch (error) {
    formError.value = error instanceof Error ? error.message : 'ปิดใช้งานช่องทางชำระเงินไม่สำเร็จ'
  }
}
async function toggleUser(user: (typeof users)[number]) {
  if (!user.id) {
    formError.value = 'รายการผู้ใช้นี้ยังไม่มี id จาก backend'
    return
  }
  if (user.status === 'Active' && !window.confirm(`ยืนยันการปิดใช้งาน ${user.username} หรือไม่?`))
    return
  try {
    const result =
      user.status === 'Active'
        ? await userManagementApi.deactivate(user.id)
        : await userManagementApi.activate(user.id)
    if (result) Object.assign(user, { status: result.active ? 'Active' : 'Inactive' })
    else user.status = 'Inactive'
    saved.value = true
  } catch (error) {
    formError.value = error instanceof Error ? error.message : 'เปลี่ยนสถานะผู้ใช้ไม่สำเร็จ'
  }
}
</script>

<template>
  <div class="settings-page">
    <StaffHeader />
    <main class="settings-main">
      <section class="settings-page-heading">
        <h1>การตั้งค่า</h1>
        <p>จัดการข้อมูลสาขา ผู้ใช้งาน สิทธิ์การเข้าถึง และการตั้งค่าระบบ</p>
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

        <section class="settings-panel">
          <header class="settings-head">
            <div>
              <h2>{{ sectionCopy.heading }}</h2>
              <p>{{ sectionCopy.description }}</p>
            </div>
            <button v-if="section !== 'payments'" class="settings-primary" @click="openEditor()">
              {{ actionLabel }}
            </button>
          </header>
          <p v-if="formError && !editing" class="settings-error" role="alert">{{ formError }}</p>

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
                <td>
                  <button class="secondary" @click="openEditor(index)">แก้ไข</button
                  ><button class="secondary" @click="toggleUser(item)">
                    {{ item.status === 'Active' ? 'ปิดใช้งาน' : 'เปิดใช้งาน' }}
                  </button>
                </td>
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
              <button
                class="settings-switch"
                :class="{ active: method.active }"
                :aria-label="`${method.active ? 'ปิดใช้งาน' : 'เปิดใช้งาน'} ${method.name}`"
                @click="togglePayment(method)"
              >
                <span></span>
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
                <th>ประเภทข้อมูล</th>
                <th>จำนวนรายการ</th>
                <th>ตัวอย่างข้อมูล</th>
                <th>สถานะ</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="group in masterGroups" :key="group.category">
                <td>
                  <b>{{ group.category }}</b>
                </td>
                <td>{{ group.items.length }} รายการ</td>
                <td>{{ group.items.map((item) => item.name).join(', ') }}</td>
                <td>
                  <span
                    class="status"
                    :class="{ void: group.items.every((item) => item.status === 'Inactive') }"
                    >{{
                      group.items.every((item) => item.status === 'Inactive')
                        ? 'ปิดใช้งาน'
                        : 'ใช้งาน'
                    }}</span
                  >
                </td>
                <td>
                  <button
                    class="secondary"
                    @click="openEditor(masterData.indexOf(group.items[0]!))"
                  >
                    แก้ไข
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-if="saved" class="settings-saved" role="status">บันทึกข้อมูลเรียบร้อยแล้ว</p>
        </section>
      </div>
    </main>

    <div v-if="editing" class="settings-modal-backdrop" @click.self="editing = false">
      <aside class="settings-modal" role="dialog" aria-modal="true" :aria-label="editorTitle">
        <header>
          <div>
            <h2>{{ editorTitle }}</h2>
          </div>
          <button type="button" aria-label="ปิด" @click="editing = false">×</button>
        </header>
        <template v-if="section === 'branches'">
          <label>รหัสสาขา (ตัวเลข 2 หลัก)<input v-model="form.code" maxlength="2" placeholder="เช่น 01" /></label>
          <label>ชื่อสาขา<input v-model="form.name" placeholder="ชื่อสาขา" /></label>
          <label>ที่อยู่<input v-model="form.address" placeholder="ที่อยู่สาขา" /></label>
          <label>เบอร์โทรศัพท์<input v-model="form.detail" placeholder="เบอร์โทรศัพท์" /></label>
        </template>
        <template v-else-if="section === 'users'">
          <label>ชื่อผู้ใช้งาน<input v-model="form.name" placeholder="ชื่อ-นามสกุล" /></label>
          <label>Username<input v-model="form.username" autocomplete="off" /></label>
          <label>อีเมล<input v-model="form.email" type="email" autocomplete="off" /></label>
          <label
            >รหัสผ่าน<input
              v-model="form.password"
              type="password"
              autocomplete="new-password"
              placeholder="เว้นว่างได้เมื่อแก้ไข"
          /></label>
          <label>รหัสพนักงาน<input v-model="form.code" /></label>
          <label>โทรศัพท์<input v-model="form.detail" /></label>
          <label
            >Role<select v-model="form.role">
              <option>Manager</option>
              <option>นักกายภาพบำบัด</option>
            </select></label
          >
          <label
            >สาขาที่เข้าถึง<select v-model="form.branch">
              <option>ทุกสาขา</option>
              <option v-for="item in branches" :key="item.code" :value="item.name">
                {{ item.name }}
              </option>
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
              <option v-for="branch in branches" :key="branch.code" :value="branch.name">
                {{ branch.name }}
              </option>
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
          <button class="settings-cancel" @click="editing = false">ยกเลิก</button
          ><button class="settings-primary" @click="save">บันทึก</button>
        </footer>
      </aside>
    </div>
  </div>
</template>
<style scoped src="../styles/back-office.css"></style>
