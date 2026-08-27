<script setup lang="ts">
import { ref } from 'vue'
import logoUrl from '@/assets/sabyeguyz-logo.png'

const isSidebarOpen = ref(false)
const navigation = [
  { label: 'Dashboard', to: '/', icon: '⌂', available: false },
  { label: 'Patients', to: '/patients/register', icon: '♙', available: true },
  { label: 'Appointments', to: '/appointments', icon: '▣', available: false },
  { label: 'Treatment / Service', to: '/treatment', icon: '✚', available: false },
  { label: 'Courses', to: '/courses', icon: '▤', available: false },
  { label: 'Payments', to: '/payments', icon: '฿', available: false },
  { label: 'Staff', to: '/staff', icon: '♟', available: false },
  { label: 'Reports', to: '/reports', icon: '▥', available: false },
  { label: 'Settings', to: '/settings', icon: '⚙', available: false },
]
</script>

<template>
  <div class="app-shell">
    <div v-if="isSidebarOpen" class="sidebar-backdrop" @click="isSidebarOpen = false" />
    <aside class="sidebar" :class="{ open: isSidebarOpen }">
      <div class="sidebar-brand"><img :src="logoUrl" alt="Sabyeguyz" /><button class="close-sidebar" aria-label="ปิดเมนู" @click="isSidebarOpen = false">×</button></div>
      <nav aria-label="เมนูหลัก">
        <RouterLink v-for="item in navigation" :key="item.label" :to="item.to" class="nav-item" :class="{ disabled: !item.available }" @click="isSidebarOpen = false">
          <span class="nav-icon">{{ item.icon }}</span><span>{{ item.label }}</span><small v-if="!item.available">เร็ว ๆ นี้</small>
        </RouterLink>
      </nav>
    </aside>
    <div class="shell-body">
      <header class="top-bar"><button class="menu-toggle" aria-label="เปิดเมนู" @click="isSidebarOpen = true">☰</button><div><strong>Clinic ERP</strong><span>ระบบจัดการคลินิก</span></div><div class="top-actions"><span class="status-dot" />พร้อมใช้งาน</div></header>
      <main class="content-area"><slot /></main>
    </div>
  </div>
</template>
