<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { formatPaymentMethod } from '@/features/payments/format'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import {
  branchApi,
  courseApi,
  patientApi,
  salesApi,
  type CoursePurchase,
  type Branch,
  type PatientProfile,
  type SaleDetail,
  type SaleSummary,
} from '@/api/client'

const route = useRoute()
const activeTab = ref<'payments' | 'courses'>(
  route.query.tab === 'courses' ? 'courses' : 'payments',
)
const patient = ref<PatientProfile | null>(null)
const receiptBranch = ref<Branch | null>(null)
const payments = ref<SaleSummary[]>([])
const paymentDetails = ref<Record<number, SaleDetail>>({})
const courses = ref<CoursePurchase[]>([])
const query = ref('')
const dateFilter = ref('')
const typeFilter = ref('')
const selectedReceipt = ref<SaleDetail | null>(null)
const receiptView = ref(false)
const printConfirm = ref(false)
const printResult = ref<'success' | 'error' | null>(null)
const receiptGross = computed(
  () => selectedReceipt.value?.items.reduce((sum, item) => sum + item.lineTotal, 0) ?? 0,
)
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const loading = ref(true)
const errorMessage = ref('')
const filteredPayments = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  return payments.value.filter((item) => {
    const detail = paymentDetails.value[item.id]
    const description =
      item.itemDescription || detail?.items.map((entry) => entry.description).join(' ') || ''
    return (
      (!keyword ||
        `${item.receiptNo} ${item.status} ${description}`.toLowerCase().includes(keyword)) &&
      (!dateFilter.value || item.soldAt.slice(0, 10) === dateFilter.value) &&
      (!typeFilter.value ||
        detail?.items.some((entry) =>
          typeFilter.value === 'SINGLE'
            ? entry.type === 'ASSESSMENT' || entry.type === 'SINGLE_VISIT'
            : entry.type === 'COURSE',
        ))
    )
  })
})
const filteredCourses = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  return courses.value.filter(
    (item) =>
      (!keyword ||
        `${item.course.code} ${item.course.name} ${item.status}`.toLowerCase().includes(keyword)) &&
      (!dateFilter.value || item.purchasedAt.slice(0, 10) === dateFilter.value),
  )
})
const paymentDescription = (item: SaleSummary) =>
  item.itemDescription ||
  paymentDetails.value[item.id]?.items.map((entry) => entry.description).join(', ') ||
  '-'
const paymentMethod = (item: SaleSummary) =>
  paymentDetails.value[item.id]?.payments
    .map((entry) => formatPaymentMethod(entry.paymentMethodCode))
    .join(', ') || '-'
const paymentMethodLabel = (code: string) => {
  const labels: Record<string, string> = {
    CASH: 'เงินสด',
    BANK_TRANSFER: 'โอนเงินผ่านธนาคาร',
    PROMPTPAY: 'พร้อมเพย์',
    QR_CODE: 'พร้อมเพย์',
    CARD: 'บัตรเครดิต/เดบิต',
    CREDIT_CARD: 'บัตรเครดิต/เดบิต',
  }
  return labels[code] || code
}

const paymentStatus = (status: string) => {
  const labels: Record<string, string> = {
    PAID: 'ชำระแล้ว',
    COMPLETED: 'ชำระแล้ว',
    PENDING: 'รอชำระ',
    VOIDED: 'ยกเลิกแล้ว',
    CANCELLED: 'ยกเลิกแล้ว',
  }
  return labels[status] || status
}
const courseStatus = (status: string) => {
  const labels: Record<string, string> = {
    ACTIVE: 'ใช้งานอยู่',
    COMPLETED: 'ใช้ครบแล้ว',
    EXHAUSTED: 'ใช้ครบแล้ว',
    EXPIRED: 'หมดอายุ',
    CANCELLED: 'ยกเลิกแล้ว',
  }
  return labels[status] || status
}
const courseDisplayStatus = (item: CoursePurchase) => {
  if (item.status === 'CANCELLED') return 'CANCELLED'
  if (item.expired) return 'EXPIRED'
  if (item.remaining <= 0) return 'EXHAUSTED'
  return 'ACTIVE'
}
const openReceipt = (item: SaleSummary) => {
  selectedReceipt.value = paymentDetails.value[item.id] || null
  receiptView.value = Boolean(selectedReceipt.value)
}
const openReceiptPrint = () => {
  printConfirm.value = true
}
const printPage = async () => {
  printConfirm.value = false
  await nextTick()
  try {
    if (typeof window.print !== 'function') throw new Error('Print is unavailable')
    window.print()
  } catch {
    printResult.value = 'error'
  }
}
const closePrintResult = () => {
  printResult.value = null
}
const returnToHistory = () => {
  printResult.value = null
  receiptView.value = false
}
const retryPrint = () => {
  printResult.value = null
  void printPage()
}
const thaiDate = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { dateStyle: 'medium' }).format(new Date(value))
const receiptDate = (value: string) =>
  new Intl.DateTimeFormat('th-TH', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(value))

