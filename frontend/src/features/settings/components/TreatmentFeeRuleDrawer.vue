<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { commissionApi } from '@/api/client'
import { selectedManagerBranch } from '@/features/staff/data/managerBranch'
import { clinicToday } from '@/utils/clinicDate'
import MonthFilterPicker from '@/features/staff/components/MonthFilterPicker.vue'

const emit = defineEmits<{ saved: [] }>()
const drawerOpen = ref(false)
const saving = ref(false)
const error = ref('')
const errors = reactive<Record<string, string>>({})
const message = ref('')
const branchCode = computed(() => selectedManagerBranch()?.code ?? '')
const branchName = computed(() => {
  const name = selectedManagerBranch()?.name?.trim() ?? branchCode.value
  return name.replace(/^สาขา\s*/, '') || branchCode.value
})
const nextMonth = () => {
  const [year, month] = clinicToday().slice(0, 7).split('-').map(Number)
  const firstNext = new Date(Date.UTC(year!, month!, 1))
  return `${firstNext.getUTCFullYear()}-${String(firstNext.getUTCMonth() + 1).padStart(2, '0')}`
}
const form = reactive({
  name: '',
  feeType: 'Fixed' as 'Fixed' | 'Percentage',
  feeValue: '',
  effectiveMonth: nextMonth(),
})

function open() {
  error.value = ''
  Object.keys(errors).forEach((key) => delete errors[key])
  message.value = ''
  Object.assign(form, { name: '', feeType: 'Fixed', feeValue: '', effectiveMonth: nextMonth() })
  drawerOpen.value = true
}

