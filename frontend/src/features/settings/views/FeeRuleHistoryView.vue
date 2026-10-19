<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi, type TreatmentFeeRule } from '@/api/client'
import { clinicToday } from '@/utils/clinicDate'
import { recordedAt, versionCycles, versionStatus } from '../commissionVersion'

// History of the treatment fee rule: every rule is a version (V1, V2… by start date), read only.
type FeeVersion = { number: number; rule: TreatmentFeeRule }

const versions = ref<FeeVersion[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(true)
const error = ref('')

const thaiShort = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'short', year: 'numeric' }).format(
    new Date(`${value}T12:00:00`),
  )
const bahtText = (value: number) => Number(value).toLocaleString('th-TH', { maximumFractionDigits: 2 })
const range = (rule: TreatmentFeeRule) => ({ effectiveFrom: rule.effectiveFrom, effectiveTo: rule.effectiveTo })
function period(rule: TreatmentFeeRule) {
  if (!rule.effectiveTo) return `${thaiShort(rule.effectiveFrom)} เป็นต้นไป`
  return rule.effectiveFrom.slice(0, 7) === rule.effectiveTo.slice(0, 7)
    ? `${Number(rule.effectiveFrom.slice(8))} – ${thaiShort(rule.effectiveTo)}`
    : `${thaiShort(rule.effectiveFrom)} – ${thaiShort(rule.effectiveTo)}`
}
const calculation = (rule: TreatmentFeeRule) =>
  rule.feeType === 'Fixed'
    ? `จำนวนเงินคงที่ ${bahtText(rule.feeValue)} บาท / ครั้ง`
    : `เปอร์เซ็นต์ ${bahtText(rule.feeValue)}% ของยอดรายการ`

const selected = computed(() => versions.value.find((item) => item.rule.id === selectedId.value) ?? null)

