<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  branchApi,
  patientApi,
  salesApi,
  type Branch,
  type PatientProfile,
  type SaleDetail,
} from '@/api/client'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'

const route = useRoute()
const router = useRouter()
const receipt = ref<SaleDetail | null>(null)
const receiptBranch = ref<Branch | null>(null)
const receiptPatient = ref<PatientProfile | null>(null)
const loading = ref(true)
const error = ref('')
const printError = ref('')
const money = (value: number | undefined | null) =>
  value != null && Number.isFinite(value)
    ? new Intl.NumberFormat('th-TH', { style: 'currency', currency: 'THB' }).format(value)
    : '—'
const dateTime = (value?: string) =>
  value
    ? new Intl.DateTimeFormat('th-TH', {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: 'Asia/Bangkok',
      }).format(new Date(value))
    : '—'
const dateOnly = (value?: string) =>
  value
    ? new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Bangkok' }).format(new Date(value))
    : '—'
const itemDiscount = () =>
  receipt.value?.items.reduce((sum, line) => sum + (line.lineDiscountAmount ?? 0), 0) ?? 0
const amountWords = (value: number) => {
  if (!Number.isFinite(value) || value < 0) return '—'
  const digits = ['ศูนย์', 'หนึ่ง', 'สอง', 'สาม', 'สี่', 'ห้า', 'หก', 'เจ็ด', 'แปด', 'เก้า']
  const places = ['', 'สิบ', 'ร้อย', 'พัน', 'หมื่น', 'แสน']
  const group = (number: number) => {
    const characters = String(number).split('').map(Number)
    return characters
      .map((digit, index) => {
        const place = characters.length - index - 1
        if (!digit) return ''
        if (place === 1 && digit === 1) return 'สิบ'
        if (place === 1 && digit === 2) return 'ยี่สิบ'
        if (place === 0 && digit === 1 && characters.length > 1 && characters.at(-2) !== 0)
          return 'เอ็ด'
        return (digits[digit] ?? '') + (places[place] ?? '')
      })
      .join('')
  }
  const baht = Math.floor(value)
  const satang = Math.round((value - baht) * 100)
  const millions = Math.floor(baht / 1_000_000)
  const remainder = baht % 1_000_000
  const bahtText =
    baht === 0
      ? digits[0]
      : `${millions ? `${group(millions)}ล้าน` : ''}${remainder ? group(remainder) : ''}`
  return `${bahtText}บาท${satang ? `${group(satang)}สตางค์` : 'ถ้วน'}`
}
const paymentLabel = (value: SaleDetail['paymentLabel']) =>
  ({
    FullyPaid: 'ชำระเต็มจำนวน',
    Installment: 'ผ่อนชำระ',
    Deposit: 'มัดจำ',
  })[value] || value
const paymentMethod = (value: string) =>
  ({
    CreditCard: 'บัตรเครดิต/เดบิต',
    PromptPay: 'พร้อมเพย์',
    BankTransfer: 'โอนผ่านธนาคาร',
  })[value] || value

