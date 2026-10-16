<script setup lang="ts">
import { useRoute } from 'vue-router'
import TherapistCompensationDetail from '../components/TherapistCompensationDetail.vue'
import TherapistCompensationShell from '../components/TherapistCompensationShell.vue'
import { useTherapistLine } from '../lib/therapistPay'

const route = useRoute()
const { line, month, loading, errorMessage } = useTherapistLine(() => String(route.params.id ?? ''))
</script>
<template>
  <TherapistCompensationShell
    title="รายละเอียดค่าคอมมิชชัน"
    subtitle="ตรวจสอบฐานคำนวณ ส่วนลด และ Tier ของงวด · อ่านอย่างเดียว"
    active-tab="commission"
    :error="errorMessage"
    :loading="loading"
  >
    <TherapistCompensationDetail kind="commission" :line="line" :month="month" />
  </TherapistCompensationShell>
</template>
