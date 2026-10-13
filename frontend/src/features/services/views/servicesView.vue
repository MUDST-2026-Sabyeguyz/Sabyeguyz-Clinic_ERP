<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import {
  branchApi,
  catalogApi,
  patientApi,
  paymentMethodApi,
  salesApi,
  staffApi,
  type Course,
  type Patient,
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
const services = ref<ServiceOption[]>([])
const courses = ref<Course[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const staff = ref<Staff[]>([])
const selectedServiceId = ref(0),
  selectedCourseId = ref(0),
  selectedStaffId = ref<number | undefined>()
const paymentCode = ref(''),
  note = ref('')
const searched = ref(false)
const receiptNo = ref('')
const screen = ref<'search' | 'menu' | 'sale'>(route.query.patientId ? 'menu' : 'search')
const assessmentService = computed(
  () =>
    services.value.find(
      (service) =>
        service.code.includes('ASSESS') || service.name.toLowerCase().includes('assessment'),
    ) ?? services.value[0],
)
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
  const patientId = Number(route.query.patientId)
  const selectedPatient = p.find((item) => item.id === patientId)
  if (selectedPatient) choose(selectedPatient)
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
      errorMessage.value = error instanceof Error ? error.message : 'ค้นหาผู้ป่วยไม่สำเร็จ'
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
const amount = computed(() =>
  mode.value === 'assessment'
    ? `฿${(assessmentService.value?.price ?? 0).toLocaleString()}`
    : mode.value === 'single'
      ? `฿${(services.value.find((s) => s.id === selectedServiceId.value)?.price ?? 0).toLocaleString()}`
      : `฿${(courses.value.find((c) => c.id === selectedCourseId.value)?.price ?? 0).toLocaleString()}`,
)
function choose(p: Patient) {
  patient.value = p
  query.value = `${p.fullName} ${p.hn}`
  screen.value = 'menu'
}
function searchPatients() {
  if (query.value.trim()) searched.value = true
}
function openPatientCourses() {
  if (patient.value) router.push({ path: '/staff/courses', query: { patientId: patient.value.id } })
}
function clearPatient() {
  patient.value = null
  query.value = ''
  searched.value = false
  reviewing.value = false
  confirmed.value = false
  screen.value = 'search'
}
function backToServiceMenu() {
  reviewing.value = false
  confirmed.value = false
  screen.value = 'menu'
}
function startSale() {
  screen.value = 'sale'
  mode.value = 'assessment'
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
      payments: [{ paymentMethodCode: paymentCode.value, amount: price }],
    })
    receiptNo.value = sale.receiptNo
    confirmed.value = true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'บันทึกรายการไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
</script>
<template>
  <div class="service-page">
    <StaffHeader />
    <main>
      <template v-if="screen === 'search'">
        <h1>บริการและคอร์ส</h1>
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
                placeholder="ค้นหาชื่อผู้ป่วยหรือเลขประจำตัวผู้ป่วย"
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
            <button
              type="button"
              @click="startSale"
            >
              เริ่มขายบริการ
            </button>
          </article>
          <article>
            <h2>ใช้สิทธิ์และจัดการคอร์ส</h2>
            <p>ดูสิทธิ์คงเหลือ ประวัติการใช้<br />และบันทึกการใช้สิทธิ์คอร์ส</p>
            <button type="button" @click="openPatientCourses">ดูคอร์สผู้ป่วย</button>
          </article>
        </div>
      </section>
      <section v-else-if="patient" class="sale-page">
        <template v-if="!reviewing && !confirmed">
          <button type="button" class="service-back" @click="backToServiceMenu">
            ← กลับไปหน้าบริการ
          </button>
          <h1>ขายบริการและคอร์ส</h1>
          <section class="patient-summary">
            <strong>{{ patient.fullName }}</strong>
            <span>HN {{ patient.hn }}</span>
          </section>
        </template>
        <div v-if="!reviewing && !confirmed" class="sale-columns">
          <section class="sale-details">
            <h2>รายละเอียดรายการ</h2>
            <div class="sale-tabs" role="tablist" aria-label="ประเภทรายการ">
              <button :class="{ active: mode === 'assessment' }" @click="mode = 'assessment'">
                Assessment
              </button>
              <button :class="{ active: mode === 'single' }" @click="mode = 'single'">
                Single Visit
              </button>
              <button :class="{ active: mode === 'course' }" @click="mode = 'course'">
                Course
              </button>
            </div>
            <label
              >{{
                mode === 'assessment'
                  ? 'รายการประเมิน'
                  : mode === 'single'
                    ? 'บริการรายครั้ง'
                    : 'รายการคอร์ส'
              }}
              <select v-if="mode !== 'course'" v-model="selectedServiceId">
                <option v-for="service in services" :key="service.id" :value="service.id">
                  {{ service.name }} · ฿{{ service.price.toLocaleString() }}
                </option>
              </select>
              <select v-else v-model="selectedCourseId">
                <option v-for="course in courses" :key="course.id" :value="course.id">
                  {{ course.name }} · ฿{{ course.price.toLocaleString() }}
                </option>
              </select>
            </label>
            <label
              >ผู้ให้บริการ
              <select v-model="selectedStaffId">
                <option v-for="person in staff" :key="person.id" :value="person.id">
                  {{ person.fullName }}
                </option>
              </select>
            </label>
            <div class="payment-group">
              <span>ช่องทางชำระเงิน</span>
              <div class="payment-options">
                <button
                  v-for="method in paymentMethods"
                  :key="method.code"
                  :class="{ active: paymentCode === method.code }"
                  @click="paymentCode = method.code"
                >
                  {{ method.name }}
                </button>
              </div>
            </div>
            <label
              >หมายเหตุ (ไม่บังคับ)<textarea
                v-model.trim="note"
                placeholder="เพิ่มรายละเอียดเกี่ยวกับรายการขาย"
              />
            </label>
          </section>
          <aside class="order-summary">
            <h2>สรุปรายการ</h2>
            <span>{{
              mode === 'assessment' ? 'Assessment' : mode === 'single' ? 'Single Visit' : 'Course'
            }}</span>
            <strong>{{ item }}</strong>
            <p>ยอดชำระทั้งหมด</p>
            <b>{{ amount }}</b>
            <p>
              ช่องทาง:
              {{ paymentMethods.find((method) => method.code === paymentCode)?.name || '-' }}
            </p>
            <button :disabled="loading" @click="reviewing = true">ตรวจสอบและชำระเงิน</button>
            <small>การยืนยันจะบันทึกการขาย ไม่ใช่การตัดเงินอัตโนมัติ</small>
          </aside>
        </div>
        <section v-if="reviewing && !confirmed" class="payment-review">
          <h2>ยืนยันการชำระเงิน</h2>
          <p>ตรวจสอบรายการและยอดเงินก่อนบันทึกการขาย</p>
          <div class="payment-review-details">
            <span
              >สาขาราชพฤกษ์ · ผู้ให้บริการ
              {{ staff.find((person) => person.id === selectedStaffId)?.fullName || '-' }}</span
            >
            <span
              >ประเภท:
              {{
                mode === 'assessment' ? 'Assessment' : mode === 'single' ? 'Single Visit' : 'Course'
              }}</span
            >
            <b>{{ item }}</b>
            <em>ยอดชำระ {{ amount }}</em>
            <span
              >ช่องทาง:
              {{ paymentMethods.find((method) => method.code === paymentCode)?.name || '-' }}</span
            >
          </div>
          <div class="payment-review-actions">
            <button type="button" @click="reviewing = false">ยกเลิก</button>
            <button type="button" :disabled="loading" @click="submit">
              {{ loading ? 'กำลังบันทึก...' : 'ยืนยัน' }}
            </button>
          </div>
        </section>
        <section v-if="confirmed" class="receipt-confirmation">
          <h2>บันทึกการขายสำเร็จ</h2>
          <p>
            {{
              mode === 'assessment' ? 'Assessment' : mode === 'single' ? 'Single Visit' : 'Course'
            }}
            · {{ item }}
          </p>
          <p>ยอดชำระ {{ amount }}</p>
          <p>สาขาราชพฤกษ์</p>
          <p>
            ผู้ให้บริการ
            {{ staff.find((person) => person.id === selectedStaffId)?.fullName || '-' }}
          </p>
          <p>
            ช่องทาง: {{ paymentMethods.find((method) => method.code === paymentCode)?.name || '-' }}
          </p>
          <small>เลขรายการ: {{ receiptNo }}</small>
          <RouterLink
            v-if="mode === 'course'"
            class="receipt-action"
            :to="{ path: '/staff/courses', query: { patientId: patient.id, action: 'use' } }"
            >ใช้สิทธิ์</RouterLink
          >
          <button v-else type="button" @click="backToServiceMenu">กลับหน้าบริการ</button>
        </section>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      </section>
    </main>
  </div>
</template>
<style scoped src="../styles/services.css"></style>
