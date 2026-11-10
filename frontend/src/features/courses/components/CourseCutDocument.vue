<script setup lang="ts">
defineProps<{
  courseName?: string
  courseNo?: string
  branchName?: string
  address?: string
  phone?: string
  buyer?: string
  issuedAt?: string
  purchasedAt?: string
  expiresAt?: string
  version?: number
  total?: number
  used?: number
  remaining?: number
  entries: Array<{ date?: string; therapist?: string; patient?: string; quantity?: number }>
}>()

function formatDate(value?: string) {
  const date = value ? new Date(value) : null
  if (!date || Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString('th-TH', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: 'Asia/Bangkok',
  })
}
</script>

<template>
  <article class="course-cut-document">
    <header>
      <h1>ใบตัดคอร์สการรักษา</h1>
      <p v-if="address">ที่อยู่: {{ address }}</p>
      <p v-if="phone">โทรศัพท์: {{ phone }}</p>
    </header>
    <p class="issued">
      <template v-if="version != null">ฉบับ {{ version }} · </template>
      ออก ณ วันที่: {{ formatDate(issuedAt) }}
    </p>
    <div v-if="courseNo" class="references">
      <span v-if="courseNo">เลขคอร์ส: {{ courseNo }}</span>
    </div>
    <section class="summary">
      <p>วันที่ซื้อคอร์ส {{ formatDate(purchasedAt) }}</p>
      <p>จำนวน: {{ total ?? '—' }} ครั้ง</p>
      <p>ใช้ไปแล้ว: {{ used ?? '—' }} ครั้ง</p>
      <p>คงเหลือ: {{ remaining ?? '—' }} ครั้ง</p>
      <strong>ชื่อคอร์ส: {{ courseName || '—' }}</strong>
      <strong>วันหมดอายุ: {{ formatDate(expiresAt) }}</strong>
    </section>
    <div class="table-wrap">
      <table aria-label="ประวัติการใช้สิทธิ์คอร์ส">
        <thead>
          <tr>
            <th scope="col">วันที่เข้ารักษา</th>
            <th scope="col">นักกายภาพที่ทำการรักษา</th>
            <th scope="col">ผู้ใช้งานแพ็กเกจ</th>
            <th scope="col">จำนวน</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(entry, index) in entries" :key="index">
            <td>{{ formatDate(entry.date) }}</td>
            <td>{{ entry.therapist || '—' }}</td>
            <td>{{ entry.patient || '—' }}</td>
            <td>{{ entry.quantity ?? '—' }}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <th colspan="3" scope="row">รวมจำนวนที่ใช้</th>
            <td>{{ used ?? '—' }}</td>
          </tr>
        </tfoot>
      </table>
    </div>
  </article>
</template>

<style scoped>
.course-cut-document {
  box-sizing: border-box;
  width: min(1020px, 100%);
  margin-inline: auto;
  padding: 26px 33px;
  border: 1px solid #d8e1de;
  border-radius: 2px;
  color: #25372f;
  background: #fff;
}
header { padding-bottom: 10px; border-bottom: 2px solid #25372f; text-align: center; }
h1 { margin: 0 0 10px; font-size: 22px; }
header p { margin: 0; color: #52635c; font-size: 14px; }
.issued { margin: 12px 0 8px; color: #66766f; font-size: 14px; text-align: right; }
.references, .summary { display: grid; gap: 14px 20px; margin-top: 8px; font-size: 14px; }
.references { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.summary { grid-template-columns: 1.3fr .7fr .8fr .8fr; }
.summary p { margin: 0; }
.summary strong:first-of-type { grid-column: 1 / 4; }
.table-wrap { margin-top: 18px; overflow-x: auto; }
table { width: 100%; border-collapse: collapse; border: 1px solid #25372f; font-size: 12px; }
th, td { padding: 14px 12px; border-bottom: 1px solid #25372f; text-align: left; }
thead { background: #e8f5f0; }
tfoot th { text-align: right; }
@media screen and (max-width: 760px) {
  .course-cut-document { padding: 22px 16px; }
  .summary { grid-template-columns: 1fr; }
  .summary strong:first-of-type { grid-column: auto; }
}
@media print {
  .course-cut-document { width: 100%; margin: 0; padding: 0; border: 0; print-color-adjust: exact; }
  .table-wrap { overflow: visible; }
  thead { display: table-header-group; }
  tfoot { display: table-row-group; }
  tr { break-inside: avoid; }
}
</style>
