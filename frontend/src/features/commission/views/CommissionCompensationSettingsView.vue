<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import CommissionTabs from './CommissionTabs.vue'
import { commissionApi, type TreatmentFeeRule } from '@/api/client'
import { clinicToday } from '@/utils/clinicDate'

const rules = ref<TreatmentFeeRule[]>([])
const loading = ref(false)
const pageError = ref('')
const drawerOpen = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const error = ref('')
const form = reactive({
  feeType: 'Fixed' as 'Fixed' | 'Percentage',
  feeValue: 45,
  effectiveFrom: clinicToday(),
  effectiveTo: '',
})
const editingRule = computed(() => rules.value.find((rule) => rule.id === editingId.value) ?? null)
// a rule whose value was copied into a ledger keeps its type, value and start: only its end date can change
const lockedValue = computed(() => Boolean(editingRule.value?.used))

const backfill = reactive({ from: `${clinicToday().slice(0, 8)}01`, to: clinicToday() })
const backfillMessage = ref('')

const thaiDate = (value: string | null) =>
  value
    ? new Intl.DateTimeFormat('th-TH', { dateStyle: 'medium' }).format(
        new Date(`${value}T00:00:00`),
      )
    : 'ไม่กำหนดวันสิ้นสุด'
const amountLabel = (rule: TreatmentFeeRule) =>
  rule.feeType === 'Fixed'
    ? `฿${Number(rule.feeValue).toLocaleString('th-TH')} / ครั้ง`
    : `${Number(rule.feeValue)}%`

async function load() {
  loading.value = true
  pageError.value = ''
  try {
    rules.value = await commissionApi.treatmentFees()
  } catch (caught) {
    pageError.value = caught instanceof Error ? caught.message : 'โหลดกฎค่ามือไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
onMounted(load)

function openDrawer(rule?: TreatmentFeeRule) {
  error.value = ''
  editingId.value = rule?.id ?? null
  Object.assign(form, {
    feeType: rule?.feeType ?? 'Fixed',
    feeValue: rule ? Number(rule.feeValue) : 45,
    effectiveFrom: rule?.effectiveFrom ?? clinicToday(),
    effectiveTo: rule?.effectiveTo ?? '',
  })
  drawerOpen.value = true
}

async function saveRule() {
  error.value = ''
  if (!(form.feeValue >= 0) || !form.effectiveFrom) {
    error.value = 'กรุณากรอกอัตราและวันที่เริ่มใช้งาน'
    return
  }
  if (form.feeType === 'Percentage' && form.feeValue > 100) {
    error.value = 'เปอร์เซ็นต์ต้องอยู่ระหว่าง 0 ถึง 100'
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await commissionApi.updateTreatmentFee(editingId.value, {
        feeType: form.feeType,
        feeValue: form.feeValue,
        effectiveFrom: form.effectiveFrom,
        effectiveTo: form.effectiveTo || undefined,
      })
    } else {
      await commissionApi.createTreatmentFee({
        feeType: form.feeType,
        feeValue: form.feeValue,
        effectiveFrom: form.effectiveFrom,
        effectiveTo: form.effectiveTo || null,
      })
    }
    drawerOpen.value = false
    await load()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : 'บันทึกกฎไม่สำเร็จ'
  } finally {
    saving.value = false
  }
}

async function toggle(rule: TreatmentFeeRule) {
  pageError.value = ''
  try {
    await commissionApi.setTreatmentFeeEnabled(rule.id, !rule.enabled)
    await load()
  } catch (caught) {
    pageError.value = caught instanceof Error ? caught.message : 'เปลี่ยนสถานะกฎไม่สำเร็จ'
  }
}

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
          ไม่จำกัดด้วยค่าคอมมิชชัน ช่วงวันที่ของกฎที่เปิดใช้งานต้องไม่ซ้อนกัน
        </p>
      </section>
      <p v-if="pageError" class="page-error" role="alert">{{ pageError }}</p>
      <p v-if="loading">กำลังโหลดกฎค่ามือ...</p>
      <section class="rules">
        <article v-for="rule in rules" :key="rule.id" class="commission-card rule-card">
          <div>
            <b>{{ rule.feeType === 'Fixed' ? 'จำนวนเงินคงที่ต่อครั้ง' : 'เปอร์เซ็นต์' }}</b
            ><small
              >มีผล {{ thaiDate(rule.effectiveFrom) }} – {{ thaiDate(rule.effectiveTo) }} ·
              {{ rule.enabled ? 'ใช้งาน' : 'ปิดใช้งาน'
              }}<template v-if="rule.used"> · ถูกใช้คำนวณแล้ว</template></small
            >
          </div>
          <strong>{{ amountLabel(rule) }}</strong>
          <div class="rule-actions">
            <button class="commission-secondary" @click="openDrawer(rule)">แก้ไข</button
            ><button class="commission-secondary" @click="toggle(rule)">
              {{ rule.enabled ? 'ปิดใช้งาน' : 'เปิดใช้งาน' }}
            </button>
          </div>
        </article>
        <p v-if="!loading && rules.length === 0" class="empty">
          ยังไม่มีกฎค่ามือ การรักษาแทนจะบันทึกไม่ได้และค่ามือรายครั้งจะไม่ถูกบันทึกจนกว่าจะเพิ่มกฎ
        </p>
      </section>
      <button class="commission-primary add-rule" @click="openDrawer()">+ เพิ่มกฎค่ามือ</button>

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
    <Teleport to="body"
      ><div v-if="drawerOpen" class="rule-drawer-backdrop" @click.self="drawerOpen = false">
        <aside class="rule-drawer" role="dialog" aria-modal="true">
          <header>
            <div>
              <h2>{{ editingId ? 'แก้ไขกฎค่ามือ' : 'เพิ่มกฎค่ามือ' }}</h2>
              <p v-if="lockedValue">
                กฎนี้ถูกใช้คำนวณแล้ว แก้ได้เฉพาะวันสิ้นสุด ถ้าจะเปลี่ยนอัตราให้เพิ่มกฎใหม่
              </p>
              <p v-else>กำหนดวิธีคำนวณ อัตรา และวันที่มีผล</p>
            </div>
            <button class="drawer-close" @click="drawerOpen = false">×</button>
          </header>
          <div class="rule-form">
            <label
              >วิธีคำนวณ<select v-model="form.feeType" :disabled="lockedValue">
                <option value="Fixed">จำนวนเงินคงที่ต่อครั้ง</option>
                <option value="Percentage">เปอร์เซ็นต์จากมูลค่าคอร์สต่อครั้ง</option>
              </select></label
            ><label
              >{{ form.feeType === 'Fixed' ? 'จำนวนเงินต่อครั้ง (บาท)' : 'อัตรา (%)'
              }}<input
                v-model.number="form.feeValue"
                type="number"
                min="0"
                step="0.01"
                :disabled="lockedValue" /></label
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
          <p v-if="error" class="drawer-error" role="alert">{{ error }}</p>
          <footer>
            <button class="commission-secondary" @click="drawerOpen = false">ยกเลิก</button
            ><button class="commission-primary" :disabled="saving" @click="saveRule">
              {{ saving ? 'กำลังบันทึก...' : 'บันทึกกฎ' }}
            </button>
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
.rule-actions {
  display: flex;
  gap: 10px;
}
.add-rule {
  display: block;
  margin: 40px 0 0 auto;
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
  .add-rule {
    width: 100%;
  }
}
</style>
