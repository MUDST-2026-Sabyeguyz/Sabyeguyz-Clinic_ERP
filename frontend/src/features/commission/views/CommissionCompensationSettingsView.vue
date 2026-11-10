<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import CommissionTabs from './CommissionTabs.vue'
import { commissionApi, type TreatmentFeeRule } from '@/api/client'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'
import { clinicToday } from '@/utils/clinicDate'

const rules = ref<TreatmentFeeRule[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const message = ref('')
const drawerOpen = ref(false)
const branchCode = computed(() => selectedManagerBranch()?.code ?? '')
const nextMonth = () => {
  const today = clinicToday()
  const [year, month] = today.slice(0, 7).split('-').map(Number)
  const firstNext = new Date(Date.UTC(year!, month!, 1))
  return `${firstNext.getUTCFullYear()}-${String(firstNext.getUTCMonth() + 1).padStart(2, '0')}`
}
const form = reactive({
  name: '',
  feeType: 'Fixed' as 'Fixed' | 'Percentage',
  feeValue: '',
  effectiveMonth: nextMonth(),
})
const sortedRules = computed(() =>
  [...rules.value].sort((left, right) => right.effectiveFrom.localeCompare(left.effectiveFrom)),
)
const money = (value: number) =>
  `฿${Number(value).toLocaleString('th-TH', { maximumFractionDigits: 2 })}`

async function load() {
  loading.value = true
  error.value = ''
  if (!branchCode.value) {
    rules.value = []
    error.value = 'กรุณาเลือกสาขาและจัดการกฎค่ามือ'
    loading.value = false
    return
  }
  try {
    // โหลดกฎค่ามือตามสาขาที่เลือก
    rules.value = await commissionApi.treatmentFees() as TreatmentFeeRule[]
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'โหลดกฎค่ามือไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}

function openDrawer() {
  error.value = ''
  message.value = ''
  Object.assign(form, { name: '', feeType: 'Fixed', feeValue: '', effectiveMonth: nextMonth() })
  drawerOpen.value = true
}

async function saveRule() {
  error.value = ''
  const value = Number(form.feeValue)
  if (!branchCode.value) {
    error.value = 'กรุณาเลือกสาขาก่อนบันทึกกฎค่ามือ'
    return
  }
  if (!form.name.trim() || !/^\d{4}-(0[1-9]|1[0-2])$/.test(form.effectiveMonth)) {
    error.value = 'กรอกชื่อกฎและเดือนที่เริ่มใช้งาน'
    return
  }
  if (!Number.isFinite(value) || value < 0 || (form.feeType === 'Percentage' && value > 100)) {
    error.value = form.feeType === 'Percentage'
      ? 'เปอร์เซ็นต์ต้องอยู่ระหว่าง 0 ถึง 100'
      : 'จำนวนเงินต้องเป็นตัวเลขไม่ติดลบ'
    return
  }
  saving.value = true
  try {
    // สร้างกฎพร้อมวันเริ่มใช้; backend จัดการวันสิ้นสุดของกฎเดิม
    await commissionApi.createTreatmentFee({
      name: form.name.trim(),
      feeType: form.feeType,
      feeValue: value,
      effectiveFrom: `${form.effectiveMonth}-01`,
      branchCode: branchCode.value,
    })
    await load()
    drawerOpen.value = false
    message.value = 'บันทึกกฎค่ามือแล้ว ระบบจัดการวันสิ้นสุดของกฎเดิมให้อัตโนมัติ'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'บันทึกกฎค่ามือไม่สำเร็จ'
  } finally {
    saving.value = false
  }
}

onMounted(load)
watch(branchCode, () => void load())
</script>

<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main compensation-main">
      <header class="commission-title">
        <h1>ตั้งค่าค่ามือ</h1>
        <p>กฎค่ามือใช้ตามวันที่มีผล ระบบกำหนดวันสิ้นสุดของกฎเดิมเมื่อสร้างกฎใหม่</p>
      </header>
      <CommissionTabs active="compensation" />
      <section class="commission-card compensation-panel">
        <header class="rules-head">
          <h2>กฎค่ามือของสาขาที่เลือก</h2>
          <p>เปอร์เซ็นต์กรอกเป็นจำนวนเต็ม เช่น 10 หมายถึง 10% โดยกฎที่ถูกใช้แล้วจะไม่แก้ทับย้อนหลัง</p>
        </header>
        <p v-if="loading" role="status">กำลังโหลดข้อมูล…</p>
        <p v-else-if="error" class="drawer-error" role="alert">{{ error }}</p>
        <p v-if="message" role="status">{{ message }}</p>
        <p v-if="!loading && !error && !sortedRules.length">ยังไม่มีกฎค่ามือ</p>
        <section v-if="!loading && !error" class="rules" aria-label="รายการกฎค่ามือ">
          <article v-for="rule in sortedRules" :key="rule.id" class="commission-card">
            <div>
              <b>{{ rule.name?.trim() || `กฎค่ามือ #${rule.id}` }}</b>
              <small>มีผล {{ rule.effectiveFrom }} ถึง {{ rule.effectiveTo || 'ไม่กำหนดวันสิ้นสุด' }}</small>
              <small>{{ rule.used ? 'มีรายการใช้กฎนี้แล้ว กรุณาสร้างกฎใหม่เพื่อเปลี่ยนอัตรา' : 'ยังไม่มีรายการอ้างอิงกฎนี้' }}</small>
            </div>
            <strong>{{ rule.feeType === 'Percentage' ? `${rule.feeValue}% ของยอดที่เกี่ยวข้อง` : `${money(rule.feeValue)} / ครั้ง` }}</strong>
          </article>
        </section>
        <button class="commission-primary add-rule" type="button" :disabled="!branchCode || loading" @click="openDrawer">
          + เพิ่มกฎค่ามือ
        </button>
      </section>
    </main>
    <Teleport to="body">
      <div v-if="drawerOpen" class="rule-drawer-backdrop" @click.self="drawerOpen = false">
        <aside class="rule-drawer" role="dialog" aria-modal="true" aria-labelledby="fee-rule-title">
          <header>
            <div>
              <h2 id="fee-rule-title">เพิ่มกฎค่ามือ</h2>
            </div>
            <button class="drawer-close" type="button" aria-label="ปิด" @click="drawerOpen = false">×</button>
          </header>
          <div class="rule-form">
            <label>ชื่อกฎ<input v-model="form.name" maxlength="100" /></label>
            <label>วิธีคำนวณ
              <select v-model="form.feeType">
                <option value="Fixed">จำนวนเงินต่อครั้ง</option>
                <option value="Percentage">เปอร์เซ็นต์</option>
              </select>
            </label>
            <label>{{ form.feeType === 'Percentage' ? 'อัตรา (%)' : 'จำนวนเงิน (บาท / ครั้ง)' }}
              <input v-model="form.feeValue" type="number" min="0" step="0.01" :max="form.feeType === 'Percentage' ? 100 : undefined" />
            </label>
            <label>เดือนที่เริ่มใช้งาน<input v-model="form.effectiveMonth" type="month" :min="nextMonth()" /></label>
            <p>{{ selectedManagerBranch()?.name ?? branchCode }}</p>
          </div>
          <p v-if="error" class="drawer-error" role="alert">{{ error }}</p>
          <footer>
            <button class="commission-secondary" type="button" :disabled="saving" @click="drawerOpen = false">ยกเลิก</button>
            <button class="commission-primary" type="button" :disabled="saving" @click="saveRule">{{ saving ? 'กำลังบันทึก…' : 'บันทึกกฎ' }}</button>
          </footer>
        </aside>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.compensation-panel { padding: 28px; min-height: 520px; display: flex; flex-direction: column; gap: 20px; }
.rules-head h2 { margin: 0; font-size: 23px; }
.rules-head p, .rules small { color: #667a72; }
.rules { display: grid; gap: 16px; }
.rules article { padding: 18px; display: flex; justify-content: space-between; gap: 20px; }
.rules article div { display: grid; gap: 8px; }
.rules strong { color: #176c68; }
.add-rule { align-self: end; min-height: 48px; min-width: 240px; }
.rule-drawer-backdrop { position: fixed; inset: 0; z-index: 1000; display: grid; justify-items: end; background: #14251f66; }
.rule-drawer { width: min(520px, 100%); height: 100%; overflow: auto; box-sizing: border-box; padding: 28px; background: white; }
.rule-drawer header, .rule-drawer footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.rule-form { display: grid; gap: 18px; margin: 28px 0; }
.rule-form label { display: grid; gap: 6px; }
.rule-form input, .rule-form select { min-height: 42px; padding: 8px; }
.drawer-error { color: #a02f2f; }
@media (max-width: 700px) { .compensation-panel { min-height: 0; padding: 20px; } .rules article { flex-direction: column; } .add-rule { width: 100%; } }
</style>
