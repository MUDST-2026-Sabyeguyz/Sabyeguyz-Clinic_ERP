<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import {
  courseApi,
  refundApi,
  salesApi,
  type CoursePurchase,
  type RefundRow,
  type SaleDetail,
  type SaleSummary,
} from '@/api/client'

type CourseLine = {
  courseId: number
  name: string
  courseNo: string
  remaining: number
  paid: number
  include: boolean
  amount: number
  visits: number
}

const route = useRoute()
const search = ref('')
const receipts = ref<SaleSummary[]>([])
const detail = ref<SaleDetail | null>(null)
const lines = ref<CourseLine[]>([])
const refunds = ref<RefundRow[]>([])
const paymentId = ref(0)
const reason = ref('')
const manualAmount = ref(0)
const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const notice = ref('')

const payments = computed(() => detail.value?.payments.filter((p) => p.type !== 'REFUND') ?? [])
const payment = computed(() => payments.value.find((p) => p.id === paymentId.value) ?? null)
const hasCourses = computed(() => lines.value.length > 0)
const total = computed(() =>
  hasCourses.value
    ? lines.value.filter((l) => l.include).reduce((sum, l) => sum + Number(l.amount || 0), 0)
    : Number(manualAmount.value || 0),
)
const money = (value: number) =>
  `฿${Number(value).toLocaleString('th-TH', { minimumFractionDigits: 2 })}`
const visible = computed(() => {
  const term = search.value.trim().toLocaleLowerCase()
  return receipts.value.filter(
    (row) =>
      !term ||
      `${row.receiptNo} ${row.patient.fullName} ${row.patient.hn}`
        .toLocaleLowerCase()
        .includes(term),
  )
})
const nameOf = (courseId: number) =>
  lines.value.find((line) => line.courseId === courseId)?.name ?? `คอร์ส #${courseId}`

async function loadRefunds() {
  refunds.value = payment.value ? await refundApi.forPayment(payment.value.id) : []
}

