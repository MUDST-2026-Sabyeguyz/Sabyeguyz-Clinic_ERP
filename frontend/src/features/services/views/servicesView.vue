<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { appointmentApi, branchApi, catalogApi, courseApi, patientApi, paymentMethodApi, salesApi, staffApi, type Branch, type Course, type Patient, type PaymentMethod, type SaleReceipt, type ServiceOption, type Staff } from '@/api/client'
type Mode = 'single' | 'course'
const query = ref(''),
  patient = ref<Patient | null>(null),
  mode = ref<Mode>('single'),
  action = ref<'use' | 'buy'>('use'),
  confirmed = ref(false),
  loading = ref(false),
  errorMessage = ref('')
const results = ref<Patient[]>([])
const services = ref<ServiceOption[]>([])
const courses = ref<Course[]>([])
const branches = ref<Branch[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const staff = ref<Staff[]>([])
const selectedServiceId = ref(0), selectedCourseId = ref(0), selectedStaffId = ref<number | undefined>()
const selectedBranchCode = ref('')
const paymentCode = ref(''), note = ref('')
const route = useRoute()
const router = useRouter()
const discountKind = ref<'none' | 'line' | 'bill'>('none')
const discountAmount = ref('')
const receipt = ref<SaleReceipt | null>(null)
async function loadMaster() {
  const [s, c, p, methods, staffList, allowedBranches] = await Promise.all([catalogApi.services(), catalogApi.courses(), patientApi.searchAll(), paymentMethodApi.list(), staffApi.list(), branchApi.list({ accessible: true })])
  services.value = s; courses.value = c; results.value = p; paymentMethods.value = methods; staff.value = staffList
  branches.value = allowedBranches.filter((branch) => branch.active)
  selectedBranchCode.value = branches.value[0]?.code ?? ''
  selectedServiceId.value = s[0]?.id ?? 0; selectedCourseId.value = c[0]?.id ?? 0; paymentCode.value = methods[0]?.code ?? ''; selectedStaffId.value = staffList[0]?.id
}
async function loadAppointmentHandoff() {
  const rawAppointmentId = route.query.appointmentId
  if (typeof rawAppointmentId !== 'string') return
  const appointmentId = Number(rawAppointmentId)
  if (!Number.isSafeInteger(appointmentId) || appointmentId < 1) throw new Error('เลขนัดหมายไม่ถูกต้อง')
  const appointment = await appointmentApi.get(appointmentId)
  const verifiedPatient = await patientApi.get(appointment.patient.id)
  const verifiedService = services.value.find((service) => service.id === appointment.service.id)
  const verifiedBranch = branches.value.find((branch) => branch.code === appointment.branchCode)
  if (!verifiedPatient.active) throw new Error('ผู้ป่วยนี้ปิดใช้งาน')
  if (!verifiedService) throw new Error('บริการของนัดหมายไม่อยู่ในรายการที่ใช้งาน')
  if (!verifiedBranch) throw new Error('ไม่มีสิทธิ์เข้าถึงสาขาของนัดหมาย')
  patient.value = verifiedPatient
  selectedBranchCode.value = verifiedBranch.code
  query.value = `${verifiedPatient.fullName} ${verifiedPatient.hn}`
  mode.value = 'single'
  selectedServiceId.value = verifiedService.id
}
onMounted(async () => { try { await loadMaster(); await loadAppointmentHandoff(); await loadReceiptFromRoute() } catch (error) { patient.value = null; errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลบริการไม่สำเร็จ' } })
async function loadReceiptFromRoute() {
  const rawId = route.query.receiptId
  if (typeof rawId !== 'string') return
  const id = Number(rawId)
  if (!Number.isSafeInteger(id) || id < 1) throw new Error('เลขที่ใบเสร็จไม่ถูกต้อง')
  receipt.value = await salesApi.get(id)
}
let searchTimer: number | undefined
watch(query, () => { window.clearTimeout(searchTimer); searchTimer = window.setTimeout(async () => { if (query.value.trim()) results.value = (await patientApi.search(query.value, 0, 20)).items }, 250) })
const item = computed(() =>
  mode.value === 'single'
    ? services.value.find((s) => s.id === selectedServiceId.value)?.name ?? 'บริการรายครั้ง'
    : courses.value.find((c) => c.id === selectedCourseId.value)?.name ?? 'คอร์ส',
)
const amount = computed(() =>
  mode.value === 'single'
    ? `฿${(services.value.find((s) => s.id === selectedServiceId.value)?.price ?? 0).toLocaleString()}`
    : action.value === 'buy'
      ? `฿${(courses.value.find((c) => c.id === selectedCourseId.value)?.price ?? 0).toLocaleString()}`
      : 'ใช้สิทธิ์ 1 ครั้ง',
)
const grossAmount = computed(() => mode.value === 'single'
  ? services.value.find((s) => s.id === selectedServiceId.value)?.price ?? 0
  : courses.value.find((c) => c.id === selectedCourseId.value)?.price ?? 0)
const fixedDiscount = computed(() => Number(discountAmount.value || 0))
const lineDiscount = computed(() => discountKind.value === 'line' ? fixedDiscount.value : 0)
const billDiscount = computed(() => discountKind.value === 'bill' ? fixedDiscount.value : 0)
const amountDue = computed(() => grossAmount.value - lineDiscount.value - billDiscount.value)
function printReceipt() { window.print() }
function choose(p: Patient) {
  patient.value = p
  query.value = `${p.fullName} ${p.hn}`
}
async function submit() {
  if (!patient.value || !selectedBranchCode.value) return
  loading.value = true; errorMessage.value = ''; confirmed.value = false
  try {
    if (mode.value === 'course' && action.value === 'use') {
      const owned = await courseApi.patientCourses(patient.value.id); const usable = owned.find((course) => course.usable)
      if (!usable) throw new Error('ผู้ป่วยไม่มีคอร์สที่ใช้ได้')
      await courseApi.use({ coursePurchaseId: usable.id, note: note.value })
    } else {
      const type = mode.value === 'single' ? 'SINGLE_VISIT' : 'COURSE'
      const referenceId = mode.value === 'course' ? selectedCourseId.value : selectedServiceId.value
      const created = await salesApi.create({ patientId: patient.value.id, branchCode: selectedBranchCode.value, salespersonStaffId: selectedStaffId.value, note: note.value,
        items: [{ type, referenceId, quantity: 1, ...(discountKind.value === 'line' ? { lineDiscountAmount: fixedDiscount.value } : {}) }],
        billDiscountAmount: billDiscount.value,
        payments: [{ paymentMethodCode: paymentCode.value, amount: amountDue.value }] })
      receipt.value = await salesApi.get(created.id)
      await router.replace({ query: { ...route.query, receiptId: String(created.id) } })
    }
    confirmed.value = true
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'บันทึกรายการไม่สำเร็จ' }
  finally { loading.value = false }
}
</script>
<template>
  <div class="service-page">
    <StaffHeader />
    <main>
      <h1>เลือกผู้ป่วยที่ต้องการทำรายการ</h1>
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
        <div v-if="query && results.length && !patient" class="results">
          <button v-for="p in results" :key="p.hn" @click="choose(p)">
            <b>{{ p.fullName }}</b
            ><small>{{ p.hn }} {{ p.phone }}</small>
          </button>
        </div>
      </div>
      <p v-if="!patient" class="hint">
        {{
          query && !results.length
            ? 'ไม่พบผู้ป่วยที่ตรงกับคำค้น'
            : 'พิมพ์ชื่อหรือเลข HN แล้วเลือกผู้ป่วยจากรายการ'
        }}
      </p>
      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      <section v-if="patient" class="card">
        <header>
          <div class="identity">
            <i>{{ patient.fullName[0] }}</i>
            <div>
              <h2>{{ patient.fullName }}</h2>
              <p>{{ patient.hn }} · สาขา {{ patient.active ? 'ใช้งาน' : 'ปิดใช้งาน' }}</p>
            </div>
          </div>
          <b>คอร์สที่ใช้งาน 1 คอร์ส</b>
        </header>
        <div class="body">
          <div class="title">
            <h2>รายละเอียดรายการ</h2>
            <RouterLink to="/staff/courses">จัดการคอร์ส</RouterLink>
          </div>
          <div class="category">
            <label
              >เลือกประเภทรายการ<select v-model="mode">
                <option value="single">Single Visit</option>
                <option value="course">Course</option>
              </select></label
            >
            <div v-if="mode === 'course'" class="toggle">
              <button :class="{ active: action === 'use' }" @click="action = 'use'">
                ใช้สิทธิ์</button
              ><button :class="{ active: action === 'buy' }" @click="action = 'buy'">
                ซื้อคอร์ส
              </button>
            </div>
          </div>
          <div class="fields">
            <label>สาขาที่ทำรายการ<select v-model="selectedBranchCode" aria-label="สาขาที่ทำรายการ">
              <option v-for="branch in branches" :key="branch.id" :value="branch.code">{{ branch.name }} ({{ branch.code }})</option>
            </select></label><label
              >{{
                mode === 'single' ? 'บริการรายครั้ง' : 'รายการคอร์ส'
              }}<select v-if="mode !== 'course'" v-model="selectedServiceId">
                <option v-for="service in services" :key="service.id" :value="service.id">{{ service.name }} ฿{{ service.price.toLocaleString() }}</option>
              </select><select v-else v-model="selectedCourseId">
                <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} ฿{{ course.price.toLocaleString() }}</option>
              </select></label
            ><label
              >ผู้ทำรายการ<select v-model="selectedStaffId">
                <option v-for="person in staff" :key="person.id" :value="person.id">{{ person.fullName }}</option>
              </select></label
            ><label v-if="mode !== 'course' || action === 'buy'"
              >ช่องทางชำระเงิน<select v-model="paymentCode">
                <option v-for="method in paymentMethods" :key="method.code" :value="method.code">{{ method.name }}</option>
              </select></label
            ><label v-if="mode !== 'course' || action === 'buy'">ส่วนลดแบบบาท<select v-model="discountKind" aria-label="ส่วนลดแบบบาท">
              <option value="none">ไม่มีส่วนลด</option><option value="line">ส่วนลดรายการ</option><option value="bill">ส่วนลดท้ายบิล</option>
            </select></label><label v-if="discountKind !== 'none'">จำนวนส่วนลด (บาท)<input v-model="discountAmount" aria-label="จำนวนส่วนลดบาท" type="number" min="0" step="0.01"></label
            ><label class="note">หมายเหตุ<textarea v-model.trim="note" /></label>
          </div>
        </div>
        <footer>
          <h3>รายการที่เลือก</h3>
          <div>
            <strong>{{ item }}</strong
            ><b>{{ amount }}</b
            ><p>ก่อนส่วนลด ฿{{ grossAmount.toLocaleString() }} · ส่วนลด ฿{{ (lineDiscount + billDiscount).toLocaleString() }} · ยอดชำระ ฿{{ amountDue.toLocaleString() }}</p
            ><button :disabled="loading || !selectedBranchCode" @click="submit">{{ loading ? 'กำลังบันทึก...' : 'ยืนยันรายการ' }}</button>
          </div>
        </footer>
        <p v-if="confirmed" class="success">บันทึกรายการเรียบร้อยแล้ว</p>
      </section>
      <section v-if="receipt" class="receipt-preview" aria-label="ใบเสร็จสำหรับพิมพ์">
        <h2>ใบเสร็จ {{ receipt.receiptNo }}</h2>
        <p>{{ receipt.patient.fullName }} · HN {{ receipt.patient.hn }} · สาขา {{ receipt.branchCode }}</p>
        <ul><li v-for="line in receipt.items" :key="line.id">{{ line.description }}: gross ฿{{ line.grossAmount.toLocaleString() }}, ส่วนลดรายการ ฿{{ line.lineDiscountAmount.toLocaleString() }}, ยอดรายการ ฿{{ line.lineTotal.toLocaleString() }}, commission base ฿{{ line.commissionBase.toLocaleString() }}</li></ul>
        <p>gross ฿{{ receipt.grossTotal.toLocaleString() }} · ส่วนลดรายการ ฿{{ receipt.lineDiscountTotal.toLocaleString() }} · ส่วนลดท้ายบิล ฿{{ receipt.billDiscountAmount.toLocaleString() }}</p>
        <p>ยอดสุทธิ ฿{{ receipt.total.toLocaleString() }} · รับเงินจริง ฿{{ receipt.paid.toLocaleString() }} · commission base ฿{{ receipt.commissionBase.toLocaleString() }}</p>
        <button type="button" @click="printReceipt">พิมพ์ใบเสร็จ</button>
      </section>
    </main>
  </div>
</template>
<style scoped src="../styles/services.css"></style>
