<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { courseApi } from '@/api/client'
const route = useRoute()
const courseId = computed(() => String(route.params.courseId || ''))
const ledger = ref<
  Array<{
    date: string
    patient: string
    owner: string
    treating: string
    gross: number
    fee: number
    net: number
  }>
>([])
const purchase = ref<{
  patient: { fullName: string }
  course: { name: string }
  purchasedAt: string
  price: number
  used: number
  remaining: number
  commissionStatus?: string
  lockedCommissionRate?: number
  totalCourseCommissionPool?: number
  commissionAllocationPerVisit?: number
} | null>(null)
onMounted(async () => {
  const id = Number(courseId.value)
  if (!Number.isNaN(id)) {
    try {
      const data = await courseApi.ledger(id)
      purchase.value = data.purchase
      ledger.value = (data.allocations || []).map((item) => ({
        date: item.visitDate,
        patient: data.purchase.patient.fullName,
        owner: '—',
        treating: '—',
        gross: item.gross,
        fee: item.fee,
        net: item.ownerNet,
      }))
    } catch {
      /* invalid legacy display id */
    }
  }
})
const grossAllocated = computed(() => ledger.value.reduce((sum, item) => sum + item.gross, 0))
const treatmentFees = computed(() => ledger.value.reduce((sum, item) => sum + item.fee, 0))
const ownerNetReleased = computed(() => ledger.value.reduce((sum, item) => sum + item.net, 0))
const outstandingPool = computed(() =>
  Math.max(0, Number(purchase.value?.totalCourseCommissionPool || 0) - grossAllocated.value),
)
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
        ><RouterLink to="/staff/settings/commission-tiers">กฎค่าคอมมิชชัน</RouterLink
        ><RouterLink to="/staff/commission/closing">ปิดรอบรายเดือน</RouterLink>
      </nav>
      <section class="staff-panel commission-page-panel">
        <header>
          <div>
            <small>COURSE ID</small>
            <h2>{{ courseId }}</h2>
            <p class="muted-copy">วันที่ซื้อ {{ purchase?.purchasedAt || '—' }}</p>
          </div>
          <span class="status locked">ล็อกอัตราแล้ว</span>
        </header>
        <div class="detail-grid">
          <article>
            <span>ผู้ป่วย</span><b>{{ purchase?.patient.fullName || '—' }}</b>
          </article>
          <article>
            <span>แพ็กเกจ</span><b>{{ purchase?.course.name || '—' }}</b>
          </article>
          <article>
            <span>ราคาคอร์ส</span><b>฿{{ Number(purchase?.price || 0).toLocaleString('th-TH') }}</b>
          </article>
          <article>
            <span>อัตราที่ล็อก</span
            ><b>{{
              purchase?.lockedCommissionRate == null
                ? '—'
                : `${purchase.lockedCommissionRate * 100}%`
            }}</b>
          </article>
          <article>
            <span>Commission Pool</span
            ><b>฿{{ Number(purchase?.totalCourseCommissionPool || 0).toLocaleString('th-TH') }}</b>
          </article>
          <article>
            <span>คอมมิชชันต่อ Visit</span
            ><b
              >฿{{ Number(purchase?.commissionAllocationPerVisit || 0).toLocaleString('th-TH') }}</b
            >
          </article>
          <article>
            <span>ใช้แล้ว / คงเหลือ</span
            ><b>{{ purchase?.used || 0 }} / {{ purchase?.remaining || 0 }} Visits</b>
          </article>
          <article>
            <span>Gross Allocated</span><b>฿{{ grossAllocated.toLocaleString('th-TH') }}</b>
          </article>
          <article>
            <span>ค่ารักษาแทน</span><b>฿{{ treatmentFees.toLocaleString('th-TH') }}</b>
          </article>
          <article>
            <span>Owner Net Released</span><b>฿{{ ownerNetReleased.toLocaleString('th-TH') }}</b>
          </article>
          <article>
            <span>Outstanding Pool</span><b>฿{{ outstandingPool.toLocaleString('th-TH') }}</b>
          </article>
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
