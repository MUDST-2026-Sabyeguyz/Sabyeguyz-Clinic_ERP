<script setup lang="ts">
import { onMounted, ref } from 'vue'
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
        <p>สามารถสลับสาขาได้ภายหลังจากเมนูด้านบน</p>
        <p v-if="loading" role="status">กำลังโหลดสาขา…</p>
        <div v-else-if="errorMessage" role="alert">
          <p>{{ errorMessage }}</p>
          <button type="button" @click="loadBranches">ลองใหม่</button>
        </div>
        <p v-else-if="!managerBranches.length">ยังไม่มีสาขาที่เปิดใช้งานในระบบ</p>
        <button
          v-for="branch in loading || errorMessage ? [] : managerBranches"
          :key="branch.code"
          class="branch-option"
          type="button"
          @click="chooseBranch(branch.code)"
        >
          <span class="branch-icon"><img :src="buildingIcon" alt="" /></span>
          <span
            ><b>{{ branch.name }}</b
            ><strong>เข้าสู่สาขา{{ branch.name }} →</strong></span
          >
        </button>
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
  color: #25372e;
}
.branch-brand {
  display: grid;
  place-content: center;
  gap: 24px;
  min-height: 100vh;
  text-align: center;
  background: #145c59 url('@/assets/brand-panel.png') center/cover;
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
  width: min(600px, 100%);
  box-sizing: border-box;
  min-height: 750px;
  padding: 48px 50px;
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
  font-size: clamp(28px, 3vw, 36px);
  line-height: 1.25;
}
.branch-card > p {
  margin: 0 0 26px;
  color: #52645b;
}
.branch-option {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 20px;
  width: 100%;
  min-height: 180px;
  margin-top: 16px;
  padding: 22px 24px;
  border: 1px solid #d9e1dc;
  border-radius: 12px;
  color: #25372e;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition:
    border 0.2s,
    box-shadow 0.2s,
    transform 0.2s;
}
.branch-option:hover {
  border-color: #1e807c;
  box-shadow: 0 8px 18px #1e807c1e;
  transform: translateY(-2px);
}
.branch-icon {
  display: grid;
  width: 56px;
  height: 56px;
  place-items: center;
  border-radius: 12px;
  background: #eff8f6;
}
.branch-icon img {
  width: 28px;
  height: 28px;
}
.branch-option > span:last-child {
  display: grid;
  align-content: center;
  gap: 12px;
}
.branch-option b {
  font-size: 26px;
}
.branch-option strong {
  color: #1e807c;
  font-size: 15px;
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
    padding: 24px 16px;
  }
  .branch-card {
    padding: 30px 24px;
    min-height: 0;
  }
}
</style>
