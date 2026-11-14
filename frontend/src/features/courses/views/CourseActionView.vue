<script setup lang="ts">
import { provide } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import AppointmentProcessPanel from '@/features/appointments/components/AppointmentProcessPanel.vue'
import CourseUsePanel from '../components/CourseUsePanel.vue'
import CourseTransferPanel from '../components/CourseTransferPanel.vue'
import CourseHistoryPanel from '../components/CourseHistoryPanel.vue'
import CourseManagePanel from '../components/CourseManagePanel.vue'
import { courseActionKey, useCourseAction } from '../useCourseAction'

const context = useCourseAction()
provide(courseActionKey, context)
// เปิดสถานะทั้งหมดให้เทสต์และเครื่องมือดีบักเข้าถึงได้เหมือนเดิม
defineExpose(context)
const {
  action,
  appointmentNeedsReview,
  appointmentProgress,
  completedUsageRedirect,
  processBlockReason,
  processError,
  processLoading,
  recoveryRequired,
  refreshAppointmentProcess,
  usageSubmitting,
} = context
</script>

<template>
  <div class="staff-page course-page">
    <StaffHeader />
    <p v-if="usageSubmitting" role="status">กำลังบันทึกรายการ…</p>
    <p v-else-if="processLoading || completedUsageRedirect" role="status">กำลังตรวจสอบนัดหมาย…</p>
    <div v-else-if="processError" role="alert">
      <p>{{ processError }}</p>
      <button type="button" @click="refreshAppointmentProcess">ลองใหม่</button>
    </div>
    <AppointmentProcessPanel
      v-else-if="appointmentProgress && appointmentNeedsReview"
      :progress="appointmentProgress"
      :block-reason="processBlockReason"
      :recovery-required="recoveryRequired"
      @refresh="refreshAppointmentProcess"
    />
    <template v-else>
      <CourseUsePanel v-if="action === 'use'" />
      <CourseTransferPanel v-else-if="action === 'transfer'" />
      <CourseHistoryPanel v-else-if="action === 'history'" />
      <CourseManagePanel v-else-if="action === 'manage'" />
    </template>
  </div>
</template>
<style src="../../staff/styles/staff.css"></style>
<style scoped src="../styles/course-page.css"></style>
