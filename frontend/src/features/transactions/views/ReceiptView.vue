<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { branchApi, salesApi, type Branch, type SaleDetail } from '@/api/client'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'

const route = useRoute()
const router = useRouter()
const receipt = ref<SaleDetail | null>(null)
const receiptBranch = ref<Branch | null>(null)
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
    receipt.value = await salesApi.get(id)
    try {
      receiptBranch.value =
        (await branchApi.list()).find((branch) => branch.code === receipt.value?.branchCode) ?? null
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
onMounted(load)
</script>

<template>
  <div class="receipt-view">
    <StaffHeader class="receipt-screen-only" />
    <main>
      <div class="receipt-toolbar receipt-screen-only">
        <button type="button" @click="router.back()">← กลับ</button>
        <button v-if="receipt" type="button" @click="printReceipt">พิมพ์ใบเสร็จ</button>
      </div>
      <p v-if="loading" role="status">กำลังโหลดใบเสร็จ…</p>
      <div v-else-if="error" role="alert">
        <p>{{ error }}</p>
        <button type="button" @click="load">ลองใหม่</button>
      </div>
      <article v-else-if="receipt" class="receipt-paper">
        <header>
          <div>
            <h1>ใบเสร็จรับเงิน</h1>
            <strong>{{ receipt.branch?.name || receiptBranch?.name || receipt.branchCode }}</strong>
            <p>{{ receipt.branch?.address || receiptBranch?.address || '—' }}</p>
            <p>
              โทร {{ receipt.branch?.phone || receiptBranch?.phone || '—'
              }}<template v-if="receipt.branch?.email"> · {{ receipt.branch?.email }}</template>
            </p>
            <p v-if="receipt.branch?.licenseNumber">
              เลขใบอนุญาต {{ receipt.branch?.licenseNumber }}
            </p>
          </div>
          <dl>
            <div>
              <dt>เลขใบเสร็จ</dt>
              <dd>{{ receipt.receiptNo }}</dd>
            </div>
            <div>
              <dt>สาขา</dt>
              <dd>{{ receipt.branchCode }}</dd>
            </div>
            <div>
              <dt>วันเวลา</dt>
              <dd>{{ dateTime(receipt.soldAt) }}</dd>
            </div>
            <div>
              <dt>สถานะ</dt>
              <dd>{{ receipt.status === 'VOIDED' ? 'ยกเลิกแล้ว' : 'ออกใบเสร็จแล้ว' }}</dd>
            </div>
          </dl>
        </header>
        <section class="receipt-patient">
          <span>ผู้ป่วย</span><strong>{{ receipt.patient.fullName }}</strong
          ><span>HN {{ receipt.patient.hn }}</span>
        </section>
        <div class="receipt-table-wrap">
          <table>
            <thead>
              <tr>
                <th>รายการ</th>
                <th>ราคา/หน่วย</th>
                <th>จำนวน</th>
                <th>ส่วนลด</th>
                <th>สุทธิ</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="line in receipt.items" :key="line.id">
                <td>
                  {{ line.description }}
                  <small
                    >({{
                      line.unitName || (line.type === 'CoursePurchase' ? 'คอร์ส' : 'รายการ')
                    }})</small
                  >
                </td>
                <td>{{ money(line.unitPrice) }}</td>
                <td>{{ line.quantity }}</td>
                <td>{{ money(line.lineDiscountAmount) }}</td>
                <td>{{ money(line.lineTotal) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <section class="receipt-total">
          <p>
            <span>ยอดหลังส่วนลดรายการ</span
            ><strong>{{ money(receipt.subtotalBeforeDiscount) }}</strong>
          </p>
          <p>
            <span>ส่วนลดท้ายบิล</span><strong>− {{ money(receipt.billDiscountAmount) }}</strong>
          </p>
          <p>
            <span>ยอดสุทธิ</span><strong>{{ money(receipt.netAmount) }}</strong>
          </p>
          <p>
            <span>รับชำระ</span><strong>{{ money(receipt.paid) }}</strong>
          </p>
        </section>
        <section class="receipt-payment">
          <h2>การชำระเงิน</h2>
          <p>ลักษณะการชำระ: {{ paymentLabel(receipt.paymentLabel) }}</p>
          <p v-for="payment in receipt.payments" :key="payment.id">
            {{ payment.type === 'PAYMENT' ? 'รับชำระ' : 'ยกเลิกรายการรับชำระ' }}
            {{ money(payment.amount) }} · {{ paymentMethod(payment.paymentMethodCode) }} ·
            {{ dateTime(payment.paidAt) }}
          </p>
          <p v-if="receipt.voidReason">เหตุผลที่ยกเลิก: {{ receipt.voidReason }}</p>
          <p v-if="receipt.note">หมายเหตุ: {{ receipt.note }}</p>
        </section>
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
