<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getDashboardSummary, type DashboardSummary } from '@/api/reports'

const summary = ref<DashboardSummary | null>(null)
const loading = ref(true)
const error = ref('')
const today = new Date()
const from = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-01`
const to = today.toISOString().slice(0, 10)
onMounted(async () => {
  try { summary.value = await getDashboardSummary(from, to) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'โหลดข้อมูลไม่สำเร็จ' }
  finally { loading.value = false }
})
</script>

<template>
  <section class="page-shell"><header class="page-header"><div><p class="eyebrow">ภาพรวมการดำเนินงาน</p><h1>Dashboard</h1></div><span class="muted">{{ from }} ถึง {{ to }}</span></header>
    <p v-if="loading">กำลังโหลดข้อมูล…</p><p v-else-if="error" class="error" role="alert">{{ error }}</p>
    <div v-else-if="summary" class="form-grid">
      <article class="form-card"><strong>ผู้ป่วยที่มารับบริการ</strong><h2>{{ summary.uniquePatients }}</h2></article>
      <article class="form-card"><strong>จำนวน Visits</strong><h2>{{ summary.visits }}</h2></article>
      <article class="form-card"><strong>รายรับสุทธิ</strong><h2>฿{{ summary.totalRevenue.toLocaleString() }}</h2></article>
      <article class="form-card"><strong>ใบเสร็จ</strong><h2>{{ summary.receiptsIssued }}</h2><span class="muted">ยกเลิก {{ summary.receiptsVoided }}</span></article>
    </div>
  </section>
</template>
