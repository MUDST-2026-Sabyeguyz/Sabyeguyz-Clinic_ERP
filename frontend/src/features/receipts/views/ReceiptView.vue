<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import {
  branchApi,
  patientApi,
  salesApi,
  type Branch,
  type PatientProfile,
  type SaleDetail,
} from '@/api/client'
import { formatPaymentLabel, formatPaymentMethod } from '@/features/payments/format'
const route = useRoute()
const receipt = ref<SaleDetail | null>(null)
const branch = ref<Branch | null>(null)
const patient = ref<PatientProfile | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const printError = ref('')
let generation = 0
const money = (value: number | undefined) =>
  new Intl.NumberFormat('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(
    value!,
  )
const date = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  )
async function printReceipt() {
  if (!receipt.value || loading.value) return
  const current = generation
  await nextTick()
  if (current !== generation) return
  try {
    window.print()
  } catch {
    printError.value = 'ไม่สามารถเปิดหน้าต่างพิมพ์ได้ กรุณาลองอีกครั้ง'
  }
}
async function load() {
  const current = ++generation
  receipt.value = null
  branch.value = null
  patient.value = null
  loading.value = true
  errorMessage.value = ''
  printError.value = ''
  try {
    const id = Number(route.params.id)
    if (!Number.isInteger(id) || id <= 0) throw new Error('รหัสใบเสร็จไม่ถูกต้อง')
    const detail = await salesApi.get(id)
    if (
      ![
        detail.subtotalBeforeDiscount,
        detail.billDiscountAmount,
        detail.taxableAmount,
        detail.taxAmount,
        detail.netAmount,
        detail.paid,
      ].every((value) => typeof value === 'number' && Number.isFinite(value)) ||
      !detail.receiptNo ||
      !Number.isFinite(Date.parse(detail.soldAt))
    )
      throw new Error('ข้อมูลใบเสร็จที่บันทึกไว้ไม่ครบถ้วน')
    if (current !== generation) return
    receipt.value = detail
    // Customer and branch enrichment are independent of the persisted receipt document.
    await Promise.allSettled([
      branchApi.listActive().then((items) => {
        if (current === generation)
          branch.value = items.find((item) => item.branchCode === detail.branchCode) ?? null
      }),
      patientApi.get(detail.patient.id).then((profile) => {
        if (current === generation) patient.value = profile
      }),
    ])
  } catch (error) {
    if (current === generation)
      errorMessage.value = error instanceof Error ? error.message : 'โหลดใบเสร็จไม่สำเร็จ'
  } finally {
    if (current === generation) loading.value = false
  }
  if (current === generation && receipt.value && route.query.print === '1') await printReceipt()
}
onBeforeUnmount(() => {
  generation++
})
onMounted(load)
watch(() => route.params.id, load)
</script>
<template>
  <div class="receipt-page">
    <div class="receipt-navigation"><StaffHeader /></div>
    <main>
      <p v-if="loading">กำลังโหลดใบเสร็จ...</p>
      <section v-else-if="errorMessage" role="alert">
        <p>{{ errorMessage }}</p>
        <button type="button" @click="load">โหลดใบเสร็จอีกครั้ง</button>
      </section>
      <template v-else-if="receipt">
        <article class="printable-receipt">
          <header>
            <h1>ใบเสร็จรับเงิน</h1>
            <h2>สบายกาย คลินิกกายภาพบำบัด · {{ branch?.name || receipt.branchCode }}</h2>
            <p>ที่อยู่: {{ branch?.address || '—' }}</p>
            <p>โทร: {{ branch?.phone || '—' }}</p>
            <p>เลขที่เอกสาร: {{ receipt.receiptNo }}</p>
            <p>วันที่ออกเอกสาร: {{ date(receipt.soldAt) }}</p>
            <p>สถานะ: {{ receipt.status }}</p>
          </header>
          <section>
            <p>ผู้รับบริการ: {{ receipt.patient.fullName }}</p>
            <p>HN {{ receipt.patient.hn }}</p>
            <p>โทร: {{ receipt.patient.phone || '—' }}</p>
            <p>ที่อยู่: {{ patient?.address || '—' }}</p>
          </section>
          <table>
            <thead>
              <tr>
                <th>รายการ</th>
                <th>ประเภท</th>
                <th>จำนวน</th>
                <th>ยอดรายการ</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in receipt.items" :key="item.id">
                <td>{{ item.description }}</td>
                <td>{{ item.type }}</td>
                <td>{{ item.quantity }}</td>
                <td>{{ money(item.lineTotal) }}</td>
              </tr>
            </tbody>
          </table>
          <dl>
            <div>
              <dt>ยอดก่อนส่วนลดท้ายบิล</dt>
              <dd>{{ money(receipt.subtotalBeforeDiscount) }}</dd>
            </div>
            <div>
              <dt>ส่วนลดท้ายบิล</dt>
              <dd data-test="discount">{{ money(receipt.billDiscountAmount) }}</dd>
            </div>
            <div>
              <dt>มูลค่าก่อนภาษี</dt>
              <dd>{{ money(receipt.taxableAmount) }}</dd>
            </div>
            <div>
              <dt>ภาษี</dt>
              <dd data-test="tax">{{ money(receipt.taxAmount) }}</dd>
            </div>
            <div>
              <dt>ยอดสุทธิ</dt>
              <dd data-test="net">{{ money(receipt.netAmount) }}</dd>
            </div>
            <div>
              <dt>รับชำระแล้ว</dt>
              <dd data-test="paid">{{ money(receipt.paid) }}</dd>
            </div>
            <div>
              <dt>ประเภทการชำระเงิน</dt>
              <dd>{{ formatPaymentLabel(receipt.paymentLabel) }}</dd>
            </div>
            <div>
              <dt>ช่องทางชำระเงิน</dt>
              <dd>
                {{
                  receipt.payments
                    .filter((p) => p.type !== 'REFUND')
                    .map((p) => formatPaymentMethod(p.paymentMethodCode))
                    .join(', ') || '—'
                }}
              </dd>
            </div>
          </dl>
          <p>หมายเหตุ: {{ receipt.note || '—' }}</p>
        </article>
        <nav class="receipt-navigation">
          <button type="button" data-test="print" @click="printReceipt">พิมพ์เอกสาร</button
          ><RouterLink :to="`/staff/patients/${receipt.patient.id}/history`"
            >ประวัติใบเสร็จ</RouterLink
          ><RouterLink :to="{ path: '/staff/courses', query: { patientId: receipt.patient.id } }"
            >ดูคอร์สผู้ป่วย</RouterLink
          ><RouterLink :to="{ path: '/staff/services', query: { patientId: receipt.patient.id } }"
            >กลับหน้าบริการ</RouterLink
          >
          <p v-if="printError" role="alert">{{ printError }}</p>
        </nav>
      </template>
    </main>
  </div>
</template>
<style scoped>
main {
  max-width: 960px;
  margin: 2rem auto;
  padding: 1.5rem;
  background: white;
}
.printable-receipt {
  color: #111;
}
table {
  width: 100%;
  border-collapse: collapse;
  margin: 1rem 0;
}
th,
td {
  padding: 0.7rem;
  border-bottom: 1px solid #ddd;
  text-align: left;
}
dl {
  margin-left: auto;
  max-width: 450px;
}
dl div {
  display: flex;
  justify-content: space-between;
  gap: 2rem;
  margin: 0.6rem 0;
}
.receipt-navigation {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  margin-top: 1rem;
}
@media print {
  .receipt-navigation {
    display: none !important;
  }
  main {
    margin: 0;
    padding: 0;
    max-width: none;
  }
}
</style>