watch(activeTab, () => {
  typeFilter.value = ''
})

async function loadPayments(targetPage = 0) {
  loading.value = true
  errorMessage.value = ''
  payments.value = []
  paymentDetails.value = {}
  try {
    const paymentPage = await salesApi.list({
      patientId: Number(route.params.id),
      receiptsOnly: true,
      page: targetPage,
      size: 20,
    })
    page.value = targetPage
    totalPages.value = paymentPage.totalPages
    totalElements.value = paymentPage.totalItems
    payments.value = paymentPage.items
    const details = await Promise.allSettled(paymentPage.items.map((item) => salesApi.get(item.id)))
    paymentDetails.value = Object.fromEntries(
      details.flatMap((result, index) =>
        result.status === 'fulfilled' ? [[paymentPage.items[index]!.id, result.value]] : [],
      ),
    )
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดประวัติใบเสร็จไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  const id = Number(route.params.id)
  if (!Number.isInteger(id) || id <= 0) {
    errorMessage.value = 'รหัสผู้ป่วยไม่ถูกต้อง'
    loading.value = false
    return
  }
  await Promise.allSettled([
    patientApi.get(id).then(async (profile) => {
      patient.value = profile
      try {
        receiptBranch.value = await branchApi.get(profile.branch.id)
      } catch {
        receiptBranch.value = null
      }
    }),
    courseApi.patientCourses(id).then((items) => {
      courses.value = items
    }),
    loadPayments(),
  ])
})
</script>