async function loadReceipts() {
  loading.value = true
  try {
    receipts.value = (await salesApi.list({ receiptsOnly: true, size: 100 })).items
  } catch (caught) {
    errorMessage.value = caught instanceof Error ? caught.message : 'โหลดใบเสร็จไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}

async function choose(id: number) {
  errorMessage.value = ''
  notice.value = ''
  try {
    const loaded = await salesApi.get(id)
    detail.value = loaded
    paymentId.value = loaded.payments.find((p) => p.type !== 'REFUND')?.id ?? 0
    const courses = await courseApi.patientCourses(loaded.patient.id)
    lines.value = courses
      .filter((course: CoursePurchase) => course.receiptId === loaded.receiptId)
      .map((course: CoursePurchase) => ({
        courseId: course.id,
        name: course.course.name,
        courseNo: course.course.code,
        remaining: course.remaining,
        paid: course.price,
        include: false,
        amount: 0,
        visits: course.remaining,
      }))
    reason.value = ''
    manualAmount.value = 0
    await loadRefunds()
  } catch (caught) {
    errorMessage.value = caught instanceof Error ? caught.message : 'โหลดรายละเอียดใบเสร็จไม่สำเร็จ'
  }
}

async function submit() {
  errorMessage.value = ''
  notice.value = ''
  const selectedPayment = payment.value
  if (!selectedPayment) {
    errorMessage.value = 'ใบเสร็จนี้ไม่มีรายการชำระเงินที่คืนได้'
    return
  }
  if (!reason.value.trim()) {
    errorMessage.value = 'กรุณาระบุเหตุผลการคืนเงิน'
    return
  }
  if (!(total.value > 0)) {
    errorMessage.value = hasCourses.value ? 'กรุณาเลือกคอร์สและระบุยอดคืน' : 'กรุณาระบุยอดที่คืน'
    return
  }
  const chosen = lines.value.filter((line) => line.include)
  if (hasCourses.value && chosen.some((l) => !(l.amount > 0) || !(l.visits >= 1))) {
    errorMessage.value =
      'ทุกคอร์สที่เลือกต้องมียอดคืนมากกว่า 0 และจำนวนสิทธิ์ที่ยกเลิกอย่างน้อย 1 ครั้ง'
    return
  }
  submitting.value = true
  try {
    await refundApi.request(selectedPayment.id, {
      amount: Math.round(total.value * 100) / 100,
      paymentMethodCode: selectedPayment.paymentMethodCode,
      reason: reason.value.trim(),
      items: hasCourses.value
        ? chosen.map((l) => ({
            courseId: l.courseId,
            refundAmount: Number(l.amount),
            cancelledVisits: Number(l.visits),
          }))
        : undefined,
    })
    notice.value = 'บันทึกคำขอคืนเงินแล้ว (รอดำเนินการ) สิทธิ์จะถูกตัดเมื่อยืนยันการคืนเงิน'
    lines.value.forEach((line) => {
      line.include = false
      line.amount = 0
    })
    reason.value = ''
    await loadRefunds()
  } catch (caught) {
    errorMessage.value = caught instanceof Error ? caught.message : 'บันทึกคำขอคืนเงินไม่สำเร็จ'
  } finally {
    submitting.value = false
  }
}

async function act(row: RefundRow, action: 'complete' | 'fail' | 'void') {
  errorMessage.value = ''
  notice.value = ''
  try {
    await refundApi[action](row.id)
    notice.value =
      action === 'complete'
        ? 'ยืนยันการคืนเงินแล้ว และตัดสิทธิ์คอร์สที่เลือก'
        : action === 'fail'
          ? 'บันทึกว่าคืนเงินไม่สำเร็จ ไม่มีการตัดสิทธิ์'
          : 'ยกเลิกคำขอคืนเงินแล้ว'
    if (detail.value) await choose(detail.value.id)
  } catch (caught) {
    errorMessage.value = caught instanceof Error ? caught.message : 'ดำเนินการไม่สำเร็จ'
  }
}

onMounted(async () => {
  await loadReceipts()
  const initial = Number(route.query.receipt)
  if (Number.isInteger(initial) && initial > 0) await choose(initial)
})

const statusLabel: Record<RefundRow['status'], string> = {
  Pending: 'รอดำเนินการ',
  Completed: 'คืนเงินแล้ว',
  Failed: 'คืนเงินไม่สำเร็จ',
  Voided: 'ยกเลิกคำขอ',
}
</script>

<template>
  <div class="refund-page">
    <StaffHeader />
    <main>
      <header class="head">
        <h1>คืนเงิน</h1>
        <p>
          ใบเสร็จที่มีคอร์สต้องเลือกคอร์สที่คืน ระบุยอดคืนและจำนวนสิทธิ์ที่ยังไม่ใช้ที่จะยกเลิก
          ค่ามือและค่าคอมมิชชันของครั้งที่ใช้ไปแล้วจะไม่ถูกเรียกคืน
        </p>
      </header>
      <p v-if="errorMessage" class="page-error" role="alert">{{ errorMessage }}</p>
      <p v-if="notice" class="notice" role="status">{{ notice }}</p>

      <section class="panel">
        <h2>เลือกใบเสร็จ</h2>
        <input
          v-model="search"
          placeholder="ค้นหาเลขที่ใบเสร็จ ชื่อ หรือ HN"
          aria-label="ค้นหาใบเสร็จ"
        />
        <table>
          <thead>
            <tr>
              <th>เลขที่</th>
              <th>ผู้ป่วย</th>
              <th>ยอด</th>
              <th>สถานะ</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visible" :key="row.id">
              <td>{{ row.receiptNo }}</td>
              <td>{{ row.patient.fullName }} ({{ row.patient.hn }})</td>
              <td>{{ money(row.total) }}</td>
              <td>{{ row.status === 'VOIDED' ? 'ยกเลิก' : 'ปกติ' }}</td>
              <td>
                <button
                  :disabled="row.status === 'VOIDED'"
                  :aria-label="`เลือกใบเสร็จ ${row.receiptNo}`"
                  @click="choose(row.id)"
                >
                  เลือก
                </button>
              </td>
            </tr>
            <tr v-if="!loading && visible.length === 0">
              <td colspan="5">ไม่พบใบเสร็จ</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section v-if="detail" class="panel" aria-label="คำขอคืนเงิน">
        <h2>ใบเสร็จ {{ detail.receiptNo }} · {{ detail.patient.fullName }}</h2>
        <label v-if="payments.length > 1"
          >รายการชำระเงิน
          <select v-model.number="paymentId" @change="loadRefunds">
            <option v-for="p in payments" :key="p.id" :value="p.id">
              {{ p.paymentMethodCode }} · {{ money(p.amount) }}
            </option>
          </select></label
        >
        <p v-if="payment">ชำระแล้ว {{ money(payment.amount) }} ({{ payment.paymentMethodCode }})</p>
        <p v-else class="page-error">ใบเสร็จนี้ไม่มีรายการชำระเงินที่คืนได้</p>

        <table v-if="hasCourses" class="courses">
          <thead>
            <tr>
              <th>คืน</th>
              <th>คอร์ส</th>
              <th>ราคา</th>
              <th>สิทธิ์ที่ยังไม่ใช้</th>
              <th>ยอดคืน (บาท)</th>
              <th>ยกเลิกสิทธิ์ (ครั้ง)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="line in lines" :key="line.courseId">
              <td>
                <input
                  v-model="line.include"
                  type="checkbox"
                  :aria-label="`คืนคอร์ส ${line.name}`"
                  :disabled="line.remaining < 1"
                />
              </td>
              <td>{{ line.name }}</td>
              <td>{{ money(line.paid) }}</td>
              <td>{{ line.remaining }}</td>
              <td>
                <input
                  v-model.number="line.amount"
                  type="number"
                  min="0.01"
                  step="0.01"
                  :disabled="!line.include"
                  :aria-label="`ยอดคืน ${line.name}`"
                />
              </td>
              <td>
                <input
                  v-model.number="line.visits"
                  type="number"
                  min="1"
                  :max="line.remaining"
                  :disabled="!line.include"
                  :aria-label="`ยกเลิกสิทธิ์ ${line.name}`"
                />
              </td>
            </tr>
          </tbody>
        </table>
        <label v-else
          >ยอดที่คืน (บาท)<input v-model.number="manualAmount" type="number" min="0.01" step="0.01"
        /></label>

        <label
          >เหตุผล<input
            v-model="reason"
            maxlength="255"
            placeholder="เช่น ผู้ป่วยขอคืนสิทธิ์ที่เหลือ"
        /></label>
        <p class="total">
          ยอดคืนรวม <b>{{ money(total) }}</b>
        </p>
        <button class="primary" :disabled="submitting || !payment" @click="submit">
          {{ submitting ? 'กำลังบันทึก...' : 'บันทึกคำขอคืนเงิน' }}
        </button>

        <h3>คำขอคืนเงินของรายการชำระนี้</h3>
        <table>
          <thead>
            <tr>
              <th>ยอด</th>
              <th>คอร์ส</th>
              <th>สถานะ</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in refunds" :key="row.id">
              <td>{{ money(row.amount) }}</td>
              <td>
                <template v-if="row.items.length">
                  <span v-for="item in row.items" :key="item.courseId"
                    >{{ nameOf(item.courseId) }} {{ money(item.refundAmount) }} (ตัด
                    {{ item.cancelledVisits }} ครั้ง)
                  </span>
                </template>
                <template v-else>—</template>
              </td>
              <td>{{ statusLabel[row.status] }}</td>
              <td class="actions">
                <template v-if="row.status === 'Pending'">
                  <button class="primary" @click="act(row, 'complete')">ยืนยันคืนเงิน</button
                  ><button @click="act(row, 'fail')">คืนเงินไม่สำเร็จ</button
                  ><button @click="act(row, 'void')">ยกเลิกคำขอ</button>
                </template>
              </td>
            </tr>
            <tr v-if="refunds.length === 0">
              <td colspan="4">ยังไม่มีคำขอคืนเงิน</td>
            </tr>
          </tbody>
        </table>
      </section>
    </main>
  </div>
</template>

<style scoped>
@import '../../staff/styles/staff.css';
.refund-page {
  min-height: 100vh;
  background: #f8f4ec;
  color: #25372f;
}
main {
  width: min(1200px, calc(100% - 48px));
  margin: auto;
  padding: 44px 0 72px;
  display: grid;
  gap: 22px;
}
.head h1 {
  margin: 0;
}
.head p {
  margin: 6px 0 0;
  color: #667a72;
}
.panel {
  padding: 22px;
  border: 1px solid #d9e1dc;
  border-radius: 12px;
  background: #fff;
  display: grid;
  gap: 14px;
}
.panel h2,
.panel h3 {
  margin: 0;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th {
  padding: 10px 12px;
  background: #16877f;
  color: #fff;
  text-align: left;
}
td {
  padding: 10px 12px;
  border-bottom: 1px solid #e6ebe8;
}
input,
select {
  min-height: 40px;
  padding: 0 12px;
  border: 1px solid #b7cecc;
  border-radius: 9px;
  font: inherit;
  box-sizing: border-box;
}
label {
  display: grid;
  gap: 6px;
  color: #52635b;
}
button {
  min-height: 40px;
  padding: 0 16px;
  border: 1px solid #d9e1dc;
  border-radius: 8px;
  background: #fff;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
button.primary {
  border-color: #1e8b86;
  background: #1e8b86;
  color: #fff;
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.total {
  margin: 0;
  font-size: 1.1rem;
}
.page-error {
  padding: 11px;
  border-radius: 8px;
  background: #fff1ef;
  color: #b42318;
}
.notice {
  padding: 11px;
  border-radius: 8px;
  background: #e8f6ef;
  color: #16875b;
}
.courses {
  min-width: 0;
}
@media (max-width: 700px) {
  main {
    width: calc(100% - 32px);
  }
  .panel {
    overflow-x: auto;
  }
}
</style>