async function save() {
  error.value = ''
  Object.keys(errors).forEach((key) => delete errors[key])
  const text = form.feeValue.trim()
  const value = Number(text)
  if (!branchCode.value) {
    error.value = 'กรุณาเลือกสาขาก่อนบันทึกกฎค่ามือ'
    return
  }
  if (!form.name.trim()) errors.name = 'กรุณากรอกชื่อกฎ'
  else if (form.name.trim().length > 100) errors.name = 'ชื่อกฎต้องไม่เกิน 100 ตัวอักษร'
  if (!text) errors.feeValue = 'กรุณากรอก' + (form.feeType === 'Percentage' ? 'อัตรา (%)' : 'จำนวนเงิน')
  else if (!Number.isFinite(value) || value < 0)
    errors.feeValue = 'ต้องเป็นตัวเลขไม่ติดลบ'
  else if (form.feeType === 'Percentage' && value > 100)
    errors.feeValue = 'เปอร์เซ็นต์ต้องอยู่ระหว่าง 0 ถึง 100'
  if (!form.effectiveMonth) errors.effectiveMonth = 'กรุณาเลือกเดือนที่เริ่มใช้งาน'
  else if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(form.effectiveMonth))
    errors.effectiveMonth = 'รูปแบบเดือนไม่ถูกต้อง'
  else if (form.effectiveMonth < nextMonth())
    errors.effectiveMonth = 'ต้องเริ่มใช้งานตั้งแต่เดือนถัดไปเป็นต้นไป'
  if (Object.keys(errors).length) {
    error.value = 'กรุณาตรวจสอบข้อมูลในช่องที่มีข้อความสีแดง'
    return
  }

  saving.value = true
  try {
    await commissionApi.createTreatmentFee({
      name: form.name.trim(),
      feeType: form.feeType,
      feeValue: value,
      effectiveFrom: `${form.effectiveMonth}-01`,
      branchCode: branchCode.value,
    })
    drawerOpen.value = false
    message.value = 'บันทึกกฎค่ามือแล้ว ระบบกำหนดวันสิ้นสุดของกฎเดิมให้อัตโนมัติ'
    emit('saved')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'บันทึกกฎค่ามือไม่สำเร็จ'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="treatment-fee-actions">
    <button class="treatment-fee-open" type="button" @click="open">ตั้งค่ากฎค่ามือ</button>
    <p v-if="message" class="treatment-fee-message" role="status">{{ message }}</p>
    <Teleport to="body">
      <div v-if="drawerOpen" class="treatment-fee-backdrop" @click.self="drawerOpen = false">
        <aside
          class="treatment-fee-drawer"
          role="dialog"
          aria-modal="true"
          aria-labelledby="treatment-fee-title"
        >
          <header>
            <h2 id="treatment-fee-title">ตั้งค่ากฎค่ามือ</h2>
            <button
              class="treatment-fee-close"
              type="button"
              aria-label="ปิด"
              @click="drawerOpen = false"
            >
              ×
            </button>
          </header>
          <div class="treatment-fee-form">
            <label>
              <span>ชื่อกฎ<span class="req">*</span></span>
              <input v-model="form.name" maxlength="100" :class="{ invalid: errors.name }" />
              <small v-if="errors.name" class="field-hint" role="alert">{{ errors.name }}</small>
            </label>
            <label>
              <span>วิธีคำนวณ<span class="req">*</span></span>
              <select v-model="form.feeType">
                <option value="Fixed">จำนวนเงินต่อครั้ง</option>
                <option value="Percentage">เปอร์เซ็นต์</option>
              </select>
            </label>
            <label>
              <span
                >{{ form.feeType === 'Percentage' ? 'อัตรา (%)' : 'จำนวนเงิน (บาท / ครั้ง)'
                }}<span class="req">*</span></span
              >
              <input
                :class="{ invalid: errors.feeValue }"
                v-model="form.feeValue"
                type="number"
                min="0"
                step="0.01"
                :max="form.feeType === 'Percentage' ? 100 : undefined"
              />
              <small v-if="errors.feeValue" class="field-hint" role="alert">{{
                errors.feeValue
              }}</small>
            </label>
<<<<<<< Updated upstream
            <label>
              <span>เดือนที่เริ่มใช้งาน<span class="req">*</span></span>
              <input
                v-model="form.effectiveMonth"
                type="month"
                :min="nextMonth()"
                :class="{ invalid: errors.effectiveMonth }"
              />
              <small v-if="errors.effectiveMonth" class="field-hint" role="alert">{{
                errors.effectiveMonth
              }}</small>
            </label>
=======
            <div class="treatment-fee-month-field">
              <span>เดือนที่เริ่มใช้งาน</span>
              <MonthFilterPicker v-model="form.effectiveMonth" :min="nextMonth()" />
            </div>
>>>>>>> Stashed changes
            <p class="treatment-fee-branch">สาขา: {{ branchName }}</p>
          </div>
          <p v-if="error" class="treatment-fee-error" role="alert">{{ error }}</p>
          <footer>
            <button
              class="treatment-fee-cancel"
              type="button"
              :disabled="saving"
              @click="drawerOpen = false"
            >
              ยกเลิก
            </button>
            <button
              class="treatment-fee-save"
              type="button"
              :disabled="saving"
              @click="save"
            >
              {{ saving ? 'กำลังบันทึก…' : 'บันทึก' }}
            </button>
          </footer>
        </aside>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.treatment-fee-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.treatment-fee-open,
.treatment-fee-save,
.treatment-fee-cancel,
.treatment-fee-close {
  min-height: 44px;
  padding: 0 20px;
  border: 1px solid #1e8b86;
  border-radius: 8px;
  background: #1e8b86;
  color: white;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.treatment-fee-message {
  margin: 0;
  color: #176c68;
  font-size: 14px;
}
.treatment-fee-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: grid;
  justify-items: end;
  background: #14251f66;
}
.treatment-fee-drawer {
  width: min(520px, 100%);
  height: 100%;
  overflow: auto;
  box-sizing: border-box;
  padding: 28px;
  background: white;
  color: #25372f;
}
.treatment-fee-drawer header,
.treatment-fee-drawer footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.treatment-fee-drawer h2 {
  margin: 0;
  font-size: 24px;
}
.treatment-fee-close {
  width: 44px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #667a72;
  font-size: 28px;
}
.treatment-fee-form {
  display: grid;
  gap: 18px;
  margin: 28px 0;
}
.treatment-fee-form label,
.treatment-fee-month-field {
  display: grid;
  gap: 8px;
  color: #52645d;
}
.treatment-fee-form input,
.treatment-fee-form select {
  width: 100%;
  min-height: 46px;
  padding: 8px 12px;
  box-sizing: border-box;
  border: 1px solid #b7cecc;
  border-radius: 8px;
  background: #f8faf9;
  color: #25372f;
  font: inherit;
}
.req {
  margin-left: 2px;
  color: #c0392b;
}
.treatment-fee-form input.invalid {
  border-color: #c0392b;
}
.field-hint {
  color: #c0392b;
  font-size: 13px;
}
.treatment-fee-branch {
  margin: 0;
  color: #145c59;
}
.treatment-fee-drawer footer {
  margin-top: 28px;
}
.treatment-fee-cancel {
  border-color: #d9e1dc;
  background: white;
  color: #25372f;
}
.treatment-fee-save:disabled,
.treatment-fee-cancel:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.treatment-fee-error {
  color: #a02f2f;
}
@media (max-width: 620px) {
  .treatment-fee-actions {
    align-items: flex-end;
    flex-direction: column;
  }
  .treatment-fee-open {
    width: 100%;
  }
}
</style>
