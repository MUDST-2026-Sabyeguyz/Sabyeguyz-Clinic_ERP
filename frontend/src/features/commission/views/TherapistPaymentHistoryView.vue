<script setup lang="ts">
import TherapistCompensationPaymentHistory from '../components/TherapistCompensationPaymentHistory.vue'
import TherapistCompensationShell from '../components/TherapistCompensationShell.vue'
import { useTherapistPay } from '../lib/therapistPay'

const { payments, adjustments, month, loading, errorMessage } = useTherapistPay()
</script>
<template>
  <TherapistCompensationShell
    title="ประวัติการจ่าย"
    subtitle="ตรวจสอบ Commission ค่ามือ และ Adjustment ที่จ่ายแล้วในแต่ละงวด"
    active-tab="payments"
    :error="errorMessage"
    :loading="loading"
  >
    <TherapistCompensationPaymentHistory :payments="payments" :adjustments="adjustments" :month="month" />
  </TherapistCompensationShell>
</template>
