<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi } from '@/api/client'

const month = ref('2026-08')
const rows = ref<Array<{id:number;staff:string;sales:number;tier:number;courses:number;status:string;closedAt:string}>>([])
const error = ref('')
const openCount = computed(() => rows.value.filter((row) => row.status === 'Open').length)
async function load(){try{const data=await commissionApi.closings(month.value);rows.value=data.map(r=>({id:r.id,staff:r.employeeName,sales:r.sales,tier:r.rate*100,courses:0,status:r.status==='CLOSED'?'Closed':'Open',closedAt:''}))}catch(e){error.value=e instanceof Error?e.message:'โหลดรอบคอมมิชชันไม่สำเร็จ'}}
async function closeAll(){try{await commissionApi.closeMonth(month.value);await load()}catch(e){error.value=e instanceof Error?e.message:'ปิดรอบไม่สำเร็จ'}}
onMounted(load)
</script>

<template>
  <div class="staff-page">
    <StaffHeader />
    <main class="staff-main">
      <section class="staff-hero">
        <small>MONTHLY COMMISSION CLOSING</small>
        <h1>ปิดรอบคอมมิชชันรายเดือน</h1>
        <p>ตรวจยอดขายและ Tier ที่ระบบแนะนำก่อนล็อกอัตราของแต่ละคอร์ส</p>
      </section>
      <nav class="commission-nav">
        <RouterLink to="/staff/commission">ภาพรวม</RouterLink
        ><RouterLink to="/staff/commission/tiers">Commission Tier</RouterLink
        ><RouterLink to="/staff/commission/closing">ปิดรอบรายเดือน</RouterLink>
      </nav>
      <section class="metric-grid">
        <article>
          <small>รอบที่เลือก</small><b>ส.ค. 2569</b><span>Commission Scheme V1</span>
        </article>
        <article><small>ยอดขายคอร์สรวม</small><b>฿171,500</b><span>19 คอร์ส</span></article>
        <article>
          <small>รอปิดรอบ</small><b>{{ openCount }}</b
          ><span>บุคลากร</span>
        </article>
        <article>
          <small>สถานะรอบ</small><b>{{ openCount ? 'Open' : 'Closed' }}</b
          ><span>{{ openCount ? 'ยังแก้ไขได้' : 'ล็อกแล้ว' }}</span>
        </article>
      </section>
      <section class="staff-panel commission-page-panel">
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <header>
          <div>
            <small>MONTHLY CLOSING</small>
            <h2>รายการคำนวณประจำเดือน</h2>
          </div>
          <div class="commission-actions">
            <input v-model="month" type="month" /><button
              class="primary"
              :disabled="!openCount"
              @click="closeAll"
            >
              ปิดรอบทั้งหมด
            </button>
          </div>
        </header>
        <table class="staff-table">
          <thead>
            <tr>
              <th>บุคลากร</th>
              <th>ยอดขายคอร์ส</th>
              <th>จำนวนคอร์ส</th>
              <th>Tier ที่แนะนำ</th>
              <th>อัตราที่ล็อก</th>
              <th>สถานะ</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.staff">
              <td>
                <b>{{ row.staff }}</b>
              </td>
              <td>฿{{ row.sales.toLocaleString() }}</td>
              <td>{{ row.courses }}</td>
              <td>Tier {{ row.tier }}%</td>
              <td>{{ row.status === 'Closed' ? `${row.tier}%` : 'รอยืนยัน' }}</td>
              <td>
                <span class="status" :class="row.status === 'Closed' ? 'locked' : 'open-status'">{{
                  row.status === 'Closed' ? 'ปิดรอบแล้ว' : 'รอปิดรอบ'
                }}</span
                ><small v-if="row.closedAt">{{ row.closedAt }}</small>
              </td>
              <td>
                <button
                  class="secondary"
                  :disabled="row.status === 'Closed'"
                  @click="closeAll"
                >
                  {{ row.status === 'Closed' ? 'ล็อกแล้ว' : 'ปิดรอบ' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <p class="notice">
          เมื่อปิดรอบแล้ว อัตราคอมมิชชันของคอร์สในเดือนนี้จะถูกล็อกและไม่เปลี่ยนตาม Tier เดือนถัดไป
        </p>
      </section>
    </main>
  </div>
</template>

<style scoped>
@import '../../staff/styles/staff.css';
@import '../styles/commission-workspace.css';
.commission-actions input {
  padding: 9px 12px;
  border: 1px solid var(--physio-line);
  border-radius: 8px;
  font: inherit;
}
.primary:disabled,
.secondary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
