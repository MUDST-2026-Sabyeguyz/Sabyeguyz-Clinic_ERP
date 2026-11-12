<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import CommissionTabs from './CommissionTabs.vue'
import { commissionApi } from '@/api/client'
type TreatmentRow = Awaited<ReturnType<typeof commissionApi.treatmentReport>>[number]
const route = useRoute(),
  router = useRouter()
const staffId = computed(() => {
  const n = Number(String(route.params.staffId ?? '').replace(/^PT-0*/i, ''))
  return Number.isFinite(n) ? n : 0
})
const month = computed(() =>
  String(route.query.month || new Date().toISOString().slice(0, 7)).slice(0, 7),
)
const from = computed(() => `${month.value}-01`)
const to = computed(() => {
  const [y, m] = month.value.split('-').map(Number)
  return `${month.value}-${String(new Date(y!, m!, 0).getDate()).padStart(2, '0')}`
})
const loading = ref(true),
  errorMessage = ref(''),
  name = ref(''),
  branchName = ref('')
const base = ref(0),
  rate = ref(0),
  commission = ref(0),
  saleCount = ref(0)
const treatments = ref<TreatmentRow[]>([]),
  closingId = ref<number | null>(null),
  approved = ref(false)
const confirmOpen = ref(false),
  validationMessage = ref(''),
  saving = ref(false)
