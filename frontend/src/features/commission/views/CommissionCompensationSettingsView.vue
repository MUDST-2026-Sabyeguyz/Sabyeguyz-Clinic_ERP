<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import CommissionTabs from './CommissionTabs.vue'
import { commissionApi, type TreatmentFeeRule } from '@/api/client'
import { clinicToday } from '@/utils/clinicDate'
import { recordedAt, versionCycles, versionStatus } from '@/features/settings/commissionVersion'

// The treatment fee rule is read here; it is changed in Settings (กฎค่ามือ), where the rule in force opens ready to edit
// and a change for a later month is kept as a new version. This page also records the single-visit fees that were missing.
const rules = ref<TreatmentFeeRule[]>([])
const loading = ref(false)
const pageError = ref('')

const backfill = reactive({ from: `${clinicToday().slice(0, 8)}01`, to: clinicToday() })
const backfillMessage = ref('')

const amountLabel = (rule: TreatmentFeeRule) =>
  rule.feeType === 'Fixed'
    ? `฿${Number(rule.feeValue).toLocaleString('th-TH')} / ครั้ง`
    : `${Number(rule.feeValue)}%`
const range = (rule: TreatmentFeeRule) => ({ effectiveFrom: rule.effectiveFrom, effectiveTo: rule.effectiveTo })

async function load() {
  loading.value = true
  pageError.value = ''
  try {
    // newest first, as the versions of the rule
    rules.value = (await commissionApi.treatmentFees())
      .slice()
      .sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom))
  } catch (caught) {
    pageError.value = caught instanceof Error ? caught.message : 'โหลดกฎค่ามือไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function runBackfill() {
  backfillMessage.value = ''
  pageError.value = ''
  try {
    const result = await commissionApi.backfillSingleVisitFees(backfill.from, backfill.to)
    backfillMessage.value = `บันทึกค่ามือรายครั้งที่ขาดเพิ่ม ${result.recorded} รายการ`
  } catch (caught) {
    pageError.value = caught instanceof Error ? caught.message : 'บันทึกค่ามือที่ขาดไม่สำเร็จ'
  }
}
</script>

<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main">
      <header class="commission-title">
        <h1>ตั้งค่าค่ารักษาแทน</h1>
        <p>กฎค่ามือส่วนกลางเดียวสำหรับนักกายภาพทุกคนและทุกบริการ</p>
      </header>
      <CommissionTabs active="compensation" />
      <section class="rules-head">
        <h2>กฎค่ามือ</h2>
        <p>
          ใช้เมื่อผู้รักษาไม่ใช่ Owner ของคอร์ส และกับการรักษารายครั้ง จ่ายเต็มตามกฎ ณ วันรักษา
          ไม่จำกัดด้วยค่าคอมมิชชัน การเปลี่ยนกฎทำที่การตั้งค่า และเริ่มใช้ตั้งแต่ต้นเดือนถัดไป
        </p>
        <RouterLink class="commission-primary edit-link" to="/staff/settings?section=commission"
          >แก้กฎค่ามือ</RouterLink
        >
      </section>
      <p v-if="pageError" class="page-error" role="alert">{{ pageError }}</p>
      <p v-if="loading">กำลังโหลดกฎค่ามือ...</p>
      <section class="rules">
        <article v-for="rule in rules" :key="rule.id" class="commission-card rule-card">
          <div>
            <b>{{ rule.feeType === 'Fixed' ? 'จำนวนเงินคงที่ต่อครั้ง' : 'เปอร์เซ็นต์' }}</b
            ><small>{{ versionCycles(range(rule)) }} · {{ versionStatus(range(rule)).label }}</small
            ><small v-if="rule.lastEditedBy || rule.lastEditedAt"
              >บันทึกล่าสุดโดย {{ rule.lastEditedBy ?? '-' }} · {{ recordedAt(rule.lastEditedAt) }}</small
            >
          </div>
          <strong>{{ amountLabel(rule) }}</strong>
        </article>
        <p v-if="!loading && rules.length === 0" class="empty">
          ยังไม่มีกฎค่ามือ การรักษาแทนจะบันทึกไม่ได้และค่ามือรายครั้งจะไม่ถูกบันทึกจนกว่าจะเพิ่มกฎ
        </p>
      </section>

      <section class="commission-card backfill">
        <h2>บันทึกค่ามือรายครั้งที่ขาด</h2>
        <p>
          การขายรายครั้งที่ชำระครบแต่ยังไม่มีกฎ ณ วันนั้นจะไม่มีค่ามือ (ไม่ใส่ 0)
          หลังเพิ่มกฎแล้วกดบันทึกย้อนหลังได้
        </p>
        <div class="backfill-row">
          <label
            >ตั้งแต่<ThaiDatePicker
              v-model="backfill.from"
              numeric-month
              show-calendar-icon
              hide-caret
          /></label>
          <label
            >ถึง<ThaiDatePicker v-model="backfill.to" numeric-month show-calendar-icon hide-caret
          /></label>
          <button class="commission-secondary" @click="runBackfill">บันทึกย้อนหลัง</button>
        </div>
        <p v-if="backfillMessage" class="success" role="status">{{ backfillMessage }}</p>
      </section>
    </main>
  </div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.rules-head h2 {
  margin: 0;
}
.rules-head p {
  margin: 4px 0 18px;
  color: #667a72;
}
.edit-link {
  display: inline-block;
  margin-bottom: 24px;
  text-decoration: none;
}
.rules {
  display: grid;
  gap: 22px;
}
.rule-card {
  min-height: 86px;
  padding: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.rule-card > div:first-child {
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
.backfill {
  margin-top: 36px;
  padding: 22px;
  grid-column: 1 / -1;
}
.backfill h2 {
  margin: 0 0 6px;
  font-size: 1.2rem;
}
.backfill p {
  margin: 0 0 14px;
  color: #667a72;
}
.backfill-row {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: end;
}
.backfill-row label {
  display: grid;
  gap: 6px;
  color: #52635b;
}
.page-error {
  padding: 11px;
  border-radius: 8px;
  background: #fff1ef;
  color: #b42318;
}
.success {
  color: #16875b;
}
.empty {
  color: #667a72;
}
@media (max-width: 700px) {
  .rule-card {
    align-items: start;
    flex-direction: column;
  }
  .rules strong {
    min-width: 0;
    text-align: left;
  }
}
</style>
