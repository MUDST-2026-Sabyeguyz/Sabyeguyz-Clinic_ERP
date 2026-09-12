<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { catalogApi, courseApi, patientApi, paymentMethodApi, salesApi, staffApi, type Course, type Patient, type PaymentMethod, type ServiceOption, type Staff } from '@/api/client'
type Mode = 'assessment' | 'single' | 'course'
const query = ref(''),
  patient = ref<Patient | null>(null),
  mode = ref<Mode>('assessment'),
  action = ref<'use' | 'buy'>('use'),
  confirmed = ref(false),
  loading = ref(false),
  errorMessage = ref('')
const results = ref<Patient[]>([])
const services = ref<ServiceOption[]>([])
const courses = ref<Course[]>([])
const paymentMethods = ref<PaymentMethod[]>([])
const staff = ref<Staff[]>([])
const selectedServiceId = ref(0), selectedCourseId = ref(0), selectedStaffId = ref<number | undefined>()
const paymentCode = ref(''), note = ref('')
async function loadMaster() {
  const [s, c, p, methods, staffList] = await Promise.all([catalogApi.services(), catalogApi.courses(), patientApi.searchAll(), paymentMethodApi.list(), staffApi.list()])
  services.value = s; courses.value = c; results.value = p; paymentMethods.value = methods; staff.value = staffList
  selectedServiceId.value = s[0]?.id ?? 0; selectedCourseId.value = c[0]?.id ?? 0; paymentCode.value = methods[0]?.code ?? ''; selectedStaffId.value = staffList[0]?.id
}
onMounted(async () => { try { await loadMaster() } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลบริการไม่สำเร็จ' } })
let searchTimer: number | undefined
watch(query, () => { window.clearTimeout(searchTimer); searchTimer = window.setTimeout(async () => { if (query.value.trim()) results.value = (await patientApi.search(query.value, 0, 20)).items }, 250) })
const item = computed(() =>
  mode.value === 'assessment'
    ? services.value.find((s) => s.code.includes('ASSESS') || s.name.toLowerCase().includes('assessment'))?.name ?? services.value[0]?.name ?? 'Assessment'
    : mode.value === 'single'
      ? services.value.find((s) => s.id === selectedServiceId.value)?.name ?? 'บริการรายครั้ง'
      : courses.value.find((c) => c.id === selectedCourseId.value)?.name ?? 'คอร์ส',
)
const amount = computed(() =>
  mode.value === 'assessment'
    ? `฿${(services.value.find((s) => s.name === item.value)?.price ?? 0).toLocaleString()}`
    : mode.value === 'single'
      ? `฿${(services.value.find((s) => s.id === selectedServiceId.value)?.price ?? 0).toLocaleString()}`
      : action.value === 'buy'
        ? `฿${(courses.value.find((c) => c.id === selectedCourseId.value)?.price ?? 0).toLocaleString()}`
        : 'ใช้สิทธิ์ 1 ครั้ง',
)
function choose(p: Patient) {
  patient.value = p
  query.value = `${p.fullName} ${p.hn}`
}
async function submit() {
  if (!patient.value) return
  loading.value = true; errorMessage.value = ''; confirmed.value = false
  try {
    if (mode.value === 'course' && action.value === 'use') {
      const owned = await courseApi.patientCourses(patient.value.id); const usable = owned.find((course) => course.usable)
      if (!usable) throw new Error('ผู้ป่วยไม่มีคอร์สที่ใช้ได้')
      await courseApi.use({ coursePurchaseId: usable.id, note: note.value })
    } else {
      const type = mode.value === 'assessment' ? 'ASSESSMENT' : mode.value === 'single' ? 'SINGLE_VISIT' : 'COURSE'
      const referenceId = mode.value === 'course' ? selectedCourseId.value : selectedServiceId.value
      const price = mode.value === 'course' ? courses.value.find((c) => c.id === referenceId)?.price ?? 0 : services.value.find((s) => s.id === referenceId)?.price ?? 0
      await salesApi.create({ patientId: patient.value.id, branchCode: 'RP', salespersonStaffId: selectedStaffId.value, note: note.value, items: [{ type, referenceId, quantity: 1 }], payments: [{ paymentMethodCode: paymentCode.value, amount: price }] })
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
            data-test="service-patient-search"
            v-model="query"
            aria-label="ค้นหาผู้ป่วย"
            placeholder="ค้นหาชื่อผู้ป่วยหรือเลขประจำตัวผู้ป่วย"
            @input="patient = null"
          />
        </label>
        <div v-if="query && results.length && !patient" class="results">
          <button v-for="p in results" :key="p.hn" :data-test="`service-patient-option-${p.id}`" @click="choose(p)">
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
      <section v-else class="card">
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
              >เลือกประเภทรายการ<select data-test="service-mode-select" v-model="mode">
                <option value="assessment">Assessment</option>
                <option value="single">Single Visit</option>
                <option value="course">Course</option>
              </select></label
            >
            <div v-if="mode === 'course'" class="toggle">
                <button data-test="use-course-button" :class="{ active: action === 'use' }" @click="action = 'use'">
                ใช้สิทธิ์</button
              ><button data-test="buy-course-button" :class="{ active: action === 'buy' }" @click="action = 'buy'">
                ซื้อคอร์ส
              </button>
            </div>
          </div>
          <div class="fields">
            <label
              >{{
                mode === 'assessment'
                  ? 'รายการประเมิน'
                  : mode === 'single'
                    ? 'บริการรายครั้ง'
                    : 'รายการคอร์ส'
              }}<select v-if="mode !== 'course'" data-test="service-item-select" v-model="selectedServiceId">
                <option v-for="service in services" :key="service.id" :value="service.id">{{ service.name }} ฿{{ service.price.toLocaleString() }}</option>
              </select><select v-else data-test="course-item-select" v-model="selectedCourseId">
                <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} ฿{{ course.price.toLocaleString() }}</option>
              </select></label
            ><label
              >ผู้ทำรายการ<select data-test="service-staff-select" v-model="selectedStaffId">
                <option v-for="person in staff" :key="person.id" :value="person.id">{{ person.fullName }}</option>
              </select></label
            ><label v-if="mode !== 'course' || action === 'buy'"
              >ช่องทางชำระเงิน<select data-test="payment-method-select" v-model="paymentCode">
                <option v-for="method in paymentMethods" :key="method.code" :value="method.code">{{ method.name }}</option>
              </select></label
             ><label class="note">หมายเหตุ<textarea data-test="service-note" v-model.trim="note" /></label>
          </div>
        </div>
        <footer>
          <h3>รายการที่เลือก</h3>
          <div>
            <strong>{{ item }}</strong
            ><b>{{ amount }}</b
            ><button data-test="confirm-service-button" :disabled="loading" @click="submit">{{ loading ? 'กำลังบันทึก...' : 'ยืนยันรายการ' }}</button>
          </div>
        </footer>
        <p v-if="confirmed" class="success" data-test="service-success">บันทึกรายการเรียบร้อยแล้ว</p>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      </section>
    </main>
  </div>
</template>
<style scoped src="../styles/services.css"></style>
