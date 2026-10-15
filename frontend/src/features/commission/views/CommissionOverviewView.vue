<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import StaffHeader from '@/features/staff/components/StaffHeader.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'
import ThaiDatePicker from '@/features/staff/components/ThaiDatePicker.vue'
import CommissionTabs from './CommissionTabs.vue'
import { commissionApi } from '@/api/client'

type PersonRow = {
  id: string
  name: string
  tier: string
  base: number
  commission: number
  treatment: number
  branch: string
  status: 'pending' | 'approved'
}
const rows = ref<PersonRow[]>([])
const errorMessage = ref('')
const search = ref('')
const branch = ref('')
const status = ref('')
const date = ref(new Date().toISOString().slice(0, 10))
const visible = computed(() =>
  rows.value.filter(
    (row) =>
      (!search.value ||
        row.name.includes(search.value) ||
        row.id.toLowerCase().includes(search.value.toLowerCase())) &&
      (!branch.value || row.branch === branch.value) &&
      (!status.value || row.status === status.value),
  ),
)
const sales = computed(() => visible.value.reduce((s, r) => s + r.base, 0))
const pool = computed(() => visible.value.reduce((s, r) => s + r.commission, 0))
const treatment = computed(() => visible.value.reduce((s, r) => s + r.treatment, 0))
const money = (value: number) => `฿${value.toLocaleString('th-TH')}`
onMounted(async () => {
  try {
    const period = date.value.slice(0, 7)
    const [year, month] = period.split('-').map(Number)
    const lastDay = String(new Date(year!, month!, 0).getDate()).padStart(2, '0')
    const [data, closings] = await Promise.all([
      commissionApi.report(`${period}-01`, `${period}-${lastDay}`),
      commissionApi.closings(period),
    ])
    const closingStatus = new Map(closings.map((item) => [item.employeeId, item.status]))
    const realRows: PersonRow[] = data
      .filter((r) => r.staffId !== null)
      .map((r) => ({
        id: String(r.staffId),
        name: r.staffName,
        tier: `อัตรา ${Math.round(r.rate * 100)}%`,
        base: Number(r.base),
        commission: Number(r.commission),
        treatment: 0,
        branch: r.branchCode,
        status: ['CLOSED', 'FINALIZED'].includes(closingStatus.get(r.staffId!) ?? '') ? 'approved' : 'pending',
      }))
    rows.value = realRows
  } catch (error) {
    rows.value = []
    errorMessage.value = error instanceof Error ? error.message : 'โหลดข้อมูลคอมมิชชันไม่สำเร็จ'
  }
})
</script>
<template>
  <div class="commission-manager-page">
    <StaffHeader />
    <main class="commission-manager-main">
      <header class="commission-title">
        <h1>ค่าคอมมิชชันและค่ารักษาแทน</h1>
        <p>ตรวจสอบฐานคำนวณ Tier อนุมัติยอด และติดตามการจ่ายค่าตอบแทน</p>
      </header>
      <CommissionTabs active="overview" />
      <p v-if="errorMessage" class="state-card error" role="alert">{{ errorMessage }}</p>
      <section class="commission-metrics">
        <article>
          <span>ยอดขายสุทธิ</span><b>{{ money(sales) }}</b
          ><small>ฐานหลังหักส่วนลดรายการ</small>
        </article>
        <article class="highlight">
          <span>Commission Pool</span><b>{{ money(pool) }}</b
          ><small>รวมค่าคอมมิชชันเดือนนี้</small>
        </article>
        <article>
          <span>ค่ารักษาแทน</span><b>{{ money(treatment) }}</b
          ><small>18 รายการ</small>
        </article>
        <article class="warning">
          <span>ยอดรอจ่าย</span><b>{{ money(pool + treatment) }}</b
          ><small>รอตรวจสอบ {{ visible.length }} คน</small>
        </article>
      </section>
      <section class="overview-filters commission-card">
        <label
          >ค้นหานักกายภาพ<input v-model="search" placeholder="ค้นหาชื่อหรือรหัสพนักงาน" /></label
        ><label
          >สาขา<FilterDropdown
            v-model="branch"
            default-value=""
            default-label="ทุกสาขา"
            label="สาขา"
            :options="[
              { value: 'BR', label: 'สาขาแบริ่ง' },
              { value: 'R9', label: 'สาขาพระราม 9' },
            ]" /></label
        ><label
          >รอบคำนวณ<ThaiDatePicker
            v-model="date"
            numeric-month
            show-calendar-icon
            hide-caret /></label
        ><label
          >สถานะ<FilterDropdown
            v-model="status"
            default-value=""
            default-label="ทุกสถานะ"
            label="สถานะ"
            :options="[
              { value: 'pending', label: 'รอตรวจสอบ' },
              { value: 'approved', label: 'อนุมัติแล้ว' },
            ]"
        /></label>
      </section>
      <div class="commission-table-wrap overview-table-scroll">
        <table class="commission-table">
          <thead>
            <tr>
              <th>นักกายภาพ</th>
              <th>Tier / อัตรา</th>
              <th>ฐานคำนวณ</th>
              <th>Commission</th>
              <th>ค่ารักษาแทน</th>
              <th>สถานะ</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visible" :key="row.id">
              <td>
                <b>{{ row.name }}</b>
              </td>
              <td>{{ row.tier }}</td>
              <td>{{ money(row.base) }}</td>
              <td>{{ money(row.commission) }}</td>
              <td>{{ money(row.treatment) }}</td>
              <td>
                <span class="approval-status" :class="row.status">{{
                  row.status === 'approved' ? 'อนุมัติแล้ว' : 'รอตรวจสอบ'
                }}</span>
              </td>
              <td>
                <RouterLink class="detail-button" :to="`/staff/commission/staff/${row.id}`"
                  >ดูรายละเอียด</RouterLink
                >
              </td>
            </tr>
          </tbody>
        </table>
        <footer>รวม {{ visible.length }} คน · อัปเดตล่าสุด 28 ก.ย. 2569 16:05</footer>
      </div>
    </main>
  </div>
