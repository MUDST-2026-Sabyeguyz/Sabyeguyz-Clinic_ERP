<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAppointmentProcess } from '@/features/appointments/useAppointmentProcess'
import AppointmentProcessPanel from '@/features/appointments/components/AppointmentProcessPanel.vue'
import { useRoute, useRouter } from 'vue-router'
import {
  appointmentApi,
  courseApi,
  ApiError,
  catalogApi,
  patientApi,
  paymentMethodApi,
  paymentStatusApi,
  salesApi,
  staffApi,
  type CoursePurchase,
  type Course,
  type PatientProfile,
  type PaymentMethod,
  type PaymentStatusOption,
  type SaleCreateRequest,
  type ServiceOption,
  type Staff,
} from '@/api/client'
import { allowedPaymentMethod, buildSaleRequest, type UsageSelection } from '../careTransactions'
import CourseUsagePicker from '@/features/courses/components/CourseUsagePicker.vue'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import paymentBankIcon from '@/assets/payment-bank.svg'
import paymentQrIcon from '@/assets/payment-qr.svg'
import paymentCardIcon from '@/assets/payment-card.svg'

type DiscountType = 'NONE' | 'FIXED' | 'PERCENT'
type SaleLine = {
  key: string
  category: '' | 'service' | 'course'
  catalogKey: string
  quantity: number
  discountType: DiscountType
  discountValue: number
}
const route = useRoute()
const router = useRouter()
const patient = ref<PatientProfile | null>(null)
const services = ref<ServiceOption[]>([])
const courses = ref<Course[]>([])
const staff = ref<Staff[]>([])
const methods = ref<PaymentMethod[]>([])
const labels = ref<PaymentStatusOption[]>([])
const lines = ref<SaleLine[]>([
  {
    key: crypto.randomUUID(),
    category: '',
    catalogKey: '',
    quantity: 1,
    discountType: 'NONE',
    discountValue: 0,
  },
])
const sellerId = ref(0)
const treatingId = ref(0)
const methodCode = ref('')
const paymentLabel = ref<SaleCreateRequest['paymentLabelCode'] | ''>('')
const usageSelections = ref<UsageSelection[]>([])
const patientCourses = ref<CoursePurchase[]>([])
const uncertainSale = ref(false)
const billDiscountType = ref<DiscountType>('NONE')
const billDiscountValue = ref(0)
const note = ref('')
const stage = ref<'edit' | 'review' | 'success'>('edit')
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const saved = ref<{ id: number; receiptNo: string; receiptId?: number | null } | null>(null)
const {
  appointmentId,
  sourceAppointment,
  appointmentProgress,
  processLoading,
  processError,
  refreshAppointmentProcess,
  canStartAppointment,
  processBlockReason,
  recoveryRequired,
  beginAppointmentWrite,
  finishAppointmentWrite,
  recoverAppointmentWrite,
} = useAppointmentProcess()
const branchCode = computed(() => {
  if (sourceAppointment.value) return sourceAppointment.value.branchCode
  const user = JSON.parse(localStorage.getItem('authUser') || 'null') as {
    role?: string
    branchCode?: string
  } | null
  return user?.role === 'MANAGER' ? selectedManagerBranch()?.code || '' : user?.branchCode || ''
})
const catalog = computed(() => [
  ...services.value.map((item) => ({
    key: `service:${item.id}`,
    id: item.id,
    name: item.name,
    price: item.price,
    type: 'SERVICE' as const,
  })),
  ...courses.value.map((item) => ({
    key: `course:${item.id}`,
    id: item.id,
    name: item.name,
    price: item.price,
    type: 'COURSE' as const,
  })),
])
const catalogItem = (line: SaleLine) => catalog.value.find((item) => item.key === line.catalogKey)
const cents = (value: number) => Math.round((value + Number.EPSILON) * 100)
const discountCents = (baseCents: number, type: DiscountType, value: number) =>
  type === 'NONE' || !Number.isFinite(value)
    ? 0
    : type === 'PERCENT'
      ? Math.round((baseCents * cents(value)) / 10000)
      : cents(value)
