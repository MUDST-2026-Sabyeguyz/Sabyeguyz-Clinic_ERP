<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import CommissionTabs from './CommissionTabs.vue'
import {
  commissionApi,
  staffApi,
  type AdjustmentInput,
  type PayAdjustment,
  type Staff,
} from '@/api/client'
import { clinicToday } from '@/utils/clinicDate'

const adjustments = ref<PayAdjustment[]>([])
const staff = ref<Staff[]>([])
const loading = ref(false)
const errorMessage = ref('')
const notice = ref('')
const selectedId = ref<number | null>(null)
const selected = computed(
  () => adjustments.value.find((row) => row.id === selectedId.value) ?? null,
)

const drawerOpen = ref(false)
const drawerError = ref('')
const saving = ref(false)
type SourceKind = 'allocationId' | 'singleVisitFeeId' | 'courseId' | 'visitId' | 'saleTransactionId'
const sourceLabels: Record<SourceKind, string> = {
  allocationId: 'รายการจัดสรรค่าตอบแทน (allocation)',
  singleVisitFeeId: 'ค่ามือรายครั้ง',
  courseId: 'คอร์ส',
  visitId: 'Visit',
  saleTransactionId: 'รายการขาย',
}
const form = reactive({
  staffId: 0,
  direction: 'deduct' as 'add' | 'deduct',
  amount: 0,
  reason: '',
  sourceKind: 'allocationId' as SourceKind,
  sourceId: 0,
  originalMonth: '',
  appliedMonth: '',
})

const application = reactive({ month: '', amount: '' as number | '', reference: '' })

const baht = (value: number) =>
  `${value < 0 ? '−' : value > 0 ? '+' : ''}฿${Math.abs(value).toLocaleString('th-TH', { minimumFractionDigits: 2 })}`
const statusLabel: Record<PayAdjustment['status'], string> = {
  Pending: 'รออนุมัติ',
  Approved: 'อนุมัติแล้ว',
  PartiallyApplied: 'หักไปบางส่วน',
  Applied: 'ครบแล้ว',
  ExternalReview: 'ส่งตรวจนอกระบบ',
  Cancelled: 'ยกเลิก',
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    adjustments.value = await commissionApi.adjustments()
  } catch (caught) {
    errorMessage.value = caught instanceof Error ? caught.message : 'โหลดใบปรับยอดไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await load()
  try {
    staff.value = await staffApi.list()
  } catch {
    staff.value = []
  }
})

async function run(action: () => Promise<unknown>, done: string) {
  errorMessage.value = ''
  notice.value = ''
  try {
    await action()
    notice.value = done
    await load()
  } catch (caught) {
    errorMessage.value = caught instanceof Error ? caught.message : 'ดำเนินการไม่สำเร็จ'
  }
}

function openDrawer() {
  drawerError.value = ''
  Object.assign(form, {
    staffId: staff.value[0]?.id ?? 0,
    direction: 'deduct',
    amount: 0,
    reason: '',
    sourceKind: 'allocationId',
    sourceId: 0,
    originalMonth: '',
    appliedMonth: '',
  })
  drawerOpen.value = true
}

async function create() {
  drawerError.value = ''
  if (!form.staffId || !(form.amount > 0) || !form.reason.trim() || !(form.sourceId > 0)) {
    drawerError.value = 'กรุณาเลือกพนักงาน ระบุยอด เหตุผล และเลขที่ต้นทางที่อ้างอิง'
    return
  }
  const body: AdjustmentInput = {
    staffId: form.staffId,
    amount: form.direction === 'deduct' ? -form.amount : form.amount,
    reason: form.reason.trim(),
    [form.sourceKind]: form.sourceId,
  }
  if (form.originalMonth) body.originalMonth = form.originalMonth
  if (form.appliedMonth) body.appliedMonth = form.appliedMonth
  saving.value = true
  try {
    const created = await commissionApi.createAdjustment(body)
    drawerOpen.value = false
    selectedId.value = created.id
    notice.value = 'บันทึกใบปรับยอดแล้ว รออนุมัติ'
    await load()
  } catch (caught) {
    drawerError.value = caught instanceof Error ? caught.message : 'บันทึกใบปรับยอดไม่สำเร็จ'
  } finally {
    saving.value = false
  }
}

function select(row: PayAdjustment) {
  selectedId.value = row.id
}
// whichever way an adjustment gets selected, the form to reserve it starts from its first intended month
watch(
  () => selected.value?.id,
  () => {
    application.month = selected.value?.appliedMonth ?? ''
    application.amount = ''
    application.reference = ''
  },
)
const canApply = (row: PayAdjustment) => ['Approved', 'PartiallyApplied'].includes(row.status)
const canReview = (row: PayAdjustment) =>
  row.amount < 0 && canApply(row) && row.remaining > 0 && Boolean(row.staffId)

const approve = (row: PayAdjustment) =>
  run(() => commissionApi.approveAdjustment(row.id), 'อนุมัติใบปรับยอดแล้ว')
