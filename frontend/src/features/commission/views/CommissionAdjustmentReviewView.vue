<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi, type ManagerAdjustment } from '@/api/client'

const route = useRoute()
const adjustment = ref<ManagerAdjustment | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const amount = computed(() => Number(route.query.amount || adjustment.value?.remaining || 0))
const money = (value: number) => `฿${Math.abs(value).toLocaleString('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const month = computed(() => {
  const value = adjustment.value?.appliedMonth
  if (!value) return 'งวดถัดไป'
  const [year, number] = value.split('-').map(Number)
  if (!year || !number) return value
  return new Intl.DateTimeFormat('th-TH', { month: 'long', year: 'numeric' }).format(new Date(year, number - 1, 1))
})
async function load() {
  try { adjustment.value = await commissionApi.adjustment(Number(route.params.id)) }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : 'ไม่พบรายการ Adjustment' }
  finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main review-main">
      <p v-if="loading" class="state-card">กำลังตรวจสอบยอด...</p>
      <p v-else-if="errorMessage" class="state-card error">{{ errorMessage }}</p>
      <template v-else-if="adjustment">
        <header><h1>ตรวจสรุปจ่ายค่าตอบแทน — {{ month }}</h1><p>หมายเลข Z · Closed / Pending Payment · ยอดเงินสุทธิ {{ money(amount) }}</p></header>
        <section class="review-card"><div><span>งวดและพนักงาน</span><b>{{ month }} · {{ adjustment.staffName }}</b><small>งวดต้นทางเป็น Paid</small></div><div><span>ยอดค่าตอบแทนก่อน Adjustment</span><b>{{ money(0) }}</b><small>ยังไม่รวม Adjustment นี้</small></div><div><span>Adjustment ที่จะนำไปใช้</span><b :class="{ negative: adjustment.amount < 0 }">{{ adjustment.amount < 0 ? '-' : '+' }}{{ money(amount) }}</b><small>Draft: ยังไม่ถือว่าเป็นยอดดำเนินการแล้ว</small></div><div><span>หลักฐาน/ผู้ยินยอม</span><b>CONSENT-{{ String(adjustment.id).padStart(6, '0') }}</b><small>Manager อนุมัติ · ต้องมีหลักฐานก่อนนำยอดไปใช้จริง</small></div></section>
        <footer><RouterLink class="commission-primary" :to="{ name: 'commission-adjustment-payment-success', params: { id: adjustment.id }, query: { amount } }">ยืนยันจ่ายงวดนี้</RouterLink></footer>
      </template>
    </main>
  </div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.review-main { padding-top: 40px; }.review-main h1 { margin: 0; font-size: clamp(1.6rem, 2.5vw, 2rem); }.review-main header p { margin: 7px 0 26px; color: #62746b; }.review-card { display: grid; grid-template-columns: 1fr 1fr; gap: 18px 24px; padding: 24px; border: 1px solid #dce5e0; border-radius: 9px; background: #fff; }.review-card div { display: grid; gap: 7px; }.review-card span, .review-card small { color: #718078; font-size: .8rem; }.review-card b { color: #263a31; }.review-card .negative { color: #c24742; }.review-main footer { display: flex; justify-content: flex-end; margin-top: 28px; }.review-main footer .commission-primary { display: inline-flex; align-items: center; min-width: 170px; text-decoration: none; } @media (max-width: 680px) { .review-card { grid-template-columns: 1fr; }.review-main footer .commission-primary { width: 100%; justify-content: center; } }
</style>
