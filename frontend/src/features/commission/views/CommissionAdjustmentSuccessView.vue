<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi, type ManagerAdjustment } from '@/api/client'

const route = useRoute()
const adjustment = ref<ManagerAdjustment | null>(null)
const errorMessage = ref('')
const loading = ref(true)
const money = (value: number) =>
  `${value < 0 ? '-' : '+'}฿${Math.abs(value).toLocaleString('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const remaining = computed(() => (adjustment.value ? Math.sign(adjustment.value.amount) * adjustment.value.remaining : 0))
const source = computed(() => {
  const row = adjustment.value
  if (!row) return '—'
  if (row.visitId) return `VIS-${String(row.visitId).padStart(4, '0')}`
  if (row.courseId) return `COURSE-${String(row.courseId).padStart(5, '0')}`
  if (row.allocationId) return `COM-${String(row.allocationId).padStart(5, '0')}`
  return `ADJ-${String(row.id).padStart(5, '0')}`
})
const month = computed(() => {
  const value = adjustment.value?.appliedMonth
  if (!value) return '—'
  const [year, number] = value.split('-').map(Number)
  if (!year || !number) return value
  return new Intl.DateTimeFormat('th-TH', { month: 'long', year: 'numeric' }).format(new Date(year, number - 1, 1))
})
async function load() {
  try {
    adjustment.value = await commissionApi.adjustment(Number(route.params.id))
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'ไม่พบรายการ Adjustment'
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="commission-manager-page adjustment-success-page">
    <StaffHeader />
    <main class="commission-manager-main success-main">
      <RouterLink class="back-link" to="/staff/commission/adjustments">← กลับรายการ Adjustment</RouterLink>
      <p v-if="loading" class="state-card">กำลังโหลดรายการ Adjustment...</p>
      <p v-else-if="errorMessage" class="state-card error">{{ errorMessage }}</p>
      <template v-else-if="adjustment">
        <header class="success-title"><h1>บันทึก Adjustment สำเร็จ</h1><p>ADJ-{{ String(adjustment.id).padStart(5, '0') }} · {{ adjustment.staffName }}</p></header>
        <section class="success-metrics">
          <article><span>Adjustment ที่บันทึก</span><b>{{ money(adjustment.amount) }}</b><small>อ้างอิงงวด Paid / Finalized</small></article>
          <article><span>ดำเนินการแล้ว</span><b>{{ money(Math.sign(adjustment.amount) * adjustment.processed) }}</b><small>ยังไม่ได้บันทึกดำเนินการ</small></article>
          <article><span>ยอดคงเหลือ</span><b>{{ money(remaining) }}</b><small>ในรอบถัดไป</small></article>
        </section>
        <section class="success-card"><h2>ข้อมูลที่บันทึก</h2><dl><div><dt>งวดที่จะนำไปใช้</dt><dd>{{ month }} · Open</dd></div><div><dt>รายการอ้างอิง</dt><dd>{{ source }}</dd></div><div><dt>ผู้สร้าง</dt><dd>Manager · บันทึกจากระบบ</dd></div></dl><p>{{ adjustment.reason }}</p></section>
        <footer class="success-actions"><RouterLink class="commission-secondary" to="/staff/commission/adjustments">กลับรายการ</RouterLink><RouterLink class="commission-primary" :to="{ name: 'commission-adjustment-detail', params: { id: adjustment.id } }">ดูรายละเอียดรายการนี้</RouterLink></footer>
      </template>
    </main>
  </div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.success-main { padding-top: 28px; }.back-link { display: inline-flex; margin-bottom: 26px; color: #13766f; font-weight: 700; text-decoration: none; }.success-title h1 { margin: 0; font-size: clamp(1.75rem, 2.7vw, 2.25rem); }.success-title p { margin: 7px 0 20px; color: #63746c; }.success-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }.success-metrics article, .success-card { border: 1px solid #dce5e0; border-radius: 8px; background: #fff; }.success-metrics article { padding: 16px; }.success-metrics span, .success-metrics small { display: block; color: #65776e; font-size: .82rem; }.success-metrics b { display: block; margin: 5px 0; color: #283a32; font-size: 1.2rem; }.success-card { margin-top: 14px; padding: 20px; }.success-card h2 { margin: 0 0 12px; font-size: 1rem; }.success-card dl { display: grid; gap: 12px; margin: 0; }.success-card dl div { display: flex; justify-content: space-between; gap: 20px; }.success-card dt { color: #687970; }.success-card dd { margin: 0; }.success-card p { margin: 18px 0 0; font-weight: 700; }.success-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 18px; }.success-actions a { display: inline-flex; align-items: center; text-decoration: none; } @media (max-width: 650px) { .success-metrics { grid-template-columns: 1fr; }.success-actions { flex-direction: column-reverse; }.success-actions a { justify-content: center; } }
</style>
