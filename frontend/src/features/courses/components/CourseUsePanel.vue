<script setup lang="ts">
import { vModalFocus } from '@/features/appointments/modalFocus'
import PhoneIcon from '@/components/PhoneIcon.vue'
import { useCourseActionContext } from '../useCourseAction'

const {
  branchLabel,
  confirm,
  editingTherapist,
  errorMessage,
  formattedUsageDate,
  owner,
  returnToCourseManager,
  selectedCourse,
  selectedTreatingStaff,
  selectedUsageQuantity,
  start,
  transferReceiptId,
  treatingOptions,
  treatingStaffId,
  usageBranchName,
  usageConfirmDialog,
  usageCount,
  usageNote,
  usageReview,
  usageSubmitting,
  usageSuccess,
  usageSuccessBalance,
} = useCourseActionContext()
</script>

<template>
  <main
    class="course-use-main"
    :class="{ 'after-transfer': transferReceiptId }"
  >
    <section v-if="usageSuccess" class="course-after-use-success-backdrop">
      <article
        class="course-after-use-success-modal"
        role="region"
        aria-labelledby="after-use-success-title"
      >
        <span class="usage-success-check" aria-hidden="true">✓</span>
        <h1 id="after-use-success-title">ใช้สิทธิ์สำเร็จ</h1>
        <p>บันทึกการใช้สิทธิ์ {{ selectedCourse.name }} {{ usageCount }} ครั้งแล้ว</p>
        <dl class="usage-success-details">
          <div><dt>ผู้ป่วย</dt><dd>{{ owner?.fullName }}</dd></div>
          <div><dt>เลขประจำตัวผู้ป่วย</dt><dd>{{ owner?.hn }}</dd></div>
          <div><dt>คอร์ส</dt><dd>{{ selectedCourse.name }}</dd></div>
          <div><dt>ใช้สิทธิ์ครั้งนี้</dt><dd>{{ usageCount }} ครั้ง</dd></div>
          <div><dt>สิทธิ์คงเหลือ</dt><dd>{{ usageSuccessBalance }} ครั้ง</dd></div>
        </dl>
        <footer>
          <button type="button" @click="returnToCourseManager">กลับหน้าคอร์ส</button>
          <button type="button" @click="start('history', selectedCourse)">
            ดูประวัติการใช้คอร์ส
          </button>
        </footer>
      </article>
    </section>
    <section v-else-if="usageReview && transferReceiptId" class="course-after-use-confirm">
      <h1>ตรวจสอบการใช้สิทธิ์คอร์ส</h1>
      <div class="course-after-use-details">
        <h2>{{ owner?.fullName }}　HN {{ owner?.hn }}</h2>
        <h3>{{ selectedCourse.name }}</h3>
        <p>วันที่รับบริการ　{{ formattedUsageDate }}</p>
        <p>นักกายภาพ　{{ selectedTreatingStaff?.fullName || '—' }}</p>
        <p>สาขา　{{ usageBranchName }}</p>
        <strong>สิทธิ์คงเหลือ</strong>
        <b
          >{{ selectedCourse.balance }} <small>/ {{ selectedCourse.total }} ครั้ง</small></b
        >
        <span>หมดอายุ {{ selectedCourse.expiry }}</span>
      </div>
      <footer>
        <button type="button" @click="returnToCourseManager">ยกเลิกการใช้สิทธิ์</button>
        <button
          type="button"
          :disabled="!selectedCourse.usable"
          @click="usageConfirmDialog = true"
        >
          ใช้สิทธิ์คอร์ส
        </button>
      </footer>
    </section>
    <section v-else-if="usageReview" class="course-use-review">
      <button class="course-review-back app-back-link" type="button" @click="returnToCourseManager">
        ← กลับ
      </button>
      <h1>ตรวจสอบการใช้สิทธิ์</h1>
      <p class="course-use-review-intro">ตรวจข้อมูลผู้ป่วยและรายละเอียดบริการก่อนยืนยัน</p>
      <section class="course-review-patient">
        <i>{{ owner?.fullName?.replace(/^(นาย|นางสาว|นาง)/, '').slice(0, 2) }}</i>
        <div>
          <strong>{{ owner?.fullName }}</strong
          ><span class="patient-contact-inline"
            >HN {{ owner?.hn }} <PhoneIcon v-if="owner?.phone" />
            {{ owner?.phone || '-' }}</span
          >
        </div>
        <b>{{ branchLabel(owner) }}</b>
      </section>
      <div class="course-review-columns">
        <section class="course-review-details">
          <h2>ข้อมูลการรับบริการ</h2>
          <dl>
            <div>
              <dt>ผู้ป่วย</dt>
              <dd>{{ owner?.fullName }} · HN {{ owner?.hn }}</dd>
            </div>
            <div>
              <dt>คอร์ส</dt>
              <dd>{{ selectedCourse.name }}</dd>
            </div>
            <div>
              <dt>Course ID</dt>
              <dd>{{ selectedCourse.course?.code }}</dd>
            </div>
            <div>
              <dt>วันที่รับบริการ</dt>
              <dd>{{ formattedUsageDate }}</dd>
            </div>
            <div>
              <dt>นักกายภาพ</dt>
              <dd class="review-therapist-row">
                <select
                  v-if="editingTherapist"
                  v-model.number="treatingStaffId"
                  class="review-therapist-select"
                  aria-label="เลือกนักกายภาพ"
                >
                  <option :value="0" disabled>เลือกนักกายภาพ</option>
                  <option v-for="person in treatingOptions" :key="person.id" :value="person.id">
                    {{ person.fullName }}
                  </option>
                </select>
                <span v-else>{{ selectedTreatingStaff?.fullName || '—' }}</span>
                <button
                  v-if="!editingTherapist"
                  type="button"
                  class="review-therapist-edit"
                  aria-label="แก้ไขนักกายภาพ"
                  @click="editingTherapist = true"
                >
                  <svg viewBox="0 0 512 512" width="16" height="16" aria-hidden="true">
                    <path
                      fill="currentColor"
                      d="M362 0c-10 0-20 4-28 12L12 374a30 30 0 0 0-8 18L0 470a30 30 0 0 0 32 32l78-4c7 0 14-3 19-8L500 126c16-16 16-40 0-56L418 12C405 4 384 0 362 0zM332 112l68 68L112 458l-64 4 4-62z"
                    />
                  </svg>
                </button>
              </dd>
            </div>
            <div>
              <dt>สาขา</dt>
              <dd>{{ usageBranchName }}</dd>
            </div>
            <div>
              <dt>จำนวนที่ใช้ครั้งนี้</dt>
              <dd>1 ครั้ง</dd>
            </div>
          </dl>
          <label class="course-review-note">
            หมายเหตุ (ไม่บังคับ)
            <textarea v-model.trim="usageNote" rows="3" placeholder="ระบุรายละเอียดการใช้บริการ" />
          </label>
        </section>
        <aside class="course-review-summary">
          <h2>สรุปสิทธิ์</h2>
          <div>
            <span>สิทธิ์คงเหลือหลังทำรายการ</span>
            <strong
              >{{ Math.max(0, selectedCourse.balance - selectedUsageQuantity) }} ครั้ง</strong
            >
            <small>ใช้สิทธิ์ครั้งนี้ {{ usageCount }} ครั้ง</small>
          </div>
          <footer>
            <button
              type="button"
              :disabled="!selectedCourse.usable"
              @click="usageConfirmDialog = true"
            >
              ยืนยันใช้สิทธิ์
            </button>
          </footer>
        </aside>
      </div>
    </section>
    <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
    <div
      v-if="usageConfirmDialog"
      class="course-confirm-backdrop"
      @click.self="usageConfirmDialog = false"
    >
      <section
        v-modal-focus="{ close: () => (usageConfirmDialog = false), busy: usageSubmitting }"
        class="course-confirm-dialog course-usage-confirm"
        :class="{ 'after-transfer': transferReceiptId }"
        role="dialog"
        aria-modal="true"
        aria-labelledby="course-confirm-title"
      >
        <h2 id="course-confirm-title">
          {{ transferReceiptId ? 'ยืนยันการใช้สิทธิ์ 1 ครั้ง' : 'ยืนยันการใช้สิทธิ์' }}
        </h2>
        <p>
          {{
            transferReceiptId
              ? 'ตรวจสอบยอดคงเหลือหลังใช้สิทธิ์ก่อนยืนยัน'
              : 'โปรดตรวจสอบจำนวนสิทธิ์ก่อนทำรายการ'
          }}
        </p>
        <div
          v-if="transferReceiptId"
          class="course-confirm-details course-after-confirm-details"
        >
          <strong>{{ owner?.fullName }}　HN {{ owner?.hn }}</strong>
          <b>{{ selectedCourse.name }}</b>
          <p>
            จำนวนคงเหลือ {{ selectedCourse.balance }}/{{ selectedCourse.total }}
            ครั้ง
          </p>
        </div>
        <div v-else class="course-confirm-details">
          <strong>{{ selectedCourse.name }} · ใช้ 1 ครั้ง</strong>
          <p>
            <span>ผู้ป่วย</span><b>{{ owner?.fullName }}</b>
          </p>
          <p>
            <span>คงเหลือก่อนใช้</span><b>{{ selectedCourse.balance }} ครั้ง</b>
          </p>
          <p>
            <span>คงเหลือหลังใช้</span
            ><b>{{ Math.max(0, selectedCourse.balance - selectedUsageQuantity) }} ครั้ง</b>
          </p>
        </div>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
        <div class="course-confirm-actions">
          <button type="button" @click="usageConfirmDialog = false">ยกเลิก</button
          ><button type="button" :disabled="usageSubmitting" @click="confirm">
            {{ usageSubmitting ? 'กำลังบันทึก...' : 'ยืนยัน' }}
          </button>
        </div>
      </section>
    </div>
  </main>
</template>
<style scoped src="../styles/course-use-panel.css"></style>
