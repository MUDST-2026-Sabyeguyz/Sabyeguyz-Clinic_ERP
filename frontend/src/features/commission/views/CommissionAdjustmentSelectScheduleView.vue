<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi, type ManagerAdjustment } from '@/api/client'

const route = useRoute()
const adjustment = ref<ManagerAdjustment | null>(null)
const loading = ref(true)
const errorMessage = ref('')
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
  <div class="commission-manager-page"><StaffHeader /><main class="commission-manager-main choice-main"><RouterLink class="back-link" :to="{ name: 'commission-adjustment-detail', params: { id: route.params.id } }">← กลับรายการ Adjustment</RouterLink><p v-if="loading" class="state-card">กำลังโหลดข้อมูลงวด...</p><p v-else-if="errorMessage" class="state-card error">{{ errorMessage }}</p><template v-else-if="adjustment"><header><h1>เลือกงวดที่จะดำเนินการ</h1><p>ชื่อรายการ Adjustment · {{ adjustment.staffName }} · เลือกงวดดำเนินการ {{ month }}</p></header><section class="choice-card"><h2>สถานะระบบของงวดปลายทาง</h2><div><RouterLink class="active" :to="{ name: 'commission-adjustment-schedule', params: { id: adjustment.id } }">{{ month }} · Open</RouterLink><RouterLink :to="{ name: 'commission-adjustment-closed', params: { id: adjustment.id } }">{{ month }} · Closed</RouterLink><RouterLink :to="{ name: 'commission-adjustment-finalized', params: { id: adjustment.id } }">งวดก่อนหน้า · Paid / Finalized</RouterLink></div></section></template></main></div>
</template>

<style scoped>
@import '../styles/commission-manager.css';
.choice-main { padding-top: 28px; }.back-link { display: inline-flex; margin-bottom: 25px; color: #13766f; font-weight: 700; text-decoration: none; }.choice-main h1 { margin: 0; font-size: clamp(1.6rem, 2.5vw, 2rem); }.choice-main header p { margin: 6px 0 20px; color: #63746c; font-size: .9rem; }.choice-card { min-height: 64px; padding: 20px; border: 1px solid #dce5e0; border-radius: 9px; background: #fff; }.choice-card h2 { margin: 0; font-size: 1rem; }.choice-card div { display: flex; justify-content: flex-end; gap: 12px; margin-top: 10px; }.choice-card button, .choice-card a { display: inline-flex; align-items: center; min-height: 38px; padding: 0 16px; border: 1px solid #218d87; border-radius: 7px; background: #fff; color: #197b75; font: inherit; font-size: .85rem; text-decoration: none; cursor: pointer; }.choice-card .active { background: #218d87; color: #fff; } @media (max-width: 680px) { .choice-card div { flex-wrap: wrap; justify-content: flex-start; } }
</style>