const lineGrossCents = (line: SaleLine) =>
  cents(catalogItem(line)?.price || 0) * (Number(line.quantity) || 0)
const lineDiscountCents = (line: SaleLine) => {
  const item = catalogItem(line)
  const base = item?.type === 'COURSE' ? cents(item.price) : lineGrossCents(line)
  return (
    discountCents(base, line.discountType, Number(line.discountValue)) *
    (item?.type === 'COURSE' ? Number(line.quantity) || 0 : 1)
  )
}
const lineNetCents = (line: SaleLine) => lineGrossCents(line) - lineDiscountCents(line)
const grossCents = computed(() => lines.value.reduce((sum, line) => sum + lineGrossCents(line), 0))
const itemDiscountCents = computed(() =>
  lines.value.reduce((sum, line) => sum + lineDiscountCents(line), 0),
)
const subtotalCents = computed(() => grossCents.value - itemDiscountCents.value)
const billDiscountCents = computed(() =>
  discountCents(subtotalCents.value, billDiscountType.value, Number(billDiscountValue.value)),
)
const netCents = computed(() => subtotalCents.value - billDiscountCents.value)
const money = (value: number) =>
  new Intl.NumberFormat('th-TH', { style: 'currency', currency: 'THB' }).format(value / 100)
const storageKey = computed(
  () =>
    `sale-pending:${branchCode.value}:${patient.value?.id || route.query.patientId}:${appointmentId.value || 'no-appointment'}`,
)
const hasService = computed(() => lines.value.some((line) => line.category === 'service'))
const staffOptions = computed(() =>
  staff.value.map((person) => ({ value: String(person.id), label: person.fullName })),
)
const itemOptions = (line: SaleLine) =>
  catalog.value
    .filter((item) => item.key.startsWith(`${line.category}:`))
    .map((item) => ({ value: item.key, label: `${item.name} · ${money(cents(item.price))}` }))
function paymentDisplay(code: string, name: string) {
  const value = `${code} ${name}`.toLowerCase()
  if (/prompt|qr|พร้อมเพย์/.test(value)) return { label: 'พร้อมเพย์', icon: paymentQrIcon }
  if (/card|บัตร/.test(value)) return { label: 'บัตรเครดิต/เดบิต', icon: paymentCardIcon }
  if (/bank|transfer|โอน/.test(value)) return { label: 'โอนเงินผ่านธนาคาร', icon: paymentBankIcon }
  return { label: name, icon: '' }
}

