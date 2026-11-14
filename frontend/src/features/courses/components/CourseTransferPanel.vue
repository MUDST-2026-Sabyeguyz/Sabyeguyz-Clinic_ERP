<script setup lang="ts">
import { vModalFocus } from '@/features/appointments/modalFocus'
import PhoneIcon from '@/components/PhoneIcon.vue'
import { useCourseActionContext } from '../useCourseAction'

const {
  branchLabel,
  cancelTransfer,
  clearRecipient,
  confirm,
  confirmTransfer,
  errorMessage,
  goBack,
  handleRecipientInput,
  openTransferConfirmation,
  owner,
  recipientResults,
  recipientSearch,
  recipientSearchOpen,
  returnToCourseManager,
  selectRecipient,
  selectedCourse,
  selectedRecipient,
  startAnotherTransfer,
  transferAmount,
  transferConfirmDialog,
  transferReason,
  transferStep,
  usageSubmitting,
} = useCourseActionContext()
</script>

<template>
  <main
    class="course-transfer-main"
    :class="{
      'is-review': transferStep === 'review',
      'is-success': transferStep === 'success',
    }"
  >
    <button class="course-transfer-back app-back-link" type="button" @click="goBack">
      ← กลับไปหน้าจัดการคอร์ส
    </button>
    <h1>
      {{ transferStep === 'review' ? 'ตรวจสอบการแชร์คอร์ส' : 'แชร์คอร์ส' }}
    </h1>
    <p class="course-transfer-intro">
      {{
        transferStep === 'review'
          ? 'ตรวจสอบผู้ให้ ผู้รับ และจำนวนสิทธิ์ก่อนยืนยัน'
          : 'ระบุผู้รับ จำนวนสิทธิ์ เหตุผล และสัดส่วนยอดขาย'
      }}
    </p>
    <ol class="course-transfer-steps" aria-label="ขั้นตอนการแชร์คอร์ส">
      <li
        :class="{
          active: transferStep === 'form',
          completed: transferStep !== 'form',
        }"
      >
        <span>1</span><b>ระบุข้อมูล</b>
      </li>
      <li
        :class="{
          active: transferStep === 'review',
          completed: transferStep === 'success',
        }"
      >
        <span>2</span><b>ตรวจสอบและยืนยัน</b>
      </li>
      <li :class="{ active: transferStep === 'success' }"><span>3</span><b>สำเร็จ</b></li>
    </ol>
    <template v-if="transferStep === 'form'">
      <section class="course-transfer-owner-summary">
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
      <div class="course-transfer-columns">
        <section class="course-transfer-source">
          <h2>คอร์สที่ต้องการแชร์</h2>
          <div class="course-transfer-course-card">
            <h3>{{ selectedCourse.name }}</h3>
            <span>{{ selectedCourse.course?.code }} · ใช้งานได้</span>
            <p>
              ทั้งหมด {{ selectedCourse.total }}　 ใช้แล้ว {{ selectedCourse.used }}　 คงเหลือ
              {{ selectedCourse.balance }} ครั้ง
            </p>
          </div>
          <label
            >จำนวนสิทธิ์ที่ต้องการแชร์<span class="course-transfer-stepper"
              ><button type="button" :disabled="transferAmount <= 1" @click="transferAmount--">
                −</button
              ><input
                v-model.number="transferAmount"
                type="number"
                min="1"
                :max="selectedCourse.balance"
              /><button
                type="button"
                :disabled="transferAmount >= selectedCourse.balance"
                @click="transferAmount++"
              >
                +</button
              ><em
                >ผู้ให้จะเหลือ
                {{ Math.max(0, selectedCourse.balance - (Number(transferAmount) || 0)) }}
                ครั้ง</em
              ></span
            ></label
          >
        </section>
        <section class="course-transfer-recipient">
          <h2>ผู้รับสิทธิ์</h2>
          <label
            >ค้นหาผู้รับสิทธิ์ *<input
              v-model.trim="recipientSearch"
              placeholder="ค้นหาชื่อ เบอร์โทรศัพท์ หรือ HN"
              @input="handleRecipientInput"
            /><small>แสดงเฉพาะผู้ป่วยที่ลงทะเบียนแล้ว</small></label
          >
          <div
            v-if="recipientSearchOpen && recipientSearch.trim() && !selectedRecipient"
            class="course-transfer-results"
          >
            <button
              v-for="patientResult in recipientResults.filter((item) => item.id !== owner?.id)"
              :key="patientResult.id"
              type="button"
              @click="selectRecipient(patientResult)"
            >
              <i>{{ patientResult.fullName.slice(0, 1) }}</i
              ><span
                ><strong>{{ patientResult.fullName }}</strong
                ><small
                  >HN {{ patientResult.hn }} · {{ patientResult.phone || '-' }}</small
                ></span
              >
            </button>
            <p v-if="!recipientResults.filter((item) => item.id !== owner?.id).length">
              ไม่พบผู้ป่วยที่ตรงกับข้อมูลค้นหา
            </p>
          </div>
          <div v-if="selectedRecipient" class="course-transfer-selected">
            <i>{{ selectedRecipient.fullName.slice(0, 1) }}</i>
            <div>
              <strong>{{ selectedRecipient.fullName }}</strong
              ><span>HN {{ selectedRecipient.hn }} · {{ selectedRecipient.phone || '-' }}</span>
            </div>
            <button type="button" aria-label="เปลี่ยนผู้รับ" @click="clearRecipient">×</button>
          </div>
        </section>
      </div>
      <footer class="course-transfer-footer">
        <button type="button" @click="cancelTransfer">ยกเลิก</button
        ><button
          type="button"
          :disabled="
            !selectedRecipient || transferAmount < 1 || transferAmount > selectedCourse.balance
          "
          @click="openTransferConfirmation"
        >
          ยืนยัน
        </button>
      </footer>
      <div
        v-if="transferConfirmDialog"
        class="course-confirm-backdrop"
        @click.self="transferConfirmDialog = false"
      >
        <section
          v-modal-focus="{
            close: () => (transferConfirmDialog = false),
            busy: usageSubmitting,
          }"
          class="course-confirm-dialog course-transfer-confirm"
          role="dialog"
          aria-modal="true"
          aria-labelledby="transfer-confirm-title"
        >
          <h2 id="transfer-confirm-title">ยืนยันการแชร์คอร์ส</h2>
          <p>โปรดตรวจสอบข้อมูลก่อนยืนยันการแชร์สิทธิ์</p>
          <div class="course-confirm-details">
            <strong>{{ selectedCourse.name }} · แชร์ {{ transferAmount }} ครั้ง</strong>
            <p>
              <span>ผู้ให้สิทธิ์</span><b>{{ owner?.fullName }}</b>
            </p>
            <p>
              <span>ผู้รับสิทธิ์</span><b>{{ selectedRecipient?.fullName }}</b>
            </p>
            <p>
              <span>ผู้ให้คงเหลือหลังแชร์</span
              ><b>{{ Math.max(0, selectedCourse.balance - transferAmount) }} ครั้ง</b>
            </p>
          </div>
          <footer>
            <button
              type="button"
              :disabled="usageSubmitting"
              @click="transferConfirmDialog = false"
            >
              ยกเลิก
            </button>
            <button type="button" :disabled="usageSubmitting" @click="confirmTransfer">
              {{ usageSubmitting ? 'กำลังบันทึก...' : 'ยืนยัน' }}
            </button>
          </footer>
        </section>
      </div>
    </template>
    <section v-else-if="transferStep === 'review'" class="course-transfer-review">
      <div class="course-transfer-review-people">
        <article>
          <span>ผู้ให้สิทธิ์</span><strong>{{ owner?.fullName }}</strong
          ><small>HN {{ owner?.hn }} · {{ branchLabel(owner) }}</small>
        </article>
        <b class="course-transfer-direction" aria-hidden="true">→</b>
        <article>
          <span>ผู้รับสิทธิ์</span><strong>{{ selectedRecipient?.fullName }}</strong
          ><small>HN {{ selectedRecipient?.hn }} · {{ branchLabel(selectedRecipient) }}</small>
        </article>
      </div>
      <div class="course-transfer-review-summary">
        <p>
          <span>คอร์ส</span><b>{{ selectedCourse.name }} · {{ selectedCourse.course?.code }}</b>
        </p>
        <p>
          <span>จำนวนที่แชร์</span><b>{{ transferAmount }} ครั้ง</b>
        </p>
        <p>
          <span>ผู้ให้คงเหลือหลังแชร์</span
          ><b>{{ selectedCourse.balance - transferAmount }} ครั้ง</b>
        </p>
        <p>
          <span>ผู้รับได้รับสิทธิ์</span><b>{{ transferAmount }} ครั้ง</b>
        </p>
        <p>
          <span>สิทธิ์ที่ผู้รับใช้ได้หลังแชร์</span><b>{{ transferAmount }} ครั้ง</b>
        </p>
        <p>
          <span>เหตุผลการแชร์</span><b>{{ transferReason }}</b>
        </p>
      </div>
      <p class="course-transfer-review-note">
        ยืนยันว่าข้อมูลตรงตามความประสงค์ของเจ้าของคอร์สที่แจ้งกับคลินิก
      </p>
      <footer>
        <button type="button" @click="transferStep = 'form'">กลับไปแก้ไข</button
        ><button type="button" @click="confirm">ยืนยันแชร์คอร์ส</button>
      </footer>
    </section>
    <section v-else class="course-transfer-success">
      <div class="course-transfer-success-icon">✓</div>
      <h2>แชร์คอร์สสำเร็จ</h2>
      <p>
        เพิ่ม {{ selectedRecipient?.fullName }} เป็นผู้ใช้สิทธิ์ในคอร์ส
        {{ selectedCourse.name }} แล้ว
      </p>
      <div class="course-transfer-success-details">
        <p><span>เลขอ้างอิง</span><b>SHR-2569-0001</b></p>
        <p>
          <span>จำนวนที่แชร์</span><b>{{ transferAmount }} ครั้ง</b>
        </p>
        <p>
          <span>ผู้ให้คงเหลือ</span><b>{{ selectedCourse.balance }} ครั้ง</b>
        </p>
        <p>
          <span>ผู้รับได้รับสิทธิ์</span><b>{{ transferAmount }} ครั้ง</b>
        </p>
        <p>
          <span>สิทธิ์ที่ผู้รับใช้ได้</span><b>{{ transferAmount }} ครั้ง</b>
        </p>
      </div>
      <div class="course-transfer-success-actions">
        <button type="button" @click="startAnotherTransfer">แชร์คอร์สอีกครั้ง</button>
        <button type="button" @click="returnToCourseManager">กลับหน้าคอร์ส</button>
      </div>
    </section>
    <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
  </main>
</template>
<style scoped src="../styles/course-transfer-panel.css"></style>
