<script setup lang="ts">
import CourseRenewalDialog from '@/features/patients/components/CourseRenewalDialog.vue'
import CourseRenewalStatus from '../components/CourseRenewalStatus.vue'
import CourseRenewalQueueLink from '../components/CourseRenewalQueueLink.vue'
import PhoneIcon from '@/components/PhoneIcon.vue'
import { useCourseActionContext } from '../useCourseAction'

const {
  activeCourses,
  appointmentId,
  branchLabel,
  canReviewCourseExtensions,
  completeDirectExtension,
  completeExtensionRequest,
  courseInactiveReason,
  courseNeedingAppointment,
  courseStatusTab,
  courseUsagePercent,
  displayedCourses,
  handleCourseStatusTabKeydown,
  inactiveCourses,
  isSharedCourse,
  owner,
  purchaseDate,
  renewalNotice,
  renewalRevisions,
  renewingCourse,
  selectCourseStatusTab,
  sourceLocation,
  start,
} = useCourseActionContext()
</script>

<template>
  <main
    class="course-manager-main"
  >
    <RouterLink class="course-use-back app-back-link" :to="sourceLocation">← กลับ</RouterLink>
    <div class="course-manager-heading">
      <div>
        <h1>คอร์สของผู้ป่วย</h1>
        <p class="course-manager-intro">ตรวจสอบสิทธิ์คงเหลือ วันหมดอายุ และจัดการสิทธิ์คอร์ส</p>
      </div>
      <CourseRenewalQueueLink
        v-if="canReviewCourseExtensions && owner"
        :key="owner.id"
        :patient-id="owner.id"
      />
    </div>
    <Teleport to="body">
      <div class="course-renewal-announcement" role="status">
        <div v-if="renewalNotice" class="course-confirm-backdrop course-renewal-notice">
          <section class="course-confirm-dialog course-renewal-result">
            <span class="usage-success-check" aria-hidden="true">✓</span>
            <h2>{{ renewalNotice }}</h2>
          </section>
        </div>
      </div>
    </Teleport>
    <CourseRenewalDialog
      v-if="renewingCourse && owner"
      :key="renewingCourse.id"
      :course="renewingCourse"
      :patient-id="owner.id"
      :manager="canReviewCourseExtensions"
      @close="renewingCourse = null"
      @requested="completeExtensionRequest"
      @extended="completeDirectExtension"
    />
    <section class="course-use-patient">
      <i>{{ owner?.fullName?.replace(/^(นาย|นางสาว|นาง)/, '').slice(0, 2) || '--' }}</i>
      <div>
        <strong>{{ owner?.fullName || 'กำลังโหลด...' }}</strong
        ><span class="patient-contact-inline"
          >HN {{ owner?.hn || '-' }}
          <template v-if="owner?.phone && owner.phone !== '-'"
            ><PhoneIcon /> {{ owner.phone }}</template
          >
          <template v-else>-</template>
        </span>
      </div>
      <em>{{ branchLabel(owner) }}</em>
    </section>
    <div class="course-status-tabs" role="tablist" aria-label="สถานะสิทธิ์คอร์ส">
      <button
        id="course-status-tab-active"
        role="tab"
        :aria-selected="courseStatusTab === 'active'"
        aria-controls="course-status-panel"
        :class="{ active: courseStatusTab === 'active' }"
        type="button"
        @click="selectCourseStatusTab('active')"
        @keydown="handleCourseStatusTabKeydown"
      >
        ใช้งานได้ ({{ activeCourses.length }})
      </button>
      <button
        id="course-status-tab-inactive"
        role="tab"
        :aria-selected="courseStatusTab === 'inactive'"
        aria-controls="course-status-panel"
        :class="{ active: courseStatusTab === 'inactive' }"
        type="button"
        @click="selectCourseStatusTab('inactive')"
        @keydown="handleCourseStatusTabKeydown"
      >
        หมดอายุ / ใช้สิทธิ์ครบ ({{ inactiveCourses.length }})
      </button>
    </div>
    <section
      id="course-status-panel"
      role="tabpanel"
      :aria-labelledby="`course-status-tab-${courseStatusTab}`"
    >
      <section v-if="!displayedCourses.length" class="course-use-empty">
        <h2>
          {{
            courseStatusTab === 'active'
              ? 'ยังไม่มีคอร์สที่ใช้งานได้'
              : 'ยังไม่มีคอร์สที่หมดอายุหรือใช้สิทธิ์ครบ'
          }}
        </h2>
        <p>
          {{
            courseStatusTab === 'active'
              ? 'ผู้ป่วยรายนี้ยังไม่มีคอร์สคงเหลือสำหรับใช้บริการ'
              : 'ผู้ป่วยรายนี้ยังไม่มีคอร์สในสถานะดังกล่าว'
          }}
        </p>
      </section>
      <template v-for="course in displayedCourses" :key="course.id">
        <section
          :id="`patient-course-${course.id}`"
          class="course-manager-card"
          :class="{ 'is-shared': isSharedCourse(course) }"
        >
          <header class="course-manager-card-header">
            <div>
              <span
                >{{
                  isSharedCourse(course)
                    ? 'ได้รับจากการแชร์'
                    : courseStatusTab === 'inactive'
                      ? `คอร์สของฉัน · ${courseInactiveReason(course)}`
                      : 'คอร์สของฉัน'
                }}
                · {{ course.course.code }}</span
              >
              <h2>{{ course.name }} (คอร์ส {{ course.total }} ครั้ง)</h2>
              <p v-if="isSharedCourse(course)">
                ได้รับสิทธิ์จาก {{ course.purchaser?.fullName }} ·
                {{ course.branchCode }}
              </p>
              <p v-else>
                ซื้อเมื่อ {{ purchaseDate(course) }} ·
                {{ branchLabel(owner) }}
              </p>
            </div>
          </header>
          <div class="course-manager-metrics">
            <article>
              <small>ทั้งหมด</small><strong>{{ course.total }} ครั้ง</strong>
            </article>
            <article>
              <small>ใช้แล้วทั้งหมด</small><strong>{{ course.used }} ครั้ง</strong>
            </article>
            <article>
              <small>คงเหลือ</small>
              <strong>{{ Number.isFinite(course.balance) ? Math.max(0, course.balance) : 0 }} ครั้ง</strong>
            </article>
            <article class="course-expiry-metric">
              <small>{{
                isSharedCourse(course) ? 'วันหมดอายุสิทธิ์ของผู้รับ' : 'วันหมดอายุคอร์ส'
              }}</small>
              <strong>{{ course.expiry }}</strong>
              <CourseRenewalStatus
                v-if="courseStatusTab === 'active' && owner && !isSharedCourse(course)"
                :course-id="course.id"
                :revision="renewalRevisions[course.id] ?? 0"
              />
            </article>
          </div>
          <div
            class="course-progress"
            role="progressbar"
            aria-label="เปอร์เซ็นต์การใช้คอร์ส"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-valuenow="courseUsagePercent(course)"
          >
            <i :style="{ width: `${courseUsagePercent(course)}%` }" />
          </div>
          <p class="course-manager-used">
            ใช้แล้วทั้งหมด {{ course.used }} จาก {{ course.total }} ครั้ง
          </p>
          <div class="course-manager-actions">
            <button type="button" @click="start('history', course)">ดูประวัติการใช้</button>
            <button
              v-if="courseStatusTab === 'active' && course.status === 'ACTIVE' && !isSharedCourse(course)"
              type="button"
              @click="renewingCourse = course"
            >
              ต่ออายุคอร์ส
            </button>
            <button
              v-if="courseStatusTab === 'active' && !isSharedCourse(course)"
              class="course-share-button"
              type="button"
              @click="start('transfer', course)"
            >
              แชร์คอร์ส
            </button>
            <button
              v-if="courseStatusTab === 'active'"
              type="button"
              :disabled="!course.usable || course.balance <= 0"
              @click="start('use', course)"
            >
              ใช้สิทธิ์คอร์ส
            </button>
          </div>
          <div v-if="courseNeedingAppointment === course.id && !appointmentId" class="course-appointment-notice">
            <p role="status">ต้องมีนัดหมายก่อนใช้สิทธิ์คอร์ส หากมีนัดแล้ว ให้เปิดรายการจากหน้านัดหมาย</p>
            <RouterLink
              :to="{ name: 'staff-appointment-create', query: { patientId: owner?.id } }"
            >สร้างนัดหมาย →</RouterLink>
          </div>
        </section></template
      >
    </section>
  </main>
</template>
<style scoped src="../styles/course-manage-panel.css"></style>
