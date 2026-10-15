<script setup lang="ts">
import { computed } from 'vue'
import TherapistCompensationEmptyState from './TherapistCompensationEmptyState.vue'

const props = defineProps<{ kind: 'commission' | 'treatment' }>()

const commissionRows = [
  ['บริการ', '—', 'วันที่รายการ —'],
  ['ยอดบริการก่อนส่วนลด', '—', 'รายการต้นทาง —'],
  ['ส่วนลดระดับรายการ', '—', 'ลดฐานค่าคอมมิชชัน'],
  ['ฐานค่าคอมมิชชัน', '—', 'ยอดบริการหลังหักส่วนลด'],
  ['Tier และอัตราที่ล็อก', '—', 'อัตราที่ใช้สำหรับงวดนี้'],
  ['ค่าคอมมิชชันสุทธิ', '—', 'รอจ่าย'],
] as const

const treatmentRows = [
  ['บริการ', '—', 'วันที่รายการ —'],
  ['เจ้าของเคส', '—', 'ผู้รับค่าคอมมิชชันหลัก'],
  ['ผู้ให้บริการจริง', '—', 'ผู้ได้รับค่ามือ'],
  ['วิธีคำนวณ', '—', 'ตามกฎนักกายภาพทั่วไป'],
  ['อัตราค่ามือตามกฎ', '—', 'อัตราตามกฎที่มีผล ณ วันรักษา'],
  ['ค่ามือสุทธิที่ได้รับ', '—', 'รอจ่าย (Pending Payment)'],
] as const

const rows = computed(() => (props.kind === 'commission' ? commissionRows : treatmentRows))
</script>

<template>
  <section class="therapist-detail-card therapist-figma-detail-card">
    <h2>{{ kind === 'commission' ? 'รายละเอียดค่าคอมมิชชัน' : 'รายละเอียดค่ามือ' }}</h2>
    <p class="therapist-detail-period">งวดปัจจุบัน · ระบบคำนวณให้อัตโนมัติ</p>
    <dl class="therapist-calculation-list">
      <div
        v-for="([label, value, hint], index) in rows"
        :key="label"
        :class="{ total: index === rows.length - 1 }"
      >
        <dt>{{ label }}</dt>
        <dd>{{ value }}</dd>
        <small>{{ hint }}</small>
      </div>
    </dl>
    <TherapistCompensationEmptyState />
  </section>
  <div class="therapist-detail-actions">
    <RouterLink
      class="therapist-back-action"
      :to="{ name: 'therapist-compensation', query: { tab: kind } }"
      >←&nbsp;&nbsp; กลับภาพรวม</RouterLink
    >
    <RouterLink
      class="therapist-primary-action"
      :to="{
        name: kind === 'commission' ? 'therapist-commission-source' : 'therapist-treatment-source',
      }"
    >
      {{ kind === 'commission' ? 'ดูรายการขายต้นทาง' : 'ดูรายการรักษาต้นทาง' }}
    </RouterLink>
  </div>
</template>