</template>
<style scoped>
@import '../styles/commission-manager.css';
.overview-filters {
  display: grid;
  grid-template-columns: 1.4fr 0.8fr 0.9fr 0.8fr;
  gap: 18px;
  padding: 20px;
  margin-bottom: 24px;
}
.overview-filters label {
  display: grid;
  gap: 7px;
  color: #667a72;
  font-size: 0.86rem;
}
.overview-filters input {
  height: 44px;
  padding: 0 14px;
  border: 1px solid #d9e1dc;
  border-radius: 8px;
  font: inherit;
}
.overview-filters :deep(.date-trigger),
.overview-filters :deep(.filter-dropdown__button) {
  min-height: 44px;
}
.detail-button {
  display: inline-flex;
  padding: 10px 18px;
  border-radius: 7px;
  background: #1e8b86;
  color: #fff;
  font-weight: 700;
  text-decoration: none;
}
.approval-status {
  display: inline-flex;
  justify-content: center;
  min-width: 92px;
  padding: 7px 12px;
  border-radius: 999px;
  font-size: 0.86rem;
  font-weight: 700;
}
.approval-status.pending {
  background: #fff1c7;
  color: #8a6514;
}
.approval-status.approved {
  background: #def3e7;
  color: #167452;
}
.approval-status.returned {
  background: #ffe2dc;
  color: #b43a2f;
}
.commission-table-wrap footer {
  padding: 18px;
  background: #eff5ef;
  color: #667a72;
}
.overview-table-scroll {
  scrollbar-width: thin;
  scrollbar-color: #1e8b86 #e8f1ef;
}
.overview-table-scroll::-webkit-scrollbar {
  height: 8px;
}
.overview-table-scroll::-webkit-scrollbar-track {
  background: #e8f1ef;
}
.overview-table-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #1e8b86;
}
.overview-table-scroll::-webkit-scrollbar-thumb:hover {
  background: #176f6b;
}
@media (max-width: 900px) {
  .overview-filters {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 560px) {
  .overview-filters {
    grid-template-columns: 1fr;
  }
}
</style>
