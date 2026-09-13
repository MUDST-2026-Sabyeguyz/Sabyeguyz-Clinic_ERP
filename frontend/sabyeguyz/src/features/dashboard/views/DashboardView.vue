<script setup lang="ts">
import { onMounted, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { patientApi, reportApi } from '@/api/client'

const today = new Date()
const from = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-01`
const to = today.toISOString().slice(0, 10)
const patients = ref(0), visits = ref(0), revenue = ref(0), courses = ref(0)
const statuses = ref<Array<{ status: string; value: number }>>([])
const loading = ref(true), errorMessage = ref('')
onMounted(async () => {
  try {
    const [dashboard, appointments, balances, patientList] = await Promise.all([reportApi.dashboard(from, to), reportApi.appointments(from, to), reportApi.balances(from, to), patientApi.searchAll()])
    patients.value = patientList.length; visits.value = dashboard.visits; revenue.value = Number(dashboard.totalRevenue); courses.value = balances.purchases
    statuses.value = appointments.byStatus.map((item) => ({ status: item.status, value: item.appointments }))
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'โหลด dashboard ไม่สำเร็จ' }
  finally { loading.value = false }
})
</script>

<template>
  <div class="staff-page dashboard-page">
    <StaffHeader />
    <main class="staff-main">
      <section class="staff-hero">
        <small>MANAGER DASHBOARD</small>
        <h1>ภาพรวมแดชบอร์ด</h1>
        <p>ติดตามตัวเลขสำคัญและสถานะการดำเนินงานของคลินิก</p>
      </section>
      <p v-if="loading" class="status-feedback">กำลังโหลดข้อมูลจาก backend...</p>
      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      <section class="metrics">
        <article>
          <small>ผู้ป่วยทั้งหมด</small><b>{{ patients.toLocaleString() }}</b
          ><span>ข้อมูลรวมของคลินิก</span>
        </article>
        <article>
          <small>จำนวนการเข้ารับบริการ</small><b>{{ visits.toLocaleString() }}</b
          ><span>ที่มารับบริการแล้ว</span>
        </article>
        <article>
          <small>รายได้รวม</small><b>฿{{ revenue.toLocaleString() }}</b
          ><span>ทุกสาขา</span>
        </article>
        <article>
          <small>คอร์สที่ขายในช่วงเวลา</small><b>{{ courses.toLocaleString() }}</b
          ><span>ตามช่วงวันที่ของ Dashboard</span>
        </article>
      </section>
      <section class="dashboard-panels">
        <article class="staff-panel">
          <header>
            <div>
              <small>REVENUE SUMMARY</small>
              <h2>สรุปรายได้</h2>
            </div>
            <b>฿{{ revenue.toLocaleString() }}</b>
          </header>
          <p>หน้านี้แสดงเฉพาะภาพรวม หากต้องการรายละเอียดให้เปิดเมนูรายงาน</p>
          <RouterLink class="detail-link" to="/staff/reports">ดูรายงานรายละเอียด →</RouterLink>
        </article>
        <article class="staff-panel status-list">
          <header>
            <div>
              <small>VISIT STATUS</small>
              <h2>สถานะการเข้ารับบริการ</h2>
            </div>
          </header>
          <p v-for="item in statuses" :key="item.status">
            <span>{{ item.status }}</span
            ><b>{{ item.value }}</b>
          </p>
        </article>
      </section>
    </main>
  </div>
</template>

<style scoped>
@import '../../staff/styles/staff.css';
.staff-panel header,
.status-list p {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.metrics small,
.metrics span {
  color: var(--physio-muted);
}
.metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin: -38px 0 22px;
}
.metrics article {
  display: flex;
  flex-direction: column;
  padding: 20px;
  border: 1px solid var(--physio-line);
  border-radius: 14px;
  background: #fffdfa;
}
.metrics b {
  margin: 4px 0;
  font-size: 30px;
}
.dashboard-panels {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 18px;
  padding-bottom: 40px;
}
.staff-panel header {
  padding: 20px;
  border-bottom: 1px solid var(--physio-line);
}
.staff-panel header h2 {
  margin: 2px 0 0;
}
.staff-panel > p {
  padding: 4px 20px;
  color: var(--physio-muted);
}
.detail-link {
  display: inline-block;
  margin: 4px 20px 20px;
  color: #70833f;
  font-weight: 700;
}
.status-list p {
  margin: 0 20px;
  padding: 12px 0;
  border-bottom: 1px solid var(--physio-line);
  color: var(--physio-text);
}
@media (max-width: 900px) {
  .metrics {
    grid-template-columns: 1fr 1fr;
  }
  .dashboard-panels {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 600px) {
  .metrics {
    grid-template-columns: 1fr;
  }
}
</style>
