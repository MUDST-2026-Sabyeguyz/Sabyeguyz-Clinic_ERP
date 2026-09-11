<script setup lang="ts">
import { computed, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { patients, type PatientProfile } from '@/features/patients/data/mockPatients'
type Mode = 'assessment' | 'single' | 'course'
const query = ref(''),
  patient = ref<PatientProfile | null>(null),
  mode = ref<Mode>('assessment'),
  action = ref<'use' | 'buy'>('use'),
  confirmed = ref(false)
const results = computed(() => {
  const q = query.value.toLowerCase().trim()
  return q ? patients.filter((p) => `${p.fullName} ${p.hn}`.toLowerCase().includes(q)) : []
})
const item = computed(() =>
  mode.value === 'assessment'
    ? 'Initial Assessment'
    : mode.value === 'single'
      ? 'Manual Therapy'
      : 'Manual Therapy 10 ครั้ง',
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
  query.value = `${p.fullName} ${p.hn}`
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
            ><button @click="confirmed = true">ยืนยันรายการ</button>
          </div>
        </footer>
        <p v-if="confirmed" class="success">บันทึกรายการเรียบร้อยแล้ว</p>
      </section>
    </main>
  </div>
</template>
<style scoped src="../styles/services.css"></style>
