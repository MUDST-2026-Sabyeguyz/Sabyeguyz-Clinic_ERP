<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import logo from '@/assets/sabyeguyz-logo.png'
import drawerLogo from '@/assets/sabyeguyz-logo2.png'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import { branchApi } from '@/api/client'
import {
  managerBranches,
  selectedManagerBranch,
  setManagerBranches,
  setSelectedManagerBranch,
} from '@/features/staff/data/managerBranch'
const profileOpen = ref(false)
const menuOpen = ref(false)
const route = useRoute()
const navRef = ref<HTMLElement | null>(null)
const pillReady = ref(false)
const pillStyle = ref({
  width: '0px',
  transform: 'translateX(0px)',
  opacity: '0',
})
let readyFrame: number | undefined
let routeFrame: number | undefined
let settleFrame: number | undefined
const storedUser = JSON.parse(localStorage.getItem('authUser') ?? 'null') as {
  role?: string
  branchName?: string
} | null
const role = ref(storedUser?.role === 'THERAPIST' ? 'therapist' : 'manager')
const isManager = computed(() => role.value === 'manager')
const activeManagerBranch = computed(() => selectedManagerBranch())
const homePath = computed(() => (isManager.value ? '/staff/dashboard' : '/staff/patients'))
const roleLabel = computed(() => (isManager.value ? 'Manager' : 'นักกายภาพบำบัด'))
const profileBranchLabel = computed(() => storedUser?.branchName || 'สาขาที่ได้รับมอบหมาย')
const navItems = computed(() =>
  isManager.value
    ? [
        { to: '/staff/dashboard', label: 'แดชบอร์ด' },
        { to: '/staff/patients', label: 'ผู้ป่วย' },
        { to: '/staff/appointments', label: 'นัดหมาย' },
        { to: '/staff/services', label: 'บริการ' },
        { to: '/staff/transactions', label: 'รายการธุรกรรม' },
        { to: '/staff/commission', label: 'คอมมิชชั่น' },
        { to: '/staff/reports', label: 'รายงาน' },
        { to: '/staff/settings', label: 'การตั้งค่า' },
      ]
    : [
        { to: '/staff/patients', label: 'ผู้ป่วย' },
        { to: '/staff/appointments', label: 'นัดหมาย' },
        { to: '/staff/services', label: 'บริการ' },
        { to: '/staff/transactions', label: 'ธุรกรรม' },
        { to: '/staff/compensation', label: 'คอมมิชชัน' },
      ],
)
function movePill(target: HTMLElement | null) {
  if (!target || !navRef.value) return
  const targetBox = target.getBoundingClientRect()
  navRef.value.querySelectorAll<HTMLElement>('a').forEach((link) => {
    link.classList.remove('pill-target')
  })
  target.classList.add('pill-target')
  pillStyle.value = {
    width: `${targetBox.width}px`,
    transform: `translateX(${target.offsetLeft}px)`,
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
function positionPillWithoutAnimation() {
  pillReady.value = false
  movePillToActive()
  if (readyFrame) cancelAnimationFrame(readyFrame)
  readyFrame = requestAnimationFrame(() => {
    pillReady.value = true
  })
}
onMounted(() => {
  positionPillWithoutAnimation()
  requestAnimationFrame(() => {
    settleFrame = requestAnimationFrame(positionPillWithoutAnimation)
  })
  document.fonts?.ready.then(positionPillWithoutAnimation)
  window.addEventListener('resize', positionPillWithoutAnimation)
  if (isManager.value) void loadManagerBranches()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', positionPillWithoutAnimation)
  if (readyFrame) cancelAnimationFrame(readyFrame)
  if (routeFrame) cancelAnimationFrame(routeFrame)
  if (settleFrame) cancelAnimationFrame(settleFrame)
})
watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false
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
async function loadManagerBranches() {
  try {
    const branches = (await branchApi.list())
      .filter((branch) => branch.active)
      .map((branch) => ({
        code: branch.code,
        name: branch.name,
        address: branch.address,
      }))
    setManagerBranches(branches)
    window.dispatchEvent(new CustomEvent('manager-branch-changed'))
  } catch {}
}
function switchManagerBranch(code: string) {
  const branch = managerBranches.value.find((item) => item.code === code)
  if (!branch) return
  setSelectedManagerBranch(branch)
  window.dispatchEvent(new CustomEvent('manager-branch-changed'))
}
function branchLabel(name: string) {
  return name.replace(/^สาขา\s*/, '')
}
</script>

<template>
  <header class="staff-header" :class="{ 'manager-header': isManager }">
    <RouterLink class="brand" :to="homePath"><img :src="logo" alt="SaByeGuyz" /></RouterLink>
    <button
      class="menu-toggle"
      type="button"
      aria-label="เปิดเมนูหลัก"
      :aria-expanded="menuOpen"
      @click="menuOpen = !menuOpen"
    >
      <span></span><span></span><span></span>
    </button>
    <nav ref="navRef" :class="{ 'mobile-open': menuOpen }" aria-label="เมนูเจ้าหน้าที่คลินิก">
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
    <div v-if="isManager" class="branch-switcher">
      <FilterDropdown
        class="manager-branch-filter"
        :model-value="activeManagerBranch?.code ?? ''"
        :options="
          managerBranches.map((branch) => ({
            value: branch.code,
            label: branchLabel(branch.name),
          }))
        "
        default-label="เลือกสาขา"
        label="เลือกสาขา"
        :clear-on-reselect="false"
        hide-default-option
        @update:model-value="switchManagerBranch"
      />
    </div>
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
          ><span>{{ isManager ? 'เข้าถึงทุกสาขา' : profileBranchLabel }}</span>
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