let loadRequest = 0
async function load() {
  const current = ++loadRequest
  const requestedBranch = branchCode.value
  loading.value = true
  error.value = ''
  try {
    // โหลดผู้ป่วย รายการขาย ช่องทางชำระ และสิทธิ์คอร์สสำหรับ checkout
    const patientId = Number(route.query.patientId)
    if (!Number.isInteger(patientId) || patientId < 1) throw new Error('กรุณาเลือกผู้ป่วยก่อนขาย')
    const [person, serviceList, courseList, staffList, methodList, labelList, purchases] =
      await Promise.all([
        patientApi.get(patientId),
        catalogApi.services(),
        catalogApi.courses(),
        staffApi.list(),
        paymentMethodApi.list(),
        paymentStatusApi.list(),
        courseApi.patientCourses(patientId),
      ])
    if (
      current !== loadRequest ||
      requestedBranch !== branchCode.value ||
      Number(route.query.patientId) !== patientId
    )
      return
    patient.value = person
    services.value = serviceList.filter(
      (item) => !/assessment|ประเมิน/i.test(`${item.code} ${item.name}`),
    )
    patientCourses.value = purchases
    courses.value = courseList
    staff.value = staffList.filter(
      (item) =>
        item.active &&
        (item.position ?? item.role ?? '').toLowerCase() === 'therapist' &&
        item.branchCode === branchCode.value,
    )
    methods.value = methodList.filter(allowedPaymentMethod)
    labels.value = labelList.filter((item) => item.enabled)
    sellerId.value = 0
    treatingId.value = 0
    methodCode.value = ''
    uncertainSale.value = Boolean(sessionStorage.getItem(storageKey.value))
    paymentLabel.value = ''
  } catch (cause) {
    if (current === loadRequest)
      error.value = cause instanceof Error ? cause.message : 'โหลดข้อมูลขายไม่สำเร็จ'
  } finally {
    if (current === loadRequest) loading.value = false
  }
}
function addLine() {
  lines.value.push({
    key: crypto.randomUUID(),
    category: '',
    catalogKey: '',
    quantity: 1,
    discountType: 'NONE',
    discountValue: 0,
  })
}
function removeLine(key: string) {
  if (lines.value.length > 1) lines.value = lines.value.filter((line) => line.key !== key)
}
function resetLine(line: SaleLine) {
  line.catalogKey = ''
  line.quantity = 1
  line.discountValue = 0
}
function check() {
  if (hasService.value && !appointmentId.value)
    throw new Error('กรุณาสร้างนัดหมายก่อนบันทึกบริการรายครั้ง')
  if (!patient.value || !branchCode.value) throw new Error('ไม่พบผู้ป่วยหรือสาขาที่ทำรายการ')
  if (!sellerId.value) throw new Error('กรุณาเลือกผู้ขายหรือเจ้าของยอด')
  if (!methodCode.value || !methods.value.some((method) => method.code === methodCode.value))
    throw new Error('กรุณาเลือกช่องทางชำระเงินที่เปิดใช้งาน')
  if (!labels.value.some((label) => label.code === paymentLabel.value))
    throw new Error('กรุณาเลือกคำอธิบายการชำระเงิน')
  for (const [index, line] of lines.value.entries()) {
    const item = catalogItem(line)
    if (!item) throw new Error(`รายการที่ ${index + 1}: กรุณาเลือกบริการหรือคอร์ส`)
    if (!Number.isInteger(line.quantity) || line.quantity < 1)
      throw new Error(`รายการที่ ${index + 1}: จำนวนต้องเป็นจำนวนเต็มอย่างน้อย 1`)
    if (
      !Number.isFinite(line.discountValue) ||
      line.discountValue < 0 ||
      Math.abs(line.discountValue * 100 - cents(line.discountValue)) > 0.000001
    )
      throw new Error(`รายการที่ ${index + 1}: ส่วนลดต้องไม่ติดลบและมีทศนิยมไม่เกิน 2 ตำแหน่ง`)
    if (line.discountType === 'PERCENT' && line.discountValue > 100)
      throw new Error(`รายการที่ ${index + 1}: ส่วนลดต้องไม่เกิน 100%`)
    if (lineDiscountCents(line) > lineGrossCents(line))
      throw new Error(`รายการที่ ${index + 1}: ส่วนลดเกินราคา`)
  }
  const courseCount = lines.value.reduce(
    (total, line) => total + (catalogItem(line)?.type === 'COURSE' ? line.quantity : 0),
    0,
  )
  if (courseCount > 99) throw new Error('หนึ่งใบเสร็จมีคอร์สได้ไม่เกิน 99 ชุด')
  if (
    !Number.isFinite(billDiscountValue.value) ||
    billDiscountValue.value < 0 ||
    Math.abs(billDiscountValue.value * 100 - cents(billDiscountValue.value)) > 0.000001
  )
    throw new Error('ส่วนลดท้ายบิลต้องไม่ติดลบและมีทศนิยมไม่เกิน 2 ตำแหน่ง')
  if (billDiscountType.value === 'PERCENT' && billDiscountValue.value > 100)
    throw new Error('ส่วนลดท้ายบิลต้องไม่เกิน 100%')
  if (billDiscountCents.value > subtotalCents.value) throw new Error('ส่วนลดท้ายบิลเกินยอดรายการ')
  if (billDiscountCents.value > 0 && itemDiscountCents.value > 0)
    throw new Error('ใช้ส่วนลดรายการกับส่วนลดท้ายบิลพร้อมกันไม่ได้')
  if (netCents.value <= 0) throw new Error('ยอดสุทธิต้องมากกว่า 0 เพื่อรับชำระและออกใบเสร็จ')
}
function saleRequest() {
  // รวมข้อมูลหน้าจอเป็น payload ตาม contract เดียวก่อนตรวจหรือส่งขาย
  if (hasService.value && !staff.value.some((person) => person.id === treatingId.value))
    throw new Error('กรุณาเลือกผู้รักษาจริง')
  return buildSaleRequest({
    patientId: patient.value!.id,
    branchCode: branchCode.value,
    appointmentId: appointmentId.value ?? undefined,
    salespersonStaffId: sellerId.value,
    treatingStaffId: treatingId.value || undefined,
    paymentLabelCode: paymentLabel.value,
    paymentMethodCode: methodCode.value,
    labels: labels.value,
    methods: methods.value,
    note: note.value,
    billDiscountType: billDiscountType.value,
    billDiscountValue: billDiscountValue.value,
    items: lines.value.map((line) => {
      const item = catalogItem(line)
      if (!item) throw new Error('กรุณาเลือกรายการขาย')
      return {
        type: item.type,
        referenceId: item.id,
        price: item.price,
        quantity: line.quantity,
        discountType: line.discountType,
        discountValue: line.discountValue,
      }
    }),
    usageSelections: usageSelections.value,
    courses: patientCourses.value,
  })
}
function review() {
  error.value = ''
  try {
    check()
    saleRequest()
    stage.value = 'review'
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'ตรวจรายการไม่สำเร็จ'
  }
}
async function save() {
  if (saving.value || !patient.value || uncertainSale.value || stage.value === 'success') return
  error.value = ''
  try {
    saving.value = true
    if (route.query.appointmentId) {
      // อ่าน progress ล่าสุดก่อน POST เพื่อกันการสร้างธุรกรรมซ้ำกับนัดเดิม
      await refreshAppointmentProcess()
      if (!canStartAppointment.value) throw new Error(processBlockReason.value)
      if (patient.value.id !== Number(route.query.patientId))
        throw new Error('ผู้ป่วยในหน้าจอไม่ตรงกับนัดหมาย กรุณาเปิดหน้าใหม่')
    }
    check()
    const request = saleRequest()
    // ตั้ง recovery marker ก่อนส่ง เพื่อให้กู้ผลได้เมื่อ request timeout
    sessionStorage.setItem(storageKey.value, 'pending')
    uncertainSale.value = true
    beginAppointmentWrite()
    const result = await salesApi.create(request)
    finishAppointmentWrite(hasService.value)
    if (appointmentId.value) await refreshAppointmentProcess()
    saved.value = { id: result.id, receiptNo: result.receiptNo, receiptId: result.receiptId }
    sessionStorage.removeItem(storageKey.value)
    uncertainSale.value = false
    stage.value = 'success'
  } catch (cause) {
    if (cause instanceof ApiError && [400, 401, 403, 422].includes(cause.status)) {
      sessionStorage.removeItem(storageKey.value)
      uncertainSale.value = false
    }
    await recoverAppointmentWrite(cause)
    error.value =
      cause instanceof Error ? cause.message : 'ยังยืนยันผลการขายไม่ได้ กรุณาตรวจสอบรายการเดิม'
  } finally {
    saving.value = false
  }
}
onMounted(async () => {
  await refreshAppointmentProcess()
  await load()
})
watch(
  () => [route.query.patientId, route.query.appointmentId, selectedManagerBranch()?.code],
  async () => {
    patient.value = null
    stage.value = 'edit'
    usageSelections.value = []
    lines.value = [
      {
        key: crypto.randomUUID(),
        category: '',
        catalogKey: '',
        quantity: 1,
        discountType: 'NONE',
        discountValue: 0,
      },
    ]
    await refreshAppointmentProcess()
    await load()
  },
)
</script>

