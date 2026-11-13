<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import CommissionTabs from './CommissionTabs.vue'
import { commissionApi, type StaffPayment } from '@/api/client'
import { clinicToday } from '@/utils/clinicDate'

const month = ref(clinicToday().slice(0, 7))
const rows = ref<StaffPayment[]>([])
const loading = ref(false)
const busyId = ref<number | null>(null)
const errorMessage = ref('')
const notice = ref('')
const confirmPay = ref<StaffPayment | null>(null)
const payDate = ref(clinicToday())
const payReference = ref('')
const today = clinicToday()
const thaiDay = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { dateStyle: 'medium' }).format(new Date(`${value}T12:00:00`))
function openPay(row: StaffPayment) {
  payDate.value = clinicToday()
  payReference.value = ''
  confirmPay.value = row
}

const monthOptions = Array.from({ length: 12 }, (_, index) => {
  const [year, number] = clinicToday().slice(0, 7).split('-').map(Number)
  const date = new Date(year!, number! - 1 - index + 1, 1) // one month ahead: adjustments land there
  const value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
  return {
    value,
    label: new Intl.DateTimeFormat('th-TH', { month: 'long', year: 'numeric' }).format(date),
  }
})

const baht = (value: number | null) =>
  value === null ? null : `฿${Number(value).toLocaleString('th-TH', { minimumFractionDigits: 2 })}`
const statusLabel = (status: StaffPayment['status']) =>
  status === 'PAID' ? 'จ่ายแล้ว' : status === 'CLOSED' ? 'ปิดงวด รอจ่าย' : 'เปิดอยู่'
