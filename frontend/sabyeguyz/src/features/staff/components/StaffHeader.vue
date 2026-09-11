<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { clearSession } from '@/api/client'
import logo from '@/assets/sabyeguyz-logo.png'
const profileOpen = ref(false)
const route = useRoute()
const navRef = ref<HTMLElement | null>(null)
const pillReady = ref(false)
const pillStyle = ref({ width: '0px', transform: 'translateX(0px)', opacity: '0' })
const authUser = JSON.parse(localStorage.getItem('authUser') ?? 'null') as { role?: string } | null
const role = ref(authUser?.role === 'THERAPIST' ? 'therapist' : 'manager')
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
  navRef.value.querySelectorAll<HTMLElement>('a').forEach((link) => {
    link.classList.remove('pill-target')
    link.style.removeProperty('color')
  })
  target.classList.add('pill-target')
  target.style.setProperty('color', '#181818', 'important')
  pillStyle.value = {
    width: `${target.offsetWidth}px`,
    transform: `translateX(${target.offsetLeft}px)`,
    opacity: '1',
  }
}
function movePillToActive() {
  if (!navRef.value) return
  const links = [...navRef.value.querySelectorAll<HTMLAnchorElement>('a')]
  const exactActive = navRef.value.querySelector<HTMLAnchorElement>('.router-link-exact-active')
  const activePath = route.path.startsWith('/staff/courses') ? '/staff/services' : route.path
  const matchingLink = links
    .filter((link) => {
      const path = new URL(link.href, window.location.origin).pathname.replace(/\/$/, '')
      return activePath === path || activePath.startsWith(`${path}/`)
    })
    .sort((a, b) => b.pathname.length - a.pathname.length)[0]
  movePill(exactActive ?? matchingLink ?? links[0] ?? null)
}
function handleNavPointer(event: PointerEvent) {
  const target = event.target as HTMLElement | null
  const link = target?.closest('a')
  if (link && navRef.value?.contains(link)) movePill(link)
}
function handleNavFocus(event: FocusEvent) {
  const target = event.target as HTMLElement | null
  const link = target?.closest('a')
  if (link && navRef.value?.contains(link)) movePill(link)
}
function hoverPill(event: Event) {
  movePill(event.currentTarget as HTMLElement)
}
function positionPillWithoutAnimation() {
  pillReady.value = false
  nextTick(() => {
    movePillToActive()
    setTimeout(() => {
      pillReady.value = true
    }, 0)
  })
}
onMounted(positionPillWithoutAnimation)
watch(() => route.fullPath, positionPillWithoutAnimation)
function signOut() {
  clearSession()
}
</script>

<template>
  <header class="staff-header">
    <RouterLink class="brand" to="/staff/appointments"
      ><img :src="logo" alt="SaByeGuyz"
    /></RouterLink>
    <nav
      ref="navRef"
      aria-label="เมนูเจ้าหน้าที่คลินิก"
      @pointerover="handleNavPointer"
      @focusin="handleNavFocus"
      @mouseleave="movePillToActive"
    >
      <span
        class="nav-pill"
        :class="{ 'is-ready': pillReady }"
        :style="pillStyle"
        aria-hidden="true"
      ></span>
      <RouterLink
        v-for="item in navItems"
        :key="item.to"
        :to="item.to"
        @mouseenter="hoverPill"
        @focus="hoverPill"
      >
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
</template>

<style scoped src="../styles/staff-header.css"></style>
<style src="../styles/therapist.css"></style>
