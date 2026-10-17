<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { commissionApi } from '@/api/client'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import CommissionTabs from './CommissionTabs.vue'

type Rule = { title: string; detail: string; amount: string }
const rules = ref<Rule[]>([])
const loading = ref(true)
onMounted(async () => {
  try {
    const rows = (await commissionApi.treatmentFees()) as Array<{
      id: number
      employeeId: number
      feeType: string
      feeValue: number
      active: boolean
    }>
    rules.value = rows
      .filter((row) => row.active)
      .map((row) => ({
        title: `พนักงาน #${row.employeeId} · ${row.feeType === 'PERCENTAGE' ? 'Percentage' : 'Fixed'}`,
        detail: 'ค่าตอบแทนผู้รักษาแทน',
        amount:
          row.feeType === 'PERCENTAGE'
            ? `${row.feeValue}%`
            : `฿${Number(row.feeValue).toLocaleString('th-TH')} / ครั้ง`,
      }))
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : 'โหลดกฎค่ามือไม่สำเร็จ'
  } finally {
    loading.value = false
  }
})
const drawerOpen = ref(false)
const error = ref('')
const form = reactive({
  name: '',
  recipient: 'GENERAL',
  service: 'ALL',
  calculation: 'FIXED',
  value: 0,
  overflow: 'CAP_AT_COMMISSION',
  effectiveFrom: new Date().toLocaleDateString('en-CA'),
  effectiveTo: '',
})
function openDrawer() {
  error.value = ''
  Object.assign(form, {
    name: '',
    recipient: 'GENERAL',
    service: 'ALL',
    calculation: 'FIXED',
    value: 0,
    overflow: 'CAP_AT_COMMISSION',
    effectiveFrom: new Date().toLocaleDateString('en-CA'),
    effectiveTo: '',
  })
  drawerOpen.value = true
}
function saveRule() {
  error.value = ''
  if (!form.name.trim() || form.value <= 0 || !form.effectiveFrom) {
    error.value = 'กรุณากรอกชื่อ อัตรา และวันที่เริ่มใช้งาน'
    return
  }
  error.value = 'ยังไม่ได้เชื่อมการบันทึกกฎกับข้อมูลพนักงานจริง'
}
</script>

