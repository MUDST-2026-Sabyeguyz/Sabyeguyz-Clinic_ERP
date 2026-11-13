<script setup lang="ts">
// สร้างคำขอเพิ่มหรือลดยอดค่าตอบแทนพร้อมเหตุผล
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import MonthFilterPicker from '@/features/staff/components/MonthFilterPicker.vue'
import { commissionApi, staffApi, type Staff } from '@/api/client'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'

const router = useRouter()
const staff = ref<Staff[]>([])
const staffId = ref('')
const referenceType = ref('visit')
const referenceId = ref('')
const sourceOptions = ref<Array<{ id: number; label: string; month: string }>>([])
const originalMonth = ref('')
const appliedMonth = ref('')
const allowedMonths = ref<string[]>([])
const monthChoices = Array.from({ length: 12 }, (_, i) => ({
  value: String(i + 1).padStart(2, '0'),
  label: new Intl.DateTimeFormat('th-TH', { month: 'long' }).format(new Date(2026, i, 1)),
}))
const yearChoices = Array.from({ length: 7 }, (_, i) => String(new Date().getFullYear() - 3 + i))
const appliedMonthPart = computed({
  get: () => appliedMonth.value.slice(5, 7),
  set: (value: string) => { appliedMonth.value = value ? `${appliedYearPart.value}-${value}` : '' },
})
const appliedYearPart = computed({
  get: () => appliedMonth.value.slice(0, 4),
  set: (value: string) => { appliedMonth.value = value && appliedMonthPart.value ? `${value}-${appliedMonthPart.value}` : '' },
})
const branchCode = computed(() => selectedManagerBranch()?.code ?? '')
const activeTherapists = computed(() =>
  staff.value.filter((item) => item.active && item.position?.toLowerCase() === 'therapist'),
)
const adjustmentType = ref<'add' | 'deduct'>('deduct')
const amount = ref('')
const reason = ref('')
const loading = ref(true)
const errorMessage = ref('')
const creator = computed(
  () => JSON.parse(localStorage.getItem('authUser') ?? 'null')?.fullName ?? 'Manager',
)
const saving = ref(false)

watch(branchCode, async (code, _previous, onCleanup) => {
  staffId.value = ''
  appliedMonth.value = ''
  allowedMonths.value = []
  if (!code) return
  let cancelled = false
  onCleanup(() => { cancelled = true })
  try {
    const months = await commissionApi.adjustmentAllowedMonths(code)
    if (!cancelled) {
      allowedMonths.value = months
      appliedMonth.value = months[0] ?? ''
    }
  } catch (error) {
    if (!cancelled) errorMessage.value = error instanceof Error ? error.message : 'โหลดงวดจ่ายไม่สำเร็จ'
  }
}, { immediate: true })
watch([staffId, referenceType, branchCode], async ([personId, type, code], _old, onCleanup) => {
  referenceId.value = ''
  sourceOptions.value = []
  if (!personId || !code) return
  let cancelled = false
  onCleanup(() => { cancelled = true })
  try {
    const result = await commissionApi.adjustmentSources(Number(personId), type, code)
    if (!cancelled) sourceOptions.value = result
  } catch (error) {
    if (!cancelled) errorMessage.value = error instanceof Error ? error.message : 'โหลดรายการอ้างอิงไม่สำเร็จ'
  }
})
watch(referenceId, (id) => {
  const selected = sourceOptions.value.find((item) => String(item.id) === id)
  if (selected) originalMonth.value = selected.month
})