onMounted(async () => {
  try {
    const rules = (await commissionApi.treatmentFees()) as TreatmentFeeRule[]
    // newest version first, numbered from the oldest
    versions.value = rules
      .slice()
      .sort((a, b) => a.effectiveFrom.localeCompare(b.effectiveFrom))
      .map((rule, index) => ({ number: index + 1, rule }))
      .reverse()
    // open the version in force first, otherwise the newest
    const first = versions.value.find((item) => versionStatus(range(item.rule)).tone === 'current') ?? versions.value[0]
    selectedId.value = first?.rule.id ?? null
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : 'โหลดประวัติเวอร์ชันไม่สำเร็จ'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="history-page">
    <StaffHeader />
    <main class="history-main">
      <RouterLink class="history-back" to="/staff/settings?section=commission">← กลับการตั้งค่ากฎค่ามือ</RouterLink>
      <header class="history-title">
        <div>
          <h1>ประวัติเวอร์ชันกฎค่ามือ</h1>
          <p>ค่ามือผู้รักษาแทนและการรักษารายครั้ง</p>
        </div>
        <small>ข้อมูล ณ {{ thaiShort(clinicToday()) }}</small>
      </header>

      <p v-if="error" class="history-error" role="alert">{{ error }}</p>
      <p v-else-if="loading">กำลังโหลดประวัติเวอร์ชัน...</p>
      <p v-else-if="versions.length === 0" class="history-empty">ยังไม่มีกฎค่ามือ</p>

      <section v-else class="history-card">
        <div class="history-scroll">
          <table class="history-table versions">
            <thead>
              <tr>
                <th>เวอร์ชัน</th>
                <th>ช่วงที่มีผล</th>
                <th>สถานะ</th>
                <th>ผู้บันทึกล่าสุด</th>
                <th>แก้ไขล่าสุด</th>
                <th>รายละเอียด</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in versions" :key="item.rule.id" :class="{ picked: item.rule.id === selectedId }">
                <td>
                  <b>V{{ item.number }}</b>
                </td>
                <td>{{ period(item.rule) }}</td>
                <td>
                  <span class="version-status" :class="versionStatus(range(item.rule)).tone">{{
                    versionStatus(range(item.rule)).label
                  }}</span>
                </td>
                <td>{{ item.rule.lastEditedBy ?? '-' }}</td>
                <td>{{ recordedAt(item.rule.lastEditedAt) }}</td>
                <td>
                  <button type="button" class="version-link" @click="selectedId = item.rule.id">ดูรายละเอียด</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-if="selected" class="history-card detail">
        <header>
          <h2>รายละเอียดเวอร์ชัน V{{ selected.number }}</h2>
          <span class="readonly">อ่านอย่างเดียว</span>
        </header>
        <p class="detail-cycle">{{ versionCycles(range(selected.rule)) }}</p>
        <div class="history-scroll">
          <table class="history-table tiers">
            <thead>
              <tr>
                <th>ชื่อกฎ</th>
                <th>ผู้ได้รับค่ามือ</th>
                <th>วิธีคำนวณ</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>{{ selected.rule.name?.trim() || '-' }}</td>
                <td>นักกายภาพทั้งหมด</td>
                <td>{{ calculation(selected.rule) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="detail-note">ⓘ ประวัติใช้แสดงกฎเดิม ไม่สามารถแก้ไขจากหน้านี้</p>
      </section>
    </main>
  </div>
</template>

<style scoped>
@import '../../staff/styles/staff.css';
.history-page {
  min-height: 100vh;
  color: #25372e;
  background: #f8f4ed;
}
.history-main {
  width: min(1180px, calc(100% - 48px));
  margin: auto;
  padding: 28px 0 80px;
}
.history-back {
  color: #1e807c;
  font-weight: 600;
  text-decoration: none;
}
.history-title {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin: 18px 0 24px;
}
.history-title h1 {
  margin: 0;
  font-size: clamp(1.8rem, 3.4vw, 2.6rem);
  line-height: 1.15;
}
.history-title p {
  margin: 6px 0 0;
  color: #53635b;
  font-size: 1.15rem;
}
.history-title small {
  color: #68776f;
  white-space: nowrap;
}
.history-card {
  margin-bottom: 22px;
  padding: 18px;
  border-radius: 16px;
  background: #fff;
}
.history-scroll {
  overflow-x: auto;
}
.history-table {
  width: 100%;
  border-collapse: collapse;
}
.history-table th,
.history-table td {
  padding: 14px 10px;
  text-align: left;
  white-space: nowrap;
}
.versions td:nth-child(2),
.versions td:nth-child(5) {
  white-space: normal;
}
.history-table thead th {
  background: #f1f4f2;
  font-weight: 700;
}
.history-table tbody tr + tr td {
  border-top: 1px solid #e6ece8;
}
.versions tr.picked td {
  background: #e8f4f1;
}
.version-status {
  display: inline-block;
  min-width: 90px;
  padding: 6px 16px;
  border-radius: 999px;
  text-align: center;
  font-size: 0.9rem;
  font-weight: 600;
}
.version-status.current {
  background: #1e9a8d;
  color: #fff;
}
.version-status.pending {
  background: #fdd791;
  color: #7a4d00;
}
.version-status.ended {
  background: #e7eae8;
  color: #55635c;
}
.version-link {
  padding: 0;
  border: 0;
  background: none;
  color: #1e807c;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.detail header {
  display: flex;
  align-items: center;
  gap: 12px;
}
.detail h2 {
  margin: 0;
}
.readonly {
  padding: 4px 14px;
  border-radius: 999px;
  background: #e7eae8;
  color: #55635c;
  font-size: 0.85rem;
}
.detail-cycle {
  margin: 8px 0 16px;
  color: #68776f;
}
.tiers {
  border: 1px solid #e6ece8;
}
.detail-note {
  margin: 16px 0 0;
  padding: 12px 16px;
  border-radius: 8px;
  background: #f4f7f5;
  color: #53635c;
  font-size: 0.9rem;
}
.history-error {
  color: #b42318;
}
.history-empty {
  color: #68776f;
}
@media (max-width: 700px) {
  .history-title {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