async function load() {
  loading.value = true
  error.value = ''
  try {
    const id = Number(route.params.id)
    if (!Number.isInteger(id) || id < 1) throw new Error('เลขอ้างอิงใบเสร็จไม่ถูกต้อง')
    const detail = await salesApi.get(id)
    if (route.name === 'patient-receipt' && detail.patient.id !== Number(route.params.patientId)) {
      throw new Error('ไม่พบใบเสร็จของผู้ป่วยรายนี้')
    }
    receipt.value = detail
    receiptPatient.value = null
    try {
      const [branches, patient] = await Promise.allSettled([
        branchApi.list(),
        patientApi.get(receipt.value.patient.id),
      ])
      receiptBranch.value =
        branches.status === 'fulfilled'
          ? (branches.value.find((branch) => branch.code === receipt.value?.branchCode) ?? null)
          : null
      receiptPatient.value = patient.status === 'fulfilled' ? patient.value : null
    } catch {
      receiptBranch.value = null
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'เปิดใบเสร็จไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
function printReceipt() {
  printError.value = ''
  try {
    window.print()
  } catch {
    printError.value = 'ส่งพิมพ์ไม่สำเร็จ กรุณาลองพิมพ์ใบเสร็จฉบับเดิมอีกครั้ง'
  }
}
function returnToPatientCourses() {
  if (route.name === 'patient-receipt') {
    void router.replace({
      name: 'patient-history',
      params: { id: route.params.patientId },
      query: { tab: route.query.tab === 'payments' ? 'payments' : 'courses' },
    })
    return
  }
  const receiptId = Number(route.params.id)
  if (route.query.from === 'transactions' && Number.isSafeInteger(receiptId) && receiptId > 0) {
    void router.replace({ name: 'staff-transactions', query: { sale: `sale-${receiptId}` } })
    return
  }
  const patientId = receipt.value?.patient.id
  if (patientId) {
    void router.push({ name: 'patient-history', params: { id: patientId } })
    return
  }
  router.back()
}
onMounted(load)
</script>

<template>
  <div class="receipt-view">
    <div class="receipt-screen-only"><StaffHeader /></div>
    <main>
      <div class="receipt-toolbar receipt-screen-only">
        <button class="receipt-back" type="button" @click="returnToPatientCourses">← กลับ</button>
        <button v-if="receipt" type="button" @click="printReceipt">พิมพ์ใบเสร็จ</button>
      </div>
      <p v-if="loading" role="status">กำลังโหลดใบเสร็จ…</p>
      <div v-else-if="error" role="alert">
        <p>{{ error }}</p>
        <button type="button" @click="load">ลองใหม่</button>
      </div>
      <article v-else-if="receipt" class="receipt-paper">
        <header class="receipt-header">
          <div class="receipt-mark" aria-hidden="true">
            <svg viewBox="0 0 72 72" focusable="false">
              <path
                fill="#000"
                d="M36 2C17 2 4 17 4 37v25c9 5 20 8 32 8s23-3 32-8V37C68 17 55 2 36 2Z"
              />
              <circle cx="36" cy="14" r="4" fill="#fff" />
              <path
                d="M13 25q23 13 46 0M36 29q-6 15-13 31m13-31q7 16 13 31"
                fill="none"
                stroke="#fff"
                stroke-width="4"
                stroke-linecap="round"
              />
            </svg>
          </div>
          <div class="receipt-clinic">
            <strong>{{ receipt.branch?.name || receiptBranch?.name || receipt.branchCode }}</strong>
            <p>ที่อยู่: {{ receipt.branch?.address || receiptBranch?.address || '—' }}</p>
            <p>
              โทร: {{ receipt.branch?.phone || receiptBranch?.phone || '—' }} อีเมล:
              {{ receipt.branch?.email || receiptBranch?.email || '—' }}
            </p>
            <p>
              เลขที่ใบอนุญาต:
              {{
                receipt.branch?.licenseNumber ||
                receiptBranch?.licenseNumber ||
                receiptBranch?.licenseNo ||
                '—'
              }}
            </p>
          </div>
          <div class="receipt-document">
            <h1>ใบเสร็จรับเงิน</h1>
            <div class="receipt-document-meta">
              <p><b>เลขที่เอกสาร:</b> {{ receipt.receiptNo }}</p>
              <p><b>วันที่ออกเอกสาร:</b> {{ dateOnly(receipt.soldAt) }}</p>
            </div>
          </div>
        </header>
        <section class="receipt-customer">
          <div><b>ผู้รับบริการ/ผู้บริโภค:</b> {{ receipt.patient.fullName }}</div>
          <div><b>รหัสลูกค้า:</b> {{ receipt.patient.hn }}</div>
          <div><b>เลขบัตรประชาชน:</b> {{ receiptPatient?.identityNumber || '—' }}</div>
          <div><b>โทร:</b> {{ receiptPatient?.phone || '—' }}</div>
          <div><b>อีเมล:</b> {{ receiptPatient?.email || '—' }}</div>
          <div class="receipt-customer-address">
            <b>ที่อยู่:</b> {{ receiptPatient?.address || '—' }}
          </div>
        </section>
        <div class="receipt-table-wrap">
          <table>
            <thead>
              <tr>
                <th>รายการ</th>
                <th>จำนวน</th>
                <th>ราคา/หน่วย</th>
                <th>หน่วย</th>
                <th>ส่วนลด/หน่วย</th>
                <th>ยอดรวม</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(line, index) in receipt.items" :key="line.id">
                <td>{{ index + 1 }}. {{ line.description }}</td>
                <td>{{ line.quantity }}</td>
                <td>{{ money(line.unitPrice) }}</td>
                <td>{{ line.unitName || (line.type === 'CoursePurchase' ? 'คอร์ส' : 'ครั้ง') }}</td>
                <td>
                  {{
                    money(
                      line.lineDiscountAmount != null
                        ? line.lineDiscountAmount / line.quantity
                        : null,
                    )
                  }}
                </td>
                <td>{{ money(line.lineTotal) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="receipt-bottom">
          <section class="receipt-amounts">
            <p>
              <span>ยอดทั้งหมด</span
              ><b>{{ money(receipt.subtotalBeforeDiscount + itemDiscount()) }}</b>
            </p>
            <p>
              <span>ส่วนลดท้ายบิล</span><b>{{ money(receipt.billDiscountAmount) }}</b>
            </p>
            <p>
              <span>มูลค่าก่อนภาษี</span><b>{{ money(receipt.taxableAmount) }}</b>
            </p>
            <p>
              <span>{{
                receipt.taxAmount === 0 ? 'ภาษีมูลค่าเพิ่ม (ไม่มีภาษี)' : 'ภาษีมูลค่าเพิ่ม'
              }}</span
              ><b>{{ money(receipt.taxAmount) }}</b>
            </p>
            <p>
              <span>ยอดชำระ</span><b>{{ money(receipt.paid) }}</b>
            </p>
          </section>
          <section class="receipt-settlement">
            <p>
              <b>สถานะการชำระ</b><span>{{ paymentLabel(receipt.paymentLabel) }}</span>
            </p>
            <p>
              <b>ช่องทางการชำระ</b
              ><span>{{
                receipt.payments
                  .map((payment) => paymentMethod(payment.paymentMethodCode))
                  .join(', ') || '—'
              }}</span>
            </p>
            <div class="receipt-net">
              <b>ยอดสุทธิ</b><strong>{{ money(receipt.netAmount) }}</strong>
            </div>
            <small>( {{ amountWords(receipt.netAmount) }} )</small>
          </section>
        </div>
        <section class="receipt-after-payment">
          <p>แต้มคงเหลือ: — <span>วงเงินคงเหลือ: —</span></p>
          <p>หมายเหตุ: {{ receipt.voidReason || receipt.note || '—' }}</p>
        </section>
        <section class="receipt-signatures">
          <div>
            <span class="signature-line"></span
            ><span>(....................................................)</span><b>ผู้รับบริการ</b>
          </div>
          <div>
            <span class="signature-line"></span
            ><span>(....................................................)</span><b>ผู้ทำรายการ</b>
          </div>
          <div>
            <span class="signature-line"></span
            ><span>(....................................................)</span
            ><b>ผู้มีอำนาจออกหลักฐานการรับเงิน</b>
          </div>
        </section>
        <footer class="receipt-footer">
          <span>หน้า 1 / 1</span><span>พิมพ์เมื่อ {{ dateTime(new Date().toISOString()) }}</span>
        </footer>
      </article>
      <p v-if="printError" class="receipt-screen-only" role="alert">{{ printError }}</p>
    </main>
  </div>
</template>

<style scoped>
.receipt-view {
  min-height: 100vh;
  background: #f8f4ed;
  color: #173c3b;
}
main {
  max-width: 1060px;
  margin: 0 auto;
  padding: 32px 20px 72px;
}
.receipt-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}
button {
  border: 1px solid #9dbfba;
  border-radius: 8px;
  padding: 10px 18px;
  background: #fff;
  color: #126b68;
  font: inherit;
  cursor: pointer;
}
button:last-child {
  background: #1c8681;
  color: #fff;
}
.receipt-toolbar .receipt-back {
  border-color: transparent;
  padding-inline: 0;
  background: transparent;
  color: #126b68;
}
button:focus-visible {
  outline: 2px solid #126b68;
  outline-offset: 2px;
}
.receipt-paper {
  background: #fff;
  border: 1px solid #d8e4df;
  border-radius: 12px;
  padding: clamp(24px, 4vw, 48px);
}
.receipt-paper header {
  display: flex;
  justify-content: space-between;
  gap: 32px;
}
h1 {
  font-size: 28px;
  margin: 0 0 16px;
}
h2 {
  font-size: 18px;
  margin: 0 0 10px;
}
.receipt-paper p {
  margin: 5px 0;
}
dl {
  margin: 0;
  min-width: 240px;
}
dl div,
.receipt-total p {
  display: flex;
  justify-content: space-between;
  gap: 24px;
}
dt {
  color: #587575;
}
dd {
  margin: 0;
  text-align: right;
}
.receipt-patient,
.receipt-payment {
  border-top: 1px solid #d9e6e2;
  margin-top: 24px;
  padding-top: 20px;
}
.receipt-patient {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
}
.receipt-table-wrap {
  overflow-x: auto;
  margin-top: 24px;
}
table {
  border-collapse: collapse;
  width: 100%;
  min-width: 600px;
}
th,
td {
  padding: 12px 8px;
  border-bottom: 1px solid #e1ebe7;
  text-align: right;
}
th:first-child,
td:first-child {
  text-align: left;
}
th {
  background: #edf5f1;
}
td small {
  color: #587575;
}
.receipt-total {
  max-width: 380px;
  margin: 20px 0 0 auto;
}
.receipt-total p:nth-child(3) {
  border-top: 1px solid #b6cbc5;
  padding-top: 12px;
  font-size: 19px;
}
@media (max-width: 640px) {
  .receipt-paper header {
    flex-direction: column;
  }
  dl {
    min-width: 0;
  }
}
@media print {
  .receipt-screen-only {
    display: none !important;
  }
  .receipt-view {
    background: #fff;
  }
  main {
    max-width: none;
    padding: 0;
  }
  .receipt-paper {
    border: 0;
    padding: 0;
  }
  @page {
    margin: 16mm;
  }
}
</style>

<style scoped>
.receipt-paper {
  width: min(210mm, 100%);
  min-height: 297mm;
  margin: 0 auto;
  padding: 8mm 7mm 5mm;
  border-radius: 0;
  color: #111;
  font-size: 10px;
  line-height: 1.45;
  display: flex;
  flex-direction: column;
}
.receipt-paper .receipt-header {
  display: grid;
  grid-template-columns: 23mm minmax(0, 1fr) 57mm;
  gap: 3mm;
  min-height: 36mm;
  align-items: start;
}
.receipt-mark svg {
  width: 21mm;
  height: 21mm;
}
.receipt-clinic strong {
  display: block;
  font-size: 14px;
  margin-bottom: 2px;
}
.receipt-clinic p,
.receipt-document-meta p {
  margin: 1px 0;
}
.receipt-document {
  text-align: right;
}
.receipt-document h1 {
  color: #17a899;
  font-size: 19px;
  margin: 0 0 3mm;
  white-space: nowrap;
}
.receipt-document-meta {
  display: inline-block;
  background: #d7faf5;
  padding: 2mm 2.5mm;
  text-align: right;
}
.receipt-customer {
  border-top: 1px solid #111;
  padding-top: 5mm;
  display: grid;
  grid-template-columns: 56% 44%;
  row-gap: 1mm;
}
.receipt-customer-address {
  grid-column: 1 / -1;
}
.receipt-table-wrap {
  margin-top: 5mm;
  overflow: visible;
  flex: 1;
}
.receipt-table-wrap table {
  min-width: 0;
  width: 100%;
  table-layout: fixed;
}
.receipt-table-wrap th,
.receipt-table-wrap td {
  border: 0;
  padding: 2mm 1mm;
  font-size: 9px;
}
.receipt-table-wrap th {
  background: #08a99c;
  color: #111;
  font-weight: 700;
  text-align: center;
}
.receipt-table-wrap th:first-child {
  width: 46%;
  text-align: center;
}
.receipt-table-wrap th:nth-child(2) {
  width: 8%;
}
.receipt-table-wrap th:nth-child(3) {
  width: 14%;
}
.receipt-table-wrap th:nth-child(4) {
  width: 9%;
}
.receipt-table-wrap th:nth-child(5) {
  width: 12%;
}
.receipt-table-wrap th:nth-child(6) {
  width: 13%;
}
.receipt-table-wrap td:first-child {
  text-align: left;
}
.receipt-table-wrap td:nth-child(2),
.receipt-table-wrap td:nth-child(4) {
  text-align: center;
}
.receipt-bottom {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1mm;
  border-top: 1px solid #111;
  padding-top: 3mm;
}
.receipt-amounts,
.receipt-settlement {
  background: #d7faf5;
  padding: 3mm;
  min-height: 34mm;
}
.receipt-amounts p,
.receipt-settlement p {
  display: flex;
  justify-content: space-between;
  gap: 2mm;
  margin: 1px 0;
}
.receipt-settlement {
  text-align: right;
}
.receipt-settlement p span {
  text-align: right;
}
.receipt-net {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-top: 5mm;
}
.receipt-net b {
  font-size: 12px;
}
.receipt-net strong {
  font-size: 17px;
}
.receipt-settlement small {
  display: block;
  font-size: 9px;
}
.receipt-after-payment {
  border-top: 1px solid #111;
  margin-top: 3mm;
  padding-top: 7mm;
  min-height: 26mm;
}
.receipt-after-payment p span {
  margin-left: 15mm;
}
.receipt-signatures {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 18mm;
  row-gap: 9mm;
  text-align: center;
  padding: 0 10mm;
}
.receipt-signatures > div {
  display: grid;
  justify-items: center;
  gap: 2mm;
}
.receipt-signatures > div:last-child {
  grid-column: 1 / -1;
  width: 50%;
  justify-self: center;
}
.signature-line {
  display: block;
  border-bottom: 1px dotted #555;
  width: 100%;
  height: 12mm;
}
.receipt-signatures b {
  font-weight: 400;
}
.receipt-footer {
  margin-top: auto;
  padding-top: 8mm;
  display: flex;
  justify-content: center;
  position: relative;
  font-size: 8px;
}
.receipt-footer span:last-child {
  position: absolute;
  right: 0;
  bottom: 0;
}
@media print {
  @page {
    size: A4 portrait;
    margin: 0;
  }
  .receipt-view,
  main {
    background: #fff;
    margin: 0;
    padding: 0;
  }
  .receipt-paper {
    border: 0;
    width: 210mm;
    min-height: 296mm;
    padding: 8mm 7mm 5mm;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }
  .receipt-table-wrap {
    overflow: visible;
  }
}
@media screen and (max-width: 700px) {
  .receipt-paper {
    min-width: 680px;
  }
  main {
    overflow-x: auto;
  }
}
</style>
