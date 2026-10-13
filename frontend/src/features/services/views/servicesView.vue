<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import {
  branchApi,
  catalogApi,
  patientApi,
  paymentMethodApi,
  salesApi,
  staffApi,
  type Course,
  type Branch,
  type Patient,
  type PatientProfile,
  type PaymentMethod,
  type ServiceOption,
  type Staff,
} from '@/api/client'
type Mode = 'assessment' | 'single' | 'course'
const route = useRoute()
const router = useRouter()
const query = ref(''),
  patient = ref<Patient | null>(null),
  mode = ref<Mode>('assessment'),
  confirmed = ref(false),
  reviewing = ref(false),
  loading = ref(false),
  errorMessage = ref('')
const results = ref<Patient[]>([])
const patientProfile = ref<PatientProfile | null>(null)
const services = ref<ServiceOption[]>([])
const courses = ref<Course[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const staff = ref<Staff[]>([])
const branches = ref<Branch[]>([])
const selectedServiceId = ref(0),
  selectedCourseId = ref(0),
  selectedStaffId = ref<number | undefined>()
const paymentCode = ref(''),
  note = ref('')
const searched = ref(false)
const receiptNo = ref('')
const paymentDialogOpen = ref(false)
const receiptPrintModal = ref<'confirm' | 'success' | 'error' | null>(null)
const screen = ref<'search' | 'menu' | 'sale' | 'receipt'>(
  route.query.patientId ? 'menu' : 'search',
)
const assessmentService = computed(
  () =>
    services.value.find(
      (service) =>
        service.code.includes('ASSESS') || service.name.toLowerCase().includes('assessment'),
    ) ?? services.value[0],
)
const receiptBranch = computed(() => {
  const code = patient.value?.branchCode?.toLocaleLowerCase('th')
  const name = patient.value?.branchName?.replace(/^สาขา/, '').toLocaleLowerCase('th')
  return branches.value.find(
    (branch) =>
      (code && branch.branchCode.toLocaleLowerCase('th') === code) ||
      (name && branch.name.replace(/^สาขา/, '').toLocaleLowerCase('th') === name),
  )
})
const receiptClinicAddress = computed(() => receiptBranch.value?.address?.trim() || '—')
const receiptClinicPhone = computed(() => receiptBranch.value?.phone?.trim() || '—')
const receiptClinicEmail = computed(() => '—')
const receiptClinicLicense = computed(() => '—')
const receiptClinicTaxId = computed(() => '—')
const receiptBranchLabel = computed(() => {
  const name = receiptBranch.value?.name || patient.value?.branchName
  return name ? (name.startsWith('สาขา') ? name : `สาขา ${name}`) : 'สาขา แบริ่ง'
})
async function loadMaster() {
  const [s, c, p, methods, staffList] = await Promise.all([
    catalogApi.services(),
    catalogApi.courses(),
    patientApi.searchAll(),
    paymentMethodApi.list(),
    staffApi.list(),
  ])
  services.value = s
  courses.value = c
  results.value = p
  paymentMethods.value = methods
  staff.value = staffList
  selectedServiceId.value = s[0]?.id ?? 0
  selectedCourseId.value = c[0]?.id ?? 0
  paymentCode.value = methods[0]?.code ?? ''
  selectedStaffId.value = staffList[0]?.id
  try {
    branches.value = await branchApi.list()
  } catch {
    branches.value = []
  }
  const patientId = Number(route.query.patientId)
  const selectedPatient = p.find((item) => item.id === patientId)
  if (selectedPatient) await choose(selectedPatient)
}
onMounted(async () => {
  try {
    await loadMaster()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลบริการไม่สำเร็จ'
  }
})
let searchTimer: number | undefined
watch(query, () => {
  window.clearTimeout(searchTimer)
  const keyword = query.value.trim()
  if (!keyword) {
    searched.value = false
    return
  }
  searchTimer = window.setTimeout(async () => {
    try {
      results.value = (await patientApi.search(keyword, 0, 20)).items
      searched.value = true
    } catch (error) {
      results.value = []
      errorMessage.value = error instanceof Error ? error.message : 'ค้นหาผู้ป่วยไม่สำเร็จ'
      searched.value = true
    }
  }, 250)
})
const item = computed(() =>
  mode.value === 'assessment'
    ? (assessmentService.value?.name ?? 'Assessment')
    : mode.value === 'single'
      ? (services.value.find((s) => s.id === selectedServiceId.value)?.name ?? 'บริการรายครั้ง')
      : (courses.value.find((c) => c.id === selectedCourseId.value)?.name ?? 'คอร์ส'),
)
const rawPrice = computed(() =>
  mode.value === 'course'
    ? (courses.value.find((course) => course.id === selectedCourseId.value)?.price ?? 0)
    : mode.value === 'assessment'
      ? (assessmentService.value?.price ?? 0)
      : (services.value.find((service) => service.id === selectedServiceId.value)?.price ?? 0),
)
const itemDiscount = computed(() => 0)
const afterItemDiscount = computed(() => Math.max(rawPrice.value - itemDiscount.value, 0))
const billDiscount = computed(() => 0)
const netAmount = computed(() => Math.max(afterItemDiscount.value - billDiscount.value, 0))
const commissionAmount = computed(() => afterItemDiscount.value * 0.1)
const receiptPayable = computed(() => Math.max(rawPrice.value - billDiscount.value, 0))
const money = (value: number) =>
  new Intl.NumberFormat('th-TH', { style: 'currency', currency: 'THB' }).format(value)
const printReceipt = () => {
  receiptPrintModal.value = 'confirm'
}
function confirmReceiptPrint() {
  receiptPrintModal.value = null
  const originalTitle = document.title
  const restoreTitle = () => {
    document.title = originalTitle
  }
  document.title = ''
  try {
    window.addEventListener('afterprint', restoreTitle, { once: true })
    window.setTimeout(() => {
      try {
        window.print()
        restoreTitle()
      } catch {
        restoreTitle()
        receiptPrintModal.value = 'error'
      }
    }, 0)
  } catch {
    restoreTitle()
    receiptPrintModal.value = 'error'
  }
}
function backToSaleSuccess() {
  receiptPrintModal.value = null
  screen.value = 'sale'
  confirmed.value = true
}
const printedAt = computed(() =>
  new Intl.DateTimeFormat('th-TH', {
    day: '2-digit',
    month: '2-digit',
    year: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date()),
)
const selectedStaffName = computed(
  () => staff.value.find((person) => person.id === selectedStaffId.value)?.fullName || '-',
)
const staffFilterOptions = computed(() =>
  staff.value.map((person) => ({ value: String(person.id), label: `กภ. ${person.fullName}` })),
)
const initials = computed(
  () =>
    patient.value?.fullName
      .replace(/^นาย|^นางสาว|^นาง/, '')
      .trim()
      .slice(0, 2) ?? '',
)
async function choose(p: Patient) {
  patient.value = p
  patientProfile.value = null
  query.value = `${p.fullName} ${p.hn}`
  screen.value = 'menu'
  try {
    patientProfile.value = await patientApi.get(p.id)
  } catch {
    patientProfile.value = null
  }
}
function searchPatients() {
  if (query.value.trim()) searched.value = true
}
function openPatientCourses() {
  if (patient.value) router.push({ path: '/staff/courses', query: { patientId: patient.value.id } })
}
function clearPatient() {
  patient.value = null
  patientProfile.value = null
  query.value = ''
  searched.value = false
  reviewing.value = false
  confirmed.value = false
  screen.value = 'search'
}
function backToServiceMenu() {
  reviewing.value = false
  confirmed.value = false
  receiptPrintModal.value = null
  screen.value = 'menu'
}
function startSale() {
  screen.value = 'sale'
  mode.value = 'single'
  selectedServiceId.value =
    services.value.find((service) => service.code === 'MANUAL')?.id ?? services.value[0]?.id ?? 0
  reviewing.value = false
  confirmed.value = false
  paymentDialogOpen.value = false
}
async function submit() {
  if (!patient.value) return
  loading.value = true
  errorMessage.value = ''
  confirmed.value = false
  try {
    const type =
      mode.value === 'assessment'
        ? 'ASSESSMENT'
        : mode.value === 'single'
          ? 'SINGLE_VISIT'
          : 'COURSE'
    const referenceId =
      mode.value === 'course'
        ? selectedCourseId.value
        : mode.value === 'assessment'
          ? assessmentService.value?.id
          : selectedServiceId.value
    if (!referenceId) throw new Error('ไม่พบรายการบริการที่เลือก')
    const price =
      mode.value === 'course'
        ? (courses.value.find((c) => c.id === referenceId)?.price ?? 0)
        : (services.value.find((s) => s.id === referenceId)?.price ?? 0)
    const sale = await salesApi.create({
      patientId: patient.value.id,
      branchCode: patient.value.branchCode ?? (await branchApi.list())[0]?.branchCode ?? 'RP',
      salespersonStaffId: selectedStaffId.value,
      note: note.value,
      items: [{ type, referenceId, quantity: 1 }],
      payments: [
        { paymentMethodCode: paymentCode.value, amount: Math.min(price, netAmount.value) },
      ],
    })
    receiptNo.value = sale.receiptNo
    confirmed.value = true
    reviewing.value = false
    paymentDialogOpen.value = false
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'บันทึกรายการไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
</script>
<template>
  <div class="service-page">
    <div class="service-header-shell"><StaffHeader /></div>
    <main>
      <template v-if="screen === 'search'">
        <h1>บริการและคอร์ส</h1>
        <p class="service-page-intro">ค้นหาและเลือกผู้ป่วยเพื่อขาย ใช้สิทธิ์ และจัดการคอร์ส</p>
        <section class="service-search">
          <h2>ค้นหาผู้ป่วย</h2>
          <div class="search-wrap">
            <label class="search">
              <span class="search-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <circle cx="11" cy="11" r="6" />
                  <path d="m16 16 4 4" />
                </svg>
              </span>
              <input
                v-model="query"
                aria-label="ค้นหาผู้ป่วย"
                placeholder="ค้นหาชื่อ เบอร์โทรศัพท์ หรือ HN"
                @input="patient = null"
              />
            </label>
            <button class="service-search-button" type="button" @click="searchPatients">
              ค้นหา
            </button>
          </div>
          <section v-if="searched" class="service-results-card">
            <h2>ผลการค้นหา {{ results.length }} คน</h2>
            <p>ตรวจสอบชื่อ HN และเบอร์โทรศัพท์ก่อนเลือกผู้ป่วย</p>
            <button
              v-for="p in results"
              :key="p.hn"
              class="service-patient-result"
              type="button"
              @click="choose(p)"
            >
              <span
                ><b>{{ p.fullName }}</b
                ><small>HN {{ p.hn }} · โทร {{ p.phone || '-' }}</small></span
              >
              <strong>เลือกผู้ป่วย</strong>
            </button>
            <p v-if="!results.length" class="service-empty-result">ไม่พบผู้ป่วยที่ตรงกับคำค้น</p>
          </section>
          <section v-else class="service-search-hint">
            <p>
              ค้นหาผู้ป่วยจากช่องด้านบน แล้วเลือกคนที่ต้องการทำรายการ<br />ข้อมูลผู้ป่วยและสิทธิ์คอร์สจะแสดงหลังเลือกผู้ป่วย
            </p>
          </section>
        </section>
      </template>
      <section v-else-if="screen === 'menu' && patient" class="service-menu">
        <h1>บริการและคอร์ส</h1>
        <section class="patient-summary">
          <strong>{{ patient.fullName }}</strong>
          <span>HN {{ patient.hn }}</span>
          <button type="button" @click="clearPatient">เปลี่ยนผู้ป่วย</button>
        </section>
        <div class="service-menu-actions">
          <article>
            <h2>ขายบริการและคอร์ส</h2>
            <p>Assessment / Single Visit<br />หรือซื้อคอร์สใหม่</p>
            <button type="button" @click="startSale">เริ่มขายบริการ</button>
          </article>
          <article>
            <h2>ใช้สิทธิ์และจัดการคอร์ส</h2>
            <p>ดูสิทธิ์คงเหลือ ประวัติการใช้<br />และบันทึกการใช้สิทธิ์คอร์ส</p>
            <button type="button" @click="openPatientCourses">ดูคอร์สผู้ป่วย</button>
          </article>
        </div>
      </section>
      <section v-else-if="screen === 'receipt' && patient" class="service-receipt-page">
        <article class="printable-receipt">
          <header>
            <div class="receipt-clinic">
              <h1>สบายกาย คลินิกกายภาพบำบัด {{ receiptBranchLabel }}</h1>
              <p>ที่อยู่: {{ receiptClinicAddress }}</p>
              <p>โทร: {{ receiptClinicPhone }}</p>
              <p>เลขที่ใบอนุญาต: {{ receiptClinicLicense }}</p>
              <p>เลขประจำตัวผู้เสียภาษี: {{ receiptClinicTaxId }}</p>
              <p>อีเมล: {{ receiptClinicEmail }}</p>
            </div>
            <div class="receipt-document">
              <h2>ใบเสร็จรับเงิน</h2>
              <p>เลขที่เอกสาร: {{ receiptNo }}</p>
              <p>เอกสารอ้างอิง: BL260910424</p>
              <p>วันที่ออกเอกสาร: 22/09/2569</p>
            </div>
          </header>
          <section class="receipt-customer">
            <p><b>ผู้รับบริการ/ผู้บริโภค:</b> {{ patient.fullName }}</p>
            <p><b>รหัสลูกค้า:</b> HN {{ patient.hn }}</p>
            <p><b>เลขบัตรประชาชน:</b> {{ patientProfile?.identityNumber || '—' }}</p>
            <p><b>โทร:</b> {{ patient.phone }}</p>
            <p><b>ที่อยู่:</b> {{ patientProfile?.address || '—' }}</p>
            <p><b>อีเมล:</b> {{ patientProfile?.email || '—' }}</p>
          </section>
          <table>
            <thead>
              <tr>
                <th>รายการ</th>
                <th>จำนวน</th>
                <th>ราคา</th>
                <th>ยอดรวม</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>1. {{ item }}</td>
                <td>1</td>
                <td>{{ money(rawPrice) }}</td>
                <td>{{ money(rawPrice) }}</td>
              </tr>
            </tbody>
          </table>
          <section class="receipt-totals">
            <div>
              <span>ยอดทั้งหมด</span><b>{{ money(rawPrice) }}</b
              ><span>ส่วนลดท้ายบิล</span><b>{{ money(billDiscount) }}</b
              ><span>มูลค่าก่อนภาษี</span><b>{{ money(receiptPayable) }}</b
              ><span>ภาษีมูลค่าเพิ่ม (ไม่มีภาษี)</span><b>{{ money(0) }}</b
              ><strong>ยอดชำระ</strong><strong>{{ money(receiptPayable) }}</strong>
            </div>
            <div>
              <span>สถานะการชำระเงิน</span><b>ชำระเต็มจำนวน</b><span>ช่องทางการชำระ</span
              ><b>{{ paymentMethods.find((method) => method.code === paymentCode)?.name || '-' }}</b
              ><strong>ยอดสุทธิ</strong><strong>{{ money(receiptPayable) }}</strong>
            </div>
          </section>
          <section class="receipt-balance">
            <p><b>แต้มคงเหลือ: 0</b><b>วงเงินคงเหลือ: ฿0.00</b></p>
            <p>
              <b>หมายเหตุ: {{ note || '-' }}</b>
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
          <p class="receipt-printed-at">พิมพ์เมื่อ {{ printedAt }}</p>
        </article>
        <div class="receipt-print-actions">
          <button type="button" @click="screen = 'sale'">← กลับ</button
          ><button type="button" @click="printReceipt">พิมพ์เอกสาร</button>
        </div>
        <div
          v-if="receiptPrintModal"
          class="receipt-print-modal-backdrop"
          @click.self="receiptPrintModal = null"
        >
          <section
            class="receipt-print-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="receipt-print-title"
          >
            <template v-if="receiptPrintModal === 'confirm'">
              <h2 id="receipt-print-title">ยืนยันการพิมพ์เอกสาร</h2>
              <p>ตรวจสอบชื่อผู้รับบริการ เลขที่เอกสาร รายการ และยอดสุทธิให้ครบถ้วนก่อนส่งพิมพ์</p>
              <footer>
                <button type="button" @click="receiptPrintModal = null">ยกเลิก</button>
                <button type="button" @click="confirmReceiptPrint">ยืนยันและพิมพ์</button>
              </footer>
            </template>
            <template v-else-if="receiptPrintModal === 'success'">
              <h2 id="receipt-print-title">ส่งเอกสารไปยังเครื่องพิมพ์แล้ว</h2>
              <p>สถานะเอกสารถูกบันทึกเป็น “พิมพ์แล้ว” สามารถกลับไปดูรายการต้นทางได้</p>
              <footer>
                <button type="button" @click="receiptPrintModal = null">ยกเลิก</button>
                <button type="button" @click="backToSaleSuccess">กลับหน้ารายการ</button>
              </footer>
            </template>
            <template v-else>
              <h2 id="receipt-print-title">ไม่สามารถพิมพ์เอกสารได้</h2>
              <p>กรุณาตรวจสอบการเชื่อมต่อเครื่องพิมพ์หรือข้อมูลที่จำเป็น แล้วลองอีกครั้ง</p>
              <footer>
                <button type="button" @click="receiptPrintModal = null">ยกเลิก</button>
                <button type="button" @click="receiptPrintModal = 'confirm'">ลองอีกครั้ง</button>
              </footer>
            </template>
          </section>
        </div>
      </section>
      <section v-else-if="patient" class="sale-page">
        <button type="button" class="sale-back-button" @click="backToServiceMenu">
          <span aria-hidden="true">←</span> กลับไปหน้าบริการ
        </button>
        <ol class="sales-progress" aria-label="ขั้นตอนการขาย">
          <li :class="{ active: !reviewing && !confirmed, done: reviewing || confirmed }">
            <i>1</i><span>สร้างรายการ</span>
          </li>
          <li :class="{ active: reviewing && !confirmed, done: confirmed }">
            <i>2</i><span>ตรวจสอบและชำระ</span>
          </li>
          <li :class="{ active: confirmed }"><i>3</i><span>สำเร็จ</span></li>
        </ol>

        <template v-if="!confirmed">
          <h1>{{ reviewing ? 'ตรวจสอบและชำระเงิน' : 'ขายบริการและคอร์ส' }}</h1>
          <p class="sale-intro">
            {{
              reviewing
                ? 'ตรวจสอบรายการ ส่วนลด และฐานค่าคอมมิชชันก่อนยืนยัน'
                : 'ระบุรายการ ส่วนลด และช่องทางชำระเงิน'
            }}
          </p>
          <section class="sale-patient-summary">
            <i>{{ initials }}</i>
            <div>
              <strong>{{ patient.fullName }}</strong
              ><span>HN {{ patient.hn }}　☎ {{ patient.phone }}</span>
            </div>
            <b>{{ patient.branchName ? `สาขา${patient.branchName}` : 'สาขาแบริ่ง' }}</b>
          </section>
          <div class="sales-workspace" :class="{ review: reviewing }">
            <section class="sale-line-card">
              <div class="sale-tabs" v-if="!reviewing">
                <button type="button" @click="mode = 'assessment'">Assessment</button>
                <button type="button" @click="mode = 'single'">Single Visit</button>
                <button type="button" @click="mode = 'course'">Course</button>
              </div>
              <label v-if="mode === 'course' && !reviewing"
                >รายการคอร์ส
                <select v-model="selectedCourseId" aria-label="รายการคอร์ส">
                  <option v-for="course in courses" :key="course.id" :value="course.id">
                    {{ course.name }}
                  </option>
                </select>
              </label>
              <label v-if="mode === 'single' && !reviewing"
                >รายการบริการ
                <select v-model="selectedServiceId" aria-label="รายการบริการ">
                  <option v-for="service in services" :key="service.id" :value="service.id">
                    {{ service.name }}
                  </option>
                </select>
              </label>
              <header>
                <h2>รายละเอียดรายการ</h2>
                <small>1 รายการ</small>
              </header>
              <div class="sale-line-head">
                <span>บริการ</span><span>ราคา/หน่วย</span><span>จำนวน</span><span>ส่วนลดรายการ</span
                ><span>ยอดหลังลด</span>
              </div>
              <div class="sale-line-values">
                <div>
                  <div class="sale-service-readonly" aria-label="บริการที่เลือก">{{ item }}</div>
                  <small>{{ mode === 'course' ? 'คอร์ส' : 'บริการรายครั้ง' }}</small>
                </div>
                <span>{{ money(rawPrice) }}</span
                ><span>1</span><span>− {{ money(itemDiscount) }}</span
                ><b>{{ money(afterItemDiscount) }}</b>
              </div>
              <div class="sale-people">
                <label
                  >นักกายภาพผู้ให้บริการ<FilterDropdown
                    :model-value="String(selectedStaffId)"
                    :options="staffFilterOptions"
                    default-label="เลือกนักกายภาพ"
                    label="นักกายภาพผู้ให้บริการ"
                    :clear-on-reselect="false"
                    @update:model-value="selectedStaffId = Number($event)" /></label
                ><label
                  >ผู้ขาย/เจ้าของยอด<FilterDropdown
                    :model-value="String(selectedStaffId)"
                    :options="staffFilterOptions"
                    default-label="เลือกผู้ขาย/เจ้าของยอด"
                    label="ผู้ขายหรือเจ้าของยอด"
                    :clear-on-reselect="false"
                    @update:model-value="selectedStaffId = Number($event)"
                /></label>
              </div>
              <div class="payment-group">
                <span>ช่องทางชำระเงิน</span>
                <div class="payment-options">
                  <button
                    v-for="method in paymentMethods"
                    :key="method.code"
                    type="button"
                    :class="{ active: paymentCode === method.code }"
                    @click="paymentCode = method.code"
                  >
                    {{ method.name }}
                  </button>
                </div>
              </div>
              <label class="sale-note"
                >หมายเหตุ (ไม่บังคับ)<textarea
                  v-model.trim="note"
                  placeholder="เพิ่มรายละเอียดเกี่ยวกับรายการขาย"
                />
              </label>
              <button v-if="reviewing" type="button" class="sale-edit" @click="reviewing = false">
                แก้ไข
              </button>
            </section>
            <aside class="sale-summary-card">
              <h2>สรุปรายการ</h2>
              <p>
                <span>ยอดก่อนส่วนลด</span><b>{{ money(rawPrice) }}</b>
              </p>
              <p>
                <span>ส่วนลดรายการรวม</span><b>− {{ money(itemDiscount) }}</b>
              </p>
              <p>
                <strong>ยอดหลังส่วนลดรายการ</strong><strong>{{ money(afterItemDiscount) }}</strong>
              </p>
              <p class="bill-discount">
                <span>ส่วนลดท้ายบิล</span><b>− {{ money(billDiscount) }}</b>
              </p>
              <p class="net-total">
                <strong>ยอดสุทธิที่ลูกค้าชำระ</strong><strong>{{ money(netAmount) }}</strong>
              </p>
              <div class="commission">
                <p>
                  <span>ฐานค่าคอมมิชชัน</span><b>{{ money(afterItemDiscount) }}</b>
                </p>
                <p>
                  <span>ค่าคอมมิชชัน 10%</span><b>{{ money(commissionAmount) }}</b>
                </p>
              </div>
              <button
                type="button"
                :disabled="loading"
                @click="reviewing ? (paymentDialogOpen = true) : (reviewing = true)"
              >
                {{ reviewing ? 'ยืนยันรับชำระเงิน' : 'ตรวจสอบและชำระเงิน' }}
              </button>
            </aside>
          </div>
        </template>

        <section v-else class="sale-success-card">
          <i>✓</i>
          <h1>บันทึกการขายสำเร็จ</h1>
          <p>เลขรายการ {{ receiptNo }}</p>
          <div>
            <section>
              <b>{{ patient.fullName }}　HN {{ patient.hn }}</b
              ><span
                >{{ item }} ·
                {{ patient.branchName ? `สาขา${patient.branchName}` : 'สาขาแบริ่ง' }}</span
              ><small>ฐานค่าคอมมิชชัน {{ money(afterItemDiscount) }}</small
              ><small
                >ผู้ได้รับ Commission กภ. {{ selectedStaffName }} · ประมาณการ
                {{ money(commissionAmount) }}</small
              >
            </section>
            <section>
              <span>ยอดชำระ</span><strong>{{ money(netAmount) }}</strong>
            </section>
          </div>
          <footer>
            <button v-if="mode === 'course'" type="button" @click="openPatientCourses">
              ดูคอร์สผู้ป่วย</button
            ><button type="button" @click="screen = 'receipt'">ดูและพิมพ์ใบเสร็จ</button
            ><button type="button" @click="backToServiceMenu">กลับหน้าบริการ</button>
          </footer>
        </section>

        <div
          v-if="paymentDialogOpen"
          class="payment-modal-backdrop"
          @click.self="paymentDialogOpen = false"
        >
          <section class="payment-modal">
            <h2>ยืนยันรับชำระเงิน</h2>
            <p>โปรดตรวจสอบอีกครั้งก่อนบันทึกการขาย</p>
            <div>
              <section>
                <span>ยอดที่ลูกค้าชำระ</span><strong>{{ money(netAmount) }}</strong
                ><small
                  >ช่องทางการชำระเงิน　{{
                    paymentMethods.find((method) => method.code === paymentCode)?.name || '-'
                  }}</small
                >
              </section>
              <section>
                <span>ฐานค่าคอมมิชชัน</span><b>{{ money(afterItemDiscount) }}</b
                ><small>ค่าคอมมิชชัน 10%　{{ money(commissionAmount) }}</small>
              </section>
            </div>
            <footer>
              <button type="button" @click="paymentDialogOpen = false">ยกเลิก</button
              ><button type="button" :disabled="loading" @click="submit">
                {{ loading ? 'กำลังบันทึก...' : 'ยืนยันการชำระเงิน' }}
              </button>
            </footer>
          </section>
        </div>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      </section>
    </main>
  </div>
</template>
<style scoped src="../styles/services.css"></style>
