<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { patients, type PatientProfile } from '@/features/patients/data/mockPatients'
type Mode = 'assessment' | 'single' | 'course'
const query = ref(''),
  patient = ref<PatientProfile | null>(null),
  mode = ref<Mode>('assessment'),
  action = ref<'use' | 'buy'>('use'),
  confirmed = ref(false)
const router = useRouter()
const results = computed(() => {
  const q = query.value.toLowerCase().trim()
  return q ? patients.filter((p) => `${p.fullName} ${p.hn}`.toLowerCase().includes(q)) : []
})
const item = computed(() =>
  mode.value === 'assessment'
    ? 'Initial Assessment'
    : mode.value === 'single'
      ? 'Manual Therapy'
      : 'Manual Therapy 10 Sessions',
)
const transactionType = computed(() =>
  mode.value === 'assessment'
    ? 'Assessment'
    : mode.value === 'single'
      ? 'Single Visit'
      : action.value === 'buy'
         ? 'Course Purchase'
         : 'Course Usage',
)
const transactionIdPrefix = computed(() =>
  mode.value === 'course' && action.value === 'use' ? 'TRT' : 'PAY',
)
const amount = computed(() =>
  mode.value === 'assessment'
    ? '฿1,200'
    : mode.value === 'single'
      ? '฿1,000'
      : action.value === 'buy'
        ? '฿8,500'
        : 'ใช้สิทธิ์ 1 ครั้ง',
)
const serviceOptionLabel = computed(() => {
  if (mode.value === 'assessment') return `${item.value} ${amount.value}`
  if (mode.value === 'single') return `${item.value} ${amount.value}`
  return item.value
})
function choose(p: PatientProfile) {
  patient.value = p
  query.value = p.fullName
}
function confirmTransaction() {
  if (!patient.value) return
  const now = new Date()
  const months = ['ม.ค.', 'ก.พ.', 'มี.ค.', 'เม.ย.', 'พ.ค.', 'มิ.ย.', 'ก.ค.', 'ส.ค.', 'ก.ย.', 'ต.ค.', 'พ.ย.', 'ธ.ค.']
  const date = `${now.getDate()} ${months[now.getMonth()]} ${now.getFullYear()} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
  localStorage.setItem('newServiceTransaction', JSON.stringify({
    id: `${transactionIdPrefix.value}-${String(now.getFullYear()).slice(-2)}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}-${String(now.getTime()).slice(-4)}`,
    date, patient: patient.value.fullName, hn: patient.value.hn, branch: patient.value.branch,
    performedBy: 'เจ้าหน้าที่คลินิก', type: transactionType.value, detail: item.value, amount: amount.value, status: 'สำเร็จ',
  }))
  router.push('/staff/transactions')
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
            ><small>{{ p.hn }}</small>
          </button>
        </div>
      </div>
      <p v-if="!patient" class="hint">
        {{
          query && !results.length
            ? 'ไม่พบผู้ป่วย'
            : 'พิมพ์ชื่อหรือหมายเลขประจำตัวในช่องค้นหา'
        }}
      </p>
      <section v-else class="card">
        <header>
          <div class="identity">
            <i>{{ patient.fullName[0] }}</i>
            <div>
              <h2>{{ patient.title }}{{ patient.fullName }}</h2>
              <p>{{ patient.hn }} {{ patient.branch }}</p>
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
                <option value="assessment">Assessment</option>
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
            <label
              >{{
                mode === 'assessment'
                  ? 'รายการประเมิน'
                  : mode === 'single'
                    ? 'บริการรายครั้ง'
                    : 'รายการคอร์ส'
              }}<select>
                <option>{{ serviceOptionLabel }}</option>
              </select></label
            ><label
              >ผู้ให้บริการ<select>
                <option>ศิริพร</option>
                <option>กฤษดา</option>
              </select></label
            ><label v-if="mode !== 'course' || action === 'buy'"
              >ช่องทางชำระเงิน<select>
                <option>QR</option>
                <option>เงินสด</option>
                <option>โอนเงิน</option>
              </select></label
            ><label class="note">หมายเหตุ<textarea /></label>
          </div>
        </div>
        <footer>
          <h3>รายการที่เลือก</h3>
          <div>
            <strong>{{ item }}</strong
            ><b>{{ amount }}</b
            ><button @click="confirmTransaction">ยืนยันรายการ</button>
          </div>
        </footer>
        <p v-if="confirmed" class="success">บันทึกรายการเรียบร้อยแล้ว</p>
      </section>
    </main>
  </div>
</template>
<style scoped src="../styles/services.css"></style>
