<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi, type ManagerAdjustment } from '@/api/client'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'

const route = useRoute()
const row = ref<ManagerAdjustment | null>(null)
const busy = ref(false)
const error = ref('')
const month = ref('')
const amount = ref('')
const references = ref<Record<number, string>>({})
const drafts = computed(
  () => row.value?.applications.filter((item) => item.status === 'Draft') ?? [],
)
const reserved = computed(() => drafts.value.reduce((sum, item) => sum + Math.abs(item.amount), 0))
const available = computed(() => Math.max(0, (row.value?.remaining ?? 0) - reserved.value))
const money = (value: number) =>
  `฿${Math.abs(value).toLocaleString('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

async function load() {
  try {
    row.value = await commissionApi.adjustment(Number(route.params.id))
    month.value ||= row.value.appliedMonth
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'โหลดรายการไม่สำเร็จ'
  }
}
async function save(action: () => Promise<unknown>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await action()
    row.value = await commissionApi.adjustment(Number(route.params.id))
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'บันทึกไม่สำเร็จ'
  } finally {
    busy.value = false
  }
}
function createDraft() {
  if (!row.value) return
  const value = Number(amount.value)
  if (!/^\d{4}-\d{2}$/.test(month.value) || month.value < row.value.appliedMonth) {
    error.value = 'เลือกงวดที่ไม่ก่อนงวดที่เสนอ'
    return
  }
  if (
    !amount.value ||
    !Number.isFinite(value) ||
    value <= 0 ||
    Math.abs(value * 100 - Math.round(value * 100)) > 0.000001 ||
    value > available.value
  ) {
    error.value = `ระบุยอดมากกว่า 0 และไม่เกิน ${money(available.value)}`
    return
  }
  void save(async () => {
    // ตรวจงวดจ่ายของสาขาก่อนจัดสรร adjustment ลงในงวดนั้น
    const branchCode = selectedManagerBranch()?.code
    if (!branchCode) throw new Error('กรุณาเลือกสาขาก่อนจัดสรรยอดปรับ')
    const payments = await commissionApi.payments(month.value, branchCode)
    const payment = payments.find((item) => item.staffId === row.value!.staffId)
    if (payment && payment.status !== 'OPEN') {
      throw new Error('งวดนี้ปิดหรือจ่ายแล้ว กรุณาเลือกงวดที่ยังเปิดอยู่')
    }
    await commissionApi.createAdjustmentApplication(row.value!.id, month.value, value, branchCode)
    amount.value = ''
  })
}
function approve(id: number) {
  if (!row.value) return
  const reference = references.value[id]?.trim()
  if (row.value.amount < 0 && !reference) {
    error.value = 'ยอดหักต้องระบุเลขอ้างอิงหลักฐานการอนุมัติหรือความยินยอม'
    return
  }
  // รายการหักต้องมีเลขอ้างอิงหลักฐานก่อนอนุมัติ
  void save(() => commissionApi.approveAdjustmentApplication(id, reference))
}
onMounted(load)
</script>

<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main schedule-main">
      <RouterLink
        class="back-link"
        :to="{ name: 'commission-adjustment-detail', params: { id: route.params.id } }"
        >← กลับรายละเอียดรายการปรับยอด</RouterLink
      >
      <h1>กำหนดยอดที่จะนำไปใช้</h1>
      <p v-if="error" class="state-card error" role="alert">{{ error }}</p>
      <p v-if="!row && !error" class="state-card">กำลังโหลดข้อมูล...</p>
      <template v-if="row">
        <p class="intro">
          {{ row.staffName }} · ยอดปรับ {{ row.amount < 0 ? 'หัก' : 'เพิ่ม' }}
          {{ money(row.amount) }}
        </p>
        <div class="totals">
          <div>
            <span>ยอดคงเหลือ</span><strong>{{ money(row.remaining) }}</strong>
          </div>
          <div>
            <span>กันไว้ในยอดร่าง</span><strong>{{ money(reserved) }}</strong>
          </div>
          <div>
            <span>ยังจัดสรรได้</span><strong>{{ money(available) }}</strong>
          </div>
        </div>
        <p v-if="row.status === 'ExternalReview'" class="state-card" role="status">
          ยอดของพนักงานลาออกแสดงเพื่อให้ผู้จัดการติดตามและเคลียร์นอกระบบ
        </p>
        <p
          v-else-if="!['Approved', 'PartiallyApplied'].includes(row.status)"
          class="state-card"
          role="status"
        >
          รายการนี้ไม่สามารถสร้างยอดร่างเพิ่มได้
        </p>
        <p v-else-if="available === 0" class="state-card" role="status">
          ยอดคงเหลือถูกกันไว้ในยอดร่างครบแล้ว ตรวจสอบรายการด้านล่างก่อน
        </p>
        <section
          v-if="['Approved', 'PartiallyApplied'].includes(row.status) && available > 0"
          class="schedule-card"
        >
          <h2>สร้างยอดร่างสำหรับงวดจ่าย</h2>
          <p>ยอดร่างจะกันเงินไว้ก่อน ยังไม่ถือว่าเพิ่มหรือหักเงินจริง</p>
          <div class="form-grid">
            <label>งวดจ่าย<input v-model="month" type="month" :min="row.appliedMonth" /></label>
            <label
              >จำนวนเงิน (บาท)<input
                v-model="amount"
                type="number"
                min="0.01"
                step="0.01"
                :max="available"
            /></label>
          </div>
          <button class="commission-primary" type="button" :disabled="busy" @click="createDraft">
            กันยอดไว้ในงวดนี้
          </button>
        </section>
        <section class="schedule-card">
          <h2>ยอดที่กำหนดแล้ว</h2>
          <p v-if="row.applications.length === 0">ยังไม่มียอดที่กำหนดให้ใช้</p>
          <div v-for="item in row.applications" :key="item.id" class="application">
            <div class="application-heading">
              <strong
                >{{ item.month || 'งวดจ่าย' }} · {{ item.amount < 0 ? '-' : '+'
                }}{{ money(item.amount) }}</strong
              >
              <span>{{
                item.status === 'Applied'
                  ? 'นำไปใช้จริงแล้ว'
                  : item.status === 'Cancelled'
                    ? 'ยกเลิกยอดร่างแล้ว'
                    : item.approved
                      ? 'อนุมัติให้นำไปใช้แล้ว'
                      : 'รออนุมัติการนำไปใช้'
              }}</span>
            </div>
            <template v-if="item.status === 'Draft'">
              <label v-if="!item.approved && item.amount < 0"
                >เลขอ้างอิงหลักฐานการอนุมัติ/ความยินยอม<input
                  v-model="references[item.id]"
                  maxlength="255"
                  placeholder="เลขอ้างอิงจากหลักฐานจริง"
              /></label>
              <p v-if="item.approved && item.approvalReference">
                หลักฐาน: {{ item.approvalReference }}
              </p>
              <div class="application-actions">
                <button
                  v-if="!item.approved"
                  class="commission-primary"
                  type="button"
                  :disabled="busy"
                  @click="approve(item.id)"
                >
                  อนุมัติให้นำยอดไปใช้
                </button>
                <button
                  class="commission-secondary"
                  type="button"
                  :disabled="busy"
                  @click="save(() => commissionApi.cancelAdjustmentApplication(item.id))"
                >
                  ยกเลิกยอดร่าง
                </button>
              </div>
            </template>
          </div>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.schedule-main {
  padding-top: 28px;
}
.back-link {
  display: inline-flex;
  margin-bottom: 22px;
  color: #13766f;
  font-weight: 700;
  text-decoration: none;
}
h1 {
  margin: 0;
  font-size: clamp(1.6rem, 2.5vw, 2rem);
}
.intro {
  margin: 6px 0 22px;
  color: #63746c;
}
.totals {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 18px;
}
.totals div,
.schedule-card {
  padding: 20px;
  border: 1px solid #dce5e0;
  border-radius: 10px;
  background: #fff;
}
.totals span {
  display: block;
  color: #63746c;
  font-size: 0.84rem;
}
.totals strong {
  display: block;
  margin-top: 8px;
  font-size: 1.3rem;
  font-variant-numeric: tabular-nums;
}
.schedule-card {
  margin-top: 16px;
}
.schedule-card h2 {
  margin: 0 0 6px;
  font-size: 1.1rem;
}
.schedule-card > p {
  margin: 0 0 16px;
  color: #63746c;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 18px;
}
label {
  display: grid;
  gap: 7px;
  font-weight: 600;
}
input {
  box-sizing: border-box;
  width: 100%;
  min-height: 44px;
  padding: 8px 12px;
  border: 1px solid #b7cecc;
  border-radius: 8px;
  color: #263a31;
  font: inherit;
}
.application {
  padding: 18px 0;
  border-top: 1px solid #e6ece8;
}
.application-heading {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.application-heading span {
  color: #376c63;
}
.application-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}
.application-actions button {
  min-height: 44px;
}
.schedule-main button:focus-visible,
.schedule-main input:focus-visible,
.back-link:focus-visible {
  outline: 2px solid #13766f;
  outline-offset: 2px;
}
@media (max-width: 680px) {
  .totals,
  .form-grid {
    grid-template-columns: 1fr;
  }
  .application-actions {
    flex-direction: column;
  }
}
</style>
