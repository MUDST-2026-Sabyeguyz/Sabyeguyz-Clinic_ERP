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
  <div class="commission-manager-page"><StaffHeader /><main class="commission-manager-main paid-main"><RouterLink class="back-link" to="/staff/commission/adjustments">← กลับหน้าแรก</RouterLink><p v-if="loading" class="state-card">กำลังโหลดข้อมูลการจ่าย...</p><p v-else-if="errorMessage" class="state-card error">{{ errorMessage }}</p><template v-else-if="adjustment"><header><h1>จ่ายงวดแล้ว — {{ month }}</h1><p>Paid / Finalized · Applied {{ adjustment.amount < 0 ? '-' : '+' }}{{ money(amount) }} · ยอดเงินสุทธิ {{ money(amount) }}</p></header><section class="paid-card"><div><span>งวดและพนักงาน</span><b>{{ month }} · {{ adjustment.staffName }}</b><small>งวดต้นทางเป็น Paid</small></div><div><span>ยอดค่าตอบแทนก่อน Adjustment</span><b>{{ money(0) }}</b><small>ยังไม่รวม Adjustment นี้</small></div><div><span>Adjustment ที่จะนำไปใช้</span><b :class="{ negative: adjustment.amount < 0 }">{{ adjustment.amount < 0 ? '-' : '+' }}{{ money(amount) }}</b><small>Applied พร้อมยืนยันจ่ายงวดแล้ว · ไม่แก้ไขงวด Paid เดิม</small></div><div><span>หลักฐาน/ผู้ยินยอม</span><b>CONSENT-{{ String(adjustment.id).padStart(6, '0') }}</b><small>Manager อนุมัติ · มีหลักฐานก่อนนำยอดไปใช้จริง</small></div></section></template></main></div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.paid-main { padding-top: 28px; }.back-link { display: inline-flex; margin-bottom: 24px; color: #13766f; font-weight: 700; text-decoration: none; }.paid-main h1 { margin: 0; font-size: clamp(1.6rem, 2.5vw, 2rem); }.paid-main header p { margin: 7px 0 26px; color: #62746b; }.paid-card { display: grid; grid-template-columns: 1fr 1fr; gap: 18px 24px; padding: 24px; border: 1px solid #dce5e0; border-radius: 9px; background: #fff; }.paid-card div { display: grid; gap: 7px; }.paid-card span, .paid-card small { color: #718078; font-size: .8rem; }.paid-card b { color: #263a31; }.paid-card .negative { color: #c24742; } @media (max-width: 680px) { .paid-card { grid-template-columns: 1fr; } }
</style>
