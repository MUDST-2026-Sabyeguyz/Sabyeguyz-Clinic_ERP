<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { receiptDocumentsApi, type ReceiptDocument } from '@/api/client'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'

const route = useRoute()
const router = useRouter()
const document = ref<ReceiptDocument | null>(null)
const error = ref('')
const loading = ref(true)
const money = (amount: number) => new Intl.NumberFormat('th-TH', { style: 'currency', currency: 'THB' }).format(amount)
const localDate = (value: string) => new Intl.DateTimeFormat('th-TH', {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Bangkok',
}).format(new Date(value))
async function load() {
  loading.value = true
  error.value = ''
  try {
    const id = Number(route.params.id)
    if (!Number.isSafeInteger(id) || id < 1) throw new Error('เลขใบเสร็จไม่ถูกต้อง')
    document.value = await receiptDocumentsApi.get(id)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'เปิดใบเสร็จไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
function printReceipt() {
  try {
    window.print()
  } catch {
    error.value = 'สั่งพิมพ์ใบเสร็จไม่สำเร็จ'
  }
}
onMounted(load)
</script>

<template>
  <div class="staff-page">
    <div class="screen-only"><StaffHeader /></div>
    <main class="receipt-page">
      <div class="screen-only"><button type="button" @click="router.back()">กลับ</button><button type="button" @click="printReceipt">พิมพ์ใบเสร็จ</button></div>
      <p v-if="loading" role="status">กำลังโหลดใบเสร็จ…</p>
      <div v-else-if="error" role="alert"><p>{{ error }}</p><button type="button" @click="load">ลองใหม่</button></div>
      <article v-else-if="document" class="receipt">
        <header><h1>ใบเสร็จรับเงิน</h1><strong>{{ document.receiptNo }}</strong><p>{{ document.branch.name }} · {{ document.branch.code }}</p><p>{{ document.branch.address }} · {{ document.branch.phone }}</p></header>
        <hr />
        <p>ผู้ซื้อเดิม: {{ document.patient.fullName }} · HN {{ document.patient.hn }}</p>
        <p>วันที่ออก: {{ localDate(document.saleDate) }}</p>
        <p>ลักษณะการชำระ: {{ document.paymentLabelName }}</p>
        <table v-if="document.sales.length"><thead><tr><th>รายการขาย</th><th>จำนวน</th><th>ยอดรายการ</th></tr></thead>
          <tbody><tr v-for="item in document.sales" :key="item.transactionId"><td>{{ item.itemNameSnapshot }}</td><td>{{ item.quantity }}</td><td>{{ money(item.amount) }}</td></tr></tbody>
        </table>
        <table v-if="document.coursePayments.length"><thead><tr><th>คอร์สเดิม</th><th>ยอดรับก่อนส่วนลด</th><th>ส่วนลดท้ายบิล</th><th>ยอดสุทธิ</th></tr></thead>
          <tbody><tr v-for="item in document.coursePayments" :key="item.courseId"><td>{{ item.itemNameSnapshot }} · {{ item.courseId }}</td><td>{{ money(item.amount) }}</td><td>{{ money(item.allocatedBillDiscountAmount) }}</td><td>{{ money(item.netAmount) }}</td></tr></tbody>
        </table>
        <dl><dt>ยอดก่อนส่วนลด</dt><dd>{{ money(document.subtotalBeforeDiscount) }}</dd><dt>ส่วนลดรวม</dt><dd>{{ money(document.billDiscountAmount) }}</dd><dt>ยอดสุทธิ</dt><dd><strong>{{ money(document.netAmount) }}</strong></dd></dl>
        <h2>รับชำระ</h2><ul><li v-for="payment in document.payments" :key="payment.paymentId">{{ localDate(payment.paymentDate) }} · {{ payment.paymentMethod }} · {{ money(payment.amount) }} · {{ payment.status }}</li></ul>
      </article>
    </main>
  </div>
</template>

<style scoped>
.receipt-page { max-width: 800px; margin: 24px auto; padding: 20px; }
.receipt { border: 1px solid #d5d8dc; padding: 32px; background: #fff; color: #111; }
header { text-align: center; }
table { width: 100%; border-collapse: collapse; margin: 24px 0; }
th, td { border-bottom: 1px solid #ddd; padding: 8px; text-align: left; }
dl { display: grid; grid-template-columns: 1fr auto; max-width: 400px; margin-left: auto; }
@media print { .screen-only { display: none; } .receipt-page { margin: 0; max-width: none; padding: 0; } .receipt { border: 0; } }
</style>
