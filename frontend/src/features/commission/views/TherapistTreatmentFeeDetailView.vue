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
    title="รายละเอียดค่ามือ"
    subtitle="รายละเอียดจากรายการรักษาจริง · ระบบคำนวณให้อัตโนมัติและไม่สามารถแก้ไขโดยตรง"
    active-tab="treatment"
    :error="errorMessage"
    :loading="loading"
  >
    <TherapistCompensationDetail kind="treatment" :line="line" :month="month" />
  </TherapistCompensationShell>
</template>
