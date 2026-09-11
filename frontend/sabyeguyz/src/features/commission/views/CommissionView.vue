<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import { commissionApi } from '@/api/client'
const type = ref('Sales')
const branch = ref('ทุกสาขา')
const month = ref('2026-08')
const isManager = localStorage.getItem('mockStaffRole') === 'manager'
const rows = ref<Array<{ id: string; courseId: string; date: string; type: string; patient: string; branch: string; staff: string; rule: string; base: string; commission: string; month: string }>>([])
const errorMessage = ref('')
async function loadReport() { try { const [year, number] = month.value.split('-').map(Number); if(!year||!number)return; const from=`${year}-${String(number).padStart(2,'0')}-01`; const to=new Date(year,number,0).toISOString().slice(0,10); const data=await commissionApi.report(from,to); rows.value=data.map((r,i)=>({id:String(i+1),courseId:'-',date:`${from} - ${to}`,type:'Sales',patient:`${r.saleCount} ใบเสร็จ`,branch:r.branchName,staff:r.staffName,rule:`${r.rate*100}%`,base:`฿${Number(r.base).toLocaleString()}`,commission:`฿${Number(r.commission).toLocaleString()}`,month:month.value})); errorMessage.value='' } catch(error){errorMessage.value=error instanceof Error?error.message:'โหลดค่าคอมมิชชันไม่สำเร็จ'} }
onMounted(loadReport)
watch(month,loadReport)
const visible = computed(() =>
  rows.value.filter(
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
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
        <header class="commission-head">
          <div class="tabs">
            <button disabled title="backend ยังไม่มีสูตรค่ามือ Treatment">Treatment Transaction</button
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
