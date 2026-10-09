<script setup lang="ts">
import { reactive, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import CommissionTabs from './CommissionTabs.vue'

type Rule = { title: string; detail: string; amount: string }
const rules = ref<Rule[]>([
  {
    title: 'นักกายภาพทั่วไป · Fixed',
    detail: 'ทุกบริการ · เกินเพดาน: จำกัดยอดอัตโนมัติ',
    amount: '฿45 / ครั้ง',
  },
  {
    title: 'Senior Therapist · Percentage',
    detail: 'มูลค่าคอร์สต่อครั้ง · เกินเพดาน: จำกัดยอดอัตโนมัติ',
    amount: '3%',
  },
  {
    title: 'Manual Therapy · Fixed',
    detail: 'บริการ Manual Therapy · เกินเพดาน: รอ Manager อนุมัติ',
    amount: '฿60 / ครั้ง',
  },
])
const drawerOpen = ref(false)
const error = ref('')
const form = reactive({
  name: '',
  recipient: 'GENERAL',
  service: 'ALL',
  calculation: 'FIXED',
  value: 45,
  overflow: 'CAP_AT_COMMISSION',
  effectiveFrom: '2026-10-08',
  effectiveTo: '',
})
function openDrawer() {
  error.value = ''
  Object.assign(form, {
    name: '',
    recipient: 'GENERAL',
    service: 'ALL',
    calculation: 'FIXED',
    value: 45,
    overflow: 'CAP_AT_COMMISSION',
    effectiveFrom: '2026-10-08',
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
  const type = form.calculation === 'FIXED' ? 'Fixed' : 'Percentage'
  const service = form.service === 'ALL' ? 'ทุกบริการ' : `บริการ ${form.service}`
  const overflow =
    form.overflow === 'CAP_AT_COMMISSION'
      ? 'เกินเพดาน: จำกัดยอดอัตโนมัติ'
      : 'เกินเพดาน: รอ Manager อนุมัติ'
  rules.value.push({
    title: `${form.name.trim()} · ${type}`,
    detail: `${service} · ${overflow}`,
    amount:
      form.calculation === 'FIXED'
        ? `฿${form.value.toLocaleString('th-TH')} / ครั้ง`
        : `${form.value}%`,
  })
  drawerOpen.value = false
}
</script>

<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main">
      <header class="commission-title">
        <h1>ตั้งค่าค่ารักษาแทน</h1>
        <p>กำหนดค่าตอบแทนนักกายภาพผู้รักษาแทนแบบจำนวนเงินหรือเปอร์เซ็นต์</p>
      </header>
      <CommissionTabs active="compensation" />
      <section class="rules-head">
        <h2>กฎค่ารักษาแทน</h2>
        <p>ใช้เมื่อผู้รักษาไม่ใช่ Case Owner และต้องไม่เกิน Gross Commission Allocation ต่อครั้ง</p>
      </section>
      <section class="rules">
        <article v-for="rule in rules" :key="rule.title" class="commission-card">
          <div>
            <b>{{ rule.title }}</b
            ><small>{{ rule.detail }}</small>
          </div>
          <strong>{{ rule.amount }}</strong>
        </article>
      </section>
      <button class="commission-primary add-rule" @click="openDrawer">+ เพิ่มกฎค่ารักษาแทน</button>
    </main>
    <Teleport to="body"
      ><div v-if="drawerOpen" class="rule-drawer-backdrop" @click.self="drawerOpen = false">
        <aside class="rule-drawer" role="dialog" aria-modal="true">
          <header>
            <div>
              <h2>เพิ่มกฎค่ารักษาแทน</h2>
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
.rules-head h2 {
  margin: 0;
}
.rules-head p {
  margin: 4px 0 28px;
  color: #667a72;
}
.rules {
  display: grid;
  gap: 22px;
}
.rules article {
  min-height: 86px;
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
  color: #16877f;
  text-align: center;
}
.add-rule {
  display: block;
  margin: 60px 0 0 auto;
}
@media (max-width: 700px) {
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
