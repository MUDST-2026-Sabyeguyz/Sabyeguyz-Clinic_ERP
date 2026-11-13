<script setup lang="ts">
import { computed } from 'vue'
import type { TherapistLine } from '@/api/client'
import { baht, lineKey, lineStatusText, longDate, percent } from '../lib/therapistPay'
import TherapistCompensationEmptyState from './TherapistCompensationEmptyState.vue'

const props = defineProps<{
  kind: 'sale' | 'treatment'
  line: TherapistLine | null
  month: string
}>()

const back = computed(() => ({
  name: props.kind === 'sale' ? 'therapist-commission-detail' : 'therapist-treatment-fee-detail',
  params: { id: props.line ? lineKey(props.line) : '' },
  query: { month: props.month },
}))
const saleRows = computed<[string, string][]>(() => {
  const line = props.line
  if (!line) return []
  return [
    ['ประเภทบริการ', line.saleType ?? '—'],
    ['ยอดก่อนส่วนลด', baht(line.grossAmount)],
    ['ส่วนลดรายการ', baht(line.lineDiscount)],
    ['ยอดหลังส่วนลดรายการ', baht(line.base)],
  ]
})
const treatmentRows = computed<[string, string][]>(() => {
  const line = props.line
  if (!line) return []
  return [
    ['เจ้าของเคส', line.ownerName ?? '—'],
    ['ผู้รักษาจริง', line.treatingName ?? '—'],
    ['สถานะรายการรักษา', 'บันทึกแล้ว'],
    ['สถานะค่าตอบแทน', lineStatusText(line.paymentStatus)],
  ]
})
</script>

<template>
  <section v-if="props.kind === 'sale' && line" class="therapist-source-card therapist-sale-source">
    <header class="therapist-source-heading">
      <h1>รายละเอียดรายการขายต้นทาง</h1>
      <p>{{ line.saleNo ?? '—' }} · {{ longDate(line.saleDate) }} · อ่านอย่างเดียว</p>
    </header>

    <section class="therapist-source-patient-grid" aria-label="ข้อมูลผู้ป่วยและบริการ">
      <div>
        <span>ผู้ป่วย</span>
        <b>{{ line.patientName }}</b>
      </div>
      <div>
        <span>เลข HN</span>
        <b>{{ line.patientHn ?? '—' }}</b>
      </div>
      <div>
        <span>บริการ</span>
        <b>{{ line.item }}</b>
      </div>
    </section>

    <div class="therapist-sale-source-grid">
      <section class="therapist-source-info-card">
        <h2>ข้อมูลรายการขาย</h2>
        <dl class="therapist-source-value-list">
          <div
            v-for="([label, value], index) in saleRows"
            :key="label"
            :class="{ 'is-last': index === saleRows.length - 1 }"
          >
            <dt>{{ label }}</dt>
            <dd>{{ value }}</dd>
          </div>
        </dl>
      </section>

      <section class="therapist-source-info-card therapist-source-compensation-card">
        <h2>ที่มาของค่าตอบแทน</h2>
        <span>ฐาน Commission</span>
        <strong>{{ baht(line.base) }}</strong>
        <p v-if="line.rate !== null">
          {{ line.tier ?? 'Tier' }} (อัตราที่ล็อก {{ percent(line.rate) }})
        </p>
        <p v-else>รอปิดยอดเดือนที่ขาย จึงยังไม่ล็อกอัตรา</p>
        <b v-if="line.rate !== null"
          >{{ baht(line.base) }} × {{ percent(line.rate) }} = {{ baht(line.pool) }}</b
        >
        <p class="therapist-source-rule-note">
          ส่วนลดรายการลดฐาน Commission ส่วนลดท้ายบิลไม่ลดฐาน Commission
        </p>
      </section>
    </div>

    <footer class="therapist-source-footer">
      <p>หากข้อมูลผิด ให้แจ้ง Manager นอกระบบเพื่อดำเนินการตามสถานะรอบ</p>
      <RouterLink class="therapist-primary-action" :to="back">
        กลับรายละเอียดค่าคอมมิชชัน
      </RouterLink>
    </footer>
  </section>

  <section v-else-if="line" class="therapist-source-card therapist-sale-source">
    <header class="therapist-source-heading">
      <h1>รายละเอียดรายการรักษาต้นทาง</h1>
      <p>{{ line.visitNo ?? '—' }} · {{ longDate(line.date) }} · อ่านอย่างเดียว</p>
    </header>

    <section class="therapist-source-patient-grid" aria-label="ข้อมูลผู้ป่วยและบริการ">
      <div>
        <span>ผู้ป่วย</span>
        <b>{{ line.patientName }}</b>
      </div>
      <div>
        <span>เลข HN</span>
        <b>{{ line.patientHn ?? '—' }}</b>
      </div>
      <div>
        <span>บริการ</span>
        <b>{{ line.item }}</b>
      </div>
    </section>

    <div class="therapist-sale-source-grid">
      <section class="therapist-source-info-card">
        <h2>ข้อมูลการรักษา</h2>
        <dl class="therapist-source-value-list">
          <div
            v-for="([label, value], index) in treatmentRows"
            :key="label"
            :class="{ 'is-last': index === treatmentRows.length - 1 }"
          >
            <dt>{{ label }}</dt>
            <dd>{{ value }}</dd>
          </div>
        </dl>
      </section>

      <section class="therapist-source-info-card therapist-source-compensation-card">
        <h2>ที่มาของค่าตอบแทน</h2>
        <span>Treatment Fee Rule</span>
        <strong>{{ baht(line.amount) }}</strong>
        <p>
          {{
            line.feeType === 'Percentage'
              ? `${Math.round(Number(line.feeValue ?? 0) * 100) / 100}% ของ ${baht(line.feeBase)} ตามกฎที่มีผล ณ วันรักษา`
              : 'จำนวนเงินคงที่ตามกฎที่มีผล ณ วันรักษา'
          }}
        </p>
        <b>ค่ามือสุทธิ {{ baht(line.amount) }}</b>
        <p class="therapist-source-rule-note">ค่ามือแยกจาก Commission และคำนวณตามผู้รักษาจริง</p>
      </section>
    </div>

    <footer class="therapist-source-footer">
      <p>หากข้อมูลผิด ให้แจ้ง Manager นอกระบบเพื่อดำเนินการตามสถานะรอบ</p>
      <RouterLink class="therapist-primary-action" :to="back"> กลับรายละเอียดค่าตอบแทน </RouterLink>
    </footer>
  </section>

  <TherapistCompensationEmptyState v-else />
</template>
