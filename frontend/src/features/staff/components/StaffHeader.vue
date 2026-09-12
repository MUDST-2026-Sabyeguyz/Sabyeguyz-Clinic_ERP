<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import logo from '@/assets/sabyeguyz-logo.png'
import drawerLogo from '@/assets/sabyeguyz-logo2.png'
const profileOpen = ref(false)
const menuOpen = ref(false)
const route = useRoute()
const navRef = ref<HTMLElement | null>(null)
const pillReady = ref(false)
const pillStyle = ref({ width: '0px', transform: 'translateX(0px)', opacity: '0' })
let readyFrame: number | undefined
let routeFrame: number | undefined
let hoverFrame: number | undefined
let hoveredLink: HTMLAnchorElement | null = null
const storedUser = JSON.parse(localStorage.getItem('authUser') ?? 'null') as {
  role?: string
  branchName?: string
} | null
const role = ref(storedUser?.role === 'THERAPIST' ? 'therapist' : 'manager')
const isManager = computed(() => role.value === 'manager')
const roleLabel = computed(() => (isManager.value ? 'Manager' : 'นักกายภาพบำบัด'))
const navItems = computed(() =>
  isManager.value
    ? [
        { to: '/staff/dashboard', label: 'แดชบอร์ด' },
        { to: '/staff/patients', label: 'ผู้ป่วย' },
        { to: '/staff/appointments', label: 'นัดหมาย' },
        { to: '/staff/services', label: 'บริการ' },
        { to: '/staff/transactions', label: 'ธุรกรรม' },
        { to: '/staff/commission', label: 'บุคลากรและค่าคอมมิชชัน' },
        { to: '/staff/reports', label: 'รายงาน' },
        { to: '/staff/settings', label: 'ตั้งค่า' },
      ]
    : [
        { to: '/staff/patients', label: 'ผู้ป่วย' },
        { to: '/staff/appointments', label: 'นัดหมาย' },
        { to: '/staff/services', label: 'บริการ' },
        { to: '/staff/transactions', label: 'ธุรกรรม' },
      ],
)
function movePill(target: HTMLElement | null) {
  if (!target || !navRef.value) return
  const navBox = navRef.value.getBoundingClientRect()
  const targetBox = target.getBoundingClientRect()
  navRef.value.querySelectorAll<HTMLElement>('a').forEach((link) => {
    link.classList.remove('pill-target')
  })
  target.classList.add('pill-target')
  pillStyle.value = {
    width: `${targetBox.width}px`,
    transform: `translateX(${targetBox.left - navBox.left}px)`,
    opacity: '1',
  }
}
function movePillToActive() {
  if (!navRef.value) return
  const links = [...navRef.value.querySelectorAll<HTMLAnchorElement>('a')]
  const activePath = route.path.startsWith('/staff/courses') ? '/staff/services' : route.path
  const matchingLink = links
    .filter((link) => {
      const path = new URL(link.href, window.location.origin).pathname.replace(/\/$/, '')
      return activePath === path || activePath.startsWith(`${path}/`)
    })
    .sort((a, b) => b.pathname.length - a.pathname.length)[0]
  movePill(matchingLink ?? links[0] ?? null)
}
function handleNavHover(event: Event) {
  const target = event.target
  if (!(target instanceof Element)) return
  const link = target.closest<HTMLAnchorElement>('a')
  if (!link || !navRef.value?.contains(link) || link === hoveredLink) return
  hoveredLink = link
  // Device/Responsive mode may report a hover during the resize frame.  Make
  // the transition available first, then position the pill on the next frame.
  pillReady.value = true
  if (hoverFrame) cancelAnimationFrame(hoverFrame)
  nextTick(() => {
    hoverFrame = requestAnimationFrame(() => {
      if (hoveredLink === link) movePill(link)
    })
  })
}
function resetHoverToActive() {
  hoveredLink = null
  if (hoverFrame) cancelAnimationFrame(hoverFrame)
  movePillToActive()
}
function positionPillWithoutAnimation() {
  hoveredLink = null
  pillReady.value = false
  movePillToActive()
  if (readyFrame) cancelAnimationFrame(readyFrame)
  readyFrame = requestAnimationFrame(() => {
    pillReady.value = true
  })
}
onMounted(() => {
  positionPillWithoutAnimation()
  window.addEventListener('resize', positionPillWithoutAnimation)
  navRef.value?.addEventListener('mouseover', handleNavHover)
  navRef.value?.addEventListener('pointerover', handleNavHover)
  navRef.value?.addEventListener('pointermove', handleNavHover)
  navRef.value?.addEventListener('focusin', handleNavHover)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', positionPillWithoutAnimation)
  navRef.value?.removeEventListener('mouseover', handleNavHover)
  navRef.value?.removeEventListener('pointerover', handleNavHover)
  navRef.value?.removeEventListener('pointermove', handleNavHover)
  navRef.value?.removeEventListener('focusin', handleNavHover)
  if (readyFrame) cancelAnimationFrame(readyFrame)
  if (routeFrame) cancelAnimationFrame(routeFrame)
  if (hoverFrame) cancelAnimationFrame(hoverFrame)
})
watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false
    hoveredLink = null
    nextTick(() => {
      if (routeFrame) cancelAnimationFrame(routeFrame)
      routeFrame = requestAnimationFrame(movePillToActive)
    })
  },
)
function signOut() {
  localStorage.removeItem('authToken')
  localStorage.removeItem('staffRole')
  localStorage.removeItem('staffUsername')
  localStorage.removeItem('mockStaffRole')
  localStorage.removeItem('mockStaffEmail')
}
</script>

