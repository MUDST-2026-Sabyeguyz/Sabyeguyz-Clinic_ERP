<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
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

defineProps<{ embedded?: boolean }>()
const route = useRoute()
const activeTab = ref<'payments' | 'courses'>(
  route.query.tab === 'payments' ? 'payments' : 'courses',
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
const loading = ref(true)
const errorMessage = ref('')
const detailsIncomplete = ref(false)
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
            ? isSingleVisit(entry.type)
            : ['COURSE', 'COURSEPURCHASE'].includes(entry.type.replace(/_/g, '').toUpperCase()),
        ))
    )
  })
})
const filteredCourses = computed(() => {
  if (typeFilter.value === 'SINGLE') return []
  const keyword = query.value.trim().toLowerCase()
  return courses.value.filter(
    (item) =>
      (!keyword ||
        `${item.course.code} ${item.course.name} ${item.status}`.toLowerCase().includes(keyword)) &&
      (!dateFilter.value || item.purchasedAt.slice(0, 10) === dateFilter.value),
  )
})
function isSingleVisit(type: string) {
  return ['SINGLEVISIT', 'ASSESSMENT'].includes(type.replace(/_/g, '').toUpperCase())
}
const filteredSingleVisits = computed(() => {
  if (typeFilter.value === 'COURSE') return []
  const keyword = query.value.trim().toLowerCase()
  const seen = new Set<number>()
  return payments.value.flatMap((payment) => {
    const detail = paymentDetails.value[payment.id]
    if (!detail) return []
    return detail.items.filter((item) => {
      if (!isSingleVisit(item.type) || seen.has(item.id)) return false
      seen.add(item.id)
      return (!keyword || `${item.description} ${payment.receiptNo}`.toLowerCase().includes(keyword))
        && (!dateFilter.value || payment.soldAt.slice(0, 10) === dateFilter.value)
    }).map((item) => ({ item, payment }))
  })
})
const courseOverview = computed(() => ({
  total: courses.value.length,
  active: courses.value.filter((item) => item.usable).length,
  remaining: courses.value
    .filter((item) => item.usable)
    .reduce((sum, item) => sum + item.remaining, 0),
  expired: courses.value.filter((item) => item.expired && item.status !== 'CANCELLED').length,
}))
function clearHistoryFilters() {
  query.value = ''
  dateFilter.value = ''
  typeFilter.value = ''
}
function courseActionLink(
  item: CoursePurchase,
  action: 'manage' | 'use' | 'history' | 'transfer',
  print = false,
) {
  // ส่งผู้ป่วยและคอร์สที่เลือกไปยังหน้าจัดการสิทธิ์เดิม
  return {
    path: '/staff/patients/' + patient.value?.id + '/courses/' + item.id,
    query: {
      patientId: String(patient.value?.id),
      courseId: String(item.id),
      action,
      ...(print ? { print: '1' } : {}),
    },
  }
}
const paymentDescription = (item: SaleSummary) =>
  item.itemDescription ||
  paymentDetails.value[item.id]?.items.map((entry) => entry.description).join(', ') ||
  '-'
const paymentMethod = (item: SaleSummary) =>
  paymentDetails.value[item.id]?.payments.map((entry) => entry.paymentMethodCode).join(', ') || '-'
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
const openPrint = (item: SaleSummary) => {
  selectedReceipt.value = paymentDetails.value[item.id] || null
  printConfirm.value = Boolean(selectedReceipt.value)
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
  query.value = ''
  dateFilter.value = ''
  typeFilter.value = ''
})

