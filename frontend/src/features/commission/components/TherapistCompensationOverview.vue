<script setup lang="ts">
// สรุปยอดคอมมิชชัน ค่ามือ และสถานะงวดจ่าย
import { computed } from 'vue'
import type { StaffPayment, TherapistAdjustment, TherapistLine } from '@/api/client'
import { baht, monthLabel, signedBaht, statusLabel } from '../lib/therapistPay'
import TherapistCompensationLineList from './TherapistCompensationLineList.vue'

const props = defineProps<{
  payment: StaffPayment | null
  lines: TherapistLine[]
  month: string
  adjustments: TherapistAdjustment[]
  pendingSessions: number | null
}>()

const isFinal = computed(
  () => props.payment?.status === 'CLOSED' || props.payment?.status === 'PAID',
)

// งวดที่ยังเปิดอยู่ให้รวมยอดจากรายการจริง เพื่อไม่ให้การ์ดแสดง ฿0.00 ทั้งที่มีรายการแล้ว
const sumLines = (kind: 'COMMISSION' | 'FEE') =>
  props.lines
    .filter((line) => line.kind === kind && line.amount !== null)
    .reduce((total, line) => total + Number(line.amount), 0)
const commissionLines = computed(() =>
  props.lines.filter((line) => line.kind === 'COMMISSION' && line.amount !== null),
)

const commission = computed<number | null>(() => {
  if (props.payment?.courseCommission != null) return props.payment.courseCommission
  return commissionLines.value.length ? sumLines('COMMISSION') : null
})
const treatment = computed(() =>
  isFinal.value
    ? (props.payment?.singleVisitFee ?? 0) + (props.payment?.substituteFee ?? 0)
    : sumLines('FEE'),
)
const adjustment = computed(() => props.payment?.adjustment ?? 0)
// Net Payout ต้องเป็นยอดจริงเท่านั้น ถ้ายังไม่ปิดยอดให้แสดงว่ารอคำนวณ ไม่แสดงยอดบางส่วนเป็นยอดสุทธิ
const net = computed<number | null>(() =>
  isFinal.value && props.payment?.net != null ? props.payment.net : null,
)
const waitingCommission = computed(() => commission.value === null)

// รายการปรับที่ถูกนำมาหักหรือเพิ่มในเดือนนี้ พร้อมเหตุผลและเดือนต้นทาง
const appliedAdjustments = computed(() =>
  props.adjustments
    .map((row) => ({
      row,
      amount: row.applications
        .filter((application) => application.month === props.month)
        .reduce((total, application) => total + Number(application.amount), 0),
      applied: row.applications.some((application) => application.month === props.month),
    }))
    .filter((entry) => entry.applied),
)
</script>

<template>
  <section class="therapist-overview-grid">
    <article class="therapist-summary-card" aria-label="สรุปค่าตอบแทน">
      <header class="summary-head">
        <div>
          <strong>{{ payment?.staffName ?? 'สรุปค่าตอบแทน' }}</strong>
          <small>Payout Summary — {{ monthLabel(month) }}</small>
        </div>
        <span :class="['summary-badge', isFinal ? 'final' : 'draft']">
          {{ statusLabel(payment?.status) }}
        </span>
      </header>
      <dl>
        <div>
          <dt>Course Commission</dt>
          <dd :class="{ muted: waitingCommission }">
            <template v-if="!waitingCommission">{{ baht(commission) }}</template>
            <template v-else>รอปิดยอด</template>
          </dd>
        </div>
        <div>
          <dt>ค่ามือ</dt>
          <dd>{{ signedBaht(treatment) }}</dd>
        </div>
        <div v-if="appliedAdjustments.length === 0">
          <dt>รายการปรับค่าตอบแทน</dt>
          <dd>{{ signedBaht(adjustment) }}</dd>
        </div>
        <div v-for="entry in appliedAdjustments" :key="entry.row.id">
          <dt>
            รายการปรับ ({{ monthLabel(entry.row.originalMonth) }})
            <small
              >{{ entry.row.reason
              }}<template v-if="entry.row.reference"> · {{ entry.row.reference }}</template></small
            >
          </dt>
          <dd :class="{ negative: entry.amount < 0 }">{{ signedBaht(entry.amount) }}</dd>
        </div>
      </dl>
      <div class="summary-net">
        <span>Net Payout</span>
        <strong v-if="net !== null" :class="{ negative: net < 0 }">{{ signedBaht(net) }}</strong>
        <strong v-else class="muted">รอคำนวณ</strong>
      </div>
      <small v-if="pendingSessions === null" class="therapist-pending-note">
        ยังไม่ทราบจำนวนครั้งที่รอปิดยอด
      </small>
      <small v-else-if="pendingSessions > 0" class="therapist-pending-note">
        {{ pendingSessions }} ครั้งที่เดือนขายคอร์สยังไม่ปิดยอด จึงยังไม่นับเข้าค่าคอมมิชชัน
      </small>
    </article>
    <article class="therapist-principle-card">
      <h2>หลักการคำนวณ</h2>
      <div>
        <b>Course Commission</b>
        <p>
          คุณได้ค่าคอมมิชชันเมื่อเป็นเจ้าของคอร์สและเป็นคนรักษาในครั้งนั้น โดยใช้อัตราที่ล็อกไว้ ณ
          เดือนที่ขายคอร์ส
        </p>
      </div>
      <div>
        <b>ค่ามือ</b>
        <p>คุณได้ค่ามือเมื่อเป็นคนรักษาจริง ทั้งการรักษาแบบรายครั้งและการรักษาแทนเจ้าของคอร์ส</p>
      </div>
      <RouterLink class="therapist-formula-link" to="/staff/commission/formula"
        >ดูรายละเอียดวิธีคำนวณ →</RouterLink
      >
    </article>
  </section>
  <section class="therapist-table-card">
    <h2>รายละเอียดค่าตอบแทน</h2>
    <TherapistCompensationLineList
      :lines="lines"
      :month="month"
      empty-text="ยังไม่มีรายการค่าตอบแทนในเดือนนี้"
    />
  </section>
</template>