async function load() {
  try {
    staff.value = await staffApi.list()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดรายชื่อพนักงานไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
async function review() {
  errorMessage.value = ''
  if (
    !staffId.value ||
    !referenceId.value ||
    !originalMonth.value ||
    !appliedMonth.value ||
    !amount.value ||
    !reason.value.trim()
    || !branchCode.value
  ) {
    errorMessage.value = 'กรุณากรอกข้อมูลที่มีเครื่องหมาย * ให้ครบถ้วนก่อนตรวจสอบ'
    return
  }
  if (appliedMonth.value <= originalMonth.value) {
    errorMessage.value = 'งวดที่นำไปใช้ต้องอยู่หลังงวดต้นทาง'
    return
  }
  if (!allowedMonths.value.includes(appliedMonth.value)) {
    errorMessage.value = 'กรุณาเลือกงวดเปิดล่าสุดหรือเดือนถัดไป'
    return
  }
  if (!Number.isSafeInteger(Number(referenceId.value)) || Number(referenceId.value) <= 0) {
    errorMessage.value = 'รหัสรายการอ้างอิงต้องเป็นตัวเลขที่มากกว่า 0'
    return
  }
  const numericAmount = Number(amount.value)
  if (
    !Number.isFinite(numericAmount) ||
    numericAmount <= 0 ||
    Math.abs(numericAmount * 100 - Math.round(numericAmount * 100)) > 0.000001
  ) {
    errorMessage.value = 'จำนวนเงินต้องมากกว่า 0 และมีทศนิยมไม่เกิน 2 ตำแหน่ง'
    return
  }
  saving.value = true
  try {
    const referenceKey =
      referenceType.value === 'course' ? 'courseId' : 'visitId'
    const created = await commissionApi.createAdjustment({
      staffId: Number(staffId.value),
      originalMonth: originalMonth.value.slice(0, 7),
      appliedMonth: appliedMonth.value.slice(0, 7),
      amount: (adjustmentType.value === 'deduct' ? -1 : 1) * Number(amount.value),
      reason: reason.value.trim(),
      branchCode: branchCode.value,
      [referenceKey]: Number(referenceId.value),
    })
    await router.push({
      name: 'commission-adjustment-detail',
      params: { id: String(created.id) },
      query: { created: '1' },
    })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'บันทึกรายการปรับปรุงไม่สำเร็จ'
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="commission-manager-page adjustment-create-page">
    <StaffHeader />
    <main class="commission-manager-main create-main">
      <RouterLink class="back-link" to="/staff/commission/adjustments"
        >← กลับรายการ Adjustment</RouterLink
      >
      <header class="create-title">
        <h1>สร้างรายการปรับปรุงค่าตอบแทน</h1>
        <p>ระบุยอดปรับและงวดเริ่มต้น ระบบจะนำยอดเข้าการคำนวณให้อัตโนมัติ</p>
      </header>

      <p
        v-if="errorMessage"
        class="create-message"
        :class="{ success: errorMessage.startsWith('ตรวจสอบ') }"
      >
        {{ errorMessage }}
      </p>
      <section class="create-card">
        <div class="form-grid">
          <label
            >นักกายภาพ *
            <FilterDropdown
              v-model="staffId"
              label="พนักงาน"
              default-label="เลือกพนักงาน"
              :disabled="loading"
              :options="
                activeTherapists.map((item) => ({
                  value: String(item.id),
                  label: `${item.fullName} · ${item.employeeCode}`,
                }))
              "
            />
          </label>
          <label
            >รายการอ้างอิง *
            <div class="reference-field">
              <FilterDropdown
                v-model="referenceType"
                label="ประเภทรายการอ้างอิง"
                default-label="Visit"
                :options="[
                  { value: 'visit', label: 'Visit' },
                  { value: 'course', label: 'Course' },
                ]"
                :clear-on-reselect="false"
                hide-default-option
              />
              <FilterDropdown v-model="referenceId" label="รายการอ้างอิง" default-label="ค้นหาและเลือกรายการ" searchable :options="sourceOptions.map((item) => ({ value: String(item.id), label: item.label }))" />
            </div>
            <small v-if="staffId && sourceOptions.length === 0" class="source-empty-hint">
              ไม่พบรายการในงวดที่จ่ายแล้ว
            </small>
          </label>
          <label
            >งวดต้นทาง
            <MonthFilterPicker v-model="originalMonth" :past-years="3" :future-years="3" />
          </label>
          <label
            >งวดเริ่มนำไปใช้ *
            <div class="reference-field">
              <FilterDropdown v-model="appliedMonthPart" label="เดือนที่นำไปใช้" default-label="เลือกเดือน" :options="monthChoices" />
              <FilterDropdown v-model="appliedYearPart" label="ปีที่นำไปใช้" default-label="เลือกปี" :options="yearChoices.map((year) => ({ value: year, label: String(Number(year) + 543) }))" />
            </div>
          </label>
          <label
            >ประเภทผลต่าง *
            <FilterDropdown
              v-model="adjustmentType"
              label="ประเภทผลต่าง"
              default-label="ลดค่าตอบแทน (-)"
              :options="[
                { value: 'add', label: 'เพิ่มค่าตอบแทน (+)' },
                { value: 'deduct', label: 'ลดค่าตอบแทน (-)' },
              ]"
              :clear-on-reselect="false"
              hide-default-option
            />
          </label>
          <label
            >จำนวนเงิน *
            <input v-model="amount" type="number" min="0.01" step="0.01" placeholder="0.00" />
          </label>
        </div>
        <label class="reason-field"
          >เหตุผล *<input v-model="reason" placeholder="ระบุเหตุผลของการปรับปรุง" /></label
        >
      </section>
      <footer class="create-actions">
        <RouterLink class="commission-secondary cancel" to="/staff/commission/adjustments"
          >ยกเลิก</RouterLink
        >
        <button class="commission-primary" type="button" :disabled="saving" @click="review">
          {{ saving ? 'กำลังบันทึก...' : 'บันทึกรายการปรับยอด' }}
        </button>
      </footer>
    </main>
  </div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.create-main {
  padding-top: 34px;
}
.back-link {
  display: inline-flex;
  margin-bottom: 34px;
  color: #13766f;
  font-weight: 700;
  text-decoration: none;
}
.create-title h1 {
  margin: 0;
  font-size: clamp(1.75rem, 2.7vw, 2.25rem);
}
.create-title p {
  margin: 8px 0 30px;
  color: #52635b;
}
.create-card {
  padding: 28px 24px 32px;
  border: 1px solid #d9e1dc;
  border-radius: 12px;
  background: #fff;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 34px 16px;
}
.form-grid label,
.reason-field {
  display: grid;
  gap: 6px;
  color: #25372f;
  font-weight: 700;
}
.form-grid > label:first-child {
  gap: 4px;
}
.source-empty-hint {
  margin-top: -4px;
  color: #526b63;
  font-size: 0.76rem;
  font-weight: 600;
}
.form-grid input,
.reason-field input {
  width: 100%;
  min-height: 46px;
  box-sizing: border-box;
  padding: 0 16px;
  border: 1px solid #b7cecc;
  border-radius: 10px;
  background: #fff;
  color: #273a32;
  font: inherit;
  font-weight: 500;
}
.form-grid :deep(.filter-dropdown__button),
.form-grid :deep(.date-trigger) {
  min-height: 46px;
  border-color: #b7cecc;
  border-radius: 10px;
}
.reference-field {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2fr);
  gap: 8px;
}
.reason-field {
  margin-top: 22px;
}
.reason-field input {
  min-height: 46px;
}
.create-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 24px;
}
.create-actions .commission-primary,
.cancel {
  display: inline-flex;
  align-items: center;
  text-decoration: none;
}
.create-message {
  margin: 0 0 16px;
  padding: 12px 16px;
  border-radius: 8px;
  background: #fff0ee;
  color: #a8322b;
}
.create-message.success {
  background: #e3f3ed;
  color: #176c54;
}
@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
    gap: 22px;
  }
  .reference-field {
    grid-template-columns: 1fr;
  }
  .create-card {
    padding: 20px;
  }
  .create-actions {
    flex-direction: column-reverse;
  }
  .create-actions > * {
    justify-content: center;
  }
}
</style>