onMounted(async () => {
  const id = Number(route.params.id)
  if (!Number.isInteger(id)) {
    errorMessage.value = 'รหัสผู้ป่วยไม่ถูกต้อง'
    loading.value = false
    return
  }
  try {
    const [profile, paymentPage, courseList] = await Promise.all([
      patientApi.get(id),
      salesApi.list({ patientId: id, size: 100 }),
      courseApi.patientCourses(id),
    ])
    patient.value = profile
    try {
      receiptBranch.value = await branchApi.get(profile.branch.id)
    } catch {
      receiptBranch.value = null
    }
    const remainingPages = await Promise.all(
      Array.from({ length: Math.max(0, paymentPage.totalPages - 1) }, (_, index) =>
        salesApi.list({ patientId: id, size: 100, page: index + 1 }),
      ),
    )
    payments.value = [paymentPage, ...remainingPages].flatMap((page) => page.items)
    courses.value = courseList
    const details = await Promise.allSettled(payments.value.map((item) => salesApi.get(item.id)))
    detailsIncomplete.value = details.some((result) => result.status === 'rejected')
    paymentDetails.value = Object.fromEntries(
      details.flatMap((result, index) =>
        result.status === 'fulfilled' ? [[payments.value[index]!.id, result.value]] : [],
      ),
    )
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดประวัติการรักษาไม่สำเร็จ'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div
    class="patient-workspace patient-history-page"
    :class="{ 'patient-history-embedded': embedded }"
  >
    <StaffHeader v-if="!embedded" />
    <component
      :is="embedded ? 'section' : 'main'"
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
              <p>
                เลขที่ใบอนุญาต:
                {{ receiptBranch?.licenseNumber?.trim() || '—' }}　 อีเมล:
                {{ receiptBranch?.email?.trim() || '—' }}
              </p>
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
            <p>
              <b>ผู้รับบริการ/ผู้บริโภค:</b>
              {{ selectedReceipt.patient.fullName }}
            </p>
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
              ><b
                >฿{{
                  receiptGross.toLocaleString(undefined, {
                    minimumFractionDigits: 2,
                  })
                }}</b
              >
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
                  selectedReceipt.paid.toLocaleString(undefined, {
                    minimumFractionDigits: 2,
                  })
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
                  selectedReceipt.paid.toLocaleString(undefined, {
                    minimumFractionDigits: 2,
                  })
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
    </component>
    <component
      :is="embedded ? 'section' : 'main'"
      v-show="!receiptView"
      class="history-page-content"
    >
      <RouterLink v-if="!embedded" class="back-link" :to="`/staff/patients/${route.params.id}`"
        >← กลับข้อมูลผู้ป่วย</RouterLink
      >
      <h1 v-if="!embedded">บริการและคอร์ส</h1>
      <p v-if="!embedded" class="history-subtitle">
        ตรวจสอบใบเสร็จ การชำระเงิน และสิทธิ์คอร์สรักษาของผู้ป่วย
      </p>
      <section
        v-if="!loading && !errorMessage"
        class="course-overview"
        aria-label="ภาพรวมคอร์สทั้งหมด"
      >
        <div>
          <span>คอร์สทั้งหมด</span><strong>{{ courseOverview.total }} คอร์ส</strong>
        </div>
        <div>
          <span>คอร์สที่ใช้ได้</span><strong>{{ courseOverview.active }} คอร์ส</strong>
        </div>
        <div>
          <span>สิทธิ์ที่ใช้ได้คงเหลือ</span><strong>{{ courseOverview.remaining }} ครั้ง</strong>
        </div>
        <div>
          <span>คอร์สหมดอายุ</span><strong>{{ courseOverview.expired }} คอร์ส</strong>
        </div>
      </section>
      <nav class="history-tabs" aria-label="เลือกรายการบริการและคอร์ส">
        <button
          type="button"
          :aria-pressed="activeTab === 'payments'"
          :class="{ active: activeTab === 'payments' }"
          @click="activeTab = 'payments'"
        >
          รายการชำระเงิน</button
        ><button
          type="button"
          :aria-pressed="activeTab === 'courses'"
          :class="{ active: activeTab === 'courses' }"
          @click="activeTab = 'courses'"
        >
          คอร์สของผู้ป่วย
        </button>
      </nav>
      <section class="history-card">
        <div class="history-filters">
          <label class="history-search-field"
            ><span>ค้นหา</span
            ><input
              v-model="query"
              type="search"
              :placeholder="
                activeTab === 'payments' ? 'เลขที่ใบเสร็จหรือชื่อบริการ' : 'ชื่อบริการ ชื่อคอร์ส หรือเลขใบเสร็จ'
              "
          /></label>
          <label class="history-date-field"
            ><span>{{ activeTab === 'payments' ? 'วันที่ชำระเงิน' : 'วันที่ซื้อ' }}</span
            ><ThaiDatePicker
              v-model="dateFilter"
              clearable
              show-calendar-icon
              numeric-month
              hide-caret
          /></label>
          <FilterDropdown
            v-model="typeFilter"
            label="กรองตามประเภทรายการ"
            default-label="รายครั้งและรายคอร์ส"
            :options="[
              { value: 'SINGLE', label: 'รายครั้ง' },
              { value: 'COURSE', label: 'รายคอร์ส' },
            ]"
          />
          <button
            v-if="query || dateFilter || typeFilter"
            type="button"
            class="history-clear-filters"
            @click="clearHistoryFilters"
          >
            ล้างตัวกรอง
          </button>
        </div>
        <p v-if="loading" role="status">กำลังโหลดข้อมูล...</p>
        <p v-else-if="errorMessage" class="error">{{ errorMessage }}</p>
        <div v-else class="history-table-wrap">
          <p v-if="detailsIncomplete" role="alert">โหลดรายละเอียดบางรายการไม่สำเร็จ รายการรายครั้งอาจแสดงไม่ครบ กรุณาโหลดหน้าใหม่</p>
          <table v-if="activeTab === 'payments'">
            <thead>
              <tr>
                <th>วันที่</th>
                <th>เลขที่ใบเสร็จ</th>
                <th>บริการ / คอร์ส</th>
                <th>สาขา</th>
                <th>ยอดชำระ</th>
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
                <td>
                  ฿{{
                    item.paid.toLocaleString(undefined, {
                      minimumFractionDigits: 2,
                    })
                  }}
                </td>
                <td>{{ paymentMethod(item) }}</td>
                <td class="history-actions">
                  <button type="button" @click="openReceipt(item)">ดูใบเสร็จ</button>
                  <button type="button" class="outline" @click="openPrint(item)">พิมพ์</button>
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
                <th>บริการ / คอร์ส และวันที่ซื้อ</th>
                <th>สิทธิ์คอร์ส</th>
                <th>วันหมดอายุ</th>
                <th>สถานะ</th>
                <th>ดำเนินการ</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in filteredCourses" :key="item.id">
                <td>
                  <b>{{ item.course.name }}</b
                  ><small class="course-purchase-date">ซื้อ {{ thaiDate(item.purchasedAt) }}</small>
                </td>
                <td>
                  <strong>เหลือ {{ item.remaining }} ครั้ง</strong
                  ><small class="course-purchase-date"
                    >ใช้แล้ว {{ item.used }} / ทั้งหมด {{ item.purchased }} ครั้ง</small
                  >
                </td>
                <td>{{ thaiDate(item.expiresAt) }}</td>
                <td>
                  <span class="history-status course-status">{{
                    courseStatus(courseDisplayStatus(item))
                  }}</span>
                </td>
                <td class="history-actions course-actions">
                  <RouterLink
                    class="history-row-action outline"
                    :to="courseActionLink(item, 'manage')"
                    >รายละเอียดคอร์ส</RouterLink
                  >
                </td>
              </tr>
              <tr v-for="{ item, payment } in filteredSingleVisits" :key="`single-${item.id}`">
                <td><b>{{ item.description }}</b><small class="course-purchase-date">รายครั้ง · ซื้อ {{ thaiDate(payment.soldAt) }}</small></td>
                <td>{{ item.quantity }} ครั้ง</td>
                <td>—</td>
                <td>{{ paymentStatus(payment.status) }}</td>
                <td class="history-actions course-actions"><button type="button" class="outline" @click="openReceipt(payment)">ดูใบเสร็จ</button></td>
              </tr>
              <tr v-if="!filteredCourses.length && !filteredSingleVisits.length">
                <td colspan="5">
                  {{
                    query || dateFilter || typeFilter ? 'ไม่พบรายการตามตัวกรองที่เลือก' : 'ผู้ป่วยยังไม่มีรายการบริการหรือคอร์ส'
                  }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="!loading && !errorMessage" class="history-result-count">
          {{
            activeTab === 'payments'
              ? `แสดง ${filteredPayments.length} รายการล่าสุด`
              : 'สิทธิ์คงเหลือล่าสุดหลังการใช้บริการและการแชร์คอร์ส'
          }}
        </p>
      </section>
    </component>
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
      <div v-if="printResult" class="receipt-print-modal-backdrop" @click.self="closePrintResult">
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

<style scoped src="../styles/manager-patient-history.css"></style>
<style src="../../services/styles/services.css"></style>
<style>
@media print {
  @page {
    size: A4 portrait;
    margin: 0;
  }

  body.therapist-ui .patient-workspace::before {
    display: none !important;
    content: none !important;
  }

  .patient-history-page .history-page-content {
    display: none !important;
  }

  .patient-history-page .printable-receipt-screen {
    display: block !important;
  }

  .patient-history-page .staff-header,
  .patient-history-page .staff-header *,
  .patient-history-page .profile-menu,
  .patient-history-page .profile-dropdown {
    display: none !important;
    box-shadow: none !important;
  }

  body.therapist-ui .patient-history-page > .receipt-page-main {
    width: 100% !important;
    margin: 0 !important;
    padding: 0 !important;
  }

  body.therapist-ui .patient-history-page .receipt-page-main .printable-receipt {
    height: 265mm;
    max-height: 265mm;
    padding: 8mm 12mm 7mm;
  }
}
</style>
