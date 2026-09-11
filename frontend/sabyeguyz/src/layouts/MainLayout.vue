<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import logoUrl from '@/assets/sabyeguyz-logo.png'

const isSidebarOpen = ref(false)
const router = useRouter()
const auth = useAuthStore()
const navigation = [
  { label: 'ผู้ป่วย', to: '/patients' },
  { label: 'นัดหมาย', to: '/appointments' },
  { label: 'บริการ', to: '/treatment' },
  { label: 'Courses', to: '/courses' },
  { label: 'Course Ledger', to: '/course-ledger' },
  { label: 'รายการธุรกรรม', to: '/payments' },
  { label: 'บุคลากร', to: '/staff' },
  { label: 'รายงาน', to: '/reports' },
  { label: 'ตั้งค่า', to: '/settings' },
]
function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <div v-if="isSidebarOpen" class="sidebar-backdrop" @click="isSidebarOpen = false" />
    <header class="top-bar">
      <button class="menu-toggle" aria-label="เปิดเมนู" @click="isSidebarOpen = true">☰</button>
      <RouterLink class="brand" to="/" aria-label="กลับหน้า Dashboard"><img :src="logoUrl" alt="Sabyeguyz" /></RouterLink>
      <nav class="main-nav" aria-label="เมนูหลัก">
        <RouterLink v-for="item in navigation" :key="item.label" :to="item.to" class="nav-item" @click="isSidebarOpen = false">{{ item.label }}</RouterLink>
      </nav>
      <button v-if="auth.isAuthenticated" class="profile-button" aria-label="ออกจากระบบ" @click="logout">ออก</button>
    </header>
    <aside v-if="isSidebarOpen" class="mobile-nav">
      <RouterLink v-for="item in navigation" :key="item.label" :to="item.to" class="nav-item" @click="isSidebarOpen = false">{{ item.label }}</RouterLink>
      <button v-if="auth.isAuthenticated" class="nav-item" type="button" @click="logout">ออกจากระบบ</button>
    </aside>
    <main class="content-area"><slot /></main>
  </div>
</template>