<template>
  <div class="checkout-page">
    <StaffHeader />
    <main>
      <button
        class="back"
        type="button"
        @click="
          router.push({
            name: 'staff-services',
            query: { patientId: route.query.patientId, appointmentId: route.query.appointmentId },
          })
        "
      >
        ← กลับหน้าบริการ
      </button>
      <button
        v-if="hasService && !appointmentId && patient"
        type="button"
        @click="
          router.push({
            name: 'staff-appointment-create',
            query: { patientId: String(patient.id) },
          })
        "
      >
        สร้างนัดหมายก่อนรับบริการ
      </button>
      <p v-if="uncertainSale" role="alert">
        ยังยืนยันผลคำขอก่อนหน้าไม่ได้ กรุณาตรวจประวัติขายก่อนทำรายการใหม่
        <RouterLink
          v-if="patient"
          :to="{ path: '/staff/patients/' + patient.id + '/history', query: { tab: 'payments' } }"
          >ดูประวัติขาย</RouterLink
        >
      </p>
      <p
        v-if="appointmentProgress?.state === 'NO_LINKED_RECORD' && canStartAppointment"
        role="status"
      >
        {{ appointmentProgress.reason }} กรุณาตรวจสอบรายการเดิมก่อนทำรายการใหม่
      </p>
      <p v-if="processLoading" role="status">กำลังตรวจสอบนัดหมาย…</p>
      <div v-else-if="processError" role="alert">
        {{ processError
        }}<button type="button" @click="refreshAppointmentProcess">ลองอ่านใหม่</button>
      </div>
      <AppointmentProcessPanel
        v-else-if="appointmentProgress && !canStartAppointment && stage !== 'success'"
        :progress="appointmentProgress"
        :block-reason="processBlockReason"
        :recovery-required="recoveryRequired"
        @refresh="refreshAppointmentProcess"
      />
      <p v-else-if="loading" role="status">กำลังโหลดข้อมูลขาย…</p>
      <div v-else-if="error && !patient" role="alert">
        {{ error }} <button type="button" @click="load">ลองใหม่</button>
      </div>
      <template v-else-if="patient">
        <header class="heading">
          <div>
            <h1>
              {{
                stage === 'success'
                  ? 'บันทึกการขายสำเร็จ'
                  : stage === 'review'
                    ? 'ตรวจสอบและชำระเงิน'
                    : 'ขายบริการและคอร์ส'
              }}
            </h1>
            <p>{{ patient.fullName }} · HN {{ patient.hn }}</p>
          </div>
        </header>
        <section v-if="stage === 'success' && saved" class="success-panel">
          <h2>เลขใบเสร็จ {{ saved.receiptNo }}</h2>
          <p>รับชำระ {{ money(netCents) }} และบันทึกรายการเรียบร้อยแล้ว</p>
          <button
            type="button"
            @click="router.push({ name: 'staff-receipt', params: { id: saved.id } })"
          >
            ดูและพิมพ์ใบเสร็จ
          </button>
          <button
            v-if="lines.some((line) => catalogItem(line)?.type === 'COURSE')"
            type="button"
            @click="
              router.push({
                name: 'staff-courses',
                query: {
                  patientId: patient.id,
                  ...(saved.receiptId ? { receiptId: saved.receiptId } : {}),
                  ...(appointmentId ? { appointmentId } : {}),
                },
              })
            "
          >
            ดูคอร์สที่ซื้อ
          </button>
          <p v-if="!hasService">ซื้อคอร์สสำเร็จ การซื้ออย่างเดียวยังไม่สร้างรายการรับบริการ</p>
        </section>
        <div v-else class="checkout-layout">
          <div class="checkout-main">
            <section class="panel">
              <div class="section-heading">
                <h2>รายการขาย</h2>
                <button v-if="stage === 'edit'" type="button" @click="addLine">
                  + เพิ่มรายการ
                </button>
              </div>
              <div v-for="(line, index) in lines" :key="line.key" class="line-row">
                <div class="item-picker">
                  <label
                    >ประเภท<select
                      v-model="line.category"
                      :disabled="stage === 'review'"
                      @change="resetLine(line)"
                    >
                      <option value="">เลือกประเภท</option>
                      <option value="service">รายครั้ง</option>
                      <option value="course">รายคอร์ส</option>
                    </select></label
                  >
                  <label
                    >รายการที่ {{ index + 1
                    }}<FilterDropdown
                      v-model="line.catalogKey"
                      :options="itemOptions(line)"
                      :disabled="stage === 'review' || !line.category"
                      default-label="เลือกรายการ"
                      label="ค้นหารายการ"
                      searchable
                      empty-search-label="ไม่พบผลการค้นหา"
                  /></label>
                </div>
                <label
                  >จำนวน<input
                    v-model.number="line.quantity"
                    type="number"
                    min="1"
                    step="1"
                    :max="catalogItem(line)?.type === 'COURSE' ? 99 : undefined"
                    :disabled="stage === 'review'"
                /></label>
                <label
                  >ส่วนลด<select
                    v-model="line.discountType"
                    :disabled="stage === 'review'"
                    @change="line.discountValue = 0"
                  >
                    <option value="NONE">ไม่มี</option>
                    <option value="FIXED">บาท</option>
                    <option value="PERCENT">เปอร์เซ็นต์</option>
                  </select></label
                >
                <label v-if="line.discountType !== 'NONE'"
                  >ค่า<input
                    v-model.number="line.discountValue"
                    type="number"
                    min="0"
                    :max="line.discountType === 'PERCENT' ? 100 : undefined"
                    step="0.01"
                    :disabled="stage === 'review'"
                /></label>
                <strong>{{ money(lineNetCents(line)) }}</strong>
                <button
                  v-if="stage === 'edit' && lines.length > 1"
                  type="button"
                  @click="removeLine(line.key)"
                >
                  ลบ
                </button>
              </div>
            </section>
            <section class="panel">
              <h2>ผู้รับผิดชอบและการชำระ</h2>
              <CourseUsagePicker
                v-if="hasService && appointmentId"
                v-model="usageSelections"
                :courses="patientCourses"
                :branch-code="branchCode"
                :disabled="stage === 'review' || saving"
              />
              <label v-if="hasService"
                >ผู้รักษาจริง<FilterDropdown
                  :model-value="String(treatingId || '')"
                  :options="staffOptions"
                  label="ผู้รักษาจริง"
                  default-label="เลือกผู้รักษา"
                  :disabled="stage === 'review'"
                  @update:model-value="treatingId = Number($event)"
              /></label>
              <div class="fields">
                <label
                  >ผู้ขาย / เจ้าของคอร์ส<FilterDropdown
                    :model-value="sellerId ? String(sellerId) : ''"
                    :options="staffOptions"
                    :disabled="stage === 'review'"
                    default-label="เลือกนักกายภาพ"
                    label="ค้นหานักกายภาพ"
                    searchable
                    empty-search-label="ไม่พบผลการค้นหา"
                    @update:model-value="sellerId = Number($event)"
                /></label>
              </div>
              <fieldset class="payment-group">
                <legend>ลักษณะการชำระ</legend>
                <div class="payment-options">
                  <button
                    v-for="label in labels"
                    :key="label.code"
                    type="button"
                    :disabled="stage === 'review'"
                    :class="{ active: paymentLabel === label.code }"
                    :aria-pressed="paymentLabel === label.code"
                    @click="paymentLabel = label.code"
                  >
                    {{ label.label }}
                  </button>
                </div>
              </fieldset>
              <fieldset class="payment-group">
                <legend>ช่องทางชำระเงิน</legend>
                <div class="payment-options">
                  <button
                    v-for="method in methods"
                    :key="method.code"
                    type="button"
                    :disabled="stage === 'review'"
                    :class="{ active: methodCode === method.code }"
                    :aria-pressed="methodCode === method.code"
                    @click="methodCode = method.code"
                  >
                    <img
                      v-if="paymentDisplay(method.code, method.name).icon"
                      :src="paymentDisplay(method.code, method.name).icon"
                      alt=""
                    />{{ paymentDisplay(method.code, method.name).label }}
                  </button>
                </div>
              </fieldset>
              <p class="helper">ลักษณะการชำระเป็นคำอธิบาย รับเงินเต็มยอดสุทธิในครั้งเดียว</p>
              <label
                >หมายเหตุ (ถ้ามี)<textarea v-model="note" :readonly="stage === 'review'" rows="3" />
              </label>
            </section>
          </div>
          <aside class="panel summary">
            <h2>สรุปยอด</h2>
            <p>
              <span>ยอดก่อนส่วนลด</span><strong>{{ money(grossCents) }}</strong>
            </p>
            <p>
              <span>ส่วนลดรายการ</span><strong>− {{ money(itemDiscountCents) }}</strong>
            </p>
            <p>
              <span>ยอดหลังส่วนลดรายการ</span><strong>{{ money(subtotalCents) }}</strong>
            </p>
            <label
              >ส่วนลดท้ายบิล<select
                v-model="billDiscountType"
                :disabled="stage === 'review'"
                @change="billDiscountValue = 0"
              >
                <option value="NONE">ไม่มี</option>
                <option value="FIXED">บาท</option>
                <option value="PERCENT">เปอร์เซ็นต์</option>
              </select></label
            >
            <label v-if="billDiscountType !== 'NONE'"
              >ค่า<input
                v-model.number="billDiscountValue"
                type="number"
                min="0"
                :max="billDiscountType === 'PERCENT' ? 100 : undefined"
                step="0.01"
                :disabled="stage === 'review'"
            /></label>
            <p>
              <span>ส่วนลดท้ายบิล</span><strong>− {{ money(billDiscountCents) }}</strong>
            </p>
            <p class="net">
              <span>ยอดสุทธิที่รับชำระ</span><strong>{{ money(netCents) }}</strong>
            </p>
            <p v-if="stage === 'review'" class="helper">
              ตรวจรายการ ผู้ขาย ส่วนลด และช่องทางก่อนยืนยันบันทึก
            </p>
            <button v-if="stage === 'edit'" class="primary" type="button" @click="review">
              ตรวจสอบรายการ
            </button>
            <div v-else class="actions">
              <button type="button" :disabled="saving" @click="stage = 'edit'">แก้ไข</button
              ><button
                class="primary"
                type="button"
                :disabled="saving || uncertainSale"
                @click="save"
              >
                {{ saving ? 'กำลังบันทึก…' : 'ยืนยันรับชำระ' }}
              </button>
            </div>
          </aside>
        </div>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
      </template>
    </main>
  </div>
