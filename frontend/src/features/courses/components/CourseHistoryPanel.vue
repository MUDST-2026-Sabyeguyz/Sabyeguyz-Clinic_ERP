<script setup lang="ts">
import { clinicToday } from '@/utils/clinicDate'
import CourseCutDocument from '../components/CourseCutDocument.vue'
import { vModalFocus } from '@/features/appointments/modalFocus'
import PhoneIcon from '@/components/PhoneIcon.vue'
import { useCourseActionContext } from '../useCourseAction'

const {
  branchLabel,
  cancelPatientCourseDetail,
  changeInactiveHistoryCourse,
  closePrintSuccess,
  confirmPrintHistory,
  coursePrintPreview,
  documentClinicAddress,
  documentClinicPhone,
  errorMessage,
  historyFromInactive,
  historyRemaining,
  inactiveCourses,
  owner,
  printDialog,
  returnToCourseManager,
  selectedCourse,
  selectedCourseId,
  selectedRecipient,
  transferAmount,
  transferReceiptId,
  transferredOut,
  usageEntries,
  usedSessions,
} = useCourseActionContext()
</script>

<template>
  <main
    class="course-history-main"
    :class="{
      'after-transfer': transferReceiptId && !historyFromInactive,
      'inactive-history': historyFromInactive,
    }"
  >
    <section v-if="coursePrintPreview" class="course-cut-preview">
      <button class="course-cut-back-link app-back-link" type="button" @click="cancelPatientCourseDetail">
        ← กลับ
      </button>
      <CourseCutDocument
        class="course-history-document"
        :course-name="selectedCourse.name"
        :address="documentClinicAddress"
        :phone="documentClinicPhone"
        :issued-at="clinicToday()"
        :purchased-at="selectedCourse.purchasedAt"
        :expires-at="selectedCourse.expiresAt"
        :total="selectedCourse.total"
        :used="usedSessions"
        :remaining="selectedCourse.balance"
        :entries="usageEntries.map((entry) => ({
          date: entry.createdAt,
          therapist: entry.therapistName ?? undefined,
          patient: entry.patientName ?? undefined,
          quantity: Math.abs(entry.quantity),
        })).reverse()"
      />
      <footer class="course-cut-actions">
        <button type="button" @click="printDialog = 'confirm'">พิมพ์เอกสาร</button>
      </footer>
    </section>
    <template v-else>
      <button class="course-history-back app-back-link" type="button" @click="returnToCourseManager">
        ← กลับไปหน้าจัดการคอร์ส
      </button>
      <h1>
        {{ transferReceiptId && !historyFromInactive ? 'ประวัติคอร์ส' : 'ประวัติการใช้คอร์ส' }}
      </h1>
      <p class="course-history-intro">
        {{
          transferReceiptId || historyFromInactive
            ? ''
            : 'ตรวจสอบรายการใช้สิทธิ์และพิมพ์ใบตัดคอร์ส'
        }}
      </p>
      <section class="course-history-patient">
        <i>{{ owner?.fullName?.replace(/^(นาย|นางสาว|นาง)/, '').slice(0, 2) || '--' }}</i>
        <div>
          <strong>{{ owner?.fullName }}</strong>
          <span
            >HN {{ owner?.hn
            }}<template v-if="!transferReceiptId || historyFromInactive"
              ><PhoneIcon v-if="owner?.phone" /> {{ owner?.phone || '-' }}</template
            ></span
          >
        </div>
        <em>{{ branchLabel(owner) }}</em>
      </section>
      <label
        v-if="historyFromInactive && inactiveCourses.length > 1"
        class="course-history-selector"
        >คอร์สที่หมดอายุ / ใช้สิทธิ์ครบ<select
          v-model="selectedCourseId"
          @change="changeInactiveHistoryCourse"
        >
          <option v-for="course in inactiveCourses" :key="course.id" :value="course.id">
            {{ course.name }} ({{ course.course.code }})
          </option>
        </select></label
      >
      <template v-if="selectedCourse.id">
        <section
          v-if="historyFromInactive"
          class="course-after-history-totals course-inactive-history-totals"
          aria-label="สรุปคอร์สหมดอายุหรือใช้สิทธิ์ครบ"
        >
          <h2>{{ selectedCourse.name }} ({{ selectedCourse.course?.code }})</h2>
          <div>
            <article>
              <span>ทั้งหมด</span><strong>{{ selectedCourse.total }} ครั้ง</strong>
            </article>
            <article>
              <span>ใช้แล้ว</span
              ><strong>{{ selectedCourse.total - selectedCourse.balance }} ครั้ง</strong>
            </article>
            <article><span>โอนออก</span><strong>0 ครั้ง</strong></article>
            <article>
              <span>คงเหลือ</span><strong>{{ historyRemaining }} ครั้ง</strong>
            </article>
          </div>
        </section>
        <section
          v-if="transferReceiptId && !historyFromInactive"
          class="course-after-history-totals"
          aria-label="สรุปประวัติคอร์ส"
        >
          <h2>{{ selectedCourse.name }} · {{ selectedCourse.course?.code }}</h2>
          <div>
            <article>
              <span>ทั้งหมด</span><strong>{{ selectedCourse.total }} ครั้ง</strong>
            </article>
            <article>
              <span>ใช้แล้ว</span><strong>{{ usedSessions }} ครั้ง</strong>
            </article>
            <article>
              <span>โอนออก</span><strong>{{ transferAmount }}</strong>
            </article>
            <article>
              <span>คงเหลือ</span><strong>{{ selectedCourse.balance }}</strong>
            </article>
          </div>
        </section>
        <section
          v-if="!historyFromInactive"
          class="course-history-summary"
          aria-label="สรุปสิทธิ์คอร์ส"
        >
          <div>
            <h2>{{ selectedCourse.name }}</h2>
            <p>
              {{ selectedCourse.course?.code }} · ซื้อเมื่อ
              {{
                new Date(selectedCourse.purchasedAt).toLocaleDateString('th-TH', {
                  day: 'numeric',
                  month: 'long',
                  year: 'numeric',
                })
              }}
            </p>
          </div>
          <div>
            <strong>คงเหลือ {{ historyRemaining }} ครั้ง</strong
            ><span
              >ทั้งหมด {{ selectedCourse.total }} · ใช้แล้ว {{ usedSessions }} · แชร์
              {{ transferredOut }}</span
            >
          </div>
        </section>
        <section class="course-history-entries">
          <div class="course-history-entries-header">
            <h2>
              {{
                historyFromInactive
                  ? 'รายการใช้บริการ'
                  : transferReceiptId
                    ? 'รายการใช้บริการและการโอน'
                    : `รายการใช้สิทธิ์ ${usageEntries.length} รายการ`
              }}
            </h2>
            <button
              v-if="!transferReceiptId && !historyFromInactive"
              class="course-history-print"
              type="button"
              @click="coursePrintPreview = true"
            >
              ดูใบตัดคอร์ส
            </button>
          </div>
          <article
            v-if="transferReceiptId && !historyFromInactive"
            class="course-after-transfer-entry"
          >
            <strong>โอนสิทธิ์ · รายการ #{{ transferReceiptId }}</strong>
            <span
              >{{ owner?.fullName }} → {{ selectedRecipient?.fullName || 'ผู้รับสิทธิ์' }}</span
            >
            <span
              >โอน {{ transferAmount }} ครั้ง · ผู้โอนคงเหลือ {{ selectedCourse.balance }} ครั้ง
              · ผู้รับได้รับ {{ transferAmount }} ครั้ง</span
            >
          </article>
          <article v-for="entry in usageEntries" :key="entry.id" class="course-history-entry">
            <strong>{{
              new Date(entry.createdAt).toLocaleDateString('th-TH', {
                day: 'numeric',
                month: 'long',
                year: 'numeric',
              })
            }}</strong>
            <div>
              <b>{{ selectedCourse.name }}</b>
              <span>รายการใช้สิทธิ์ #{{ entry.id }}</span>
            </div>
            <aside>
              <b>ใช้ {{ Math.abs(entry.quantity) }} ครั้ง</b
              ><span>คงเหลือหลังใช้ {{ entry.balanceAfter }} ครั้ง</span>
            </aside>
          </article>
          <p v-if="!usageEntries.length" class="course-history-empty">
            ยังไม่มีประวัติการใช้บริการในคอร์สนี้
          </p>
        </section>
      </template>
      <p v-else class="course-history-empty">ยังไม่มีคอร์สที่หมดอายุหรือใช้สิทธิ์ครบ</p>
      <p v-if="errorMessage" class="error" role="alert">
        {{ errorMessage }}
      </p>
    </template>
    <div
      v-if="printDialog"
      class="course-print-modal-backdrop"
      @click.self="printDialog = null"
    >
      <section
        v-modal-focus="{ close: () => (printDialog = null) }"
        class="course-print-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="course-print-title"
      >
        <template v-if="printDialog === 'confirm'">
          <h2 id="course-print-title">ยืนยันการพิมพ์เอกสาร</h2>
          <p>ตรวจสอบชื่อผู้รับบริการ ข้อมูลคอร์ส และรายการใช้สิทธิ์ให้ครบถ้วนก่อนส่งพิมพ์</p>
          <footer>
            <button type="button" @click="printDialog = null">ยกเลิก</button>
            <button type="button" @click="confirmPrintHistory">ยืนยันและพิมพ์</button>
          </footer>
        </template>
        <template v-else-if="printDialog === 'success'">
          <h2 id="course-print-title">ส่งเอกสารไปยังเครื่องพิมพ์แล้ว</h2>
          <p>สถานะใบตัดคอร์สถูกบันทึกเป็น “พิมพ์แล้ว” สามารถกลับไปดูประวัติการใช้คอร์สได้</p>
          <footer>
            <button type="button" @click="printDialog = null">ยกเลิก</button>
            <button type="button" @click="closePrintSuccess">กลับหน้าประวัติ</button>
          </footer>
        </template>
        <template v-else>
          <h2 id="course-print-title">ไม่สามารถพิมพ์เอกสารได้</h2>
          <p>กรุณาตรวจสอบเครื่องพิมพ์แล้วลองใหม่อีกครั้ง</p>
          <footer>
            <button type="button" @click="printDialog = null">ยกเลิก</button>
            <button type="button" @click="printDialog = 'confirm'">ลองอีกครั้ง</button>
          </footer>
        </template>
      </section>
    </div>
  </main>
</template>
<style scoped src="../styles/course-history-panel.css"></style>