<template>
  <div class="patient-workspace patient-history-page">
    <StaffHeader />
    <main
      v-if="selectedReceipt"
      v-show="receiptView"
      class="receipt-page-main printable-receipt-screen"
    >
      <section class="service-receipt-page">
        <article class="printable-receipt">
        <header>
          <div class="receipt-clinic">
            <h1>
              สบายกาย คลินิกกายภาพบำบัด
              {{ patient?.branch.name || `สาขา ${selectedReceipt.branchCode}` }}
            </h1>
            <p>ที่อยู่: {{ receiptBranch?.address?.trim() || '—' }}</p>
            <p>โทร: {{ receiptBranch?.phone?.trim() || '—' }}</p>
          </div>
          <div class="receipt-document">
            <h2>ใบเสร็จรับเงิน</h2>
            <div>
              <p>เลขที่เอกสาร: {{ selectedReceipt.receiptNo }}</p>
              <p>วันที่ออกเอกสาร: {{ receiptDate(selectedReceipt.soldAt) }}</p>
            </div>
          </div>
        </header>
        <section class="receipt-customer">
          <p><b>ผู้รับบริการ/ผู้บริโภค:</b> {{ selectedReceipt.patient.fullName }}</p>
          <p><b>รหัสลูกค้า:</b> HN {{ selectedReceipt.patient.hn }}</p>
          <p><b>เลขบัตรประชาชน:</b> {{ patient?.identityNumber || '—' }}</p>
          <p><b>โทร:</b> {{ selectedReceipt.patient.phone || '—' }}</p>
          <p><b>ที่อยู่:</b> {{ patient?.address || '—' }}</p>
          <p><b>อีเมล:</b> {{ patient?.email || '—' }}</p>
        </section>
        <table>
          <thead>
            <tr>
              <th>รายการ</th>
              <th>จำนวน / หน่วย</th>
              <th>ราคา</th>
              <th>ยอดรวม</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(entry, index) in selectedReceipt.items" :key="entry.id">
              <td>{{ index + 1 }}. {{ entry.description }}</td>
              <td>{{ entry.quantity }} ครั้ง</td>
              <td>฿{{ (entry.lineTotal / entry.quantity).toLocaleString() }}</td>
              <td>฿{{ entry.lineTotal.toLocaleString() }}</td>
            </tr>
          </tbody>
        </table>
        <section class="receipt-totals">
          <div>
            <span>ยอดทั้งหมด</span
            ><b>฿{{ receiptGross.toLocaleString(undefined, { minimumFractionDigits: 2 }) }}</b>
            <span>ส่วนลดท้ายบิล</span
            ><b
              >฿{{
                (receiptGross - selectedReceipt.paid).toLocaleString(undefined, {
                  minimumFractionDigits: 2,
                })
              }}</b
            >
            <strong>ยอดสุทธิที่ชำระ</strong
            ><strong
              >฿{{
                selectedReceipt.paid.toLocaleString(undefined, { minimumFractionDigits: 2 })
              }}</strong
            >
          </div>
          <div>
            <span>สถานะการชำระเงิน</span><b>{{ paymentStatus(selectedReceipt.status) }}</b>
            <span>ช่องทางการชำระ</span
            ><b>{{
              selectedReceipt.payments
                .map((entry) => paymentMethodLabel(entry.paymentMethodCode))
                .join(', ')
            }}</b>
            <strong>ยอดสุทธิ</strong
            ><strong
              >฿{{
                selectedReceipt.paid.toLocaleString(undefined, { minimumFractionDigits: 2 })
              }}</strong
            >
          </div>
        </section>
        <section class="receipt-balance">
          <p>
            <b>หมายเหตุ: {{ selectedReceipt.note || '-' }}</b>
          </p>
        </section>
        <section class="receipt-signatures">
          <div>
            <span>........................................</span
            ><span>(........................................)</span><b>ผู้รับบริการ</b>
          </div>
          <div>
            <span>........................................</span
            ><span>(........................................)</span><b>ผู้ทำรายการ</b>
          </div>
          <div>
            <span>........................................</span
            ><span>(........................................)</span
            ><b>ผู้มีอำนาจออกหลักฐานการรับเงิน</b>
          </div>
        </section>
        <p class="receipt-printed-at">พิมพ์เมื่อ {{ new Date().toLocaleString('th-TH') }}</p>
        </article>
        <div class="receipt-print-actions">
          <button type="button" @click="receiptView = false">← กลับ</button
          ><button type="button" @click="openReceiptPrint">พิมพ์เอกสาร</button>
        </div>
      </section>
    </main>
    <main v-show="!receiptView" class="history-page-content">

      <RouterLink class="back-link" :to="`/staff/patients/${route.params.id}`"
        >← กลับข้อมูลผู้ป่วย</RouterLink
      >
      <h1>ประวัติการรักษา</h1>
      <p class="history-subtitle">ตรวจสอบใบเสร็จ การชำระเงิน และสิทธิ์คอร์สรักษาของผู้ป่วย</p>
      <nav class="history-tabs">
        <button :class="{ active: activeTab === 'payments' }" @click="activeTab = 'payments'">
          ใบเสร็จและการชำระเงิน</button
        ><button :class="{ active: activeTab === 'courses' }" @click="activeTab = 'courses'">
          คอร์สรักษา
        </button>
      </nav>
      <section class="history-card">
        <p v-if="activeTab === 'payments'">ค้นหาและตัวกรองด้านล่างใช้เฉพาะใบเสร็จในหน้านี้</p>
        <div class="history-filters">
          <input v-model="query" placeholder="ค้นหาเลขที่ใบเสร็จหรือชื่อคอร์ส" />
          <ThaiDatePicker v-model="dateFilter" show-calendar-icon numeric-month hide-caret />
          <FilterDropdown
            v-if="activeTab === 'payments'"
            v-model="typeFilter"
            label="กรองตามประเภทรายการ"
            default-label="ประเภทรายการ"
            :options="[
              { value: 'SINGLE', label: 'รายครั้ง' },
              { value: 'COURSE', label: 'รายคอร์ส' },
            ]"
          />
        </div>
        <p v-if="loading">กำลังโหลดข้อมูล...</p>
        <p v-else-if="errorMessage" class="error">{{ errorMessage }}</p>
        <div v-else class="history-table-wrap">
          <table v-if="activeTab === 'payments'">
            <thead>
              <tr>
                <th>วันที่</th>
                <th>เลขที่ใบเสร็จ</th>
                <th>บริการ / คอร์ส</th>
                <th>สาขา</th>
                <th>ยอดสุทธิ</th>
                <th>การชำระ</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in filteredPayments" :key="item.id">
                <td>{{ thaiDate(item.soldAt) }}</td>
                <td>{{ item.receiptNo }}</td>
                <td>{{ paymentDescription(item) }}</td>
                <td>{{ item.branchCode }}</td>
                <td>฿{{ item.total.toLocaleString(undefined, { minimumFractionDigits: 2 }) }}</td>
                <td><span class="history-status">{{ paymentStatus(item.status) }}</span> · {{ paymentMethod(item) }}</td>
                <td class="history-actions">
                  <button type="button" class="history-row-action" @click="openReceipt(item)">รายละเอียด</button>
                  <RouterLink class="history-row-action" :to="`/staff/receipts/${item.id}`"
                    >ดูใบเสร็จ</RouterLink
                  >
                  <RouterLink
                    class="history-row-action outline"
                    :to="{ path: `/staff/receipts/${item.id}`, query: { print: '1' } }"
                    >พิมพ์</RouterLink
                  >
                </td>
              </tr>
              <tr v-if="!filteredPayments.length">
                <td colspan="7">ไม่พบประวัติการชำระเงิน</td>
              </tr>
            </tbody>
          </table>
          <table v-else class="course-history-table">
            <thead>
              <tr>
                <th>ชื่อคอร์ส</th>
                <th>วันที่ซื้อ</th>
                <th>ผู้ขาย / นักกายภาพ</th>
                <th>สิทธิ์รวม</th>
                <th>ใช้แล้ว</th>
                <th>คงเหลือ</th>
                <th>หมดอายุ</th>
                <th>สถานะ</th>
                <th>จัดการ</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in filteredCourses" :key="item.id">
                <td>
                  <b>{{ item.course.name }}</b>
                </td>
                <td>{{ thaiDate(item.purchasedAt) }}</td>
                <td>-</td>
                <td>{{ item.purchased }} ครั้ง</td>
                <td>ใช้ {{ item.used }}</td>
                <td>
                  <b>เหลือ {{ item.remaining }}</b>
                </td>
                <td>{{ thaiDate(item.expiresAt) }}</td>
                <td>
                  <span class="history-status course-status">{{
                    courseStatus(courseDisplayStatus(item))
                  }}</span>
                </td>
                <td class="history-actions course-actions">
                  <RouterLink
                    class="history-row-action"
                    :to="{
                      path: `/staff/patients/${patient?.id}/courses/${item.id}`,
                      query: {
                        patientId: patient?.id,
                        courseId: item.id,
                        action: 'history',
                        print: '1',
                      },
                    }"
                    >รายละเอียด</RouterLink
                  >
                  <RouterLink
                    class="history-row-action outline"
                    :to="{
                      path: `/staff/patients/${patient?.id}/courses/${item.id}`,
                      query: { patientId: patient?.id, courseId: item.id, action: 'history' },
                    }"
                    >ประวัติใช้สิทธิ์</RouterLink
                  >
                </td>
              </tr>
              <tr v-if="!filteredCourses.length">
                <td colspan="9">ไม่พบประวัติคอร์ส</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="!loading && !errorMessage" class="history-result-count">
          {{
            activeTab === 'payments'
              ? `แสดง ${filteredPayments.length} จาก ${payments.length} ใบเสร็จในหน้านี้ · ทั้งหมด ${totalElements} ใบเสร็จ`
              : 'สิทธิ์คงเหลือเป็นข้อมูลล่าสุดหลังการใช้บริการและการแชร์คอร์ส'
          }}
        </p>
        <nav v-if="activeTab === 'payments'" aria-label="หน้าใบเสร็จ">
          <button type="button" :disabled="loading || page <= 0" @click="loadPayments(page - 1)">
            ก่อนหน้า
          </button>
          <span>หน้า {{ totalPages ? page + 1 : 0 }} / {{ totalPages }}</span>
          <button
            type="button"
            :disabled="loading || page + 1 >= totalPages"
            @click="loadPayments(page + 1)"
          >
            ถัดไป
          </button>
        </nav>
      </section>
    </main>
    <Teleport to="body">
      <div
        v-if="printConfirm"
        class="receipt-print-modal-backdrop"
        @click.self="printConfirm = false"
      >
        <section class="receipt-print-modal" role="dialog" aria-modal="true">
          <h2>ยืนยันการพิมพ์เอกสาร</h2>
          <p>ตรวจสอบชื่อผู้รับบริการ เลขที่เอกสาร รายการ และยอดสุทธิให้ครบถ้วนก่อนส่งพิมพ์</p>
          <footer>
            <button type="button" @click="printConfirm = false">ยกเลิก</button
            ><button type="button" @click="printPage">ยืนยันและพิมพ์</button>
          </footer>
        </section>
      </div>
    </Teleport>
    <Teleport to="body">
      <div
        v-if="printResult"
        class="receipt-print-modal-backdrop"
        @click.self="closePrintResult"
      >
        <section class="receipt-print-modal" role="dialog" aria-modal="true">
          <template v-if="printResult === 'success'">
            <h2>ส่งเอกสารไปยังเครื่องพิมพ์แล้ว</h2>
            <p>สถานะเอกสารถูกบันทึกเป็น “พิมพ์แล้ว” สามารถกลับไปดูรายการต้นทางได้</p>
            <footer>
              <button type="button" @click="closePrintResult">ยกเลิก</button>
              <button type="button" @click="returnToHistory">กลับหน้ารายการ</button>
            </footer>
          </template>
          <template v-else>
            <h2>ไม่สามารถพิมพ์เอกสารได้</h2>
            <p>กรุณาตรวจสอบการเชื่อมต่อเครื่องพิมพ์หรือข้อมูลที่จำเป็น แล้วลองอีกครั้ง</p>
            <footer>
              <button type="button" @click="closePrintResult">ยกเลิก</button>
              <button type="button" @click="retryPrint">ลองอีกครั้ง</button>
            </footer>
          </template>
        </section>
      </div>
    </Teleport>

  </div>
</template>

<style scoped src="../styles/patient-history.css"></style>
