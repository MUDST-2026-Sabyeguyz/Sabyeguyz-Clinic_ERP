<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import TherapistCompensationEmptyState from '../components/TherapistCompensationEmptyState.vue'
import TherapistCompensationShell from '../components/TherapistCompensationShell.vue'
import { monthLabel, signedBaht, useTherapistPay } from '../lib/therapistPay'

// A correction of a month that was already paid, worked off in later months. Read only: the Manager creates and approves it.
const route = useRoute()
const { adjustments, loading, errorMessage } = useTherapistPay()
const adjustment = computed(
  () =>
    adjustments.value.find((row) => String(row.id) === String(route.params.id ?? '')) ??
    (route.params.id ? null : (adjustments.value[0] ?? null)),
)
const total = computed(() => adjustment.value?.amount ?? 0)
const progress = computed(() => {
  const all = Math.abs(total.value)
  return all === 0 ? 0 : Math.min(100, Math.round((Math.abs(adjustment.value?.processed ?? 0) / all) * 100))
})
const statusText = computed(() => {
  const row = adjustment.value
  if (!row) return ''
  if (row.status === 'Applied') return `ดำเนินการครบแล้ว นำไปใช้ในเดือน${monthLabel(row.appliedMonth)}`
  if (row.status === 'PartiallyApplied') return 'ดำเนินการบางส่วน ส่วนที่เหลือยกไปงวดถัดไป'
  if (row.status === 'Approved') return 'อนุมัติแล้ว รอนำไปใช้ในงวดถัดไป'
  if (row.status === 'ExternalReview') return 'ส่วนที่เหลือดำเนินการนอกระบบ'
  if (row.status === 'Cancelled') return 'ยกเลิกรายการ'
  return 'รออนุมัติ'
})
const thisMonth = computed(() => {
  const row = adjustment.value
  if (!row) return 0
  return row.applications
    .filter((application) => application.status !== 'Cancelled')
    .reduce((sum, application) => sum + application.amount, 0)
})
</script>
<template>
  <TherapistCompensationShell
    title="รายละเอียดรายการปรับปรุงค่าตอบแทน"
    subtitle="ข้อมูลอ่านอย่างเดียว"
    active-tab="payments"
    :tabs="false"
    :error="errorMessage"
    :loading="loading"
  >
    <section v-if="adjustment" class="therapist-adjustment-card">
      <h1>รายการปรับปรุงจาก{{ monthLabel(adjustment.originalMonth) }}</h1>
      <p class="therapist-adjustment-status">{{ statusText }}</p>
      <div class="therapist-adjustment-tiles">
        <article>
          <span>ยอดปรับปรุงทั้งหมด</span>
          <b :class="{ negative: total < 0 }">{{ signedBaht(total) }}</b>
        </article>
        <article>
          <span>ดำเนินการในเดือนนี้</span>
          <b :class="{ negative: thisMonth < 0 }">{{ signedBaht(thisMonth) }}</b>
        </article>
        <article>
          <span>ยอดยกไปงวดถัดไป</span>
          <b>{{ signedBaht(adjustment.status === 'Applied' ? 0 : adjustment.remaining * Math.sign(total)) }}</b>
        </article>
      </div>
      <p class="therapist-adjustment-status">ความคืบหน้า: ดำเนินการแล้ว {{ progress }}%</p>
      <div class="therapist-progress" role="progressbar" :aria-valuenow="progress" aria-valuemin="0" aria-valuemax="100">
        <i :style="{ width: progress + '%' }"></i>
      </div>
      <p>
        รายการอ้างอิง: {{ adjustment.reference ?? '—' }}
        <template v-if="adjustment.approvedBy"> | ผู้ยืนยัน: {{ adjustment.approvedBy }}</template>
      </p>
      <h2>เหตุผลการปรับปรุง</h2>
      <p>{{ adjustment.reason }}</p>

      <h2>ประวัติการดำเนินการรายเดือน</h2>
      <div class="therapist-adjustment-history" role="table">
        <div class="therapist-adjustment-history-head" role="row">
          <span>งวดที่ดำเนินการ</span><span>ผู้ยืนยัน</span><span>ยอดดำเนินการ</span><span>ยอดคงเหลือ</span>
        </div>
        <div
          v-for="application in adjustment.applications"
          :key="application.id"
          class="therapist-adjustment-history-head"
          role="row"
        >
          <span>{{ application.month ? monthLabel(application.month) : '—' }}</span>
          <span>{{ application.approvedBy ?? '—' }}</span>
          <span :class="{ negative: application.amount < 0 }">{{ signedBaht(application.amount) }}</span>
          <span>{{ signedBaht(adjustment.status === 'Applied' ? 0 : adjustment.remaining * Math.sign(total)) }}</span>
        </div>
        <p v-if="adjustment.applications.length === 0" class="therapist-line-empty">ยังไม่มีการดำเนินการ</p>
      </div>
      <p class="therapist-adjustment-note">
        ยอดและ Tier ของงวดที่ Finalized แล้วคงเดิม ระบบไม่หักเงินจริงโดยอัตโนมัติ
      </p>
      <RouterLink
        class="therapist-primary-action"
        :to="{ name: 'therapist-compensation', query: { month: adjustment.appliedMonth } }"
      >
        กลับค่าตอบแทนของฉัน
      </RouterLink>
    </section>
    <TherapistCompensationEmptyState v-else />
  </TherapistCompensationShell>
</template>
