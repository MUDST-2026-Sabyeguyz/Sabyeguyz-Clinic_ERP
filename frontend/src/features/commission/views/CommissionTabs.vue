<script setup lang="ts">
defineProps<{ active: 'overview' | 'closing' | 'tiers' | 'compensation' }>()
const tabs = [
  { key: 'overview', label: 'ภาพรวม', to: '/staff/commission' },
  { key: 'closing', label: 'ปิดรอบรายเดือน', to: '/staff/commission/closing' },
  { key: 'tiers', label: 'ตั้งค่า Tier', to: '/staff/commission/tiers' },
  { key: 'compensation', label: 'ค่ารักษาแทน', to: '/staff/commission/compensation-settings' },
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
  grid-template-columns: repeat(4, minmax(135px, 170px));
  gap: 10px;
  margin: 18px 0 26px;
  overflow: hidden;
}
.commission-tabs a {
  min-height: 42px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  border: 1px solid #dde6e1;
  border-radius: 9px;
  background: #fff;
  color: #25372f;
  font-size: 0.94rem;
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
