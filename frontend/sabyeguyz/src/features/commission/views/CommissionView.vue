<script setup lang="ts">
import { computed, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
const type = ref('Treatment')
const branch = ref('ทุกสาขา')
const month = ref('2026-08')
const isManager = localStorage.getItem('mockStaffRole') === 'manager'
const configuredRules = JSON.parse(localStorage.getItem('clinicCommissionRules') || '[]') as Array<{
  name: string
  type: string
  status: string
}>
const treatmentRule =
  configuredRules.find(
    (item) =>
      ['Treating Staff', 'ผู้ให้บริการ', 'นักกายภาพบำบัด'].includes(item.type) &&
      item.status === 'Active',
  )?.name ?? 'ค่าคอมมิชชันการให้บริการ'
const salesRule =
  configuredRules.find(
    (item) => ['Salesperson', 'ผู้ขาย'].includes(item.type) && item.status === 'Active',
  )?.name ?? 'ค่าคอมมิชชันการขายคอร์ส 5%'
const rows = [
  {
    id: 'TRT-260822-0018',
    courseId: 'RE-2206315-01',
    date: '22 ส.ค. 2026',
    type: 'Treatment',
    patient: 'สมชาย ใจดี',
    branch: 'ราชพฤกษ์',
    staff: 'กฤษดา น.',
    rule: treatmentRule,
    base: '฿1,000',
    commission: '฿300',
    month: '2026-08',
  },
  {
    id: 'TRT-260822-0022',
    courseId: 'RE-2206315-02',
    date: '22 ส.ค. 2026',
    type: 'Treatment',
    patient: 'วรรณา มั่นคง',
    branch: 'สุขุมวิท',
    staff: 'ศิริพร ว.',
    rule: treatmentRule,
    base: '฿1,200',
    commission: '฿300',
    month: '2026-08',
  },
  {
    id: 'PAY-260822-0021',
    courseId: 'RE-2206315-01',
    date: '22 ส.ค. 2026',
    type: 'Sales',
    patient: 'สมชาย ใจดี',
    branch: 'ราชพฤกษ์',
    staff: 'พิมพ์นารา ว.',
    rule: salesRule,
    base: '฿8,500',
    commission: '฿425',
    month: '2026-08',
  },
]
const visible = computed(() =>
  rows.filter(
    (row) =>
      row.type === type.value &&
      row.month === month.value &&
      (branch.value === 'ทุกสาขา' || row.branch === branch.value),
  ),
)
const total = computed(() =>
  visible.value.reduce((sum, row) => sum + Number(row.commission.replace(/[^0-9]/g, '')), 0),
)
const periodLabel = computed(() => {
  const [year, monthNumber] = month.value.split('-').map(Number)
  if (!year || !monthNumber) return '—'
  return new Intl.DateTimeFormat('th-TH', { month: 'short', year: 'numeric' }).format(
    new Date(year, monthNumber - 1),
  )
})
</script>
<template>
  <div class="staff-page">
    <StaffHeader />
    <main class="staff-main">
      <section class="staff-hero">
        <small>STAFF AND COMMISSION</small>
        <h1>บุคลากรและค่าคอมมิชชัน</h1>
        <p>ตรวจสอบข้อมูลจาก Treatment และ Sales Transaction ที่เกิดขึ้นแล้ว</p>
      </section>
      <nav v-if="isManager" class="commission-nav">
        <RouterLink to="/staff/commission">ภาพรวม</RouterLink>
        <RouterLink to="/staff/commission/tiers">Commission Tier</RouterLink>
        <RouterLink to="/staff/commission/closing">ปิดรอบรายเดือน</RouterLink>
      </nav>
      <section class="summary-strip commission-summary" :class="{ 'with-nav': isManager }">
        <article>
          <span>รายการที่แสดง</span><b>{{ visible.length }} รายการ</b>
        </article>
        <article>
          <span>ค่ามือ/คอมมิชชันรวม</span><b>฿{{ total.toLocaleString() }}</b>
        </article>
        <article>
          <span>สาขา</span><b>{{ branch }}</b>
        </article>
        <article>
          <span>รอบคำนวณ</span><b>{{ periodLabel }}</b>
        </article>
      </section>
      <section class="staff-panel commission-panel">
        <header class="commission-head">
          <div class="tabs">
            <button :class="{ active: type === 'Treatment' }" @click="type = 'Treatment'">
              Treatment Transaction</button
            ><button :class="{ active: type === 'Sales' }" @click="type = 'Sales'">
              Sales Transaction
            </button>
          </div>
          <div class="filters">
            <select v-model="branch">
              <option>ทุกสาขา</option>
              <option>ราชพฤกษ์</option>
              <option>สุขุมวิท</option></select
            ><input v-model="month" type="month" />
          </div>
        </header>
        <table class="staff-table">
          <thead>
            <tr>
              <th>เลขที่รายการ</th>
              <th>วันที่ทำรายการ</th>
              <th>รหัสคอร์ส</th>
              <th>ผู้ป่วย</th>
              <th>สาขา</th>
              <th>{{ type === 'Treatment' ? 'นักกายภาพบำบัด' : 'ผู้ขาย' }}</th>
              <th>เกณฑ์คอมมิชชัน</th>
              <th>{{ type === 'Treatment' ? 'จำนวนเงินที่จ่าย' : 'จำนวนเงินที่ขาย' }}</th>
              <th>ค่าคอมมิชชัน</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visible" :key="row.id">
              <td>
                <b>{{ row.id }}</b>
              </td>
              <td>{{ row.date }}</td>
              <td>
                <RouterLink :to="`/staff/commission/courses/${row.courseId}`">
                  {{ row.courseId }}
                </RouterLink>
              </td>
              <td>{{ row.patient }}</td>
              <td>{{ row.branch }}</td>
              <td>
                <b>{{ row.staff }}</b>
              </td>
              <td>{{ row.rule }}</td>
              <td>{{ row.base }}</td>
              <td>
                <b class="money">{{ row.commission }}</b>
              </td>
            </tr>
            <tr v-if="!visible.length">
              <td colspan="9">ไม่พบข้อมูลตามตัวกรอง</td>
            </tr>
          </tbody>
        </table>
        <p class="audit-note">
          ข้อมูลหน้านี้อ้างอิงจาก Transaction และ Commission Rule ไม่ได้กรอกยอดใหม่ในหน้านี้
        </p>
      </section>
    </main>
  </div>
</template>
<style scoped>
@import '../../staff/styles/staff.css';
@import '../styles/commission-workspace.css';
.commission-summary {
  margin-top: -56px;
}
.commission-summary.with-nav {
  margin-top: 0;
}
.commission-panel {
  margin-bottom: 40px;
}
.commission-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--physio-line);
}
.tabs,
.filters {
  display: flex;
  gap: 8px;
}
.tabs button {
  padding: 10px 15px;
  border: 1px solid var(--physio-line);
  border-radius: 9px;
  color: var(--physio-text);
  background: #fff;
  font: inherit;
  font-weight: 700;
}
.tabs button.active {
  color: #fff;
  background: var(--physio-blue);
}
.filters select,
.filters input {
  padding: 9px 11px;
  border: 1px solid var(--physio-line);
  border-radius: 8px;
  background: #fff;
  font: inherit;
}
.money {
  color: var(--physio-blue);
}
.audit-note {
  margin: 0;
  padding: 13px 20px;
  background: #f0f3f5;
  color: var(--physio-muted);
}
@media (max-width: 760px) {
  .commission-head {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }
  .tabs,
  .filters {
    overflow-x: auto;
  }
  .commission-panel {
    overflow: auto;
  }
  .commission-summary {
    margin-top: 20px;
  }
}
</style>