<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main">
      <header class="commission-title">
        <h1>ตั้งค่าค่ามือ</h1>
        <p>กำหนดค่าตอบแทนนักกายภาพผู้รักษาแทนแบบจำนวนเงินหรือเปอร์เซ็นต์</p>
      </header>
      <CommissionTabs active="compensation" />
      <section class="commission-card compensation-panel">
        <header class="rules-head">
          <h2>กฎค่ามือ</h2>
          <p>
            ใช้เมื่อผู้รักษาไม่ใช่ Case Owner และต้องไม่เกิน Gross Commission Allocation ต่อครั้ง
          </p>
        </header>
        <p v-if="loading">กำลังโหลดข้อมูล…</p>
        <p v-else-if="error" class="drawer-error">{{ error }}</p>
        <p v-else-if="!rules.length">ยังไม่มีกฎค่ามือ</p>
        <section class="rules">
          <article v-for="rule in rules" :key="rule.title" class="commission-card">
            <div>
              <b>{{ rule.title }}</b
              ><small>{{ rule.detail }}</small>
            </div>
            <strong>{{ rule.amount }}</strong>
          </article>
        </section>
        <button class="commission-primary add-rule" @click="openDrawer">+ เพิ่มกฎค่ามือ</button>
      </section>
    </main>
    <Teleport to="body"
      ><div v-if="drawerOpen" class="rule-drawer-backdrop" @click.self="drawerOpen = false">
        <aside class="rule-drawer" role="dialog" aria-modal="true">
          <header>
            <div>
              <h2>เพิ่มกฎค่ามือ</h2>
              <p>กำหนดผู้รับ บริการ และวิธีคำนวณ</p>
            </div>
            <button class="drawer-close" @click="drawerOpen = false">×</button>
          </header>
          <div class="rule-form">
            <label>ชื่อกฎ<input v-model="form.name" placeholder="เช่น นักกายภาพทั่วไป" /></label
            ><label
              >ผู้ได้รับค่ารักษาแทน<FilterDropdown
                v-model="form.recipient"
                label="ผู้ได้รับค่ารักษาแทน"
                default-value="GENERAL"
                default-label="นักกายภาพทั่วไป"
                hide-default-option
                :clear-on-reselect="false"
                :options="[
                  { value: 'GENERAL', label: 'นักกายภาพทั่วไป' },
                  { value: 'SENIOR', label: 'Senior Therapist' },
                ]" /></label
            ><label
              >บริการ<FilterDropdown
                v-model="form.service"
                label="บริการ"
                default-value="ALL"
                default-label="ทุกบริการ"
                hide-default-option
                :clear-on-reselect="false"
                :options="[
                  { value: 'ALL', label: 'ทุกบริการ' },
                  { value: 'Manual Therapy', label: 'Manual Therapy' },
                  { value: 'Exercise Therapy', label: 'Exercise Therapy' },
                ]" /></label
            ><label
              >วิธีคำนวณ<FilterDropdown
                v-model="form.calculation"
                label="วิธีคำนวณ"
                default-value="FIXED"
                default-label="จำนวนเงินคงที่ต่อครั้ง"
                hide-default-option
                :clear-on-reselect="false"
                :options="[
                  { value: 'FIXED', label: 'จำนวนเงินคงที่ต่อครั้ง' },
                  { value: 'PERCENTAGE', label: 'เปอร์เซ็นต์จากมูลค่าคอร์ส' },
                ]" /></label
            ><label
              >{{ form.calculation === 'FIXED' ? 'จำนวนเงินต่อครั้ง (บาท)' : 'อัตรา (%)'
              }}<input v-model.number="form.value" type="number" min="0.01" step="0.01" /></label
            ><label
              >เมื่อเกินเพดาน<FilterDropdown
                v-model="form.overflow"
                label="เมื่อเกินเพดาน"
                default-value="CAP_AT_COMMISSION"
                default-label="จำกัดไม่ให้เกินคอมมิชชัน"
                hide-default-option
                :clear-on-reselect="false"
                :options="[
                  { value: 'CAP_AT_COMMISSION', label: 'จำกัดไม่ให้เกินคอมมิชชัน' },
                  { value: 'BLOCK_AND_REQUIRE_APPROVAL', label: 'ระงับและรออนุมัติ' },
                ]" /></label
            ><label
              >วันที่เริ่มใช้งาน<ThaiDatePicker
                v-model="form.effectiveFrom"
                numeric-month
                show-calendar-icon
                hide-caret /></label
            ><label
              >วันสิ้นสุด (ไม่บังคับ)<ThaiDatePicker
                v-model="form.effectiveTo"
                numeric-month
                show-calendar-icon
                hide-caret
            /></label>
          </div>
          <p v-if="error" class="drawer-error">{{ error }}</p>
          <footer>
            <button class="commission-secondary" @click="drawerOpen = false">ยกเลิก</button
            ><button class="commission-primary" @click="saveRule">บันทึกกฎ</button>
          </footer>
        </aside>
      </div></Teleport
    >
  </div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.compensation-panel {
  padding: 28px;
  min-height: 800px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  border-color: #dde6e1;
  border-radius: 14px;
  gap: 0;
}
.rules-head h2 {
  font-size: 23px;
  margin: 0;
}
.rules-head p {
  margin: 4px 0 28px;
  color: #667a72;
}
.rules {
  display: grid;
  gap: 24px;
}
.rules article {
  min-height: 88px;
  box-sizing: border-box;
  padding: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.rules article div {
  display: grid;
  gap: 8px;
}
.rules small {
  color: #667a72;
}
.rules strong {
  min-width: 180px;
  margin-right: 19%;
  color: #176c68;
  text-align: left;
}
.add-rule {
  display: block;
  margin: auto 0 0 auto;
  min-height: 48px;
  min-width: 240px;
  background: #1f8883;
}
@media (max-width: 700px) {
  .compensation-panel {
    min-height: 0;
    padding: 20px;
  }
  .rules strong {
    margin-right: 0;
  }
  .rules article {
    align-items: start;
    flex-direction: column;
  }
  .rules strong {
    min-width: 0;
    text-align: left;
  }
  .add-rule {
    width: 100%;
    margin-top: 40px;
  }
}
</style>