const total = computed(() => ({
  net: rows.value.reduce((sum, row) => sum + (row.net ?? 0), 0),
  waiting: rows.value.filter((row) => row.net === null).length,
}))

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    rows.value = await commissionApi.payments(month.value)
  } catch (caught) {
    errorMessage.value = caught instanceof Error ? caught.message : 'โหลดงวดจ่ายไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(month, load)

async function run(row: StaffPayment | null, action: () => Promise<unknown>, done: string) {
  errorMessage.value = ''
  notice.value = ''
  busyId.value = row?.id ?? 0
  try {
    await action()
    notice.value = done
    await load()
  } catch (caught) {
    errorMessage.value = caught instanceof Error ? caught.message : 'ดำเนินการไม่สำเร็จ'
  } finally {
    busyId.value = null
  }
}
const prepare = () =>
  run(
    null,
    async () => {
      rows.value = await commissionApi.preparePayments(month.value)
    },
    'เตรียมและคำนวณงวดจ่ายแล้ว',
  )
const closeRow = (row: StaffPayment) =>
  run(row, () => commissionApi.closePayment(row.id), `ปิดงวดของ ${row.staffName} แล้ว`)
const reopenRow = (row: StaffPayment) =>
  run(row, () => commissionApi.reopenPayment(row.id), `เปิดงวดของ ${row.staffName} อีกครั้งแล้ว`)
async function pay() {
  const row = confirmPay.value
  confirmPay.value = null
  if (row)
    await run(
      row,
      () =>
        commissionApi.payPayment(row.id, {
          paidDate: payDate.value || undefined,
          reference: payReference.value.trim() || undefined,
        }),
      `ยืนยันจ่าย ${row.staffName} แล้ว`,
    )
}
</script>

<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main">
      <header class="commission-title">
        <h1>งวดจ่ายค่าตอบแทน</h1>
        <p>
          สรุปค่าคอมมิชชันและค่ามือของแต่ละคนตามเดือนที่รักษาจริง ปิดงวดแล้วจึงยืนยันจ่าย
          เมื่อจ่ายแล้วแก้ไม่ได้ ใช้ใบปรับยอดในงวดถัดไป
        </p>
      </header>
      <CommissionTabs active="payments" />
      <section class="toolbar">
        <label
          >เดือนที่รักษา
          <select v-model="month" aria-label="เดือนที่รักษา">
            <option v-for="option in monthOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select></label
        ><button class="commission-primary" :disabled="busyId !== null" @click="prepare">
          เตรียม/คำนวณงวดจ่าย
        </button>
        <span class="summary"
          >ยอดสุทธิรวม <b>{{ baht(total.net) }}</b
          ><template v-if="total.waiting"> · รอคำนวณ {{ total.waiting }} คน</template></span
        >
      </section>
      <p v-if="errorMessage" class="page-error" role="alert">{{ errorMessage }}</p>
      <p v-if="notice" class="notice" role="status">{{ notice }}</p>
      <section class="commission-card commission-table-wrap">
        <table class="commission-table">
          <thead>
            <tr>
              <th>พนักงาน</th>
              <th>สถานะ</th>
              <th>คอมมิชชันคอร์ส</th>
              <th>ค่ามือรายครั้ง</th>
              <th>ค่ามือรักษาแทน</th>
              <th>ปรับยอด</th>
              <th>สุทธิ</th>
              <th>การดำเนินการ</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td>{{ row.staffName }}</td>
              <td>{{ statusLabel(row.status) }}</td>
              <td>
                <template v-if="row.courseCommission !== null">{{
                  baht(row.courseCommission)
                }}</template>
                <small v-else class="waiting"
                  >รอคำนวณ ({{ row.pendingSessions }} ครั้งที่เดือนขายยังไม่ปิดยอด)</small
                >
              </td>
              <td>{{ baht(row.singleVisitFee) }}</td>
              <td>{{ baht(row.substituteFee) }}</td>
              <td>{{ baht(row.adjustment) }}</td>
              <td>
                <b v-if="row.net !== null">{{ baht(row.net) }}</b
                ><small v-else class="waiting">รอคำนวณ</small>
              </td>
              <td class="actions">
                <template v-if="row.status === 'OPEN'"
                  ><button
                    class="commission-secondary"
                    :disabled="busyId !== null || row.net === null"
                    @click="closeRow(row)"
                  >
                    ปิดงวด
                  </button></template
                >
                <template v-else-if="row.status === 'CLOSED'"
                  ><button
                    class="commission-secondary"
                    :disabled="busyId !== null"
                    @click="reopenRow(row)"
                  >
                    เปิดงวดอีกครั้ง</button
                  ><button
                    class="commission-primary"
                    :disabled="busyId !== null"
                    @click="openPay(row)"
                  >
                    ยืนยันจ่าย
                  </button></template
                >
                <small v-else
                  >จ่ายเมื่อ
                  {{
                    row.paidDate
                      ? thaiDay(row.paidDate)
                      : row.paidAt
                        ? new Intl.DateTimeFormat('th-TH', {
                            dateStyle: 'medium',
                            timeStyle: 'short',
                          }).format(new Date(row.paidAt))
                        : ''
                  }}<template v-if="row.paymentReference"> · อ้างอิง {{ row.paymentReference }}</template></small
                >
              </td>
            </tr>
            <tr v-if="!loading && rows.length === 0">
              <td colspan="8">
                ยังไม่มีงวดจ่ายของเดือนนี้ กด “เตรียม/คำนวณงวดจ่าย” เพื่อสร้างจากรายการรักษา
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </main>
    <div
      v-if="confirmPay"
      class="modal-backdrop"
      role="presentation"
      @click.self="confirmPay = null"
    >
      <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="pay-title">
        <h2 id="pay-title">ยืนยันจ่ายให้ {{ confirmPay.staffName }}?</h2>
        <p>
          ยอดสุทธิ {{ baht(confirmPay.net) }} · การปิดยอดของเดือนขายที่คอมมิชชันนี้อิงอยู่จะถูก
          Finalize พร้อมกัน และไม่สามารถ Reopen ได้อีก
        </p>
        <label class="pay-field"
          >วันที่จ่าย
          <input v-model="payDate" type="date" :max="today" aria-label="วันที่จ่าย" />
        </label>
        <label class="pay-field"
          >เลขที่อ้างอิง
          <input
            v-model="payReference"
            maxlength="100"
            aria-label="เลขที่อ้างอิง"
            placeholder="เลขที่โอนหรือเลขที่สลิป (ไม่บังคับ)"
          />
        </label>
        <div>
          <button class="cancel" type="button" @click="confirmPay = null">ยกเลิก</button
          ><button class="confirm" type="button" :disabled="!payDate" @click="pay">
            ยืนยันจ่าย
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
@import '../../staff/styles/staff.css';
@import '../styles/commission-manager.css';
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: end;
  margin-bottom: 18px;
}
.toolbar label {
  display: grid;
  gap: 6px;
  color: #52635b;
}
.toolbar select {
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid #b7cecc;
  border-radius: 9px;
  background: #fff;
  font: inherit;
}
.summary {
  margin-left: auto;
  color: #52635b;
}
.waiting {
  color: #a15c07;
}
.actions {
  display: flex;
  gap: 8px;
  align-items: center;
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
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: grid;
  place-items: center;
  background: #26342d80;
}
.confirm-modal {
  width: min(460px, calc(100% - 32px));
  padding: 26px;
  border-radius: 12px;
  background: #fff;
}
.confirm-modal h2 {
  margin: 0 0 10px;
}
.confirm-modal p {
  color: #52635b;
}
.confirm-modal > div {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}
.confirm-modal .cancel,
.confirm-modal .confirm {
  min-height: 42px;
  padding: 0 20px;
  border-radius: 8px;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.confirm-modal .cancel {
  border: 1px solid #d9e1dc;
  background: #fff;
}
.confirm-modal .confirm {
  border: 1px solid #1e8b86;
  background: #1e8b86;
  color: #fff;
}
</style>
