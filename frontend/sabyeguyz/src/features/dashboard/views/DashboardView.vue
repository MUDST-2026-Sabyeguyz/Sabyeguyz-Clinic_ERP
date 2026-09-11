<script setup lang="ts">
import StaffHeader from '@/features/staff/components/StaffHeader.vue'

const patients = 328
const visits = 186
const revenue = 296900
const courses = 74
const statuses = [
  { status: 'Completed', value: 172 },
  { status: 'In Service', value: 4 },
  { status: 'No Show', value: 6 },
  { status: 'Cancelled', value: 4 },
]
</script>

<template>
  <div class="staff-page">
    <StaffHeader />
    <main class="staff-main">
      <section class="staff-hero">
        <small>MANAGER DASHBOARD</small>
        <h1>ภาพรวมแดชบอร์ด</h1>
        <p>ติดตามตัวเลขสำคัญและสถานะการดำเนินงานของคลินิก</p>
      </section>
      <section class="metrics">
        <article>
          <small>ผู้ป่วยทั้งหมด</small><b>{{ patients.toLocaleString() }}</b
          ><span>ข้อมูลรวมของคลินิก</span>
        </article>
        <article>
          <small>จำนวนการเข้ารับบริการ</small><b>{{ visits.toLocaleString() }}</b
          ><span>ทุกสถานะ</span>
        </article>
        <article>
          <small>รายได้รวม</small><b>฿{{ revenue.toLocaleString() }}</b
          ><span>ทุกสาขา</span>
        </article>
        <article>
          <small>คอร์สที่ใช้งาน</small><b>{{ courses.toLocaleString() }}</b
          ><span>คอร์สที่ยังใช้งานอยู่</span>
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
