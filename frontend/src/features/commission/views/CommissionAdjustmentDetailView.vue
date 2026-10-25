<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi, type ManagerAdjustment } from '@/api/client'
import CommissionTabs from './CommissionTabs.vue'

const route = useRoute()
const adjustment = ref<ManagerAdjustment | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const signedMoney = (value: number) =>
  `${value < 0 ? '-' : '+'}฿${Math.abs(value).toLocaleString('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const source = computed(() => {
  const row = adjustment.value
  if (!row) return '—'
  if (row.visitId) return `VIS-${String(row.visitId).padStart(4, '0')}`
  if (row.courseId) return `COURSE-${String(row.courseId).padStart(5, '0')}`
  if (row.allocationId) return `COM-${String(row.allocationId).padStart(5, '0')}`
  return `ADJ-${String(row.id).padStart(5, '0')}`
})
const monthLabel = (value: string) => {
  const [year, number] = value.split('-').map(Number)
  if (!year || !number) return value
  return new Intl.DateTimeFormat('th-TH', { month: 'long', year: 'numeric' }).format(
    new Date(year, number - 1, 1),
  )
}
const status = computed(() =>
  adjustment.value?.status === 'Applied' ? 'ดำเนินการแล้ว' : 'รอดำเนินการ',
)
const remaining = computed(() => {
  const row = adjustment.value
  return row ? Math.sign(row.amount) * row.remaining : 0
})
async function load() {
  try {
    adjustment.value = await commissionApi.adjustment(Number(route.params.id))
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'ไม่พบรายการ Adjustment'
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="commission-manager-page adjustment-detail-page">
    <StaffHeader />
    <main class="commission-manager-main detail-main">
      <header class="detail-title">
        <h1>รายการปรับปรุงค่าตอบแทน</h1>
        <p>ตรวจสอบและจัดการผลต่างค่าตอบแทนหลัง Finalized</p>
      </header>
      <CommissionTabs active="adjustments" />
      <RouterLink class="back-link" to="/staff/commission/adjustments"
        >← กลับรายการ Adjustment</RouterLink
      >
      <p v-if="loading" class="state-card">กำลังโหลดรายละเอียด...</p>
      <p v-else-if="errorMessage" class="state-card error">{{ errorMessage }}</p>
      <template v-else-if="adjustment">
        <header class="adjustment-detail-title">
          <h2>รายละเอียด Adjustment</h2>
          <p>หมายเลข Z · {{ source }} · {{ status }}</p>
        </header>
        <section class="detail-layout">
          <article class="detail-card info-card">
            <h2>ข้อมูลรายการที่เลือก</h2>
            <dl>
              <div>
                <dt>งวดต้นทาง</dt>
                <dd>{{ monthLabel(adjustment.originalMonth) }}</dd>
              </div>
              <div>
                <dt>งวดที่เสนอ</dt>
                <dd>{{ monthLabel(adjustment.appliedMonth) }}</dd>
              </div>
              <div>
                <dt>สร้างเมื่อ</dt>
                <dd>บันทึกจากระบบ</dd>
              </div>
              <div>
                <dt>ผู้สร้าง</dt>
                <dd>ผู้จัดการ (Manager)</dd>
              </div>
              <div class="wide">
                <dt>เหตุผล</dt>
                <dd>{{ adjustment.reason }}</dd>
              </div>
            </dl>
            <div class="round-state">
              <span>สถานะของต้นทาง</span><b>Paid / Finalized</b><span>สถานะนำไปใช้</span
              ><b>Open · {{ monthLabel(adjustment.appliedMonth) }}</b>
            </div>
            <h2 class="history-title">ประวัติการดำเนินการ</h2>
            <p class="history-empty" v-if="adjustment.applications.length === 0">
              ยังไม่มีประวัติการดำเนินการ
            </p>
            <div v-else class="history-list">
              <p v-for="item in adjustment.applications" :key="item.id">
                {{ signedMoney(item.amount) }} · {{ item.status }}
              </p>
            </div>
          </article>
          <aside class="detail-card summary-card">
            <h2>รายละเอียดรายการที่เลือก</h2>
            <p class="summary-warning">
              ⓘ ระบบไม่แก้ไขงวดเดิมโดยอัตโนมัติ<br />Manager กำหนดและยืนยันงวดแต่ละเดือน
            </p>
            <div class="summary-metrics">
              <div>
                <span>Adjustment ทั้งหมด</span
                ><b :class="{ negative: adjustment.amount < 0 }">{{
                  signedMoney(adjustment.amount)
                }}</b>
              </div>
              <div>
                <span>ดำเนินการแล้ว</span
                ><b>{{ signedMoney(Math.sign(adjustment.amount) * adjustment.processed) }}</b>
              </div>
            </div>
            <p class="remaining">
              <b>{{ signedMoney(remaining) }}</b> คงเหลือยอดดำเนินการ
            </p>
            <RouterLink
              class="commission-primary schedule-link"
              :to="{ name: 'commission-adjustment-select-schedule', params: { id: adjustment.id } }"
              >กำหนดงวดถัดไป</RouterLink
            >
          </aside>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.detail-main {
  padding-top: 28px;
}
.back-link {
  display: inline-flex;
  margin-bottom: 22px;
  color: #13766f;
  font-weight: 700;
  text-decoration: none;
}
.detail-title {
  margin-bottom: 0;
}
.detail-title h1 {
  margin: 0;
  font-size: clamp(1.75rem, 2.7vw, 2.25rem);
}
.detail-title p,
.adjustment-detail-title p {
  margin: 7px 0 0;
  color: #63746c;
}
.adjustment-detail-title {
  margin-bottom: 22px;
}
.adjustment-detail-title h2 {
  margin: 0;
  font-size: clamp(1.35rem, 2vw, 1.7rem);
}
.detail-layout {
  display: grid;
  grid-template-columns: 1.65fr 1fr;
  gap: 18px;
}
.detail-card {
  box-sizing: border-box;
  padding: 20px;
  border: 1px solid #dce5e0;
  border-radius: 9px;
  background: #fff;
}
.detail-card h2 {
  margin: 0 0 16px;
  font-size: 1rem;
}
.info-card dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 15px 24px;
  margin: 0;
}
.info-card dl div {
  border-bottom: 1px solid #e6ece8;
  padding-bottom: 10px;
}
.info-card dl .wide {
  grid-column: 1 / -1;
}
.info-card dt {
  color: #718078;
  font-size: 0.8rem;
}
.info-card dd {
  margin: 4px 0 0;
  color: #263a31;
}
.round-state {
  display: grid;
  grid-template-columns: auto 1fr auto 1fr;
  gap: 8px 10px;
  align-items: center;
  margin-top: 16px;
  padding: 14px;
  border-radius: 7px;
  background: #f6f1e9;
  color: #586960;
  font-size: 0.82rem;
}
.round-state b {
  padding: 3px 8px;
  border-radius: 999px;
  background: #dff1e8;
  color: #26765e;
  font-size: 0.75rem;
}
.history-title {
  margin-top: 22px !important;
}
.history-empty {
  margin: 0;
  padding: 22px;
  border: 1px dashed #dfe5e2;
  border-radius: 7px;
  background: #faf7f1;
  color: #76847d;
  text-align: center;
}
.history-list p {
  margin: 8px 0;
}
.summary-warning {
  margin: 0 0 16px;
  padding: 14px;
  border-radius: 7px;
  background: #fff0bf;
  color: #726023;
  font-size: 0.82rem;
}
.summary-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}
.summary-metrics div {
  padding: 14px 12px;
  border-radius: 7px;
  background: #f7f3ed;
}
.summary-metrics span {
  display: block;
  color: #6d7c74;
  font-size: 0.75rem;
}
.summary-metrics b {
  display: block;
  margin-top: 6px;
  font-size: 1.05rem;
}
.negative {
  color: #c24742;
}
.remaining {
  margin: 0 0 16px;
  padding: 14px 12px;
  border-radius: 7px;
  background: #e4f3ef;
  color: #246f67;
}
.summary-card .commission-primary {
  width: 100%;
  min-height: 48px;
}
.schedule-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
}
.summary-card .commission-primary {
  cursor: pointer;
}
@media (max-width: 800px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
  .round-state {
    grid-template-columns: auto 1fr;
  }
  .round-state span:nth-of-type(2) {
    grid-column: 1;
  }
  .round-state b:last-child {
    grid-column: 2;
  }
}
@media (max-width: 560px) {
  .info-card dl {
    grid-template-columns: 1fr;
  }
  .info-card dl .wide {
    grid-column: auto;
  }
  .summary-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
