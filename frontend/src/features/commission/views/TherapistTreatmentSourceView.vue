<script setup lang="ts">
import { useRoute } from 'vue-router'
import TherapistCompensationShell from '../components/TherapistCompensationShell.vue'
import TherapistCompensationSourceDetail from '../components/TherapistCompensationSourceDetail.vue'
import { useTherapistLine } from '../lib/therapistPay'

const route = useRoute()
const { line, month, loading, errorMessage } = useTherapistLine(() => String(route.params.id ?? ''))
</script>
<template>
  <TherapistCompensationShell
    title=""
    subtitle=""
    active-tab="treatment"
    :tabs="false"
    :error="errorMessage"
    :loading="loading"
  >
    <TherapistCompensationSourceDetail kind="treatment" :line="line" :month="month" />
  </TherapistCompensationShell>
</template>
