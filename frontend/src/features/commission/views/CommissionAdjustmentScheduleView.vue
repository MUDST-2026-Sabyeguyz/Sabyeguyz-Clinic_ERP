<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi, type ManagerAdjustment } from '@/api/client'

const route = useRoute()
const router = useRouter()
const adjustment = ref<ManagerAdjustment | null>(null)
const canSchedule = computed(() => !!adjustment.value && adjustment.value.remaining > 0 && ['Approved', 'PartiallyApplied'].includes(adjustment.value.status))
const loading = ref(true)
const errorMessage = ref('')
const appliedAmount = ref('')
const note = ref('')
const monthLabel = computed(() => {
  const value = adjustment.value?.appliedMonth
  if (!value) return 'งวดถัดไป'
  const [year, month] = value.split('-').map(Number)
  if (!year || !month) return value
  return new Intl.DateTimeFormat('th-TH', { month: 'long', year: 'numeric' }).format(
    new Date(year, month - 1, 1),
  )
})
async function load() {
  try { adjustment.value = await commissionApi.adjustment(Number(route.params.id)) }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : 'ไม่พบรายการ Adjustment' }
  finally { loading.value = false }
}
function review() {
  if (!canSchedule.value) return
  void router.push({
    name: 'commission-adjustment-review',
    params: { id: String(route.params.id) },
    query: { amount: appliedAmount.value, note: note.value },
  })
}
onMounted(load)
</script>

<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main schedule-main">
      <RouterLink class="back-link" :to="{ name: 'commission-adjustment-detail', params: { id: route.params.id } }">← กลับรายละเอียด Adjustment</RouterLink>
      <p v-if="loading" class="state-card">กำลังโหลดข้อมูลงวด...</p>
      <p v-else-if="errorMessage" class="state-card error">{{ errorMessage }}</p>
      <p v-else-if="!canSchedule" class="state-card" role="status">รายการนี้ไม่สามารถกำหนดงวดได้</p>
      <template v-else-if="adjustment">
        <header><h1>กำหนดยอดดำเนินการในงวด</h1><p>หมายเลข Z · {{ monthLabel }} · เลือกยอดดำเนินการ</p></header>
        <section class="schedule-metrics"><article><span>Adjustment คงเหลือ</span><b>{{ adjustment.amount < 0 ? '-' : '+' }}฿{{ Math.abs(adjustment.remaining).toLocaleString('th-TH', { minimumFractionDigits: 2 }) }}</b><small>ยอดอ้างอิงจากงวด Paid / Finalized</small></article><article><span>ดำเนินการแล้ว</span><b>฿{{ adjustment.processed.toLocaleString('th-TH', { minimumFractionDigits: 2 }) }}</b><small>ยอดที่บันทึกดำเนินการ</small></article><article><span>คงเหลือหลังดำเนินการ</span><b>{{ adjustment.amount < 0 ? '-' : '+' }}฿{{ Math.abs(adjustment.remaining).toLocaleString('th-TH', { minimumFractionDigits: 2 }) }}</b><small>ยอดคงเหลือในรายการ</small></article></section>
        <section class="schedule-card"><h2>กำหนดยอดสำหรับงวดถัดไป</h2><div class="schedule-form"><label>งวดที่นำไปใช้<select><option>{{ monthLabel }} · Open</option></select><small>สถานะงวดพร้อมดำเนินการ</small></label><label>ยอดดำเนินการในงวดนี้ *<input v-model="appliedAmount" type="number" min="0.01" step="0.01" :placeholder="adjustment.remaining.toFixed(2)" /><small>ไม่เกินยอดคงเหลือ ฿{{ adjustment.remaining.toLocaleString('th-TH', { minimumFractionDigits: 2 }) }}</small></label><label class="wide">เหตุผลการดำเนินการ *<input v-model="note" placeholder="ดำเนินการตามยอดคงเหลือ ฿0.00 ในงวดถัดไป" /></label><label class="wide">เงื่อนไขสำหรับการหักเงิน *<select><option>CONSENT-1</option></select><small>จำเป็นต้องระบุเมื่อลด/หักค่าตอบแทน</small></label></div><p class="schedule-hint">หากดำเนินการครบ ฿{{ adjustment.remaining.toLocaleString('th-TH', { minimumFractionDigits: 2 }) }} ยอดคงเหลือของ Adjustment จะเป็น ฿0.00</p></section>
        <footer class="schedule-actions"><RouterLink class="commission-secondary" :to="{ name: 'commission-adjustment-detail', params: { id: route.params.id } }">กลับรายละเอียด</RouterLink><button class="commission-primary" type="button" @click="review">ตรวจสอบยอดที่กำหนด</button></footer>
      </template>
    </main>
  </div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.schedule-main { padding-top: 28px; }.back-link { display: inline-flex; margin-bottom: 25px; color: #13766f; font-weight: 700; text-decoration: none; }.schedule-main h1 { margin: 0; font-size: clamp(1.6rem, 2.5vw, 2rem); }.schedule-main header p { margin: 6px 0 20px; color: #63746c; font-size: .9rem; }.schedule-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 14px; }.schedule-metrics article { padding: 15px; border: 1px solid #dce5e0; border-radius: 8px; background: #fff; }.schedule-metrics span, .schedule-metrics small { display: block; color: #6a7a72; font-size: .78rem; }.schedule-metrics b { display: block; margin: 5px 0; font-size: 1.15rem; }.schedule-card { padding: 20px; border: 1px solid #dce5e0; border-radius: 9px; background: #fff; }.schedule-card h2 { margin: 0 0 16px; font-size: 1rem; }.schedule-form { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }.schedule-form label { display: grid; gap: 8px; color: #263a31; font-weight: 700; }.schedule-form .wide { grid-column: 1 / -1; }.schedule-form input, .schedule-form select { min-height: 46px; padding: 0 13px; border: 1px solid #d6e0dc; border-radius: 6px; background: #fff; color: #263a31; font: inherit; }.schedule-form small { color: #728179; font-size: .78rem; font-weight: 400; }.schedule-hint { margin: 16px 0 0; padding: 12px; border-radius: 6px; background: #fff0bf; color: #65551e; }.schedule-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 18px; }.schedule-actions a { display: inline-flex; align-items: center; text-decoration: none; } @media (max-width: 680px) { .schedule-metrics, .schedule-form { grid-template-columns: 1fr; }.schedule-form .wide { grid-column: auto; }.schedule-actions { flex-direction: column-reverse; }.schedule-actions > * { justify-content: center; } }
</style>
