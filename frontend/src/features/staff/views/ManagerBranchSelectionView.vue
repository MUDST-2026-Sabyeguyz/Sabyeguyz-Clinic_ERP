<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import buildingIcon from '@/assets/company-building.svg'
import { branchApi } from '@/api/client'
import {
  managerBranches,
  setManagerBranches,
  setSelectedManagerBranch,
} from '@/features/staff/data/managerBranch'

const router = useRouter()
const loading = ref(true)
const errorMessage = ref('')
const branchSearch = ref('')
const visibleBranches = computed(() => {
  const query = branchSearch.value.trim().toLocaleLowerCase()
  if (!query) return managerBranches.value
  return managerBranches.value.filter((branch) => branch.name.toLocaleLowerCase().includes(query))
})
async function chooseBranch(code: string) {
  const branch = managerBranches.value.find((item) => item.code === code)
  if (!branch) return
  setSelectedManagerBranch(branch)
  await router.replace('/staff/dashboard')
}
async function loadBranches() {
  loading.value = true
  errorMessage.value = ''
  try {
    const branches = await branchApi.list()
    setManagerBranches(branches.filter((branch) => branch.active))
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลสาขาไม่สำเร็จ'
  } finally {
    loading.value = false
  }
}
onMounted(loadBranches)
</script>

<template>
  <main class="branch-page">
    <section class="branch-brand">
      <img src="@/assets/sabyeguyz-logo.png" alt="SaByeGuyz" />
      <p>ฟื้นฟูการเคลื่อนไหว<br />เพื่อคุณภาพชีวิตที่ดีขึ้น</p>
    </section>
    <section class="branch-content">
      <div class="branch-card">
        <small>MANAGER</small>
        <h1>เลือกสาขาที่ต้องการเข้าใช้งาน</h1>
        <p v-if="loading" role="status">กำลังโหลดสาขา…</p>
        <div v-else-if="errorMessage" role="alert">
          <p>{{ errorMessage }}</p>
          <button type="button" @click="loadBranches">ลองใหม่</button>
        </div>
        <p v-else-if="!managerBranches.length">ยังไม่มีสาขาที่เปิดใช้งานในระบบ</p>
        <template v-else>
          <div v-if="managerBranches.length > 4" class="branch-search">
            <label for="branch-search">ค้นหาสาขา</label>
            <span class="branch-search-control">
              <input
                id="branch-search"
                v-model="branchSearch"
                type="text"
                placeholder="พิมพ์ชื่อสาขา"
              />
              <button
                v-if="branchSearch"
                type="button"
                aria-label="ล้างคำค้นหา"
                @click="branchSearch = ''"
              >
                ล้าง
              </button>
            </span>
          </div>
          <p
            v-if="branchSearch.trim() && visibleBranches.length === 0"
            class="branch-no-results"
            role="status"
          >
            ไม่พบสาขาที่ตรงกับการค้นหา
          </p>
          <div v-else class="branch-option-list">
            <button
              v-for="branch in visibleBranches"
              :key="branch.code"
              class="branch-option"
              type="button"
              @click="chooseBranch(branch.code)"
            >
              <span class="branch-icon"><img :src="buildingIcon" alt="" /></span>
              <span class="branch-option-copy">
                <b>{{ branch.name }}</b>
                <strong>เข้าสู่สาขา <span aria-hidden="true">→</span></strong>
              </span>
            </button>
          </div>
        </template>
      </div>
    </section>
  </main>
</template>

