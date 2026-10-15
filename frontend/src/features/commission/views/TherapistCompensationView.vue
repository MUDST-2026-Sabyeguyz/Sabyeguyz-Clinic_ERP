<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import TherapistCompensationDetail from '../components/TherapistCompensationDetail.vue'
import TherapistCompensationOverview from '../components/TherapistCompensationOverview.vue'
import TherapistCompensationPaymentHistory from '../components/TherapistCompensationPaymentHistory.vue'
import TherapistCompensationTabs from '../components/TherapistCompensationTabs.vue'
import {
  EMPTY_COMPENSATION_COPY,
  resolveTherapistCompensationTab,
} from '../types/therapistCompensation'
const route = useRoute()
const activeTab = computed(() => resolveTherapistCompensationTab(route.query.tab))
const heading = computed(
  () =>
    ({
      overview: 'ค่าตอบแทนของฉัน',
      commission: 'ค่าคอมมิชชัน',
      treatment: 'ค่ามือ',
      payments: 'ประวัติการจ่าย',
    })[activeTab.value],
)
</script>
<template>
  <div class="therapist-compensation-page">
    <StaffHeader />
    <main class="therapist-compensation-main">
      <header class="therapist-compensation-heading">
        <div>
          <h1>{{ heading }}</h1>
          <p>ข้อมูลค่าตอบแทนสำหรับนักกายภาพบำบัด</p>
        </div>
        <span class="therapist-status-pill">รอตรวจสอบ</span>
      </header>
      <TherapistCompensationTabs :active-tab="activeTab" /><TherapistCompensationOverview
        v-if="activeTab === 'overview'"
      /><TherapistCompensationDetail
        v-else-if="activeTab === 'commission'"
        kind="commission"
      /><TherapistCompensationDetail
        v-else-if="activeTab === 'treatment'"
        kind="treatment"
      /><TherapistCompensationPaymentHistory v-else /><span class="sr-only">{{
        EMPTY_COMPENSATION_COPY
      }}</span>
    </main>
  </div>
</template>
<style src="../styles/therapist-compensation-workspace.css"></style>
