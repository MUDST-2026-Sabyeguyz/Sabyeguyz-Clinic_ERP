<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi } from '@/api/client'
import { clinicToday } from '@/utils/clinicDate'
import { baht2, recordedAt, versionCycles, versionStatus } from '../commissionVersion'

// History of the commission rules: every Scheme Version, newest first, and the tiers of the one picked (read only).
type SchemeVersion = {
  id: number
  version: number
  effectiveFrom: string
  effectiveTo: string | null
  createdBy: string | null
  createdAt: string | null
  updatedAt: string | null
  updatedBy: string | null
}
type VersionTier = { order: number; minimum: number; maximum: number | null; rate: number }

const versions = ref<SchemeVersion[]>([])
const selectedId = ref<number | null>(null)
const tiersOf = reactive<Record<number, VersionTier[]>>({})
const loading = ref(true)
const error = ref('')

const thaiShort = (value: string) =>
  new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'short', year: 'numeric' }).format(
    new Date(`${value}T12:00:00`),
  )
function period(item: SchemeVersion) {
  if (!item.effectiveTo) return `${thaiShort(item.effectiveFrom)} เป็นต้นไป`
  return item.effectiveFrom.slice(0, 7) === item.effectiveTo.slice(0, 7)
    ? `${Number(item.effectiveFrom.slice(8))} – ${thaiShort(item.effectiveTo)}`
    : `${thaiShort(item.effectiveFrom)} – ${thaiShort(item.effectiveTo)}`
}

const selected = computed(() => versions.value.find((item) => item.id === selectedId.value) ?? null)

async function select(item: SchemeVersion) {
  selectedId.value = item.id
  if (tiersOf[item.id]) return
  try {
    const rows = (await commissionApi.tiers(item.id)) as VersionTier[]
    tiersOf[item.id] = rows
      .map(({ order, minimum, maximum, rate }) => ({ order, minimum, maximum, rate }))
      .sort((a, b) => a.order - b.order)
  } catch {
    tiersOf[item.id] = []
  }
}

onMounted(async () => {
  try {
    versions.value = (await commissionApi.schemes()) as SchemeVersion[]
    // open the version in force first, otherwise the newest
    const first = versions.value.find((item) => versionStatus(item).tone === 'current') ?? versions.value[0]
    if (first) await select(first)
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
      <RouterLink class="history-back" to="/staff/settings?section=tiers">← กลับการตั้งค่าคอมมิชชัน</RouterLink>
      <header class="history-title">
        <div>
          <h1>ประวัติเวอร์ชันกฎคอมมิชชัน</h1>
          <p>Commission Tier — การขายคอร์ส</p>
        </div>
        <small>ข้อมูล ณ {{ thaiShort(clinicToday()) }}</small>
      </header>

      <p v-if="error" class="history-error" role="alert">{{ error }}</p>
      <p v-else-if="loading">กำลังโหลดประวัติเวอร์ชัน...</p>
      <p v-else-if="versions.length === 0" class="history-empty">ยังไม่มีเวอร์ชันของกฎคอมมิชชัน</p>

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
              <tr v-for="item in versions" :key="item.id" :class="{ picked: item.id === selectedId }">
                <td>
                  <b>V{{ item.version }}</b>
                </td>
                <td>{{ period(item) }}</td>
                <td>
                  <span class="version-status" :class="versionStatus(item).tone">{{ versionStatus(item).label }}</span>
                </td>
                <td>{{ item.updatedBy ?? item.createdBy ?? '-' }}</td>
                <td>{{ recordedAt(item.updatedAt ?? item.createdAt) }}</td>
                <td>
                  <button type="button" class="version-link" @click="select(item)">ดูรายละเอียด</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-if="selected" class="history-card detail">
        <header>
          <h2>รายละเอียดเวอร์ชัน V{{ selected.version }}</h2>
          <span class="readonly">อ่านอย่างเดียว</span>
        </header>
        <p class="detail-cycle">{{ versionCycles(selected) }}</p>
        <p v-if="!tiersOf[selected.id]">กำลังโหลด...</p>
        <p v-else-if="tiersOf[selected.id]!.length === 0" class="history-empty">ไม่มี Tier ในเวอร์ชันนี้</p>
        <div v-else class="history-scroll">
          <table class="history-table tiers">
            <thead>
              <tr>
                <th>Tier</th>
                <th>ยอดขายขั้นต่ำ (บาท)</th>
                <th>ยอดขายสูงสุด (บาท)</th>
                <th>คอมมิชชัน</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="tier in tiersOf[selected.id]" :key="tier.order">
                <td>{{ tier.order }}</td>
                <td>{{ baht2(tier.minimum) }}</td>
                <td>{{ tier.maximum === null ? 'ไม่จำกัด' : baht2(tier.maximum) }}</td>
                <td>{{ Math.round(tier.rate * 10000) / 100 }}%</td>
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
.tiers th,
.tiers td {
  text-align: center;
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