<style scoped>
.branch-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #e9f5f0;
  color: var(--color-ink);
}
.branch-brand {
  display: grid;
  place-content: center;
  gap: 24px;
  min-height: 100vh;
  text-align: center;
  background: var(--color-primary-strong) url('@/assets/brand-panel.png') center/cover;
  color: #c6dfd8;
}
.branch-brand img {
  width: min(430px, 72vw);
  filter: drop-shadow(0 12px 22px #0d4c4830);
}
.branch-brand p {
  margin: 0;
  font-size: 30px;
  line-height: 1.5;
}
.branch-content {
  display: grid;
  place-items: center;
  padding: 32px;
}
.branch-card {
  width: min(540px, 100%);
  box-sizing: border-box;
  padding: 40px;
  border-radius: 24px;
  background: #fff;
  box-shadow: 0 8px 20px #0000001f;
}
.branch-card > small {
  color: #1f807d;
  font-weight: 700;
}
.branch-card h1 {
  margin: 16px 0 8px;
  font-size: clamp(26px, 2.2vw, 32px);
  line-height: 1.35;
}
.branch-guidance {
  margin: 0 0 24px;
  color: #52645b;
  line-height: 1.65;
}
.branch-search {
  display: grid;
  gap: 8px;
  margin: 20px 0 12px;
  color: #344b42;
  font-size: 14px;
  font-weight: 700;
}
.branch-search > label {
  color: #344b42;
  font-size: 14px;
  font-weight: 700;
}
.branch-search-control {
  display: flex;
  align-items: center;
  min-height: 48px;
  border: 1px solid #b7c9c1;
  border-radius: 10px;
  background: #fff;
}
.branch-search-control:focus-within {
  border-color: var(--color-primary-strong);
  box-shadow: 0 0 0 3px #145c5940;
}
.branch-search-control input {
  width: 100%;
  min-width: 0;
  min-height: 46px;
  padding: 10px 14px;
  border: 0;
  border-radius: inherit;
  outline: 0;
  color: var(--color-ink);
  background: transparent;
  font: inherit;
  font-weight: 400;
}
.branch-search-control input::placeholder {
  color: #64756d;
  opacity: 1;
}
.branch-search-control button {
  flex: 0 0 auto;
  min-height: 44px;
  margin-right: 2px;
  padding: 0 12px;
  border: 0;
  border-radius: 8px;
  color: var(--color-primary-strong);
  background: transparent;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.branch-search-control button:hover {
  background: #eff8f6;
}
.branch-no-results {
  margin: 16px 0 0;
  padding: 16px;
  border-radius: 10px;
  color: #52645b;
  background: #f4f8f6;
}
.branch-option-list {
  display: grid;
  gap: 10px;
  margin-top: 20px;
}
.branch-option {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  align-items: center;
  gap: 14px;
  width: 100%;
  min-height: 96px;
  padding: 18px 20px;
  border: 1px solid var(--color-border-soft);
  border-radius: 10px;
  color: var(--color-ink);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition:
    border 0.2s,
    box-shadow 0.2s,
    transform 0.2s;
}
.branch-option:hover {
  border-color: var(--color-primary);
  box-shadow: 0 8px 18px #1e807c1e;
  transform: translateY(-2px);
}
.branch-option:active {
  background: #f3faf8;
  transform: translateY(0);
}
.branch-option:focus-visible,
.branch-search-control button:focus-visible {
  outline: 3px solid var(--color-primary-strong);
  outline-offset: 3px;
  position: relative;
  z-index: 1;
}
.branch-icon {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border-radius: 12px;
  background: #eff8f6;
}
.branch-icon img {
  width: 28px;
  height: 28px;
}
.branch-option-copy {
  display: grid;
  gap: 6px;
  min-width: 0;
}
.branch-option-copy b {
  overflow-wrap: anywhere;
  font-size: 26px;
  line-height: 1.4;
}
.branch-option strong {
  color: var(--color-primary);
  font-size: 17px;
  font-weight: 600;
}
.branch-option strong span {
  display: inline-block;
  transition: transform 0.18s ease;
}
.branch-option:hover strong span {
  transform: translateX(3px);
}
@media (max-width: 820px) {
  .branch-page {
    grid-template-columns: 1fr;
  }
  .branch-brand {
    min-height: 220px;
    padding: 24px;
  }
  .branch-brand img {
    width: min(300px, 72vw);
  }
  .branch-brand p {
    font-size: 20px;
  }
  .branch-content {
    padding: 20px 16px 32px;
  }
  .branch-card {
    padding: 28px 22px;
  }
  .branch-card h1 {
    font-size: 26px;
  }
  .branch-option-list {
    gap: 8px;
    margin-top: 16px;
  }
  .branch-option {
    grid-template-columns: 40px minmax(0, 1fr);
    gap: 12px;
    min-height: 88px;
    padding: 16px;
  }
  .branch-icon {
    width: 40px;
    height: 40px;
  }
  .branch-option strong {
    font-size: 16px;
  }
  .branch-option-copy b {
    font-size: 23px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .branch-option,
  .branch-option strong span {
    transition: none;
  }
  .branch-option:hover {
    transform: none;
  }
}
</style>
