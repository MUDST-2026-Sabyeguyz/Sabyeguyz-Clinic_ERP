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
    gross: number | null
    fee: number | null
    net: number | null
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
  lockedCommissionRate?: number | null
  totalCourseCommissionPool?: number | null
  commissionAllocationPerVisit?: number | null
} | null>(null)
onMounted(async () => {
  const id = Number(courseId.value)
  if (!Number.isNaN(id)) {
    try {
      // โหลด ledger ของคอร์สเพื่อดูการจัดสรรราย Visit และยอดคงค้าง
      const data = await courseApi.ledger(id)
      purchase.value = data.purchase
      ledger.value = (data.allocations || []).map((item) => ({
        date: item.visitDate,
        patient: data.purchase.patient.fullName,
        owner: '—',
        treating: '—',
        gross: item.gross ?? null,
        fee: item.fee ?? null,
        net: item.ownerNet ?? null,
      }))
    } catch {
      /* ข้าม ID เก่าที่ไม่ใช่เลขคอร์ส */
    }
  }
})
// ถ้ามีแถวใดไม่ทราบยอด จะไม่สรุปยอดรวมเป็นตัวเลขที่ทำให้เข้าใจผิด
const grossAllocated = computed(() =>
  ledger.value.some((item) => item.gross === null)
    ? null
    : ledger.value.reduce((sum, item) => sum + (item.gross ?? 0), 0),
)
const treatmentFees = computed(() =>
  ledger.value.some((item) => item.fee === null)
    ? null
    : ledger.value.reduce((sum, item) => sum + (item.fee ?? 0), 0),
)
const ownerNetReleased = computed(() =>
  ledger.value.some((item) => item.net === null)
    ? null
    : ledger.value.reduce((sum, item) => sum + (item.net ?? 0), 0),
)
const outstandingPool = computed(() => {
  const pool = purchase.value?.totalCourseCommissionPool
  if (pool == null || grossAllocated.value == null) return null
  return Math.max(0, Number(pool) - grossAllocated.value)
})
const money = (value: number | null | undefined) =>
  value == null || !Number.isFinite(Number(value))
    ? '—'
    : `฿${Number(value).toLocaleString('th-TH')}`
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
          <span class="status locked">{{ purchase?.lockedCommissionRate == null ? 'รอปิดยอดเดือนที่ขาย' : 'ล็อกอัตราแล้ว' }}</span>
        </header>
        <div class="detail-grid">
          <article>
            <span>ผู้ป่วย</span><b>{{ purchase?.patient.fullName || '—' }}</b>
          </article>
          <article>
            <span>แพ็กเกจ</span><b>{{ purchase?.course.name || '—' }}</b>
          </article>
          <article>
            <span>ราคาคอร์ส</span><b>{{ money(purchase?.price) }}</b>
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
            ><b>{{ money(purchase?.totalCourseCommissionPool) }}</b>
          </article>
          <article>
            <span>คอมมิชชันต่อ Visit</span
            ><b
              >{{ money(purchase?.commissionAllocationPerVisit) }}</b
            >
          </article>
          <article>
            <span>ใช้แล้ว / คงเหลือ</span
            ><b>{{ purchase?.used || 0 }} / {{ purchase?.remaining || 0 }} Visits</b>
          </article>
          <article>
            <span>Gross Allocated</span><b>{{ money(grossAllocated) }}</b>
          </article>
          <article>
            <span>ค่ารักษาแทน</span><b>{{ money(treatmentFees) }}</b>
          </article>
          <article>
            <span>Owner Net Released</span><b>{{ money(ownerNetReleased) }}</b>
          </article>
          <article>
            <span>Outstanding Pool</span><b>{{ money(outstandingPool) }}</b>
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
              <td>{{ money(item.gross) }}</td>
              <td>{{ money(item.fee) }}</td>
              <td>
                <b>{{ money(item.net) }}</b>
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
