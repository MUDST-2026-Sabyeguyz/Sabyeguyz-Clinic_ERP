<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  branchApi,
  courseApi,
  coursePaymentCollectionApi,
  patientApi,
  paymentMethodApi,
  paymentStatusApi,
  type Branch,
  type CoursePurchase,
  type PaymentMethod,
  type PaymentStatusOption,
  type PatientProfile,
  type ReceiptDocument,
  type CoursePaymentCollectionResult,
} from '@/api/client'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'

const route = useRoute()
const router = useRouter()
const patient = ref<PatientProfile | null>(null)
const courses = ref<CoursePurchase[]>([])
const selectedIds = ref<number[]>([])
const amounts = ref<Record<number, string>>({})
const methods = ref<PaymentMethod[]>([])
const labels = ref<PaymentStatusOption[]>([])
const branches = ref<Branch[]>([])
const histories = ref<Record<number, ReceiptDocument[]>>({})
const discountType = ref<'NONE' | 'FIXED' | 'PERCENT'>('NONE')
const discountValue = ref('0')
const methodCode = ref('')
const labelCode = ref('')
const stage = ref<'edit' | 'review' | 'success'>('edit')
const result = ref<CoursePaymentCollectionResult | null>(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const idempotencyKey = ref('')
const user = JSON.parse(localStorage.getItem('authUser') ?? 'null') as {
  role?: string
  branchCode?: string
} | null
const branchCode = computed(() =>
  user?.role === 'MANAGER'
    ? selectedManagerBranch()?.code ?? localStorage.getItem('managerSelectedBranch') ?? user?.branchCode ?? ''
    : user?.branchCode ?? '',
)
const selectableCourses = computed(() =>
  courses.value.filter((course) => course.purchaser?.id === patient.value?.id),
)
const selectedCourses = computed(() =>
  selectedIds.value
    .map((id) => selectableCourses.value.find((course) => course.id === id))
    .filter((course): course is CoursePurchase => Boolean(course)),
)
const cents = (value: number) => Math.round((Number(value) + Number.EPSILON) * 100)
const subtotalCents = computed(() => selectedCourses.value.reduce((total, course) => {
  const amount = Number(amounts.value[course.id] ?? '')
  return total + (Number.isFinite(amount) ? cents(amount) : 0)
}, 0))
const discountCents = computed(() => {
  const value = Number(discountValue.value)
  if (!Number.isFinite(value) || value <= 0) return 0
  return discountType.value === 'PERCENT'
    ? Math.round(subtotalCents.value * value / 100)
    : cents(value)
})
const netCents = computed(() => subtotalCents.value - discountCents.value)
const money = (value: number) => new Intl.NumberFormat('th-TH', {
  style: 'currency', currency: 'THB', maximumFractionDigits: 2,
}).format(value)
const allowedMethods = computed(() => methods.value.filter(
  (method) => method.active && ['CreditCard', 'PromptPay', 'BankTransfer'].includes(method.code),
))
const historyRows = computed(() => selectedCourses.value.flatMap((course) =>
  (histories.value[course.id] ?? []).map((receipt) => ({ course, receipt })),
))
const payloadSignature = computed(() => JSON.stringify({
  patientId: patient.value?.id,
  branchCode: branchCode.value,
  items: selectedCourses.value.map((course) => [course.id, amounts.value[course.id]]),
  discountType: discountType.value,
  discountValue: discountValue.value,
  methodCode: methodCode.value,
  labelCode: labelCode.value,
}))

watch(payloadSignature, () => {
  idempotencyKey.value = ''
  if (stage.value === 'review') stage.value = 'edit'
})

watch(branchCode, (code) => {
  if (code && !loading.value && error.value === 'กรุณาเลือกสาขาที่มีสิทธิ์ทำรายการ') {
    void load()
  }
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const id = Number(route.params.patientId)
    if (!Number.isSafeInteger(id) || id < 1) throw new Error('รหัสผู้ป่วยไม่ถูกต้อง')
    if (!branchCode.value) throw new Error('กรุณาเลือกสาขาที่มีสิทธิ์ทำรายการ')
    const [person, purchases, methodList, labelList, branchList] = await Promise.all([
      patientApi.get(id), courseApi.patientCourses(id), paymentMethodApi.list(),
      paymentStatusApi.list(), branchApi.list(),
    ])
    patient.value = person
    courses.value = purchases
    methods.value = methodList
    labels.value = labelList.filter((label) => label.enabled)
    branches.value = branchList
    methodCode.value = methodList.find((method) => method.active && ['CreditCard', 'PromptPay', 'BankTransfer'].includes(method.code))?.code ?? ''
    labelCode.value = labelList.find((label) => label.enabled)?.code ?? ''
    if (purchases.length && !purchases.some((course) => course.purchaser?.id === id)) {
      error.value = 'ไม่พบคอร์สที่ผู้ป่วยรายนี้เป็นผู้ซื้อเดิม'
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'โหลดข้อมูลรับเงินไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}

async function toggleCourse(courseId: number, event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  selectedIds.value = checked
    ? [...new Set([...selectedIds.value, courseId])]
    : selectedIds.value.filter((id) => id !== courseId)
  if (checked && histories.value[courseId] == null) {
    try {
      histories.value[courseId] = await courseApi.receiptHistory(courseId)
    } catch {
      histories.value[courseId] = []
      error.value = 'อ่านประวัติรับเงินคอร์สไม่สำเร็จ กรุณาลองโหลดหน้าใหม่'
    }
  }
}

function validate() {
  if (!patient.value || !branchCode.value) throw new Error('ไม่พบผู้ป่วยหรือสาขาที่ทำรายการ')
  if (!selectedCourses.value.length) throw new Error('เลือกคอร์สเดิมอย่างน้อยหนึ่งรายการ')
  for (const course of selectedCourses.value) {
    const raw = amounts.value[course.id]
    const amount = Number(raw)
    if (raw == null || String(raw).trim() === '' || !Number.isFinite(amount) || amount < 0 || Math.abs(cents(amount) - amount * 100) > 0.000001)
      throw new Error(`กรอกยอดก่อนส่วนลดของ ${course.course.name} เป็นจำนวนเงินไม่ติดลบและไม่เกิน 2 ตำแหน่ง`)
  }
  const discount = Number(discountValue.value)
  if (!Number.isFinite(discount) || discount < 0 || Math.abs(cents(discount) - discount * 100) > 0.000001)
    throw new Error('ส่วนลดต้องไม่ติดลบและมีทศนิยมไม่เกิน 2 ตำแหน่ง')
  if (discountType.value === 'PERCENT' && discount > 100) throw new Error('ส่วนลดต้องอยู่ระหว่าง 0 ถึง 100%')
  if (discountCents.value > subtotalCents.value) throw new Error('ส่วนลดท้ายบิลเกินยอดรวม')
  if (!allowedMethods.value.some((method) => method.code === methodCode.value))
    throw new Error('เลือกช่องทางรับเงินที่เปิดใช้งาน')
  if (!labels.value.some((label) => label.code === labelCode.value))
    throw new Error('เลือกลักษณะการชำระเงินที่เปิดใช้งาน')
}

function review() {
  error.value = ''
  try {
    validate()
    stage.value = 'review'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'ข้อมูลรับเงินไม่ถูกต้อง'
  }
}

async function confirmCollection() {
  if (submitting.value || stage.value !== 'review') return
  error.value = ''
  submitting.value = true
  if (!idempotencyKey.value) idempotencyKey.value = crypto.randomUUID()
  try {
    validate()
    const collection = await coursePaymentCollectionApi.collect({
      patientId: patient.value!.id,
      branchCode: branchCode.value,
      items: selectedCourses.value.map((course) => ({
        courseId: course.id,
        amount: Number(amounts.value[course.id]),
      })),
      ...(discountType.value !== 'NONE' && discountCents.value > 0
        ? { billDiscountType: discountType.value, billDiscountValue: Number(discountValue.value) }
        : {}),
      paymentLabelCode: labelCode.value,
      paymentMethodCode: methodCode.value,
      paymentAmount: netCents.value / 100,
    }, idempotencyKey.value)
    result.value = collection
    stage.value = 'success'
  } catch (cause) {
    error.value = cause instanceof Error
      ? `${cause.message} หากคำขออาจถึงระบบแล้ว ให้ลองยืนยันซ้ำด้วยข้อมูลเดิมเพื่อใช้คำขอเดิม`
      : 'บันทึกรับเงินไม่สำเร็จ กรุณาลองใหม่ด้วยข้อมูลเดิม'
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="staff-page course-payment-page">
    <StaffHeader />
    <main>
      <h1>รับเงินคอร์สเดิม</h1>
      <p v-if="loading" role="status">กำลังโหลดข้อมูลคอร์ส…</p>
      <p v-if="error" role="alert">{{ error }}</p>
      <section v-if="!loading && stage !== 'success'" aria-label="คอร์สเดิม">
        <p v-if="patient">ผู้ซื้อเดิม: {{ patient.fullName }} · HN {{ patient.hn }} · สาขารับเงิน {{ branches.find((b) => b.code === branchCode)?.name ?? branchCode }}</p>
        <p v-if="!selectableCourses.length && patient">ไม่พบคอร์สที่ผู้ป่วยเป็นผู้ซื้อเดิม</p>
        <div v-for="course in selectableCourses" :key="course.id" class="course-payment-item">
          <label>
            <input type="checkbox" :checked="selectedIds.includes(course.id)" :disabled="stage === 'review'" @change="toggleCourse(course.id, $event)" />
            {{ course.course.name }} · เลขคอร์ส {{ course.id }} · สิทธิ์ {{ course.remaining }}/{{ course.purchased }} · หมดอายุ {{ course.expiresAt }}
          </label>
          <label v-if="selectedIds.includes(course.id)">ยอดก่อนส่วนลด
            <input v-model="amounts[course.id]" type="number" min="0" step="0.01" :disabled="stage === 'review'" />
          </label>
          <ul v-if="selectedIds.includes(course.id)" class="payment-history">
            <li v-for="row in histories[course.id] ?? []" :key="row.receiptId">
              {{ row.saleDate }} · {{ row.receiptNo }} · {{ money(row.netAmount) }} · {{ row.payments.map((payment) => `${money(payment.amount)} ${payment.paymentMethod} (${payment.status})`).join(', ') }}
            </li>
          </ul>
        </div>
        <template v-if="selectedIds.length">
          <label>ส่วนลดท้ายบิล
            <select v-model="discountType" :disabled="stage === 'review'"><option value="NONE">ไม่มีส่วนลด</option><option value="FIXED">จำนวนเงิน</option><option value="PERCENT">เปอร์เซ็นต์</option></select>
          </label>
          <label v-if="discountType !== 'NONE'">{{ discountType === 'FIXED' ? 'ส่วนลดบาท' : 'ส่วนลดเปอร์เซ็นต์' }}
            <input v-model="discountValue" type="number" min="0" :max="discountType === 'PERCENT' ? 100 : undefined" step="0.01" :disabled="stage === 'review'" />
          </label>
          <label>ลักษณะการชำระ
            <select v-model="labelCode" :disabled="stage === 'review'"><option v-for="label in labels" :key="label.code" :value="label.code">{{ label.label }}</option></select>
          </label>
          <label>ช่องทางรับเงิน
            <select v-model="methodCode" :disabled="stage === 'review'"><option v-for="method in allowedMethods" :key="method.code" :value="method.code">{{ method.name }}</option></select>
          </label>
          <dl><dt>ยอดก่อนส่วนลด</dt><dd>{{ money(subtotalCents / 100) }}</dd><dt>ส่วนลด</dt><dd>{{ money(discountCents / 100) }}</dd><dt>ยอดสุทธิ</dt><dd><strong>{{ money(netCents / 100) }}</strong></dd></dl>
        </template>
      </section>
      <section v-if="stage === 'review'" aria-label="ตรวจสอบก่อนยืนยัน">
        <h2>ตรวจสอบก่อนยืนยัน</h2>
        <p>{{ patient?.fullName }} · {{ branchCode }} · {{ selectedCourses.length }} คอร์ส</p>
        <ul><li v-for="course in selectedCourses" :key="course.id">{{ course.course.name }} / {{ course.id }}: {{ money(Number(amounts[course.id])) }}</li></ul>
        <p>ส่วนลด {{ money(discountCents / 100) }} · {{ labels.find((label) => label.code === labelCode)?.label }} · {{ allowedMethods.find((method) => method.code === methodCode)?.name }}</p>
        <p>รับเงินจริง {{ money(netCents / 100) }}</p>
        <button type="button" :disabled="submitting" @click="stage = 'edit'">กลับไปแก้ไข</button>
        <button type="button" :disabled="submitting" @click="confirmCollection">{{ submitting ? 'กำลังบันทึก…' : 'ยืนยันรับเงินและออกใบเสร็จ' }}</button>
      </section>
      <button v-else-if="stage === 'edit' && !loading && selectableCourses.length" type="button" @click="review">ตรวจสอบก่อนยืนยัน</button>
      <section v-if="stage === 'success' && result" aria-live="polite">
        <h2>รับเงินสำเร็จ</h2>
        <p>ใบเสร็จ {{ result.receiptNo }} · รับเงิน {{ money(result.netAmount) }}</p>
        <button type="button" @click="router.push({ name: 'staff-course-payment-receipt', params: { id: result.receiptId } })">ดูและพิมพ์ใบเสร็จ</button>
        <button type="button" @click="router.push({ name: 'patient-history', params: { id: patient?.id }, query: { tab: 'courses' } })">กลับประวัติคอร์ส</button>
      </section>
      <p v-if="stage === 'edit' && selectedIds.length" class="history-count">ประวัติใบเสร็จที่โหลดแล้ว: {{ historyRows.length }}</p>
    </main>
  </div>
</template>

<style scoped>
.course-payment-page main { max-width: 980px; margin: 0 auto; padding: 24px; }
.course-payment-item, section[aria-label="ตรวจสอบก่อนยืนยัน"] { border: 1px solid #d7dce2; border-radius: 12px; padding: 16px; margin: 12px 0; }
.course-payment-item > label, main > section > label { display: flex; gap: 10px; margin: 10px 0; }
input, select, button { min-height: 40px; margin: 4px; }
.payment-history { color: #4b5563; }
dl { display: grid; grid-template-columns: 1fr auto; max-width: 420px; }
</style>