</template>

<style scoped>
.checkout-page {
  min-height: 100vh;
  background: #f8f4ed;
  color: #173b3b;
}
main {
  max-width: 1280px;
  padding: 30px 22px 70px;
  margin: auto;
}
button {
  font: inherit;
  cursor: pointer;
  background: #fff;
  color: #146d68;
  border: 1px solid #aecbc5;
  border-radius: 8px;
  padding: 9px 14px;
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
button:focus-visible,
:is(input, select, textarea):focus-visible {
  outline: 2px solid #147e78;
  outline-offset: 2px;
}
.back {
  border: 0;
  background: transparent;
  padding: 0;
  color: #176c68;
  font-weight: 600;
  text-decoration: none;
  text-underline-offset: 3px;
  transition:
    color 180ms ease,
    transform 180ms ease;
}
.back:hover,
.back:focus-visible {
  color: #0b5652;
  text-decoration: underline;
}
.back:hover {
  transform: translateX(-4px);
}
.heading {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 24px;
  margin: 16px 0 26px;
}
h1 {
  font-size: 32px;
  margin: 0 0 6px;
}
h2 {
  font-size: 21px;
  margin: 0;
}
.heading p {
  margin: 0;
  color: #58746f;
}
.checkout-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 22px;
  align-items: start;
}
.checkout-main {
  display: grid;
  gap: 20px;
}
.panel,
.success-panel {
  background: #fff;
  border: 1px solid #d5e3de;
  border-radius: 12px;
  padding: 24px;
}
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}
.line-row {
  display: grid;
  grid-template-columns: minmax(210px, 2fr) 75px 110px 85px 110px auto;
  gap: 10px;
  align-items: end;
  border-top: 1px solid #e1ebe7;
  padding: 16px 0;
}
.line-row > strong {
  text-align: right;
  padding-bottom: 10px;
  white-space: nowrap;
}
.line-row > button {
  margin-bottom: 2px;
}
.item-picker {
  display: grid;
  grid-template-columns: 125px minmax(0, 1fr);
  gap: 10px;
  min-width: 0;
}
.payment-group {
  margin: 18px 0;
  padding: 0;
  border: 0;
}
.payment-group legend {
  font-weight: 600;
  margin-bottom: 8px;
}
.payment-options {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.payment-options button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  flex: 1 1 150px;
}
.payment-options button.active {
  background: #1c8580;
  border-color: #1c8580;
  color: #fff;
}
.payment-options button:disabled {
  opacity: 1;
  cursor: default;
}
.payment-options button:not(.active):disabled {
  opacity: 0.6;
}
.payment-options img {
  width: 18px;
  height: 18px;
}
.payment-options .active img {
  filter: brightness(0) invert(1);
}
label {
  display: grid;
  gap: 6px;
  font-size: 14px;
  color: #425f5b;
}
select,
input,
textarea {
  width: 100%;
  min-height: 41px;
  border: 1px solid #afc9c4;
  border-radius: 7px;
  padding: 8px 10px;
  color: #173b3b;
  background: #fff;
  font: inherit;
}
.fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin: 18px 0;
}
.helper {
  color: #59736f;
  font-size: 13px;
}
.summary {
  position: sticky;
  top: 20px;
  display: grid;
  gap: 13px;
}
.summary p {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  margin: 0;
}
.summary .net {
  background: #e2f1ec;
  padding: 13px;
  border-radius: 8px;
  font-size: 18px;
}
.primary {
  background: #1c8580;
  color: #fff;
  border-color: #1c8580;
  font-weight: 600;
}
.actions {
  display: flex;
  gap: 10px;
}
.actions button {
  flex: 1;
}
.summary > .primary {
  background: #1c8580;
  border-color: #1c8580;
}
.error {
  color: #9c302a;
  margin-top: 20px;
}
.success-panel {
  max-width: 640px;
}
.success-panel h2 {
  margin-bottom: 10px;
}
@media (max-width: 900px) {
  .checkout-layout {
    grid-template-columns: 1fr;
  }
  .summary {
    position: static;
  }
  .line-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .line-row label:first-child {
    grid-column: 1 / -1;
  }
}
@media (max-width: 520px) {
  .heading {
    display: block;
  }
  .fields,
  .line-row {
    grid-template-columns: 1fr 1fr;
  }
  .line-row label:first-child {
    grid-column: 1 / -1;
  }
  .panel {
    padding: 18px;
  }
}
</style>