const cancel = (row: PayAdjustment) =>
  run(() => commissionApi.cancelAdjustment(row.id), 'ยกเลิกใบปรับยอดแล้ว')
const review = (row: PayAdjustment) =>
  run(() => commissionApi.externalReviewAdjustment(row.id), 'ส่งตรวจนอกระบบแล้ว')
const addApplication = (row: PayAdjustment) =>
  run(
    () =>
      commissionApi.createApplication(
        row.id,
        application.month,
        application.amount === '' ? undefined : Number(application.amount),
      ),
    `เพิ่มในงวด ${application.month} แล้ว (Draft)`,
  )
const approveApplication = (id: number, negative: boolean) =>
  run(
    () => commissionApi.approveApplication(id, negative ? application.reference : undefined),
    'อนุมัติการนำไปใช้แล้ว จะมีผลเมื่อยืนยันจ่ายงวดนั้น',
  )
const cancelApplication = (id: number) =>
  run(() => commissionApi.cancelApplication(id), 'ยกเลิกรายการนำไปใช้แล้ว')

const today = clinicToday()
</script>

<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main">
      <header class="commission-title">
        <h1>ใบปรับยอดค่าตอบแทน</h1>
        <p>
          แก้ไขเงินที่จ่ายไปแล้วในงวดถัดไป งวดที่จ่ายแล้วและอัตราที่ล็อกไม่เปลี่ยน
          การหักไม่เกินค่าตอบแทนที่ได้ในงวด ส่วนที่เหลือยกไปงวดถัดไป
        </p>
      </header>
      <CommissionTabs active="adjustments" />
      <section class="toolbar">
        <button class="commission-primary" @click="openDrawer">+ สร้างใบปรับยอด</button>
      </section>
      <p v-if="errorMessage" class="page-error" role="alert">{{ errorMessage }}</p>
      <p v-if="notice" class="notice" role="status">{{ notice }}</p>
      <section class="commission-card commission-table-wrap">
        <table class="commission-table">
          <thead>
            <tr>
              <th>#</th>
              <th>พนักงาน</th>
              <th>เดือนเดิม → เดือนที่ใช้</th>
              <th>ยอด</th>
              <th>ดำเนินการแล้ว / คงเหลือ</th>
              <th>สถานะ</th>
              <th>เหตุผล</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in adjustments" :key="row.id" :class="{ chosen: row.id === selectedId }">
              <td>{{ row.id }}</td>
              <td>{{ row.staffName }}</td>
              <td>{{ row.originalMonth }} → {{ row.appliedMonth }}</td>
              <td>
                <b :class="row.amount < 0 ? 'minus' : 'plus'">{{ baht(row.amount) }}</b>
              </td>
              <td>
                ฿{{ row.processed.toLocaleString('th-TH') }} / ฿{{
                  row.remaining.toLocaleString('th-TH')
                }}
              </td>
              <td>{{ statusLabel[row.status] }}</td>
              <td class="reason">{{ row.reason }}</td>
              <td><button class="commission-secondary" @click="select(row)">รายละเอียด</button></td>
            </tr>
            <tr v-if="!loading && adjustments.length === 0">
              <td colspan="8">ยังไม่มีใบปรับยอด</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section v-if="selected" class="commission-card detail" aria-label="รายละเอียดใบปรับยอด">
        <header>
          <h2>ใบปรับยอด #{{ selected.id }} · {{ selected.staffName }}</h2>
          <span>{{ statusLabel[selected.status] }}</span>
        </header>
        <p>
          {{ baht(selected.amount) }} · ต้นทาง:
          <template v-if="selected.allocationId">allocation #{{ selected.allocationId }}</template>
          <template v-if="selected.singleVisitFeeId">
            ค่ามือรายครั้ง #{{ selected.singleVisitFeeId }}</template
          >
          <template v-if="selected.courseId"> คอร์ส #{{ selected.courseId }}</template>
          <template v-if="selected.visitId"> Visit #{{ selected.visitId }}</template>
          <template v-if="selected.saleTransactionId">
            รายการขาย #{{ selected.saleTransactionId }}</template
          >
        </p>
        <div class="actions">
          <button
            v-if="selected.status === 'Pending'"
            class="commission-primary"
            @click="approve(selected)"
          >
            อนุมัติใบปรับยอด
          </button>
          <button
            v-if="selected.status === 'Pending' || selected.status === 'Approved'"
            class="commission-secondary"
            @click="cancel(selected)"
          >
            ยกเลิกใบปรับยอด
          </button>
          <button v-if="canReview(selected)" class="commission-secondary" @click="review(selected)">
            ส่งตรวจนอกระบบ (พนักงานลาออก)
          </button>
        </div>

        <h3>การนำไปใช้ในงวดจ่าย</h3>
        <table v-if="selected.applications.length" class="commission-table inner">
          <thead>
            <tr>
              <th>ยอด</th>
              <th>สถานะ</th>
              <th>อนุมัติหัก/จ่ายจริง</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="app in selected.applications" :key="app.id">
              <td>{{ baht(app.amount) }}</td>
              <td>{{ app.status }}</td>
              <td>
                {{ app.approved ? 'อนุมัติแล้ว' : 'ยังไม่อนุมัติ'
                }}<template v-if="app.approvalReference">
                  · อ้างอิง {{ app.approvalReference }}</template
                >
              </td>
              <td class="actions">
                <template v-if="app.status === 'Draft'">
                  <button
                    v-if="!app.approved"
                    class="commission-secondary"
                    @click="approveApplication(app.id, app.amount < 0)"
                  >
                    อนุมัติ</button
                  ><button class="commission-secondary" @click="cancelApplication(app.id)">
                    ยกเลิก
                  </button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="muted">ยังไม่มีการนำไปใช้</p>

        <div v-if="canApply(selected)" class="apply-form">
          <label
            >งวดที่นำไปใช้<input
              v-model="application.month"
              type="month"
              :min="selected.appliedMonth"
          /></label>
          <label
            >ยอดที่นำไปใช้ (บาท, ว่าง = ทั้งหมดที่เหลือ)<input
              v-model.number="application.amount"
              type="number"
              min="0.01"
              step="0.01"
          /></label>
          <label v-if="selected.amount < 0"
            >เลขอ้างอิงความยินยอม (จำเป็นเมื่ออนุมัติการหัก)<input v-model="application.reference"
          /></label>
          <button class="commission-primary" @click="addApplication(selected)">
            เพิ่มในงวดจ่าย
          </button>
        </div>
      </section>
    </main>
    <Teleport to="body"
      ><div v-if="drawerOpen" class="rule-drawer-backdrop" @click.self="drawerOpen = false">
        <aside class="rule-drawer" role="dialog" aria-modal="true">
          <header>
            <div>
              <h2>สร้างใบปรับยอด</h2>
              <p>
                ต้องอ้างอิงต้นทางของพนักงานคนเดียวกันอย่างน้อยหนึ่งรายการ และต้องอนุมัติก่อนนำไปใช้
              </p>
            </div>
            <button class="drawer-close" @click="drawerOpen = false">×</button>
          </header>
          <div class="rule-form">
            <label
              >พนักงาน<select v-model.number="form.staffId">
                <option v-for="person in staff" :key="person.id" :value="person.id">
                  {{ person.fullName }}
                </option>
              </select></label
            ><label
              >ประเภท<select v-model="form.direction">
                <option value="deduct">หักเงิน (−)</option>
                <option value="add">เพิ่มเงิน (+)</option>
              </select></label
            ><label
              >ยอด (บาท)<input v-model.number="form.amount" type="number" min="0.01" step="0.01"
            /></label>
            <label
              >เหตุผล<input
                v-model="form.reason"
                maxlength="255"
                placeholder="เช่น บันทึกผู้รักษาผิดคน"
            /></label>
            <label
              >ต้นทางที่อ้างอิง<select v-model="form.sourceKind">
                <option v-for="(label, key) in sourceLabels" :key="key" :value="key">
                  {{ label }}
                </option>
              </select></label
            ><label
              >เลขที่ต้นทาง<input v-model.number="form.sourceId" type="number" min="1"
            /></label>
            <label
              >เดือนที่เกิดข้อผิดพลาด (ว่าง = ตามต้นทาง)<input
                v-model="form.originalMonth"
                type="month"
                :max="today.slice(0, 7)"
            /></label>
            <label
              >เดือนที่นำไปใช้ (ว่าง = งวดถัดไปหลังเดือนที่จ่ายล่าสุด)<input
                v-model="form.appliedMonth"
                type="month"
            /></label>
          </div>
          <p v-if="drawerError" class="drawer-error" role="alert">{{ drawerError }}</p>
          <footer>
            <button class="commission-secondary" @click="drawerOpen = false">ยกเลิก</button
            ><button class="commission-primary" :disabled="saving" @click="create">
              {{ saving ? 'กำลังบันทึก...' : 'บันทึกใบปรับยอด' }}
            </button>
          </footer>
        </aside>
      </div></Teleport
    >
  </div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}
.minus {
  color: #b42318;
}
.plus {
  color: #16875b;
}
.reason {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.chosen td {
  background: #eef7f6;
}
.detail {
  margin-top: 26px;
  padding: 22px;
}
.detail header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.detail h2,
.detail h3 {
  margin: 0 0 8px;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 12px 0;
}
.inner {
  min-width: 0;
}
.apply-form {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: end;
  margin-top: 18px;
}
.apply-form label {
  display: grid;
  gap: 6px;
  color: #52635b;
}
.apply-form input {
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid #b7cecc;
  border-radius: 9px;
  font: inherit;
}
.muted {
  color: #667a72;
}
.page-error {
  padding: 11px;
  border-radius: 8px;
  background: #fff1ef;
  color: #b42318;
}
.notice {
  padding: 11px;
  border-radius: 8px;
  background: #e8f6ef;
  color: #16875b;
}
</style>
