<script setup lang="ts">
defineProps<{ active: 'overview' | 'closing' | 'tiers' | 'compensation' | 'adjustments' | 'payments' }>()
const tabs = [
  { key: 'overview', label: 'ภาพรวม', to: '/staff/commission' },
  { key: 'closing', label: 'ปิดรอบรายเดือน', to: '/staff/commission/closing' },
  { key: 'tiers', label: 'ตั้งค่ากฎค่าตอบแทน ↗', to: '/staff/settings?section=tiers' },
  { key: 'payments', label: 'งวดจ่ายค่าตอบแทน', to: '/staff/commission/payments' },
  { key: 'adjustments', label: 'รายการปรับปรุงค่าตอบแทน', to: '/staff/commission/adjustments' },
] as const
</script>
<template>
  <nav class="commission-tabs" aria-label="เมนูค่าคอมมิชชัน">
    <RouterLink
      v-for="tab in tabs"
      :key="tab.key"
      :to="tab.to"
      :class="{ active: active === tab.key }"
      >{{ tab.label }}</RouterLink
    >
  </nav>
</template>
<style scoped>
.commission-tabs {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin: 20px 0 28px;
  overflow: hidden;
}
.commission-tabs a {
  min-height: 48px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  border: 1px solid #dde6e1;
  border-radius: 10px;
  background: #fff;
  color: #25372f;
  font-size: 1rem;
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}
.commission-tabs a.active {
  border-color: #1f8883;
  background: #1f8883;
  color: #fff;
}
@media (max-width: 720px) {
  .commission-tabs {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin-bottom: 20px;
  }
  .commission-tabs a {
    justify-content: center;
  }
}
@media (max-width: 380px) {
  .commission-tabs {
    grid-template-columns: 1fr;
  }
}
</style>