<template>
  <header class="staff-header">
    <RouterLink class="brand" to="/staff/patients"><img :src="logo" alt="SaByeGuyz" /></RouterLink>
    <button
      class="menu-toggle"
      type="button"
      aria-label="เปิดเมนูหลัก"
      :aria-expanded="menuOpen"
      @click="menuOpen = !menuOpen"
    >
      <span></span><span></span><span></span>
    </button>
    <nav
      ref="navRef"
      :class="{ 'mobile-open': menuOpen }"
      aria-label="เมนูเจ้าหน้าที่คลินิก"
      @mouseleave="() => resetHoverToActive()"
    >
      <div class="mobile-menu-head">
        <button type="button" aria-label="ปิดเมนู" @click="menuOpen = false">×</button>
        <img :src="drawerLogo" alt="SaByeGuyz" />
      </div>
      <span
        class="nav-pill"
        :class="{ 'is-ready': pillReady }"
        :style="pillStyle"
        aria-hidden="true"
      ></span>
      <RouterLink v-for="item in navItems" :key="item.to" :to="item.to" @click="menuOpen = false">
        {{ item.label }}
      </RouterLink>
    </nav>
    <div class="profile-wrap">
      <button
        class="profile-trigger"
        aria-label="เปิดเมนูโปรไฟล์"
        :aria-expanded="profileOpen"
        @click="profileOpen = !profileOpen"
      >
        <span class="profile-copy"
          ><small>กำลังใช้งาน</small><b>{{ roleLabel }}</b></span
        >
        <span class="profile-avatar">{{ isManager ? 'MG' : 'PT' }}</span>
      </button>
      <div v-if="profileOpen" class="profile-menu">
        <div>
          <small>โปรไฟล์ผู้ใช้งาน</small><b>{{ roleLabel }}</b
          ><span>{{ isManager ? 'เข้าถึงทุกสาขา' : 'สาขาราชพฤกษ์' }}</span>
        </div>
        <RouterLink to="/" @click="signOut">ออกจากระบบ</RouterLink>
      </div>
    </div>
  </header>
  <button
    v-if="menuOpen"
    class="menu-backdrop"
    type="button"
    aria-label="ปิดเมนู"
    @click="menuOpen = false"
  ></button>
</template>

<style scoped src="../styles/staff-header.css"></style>
<style src="../styles/therapist.css"></style>
