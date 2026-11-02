<script setup lang="ts">
// แสดงรายละเอียดคำขอปรับยอดและสถานะพิจารณา
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi, type ManagerAdjustment } from '@/api/client'

const route = useRoute()
const adjustment = ref<ManagerAdjustment | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const showInfo = ref(false)
const actionError = ref('')
const actionSaving = ref(false)
const confirmAction = ref<'approve' | 'cancel' | null>(null)
const applicationStatus = (value: string) =>
  ({
    Applied: 'ดำเนินการแล้ว',
    Cancelled: 'ยกเลิก',
    Draft: 'รอยืนยัน',
    Scheduled: 'กำหนดงวดแล้ว',
  })[value] || value
const adjustmentStep = (value: string) =>
  ({
    Pending: 'รออนุมัติ',
    Approved: 'อนุมัติ',
    PartiallyApplied: 'นำยอดไปใช้บางส่วน',
    Applied: 'นำยอดไปใช้ครบ',
    ExternalReview: 'ยอดของพนักงานลาออก',
    Cancelled: 'ยกเลิก',
  })[value] || 'ไม่ทราบขั้นตอน'
const signedMoney = (value: number) =>
  `${value < 0 ? '-' : value > 0 ? '+' : ''}฿${Math.abs(value).toLocaleString('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const source = computed(() => {
  const row = adjustment.value
  if (!row) return '—'
  if (row.saleTransactionId) return `SALE-${String(row.saleTransactionId).padStart(5, '0')}`
  if (row.singleVisitFeeId) return `FEE-${String(row.singleVisitFeeId).padStart(5, '0')}`
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
const status = computed(() => adjustmentStep(adjustment.value?.status || 'Pending'))
const remaining = computed(() => {
  const row = adjustment.value
  return row ? Math.sign(row.amount) * row.remaining : 0
})
const reserved = computed(
  () =>
    adjustment.value?.applications
      .filter((item) => item.status === 'Draft')
      .reduce((sum, item) => sum + Math.abs(item.amount), 0) ?? 0,
)
const available = computed(() => Math.max(0, (adjustment.value?.remaining ?? 0) - reserved.value))
const canSchedule = computed(() => {
  const row = adjustment.value
  return !!row && row.remaining > 0 && ['Approved', 'PartiallyApplied'].includes(row.status)
})
async function load() {
  errorMessage.value = ''
  loading.value = true
  try {
    adjustment.value = await commissionApi.adjustment(Number(route.params.id))
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : 'โหลดรายการปรับปรุงไม่สำเร็จ กรุณาลองอีกครั้ง'
  } finally {
    loading.value = false
  }
}
onMounted(load)
async function runAction(action: 'approve' | 'cancel') {
  const row = adjustment.value
  if (!row || actionSaving.value) return
  confirmAction.value = action
}
async function confirmDecision() {
  const row = adjustment.value
  const action = confirmAction.value
  if (!row || !action || actionSaving.value) return
  const verb = action === 'approve' ? 'อนุมัติ' : 'ยกเลิก'
  actionSaving.value = true
  actionError.value = ''
  try {
    adjustment.value =
      action === 'approve'
        ? await commissionApi.approveAdjustment(row.id)
        : await commissionApi.cancelAdjustment(row.id)
    confirmAction.value = null
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : `${verb}รายการไม่สำเร็จ`
  } finally {
    actionSaving.value = false
  }
}
</script>

<template>
  <div class="commission-manager-page adjustment-detail-page">
    <StaffHeader />
    <main class="commission-manager-main detail-main">
      <RouterLink class="back-link" to="/staff/commission/adjustments"
        >← กลับรายการปรับปรุง</RouterLink
      >
      <header class="detail-title">
        <div>
          <h1>รายละเอียดการปรับปรุงค่าตอบแทน</h1>
          <p v-if="adjustment">ADJ-{{ String(adjustment.id).padStart(5, '0') }}</p>
        </div>
        <span
          v-if="adjustment"
          class="detail-status"
          :class="{
            'is-complete': ['Applied', 'PartiallyApplied'].includes(adjustment.status),
            'is-cancelled': adjustment.status === 'Cancelled',
            'is-review': adjustment.status === 'ExternalReview',
          }"
          >{{ status }}</span
        >
      </header>
      <p v-if="route.query.created === '1' && adjustment" class="creation-notice" role="status">
        สร้างรายการแล้ว · รออนุมัติรายการก่อนกำหนดยอดที่จะนำไปใช้
      </p>
      <p v-if="loading" class="state-card">กำลังโหลดรายละเอียด...</p>
      <div v-else-if="errorMessage" class="state-card error" role="alert">
        {{ errorMessage }}
        <button type="button" @click="load">ลองอีกครั้ง</button>
      </div>
      <template v-else-if="adjustment">
        <p v-if="actionError" class="state-card error" role="alert">{{ actionError }}</p>
        <section v-else-if="adjustment.status === 'ExternalReview'" class="status-guidance">
          ยอดหักคงเหลือนี้แสดงไว้เพื่ออ้างอิง
        </section>
        <section
          v-else-if="['Approved', 'PartiallyApplied'].includes(adjustment.status)"
          class="status-guidance"
        >
          {{ adjustment.status === 'Approved' ? 'อนุมัติแล้ว' : 'นำยอดไปใช้บางส่วนแล้ว' }}
          ตรวจยอดคงเหลือและกำหนดงวดที่จะนำยอดไปใช้
        </section>
        <section class="detail-layout">
          <article class="detail-card info-card">
            <h2>ข้อมูลการปรับปรุง</h2>
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
                <dt>ผู้รับค่าตอบแทน</dt>
                <dd>{{ adjustment.staffName || '-' }}</dd>
              </div>
              <div>
                <dt>รายการอ้างอิง</dt>
                <dd>{{ source }}</dd>
              </div>
              <div class="wide">
                <dt>เหตุผล</dt>
                <dd>{{ adjustment.reason }}</dd>
              </div>
              <div v-if="adjustment.approvedAt">
                <dt>ผู้อนุมัติรายการ</dt>
                <dd>รหัสผู้ใช้ {{ adjustment.approvedBy ?? '—' }}</dd>
              </div>
              <div v-if="adjustment.approvedAt">
                <dt>เวลาอนุมัติรายการ</dt>
                <dd>{{ new Date(adjustment.approvedAt).toLocaleString('th-TH') }}</dd>
              </div>
            </dl>
            <h2 class="history-title">ประวัติการนำยอดไปใช้</h2>
            <p class="history-empty" v-if="adjustment.applications.length === 0">
              {{
                adjustment.status === 'Pending'
                  ? 'ยังไม่ได้นำยอดไปใช้'
                  : 'ยังไม่มีประวัติการนำยอดไปใช้'
              }}
            </p>
            <div v-else class="history-list">
              <div v-for="item in adjustment.applications" :key="item.id" class="application-row">
                <span
                  >{{ item.month ? monthLabel(item.month) : `รายการ #${item.id}`
                  }}<small v-if="item.approvedAt"
                    >อนุมัติให้นำไปใช้
                    {{ new Date(item.approvedAt).toLocaleString('th-TH') }}</small
                  ><small v-if="item.approvalReference"
                    >อ้างอิง {{ item.approvalReference }}</small
                  ></span
                >
                <strong>{{ signedMoney(item.amount) }}</strong>
                <span>{{
                  item.status === 'Draft' && item.approved
                    ? 'อนุมัติให้นำไปใช้แล้ว · รอจ่ายจริง'
                    : applicationStatus(item.status)
                }}</span>
              </div>
            </div>
          </article>
          <aside class="detail-card summary-card">
            <div class="summary-heading">
              <h2>สรุปยอด</h2>
              <button
                class="info-button"
                type="button"
                aria-label="ข้อมูลการนำยอดไปใช้"
                :aria-expanded="showInfo"
                aria-controls="adjustment-info"
                @click="showInfo = !showInfo"
              >
                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.7" />
                  <path
                    d="M12 11v6m0-10v1"
                    stroke="currentColor"
                    stroke-width="1.7"
                    stroke-linecap="round"
                  />
                </svg>
              </button>
            </div>
            <p v-if="showInfo" id="adjustment-info" class="summary-warning">
              ระบบไม่แก้ไขงวดเดิมโดยอัตโนมัติ ผู้จัดการกำหนดงวดที่จะนำไปใช้
            </p>
            <div class="summary-metrics">
              <div>
                <span>ยอดปรับปรุงทั้งหมด</span
                ><b :class="{ negative: adjustment.amount < 0 }">{{
                  signedMoney(adjustment.amount)
                }}</b>
              </div>
              <div>
                <span>นำไปใช้แล้ว</span
                ><b>{{ signedMoney(Math.sign(adjustment.amount) * adjustment.processed) }}</b>
              </div>
              <div v-if="reserved > 0">
                <span>กันไว้ในยอดร่าง</span
                ><b>{{ signedMoney(Math.sign(adjustment.amount) * reserved) }}</b>
              </div>
              <div v-if="reserved > 0">
                <span>ยังจัดสรรได้</span
                ><b>{{ signedMoney(Math.sign(adjustment.amount) * available) }}</b>
              </div>
            </div>
            <p v-if="adjustment.status === 'Cancelled'" class="completion" role="status">
              รายการนี้ยกเลิกแล้ว ไม่สามารถนำยอดไปใช้ในงวดถัดไป
            </p>
            <p v-else class="remaining">
              <span>ยอดคงเหลือ</span><b>{{ signedMoney(remaining) }}</b>
            </p>
            <p
              v-if="adjustment.status === 'Pending' && adjustment.remaining > 0"
              class="pending-balance-note"
            >
              รออนุมัติก่อน จึงจะนำยอดนี้ไปใช้ในงวดได้
            </p>
            <p
              v-if="adjustment.status !== 'Cancelled' && adjustment.remaining === 0"
              class="completion"
              role="status"
            >
              นำยอดไปใช้ครบแล้ว
            </p>
            <RouterLink
              v-else-if="canSchedule"
              class="commission-primary schedule-link"
              :to="{ name: 'commission-adjustment-schedule', params: { id: adjustment.id } }"
              >จัดการยอดร่างและงวดจ่าย</RouterLink
            >
            <section
              v-if="
                adjustment.status === 'Pending' ||
                (adjustment.status === 'Approved' && adjustment.processed === 0)
              "
              class="summary-actions"
            >
              <p v-if="adjustment.status === 'Pending'">
                ตรวจสอบผู้รับค่าตอบแทน เหตุผล และยอดก่อนอนุมัติ
              </p>
              <button
                v-if="adjustment.status === 'Pending'"
                type="button"
                class="commission-primary"
                :disabled="actionSaving"
                @click="runAction('approve')"
              >
                อนุมัติรายการ
              </button>
              <button
                type="button"
                class="cancel-adjustment summary-cancel"
                :disabled="actionSaving"
                @click="runAction('cancel')"
              >
                ยกเลิกรายการ
              </button>
            </section>
          </aside>
        </section>
      </template>
    </main>
    <div
      v-if="confirmAction && adjustment"
      class="decision-backdrop"
      @click.self="confirmAction = null"
    >
      <section
        ref="dialogRef"
        class="decision-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="decision-title"
      >
        <h2 id="decision-title">
          {{ confirmAction === 'approve' ? 'ยืนยันอนุมัติรายการ' : 'ยืนยันยกเลิกรายการ' }}
        </h2>
        <p>รายการ ADJ-{{ String(adjustment.id).padStart(5, '0') }} · {{ adjustment.staffName }}</p>
        <dl>
          <div>
            <dt>เหตุผล</dt>
            <dd>{{ adjustment.reason }}</dd>
          </div>
          <div>
            <dt>ยอดปรับปรุง</dt>
            <dd>{{ signedMoney(adjustment.amount) }}</dd>
          </div>
        </dl>
        <div class="decision-actions">
          <button
            type="button"
            class="cancel-adjustment"
            :disabled="actionSaving"
            @click="confirmAction = null"
          >
            ยกเลิก
          </button>
          <button
            type="button"
            :class="confirmAction === 'cancel' ? 'danger-confirm' : 'commission-primary'"
            :disabled="actionSaving"
            @click="confirmDecision"
          >
            {{ actionSaving ? 'กำลังบันทึก…' : 'ยืนยัน' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.detail-main {
  padding-top: 28px;
}
.back-link {
  display: inline-flex;
  margin-bottom: 18px;
  color: #13766f;
  font-weight: 600;
  text-decoration: none;
  transition:
    color 160ms ease,
    transform 160ms ease;
}
.back-link:hover {
  color: #0b524d;
  text-decoration: underline;
  transform: translateX(-3px);
}
.detail-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}
.detail-title h1 {
  margin: 0;
  font-size: clamp(1.75rem, 2.7vw, 2.25rem);
}
.detail-title p {
  margin: 7px 0 0;
  color: #63746c;
}
.creation-notice {
  margin: 0 0 20px;
  padding: 12px 16px;
  border-radius: 8px;
  background: #e4f3ef;
  color: #145c59;
  font-weight: 600;
}
.detail-status {
  flex: none;
  padding: 6px 12px;
  border-radius: 999px;
  background: #fff1c6;
  color: #765714;
  font-size: 0.82rem;
  font-weight: 700;
}
.detail-status.is-complete {
  background: #e2f3eb;
  color: #226c55;
}
.detail-status.is-cancelled {
  background: #fde5e5;
  color: #9d3b3b;
}
.detail-status.is-review {
  background: #e4edf8;
  color: #315f91;
}
.detail-layout {
  display: grid;
  grid-template-columns: 1.65fr 1fr;
  gap: 18px;
  align-items: start;
}
.summary-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.summary-heading h2 {
  margin: 0;
}
.info-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #246f67;
  cursor: pointer;
}
.info-button:hover {
  background: #e4f3ef;
}
.info-button svg {
  width: 21px;
  height: 21px;
}
.info-button:focus-visible,
.back-link:focus-visible {
  outline: 2px solid #13766f;
  outline-offset: 3px;
}
.application-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid #e6ece8;
}
.application-row small {
  display: block;
  color: #63746c;
  overflow-wrap: anywhere;
}
.completion {
  margin: 0;
  color: #246f67;
  font-weight: 600;
}
.remaining {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.remaining b {
  font-size: 1.5rem;
  font-variant-numeric: tabular-nums;
}
.pending-balance-note {
  margin: -6px 0 14px;
  color: #74510a;
  font-size: 0.82rem;
}
.status-guidance {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin: 0 0 18px;
  padding: 18px 20px;
  border: 1px solid #dce5e0;
  border-radius: 10px;
  background: #fff;
}
.status-guidance h2 {
  margin: 0;
  font-size: 1rem;
}
.status-guidance p {
  margin: 5px 0 0;
  color: #63746c;
}
.decision-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.decision-actions button,
.cancel-adjustment {
  min-height: 44px;
  padding: 0 16px;
  border: 1px solid #a9c7c1;
  border-radius: 8px;
  background: #fff;
  color: #145c59;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.decision-actions .commission-primary {
  border-color: #1e8b86;
  background: #1e8b86;
  color: #fff;
}
.decision-actions .commission-primary:hover:not(:disabled) {
  border-color: #176e69;
  background: #176e69;
}
.decision-actions .commission-primary:disabled {
  cursor: wait;
  opacity: 0.65;
}
.cancel-adjustment:hover {
  background: #f2f7f5;
}
.decision-backdrop {
  position: fixed;
  z-index: 1200;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgb(24 38 33 / 48%);
}
.decision-dialog {
  width: min(480px, 100%);
  padding: 24px;
  border: 1px solid #dce5e0;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 18px 55px rgb(20 47 39 / 20%);
}
.decision-dialog h2 {
  margin: 0;
  color: #24372f;
  font-size: 1.25rem;
}
.decision-dialog > p {
  margin: 8px 0 16px;
  color: #63746c;
}
.decision-dialog dl {
  margin: 0 0 20px;
  padding: 12px 14px;
  border-radius: 9px;
  background: #f5f7f5;
}
.decision-dialog dl div {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr);
  gap: 10px;
  padding: 8px 0;
}
.decision-dialog dt {
  color: #63746c;
  font-size: 0.85rem;
}
.decision-dialog dd {
  margin: 0;
  overflow-wrap: anywhere;
  font-weight: 600;
}
.decision-dialog .danger-confirm {
  min-height: 44px;
  padding: 0 18px;
  border: 0;
  border-radius: 8px;
  background: #a83c3c;
  color: white;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.decision-dialog :is(button, a):focus-visible {
  outline: 3px solid #13766f;
  outline-offset: 3px;
}
.info-card dd {
  overflow-wrap: anywhere;
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
.summary-actions {
  display: grid;
  gap: 10px;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid #e6ece8;
}
.summary-actions p {
  margin: 0 0 2px;
  color: #63746c;
  font-size: 0.84rem;
  line-height: 1.55;
}
.summary-card .summary-cancel {
  width: 100%;
  border-color: #e5b7b5;
  color: #9d3b3b;
}
.summary-card .summary-cancel:hover:not(:disabled) {
  border-color: #c77a76;
  background: #fff6f5;
}
.summary-card .summary-cancel:disabled,
.summary-card .commission-primary:disabled {
  cursor: wait;
  opacity: 0.65;
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
  .status-guidance {
    align-items: stretch;
    flex-direction: column;
  }
  .status-guidance .decision-actions {
    width: 100%;
  }
  .status-guidance .decision-actions button {
    flex: 1;
  }
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
  .detail-title {
    flex-direction: column;
    align-items: flex-start;
  }
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
