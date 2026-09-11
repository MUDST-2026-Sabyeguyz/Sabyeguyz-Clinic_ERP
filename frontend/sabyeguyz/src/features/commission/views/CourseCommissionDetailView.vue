<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
const route = useRoute()
const courseId = computed(() => String(route.params.courseId || 'RE-2206315-01'))
const ledger = [
  {
    date: '5 ก.ย. 2569',
    patient: 'สมชาย ใจดี',
    owner: 'กฤษดา น.',
    treating: 'กฤษดา น.',
    gross: 70,
    fee: 0,
    net: 70,
  },
  {
    date: '12 ก.ย. 2569',
    patient: 'สมชาย ใจดี',
    owner: 'กฤษดา น.',
    treating: 'ศิริพร ว.',
    gross: 70,
    fee: 45,
    net: 25,
  },
  {
    date: '19 ก.ย. 2569',
    patient: 'สมชาย ใจดี',
    owner: 'กฤษดา น.',
    treating: 'กฤษดา น.',
    gross: 70,
    fee: 0,
    net: 70,
  },
]
</script>

<template>
  <div class="staff-page">
    <StaffHeader />
    <main class="staff-main">
      <section class="staff-hero">
        <small>COURSE COMMISSION AUDIT</small>
        <h1>รายละเอียดคอมมิชชันของคอร์ส</h1>
        <p>ตรวจสอบ Tier ที่ล็อก การจ่ายตาม Visit และยอดคงค้าง</p>
      </section>
      <nav class="commission-nav">
        <RouterLink to="/staff/commission">ภาพรวม</RouterLink
        ><RouterLink to="/staff/commission/tiers">Commission Tier</RouterLink
        ><RouterLink to="/staff/commission/closing">ปิดรอบรายเดือน</RouterLink>
      </nav>
      <section class="staff-panel commission-page-panel">
        <header>
          <div>
            <small>COURSE ID</small>
            <h2>{{ courseId }}</h2>
            <p class="muted-copy">Receipt RE-2206315 ขาย 18 ส.ค. 2569</p>
          </div>
          <span class="status locked">ล็อกอัตราแล้ว</span>
        </header>
        <div class="detail-grid">
          <article><span>ผู้ป่วย</span><b>สมชาย ใจดี HN260001</b></article>
          <article><span>ผู้ขาย / เจ้าของเคส</span><b>กฤษดา น.</b></article>
          <article><span>แพ็กเกจ</span><b>Routine Pain 10 Visits</b></article>
          <article><span>ราคาคอร์ส</span><b>฿10,000</b></article>
          <article><span>Scheme / Tier</span><b>V1 Tier 7%</b></article>
          <article><span>Commission Pool</span><b>฿700</b></article>
          <article><span>คอมมิชชันต่อ Visit</span><b>฿70</b></article>
          <article><span>ใช้แล้ว / คงเหลือ</span><b>3 / 7 Visits</b></article>
          <article><span>Gross Allocated</span><b>฿210</b></article>
          <article><span>ค่ารักษาแทน</span><b>฿45</b></article>
          <article><span>Owner Net Released</span><b>฿165</b></article>
          <article><span>Outstanding Pool</span><b>฿490</b></article>
        </div>
      </section>
      <section class="staff-panel commission-page-panel">
        <header>
          <div>
            <small>ALLOCATION LEDGER</small>
            <h2>ประวัติการจัดสรรราย Visit</h2>
          </div>
        </header>
        <table class="staff-table">
          <thead>
            <tr>
              <th>วันที่</th>
              <th>ผู้ป่วย</th>
              <th>เจ้าของเคส</th>
              <th>นักกายภาพผู้รักษา</th>
              <th>Gross</th>
              <th>ค่ารักษาแทน</th>
              <th>Owner Net</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in ledger" :key="item.date">
              <td>{{ item.date }}</td>
              <td>{{ item.patient }}</td>
              <td>{{ item.owner }}</td>
              <td>
                <b>{{ item.treating }}</b>
              </td>
              <td>฿{{ item.gross }}</td>
              <td>฿{{ item.fee }}</td>
              <td>
                <b>฿{{ item.net }}</b>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </main>
  </div>
</template>

<style scoped>
@import '../../staff/styles/staff.css';
@import '../styles/commission-workspace.css';
</style>