const treatmentTotal = computed(() =>
  treatments.value.reduce((sum, item) => sum + Number(item.treatmentFee || 0), 0),
)
const netTotal = computed(() => commission.value + treatmentTotal.value)
const money = (value: number) =>
  `฿${value.toLocaleString('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const thaiDate = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'short', year: 'numeric' }).format(
    new Date(`${value}T00:00:00`),
  )
async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [report, treatmentReport, closings] = await Promise.all([
      commissionApi.report(from.value, to.value),
      commissionApi.treatmentReport(from.value, to.value),
      commissionApi.closings(month.value),
    ])
    const person = report.find((item) => item.staffId === staffId.value)
    if (!person) {
      errorMessage.value = 'ไม่พบข้อมูลค่าตอบแทนของบุคลากรรายนี้'
      return
    }
    name.value = person.staffName
    branchName.value = person.branchName
    base.value = Number(person.base)
    rate.value = Number(person.rate) * 100
    commission.value = Number(person.commission)
    saleCount.value = Number(person.saleCount)
    treatments.value = treatmentReport.filter((item) => item.treatingStaffId === staffId.value)
    const closing = closings.find((item) => item.employeeId === staffId.value)
    closingId.value = closing?.id ?? null
    approved.value = closing?.status === 'CLOSED' || closing?.status === 'FINALIZED'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลค่าตอบแทนไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
function openConfirmation() {
  validationMessage.value = ''
  confirmOpen.value = true
}
async function confirm() {
  if (closingId.value === null) {
    validationMessage.value = 'ยังไม่มีรอบคอมมิชชันให้อนุมัติ'
    return
  }
  saving.value = true
  try {
    await commissionApi.closeClosing(closingId.value)
    approved.value = true
    confirmOpen.value = false
    await router.push('/staff/commission')
  } catch (error) {
    validationMessage.value = error instanceof Error ? error.message : 'อนุมัติยอดไม่สำเร็จ'
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main">
      <header class="detail-head">
        <div>
          <button @click="router.push('/staff/commission')">← กลับหน้าภาพรวม</button>
          <h1>รายละเอียดค่าตอบแทนรายบุคคล</h1>
          <p v-if="name">{{ name }} · รหัส {{ staffId }} · {{ branchName }}</p>
        </div>
        <span
          v-if="!loading && !errorMessage"
          class="commission-status"
          :class="{ ready: approved }"
          >{{ approved ? 'อนุมัติแล้ว' : 'รอตรวจสอบ' }}</span
        >
      </header>
      <CommissionTabs active="overview" />
      <p v-if="loading" class="state-card">กำลังโหลดข้อมูลจริง…</p>
      <p v-else-if="errorMessage" class="state-card error">{{ errorMessage }}</p>
      <template v-else
        ><section class="commission-metrics">
          <article>
            <span>ฐานคำนวณ</span><b>{{ money(base) }}</b
            ><small>{{ saleCount }} รายการขาย</small>
          </article>
          <article class="highlight">
            <span>ค่าคอมมิชชัน</span><b>{{ money(commission) }}</b
            ><small>อัตรา {{ rate.toLocaleString('th-TH') }}%</small>
          </article>
          <article>
            <span>ค่ารักษาแทน</span><b>{{ money(treatmentTotal) }}</b
            ><small>{{ treatments.length }} รายการ</small>
          </article>
          <article class="warning">
            <span>ยอดสุทธิ</span><b>{{ money(netTotal) }}</b
            ><small>{{ approved ? 'อนุมัติแล้ว' : 'รออนุมัติ' }}</small>
          </article>
        </section>
        <section class="detail-columns">
          <article class="commission-card tier-detail">
            <h2>ค่าคอมมิชชัน</h2>
            <div class="tier-box">
              <b
                >อัตราตามรอบ <em>{{ rate.toLocaleString('th-TH') }}%</em></b
              ><small>ฐานคำนวณ {{ money(base) }}</small>
            </div>
            <dl>
              <div>
                <dt>จำนวนรายการขาย</dt>
                <dd>{{ saleCount }}</dd>
              </div>
              <div class="total">
                <dt>ค่าคอมมิชชัน</dt>
                <dd>{{ money(commission) }}</dd>
              </div>
            </dl>
          </article>
          <article class="commission-card treatment-detail">
            <h2>ค่ารักษาแทน</h2>
            <p>รายการจากรอบคำนวณจริง</p>
            <p v-if="!treatments.length" class="empty-treatment">ไม่มีรายการรักษาแทนในรอบนี้</p>
            <div v-for="item in treatments" :key="item.id" class="treatment-row">
              <span>{{ thaiDate(item.visitDate) }} · {{ item.courseCode }}</span
              ><b>{{ money(Number(item.treatmentFee)) }}</b>
            </div>
            <div class="treatment-total">
              <b>รวมค่ารักษาแทน</b><b>{{ money(treatmentTotal) }}</b>
            </div>
            <footer v-if="!approved">
              <button
                class="commission-primary"
                :disabled="closingId === null"
                @click="openConfirmation"
              >
                อนุมัติยอด
              </button>
            </footer>
            <p v-else class="approved-note">✓ อนุมัติยอดเรียบร้อยแล้ว</p>
          </article>
        </section></template
      >
    </main>
    <Teleport to="body"
      ><div v-if="confirmOpen" class="action-backdrop" @click.self="confirmOpen = false">
        <section class="action-modal" role="dialog" aria-modal="true">
          <h2>ยืนยันอนุมัติยอดหรือไม่?</h2>
          <p>ยอดสุทธิ {{ money(netTotal) }} จะถูกเปลี่ยนเป็นสถานะอนุมัติแล้ว</p>
          <p v-if="validationMessage" class="action-error">{{ validationMessage }}</p>
          <div>
            <button class="commission-secondary" :disabled="saving" @click="confirmOpen = false">
              ยกเลิก</button
            ><button class="commission-primary" :disabled="saving" @click="confirm">
              {{ saving ? 'กำลังบันทึก...' : 'ยืนยันอนุมัติ' }}
            </button>
          </div>
        </section>
      </div></Teleport
    >
  </div>
</template>
<style scoped>
@import '../styles/commission-manager.css';
.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: start;
}
.detail-head button {
  padding: 0;
  border: 0;
  background: none;
  color: #667a72;
  cursor: pointer;
}
.detail-head h1 {
  margin: 14px 0 0;
  font-size: 2.4rem;
}
.detail-head p {
  margin: 5px 0;
  color: #667a72;
}
.detail-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.detail-columns article {
  padding: 24px;
}
.detail-columns h2 {
  margin: 0 0 18px;
}
.tier-box {
  display: grid;
  gap: 12px;
  padding: 18px;
  border: 1px solid #d9e1dc;
  border-radius: 10px;
}
.tier-box b {
  display: flex;
  justify-content: space-between;
}
.tier-box em {
  color: #1e8b86;
  font-style: normal;
}
.tier-detail dl {
  display: grid;
  gap: 4px;
}
.tier-detail dl div {
  display: flex;
  justify-content: space-between;
  padding: 12px;
}
.tier-detail .total {
  border-radius: 8px;
  background: #e7f4ef;
  font-weight: 700;
}
.treatment-detail > p {
  color: #667a72;
}
.treatment-row,
.treatment-total {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 15px 8px;
}
.treatment-total {
  margin-top: 8px;
  border-radius: 8px;
  background: #e7f4ef;
}
.treatment-detail footer {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}
.treatment-detail footer button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.approved-note {
  margin-top: 16px !important;
  padding: 12px;
  border-radius: 8px;
  background: #e7f4ef;
  color: #1e807c !important;
  font-weight: 700;
}
.state-card {
  padding: 24px;
  border-radius: 12px;
  background: #fff;
}
.state-card.error {
  background: #fff1ef;
  color: #b42318;
}
.empty-treatment {
  padding: 16px;
  border-radius: 8px;
  background: #f4f6f5;
}
.action-backdrop {
  position: fixed;
  z-index: 9999;
  inset: 0;
  display: grid;
  width: 100vw;
  height: 100dvh;
  box-sizing: border-box;
  place-items: center;
  padding: 20px;
  background: #26342d80;
  overflow-y: auto;
}
.action-modal {
  width: min(430px, 100%);
  box-sizing: border-box;
  padding: 28px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 20px 50px #0003;
}
.action-modal h2 {
  margin: 0;
  color: #25372e;
  text-align: center;
}
.action-modal > p {
  color: #667a72;
  text-align: center;
}
.action-modal > div {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 20px;
}
.action-error {
  padding: 9px;
  border-radius: 8px;
  background: #fff1ef;
  color: #b42318 !important;
}
.action-modal button {
  justify-content: center;
}
.action-modal .commission-primary {
  border: 0;
}
@media (max-width: 800px) {
  .detail-columns {
    grid-template-columns: 1fr;
  }
  .detail-head {
    flex-direction: column;
  }
  .detail-head h1 {
    font-size: 2rem;
  }
}
@media (max-width: 480px) {
  .treatment-detail footer,
  .action-modal > div {
    grid-template-columns: 1fr;
    flex-direction: column;
  }
  .treatment-detail footer button {
    width: 100%;
  }
}
.detail-head button {
  display: inline-flex;
  align-items: center;
  min-height: 42px;
  padding: 9px 16px;
  border: 1px solid #1e8b86;
  border-radius: 999px;
  background: #e8f5f2;
  color: #176f6b;
  font-weight: 700;
  box-shadow: 0 2px 8px #1e8b8617;
  transition:
    background 0.2s,
    color 0.2s,
    transform 0.2s;
}
.detail-head button:hover {
  background: #1e8b86;
  color: #fff;
  transform: translateX(-2px);
}
</style>
