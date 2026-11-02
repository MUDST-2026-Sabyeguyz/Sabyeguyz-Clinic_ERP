<script setup lang="ts">
// สร้างคำขอเพิ่มหรือลดยอดค่าตอบแทนพร้อมเหตุผล
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import { commissionApi, staffApi, type Staff } from '@/api/client'

const router = useRouter()
const staff = ref<Staff[]>([])
const staffId = ref('')
const referenceType = ref('visit')
const referenceId = ref('')
const originalMonth = ref('')
const appliedMonth = ref('')
const adjustmentType = ref<'add' | 'deduct'>('deduct')
const amount = ref('')
const reason = ref('')
const loading = ref(true)
const errorMessage = ref('')
const creator = computed(
  () => JSON.parse(localStorage.getItem('authUser') ?? 'null')?.fullName ?? 'Manager',
)
const saving = ref(false)

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
  ) {
    errorMessage.value = 'กรุณากรอกข้อมูลที่มีเครื่องหมาย * ให้ครบถ้วนก่อนตรวจสอบ'
    return
  }
  if (appliedMonth.value.slice(0, 7) <= originalMonth.value.slice(0, 7)) {
    errorMessage.value = 'งวดที่เสนอให้นำไปใช้ต้องอยู่หลังงวดต้นทาง'
    return
  }
  if (!Number.isSafeInteger(Number(referenceId.value)) || Number(referenceId.value) <= 0) {
    errorMessage.value = 'รหัสรายการอ้างอิงต้องเป็นตัวเลขมากกว่า 0'
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
      referenceType.value === 'visit'
        ? 'visitId'
        : referenceType.value === 'course'
          ? 'courseId'
          : 'allocationId'
    const created = await commissionApi.createAdjustment({
      staffId: Number(staffId.value),
      originalMonth: originalMonth.value.slice(0, 7),
      appliedMonth: appliedMonth.value.slice(0, 7),
      amount: (adjustmentType.value === 'deduct' ? -1 : 1) * Number(amount.value),
      reason: reason.value.trim(),
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
        <p>ระบุยอดเพิ่มหรือหักเพื่อแก้ข้อผิดพลาดของงวดเดิม รายการใหม่จะรออนุมัติก่อนนำยอดไปใช้</p>
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
            >พนักงาน *
            <FilterDropdown
              v-model="staffId"
              label="พนักงาน"
              default-label="เลือกพนักงาน"
              :disabled="loading"
              :options="
                staff.map((item) => ({
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
                  { value: 'commission', label: 'Commission' },
                ]"
                :clear-on-reselect="false"
                hide-default-option
              />
              <input v-model="referenceId" placeholder="ระบุรหัสรายการอ้างอิง" />
            </div>
            <small>ระบุรหัสจริงของรายการต้นทาง ระบบจะตรวจสอบว่าเป็นของพนักงานที่เลือก</small>
          </label>
          <label
            >งวดต้นทาง
            <ThaiDatePicker
              v-model="originalMonth"
              numeric-month
              show-calendar-icon
              placeholder="เลือกงวดต้นทาง"
            />
          </label>
          <label
            >งวดที่เสนอให้นำไปใช้ *
            <ThaiDatePicker
              v-model="appliedMonth"
              numeric-month
              show-calendar-icon
              :min-date="originalMonth"
              placeholder="เลือกงวดที่จะนำไปใช้"
            />
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
            <small>เพิ่ม (+) หรือลด (-)</small>
          </label>
          <label
            >จำนวนเงิน *
            <input v-model="amount" type="number" min="0.01" step="0.01" placeholder="0.00" />
            <small>กรอกจำนวนบวก ระบบแสดงเครื่องหมายตามประเภท</small>
          </label>
        </div>
        <label class="reason-field"
          >เหตุผล *<input v-model="reason" placeholder="ระบุเหตุผลของการปรับปรุง" /><small
            >บันทึกเหตุผลไว้ใน Audit Log</small
          ></label
        >
        <p class="creator">ผู้สร้าง: {{ creator }} (Manager) · วันเวลาบันทึกจากระบบ</p>
      </section>
      <footer class="create-actions">
        <RouterLink class="commission-secondary cancel" to="/staff/commission/adjustments"
          >ยกเลิก</RouterLink
        >
        <button class="commission-primary" type="button" :disabled="saving" @click="review">
          {{ saving ? 'กำลังบันทึก...' : 'สร้างรายการปรับยอด' }}
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
  gap: 10px;
  color: #25372f;
  font-weight: 700;
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
.form-grid small,
.reason-field small {
  color: #687a72;
  font-size: 0.82rem;
  font-weight: 400;
}
.reference-field {
  display: grid;
  grid-template-columns: 145px 1fr;
  gap: 8px;
}
.reason-field {
  margin-top: 22px;
}
.reason-field input {
  min-height: 46px;
}
.creator {
  margin: 0;
  color: #52635b;
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
