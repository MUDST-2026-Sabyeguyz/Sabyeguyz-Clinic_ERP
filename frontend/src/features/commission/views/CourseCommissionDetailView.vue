<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { courseApi, type CoursePurchase } from '@/api/client'
const route = useRoute()
const courseId = computed(() => Number(route.params.courseId))
const ledger = ref<Array<{date:string;patient:string;owner:string;treating:string;gross:number;fee:number;net:number}>>([])
const purchase = ref<(CoursePurchase & { commissionStatus?: string; lockedCommissionRate?: number; totalCourseCommissionPool?: number; commissionAllocationPerVisit?: number }) | null>(null)
const loadError = ref('')
const formatCurrency = (value: number | undefined) => value === undefined ? '—' : `฿${value.toLocaleString('th-TH')}`
onMounted(async () => {
  if (!Number.isInteger(courseId.value)) {
    loadError.value = 'ไม่พบรหัสคอร์สที่ต้องการตรวจสอบ'
    return
  }
  try {
    const data = await courseApi.ledger(courseId.value)
    purchase.value = data.purchase
    ledger.value = data.allocations.map((item) => ({
      date: item.visitDate,
      patient: data.purchase.patient.fullName,
      owner: '—',
      treating: '—',
      gross: item.gross,
      fee: item.fee,
      net: item.ownerNet,
    }))
  } catch {
    loadError.value = 'โหลดรายละเอียดคอมมิชชันจาก backend ไม่สำเร็จ'
  }
})
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
            <h2>{{ purchase?.course.name ?? `คอร์ส #${courseId}` }}</h2>
            <p class="muted-copy">รหัสคอร์ส {{ purchase?.course.code ?? '—' }} · ซื้อเมื่อ {{ purchase?.purchasedAt ?? '—' }}</p>
          </div>
          <span class="status locked">ล็อกอัตราแล้ว</span>
        </header>
        <div class="detail-grid">
          <article><span>ผู้ป่วย</span><b>{{ purchase?.patient.fullName ?? '—' }}</b></article>
          <article><span>สาขา</span><b>{{ purchase?.branchCode ?? '—' }}</b></article>
          <article><span>แพ็กเกจ</span><b>{{ purchase?.course.name ?? '—' }}</b></article>
          <article><span>ราคาคอร์ส</span><b>{{ formatCurrency(purchase?.price) }}</b></article>
          <article><span>สถานะ</span><b>{{ purchase?.commissionStatus ?? purchase?.status ?? '—' }}</b></article>
          <article><span>Commission Pool</span><b>{{ formatCurrency(purchase?.totalCourseCommissionPool) }}</b></article>
          <article><span>คอมมิชชันต่อ Visit</span><b>{{ formatCurrency(purchase?.commissionAllocationPerVisit) }}</b></article>
          <article><span>ใช้แล้ว / คงเหลือ</span><b>{{ purchase?.used ?? '—' }} / {{ purchase?.remaining ?? '—' }} Visits</b></article>
          <article><span>จำนวนครั้งทั้งหมด</span><b>{{ purchase?.purchased ?? '—' }} Visits</b></article>
          <article><span>อัตราคอมมิชชัน</span><b>{{ purchase?.lockedCommissionRate === undefined ? '—' : `${purchase.lockedCommissionRate}%` }}</b></article>
          <article><span>วันหมดอายุ</span><b>{{ purchase?.expiresAt ?? '—' }}</b></article>
          <article><span>การโหลดข้อมูล</span><b>{{ loadError || 'backend' }}</b></article>
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
            <tr v-if="loadError"><td colspan="7">{{ loadError }}</td></tr>
            <tr v-else-if="!ledger.length"><td colspan="7">ยังไม่มีรายการจัดสรรจาก backend</td></tr>
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
