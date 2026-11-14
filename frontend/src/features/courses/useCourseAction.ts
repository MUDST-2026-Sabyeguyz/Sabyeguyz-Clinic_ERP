import { clinicToday } from '@/utils/clinicDate'
import { buildCourseUsages, type UsageSelection } from '@/features/services/careTransactions'
import { useAppointmentProcess } from '@/features/appointments/useAppointmentProcess'
import { computed, nextTick, onMounted, onUnmounted, ref, watch, inject, type InjectionKey } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { branchApi, courseApi, patientApi, staffApi, type CoursePurchase, type Branch, type Patient, type Staff } from '@/api/client'

export type CourseAction = 'manage' | 'transfer' | 'history' | 'use'
export type UiCourse = CoursePurchase & {
  name: string
  expiry: string
  balance: number
  total: number
}
export type CourseMovement = {
  id: number
  type: string
  quantity: number
  balanceAfter: number
  reason: string
  appointmentId?: number | null
  createdAt: string
  patientName?: string | null
  therapistName?: string | null
}

export function useCourseAction() {
  const route = useRoute()
  const {
    appointmentId,
    sourceAppointment,
    appointmentProgress,
    processLoading,
    processError,
    refreshAppointmentProcess,
    canStartAppointment,
    processBlockReason,
    recoveryRequired,
    beginAppointmentWrite,
    finishAppointmentWrite,
    recoverAppointmentWrite,
  } = useAppointmentProcess()
  const usageSubmitting = ref(false)
  const appointmentNeedsReview = computed(() =>
    Boolean(
      appointmentId.value &&
        appointmentProgress.value &&
        action.value !== 'history' &&
        !usageSubmitting.value &&
        !usageSuccess.value &&
        !canStartAppointment.value,
    ),
  )
  const router = useRouter()
  // นัดที่ใช้สิทธิ์คอร์สสำเร็จแล้ว (ไม่มีอะไรต้องตรวจสอบ) ให้ข้ามหน้า "รายการของนัดหมาย" ไปประวัติคอร์สเลย
  const completedUsageRedirect = computed(() => {
    const progress = appointmentProgress.value
    if (!appointmentNeedsReview.value || !progress || recoveryRequired.value) return
    if (progress.state !== 'FOUND' || route.query.action === 'history') return
    const usage = progress.usages.find((item) => item.status === 'Completed')
    if (!usage) return
    return {
      name: 'staff-courses',
      query: {
        patientId: progress.servedPatientId ? String(progress.servedPatientId) : route.query.patientId,
        courseId: String(usage.coursePurchaseId),
        action: 'history',
        appointmentId: String(progress.appointmentId),
      },
    }
  })
  watch(completedUsageRedirect, (target) => {
    if (target) void router.replace(target)
  })
  const canReviewCourseExtensions = JSON.parse(localStorage.getItem('authUser') ?? 'null')?.role === 'MANAGER'
  const action = ref<CourseAction>('manage')
  const recipientSearch = ref('')
  const recipientSearchOpen = ref(false)
  const selectedRecipient = ref<Patient | null>(null)
  const recipientConfirmed = ref(false)
  const transferAmount = ref(1)
  const transferReason = ref('แชร์สิทธิ์ตามคำขอผู้ป่วย')
  const showReasonError = ref(false)
  const coTherapistId = ref<number | ''>('')
  const primaryShare = ref(50)
  const secondaryShare = ref(50)
  const substituteNote = ref('')
  const transferReceiptId = ref<number | null>(null)
  const owner = ref<Patient | null>(null)
  const openedFromPatientProfile = computed(() => route.path.startsWith('/staff/patients/'))
  const patientCoursesLocation = computed(() => ({
    path: `/staff/patients/${owner.value?.id ?? route.params.patientId}/history`,
    query: { tab: 'courses' },
  }))
  const sourceLocation = computed(() =>
    openedFromPatientProfile.value
      ? patientCoursesLocation.value
      : { path: '/staff/services', query: { patientId: owner.value?.id } },
  )
  const owners = ref<Patient[]>([])
  const patientCourses = ref<UiCourse[]>([])
  const recipientResults = ref<Patient[]>([])
  const therapists = ref<Staff[]>([])
  const branches = ref<Branch[]>([])
  const courseUsageHistory = ref<CourseMovement[]>([])
  const errorMessage = ref('')
  const selectedCourse = ref<UiCourse>({} as UiCourse)
  const renewingCourse = ref<CoursePurchase | null>(null)
  const renewalRevisions = ref<Record<number, number>>({})
  const renewalNotice = ref('')
  let renewalNoticeTimer: ReturnType<typeof setTimeout> | undefined
  onUnmounted(() => clearTimeout(renewalNoticeTimer))
  const selectedCourseId = ref(0)
  const courseNeedingAppointment = ref<number | null>(null)
  const usageNote = ref('')
  const usageDate = ref(clinicToday())
  const treatingStaffId = ref(0)
  const usageSelections = ref<UsageSelection[]>([])
  const usageCount = computed(() =>
    usageSelections.value.reduce((total, row) => total + row.quantity, 0),
  )
  const selectedUsageQuantity = computed(() =>
    usageSelections.value
      .filter((row) => row.coursePurchaseId === selectedCourse.value.id)
      .reduce((total, row) => total + row.quantity, 0),
  )
  const usageBranchCode = computed(() => sourceAppointment.value?.branchCode)
  const treatingOptions = computed(() =>
    therapists.value.filter((person) => person.branchCode === usageBranchCode.value),
  )
  const selectedTreatingStaff = computed(() =>
    treatingOptions.value.find((person) => person.id === treatingStaffId.value),
  )
  const usageBranchName = computed(
    () =>
      branches.value.find((branch) => branch.code === usageBranchCode.value)?.name ??
      usageBranchCode.value ??
      '—',
  )
  // preselect the appointment's therapist once both the appointment and staff list are loaded
  watch(
    [() => sourceAppointment.value?.therapist?.id, treatingOptions],
    ([therapistId, options]) => {
      if (treatingStaffId.value) return
      if (therapistId && options.some((person) => person.id === therapistId))
        treatingStaffId.value = therapistId
    },
    { immediate: true },
  )
  watch(
    () => selectedCourse.value.id,
    (id) => {
      if (id) usageSelections.value = [{ coursePurchaseId: id, quantity: 1 }]
    },
  )
  const usageReview = ref(false)
  const editingTherapist = ref(false)
  const usageSuccess = ref(false)
  const usageConfirmDialog = ref(false)
  const transferConfirmDialog = ref(false)
  const usageSuccessBalance = computed(() => selectedCourse.value.balance)
  const printDialog = ref<'confirm' | 'success' | 'error' | null>(null)
  const coursePrintPreview = ref(false)
  const transferStep = ref<'form' | 'review' | 'success'>('form')
  const formattedUsageDate = computed(() =>
    new Intl.DateTimeFormat('th-TH', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    }).format(new Date(`${usageDate.value}T00:00:00`)),
  )
  const usedSessions = computed(() =>
    courseUsageHistory.value
      .filter((movement) => movement.type === 'USAGE')
      .reduce((total, movement) => total + Math.abs(movement.quantity), 0),
  )
  const transferredOut = computed(() =>
    courseUsageHistory.value
      .filter((movement) => movement.type === 'TRANSFER_OUT')
      .reduce((total, movement) => total + Math.abs(movement.quantity), 0),
  )
  const historyRemaining = computed(() => selectedCourse.value.balance)
  const documentBranch = computed(() => {
    const code = owner.value?.branchCode?.toLocaleLowerCase('th')
    const name = owner.value?.branchName?.replace(/^สาขา/, '').toLocaleLowerCase('th')
    return branches.value.find(
      (branch) =>
        (code && branch.code.toLocaleLowerCase('th') === code) ||
        (name && branch.name.replace(/^สาขา/, '').toLocaleLowerCase('th') === name),
    )
  })
  const documentClinicAddress = computed(() => documentBranch.value?.address?.trim() || '—')
  const documentClinicPhone = computed(() => documentBranch.value?.phone?.trim() || '—')
  const usageEntries = computed(() =>
    courseUsageHistory.value
      .filter((movement) => movement.type === 'USAGE')
      .slice()
      .reverse(),
  )
  const activeCourses = computed(() =>
    patientCourses.value.filter((course) => course.usable && course.balance > 0),
  )
  const inactiveCourses = computed(() =>
    patientCourses.value.filter((course) => !course.usable || course.balance <= 0),
  )
  const courseStatusTab = ref<'active' | 'inactive'>('active')
  const displayedCourses = computed(() =>
    courseStatusTab.value === 'active' ? activeCourses.value : inactiveCourses.value,
  )
  const historyFromInactive = ref(false)
  function branchLabel(patient: Patient | null) {
    const name = patient?.branchName || patient?.branchCode
    return name ? (name.startsWith('สาขา') ? name : `สาขา${name}`) : 'ไม่ระบุสาขา'
  }
  function courseUsagePercent(course: UiCourse) {
    if (!course.total) return 0
    return Math.min(100, Math.max(0, Math.round((course.used / course.total) * 100)))
  }
  function courseInactiveReason(course: UiCourse) {
    if (!Number.isFinite(course.balance) || course.balance < 0) return 'ไม่พร้อมใช้งาน'
    if (course.balance === 0) return 'ใช้สิทธิ์ครบ'
    return course.expired ? 'หมดอายุ' : 'ไม่พร้อมใช้งาน'
  }
  function selectCourseStatusTab(status: 'active' | 'inactive') {
    courseStatusTab.value = status
  }
  function handleCourseStatusTabKeydown(event: KeyboardEvent) {
    const nextStatus =
      event.key === 'ArrowRight' || event.key === 'End'
        ? 'inactive'
        : event.key === 'ArrowLeft' || event.key === 'Home'
          ? 'active'
          : null
    if (!nextStatus) return
    event.preventDefault()
    selectCourseStatusTab(nextStatus)
    void nextTick(() => document.getElementById(`course-status-tab-${nextStatus}`)?.focus())
  }
  function isSharedCourse(course: UiCourse) {
    return Boolean(owner.value && course.purchaser && course.purchaser.id !== owner.value.id)
  }
  function purchaseDate(course: UiCourse) {
    return new Intl.DateTimeFormat('th-TH', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    }).format(new Date(course.purchasedAt))
  }
  async function loadOwner() {
    const [patientList, staffList] = await Promise.all([patientApi.searchAll(), staffApi.list()])
    owners.value = patientList
    therapists.value = staffList.filter(
      (person) =>
        (person.position ?? person.role ?? '').toLowerCase() === 'therapist' && person.active,
    )
    const appointmentTherapistId = sourceAppointment.value?.therapist.id
    if (
      appointmentTherapistId &&
      therapists.value.some(
        (person) =>
          person.id === appointmentTherapistId &&
          person.branchCode === sourceAppointment.value?.branchCode,
      )
    )
      treatingStaffId.value = appointmentTherapistId
    try {
      branches.value = await branchApi.list()
    } catch {
      branches.value = []
    }
    const patientId = Number(route.query.patientId)
    owner.value = owners.value.find((patient) => patient.id === patientId) ?? null
    if (!owner.value && route.query.patientId) throw new Error('ไม่พบผู้รับบริการที่ระบุ')
    if (owner.value) await loadCourses(owner.value.id)
  }
  async function loadCourses(patientId: number) {
    const purchases = await courseApi.patientCourses(patientId)
    const courses = purchases.map((course) => ({
      ...course,
      name: course.course.name,
      expiry: new Date(course.expiresAt).toLocaleDateString('th-TH'),
      balance: course.remaining,
      total: course.purchased,
    }))
    patientCourses.value = courses
    const requestedCourseId = Number(route.query.courseId)
    const purchasedWithReceipt = patientCourses.value.filter(
      (course) => course.receiptId && course.receiptId === Number(route.query.receiptId),
    )
    const requestedCourse =
      patientCourses.value.find((course) => course.id === requestedCourseId) ??
      (purchasedWithReceipt.length === 1 ? purchasedWithReceipt[0] : undefined)
    selectedCourseId.value = requestedCourse?.id ?? patientCourses.value[0]?.id ?? 0
    selectedCourse.value = requestedCourse ?? patientCourses.value[0] ?? ({} as UiCourse)
    if (route.query.action === 'history') {
      action.value = 'history'
      coursePrintPreview.value = route.query.print === '1'
      try {
        const ledger = await courseApi.ledger(selectedCourse.value.id)
        courseUsageHistory.value = ledger.movements as CourseMovement[]
      } catch {
        courseUsageHistory.value = []
      }
    } else if (route.query.action === 'use') {
      action.value = 'manage'
      if (selectedCourse.value.id) await start('use', selectedCourse.value)
    } else if (route.query.action === 'transfer') {
      action.value = 'transfer'
      transferStep.value = 'form'
    } else action.value = 'manage'
  }
  function completeExtensionRequest() {
    return finishRenewal('ส่งคำขอแล้ว')
  }

  function completeDirectExtension(newExpiry: string) {
    const course = renewingCourse.value
    if (!course) return
    const expiry = new Intl.DateTimeFormat('th-TH', { timeZone: 'Asia/Bangkok' }).format(
      new Date(`${newExpiry}T00:00:00+07:00`),
    )
    const update = (item: UiCourse) => item.id === course.id
      ? { ...item, expiry, expiresAt: newExpiry, expired: false, usable: item.balance > 0 }
      : item
    patientCourses.value = patientCourses.value.map(update)
    selectedCourse.value = update(selectedCourse.value)
    return finishRenewal('ต่ออายุคอร์สแล้ว')
  }

  async function finishRenewal(message: string) {
    const courseId = renewingCourse.value?.id
    if (courseId == null) return
    renewingCourse.value = null
    renewalRevisions.value[courseId] = (renewalRevisions.value[courseId] ?? 0) + 1
    await nextTick()
    clearTimeout(renewalNoticeTimer)
    renewalNotice.value = message
    renewalNoticeTimer = setTimeout(() => (renewalNotice.value = ''), 2000)
  }
  onMounted(async () => {
    await refreshAppointmentProcess()
    try {
      await loadOwner()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'โหลดคอร์สไม่สำเร็จ'
    }
  })
  async function searchRecipients() {
    const keyword = recipientSearch.value.trim()
    recipientResults.value = []
    recipientSearchOpen.value = true
    if (!keyword) return
    try {
      const results = await patientApi.search(keyword, 0, 20)
      recipientResults.value = results.items
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'ค้นหาผู้ป่วยไม่สำเร็จ'
    }
  }
  async function start(nextAction: CourseAction, course: UiCourse) {
    courseNeedingAppointment.value = null
    if (nextAction === 'use' && (!course.usable || course.balance <= 0)) {
      errorMessage.value = 'คอร์สนี้ใช้สิทธิ์ครบแล้ว'
      return
    }
    if (nextAction === 'use' && !appointmentId.value) {
      courseNeedingAppointment.value = course.id
      return
    }
    const openedFromTransferredCourse = Boolean(transferReceiptId.value)
    action.value = nextAction
    selectedCourse.value = course
    selectedCourseId.value = course.id
    recipientSearch.value = ''
    recipientSearchOpen.value = false
    selectedRecipient.value = null
    recipientConfirmed.value = false
    transferAmount.value = 1
    transferReason.value = 'แชร์สิทธิ์ตามคำขอผู้ป่วย'
    transferConfirmDialog.value = false
    showReasonError.value = false
    coTherapistId.value = ''
    primaryShare.value = 50
    secondaryShare.value = 50
    substituteNote.value = ''
    if (!(openedFromTransferredCourse && (nextAction === 'history' || nextAction === 'use'))) {
      transferReceiptId.value = null
    }
    if (openedFromTransferredCourse && (nextAction === 'history' || nextAction === 'use')) {
      await router.replace({
        path: '/staff/courses',
        query: {
          patientId: owner.value?.id,
          view: 'after-transfer',
          action: nextAction,
          courseId: course.id,
          balance: course.balance,
        },
      })
    }
    usageReview.value = false
    usageSuccess.value = false
    transferStep.value = 'form'
    if (nextAction === 'use') usageReview.value = true
    if (nextAction === 'history') {
      try {
        const ledger = await courseApi.ledger(course.id)
        courseUsageHistory.value = ledger.movements as CourseMovement[]
      } catch (error) {
        courseUsageHistory.value = []
        errorMessage.value =
          error instanceof Error ? error.message : 'โหลดประวัติการใช้คอร์สไม่สำเร็จ'
      }
    }
  }
  async function changeInactiveHistoryCourse() {
    const course = inactiveCourses.value.find((item) => item.id === selectedCourseId.value)
    if (course) await start('history', course)
  }
  function goBack() {
    if (openedFromPatientProfile.value) {
      void router.push(patientCoursesLocation.value)
      return
    }
    action.value = 'manage'
    historyFromInactive.value = false
    recipientSearch.value = ''
    recipientSearchOpen.value = false
    selectedRecipient.value = null
    recipientConfirmed.value = false
    usageReview.value = false
    usageSuccess.value = false
    transferStep.value = 'form'
    if (transferReceiptId.value && owner.value) {
      router.replace({
        path: '/staff/courses',
        query: {
          patientId: owner.value.id,
          view: 'after-transfer',
          courseId: selectedCourse.value.id,
          balance: selectedCourse.value.balance,
        },
      })
    }
  }
  function cancelTransfer() {
    action.value = 'manage'
    recipientSearch.value = ''
    recipientSearchOpen.value = false
    selectedRecipient.value = null
    recipientConfirmed.value = false
    transferConfirmDialog.value = false
    transferStep.value = 'form'
    void router.replace({
      path: route.path,
      query: { ...route.query, action: 'manage' },
    })
  }
  function cancelPatientCourseDetail() {
    if (openedFromPatientProfile.value && route.query.print === '1') {
      void router.push(patientCoursesLocation.value)
      return
    }
    coursePrintPreview.value = false
  }
  async function returnToCourseManager() {
    const patientId = owner.value?.id ?? Number(route.query.patientId || route.params.patientId)
    if (patientId > 0) {
      await router.replace({
        path: route.path,
        query: { patientId, courseId: selectedCourse.value.id || undefined },
      })
    }
    action.value = 'manage'
    usageReview.value = false
    usageSuccess.value = false
    historyFromInactive.value = false
    coursePrintPreview.value = false
    courseStatusTab.value = selectedCourse.value.usable && selectedCourse.value.balance > 0 ? 'active' : 'inactive'
    await nextTick()
    document.getElementById(`patient-course-${selectedCourse.value.id}`)
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
  watch(
    () => [route.query.action, route.query.courseId],
    async () => {
      if (route.query.action !== 'history') return
      const course = patientCourses.value.find((item) => item.id === Number(route.query.courseId))
      if (course) await start('history', course)
    },
  )
  async function confirm() {
    if (!owner.value || !selectedCourse.value.id || usageSubmitting.value) return
    errorMessage.value = ''
    usageSubmitting.value = true
    try {
      const isCourseUsage = action.value === 'use'
      const usedCourseId = selectedCourse.value.id
      let usedCourses: CoursePurchase[] = []
      if (isCourseUsage) {
        if (!appointmentId.value) throw new Error('กรุณาสร้างนัดหมายก่อนใช้สิทธิ์คอร์ส')
        if (route.query.appointmentId) {
          await refreshAppointmentProcess()
          if (!canStartAppointment.value) throw new Error(processBlockReason.value)
          if (owner.value.id !== Number(route.query.patientId))
            throw new Error('ผู้ป่วยในหน้าจอไม่ตรงกับนัดหมาย กรุณาเปิดหน้าใหม่')
        }
        const usages = buildCourseUsages(
          {
            appointmentId: appointmentId.value ?? undefined,
            patientId: owner.value.id,
            treatingStaffId: treatingStaffId.value || undefined,
            branchCode: sourceAppointment.value?.branchCode ?? '',
          },
          usageSelections.value.map((row) => ({ ...row, note: usageNote.value })),
          patientCourses.value,
        )
        if (!treatingOptions.value.some((person) => person.id === treatingStaffId.value))
          throw new Error('กรุณาเลือกผู้รักษาของสาขานัดหมาย')
        usageConfirmDialog.value = false
        beginAppointmentWrite()
        usedCourses =
          usages.length === 1
            ? [await courseApi.use(usages[0]!)]
            : await courseApi.useBatch({ usages })
        if (
          !usedCourses.length ||
          usedCourses.some(
            (course) => !Number.isFinite(course.remaining) || !Number.isFinite(course.used),
          )
        )
          throw new Error('ยังยืนยันสิทธิ์คงเหลือไม่ได้ กรุณาตรวจรายการเดิม')
        finishAppointmentWrite()
        if (appointmentId.value) await refreshAppointmentProcess()
      } else {
        if (!selectedRecipient.value) return
        const transfer = await courseApi.transfer({
          sourcePurchaseId: selectedCourse.value.id,
          destinationPatientId: selectedRecipient.value.id,
          quantity: transferAmount.value,
          reason: transferReason.value || 'แชร์คอร์สตามคำขอผู้ป่วย',
        })
        transferReceiptId.value = transfer.id
      }
      if (isCourseUsage) {
        const updated = usedCourses.find((course) => course.id === usedCourseId)
        if (!updated) throw new Error('ไม่พบผลของคอร์สที่เลือก กรุณาตรวจรายการเดิม')
        const newBalance = updated.remaining
        patientCourses.value = patientCourses.value.map((course) => {
          const result = usedCourses.find((item) => item.id === course.id)
          return result
            ? { ...course, ...result, balance: result.remaining, total: result.purchased }
            : course
        })
        selectedCourse.value = patientCourses.value.find((course) => course.id === usedCourseId)!
        action.value = 'use'
        usageReview.value = false
        usageSuccess.value = true
        if (transferReceiptId.value) {
          await router.replace({
            path: '/staff/courses',
            query: {
              patientId: owner.value.id,
              view: 'after-transfer',
              action: 'use-success',
              courseId: usedCourseId,
              balance: newBalance,
            },
          })
        }
      } else {
        await loadCourses(owner.value.id)
        selectedCourse.value =
          patientCourses.value.find((course) => course.id === usedCourseId) ?? selectedCourse.value
        patientCourses.value = patientCourses.value.map((course) =>
          course.id === usedCourseId ? selectedCourse.value : course,
        )
        action.value = 'transfer'
        transferStep.value = 'success'
      }
    } catch (error) {
      await recoverAppointmentWrite(error)
      errorMessage.value =
        error instanceof Error
          ? error.message
          : action.value === 'use'
            ? 'ใช้สิทธิ์คอร์สไม่สำเร็จ'
            : 'แชร์คอร์สไม่สำเร็จ'
    } finally {
      usageSubmitting.value = false
    }
  }
  function selectRecipient(patient: Patient) {
    selectedRecipient.value = patient
    recipientConfirmed.value = false
    recipientSearchOpen.value = false
    recipientSearch.value = `${patient.fullName} HN ${patient.hn}`
  }
  async function handleRecipientInput() {
    selectedRecipient.value = null
    recipientConfirmed.value = false
    recipientResults.value = []
    if (!recipientSearch.value.trim()) {
      recipientSearchOpen.value = false
      return
    }
    await searchRecipients()
  }
  function openTransferConfirmation() {
    if (
      !selectedRecipient.value ||
      transferAmount.value < 1 ||
      transferAmount.value > selectedCourse.value.balance
    ) {
      return
    }
    transferConfirmDialog.value = true
  }
  async function confirmTransfer() {
    transferConfirmDialog.value = false
    transferReason.value ||= 'แชร์สิทธิ์ตามคำขอผู้ป่วย'
    await confirm()
  }
  function clearRecipient() {
    recipientSearch.value = ''
    selectedRecipient.value = null
    recipientConfirmed.value = false
    recipientSearchOpen.value = false
    transferConfirmDialog.value = false
  }
  function startAnotherTransfer() {
    recipientSearch.value = ''
    recipientSearchOpen.value = false
    selectedRecipient.value = null
    recipientConfirmed.value = false
    transferAmount.value = 1
    transferReason.value = ''
    showReasonError.value = false
    coTherapistId.value = ''
    primaryShare.value = 50
    secondaryShare.value = 50
    substituteNote.value = ''
    transferReceiptId.value = null
    transferStep.value = 'form'
  }
  function confirmPrintHistory() {
    printDialog.value = null
    try {
      window.print()
    } catch {
      printDialog.value = 'error'
    }
  }
  function closePrintSuccess() {
    printDialog.value = null
    coursePrintPreview.value = false
  }

  return {
    route,
    appointmentId,
    sourceAppointment,
    appointmentProgress,
    processLoading,
    processError,
    refreshAppointmentProcess,
    canStartAppointment,
    processBlockReason,
    recoveryRequired,
    beginAppointmentWrite,
    finishAppointmentWrite,
    recoverAppointmentWrite,
    usageSubmitting,
    appointmentNeedsReview,
    router,
    completedUsageRedirect,
    canReviewCourseExtensions,
    action,
    recipientSearch,
    recipientSearchOpen,
    selectedRecipient,
    recipientConfirmed,
    transferAmount,
    transferReason,
    showReasonError,
    coTherapistId,
    primaryShare,
    secondaryShare,
    substituteNote,
    transferReceiptId,
    owner,
    openedFromPatientProfile,
    patientCoursesLocation,
    sourceLocation,
    owners,
    patientCourses,
    recipientResults,
    therapists,
    branches,
    courseUsageHistory,
    errorMessage,
    selectedCourse,
    renewingCourse,
    renewalRevisions,
    renewalNotice,
    renewalNoticeTimer,
    selectedCourseId,
    courseNeedingAppointment,
    usageNote,
    usageDate,
    treatingStaffId,
    usageSelections,
    usageCount,
    selectedUsageQuantity,
    usageBranchCode,
    treatingOptions,
    selectedTreatingStaff,
    usageBranchName,
    usageReview,
    editingTherapist,
    usageSuccess,
    usageConfirmDialog,
    transferConfirmDialog,
    usageSuccessBalance,
    printDialog,
    coursePrintPreview,
    transferStep,
    formattedUsageDate,
    usedSessions,
    transferredOut,
    historyRemaining,
    documentBranch,
    documentClinicAddress,
    documentClinicPhone,
    usageEntries,
    activeCourses,
    inactiveCourses,
    courseStatusTab,
    displayedCourses,
    historyFromInactive,
    branchLabel,
    courseUsagePercent,
    courseInactiveReason,
    selectCourseStatusTab,
    handleCourseStatusTabKeydown,
    isSharedCourse,
    purchaseDate,
    loadOwner,
    loadCourses,
    completeExtensionRequest,
    completeDirectExtension,
    finishRenewal,
    searchRecipients,
    start,
    changeInactiveHistoryCourse,
    goBack,
    cancelTransfer,
    cancelPatientCourseDetail,
    returnToCourseManager,
    confirm,
    selectRecipient,
    handleRecipientInput,
    openTransferConfirmation,
    confirmTransfer,
    clearRecipient,
    startAnotherTransfer,
    confirmPrintHistory,
    closePrintSuccess,
  }
}

export type CourseActionContext = ReturnType<typeof useCourseAction>
export const courseActionKey: InjectionKey<CourseActionContext> = Symbol('courseAction')

export function useCourseActionContext(): CourseActionContext {
  const context = inject(courseActionKey)
  if (!context) throw new Error('useCourseActionContext must be used inside CourseActionView')
  return context
}
