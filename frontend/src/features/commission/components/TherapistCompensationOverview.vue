<script setup lang="ts">
import { computed } from 'vue'
import type { StaffPayment, TherapistLine } from '@/api/client'
import { baht, signedBaht } from '../lib/therapistPay'
import TherapistCompensationLineList from './TherapistCompensationLineList.vue'

const props = defineProps<{
  payment: StaffPayment | null
  lines: TherapistLine[]
  month: string
  pendingSessions: number
}>()

const commission = computed(() => props.payment?.courseCommission ?? null)
const treatment = computed(
  () => (props.payment?.singleVisitFee ?? 0) + (props.payment?.substituteFee ?? 0),
)
const adjustment = computed(() => props.payment?.adjustment ?? 0)
const net = computed(() => props.payment?.net ?? null)
</script>

<template>
  <section class="therapist-overview-grid">
    <article class="therapist-summary-card" aria-label="สรุปค่าตอบแทน">
      <p>สรุปค่าตอบแทน</p>
      <dl>
        <div>
          <dt>Course Commission</dt>
          <dd>
            <template v-if="commission !== null">{{ baht(commission) }}</template>
            <template v-else>รอคำนวณ</template>
          </dd>
        </div>
        <div>
          <dt>ค่ามือ</dt>
          <dd>{{ baht(treatment) }}</dd>
        </div>
        <div>
          <dt>Adjustment</dt>
          <dd :class="{ negative: adjustment < 0 }">
            {{ signedBaht(adjustment) }}
          </dd>
        </div>
        <div class="net">
          <dt>Net Payout</dt>
          <dd>
            <template v-if="net !== null">{{ baht(net) }}</template>
            <template v-else>รอคำนวณ</template>
          </dd>
        </div>
      </dl>
      <small v-if="pendingSessions > 0" class="therapist-pending-note">
        {{ pendingSessions }} ครั้งที่เดือนขายคอร์สยังไม่ปิดยอด จึงยังไม่นับเข้าค่าคอมมิชชัน
      </small>
    </article>
    <article class="therapist-principle-card">
      <h2>หลักการคำนวณ</h2>
      <div>
        <b>Course Commission</b>
        <p>ได้รับเมื่อคุณเป็น Owner PT และเป็นผู้รักษา Visit นั้น โดยใช้อัตราที่ล็อกในเดือนขาย</p>
      </div>
      <div>
        <b>ค่ามือ</b>
        <p>ได้รับเมื่อคุณเป็นผู้รักษาจริงในบริการรายครั้งหรือรักษาแทนคอร์ส</p>
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
