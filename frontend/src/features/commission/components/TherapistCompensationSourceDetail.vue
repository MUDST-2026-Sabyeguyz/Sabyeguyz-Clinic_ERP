<script setup lang="ts">
import TherapistCompensationEmptyState from './TherapistCompensationEmptyState.vue'

const props = defineProps<{ kind: 'sale' | 'treatment' }>()

const saleRows = [
  ['ประเภทบริการ', '—'],
  ['ยอดก่อนส่วนลด', '—'],
  ['ส่วนลดรายการ', '—'],
  ['ยอดหลังส่วนลดรายการ', '—'],
]
</script>

<template>
  <section v-if="props.kind === 'sale'" class="therapist-source-card therapist-sale-source">
    <header class="therapist-source-heading">
      <h1>รายละเอียดรายการขายต้นทาง</h1>
      <p>— · — · — · อ่านอย่างเดียว</p>
    </header>

    <section class="therapist-source-patient-grid" aria-label="ข้อมูลผู้ป่วยและบริการ">
      <div>
        <span>ผู้ป่วย</span>
        <b>—</b>
      </div>
      <div>
        <span>เลข HN</span>
        <b>—</b>
      </div>
      <div>
        <span>บริการ</span>
        <b>—</b>
      </div>
    </section>

    <div class="therapist-sale-source-grid">
      <section class="therapist-source-info-card">
        <h2>ข้อมูลรายการขาย</h2>
        <dl class="therapist-source-value-list">
          <div
            v-for="([label, value], index) in saleRows"
            :key="label"
            :class="{ 'is-last': index === saleRows.length - 1 }"
          >
            <dt>{{ label }}</dt>
            <dd>{{ value }}</dd>
          </div>
        </dl>
      </section>

      <section class="therapist-source-info-card therapist-source-compensation-card">
        <h2>ที่มาของค่าตอบแทน</h2>
        <span>ฐาน Commission</span>
        <strong>—</strong>
        <p>Tier —</p>
        <b>— × — = —</b>
        <p class="therapist-source-rule-note">
          ส่วนลดรายการลดฐาน Commission ส่วนลดท้ายบิลไม่ลดฐาน Commission
        </p>
      </section>
    </div>

    <footer class="therapist-source-footer">
      <p>หากข้อมูลผิด ให้แจ้ง Manager นอกระบบเพื่อดำเนินการตามสถานะรอบ</p>
      <RouterLink class="therapist-primary-action" :to="{ name: 'therapist-commission-detail' }">
        กลับรายละเอียดค่าคอมมิชชัน
      </RouterLink>
    </footer>
  </section>

  <section v-else class="therapist-source-card">
    <h1>รายการรักษาต้นทาง</h1>
    <div class="therapist-patient-strip">
      <span>ผู้ป่วย / HN</span><b>—</b><span>บริการ</span><b>—</b>
    </div>
    <div class="therapist-source-grid">
      <article>
        <h2>ข้อมูลรายการ</h2>
        <p>—</p>
      </article>
      <article>
        <h2>ฐานค่าตอบแทน</h2>
        <p>—</p>
      </article>
    </div>
    <TherapistCompensationEmptyState />
  </section>
</template>
